"""
GlucoCoach — Streamlit 기반 혈당 시뮬레이터
Dalla Man 2007 ODE 모델 (Normal / T1D / T2D) 사용
실행: streamlit run app.py
"""

import copy
import os
from datetime import datetime, timedelta

import numpy as np
import pandas as pd
import pkg_resources
import plotly.graph_objects as go
import streamlit as st

from simglucose.actuator.pump import InsulinPump
from simglucose.controller.basal_bolus_ctrller import BBController
from simglucose.dataset import (
    category_describe,
    category_id,
    enumerate_categories,
    generate_from_plan,
    persona_to_category,
    save_dataset,
)
from simglucose.patient import factory as patient_factory
from simglucose.sensor.cgm import CGMSensor
from simglucose.simulation.env import T1DSimEnv
from simglucose.simulation.scenario import CustomScenario

# ── 파일 경로 ──────────────────────────────────────────────────────────────────
SENSOR_PARA = pkg_resources.resource_filename("simglucose", "params/sensor.csv")
PUMP_PARA   = pkg_resources.resource_filename("simglucose", "params/pump.csv")
LOG_FILE    = os.path.join(os.path.dirname(__file__), "results", "simulation_log.csv")

COLORS = ["#3B82F6", "#EF4444", "#22C55E", "#F59E0B",
          "#A855F7", "#06B6D4", "#EC4899", "#84CC16"]

# UI에 노출할 환자 타입 — factory에 등록된 타입 중 사용자에게 보여줄 것만
PATIENT_TYPE_LABELS = {
    "Normal": "정상인",
    "T1D":    "1형 당뇨",
    "T2D":    "2형 당뇨",
}

# 음식 → 탄수화물(g) 임시 매핑. 추후 음식 DB 연동 시 교체.
FOOD_CHO_MAP = {
    "밥 1공기 (200g)":    75,
    "잡곡밥 1공기 (200g)": 70,
    "김밥 1줄":           60,
    "라면 1봉":           65,
    "비빔밥 1그릇":        90,
    "국수 1인분":         70,
    "샌드위치 1개":        45,
    "피자 1조각":          35,
    "햄버거 1개":          40,
    "우유 1잔 (200mL)":   10,
    "사과 1개 (200g)":    25,
    "바나나 1개":          27,
    "식빵 2장":            30,
    "감자 1개":            27,
    "고구마 1개":          30,
}


# ── 페르소나 편집 폼 (자연어 모드 / 데이터셋 NL 탭에서 공용) ──────────────────

def render_persona_edit_form(parsed: dict, key_prefix: str) -> dict:
    """파싱된 페르소나(`parsed`)를 4탭 폼에 pre-fill하고 사용자 수정값으로 dict 반환.

    위젯 key 충돌 방지를 위해 `key_prefix`로 namespace 구분 (예: "nl", "dsnl").
    `_DISPLAY_LABELS` / `_SOURCE_LABEL`는 호출부에서 import 되어 있어야 함.
    """
    from simglucose.patient.persona_builder import _SOURCE_LABEL  # noqa: F401

    def _pf(key, default):
        v = parsed.get(key)
        try:
            return float(v) if v not in (None, "", "null") else default
        except (TypeError, ValueError):
            return default

    def _src_help(key):
        src = parsed.get(key + "_source", "default")
        return {"explicit": "", "inferred": "(AI 추론)", "default": "(기본값)"}.get(src, "")

    _bd_raw = parsed.get("birthdate", "1990-01-01")
    if isinstance(_bd_raw, str):
        try:
            _bd_default = datetime.strptime(_bd_raw, "%Y-%m-%d").date()
        except ValueError:
            _bd_default = datetime(1990, 1, 1).date()
    else:
        _bd_default = _bd_raw or datetime(1990, 1, 1).date()

    with st.expander("✏️ 파라미터 수정", expanded=True):
        tab_basic, tab_treat, tab_life, tab_goal = st.tabs(
            ["기본정보", "치료정보", "생활패턴", "목표"]
        )

        with tab_basic:
            _dt_options = list(PATIENT_TYPE_LABELS.values())
            _dt_default_label = PATIENT_TYPE_LABELS.get(parsed.get("diabetes_type", "Normal"), _dt_options[0])
            _dt_idx = _dt_options.index(_dt_default_label) if _dt_default_label in _dt_options else 0

            c1, c2 = st.columns(2)
            f_diabetes_label = c1.radio(
                f"당뇨유형 {_src_help('diabetes_type')}",
                _dt_options, index=_dt_idx, horizontal=True, key=f"{key_prefix}_diabetes_type",
            )
            f_diabetes_type = next(k for k, v in PATIENT_TYPE_LABELS.items() if v == f_diabetes_label)

            _sex_options = ["M", "F"]
            _sex_idx = _sex_options.index(parsed.get("sex", "M")) if parsed.get("sex", "M") in _sex_options else 0
            f_sex = c2.radio(
                f"성별 {_src_help('sex')}",
                _sex_options, index=_sex_idx, horizontal=True, key=f"{key_prefix}_sex",
            )

            c3, c4 = st.columns(2)
            f_birthdate = c3.date_input(
                f"생년월일 {_src_help('birthdate')}",
                value=_bd_default,
                min_value=datetime(1925, 1, 1).date(),
                max_value=datetime.now().date(),
                key=f"{key_prefix}_birthdate",
            )
            f_height_cm = c4.number_input(
                f"키 (cm) {_src_help('height_cm')}",
                100.0, 220.0, float(_pf("height_cm", 170.0)), 0.5, key=f"{key_prefix}_height_cm",
            )

            f_weight_kg = st.number_input(
                f"몸무게 (kg) {_src_help('weight_kg')}",
                30.0, 200.0, float(_pf("weight_kg", 70.0)), 0.5, key=f"{key_prefix}_weight_kg",
            )
            f_bmi = f_weight_kg / (f_height_cm / 100.0) ** 2
            st.caption(f"BMI ≈ {f_bmi:.1f}")

        with tab_treat:
            _treat_options = ["insulin", "medication", "diet"]
            _treat_default = parsed.get("treatment", "diet")
            _treat_idx = _treat_options.index(_treat_default) if _treat_default in _treat_options else 2
            f_treatment = st.selectbox(
                f"치료방법 {_src_help('treatment')}",
                _treat_options, index=_treat_idx,
                format_func=lambda x: {"insulin": "인슐린 주사", "medication": "약 복용", "diet": "식이요법"}[x],
                key=f"{key_prefix}_treatment",
            )

            f_medication_timing = None
            f_medication = None
            if f_treatment == "medication":
                c1, c2 = st.columns(2)
                _mt_options = ["식전", "식후", "식사 직후"]
                _mt_default = parsed.get("medication_timing") or "식후"
                _mt_idx = _mt_options.index(_mt_default) if _mt_default in _mt_options else 1
                f_medication_timing = c1.selectbox(
                    f"복용 시점 {_src_help('medication_timing')}",
                    _mt_options, index=_mt_idx, key=f"{key_prefix}_medication_timing",
                )
                f_medication = c2.text_input(
                    f"복용 약품 {_src_help('medication')}",
                    value=parsed.get("medication") or "메트포르민",
                    key=f"{key_prefix}_medication",
                )

            c3, c4 = st.columns(2)
            f_diagnosis_years = c3.number_input(
                f"진단기간 (년) {_src_help('diagnosis_years')}",
                0.0, 60.0, float(_pf("diagnosis_years", 0.0)), 0.5, key=f"{key_prefix}_diagnosis_years",
            )
            f_hba1c = c4.number_input(
                f"HbA1c (%, 선택) {_src_help('hba1c')}",
                0.0, 15.0, float(_pf("hba1c", 0.0)), 0.1,
                help="0이면 미입력으로 간주됩니다.", key=f"{key_prefix}_hba1c",
            )

        with tab_life:
            _act_map = {"low": "저", "medium": "중", "high": "고"}
            _act_rev = {"저": "low", "중": "medium", "고": "high"}
            _act_options = ["저", "중", "고"]
            _act_default_label = _act_map.get(parsed.get("activity", "medium"), "중")
            _act_idx = _act_options.index(_act_default_label)
            f_activity_label = st.radio(
                f"활동수준 {_src_help('activity')}",
                _act_options, index=_act_idx, horizontal=True,
                help="저: 좌식 위주 · 중: 일상적 활동 · 고: 운동선수/육체노동",
                key=f"{key_prefix}_activity",
            )
            f_activity = _act_rev[f_activity_label]

            _mp_options = ["regular_3", "irregular", "frequent"]
            _mp_default = parsed.get("meal_pattern", "regular_3")
            _mp_idx = _mp_options.index(_mp_default) if _mp_default in _mp_options else 0
            f_meal_pattern = st.selectbox(
                f"식사패턴 {_src_help('meal_pattern')}",
                _mp_options, index=_mp_idx,
                format_func=lambda x: {"regular_3": "규칙적 3식", "irregular": "불규칙", "frequent": "잦은 간식"}[x],
                key=f"{key_prefix}_meal_pattern",
            )

        with tab_goal:
            c1, c2 = st.columns(2)
            f_target_bg = c1.number_input(
                f"목표혈당 (mg/dL) {_src_help('target_bg')}",
                70.0, 200.0, float(_pf("target_bg", 110.0)), 1.0, key=f"{key_prefix}_target_bg",
            )
            f_fasting_bg = c2.number_input(
                f"공복혈당 (mg/dL) {_src_help('fasting_bg')}",
                50.0, 300.0, float(_pf("fasting_bg", 91.0)), 1.0, key=f"{key_prefix}_fasting_bg",
            )

    return {
        "diabetes_type":     f_diabetes_type,
        "birthdate":         f_birthdate,
        "sex":               f_sex,
        "height_cm":         f_height_cm,
        "weight_kg":         f_weight_kg,
        "treatment":         f_treatment,
        "medication_timing": f_medication_timing,
        "medication":        f_medication,
        "diagnosis_years":   f_diagnosis_years,
        "hba1c":             f_hba1c if f_hba1c > 0 else None,
        "fasting_bg":        f_fasting_bg,
        "activity":          f_activity,
        "meal_pattern":      f_meal_pattern,
        "target_bg":         f_target_bg,
    }


# ── 시뮬레이션 함수 ────────────────────────────────────────────────────────────

def run_meal_only_sim(patient_type, patient_names, scenario, sim_time,
                      cgm_name, cgm_seed):
    """인슐린 입력 없는 환자 (Normal) 용 루프."""
    action_cls = patient_factory.get_action_type(patient_type)
    results = {}
    total_min = int(sim_time.total_seconds() / 60)

    for pname in patient_names:
        patient = patient_factory.create_patient(patient_type, pname)
        sensor  = CGMSensor.withName(cgm_name, seed=cgm_seed)
        scen    = copy.deepcopy(scenario)
        t0      = scen.start_time

        time_hist = [t0]
        BG_hist   = [patient.observation.Gsub]
        CGM_hist  = [sensor.measure(patient)]
        CHO_hist  = [0.0]

        current = t0
        for _ in range(total_min):
            meal = scen.get_action(current).meal
            patient.step(action_cls(CHO=meal))
            current += timedelta(minutes=1)
            time_hist.append(current)
            BG_hist.append(patient.observation.Gsub)
            CGM_hist.append(sensor.measure(patient))
            CHO_hist.append(meal)

        results[pname] = pd.DataFrame(
            {"BG": BG_hist, "CGM": CGM_hist, "CHO": CHO_hist},
            index=pd.DatetimeIndex(time_hist, name="Time"),
        )
    return results


def run_insulin_sim(patient_type, patient_names, scenario, sim_time,
                    cgm_name, cgm_seed, pump_name):
    """인슐린 입력 받는 환자 (T1D, T2D) 용 루프 — 펌프+컨트롤러 사용."""
    results = {}
    for pname in patient_names:
        patient    = patient_factory.create_patient(patient_type, pname)
        sensor     = CGMSensor.withName(cgm_name, seed=cgm_seed)
        pump       = InsulinPump.withName(pump_name)
        scen       = copy.deepcopy(scenario)
        controller = BBController()
        env        = T1DSimEnv(patient, sensor, pump, scen)

        obs, reward, done, info = env.reset()
        controller.reset()
        while env.time < scen.start_time + sim_time:
            action = controller.policy(obs, reward, done, **info)
            obs, reward, done, info = env.step(action)

        results[pname] = env.show_history()
    return results


def run_sim(patient_type, patient_names, scenario, sim_time,
            cgm_name, cgm_seed, pump_name):
    """타입에 따라 알맞은 시뮬레이션 경로로 라우팅."""
    if patient_factory.is_insulin_capable(patient_type):
        return run_insulin_sim(patient_type, patient_names, scenario, sim_time,
                               cgm_name, cgm_seed, pump_name)
    return run_meal_only_sim(patient_type, patient_names, scenario, sim_time,
                             cgm_name, cgm_seed)


def run_sim_with_patient(patient, patient_type, patient_label, scenario, sim_time,
                         cgm_name, cgm_seed, pump_name):
    """페르소나로 만든 단일 환자 인스턴스용 시뮬레이션 진입점."""
    if patient_factory.is_insulin_capable(patient_type):
        sensor     = CGMSensor.withName(cgm_name, seed=cgm_seed)
        pump       = InsulinPump.withName(pump_name)
        scen       = copy.deepcopy(scenario)
        controller = BBController()
        env        = T1DSimEnv(patient, sensor, pump, scen)

        obs, reward, done, info = env.reset()
        controller.reset()
        while env.time < scen.start_time + sim_time:
            action = controller.policy(obs, reward, done, **info)
            obs, reward, done, info = env.step(action)

        return {patient_label: env.show_history()}

    # meal-only (Normal)
    action_cls = patient_factory.get_action_type(patient_type)
    sensor     = CGMSensor.withName(cgm_name, seed=cgm_seed)
    scen       = copy.deepcopy(scenario)
    t0         = scen.start_time
    total_min  = int(sim_time.total_seconds() / 60)

    time_hist = [t0]
    BG_hist   = [patient.observation.Gsub]
    CGM_hist  = [sensor.measure(patient)]
    CHO_hist  = [0.0]

    current = t0
    for _ in range(total_min):
        meal = scen.get_action(current).meal
        patient.step(action_cls(CHO=meal))
        current += timedelta(minutes=1)
        time_hist.append(current)
        BG_hist.append(patient.observation.Gsub)
        CGM_hist.append(sensor.measure(patient))
        CHO_hist.append(meal)

    df = pd.DataFrame(
        {"BG": BG_hist, "CGM": CGM_hist, "CHO": CHO_hist},
        index=pd.DatetimeIndex(time_hist, name="Time"),
    )
    return {patient_label: df}


# ── 통계 / 등급 ────────────────────────────────────────────────────────────────

def compute_stats(df):
    bg = df["BG"].dropna().values
    peak_bg  = float(np.max(bg))
    t_peak   = int(np.argmax(bg))
    baseline = float(bg[0])
    tir_140  = float(np.mean((bg >= 70) & (bg <= 140)) * 100)
    tir_180  = float(np.mean((bg >= 70) & (bg <= 180)) * 100)
    min_bg   = float(np.min(bg))
    return dict(peak_bg=peak_bg, t_peak=t_peak, baseline=baseline,
                tir_140=tir_140, tir_180=tir_180, min_bg=min_bg)


def grade(peak_bg):
    if peak_bg > 180: return "D", "🚨"
    if peak_bg > 170: return "C", "⚠️"
    if peak_bg > 140: return "B", "⚠️"
    return "A", "✅"


def log_run(run_meta):
    os.makedirs(os.path.dirname(LOG_FILE), exist_ok=True)
    row = pd.DataFrame([run_meta])
    if os.path.exists(LOG_FILE):
        row.to_csv(LOG_FILE, mode="a", header=False, index=False)
    else:
        row.to_csv(LOG_FILE, index=False)


# ── 차트 ──────────────────────────────────────────────────────────────────────

def build_chart(results_dict, meals, patient_type):
    fig = go.Figure()
    show_insulin = patient_factory.is_insulin_capable(patient_type)

    for i, (pname, df) in enumerate(results_dict.items()):
        color   = COLORS[i % len(COLORS)]
        minutes = [(t - df.index[0]).total_seconds() / 60 for t in df.index]

        fig.add_trace(go.Scatter(
            x=minutes, y=df["BG"].tolist(),
            mode="lines", name=f"{pname} BG",
            line=dict(color=color, width=2.5),
        ))
        fig.add_trace(go.Scatter(
            x=minutes, y=df["CGM"].tolist(),
            mode="lines", name=f"{pname} CGM",
            line=dict(color=color, width=1.5, dash="dot"),
            opacity=0.65,
        ))
        if show_insulin and "insulin" in df.columns:
            fig.add_trace(go.Scatter(
                x=minutes, y=df["insulin"].fillna(0).tolist(),
                mode="lines", name=f"{pname} 인슐린",
                line=dict(color=color, width=1, dash="longdash"),
                opacity=0.5,
                yaxis="y2",
            ))

    # 기준선
    for y_val, color, label in [
        (70,  "#EF4444", "저혈당 70"),
        (140, "#F59E0B", "주의 140"),
        (180, "#EF4444", "고혈당 180"),
    ]:
        fig.add_hline(y=y_val, line_dash="dash", line_color=color, line_width=1,
                      annotation_text=label, annotation_position="right")

    # 식사 이벤트
    for meal_hr, meal_g in meals:
        meal_min = meal_hr * 60
        fig.add_vline(x=meal_min, line_dash="dash", line_color="#22C55E", line_width=2,
                      annotation_text=f"{meal_g}g", annotation_position="top right")

    layout = dict(
        xaxis_title="경과 시간 (분)",
        yaxis_title="혈당 (mg/dL)",
        legend=dict(orientation="h", y=-0.18),
        plot_bgcolor="#F8FAFC",
        paper_bgcolor="#fff",
        margin=dict(l=50, r=60, t=30, b=50),
        hovermode="x unified",
    )
    if show_insulin:
        layout["yaxis2"] = dict(
            title="인슐린 (U/min)", overlaying="y", side="right", showgrid=False
        )
    fig.update_layout(**layout)
    return fig


# ══════════════════════════════════════════════════════════════════════════════
# Streamlit UI
# ══════════════════════════════════════════════════════════════════════════════

st.set_page_config(page_title="GlucoCoach", layout="wide", page_icon="📊")
st.title("GlucoCoach — 혈당 시뮬레이터")
st.caption("Dalla Man 2007 ODE 모델 기반 · Normal / T1D / T2D")

# ── 사이드바 ──────────────────────────────────────────────────────────────────
with st.sidebar:
    st.header("⚙ 설정")

    input_mode = st.radio(
        "입력 모드",
        ["프리셋 환자", "페르소나 입력", "자연어 입력", "데이터셋 생성"],
        horizontal=True,
        help="프리셋: 사전 정의된 가상환자 · 페르소나: 14개 항목 직접 입력 · 자연어: AI가 파라미터 추론 · 데이터셋: 카테고리별 ML 학습 데이터 생성",
    )

    # factory에 등록된 타입만 UI에 표시
    available = [t for t in patient_factory.available_types() if t in PATIENT_TYPE_LABELS]
    type_options = [PATIENT_TYPE_LABELS[t] for t in available]

    if input_mode == "프리셋 환자":
        selected_label = st.radio("환자 유형", type_options, horizontal=True)
        patient_type = available[type_options.index(selected_label)]

        df_p      = pd.read_csv(patient_factory.get_params_path(patient_type))
        all_names = list(df_p["Name"].values)
        sel_patients = st.multiselect(
            "환자 선택 (복수 가능)", all_names, default=[all_names[0]]
        )
    elif input_mode == "페르소나 입력":
        st.info("👉 메인 영역의 페르소나 입력 폼을 채워주세요.")
        patient_type = None
        sel_patients = []
    elif input_mode == "데이터셋 생성":
        st.info("👉 메인 영역에서 카테고리별 생성 계획을 설정하세요.")
        patient_type = None
        sel_patients = []
    else:  # 자연어 입력
        st.info("👉 메인 영역에서 자연어로 페르소나를 설명하세요.")
        patient_type = None
        sel_patients = []

    st.divider()

    is_dataset_mode = (input_mode == "데이터셋 생성")

    if is_dataset_mode:
        st.subheader("📦 데이터셋 설정")
        st.caption("시뮬 기간: 1일/명 고정 (meal_pattern은 1일 결정론적 카테고리)")
        dataset_output_dir = st.text_input("출력 디렉터리", "data/simulator")
        dataset_seed = int(st.number_input("랜덤 시드", 0, 99999, 42, key="ds_seed"))
        _max_workers = max(1, (os.cpu_count() or 1))
        _default_workers = min(4, _max_workers)
        dataset_workers = int(st.number_input(
            "병렬 워커 수", 1, _max_workers, _default_workers, key="ds_workers",
            help=f"1=직렬(디버깅용). >1=multiprocessing. 이 머신 CPU={_max_workers}코어. "
                 "워커가 늘어도 결정성은 유지됨(시드는 페르소나별로 미리 분배)."
        ))

        # 단일 시뮬용 설정은 비활성. dummy 값으로 둬서 하단 코드 호환.
        sim_hours = 24
        start_hour = 0
        meals = []
        meal_input_mode = "탄수화물 직접 입력"
        n_meals = 0
    else:
        sim_hours  = st.slider("시뮬레이션 시간 (hr)", 1, 24, 8)
        start_hour = st.slider("시작 시간 (hr, 0=자정)", 0, 23, 0)

        st.subheader("🍽 식사 시나리오")
        meal_input_mode = st.radio(
            "입력 방식",
            ["탄수화물 직접 입력", "음식 선택"],
            horizontal=True,
        )
        n_meals = int(st.number_input("식사 횟수", 1, 6, 3))

        _defaults = [(1.0, 60), (5.0, 70), (11.0, 60), (15.0, 40), (19.0, 70), (22.0, 30)]
        _food_options = list(FOOD_CHO_MAP.keys())
        meals = []
        for i in range(n_meals):
            c1, c2 = st.columns(2)
            dh, dg = _defaults[i] if i < len(_defaults) else (float(i * 4 + 1), 60)
            mt = c1.number_input(f"#{i+1} 시간(hr)", 0.0, float(sim_hours),
                                 min(dh, float(sim_hours)), 0.5, key=f"mt{i}")
            if meal_input_mode == "음식 선택":
                food = c2.selectbox(
                    f"#{i+1} 음식", _food_options,
                    index=min(i, len(_food_options) - 1), key=f"mfood{i}",
                )
                mg = FOOD_CHO_MAP[food]
                c2.caption(f"≈ {mg} g CHO")
            else:
                mg = c2.number_input(f"#{i+1} 탄수화물(g)", 0, 300, dg, 5, key=f"mg{i}")
            if mg > 0:
                meals.append((mt, mg))

    st.divider()

    sensor_df    = pd.read_csv(SENSOR_PARA)
    sensor_names = list(sensor_df["Name"].values)
    cgm_name     = st.selectbox("CGM 센서", sensor_names)
    cgm_seed     = int(st.number_input("센서 노이즈 시드", 0, 9999, 42))

    # 페르소나 모드는 폼 결과로 인슐린 가능 여부가 결정됨 → 펌프는 항상 노출
    show_pump = (input_mode in ("페르소나 입력", "데이터셋 생성")) or (
        patient_type is not None and patient_factory.is_insulin_capable(patient_type)
    )
    if show_pump:
        pump_df   = pd.read_csv(PUMP_PARA)
        pump_name = st.selectbox("인슐린 펌프", list(pump_df["Name"].values))
    else:
        pump_name = None

    if is_dataset_mode:
        run_btn = False
    else:
        run_btn = st.button("▶ 시뮬레이션 실행", use_container_width=True, type="primary")

# ── 페르소나 입력 폼 (메인 영역) ──────────────────────────────────────────────
persona = None
if input_mode == "페르소나 입력":
    st.subheader("👤 페르소나 입력")
    st.caption("14개 항목을 입력하면 시뮬레이터 파라미터로 변환되어 시뮬레이션이 실행됩니다.")
    tab_basic, tab_treat, tab_life, tab_goal = st.tabs(
        ["기본정보", "치료정보", "생활패턴", "목표"]
    )

    with tab_basic:
        c1, c2 = st.columns(2)
        diabetes_label = c1.radio("당뇨유형", list(PATIENT_TYPE_LABELS.values()), horizontal=True)
        diabetes_type  = next(k for k, v in PATIENT_TYPE_LABELS.items() if v == diabetes_label)
        sex            = c2.radio("성별", ["M", "F"], horizontal=True)

        c3, c4 = st.columns(2)
        birthdate = c3.date_input(
            "생년월일",
            value=datetime(1990, 1, 1).date(),
            min_value=datetime(1925, 1, 1).date(),
            max_value=datetime.now().date(),
        )
        height_cm = c4.number_input("키 (cm)", 100.0, 220.0, 170.0, 0.5)

        weight_kg = st.number_input("몸무게 (kg)", 30.0, 200.0, 70.0, 0.5)
        bmi = weight_kg / (height_cm / 100.0) ** 2
        st.caption(f"BMI ≈ {bmi:.1f}")

    with tab_treat:
        treatment = st.selectbox(
            "치료방법",
            ["insulin", "medication", "diet"],
            format_func=lambda x: {"insulin": "인슐린 주사",
                                    "medication": "약 복용",
                                    "diet": "식이요법"}[x],
        )
        medication_timing = None
        medication = None
        if treatment == "medication":
            c1, c2 = st.columns(2)
            medication_timing = c1.selectbox(
                "복용 시점",
                ["식전", "식후", "식사 직후"],
                help="식사 직후: 위장 부작용 감소 (메트포르민 등)",
            )
            medication = c2.text_input("복용 약품", "메트포르민")

        c3, c4 = st.columns(2)
        diagnosis_years = c3.number_input("진단기간 (년)", 0.0, 60.0, 5.0, 0.5)
        hba1c = c4.number_input(
            "HbA1c (%, 선택)", 0.0, 15.0, 0.0, 0.1,
            help="0이면 미입력으로 간주됩니다.",
        )

    with tab_life:
        activity_label = st.radio(
            "활동수준",
            ["저", "중", "고"],
            horizontal=True,
            help="저: 좌식 위주 · 중: 일상적 활동 · 고: 운동선수/육체노동",
        )
        activity = {"저": "low", "중": "medium", "고": "high"}[activity_label]

        meal_pattern = st.selectbox(
            "식사패턴",
            ["regular_3", "irregular", "frequent"],
            format_func=lambda x: {"regular_3": "규칙적 3식",
                                    "irregular": "불규칙",
                                    "frequent": "잦은 간식"}[x],
        )

    with tab_goal:
        c1, c2 = st.columns(2)
        target_bg  = c1.number_input("목표혈당 (mg/dL)", 70.0, 200.0, 110.0, 1.0)
        fasting_bg = c2.number_input("공복혈당 (mg/dL)", 50.0, 300.0, 100.0, 1.0)

    persona = {
        "diabetes_type":   diabetes_type,
        "birthdate":       birthdate,
        "sex":             sex,
        "height_cm":       height_cm,
        "weight_kg":       weight_kg,
        "treatment":       treatment,
        "medication_timing": medication_timing,
        "medication":      medication,
        "diagnosis_years": diagnosis_years,
        "hba1c":           hba1c if hba1c > 0 else None,
        "fasting_bg":      fasting_bg,
        "activity":        activity,
        "meal_pattern":    meal_pattern,
        "target_bg":       target_bg,
    }

    with st.expander("🔍 페르소나 입력 확인", expanded=False):
        st.json(persona)

elif input_mode == "자연어 입력":
    from simglucose.patient.persona_builder import (
        parse_persona_from_text, _DISPLAY_LABELS, _SOURCE_LABEL, _PERSONA_KEYS,
    )
    st.subheader("💬 자연어 페르소나 입력")
    st.caption("예: '51세 172cm 75kg 축구선수 남성, 5년 전 T2D 진단'")

    nl_text = st.text_area(
        "페르소나를 자연어로 설명하세요",
        height=100,
        placeholder="예: 51세 172cm 75kg 축구선수 남성",
        key="nl_persona_text",
    )

    if st.button("🔍 AI 분석", type="secondary"):
        if not nl_text.strip():
            st.error("내용을 입력해주세요.")
        else:
            with st.spinner("Claude가 파라미터를 추론 중..."):
                try:
                    parsed = parse_persona_from_text(nl_text)
                    st.session_state.parsed_persona = parsed
                except Exception as e:
                    st.error(f"분석 실패: {e}")

    if st.session_state.get("parsed_persona"):
        parsed = st.session_state.parsed_persona

        # 1. AI 추론 결과 요약 (접이식, 기본 닫힘)
        with st.expander("📋 AI 추론 결과 보기", expanded=False):
            rows = [
                {
                    "항목": _DISPLAY_LABELS[k],
                    "값": str(parsed.get(k)) if parsed.get(k) is not None else "-",
                    "출처": _SOURCE_LABEL.get(parsed.get(k + "_source", "default"), "기본값"),
                }
                for k in _PERSONA_KEYS
            ]
            st.dataframe(rows, use_container_width=True, hide_index=True)

        # 2. 편집 폼 — 추론값으로 pre-fill, 모든 항목 수정 가능
        st.markdown("**파라미터 확인 및 수정** — AI 추론값이 자동으로 채워집니다. 필요시 수정 후 시뮬레이션을 실행하세요.")
        persona = render_persona_edit_form(parsed, key_prefix="nl")
    else:
        persona = None

elif input_mode == "데이터셋 생성":
    from simglucose.patient.persona_builder import (
        parse_persona_from_text, _DISPLAY_LABELS, _SOURCE_LABEL, _PERSONA_KEYS,
    )

    st.subheader("📦 데이터셋 생성")
    st.caption(
        f"카테고리당 N명을 시뮬레이션 → CGM 시계열·식사·페르소나를 통합 스키마로 저장. "
        f"설정: 1일/명, 시드 {dataset_seed}, 워커 {dataset_workers}, "
        f"출력 → `{dataset_output_dir}`"
    )

    _all_categories = enumerate_categories()

    tab_nl, tab_grid = st.tabs(["🤖 자연어 기반", "📋 카테고리 커스텀"])

    # ── 탭 1: 자연어 기반 ─────────────────────────────────────────────────
    with tab_nl:
        st.markdown(
            "자연어로 페르소나를 묘사 → AI가 14개 항목 추론 → 어느 카테고리인지 판별 → "
            "그 카테고리에서 N개 시뮬레이션."
        )

        ds_nl_text = st.text_area(
            "페르소나를 자연어로 설명하세요",
            height=80,
            placeholder="예: 51세 172cm 75kg 축구선수 남성, 5년 전 T2D 진단",
            key="ds_nl_text",
        )

        if st.button("🔍 AI 분석", type="secondary", key="ds_nl_analyze"):
            if not ds_nl_text.strip():
                st.error("내용을 입력해주세요.")
            else:
                with st.spinner("Claude가 파라미터를 추론 중..."):
                    try:
                        st.session_state.ds_parsed = parse_persona_from_text(ds_nl_text)
                    except Exception as e:
                        st.error(f"분석 실패: {e}")

        if st.session_state.get("ds_parsed"):
            parsed = st.session_state.ds_parsed

            with st.expander("📋 AI 추론 결과 보기", expanded=False):
                rows = [
                    {
                        "항목": _DISPLAY_LABELS[k],
                        "값":   str(parsed.get(k)) if parsed.get(k) is not None else "-",
                        "출처": _SOURCE_LABEL.get(parsed.get(k + "_source", "default"), "기본값"),
                    }
                    for k in _PERSONA_KEYS
                ]
                st.dataframe(rows, use_container_width=True, hide_index=True)

            # 추론값을 폼에 pre-fill — 사용자가 수정한 최종 값으로 셀이 매핑됨
            st.markdown(
                "**파라미터 확인 및 수정** — AI 추론값이 자동 채움. 값을 바꾸면 아래 카테고리가 즉시 갱신됩니다."
            )
            inferred = render_persona_edit_form(parsed, key_prefix="dsnl")
            category, warnings = persona_to_category(inferred)

            if category is None:
                st.warning(
                    "이 페르소나는 유효한 카테고리에 매핑되지 않습니다. "
                    "값을 카테고리 커스텀 탭에서 직접 골라주세요."
                )
                for w in warnings:
                    st.caption(f"⚠ {w}")
            else:
                desc = category_describe(category)
                st.success(f"식별된 카테고리: **{desc['id']}**")
                c1, c2, c3, c4 = st.columns(4)
                c1.metric("유형", desc["diabetes_type"])
                c2.metric("활동", desc["activity_label"])
                c3.metric("공복혈당", desc["fbg_range"])
                c4.metric("체중", desc["weight_range"])

                ds_nl_count = int(st.number_input(
                    "이 카테고리에서 몇 개의 데이터를 만들까요?",
                    min_value=1, max_value=200, value=10, step=1,
                    key="ds_nl_count",
                ))
                _est_serial = ds_nl_count * 10 / 60
                _est_parallel = _est_serial / max(1, dataset_workers)
                st.caption(
                    f"예상 시간 약 {_est_parallel:.1f}분 "
                    f"(워커 {dataset_workers} · 직렬 기준 {_est_serial:.1f}분)"
                )

                if st.button("▶ 데이터셋 생성", type="primary", key="ds_nl_generate"):
                    plan = [(category, ds_nl_count)]
                    progress = st.progress(0.0, text="시작 중...")
                    status = st.empty()

                    def _on_progress(idx, total, persona, c):
                        progress.progress(idx / total,
                                          text=f"[{idx}/{total}] {category_id(c)}")
                        status.caption(
                            f"  weight={persona['weight_kg']}  fbg={persona['fasting_bg']}"
                        )

                    try:
                        result = generate_from_plan(
                            plan=plan,
                            seed=dataset_seed,
                            progress_callback=_on_progress,
                            n_workers=dataset_workers,
                        )
                        paths = save_dataset(result, dataset_output_dir)
                        st.success(
                            f"✅ {len(result['users_df'])}명 생성 완료 · "
                            f"{len(result['glucose_df']):,} CGM 포인트 · "
                            f"{len(result['meal_df']):,} 식사"
                        )
                        for name, p in paths.items():
                            st.code(p, language="text")
                        if result["failures"]:
                            st.warning(f"{len(result['failures'])}개 실패")
                    except Exception as e:
                        st.error(f"생성 실패: {e}")
                        st.exception(e)

    # ── 탭 2: 카테고리 커스텀 ────────────────────────────────────────────
    with tab_grid:
        st.markdown(
            "**모든 카테고리를 표로 보고 카테고리별로 생성 개수를 직접 정합니다.** "
            "기본값은 0이며, `count` 컬럼만 편집 가능."
        )

        category_data = []
        for c in _all_categories:
            d = category_describe(c)
            category_data.append({
                "id":       d["id"],
                "type":     d["diabetes_type"],
                "activity": d["activity_label"],
                "fbg":      d["fbg_range"],
                "weight":   d["weight_range"],
                "count":    int(st.session_state.get(f"category_count_{d['id']}", 0)),
            })
        category_df = pd.DataFrame(category_data)

        c1, c2, c3 = st.columns([1, 1, 2])
        bulk_n = int(c1.number_input("일괄 설정값", 0, 100, 10, 1, key="bulk_n"))
        if c2.button("모든 카테고리에 적용", key="apply_all"):
            for d in category_data:
                st.session_state[f"category_count_{d['id']}"] = bulk_n
            st.rerun()
        if c3.button("모두 0으로 초기화", key="reset_all"):
            for d in category_data:
                st.session_state[f"category_count_{d['id']}"] = 0
            st.rerun()

        edited = st.data_editor(
            category_df,
            column_config={
                "id":       st.column_config.TextColumn("카테고리 ID", disabled=True),
                "type":     st.column_config.TextColumn("유형", disabled=True),
                "activity": st.column_config.TextColumn("활동", disabled=True),
                "fbg":      st.column_config.TextColumn("공복혈당", disabled=True),
                "weight":   st.column_config.TextColumn("체중", disabled=True),
                "count":    st.column_config.NumberColumn(
                    "생성 개수", min_value=0, max_value=200, step=1, default=0,
                ),
            },
            disabled=["id", "type", "activity", "fbg", "weight"],
            hide_index=True,
            use_container_width=True,
            key="category_grid_editor",
        )

        # session_state에 반영 (다음 rerun에서 보존)
        for _, row in edited.iterrows():
            st.session_state[f"category_count_{row['id']}"] = int(row["count"])

        total_n = int(edited["count"].sum())
        type_counts = edited.groupby("type")["count"].sum()
        nonzero_categories = int((edited["count"] > 0).sum())

        st.divider()
        m1, m2, m3, m4 = st.columns(4)
        m1.metric("선택된 카테고리", f"{nonzero_categories} / {len(category_df)}")
        m2.metric("총 페르소나", total_n)
        m3.metric("예상 시간", f"{total_n * 10 / 60 / max(1, dataset_workers):.1f}분")
        m4.metric("타입 분포", " / ".join(f"{t}:{int(n)}" for t, n in type_counts.items()))

        if st.button("▶ 데이터셋 생성", type="primary", key="ds_grid_generate",
                     disabled=total_n == 0):
            category_lookup = {category_id(c): c for c in _all_categories}
            plan = [
                (category_lookup[row["id"]], int(row["count"]))
                for _, row in edited.iterrows()
                if int(row["count"]) > 0
            ]

            progress = st.progress(0.0, text="시작 중...")
            status = st.empty()

            def _on_progress(idx, total, persona, c):
                progress.progress(idx / total, text=f"[{idx}/{total}] {category_id(c)}")
                status.caption(
                    f"  weight={persona['weight_kg']}  fbg={persona['fasting_bg']}"
                )

            try:
                result = generate_from_plan(
                    plan=plan,
                    seed=dataset_seed,
                    progress_callback=_on_progress,
                    n_workers=dataset_workers,
                )
                paths = save_dataset(result, dataset_output_dir)
                st.success(
                    f"✅ {len(result['users_df'])}명 생성 완료 · "
                    f"{len(result['glucose_df']):,} CGM 포인트 · "
                    f"{len(result['meal_df']):,} 식사"
                )
                for name, p in paths.items():
                    st.code(p, language="text")
                if result["failures"]:
                    st.warning(f"{len(result['failures'])}개 실패")
            except Exception as e:
                st.error(f"생성 실패: {e}")
                st.exception(e)

    persona = None

# ── 세션 상태 초기화 ──────────────────────────────────────────────────────────
if "sim_results" not in st.session_state:
    st.session_state.sim_results = None
if "sim_meta" not in st.session_state:
    st.session_state.sim_meta = {}

# ── 시뮬레이션 실행 ───────────────────────────────────────────────────────────
if run_btn:
    is_persona_mode = input_mode in ("페르소나 입력", "자연어 입력")

    if is_persona_mode and persona is None:
        st.error("페르소나를 입력하고 AI 분석을 완료해주세요." if input_mode == "자연어 입력" else "페르소나 입력 폼을 채워주세요.")
    elif (not is_persona_mode) and not sel_patients:
        st.error("환자를 최소 1명 선택하세요.")
    elif not meals:
        st.error("식사를 최소 1개 이상 설정하세요.")
    else:
        today      = datetime.now().date()
        start_time = datetime.combine(today, datetime.min.time()) + timedelta(hours=start_hour)
        scenario   = CustomScenario(start_time, meals)
        sim_time   = timedelta(hours=sim_hours)

        try:
            if is_persona_mode:
                with st.spinner("페르소나 → 파라미터 변환 후 ODE 시뮬레이션 실행 중..."):
                    eff_type, patient = patient_factory.create_patient_from_persona(persona)
                    label = "persona_custom"
                    res = run_sim_with_patient(
                        patient, eff_type, label, scenario, sim_time,
                        cgm_name, cgm_seed, pump_name,
                    )
                effective_type = eff_type
                meal_scenario_str = str(meals) + " | persona=" + str({
                    k: v for k, v in persona.items()
                    if k in ("diabetes_type", "weight_kg", "fasting_bg",
                             "activity", "diagnosis_years")
                })
            else:
                with st.spinner(
                    f"ODE 시뮬레이션 실행 중 ({patient_type}, {len(sel_patients)}명)..."
                ):
                    res = run_sim(patient_type, sel_patients, scenario, sim_time,
                                  cgm_name, cgm_seed, pump_name)
                effective_type = patient_type
                meal_scenario_str = str(meals)

            st.session_state.sim_results = res
            st.session_state.sim_meta = {
                "patient_type": effective_type,
                "meals": meals,
                "sim_hours": sim_hours,
            }

            run_id = datetime.now().strftime("%Y%m%d_%H%M%S")
            for pname, df in res.items():
                stats = compute_stats(df)
                log_run({
                    "run_id":        run_id,
                    "timestamp":     datetime.now().isoformat(),
                    "patient_name":  pname,
                    "patient_type":  effective_type,
                    "meal_scenario": meal_scenario_str,
                    "sim_hours":     sim_hours,
                    **stats,
                })
            st.success(f"완료! {len(res)}명 시뮬레이션 결과를 확인하세요.")
        except Exception as e:
            st.error(f"오류 발생: {e}")
            st.exception(e)

# ── 결과 표시 ─────────────────────────────────────────────────────────────────
if st.session_state.sim_results:
    res  = st.session_state.sim_results
    meta = st.session_state.sim_meta
    pnames = list(res.keys())

    # 환자별 탭
    tabs = st.tabs(pnames + (["📊 전체 비교"] if len(pnames) > 1 else []))

    for i, pname in enumerate(pnames):
        with tabs[i]:
            df    = res[pname]
            stats = compute_stats(df)
            g, icon = grade(stats["peak_bg"])

            # 요약 카드
            c1, c2, c3, c4, c5, c6 = st.columns(6)
            c1.metric("기저 혈당",     f"{stats['baseline']:.1f}",  "mg/dL")
            c2.metric("최저 혈당",     f"{stats['min_bg']:.1f}",    "mg/dL")
            c3.metric("최고 혈당",     f"{stats['peak_bg']:.1f}",   "mg/dL")
            c4.metric("피크 도달",     f"{stats['t_peak']}",        "분")
            c5.metric("TIR 70–140",  f"{stats['tir_140']:.1f}",   "%")
            c6.metric("TIR 70–180",  f"{stats['tir_180']:.1f}",   "%")

            # 등급 알림
            color_map = {"A": "success", "B": "warning", "C": "warning", "D": "error"}
            getattr(st, color_map[g])(
                f"{icon} 등급 **{g}** — 피크 혈당 {stats['peak_bg']:.1f} mg/dL"
            )

            # 차트
            fig = build_chart({pname: df}, meta["meals"], meta["patient_type"])
            st.plotly_chart(fig, use_container_width=True)

            # 데이터 테이블 / 다운로드
            with st.expander("📋 원시 데이터 보기"):
                st.dataframe(df.reset_index(), use_container_width=True)

            csv_bytes = df.reset_index().to_csv(index=False).encode("utf-8")
            st.download_button(
                f"📥 {pname} CSV 다운로드",
                csv_bytes,
                f"{pname}_{datetime.now().strftime('%Y%m%d_%H%M%S')}.csv",
                "text/csv",
            )

    # 전체 비교 탭
    if len(pnames) > 1:
        with tabs[-1]:
            fig_all = build_chart(res, meta["meals"], meta["patient_type"])
            st.plotly_chart(fig_all, use_container_width=True)

            # 요약 테이블
            summary_rows = []
            for pname in pnames:
                s = compute_stats(res[pname])
                g, icon = grade(s["peak_bg"])
                summary_rows.append({"환자": pname, "등급": f"{icon}{g}",
                                     "기저(mg/dL)": round(s["baseline"], 1),
                                     "최고(mg/dL)": round(s["peak_bg"], 1),
                                     "피크(분)": s["t_peak"],
                                     "TIR70-140(%)": round(s["tir_140"], 1),
                                     "TIR70-180(%)": round(s["tir_180"], 1)})
            st.dataframe(pd.DataFrame(summary_rows), use_container_width=True)

    # 실행 기록
    st.divider()
    st.subheader("📋 실행 기록")
    if os.path.exists(LOG_FILE):
        log_df = pd.read_csv(LOG_FILE)
        st.dataframe(log_df.tail(30), use_container_width=True)
        st.download_button(
            "📥 전체 기록 다운로드",
            log_df.to_csv(index=False).encode("utf-8"),
            "simulation_log.csv",
            "text/csv",
        )
    else:
        st.info("아직 실행 기록이 없습니다. 시뮬레이션을 실행하면 자동 저장됩니다.")

else:
    st.info("← 왼쪽 설정 패널에서 환자와 식사 시나리오를 선택한 뒤 **▶ 시뮬레이션 실행**을 클릭하세요.")
