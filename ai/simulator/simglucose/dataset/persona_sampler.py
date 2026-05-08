"""Stratified persona sampling.

A persona category is defined by 4 categorical bins:
  (diabetes_type, activity, fbg_bin, weight_bin)

`enumerate_categories()` returns all clinically-plausible categories. For each
category, `sample_persona(category, rng)` draws a persona whose continuous
values fall inside that category's bins.

The output dict is compatible with simglucose.patient.factory.create_patient_from_persona.
"""

from datetime import date, timedelta

from .scenario_sampler import MEAL_PATTERNS


_DIABETES_TYPES = ("T1D", "T2D", "Normal")
_ACTIVITY_LEVELS = ("low", "medium", "high")

# fasting_bg bins (mg/dL) — taken from ADA Standards of Care 2024
_FBG_BINS = {
    "normal":      (70.0, 99.0),
    "prediabetes": (100.0, 125.0),
    "diabetes":    (126.0, 180.0),
    "uncontrolled": (180.0, 250.0),
}

# Type-specific allowable fbg bins.
# - Normal: 1 bin only (by ADA definition Normal has fbg < 100)
# - T1D / T2D: all 4 bins — once diagnosed, treatment can bring fbg into any
#   range. We're typifying ongoing-state patients, not at-diagnosis criteria.
# See docs/DATASET_GENERATION.md for the rationale and decision history.
_FBG_BINS_BY_TYPE = {
    "Normal": ("normal",),
    "T2D":    ("normal", "prediabetes", "diabetes", "uncontrolled"),
    "T1D":    ("normal", "prediabetes", "diabetes", "uncontrolled"),
}

# weight_kg bins — covers the realistic Korean adult range (40-100 kg)
_WEIGHT_BINS = {
    "light":      (40.0, 55.0),
    "medium":     (55.0, 70.0),
    "heavy":      (70.0, 85.0),
    "very_heavy": (85.0, 100.0),
}

_TREATMENT_BY_TYPE = {
    "T1D":    "insulin",
    "T2D":    "medication",
    "Normal": "diet",
}


def enumerate_categories():
    """Return all clinically-plausible persona categories.

    A category is a 4-tuple: (diabetes_type, activity, fbg_bin_name, weight_bin_name).
    """
    categories = []
    for diabetes_type in _DIABETES_TYPES:
        for activity in _ACTIVITY_LEVELS:
            for fbg_bin in _FBG_BINS_BY_TYPE[diabetes_type]:
                for weight_bin in _WEIGHT_BINS:
                    categories.append((diabetes_type, activity, fbg_bin, weight_bin))
    return categories


def category_id(category):
    """Compact human-readable category label, e.g. 'T2D_med_diabetes_heavy'."""
    diabetes_type, activity, fbg_bin, weight_bin = category
    act_short = {"low": "lo", "medium": "med", "high": "hi"}[activity]
    return f"{diabetes_type}_{act_short}_{fbg_bin}_{weight_bin}"


def category_describe(category):
    """Return a human-readable description dict for a category."""
    diabetes_type, activity, fbg_bin, weight_bin = category
    fbg_lo, fbg_hi = _FBG_BINS[fbg_bin]
    w_lo, w_hi = _WEIGHT_BINS[weight_bin]
    activity_kr = {"low": "저", "medium": "중", "high": "고"}[activity]
    return {
        "id":             category_id(category),
        "diabetes_type":  diabetes_type,
        "activity":       activity,
        "activity_label": activity_kr,
        "fbg_bin":        fbg_bin,
        "fbg_range":      f"{int(fbg_lo)}–{int(fbg_hi)} mg/dL",
        "weight_bin":     weight_bin,
        "weight_range":   f"{int(w_lo)}–{int(w_hi)} kg",
    }


def _bin_value(value, bins_dict):
    """Find which bin a continuous value falls into. Returns bin name or None."""
    if value is None:
        return None
    v = float(value)
    for name, (lo, hi) in bins_dict.items():
        if lo <= v <= hi:
            return name
    return None


def persona_to_category(persona):
    """Map a persona dict to its (diabetes_type, activity, fbg_bin, weight_bin) category.

    Returns:
        (category_tuple, warnings_list)
        category_tuple: 4-tuple if a valid category exists, otherwise None
        warnings_list: messages explaining any out-of-range values or invalid combos
    """
    warnings = []
    diabetes_type = persona.get("diabetes_type")
    activity = persona.get("activity")
    fasting_bg = persona.get("fasting_bg")
    weight_kg = persona.get("weight_kg")

    if diabetes_type not in _DIABETES_TYPES:
        warnings.append(f"unknown diabetes_type: {diabetes_type!r}")
        return None, warnings
    if activity not in _ACTIVITY_LEVELS:
        warnings.append(f"unknown activity: {activity!r}")
        return None, warnings

    fbg_bin = _bin_value(fasting_bg, _FBG_BINS)
    if fbg_bin is None:
        warnings.append(f"fasting_bg {fasting_bg} 이 어떤 빈에도 속하지 않음 (40–250 범위)")
        return None, warnings
    if fbg_bin not in _FBG_BINS_BY_TYPE[diabetes_type]:
        warnings.append(
            f"{diabetes_type} 에는 fbg 빈 {fbg_bin!r} 가 허용되지 않음 "
            f"(허용: {_FBG_BINS_BY_TYPE[diabetes_type]})"
        )
        return None, warnings

    weight_bin = _bin_value(weight_kg, _WEIGHT_BINS)
    if weight_bin is None:
        warnings.append(f"weight_kg {weight_kg} 이 어떤 빈에도 속하지 않음 (40–100 범위)")
        return None, warnings

    return (diabetes_type, activity, fbg_bin, weight_bin), warnings


def _age_to_birthdate(age_years):
    today = date.today()
    return today - timedelta(days=int(age_years * 365.25))


def sample_persona(category, rng):
    """Sample a persona dict whose continuous values fall inside the given category.

    Args:
        category: 4-tuple (diabetes_type, activity, fbg_bin, weight_bin)
        rng: numpy.random.Generator

    Returns:
        dict matching build_params_from_persona's expected input.
    """
    diabetes_type, activity, fbg_bin_name, weight_bin_name = category

    if diabetes_type not in _DIABETES_TYPES:
        raise ValueError(f"Unknown diabetes_type: {diabetes_type}")
    if activity not in _ACTIVITY_LEVELS:
        raise ValueError(f"Unknown activity: {activity}")
    if fbg_bin_name not in _FBG_BINS_BY_TYPE[diabetes_type]:
        raise ValueError(
            f"fbg_bin {fbg_bin_name!r} not allowed for {diabetes_type}"
        )
    if weight_bin_name not in _WEIGHT_BINS:
        raise ValueError(f"Unknown weight_bin: {weight_bin_name}")

    sex = "M" if rng.random() < 0.5 else "F"

    # height: free, sex-conditional
    height_mean = 173.0 if sex == "M" else 161.0
    height_cm = float(rng.normal(height_mean, 7.0))
    height_cm = max(145.0, min(195.0, height_cm))

    # weight: uniform within the cell's bin
    w_lo, w_hi = _WEIGHT_BINS[weight_bin_name]
    weight_kg = float(rng.uniform(w_lo, w_hi))

    # fasting_bg: uniform within the cell's bin
    fbg_lo, fbg_hi = _FBG_BINS[fbg_bin_name]
    fasting_bg = float(rng.uniform(fbg_lo, fbg_hi))

    # age: uniform 20-70
    age = float(rng.uniform(20.0, 70.0))
    birthdate = _age_to_birthdate(age)

    # diagnosis_years: free (informational only since progression is removed)
    if diabetes_type == "Normal":
        diagnosis_years = 0.0
    else:
        diagnosis_years = float(min(40.0, rng.exponential(scale=10.0)))

    treatment = _TREATMENT_BY_TYPE[diabetes_type]
    medication_timing = "식후" if treatment == "medication" else None
    medication = "메트포르민" if treatment == "medication" else None

    target_bg = 110.0 if diabetes_type != "Normal" else 100.0

    # meal_pattern: uniform draw from the 11 categories defined in scenario_sampler.
    # Each category is a deterministic 1-day meal plan; cell stratification stays
    # at 4 dimensions (type/activity/fbg/weight) so meal_pattern varies inside each cell.
    meal_pattern = MEAL_PATTERNS[int(rng.integers(0, len(MEAL_PATTERNS)))]

    return {
        "diabetes_type":     diabetes_type,
        "birthdate":         birthdate,
        "sex":               sex,
        "height_cm":         round(height_cm, 1),
        "weight_kg":         round(weight_kg, 1),
        "treatment":         treatment,
        "medication_timing": medication_timing,
        "medication":        medication,
        "diagnosis_years":   round(diagnosis_years, 1),
        "hba1c":             None,
        "fasting_bg":        round(fasting_bg, 1),
        "activity":          activity,
        "meal_pattern":      meal_pattern,
        "target_bg":         target_bg,
    }
