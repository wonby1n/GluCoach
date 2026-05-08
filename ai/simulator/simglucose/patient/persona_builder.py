"""
Persona Builder
================
Convert a 14-item user persona dict into a parameter pandas Series that
can be passed directly to T1DPatient / T2DPatient / NormalPatient.

Strategy (v1, "preset + override"):
  1. Pick a template row from the type's CSV by age
  2. Override BW, Gb directly from user input
  3. Apply activity-level multipliers (Vmx, p2u, kabs)
  4. For Normal, re-derive m6/Vm0 via finalize_params

`diagnosis_years` is collected but does NOT modify simulation parameters.
The original UVA/Padova simulator validates static patient states; we have no
literature-grounded longitudinal model for kp3/Vmx/β-cell progression that
maps cleanly onto these parameters, so we keep the verified CSV values intact.
See docs/SIMILARITY_DESIGN.md §5 for the full rationale.

The 14 persona inputs (keys used by this module):
    diabetes_type     "T1D" | "T2D" | "Normal"
    birthdate         date | "YYYY-MM-DD"
    sex               "M" | "F"            (informational)
    height_cm         float                (informational; BMI display)
    weight_kg         float                (overrides BW)
    treatment         "insulin"|"medication"|"diet"  (informational)
    treatment_start   optional             (informational)
    medication        optional             (informational)
    diagnosis_years   float                (informational; not used in ODE — see header note)
    hba1c             float, optional      (estimates Gb if fasting_bg missing)
    fasting_bg        float (mg/dL)        (overrides Gb)
    activity          "low"|"medium"|"high"
    meal_pattern      str                  (informational; used by scenario)
    target_bg         float                (controller target, not patient params)
"""

import json
import os
from datetime import date, datetime

import pandas as pd

from simglucose._paths import cohort_params_path


DIABETES_TYPES = {"T1D", "T2D", "Normal"}
ACTIVITY_LEVELS = {"low", "medium", "high"}

# Activity-level multipliers applied to insulin sensitivity / absorption keys.
_ACTIVITY_MULTIPLIERS = {
    "low":    {"Vmx": 0.7, "p2u": 0.8, "kabs": 0.9},
    "medium": {"Vmx": 1.0, "p2u": 1.0, "kabs": 1.0},
    "high":   {"Vmx": 1.5, "p2u": 1.2, "kabs": 1.1},
}

# T2D CSV uses uppercase variants for distribution-volume keys.
_T2D_KEY_MAP = {"Vg": "VG", "Vi": "VI", "p2u": "p2U"}


def _params_path(patient_type):
    return cohort_params_path(patient_type)


def _resolve_key(key, patient_type):
    if patient_type == "T2D":
        return _T2D_KEY_MAP.get(key, key)
    return key


def _compute_age(birthdate):
    if isinstance(birthdate, str):
        birthdate = datetime.strptime(birthdate, "%Y-%m-%d").date()
    today = date.today()
    return today.year - birthdate.year - (
        (today.month, today.day) < (birthdate.month, birthdate.day)
    )


def _pick_template_name(patient_type, age):
    if patient_type == "T1D":
        if age < 13:
            return "child#001"
        if age < 18:
            return "adolescent#001"
        return "adult#001"
    if patient_type == "T2D":
        return "t2d#001"
    if patient_type == "Normal":
        return "normal_adult"
    raise ValueError("Unknown patient_type: {}".format(patient_type))


def _estimate_gb_from_hba1c(hba1c):
    # ADAG study: average glucose (mg/dL) ≈ 28.7 × HbA1c − 46.7
    return 28.7 * float(hba1c) - 46.7


def _apply_multipliers(params, mults, patient_type):
    for key, mult in mults.items():
        actual = _resolve_key(key, patient_type)
        if actual in params.index:
            params[actual] = float(params[actual]) * mult


def _fix_initial_glucose_state(params, gb_new, patient_type):
    """x0_4(Gp)와 Gsub을 새 Gb에 맞게 비례 보정. Gb 오버라이드 직전에 호출."""
    if "Gb" not in params.index or "x0_4" not in params.index:
        return
    gb_old = float(params["Gb"])
    if gb_old <= 0:
        return
    ratio = float(gb_new) / gb_old
    params["x0_4"] = float(params["x0_4"]) * ratio
    gsub_key = "x0_15" if patient_type == "T2D" else "x0_13"
    if gsub_key in params.index:
        params[gsub_key] = float(params[gsub_key]) * ratio


def _refinalize_normal(params):
    """Re-derive m6, Vm0 for Normal after BW/Gb/multiplier changes."""
    try:
        from generate_normal_cohort import finalize_params
    except ImportError:
        return params

    p_dict = {}
    for k, v in params.items():
        try:
            p_dict[k] = float(v)
        except (TypeError, ValueError):
            continue

    finalized = finalize_params(p_dict)
    if finalized is None:
        return params

    if "m6" in params.index:
        params["m6"] = finalized["m6"]
    if "Vm0" in params.index:
        params["Vm0"] = finalized["Vm0"]
    return params


def build_params_from_persona(persona):
    """
    Convert a 14-item persona dict into (patient_type, params_series).
    The returned Series can be passed directly into the corresponding
    Patient class constructor: ``T1DPatient(params)`` etc.
    """
    diabetes_type = persona["diabetes_type"]
    if diabetes_type not in DIABETES_TYPES:
        raise ValueError(
            "diabetes_type must be one of {}, got {!r}".format(
                DIABETES_TYPES, diabetes_type
            )
        )

    age = _compute_age(persona["birthdate"])
    template_name = _pick_template_name(diabetes_type, age)

    df = pd.read_csv(_params_path(diabetes_type))
    row = df.loc[df["Name"] == template_name]
    if row.empty:
        raise ValueError(
            "Template '{}' not found in {} CSV".format(template_name, diabetes_type)
        )
    params = row.squeeze().copy()

    # Direct overrides
    params["BW"] = float(persona["weight_kg"])

    fasting_bg = persona.get("fasting_bg")
    if fasting_bg in (None, "") and persona.get("hba1c"):
        fasting_bg = _estimate_gb_from_hba1c(persona["hba1c"])
    if fasting_bg not in (None, ""):
        if diabetes_type in ("T1D", "T2D"):
            _fix_initial_glucose_state(params, fasting_bg, diabetes_type)
        params["Gb"] = float(fasting_bg)

    params["Name"] = "persona_custom"

    # Activity multipliers
    activity = persona.get("activity", "medium")
    if activity not in ACTIVITY_LEVELS:
        raise ValueError(
            "activity must be one of {}, got {!r}".format(ACTIVITY_LEVELS, activity)
        )
    _apply_multipliers(params, _ACTIVITY_MULTIPLIERS[activity], diabetes_type)

    # Medication timing: 식사 직후 → 위장 완충으로 흡수 속도 소폭 완화
    # 식전 → 약이 먼저 흡수되어 식후 혈당 상승 억제 효과 약간 증가
    _MEDICATION_TIMING_KABS = {
        "식전":     1.1,   # 빠른 흡수 → 식후 혈당 spike 억제
        "식후":     1.0,   # 기본값
        "식사 직후": 0.85,  # 음식 완충 → 흡수 완화, 위장 부작용 감소
    }
    timing = persona.get("medication_timing")
    if timing and persona.get("treatment") == "medication" and timing in _MEDICATION_TIMING_KABS:
        _apply_multipliers(
            params, {"kabs": _MEDICATION_TIMING_KABS[timing]}, diabetes_type
        )

    # Normal: re-derive m6, Vm0 after parameter changes
    if diabetes_type == "Normal":
        params = _refinalize_normal(params)

    return diabetes_type, params


_PARSE_SYSTEM_PROMPT = """
You are a medical parameter extractor. Given a natural language description of a person,
extract the following 14 fields and return ONLY a valid JSON object with no extra text.

For each field, also return a companion "_source" field with one of:
  "explicit"  — the value was directly stated
  "inferred"  — the value was reasonably inferred from context
  "default"   — the value was not mentioned; use the population default

Fields and allowed values:
  diabetes_type       : "T1D" | "T2D" | "Normal"
  birthdate           : "YYYY-MM-DD" (derive from age if given; use today's year minus age)
  sex                 : "M" | "F"
  height_cm           : float (cm)
  weight_kg           : float (kg)
  treatment           : "insulin" | "medication" | "diet"
  medication_timing   : "식전" | "식후" | "식사 직후" | null
  medication          : string | null
  diagnosis_years     : float (years since diagnosis)
  hba1c               : float | null (percent)
  fasting_bg          : float (mg/dL)
  activity            : "low" | "medium" | "high"
  meal_pattern        : "regular_3" | "irregular" | "frequent_small" | "skip_breakfast" | "skip_lunch" | "skip_dinner" | "skip_breakfast_lunch" | "skip_breakfast_dinner" | "skip_lunch_dinner" | "fasting_day" | "late_dinner"
  target_bg           : float (mg/dL)

Universal defaults (use when not mentioned, type-independent):
  diabetes_type=Normal (only when no diabetes context at all)
  sex=M, height_cm=170, weight_kg=70,
  medication_timing=null, medication=null, hba1c=null,
  activity=medium, meal_pattern=regular_3

Type-conditional defaults (depend on diabetes_type — apply AFTER diabetes_type is resolved):
  Normal:  treatment=diet,        fasting_bg=91,   diagnosis_years=0,   target_bg=100
  T2D:     treatment=medication,  fasting_bg=140,  diagnosis_years=5,   target_bg=110
  T1D:     treatment=insulin,     fasting_bg=140,  diagnosis_years=10,  target_bg=110

Rationale: an unspecified T1D/T2D patient is most plausibly in the diabetes range (fbg≈140),
not in the normal range (91). Using a normal default for diabetic types collapses unspecified
cases into the wrong category.

Return exactly this structure (14 value fields + 14 _source fields = 28 keys total):
{
  "diabetes_type": "...", "diabetes_type_source": "...",
  "birthdate": "...", "birthdate_source": "...",
  ...
}
"""

_SOURCE_LABEL = {"explicit": "직접 언급", "inferred": "추론", "default": "기본값"}

_DISPLAY_LABELS = {
    "diabetes_type":     "당뇨유형",
    "birthdate":         "생년월일",
    "sex":               "성별",
    "height_cm":         "키 (cm)",
    "weight_kg":         "몸무게 (kg)",
    "treatment":         "치료방법",
    "medication_timing": "복용 시점",
    "medication":        "복용 약품",
    "diagnosis_years":   "진단기간 (년)",
    "hba1c":             "HbA1c (%)",
    "fasting_bg":        "공복혈당 (mg/dL)",
    "activity":          "활동수준",
    "meal_pattern":      "식사패턴",
    "target_bg":         "목표혈당 (mg/dL)",
}

_PERSONA_KEYS = list(_DISPLAY_LABELS.keys())


def parse_persona_from_text(text: str) -> dict:
    """
    자연어 설명 → 14개 페르소나 dict + 각 값의 출처(_source).
    Claude Code CLI(subprocess)를 사용하므로 별도 API 키 불필요.
    """
    import subprocess

    claude_path = r"C:\Users\SSAFY\.local\bin\claude.exe"
    full_prompt = _PARSE_SYSTEM_PROMPT.strip() + "\n\nInput: " + text
    env = {k: v for k, v in os.environ.items() if k != "ANTHROPIC_API_KEY"}
    result = subprocess.run(
        [claude_path, "-p", full_prompt],
        capture_output=True,
        text=True,
        encoding="utf-8",
        env=env,
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip() or "claude CLI 호출 실패")
    raw = result.stdout.strip()
    if raw.startswith("```"):
        raw = raw.split("```")[1]
        if raw.startswith("json"):
            raw = raw[4:]
    return json.loads(raw)
