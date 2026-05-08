"""
T2D Patient Model
Based on: Visentin et al. 2020, "The Padova Type 2 Diabetes Simulator"
Diabetes Technology & Therapeutics, 22(12), 892-903.

ODE equations (A1-A22 from paper Appendix):
- 15 state variables
- Key additions vs T1D: beta-cell insulin secretion, 3-compartment insulin kinetics,
  glucose-dependent hepatic extraction, portal insulin effect on EGP
"""

from .base import Patient
import numpy as np
from scipy.integrate import ode
import pandas as pd
from collections import namedtuple
import logging

from simglucose._paths import cohort_params_path

logger = logging.getLogger(__name__)

Action = namedtuple("patient_action", ["CHO", "insulin"])
Observation = namedtuple("observation", ["Gsub"])

PATIENT_PARA_FILE = cohort_params_path("T2D")


class T2DPatient(Patient):
    SAMPLE_TIME = 1  # min
    EAT_RATE = 5     # g/min CHO

    def __init__(self, params, init_state=None, random_init_bg=False, seed=None, t0=0):
        self._params = params
        self._init_state = init_state
        self.random_init_bg = random_init_bg
        self._seed = seed
        self.t0 = t0
        self.reset()

    @classmethod
    def withID(cls, patient_id, **kwargs):
        patient_params = pd.read_csv(PATIENT_PARA_FILE, index_col=False)
        params = patient_params.iloc[patient_id - 1, :]
        return cls(params, **kwargs)

    @classmethod
    def withName(cls, name, **kwargs):
        patient_params = pd.read_csv(PATIENT_PARA_FILE, index_col=False)
        params = patient_params.loc[patient_params.Name == name].squeeze()
        return cls(params, **kwargs)

    @property
    def state(self):
        return self._odesolver.y

    @property
    def t(self):
        return self._odesolver.t

    @property
    def sample_time(self):
        return self.SAMPLE_TIME

    def step(self, action):
        # 식사량을 EAT_RATE(5g/min)로 나눠서 소화
        to_eat = self._announce_meal(action.CHO)
        action = action._replace(CHO=to_eat)

        if action.CHO > 0 and self._last_action.CHO <= 0:
            logger.info("t = {}, patient starts eating ...".format(self.t))
            self._last_Qsto = self.state[0] + self.state[1]
            self._last_foodtaken = 0
            self.is_eating = True

        if to_eat > 0:
            logger.debug("t = {}, patient eats {} g".format(self.t, action.CHO))

        if self.is_eating:
            self._last_foodtaken += action.CHO

        if action.CHO <= 0 and self._last_action.CHO > 0:
            logger.info("t = {}, Patient finishes eating!".format(self.t))
            self.is_eating = False

        self._last_action = action

        self._odesolver.set_f_params(
            action, self._params, self._last_Qsto, self._last_foodtaken
        )
        if self._odesolver.successful():
            self._odesolver.integrate(self._odesolver.t + self.sample_time)
        else:
            logger.error("ODE solver failed!!")
            raise RuntimeError("ODE solver failed")

    @staticmethod
    def model(t, x, action, params, last_Qsto, last_foodtaken):
        """
        T2D glucose-insulin ODE model.
        Equations A1-A22 from Visentin et al. 2020.

        State variables (15):
          x[0]  Qsto1  위장 고체 포도당 (mg)
          x[1]  Qsto2  위장 액체 포도당 (mg)
          x[2]  Qgut   소장 포도당 (mg)
          x[3]  Gp     혈장 포도당 (mg/kg)
          x[4]  Gt     조직 포도당 (mg/kg)
          x[5]  Il     간 인슐린 (pmol/kg)
          x[6]  Ip     혈장 인슐린 (pmol/kg)
          x[7]  Iev    혈관외 인슐린 (pmol/kg)
          x[8]  X      인슐린의 포도당 이용 작용 (pmol/L)
          x[9]  Iprime 지연 인슐린 신호 (pmol/L)
          x[10] XL     지연 인슐린 작용 (pmol/L)
          x[11] ISRs   베타세포 정적 분비 (pmol/min)
          x[12] CP1    C-펩타이드 구획1 (pmol/L)
          x[13] CP2    C-펩타이드 구획2 (pmol/L)
          x[14] Gsub   피하 포도당 (mg/kg)
        """
        dxdt = np.zeros(15)

        d    = action.CHO * 1000        # g -> mg (식사량)
        BW   = params.BW
        Gb   = params.Gb                # 기저 혈당 (mg/dL)
        Ib   = params.Ib                # 기저 인슐린 (pmol/L)

        # ── 편의 변수 ──────────────────────────────────────────────
        # 혈장 인슐린 농도 (pmol/L)
        I = x[6] / params.VI
        # 혈장 포도당 농도 (mg/dL)
        G = x[3] / params.VG

        # ── A5-A8: 음식 흡수 (Rate of glucose appearance) ──────────
        Qsto = x[0] + x[1]
        Dose = last_Qsto + last_foodtaken * 1000  # mg

        if Dose > 0:
            aa = 5.0 / (2.0 * Dose * (1.0 - params.b))
            bb = 5.0 / (2.0 * Dose * params.d)
            kempt = params.kmin + (params.kmax - params.kmin) / 2.0 * (
                np.tanh(aa * (Qsto - params.b * Dose))
                - np.tanh(bb * (Qsto - params.d * Dose))
                + 2.0
            )
        else:
            kempt = params.kmax

        dxdt[0] = -params.kmax * x[0] + d           # Qsto1 (A5)
        dxdt[1] = params.kmax * x[0] - kempt * x[1] # Qsto2 (A5)
        dxdt[2] = kempt * x[1] - params.kabs * x[2] # Qgut  (A5)

        Rameal = params.f * params.kabs * x[2] / BW  # mg/kg/min (A5)

        # ── A9-A11: 간 포도당 생산 (EGP) ──────────────────────────
        # I' (지연 인슐린 신호)
        dxdt[9]  = -params.ki * (x[9]  - I)          # I'  (A11)
        # XL (지연 인슐린 작용)
        dxdt[10] = -params.ki * (x[10] - x[9])        # XL  (A10)

        # kp1: CSV에서 직접 로드 (EGPb + kp2*Gpb + kp3*Ib + kp4*Ilb로 사전 계산)
        EGP = params.kp1 - params.kp2 * x[3] - params.kp3 * x[10] - params.kp4 * x[5]
        EGP = max(EGP, 0.0)           # 음수 방지 (A9)

        # ── A12-A16: 포도당 이용 (Glucose utilization) ─────────────
        Uii = params.Fcns             # 인슐린 독립적 이용 (A12)

        # 위험 함수 (저혈당 시 인슐린 감수성 역설적 증가, A15-A16)
        r1, r2 = 0.1772, 0.1231      # Dalla Man et al. 2014
        Gth = 60.0                    # 저혈당 임계값 (mg/dL)
        if G >= Gb:
            risk = 0.0
        elif G >= Gth:
            risk = 10.0 * (np.log(G) ** r2 - np.log(Gb) ** r2) ** 2
        else:
            risk = 10.0 * (np.log(Gth) ** r2 - np.log(Gb) ** r2) ** 2

        # 인슐린 작용 (X, A14)
        dxdt[8] = -params.p2U * x[8] + params.p2U * (I - Ib)

        Vm = params.Vmx * x[8] * (1.0 + r1 * risk)
        Uid = Vm * x[4] / (params.Km0 + x[4])        # Michaelis-Menten (A13)

        # ── A17: 신장 배출 ─────────────────────────────────────────
        if x[3] > params.ke2:
            E = params.ke1 * (x[3] - params.ke2)
        else:
            E = 0.0

        # ── A1: 포도당 동역학 ──────────────────────────────────────
        dxdt[3] = EGP + Rameal - Uii - E - params.k1 * x[3] + params.k2 * x[4]
        dxdt[3] = (x[3] >= 0) * dxdt[3]

        dxdt[4] = -Uid + params.k1 * x[3] - params.k2 * x[4]
        dxdt[4] = (x[4] >= 0) * dxdt[4]

        # ── A19-A22: 베타세포 인슐린 분비 (ISR) ────────────────────
        # 정적 분비 (ISRs): 혈당에 비례 (A20)
        # Fs, Fd는 논문 Table A1에서 10^-9 단위로 표기 → 1e-9 곱해야 함
        dxdt[11] = -params.alpha * x[11] + params.VC * params.Fs * 1e-9 * max(G - params.h, 0.0)

        # 동적 분비 (ISRd): 혈당 변화율에 비례 (A21)
        Gdot = dxdt[3] / params.VG   # dG/dt (mg/dL/min 근사)
        ISRd = params.VC * params.Fd * 1e-9 * Gdot if Gdot >= 0 else 0.0

        # 기저 분비 (ISRb, A22): 바살 C-펩타이드 초기값(x0_13) 사용
        ISRb = params.CPb * params.k01 * params.VC

        ISR = x[11] + ISRd + ISRb    # 총 분비율 (pmol/min, A19)
        ISR = max(ISR, 0.0)

        # ── A2-A4: 인슐린 동역학 (3구획) ──────────────────────────
        # 간 인슐린 추출률 HE (혈당에 따라 변함, A4)
        # HEb=0.6 (문헌 기준값), a0G = HEb + aG*Gb (steady-state에서 유도)
        HEb = 0.6
        a0G = HEb + params.aG * Gb
        HE = -params.aG * G + a0G
        HE = min(max(HE, 0.0), 0.9)  # 0~0.9 범위 제한

        m3 = HE * params.m1 / (1.0 - HE)

        # 외부 인슐린 주입 (피하, T2D에서도 인슐린 치료 가능)
        insulin_injection = action.insulin * 6000.0 / BW  # U/min -> pmol/kg/min

        dxdt[5] = -(params.m1 + m3) * x[5] + params.m2 * x[6] + ISR / BW  # Il (A2)
        dxdt[5] = (x[5] >= 0) * dxdt[5]

        dxdt[6] = (-(params.m2 + params.m4 + params.m5) * x[6]
                   + params.m1 * x[5]
                   + params.m6 * x[7]
                   + insulin_injection)                                       # Ip (A2)
        dxdt[6] = (x[6] >= 0) * dxdt[6]

        dxdt[7] = -params.m6 * x[7] + params.m5 * x[6]                     # Iev (A2)
        dxdt[7] = (x[7] >= 0) * dxdt[7]

        # ── A18: C-펩타이드 동역학 ──────────────────────────────────
        dxdt[12] = -(params.k01 + params.k21) * x[12] + params.k12 * x[13] + ISR / params.VC
        dxdt[13] = -params.k12 * x[13] + params.k21 * x[12]

        # ── 피하 포도당 (CGM 측정용) ────────────────────────────────
        # T1D의 ksc 대신 고정 속도 상수 사용
        ksc = 0.05  # min^-1
        dxdt[14] = -ksc * x[14] + ksc * x[3]
        dxdt[14] = (x[14] >= 0) * dxdt[14]

        return dxdt

    @property
    def observation(self):
        Gsub = self.state[14] / self._params.VG  # mg/dL
        return Observation(Gsub=Gsub)

    def _announce_meal(self, meal):
        self.planned_meal += meal
        if self.planned_meal > 0:
            to_eat = min(self.EAT_RATE, self.planned_meal)
            self.planned_meal -= to_eat
            self.planned_meal = max(0, self.planned_meal)
        else:
            to_eat = 0
        return to_eat

    @property
    def seed(self):
        return self._seed

    @seed.setter
    def seed(self, seed):
        self._seed = seed
        self.reset()

    def reset(self):
        # CSV의 x0_1 ~ x0_15 컬럼에서 초기 상태 로드
        if self._init_state is None:
            cols = [f"x0_{i+1}" for i in range(15)]
            self.init_state = np.array([float(self._params[c]) for c in cols], dtype=float)
        else:
            self.init_state = np.array(self._init_state, dtype=float)

        # ISRb 계산용 기저 C-펩타이드 (x0_13 = CP1 초기값)
        self._params = self._params.copy()
        self._params['CPb'] = self.init_state[12]

        self.random_state = np.random.RandomState(self._seed)
        if self.random_init_bg:
            # 혈당 관련 상태(Gp=x[3], Gt=x[4], Gsub=x[14])만 랜덤화
            for idx in [3, 4, 14]:
                self.init_state[idx] = self.random_state.normal(
                    self.init_state[idx],
                    0.1 * self.init_state[idx]
                )
                self.init_state[idx] = max(self.init_state[idx], 0.0)

        self._last_Qsto = self.init_state[0] + self.init_state[1]
        self._last_foodtaken = 0
        self.name = self._params.Name

        self._odesolver = ode(self.model).set_integrator("dopri5")
        self._odesolver.set_initial_value(self.init_state, self.t0)

        self._last_action = Action(CHO=0, insulin=0)
        self.is_eating = False
        self.planned_meal = 0
