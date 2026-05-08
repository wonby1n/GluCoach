"""
Normal (Healthy, Non-Diabetic) Patient Simulator
=================================================
Based on: Dalla Man, Rizza, Cobelli (2007) — IEEE TBME 54(10):1740-1749
Reference: MathWorks SimBiology insulindemo (Rules 1-35)

Key physiological features:
  - Dynamic hepatic extraction  HE = clip(-m5·S + m6, 0, 1)
  - 1st-phase beta-cell secretion gated by  (dG/dt > 0) AND (G > Gb)
  - Y (2nd-phase) dynamics handle sub-basal glucose smoothly
  - Subcutaneous glucose state x[12] for realistic CGM measurements
  - Steady-state initial conditions derived at runtime (always consistent)

State vector (13 elements):
  x[0]  Qsto1    stomach solid          (mg)
  x[1]  Qsto2    stomach liquid         (mg)
  x[2]  Qgut     gut glucose            (mg)
  x[3]  Gp       plasma glucose         (mg/kg)
  x[4]  Gt       tissue glucose         (mg/kg)
  x[5]  Ip       plasma insulin         (pmol/kg)
  x[6]  X        interstitial insulin   (pmol/L)
  x[7]  Id1      insulin delay 1        (pmol/L)
  x[8]  Id2      insulin delay 2        (pmol/L)
  x[9]  Il       liver insulin          (pmol/kg)
  x[10] Ipo      portal insulin         (pmol/kg)
  x[11] Y        delayed glucose signal (pmol/kg/min)
  x[12] Gsc      subcutaneous glucose   (mg/kg)
"""

import numpy as np
from scipy.integrate import ode
import pandas as pd
from collections import namedtuple
import logging

from simglucose._paths import cohort_params_path
from simglucose.patient.base import Patient

logger = logging.getLogger(__name__)

Action      = namedtuple("patient_action", ["CHO"])
Observation = namedtuple("observation",    ["Gsub"])

PATIENT_PARA_FILE = cohort_params_path("Normal")


# ── 정상상태 초기값 유도 ──────────────────────────────────────────────────────
def _compute_initial_state(p):
    """
    Derive basal steady-state x0 (13 elements) directly from ODE mass balance.
    완전한 steady state 보장 (dx/dt = 0 at t=0).

    접근:
      - Ip = Ib·Vi  (정의: Ib는 기저 혈장 인슐린 농도)
      - Il = (m2+m4)/m1 · Ip    (dIp/dt = 0)
      - Sb = (m6-HEb)/m5        (Rule 7: HE(t=0) = HEb)
      - Ipo = Sb/gamma          (dIpo/dt = 0)
      - X = 0                    (I = Ib 이므로 X = I - Ib = 0)
      - Id1 = Id2 = Ib          (인슐린 지연 체인 평형)
      - Gp = Gb·Vg              (정의)
      - EGPb = kp1 - kp2·Gp - kp3·Ib - kp4·Ipo
      - Gt = (Fsnc - EGPb + k1·Gp)/k2   (dGp/dt = 0)
      - Gsc = Gp                (dGsc/dt = 0)
    """
    Gb, Ib   = p["Gb"],  p["Ib"]
    Vg, Vi   = p["Vg"],  p["Vi"]
    k1, k2   = p["k1"],  p["k2"]
    kp1, kp2 = p["kp1"], p["kp2"]
    kp3, kp4 = p["kp3"], p["kp4"]
    m1, m2, m4 = p["m1"], p["m2"], p["m4"]
    m5, m6   = p["m5"],  p["m6"]
    HEb      = p["HEb"]
    Fsnc     = p["Fsnc"]
    gamma    = p["gamma"]

    PlasmaGlu = Gb * Vg
    PlasmaIns = Ib * Vi                                     # 직접 정의
    LiverIns  = (m2 + m4) / m1 * PlasmaIns                  # dIp/dt = 0
    Sb        = (m6 - HEb) / m5                             # Rule 7
    PortalIns = Sb / gamma                                  # dIpo/dt = 0
    EGPb      = kp1 - kp2 * PlasmaGlu - kp3 * Ib - kp4 * PortalIns
    TissueGlu = (Fsnc - EGPb + k1 * PlasmaGlu) / k2         # dGp/dt = 0

    x0 = np.zeros(13)
    x0[3]  = PlasmaGlu
    x0[4]  = TissueGlu
    x0[5]  = PlasmaIns
    x0[6]  = 0.0                                             # X = I - Ib = 0
    x0[7]  = Ib
    x0[8]  = Ib
    x0[9]  = LiverIns
    x0[10] = PortalIns
    # x[11] (Y) = 0 at basal
    x0[12] = PlasmaGlu                                       # Gsc = Gp at basal
    return x0


class NormalPatient(Patient):
    SAMPLE_TIME = 1   # min
    EAT_RATE    = 5   # g/min CHO

    def __init__(self, params, t0=0):
        self._params = params
        self.t0      = t0
        self.reset()

    # ── 생성자 ───────────────────────────────────────────────────────────────
    @classmethod
    def withName(cls, name, **kwargs):
        df = pd.read_csv(PATIENT_PARA_FILE)
        params = df.loc[df.Name == name].squeeze()
        if params.empty:
            raise ValueError(f"Patient '{name}' not found in {PATIENT_PARA_FILE}")
        return cls(params, **kwargs)

    @classmethod
    def withID(cls, patient_id, **kwargs):
        df = pd.read_csv(PATIENT_PARA_FILE)
        params = df.iloc[patient_id - 1]
        return cls(params, **kwargs)

    @property
    def name(self):
        return self._params["Name"]

    # ── ODE 모델 (Dalla Man 2007 + Rules 20-35) ──────────────────────────────
    @staticmethod
    def model(t, x, p, last_Qsto, last_foodtaken):
        dxdt = np.zeros(13)

        # parameters
        Vg   = p["Vg"];   Vi   = p["Vi"]
        k1   = p["k1"];   k2   = p["k2"]
        kmax = p["kmax"]; kmin = p["kmin"]; kabs = p["kabs"]
        f    = p["f"];    b    = p["b"];    d    = p["d"];   BW = p["BW"]
        kp1  = p["kp1"];  kp2  = p["kp2"];  kp3  = p["kp3"]; kp4 = p["kp4"]
        ki   = p["ki"];   Fsnc = p["Fsnc"]
        Vm0  = p["Vm0"];  Vmx  = p["Vmx"];  Km0  = p["Km0"]; p2u = p["p2u"]
        ke1  = p["ke1"];  ke2  = p["ke2"]
        m1v  = p["m1"];   m2   = p["m2"];   m4   = p["m4"]
        m5   = p["m5"];   m6   = p["m6"];   HEb  = p["HEb"]
        K    = p["K"];    alpha= p["alpha"];beta = p["beta"]; gamma = p["gamma"]
        Gb   = p["Gb"];   Ib   = p["Ib"];   ksc  = p["ksc"]

        # state
        QSto1 = x[0]; QSto2 = x[1]; Qgut = x[2]
        Gp = x[3]; Gt = x[4]; Ip = x[5]; X = x[6]
        Id1 = x[7]; Id2 = x[8]; Il = x[9]; Ipo = x[10]; Y = x[11]; Gsc = x[12]

        G = Gp / Vg                           # plasma glucose conc. (mg/dL)
        I = Ip / Vi                           # plasma insulin conc. (pmol/L)

        # ── 위 배출 (Rule 22) ─────────────────────────────────────────────
        Qsto = QSto1 + QSto2
        Dbar = last_Qsto + last_foodtaken * 1000.0
        if Dbar > 0:
            aa    = 5.0 / (2.0 * Dbar * (1.0 - b))
            cc    = 5.0 / (2.0 * Dbar * d)
            kempt = kmin + (kmax - kmin) / 2.0 * (
                np.tanh(aa * (Qsto - b * Dbar))
                - np.tanh(cc * (Qsto - d * Dbar)) + 2.0
            )
        else:
            kempt = kmax

        dxdt[0] = -kmax  * QSto1
        dxdt[1] =  kmax  * QSto1 - kempt * QSto2
        dxdt[2] =  kempt * QSto2 - kabs  * Qgut

        # ── 포도당 동역학 ────────────────────────────────────────────────
        Ra  = f * kabs * Qgut / BW                                # glucose appearance
        EGP = max(kp1 - kp2 * Gp - kp3 * Id2 - kp4 * Ipo, 0.0)    # Rule 21
        E   = ke1 * (Gp - ke2) if Gp > ke2 else 0.0               # renal excretion
        Vm  = Vm0 + Vmx * X
        Uid = Vm * Gt / (Km0 + Gt)
        Uii = Fsnc

        dxdt[3] = EGP + Ra - Uii - E - k1 * Gp + k2 * Gt          # plasma glucose
        dxdt[4] = k1 * Gp - k2 * Gt - Uid                          # tissue glucose

        # ── 베타세포 분비 (Rules 28-29, 34) ──────────────────────────────
        GluConcRate    = dxdt[3] / Vg                              # dG/dt (mg/dL/min)
        Sb             = (m6 - HEb) / m5                           # basal secretion
        ins_prod_mode  = (GluConcRate > 0) and (G > Gb)            # 1상 조건: 상승 AND 기저 위
        delayed_glu_ok = beta * (G - Gb) >= -Sb                    # Y 목표값 물리적 유효

        InsProd = max(
            Y + Sb + (K * GluConcRate if ins_prod_mode else 0.0),
            0.0,
        )

        # Y (지연된 2상 신호) 동역학
        dxdt[11] = (
            -alpha * (Y - beta * (G - Gb))
            if delayed_glu_ok
            else -alpha * Y - alpha * Sb
        )

        # ── 인슐린 kinetics (동적 간 추출) ───────────────────────────────
        InsSecr = gamma * Ipo
        HE_val  = np.clip(-m5 * InsSecr + m6, 0.0, 1.0)            # Rule 30 (dynamic HE)
        m3      = HE_val * m1v / (1.0 - HE_val)                    # Rule 31

        dxdt[10] = InsProd - InsSecr                               # Ipo
        dxdt[9]  = -(m1v + m3) * Il + m2 * Ip + InsSecr            # Il
        dxdt[5]  = m1v * Il - (m2 + m4) * Ip                       # Ip
        dxdt[6]  = -p2u * X + p2u * (I - Ib)                       # X
        dxdt[7]  = ki * (I   - Id1)                                # Id1
        dxdt[8]  = ki * (Id1 - Id2)                                # Id2

        # ── 피하 포도당 (CGM 신호용) ─────────────────────────────────────
        dxdt[12] = -ksc * Gsc + ksc * Gp

        return dxdt

    @staticmethod
    def _model_with_dose(t, x, p, last_Qsto, last_foodtaken, dose_rate):
        """Wrap model() to add meal input (mg/min) to stomach solid."""
        dxdt    = NormalPatient.model(t, x, p, last_Qsto, last_foodtaken)
        dxdt[0] += dose_rate
        return dxdt

    # ── 시뮬레이션 단계 ──────────────────────────────────────────────────────
    def step(self, action):
        to_eat = self._announce_meal(action.CHO)
        action = action._replace(CHO=to_eat)

        if action.CHO > 0 and self._last_action.CHO <= 0:
            logger.info("t = {}, patient starts eating ...".format(self.t))
            self._last_Qsto      = self.state[0] + self.state[1]
            self._last_foodtaken = 0.0
            self.is_eating       = True

        if self.is_eating:
            self._last_foodtaken += action.CHO

        if action.CHO <= 0 and self._last_action.CHO > 0:
            logger.info("t = {}, Patient finishes eating!".format(self.t))
            self.is_eating = False

        self._last_action = action

        self._odesolver.set_f_params(
            self._params,
            self._last_Qsto,
            self._last_foodtaken,
            action.CHO * 1000.0,   # g/min → mg/min
        )
        if self._odesolver.successful():
            self._odesolver.integrate(self._odesolver.t + self.SAMPLE_TIME)
        else:
            raise RuntimeError("ODE solver failed")

    # ── 관측 인터페이스 ──────────────────────────────────────────────────────
    @property
    def state(self):
        return self._odesolver.y

    @property
    def t(self):
        return self._odesolver.t

    @property
    def sample_time(self):
        return self.SAMPLE_TIME

    @property
    def observation(self):
        """Subcutaneous glucose (mg/dL) — CGM input."""
        Gsub = self.state[12] / self._params["Vg"]
        return Observation(Gsub=Gsub)

    def _announce_meal(self, meal):
        self.planned_meal += meal
        if self.planned_meal > 0:
            to_eat            = min(self.EAT_RATE, self.planned_meal)
            self.planned_meal -= to_eat
            self.planned_meal  = max(0.0, self.planned_meal)
        else:
            to_eat = 0.0
        return to_eat

    # ── 리셋 ────────────────────────────────────────────────────────────────
    def reset(self):
        self.init_state      = _compute_initial_state(self._params)
        self._last_Qsto      = 0.0
        self._last_foodtaken = 0.0
        self.is_eating       = False
        self.planned_meal    = 0.0
        self._last_action    = Action(CHO=0)

        self._odesolver = ode(self._model_with_dose).set_integrator(
            "dopri5", nsteps=5000, rtol=1e-6, atol=1e-8,
        )
        self._odesolver.set_initial_value(self.init_state, self.t0)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)

    p = NormalPatient.withName("normal_adult")
    t, CHO, BG = [], [], []
    while p.t < 500:
        carb = 80 if p.t == 100 else 0
        act  = Action(CHO=carb)
        t.append(p.t)
        CHO.append(carb)
        BG.append(p.observation.Gsub)
        p.step(act)

    import matplotlib.pyplot as plt
    fig, ax = plt.subplots(2, sharex=True)
    ax[0].plot(t, BG);  ax[0].set_ylabel("BG (mg/dL)"); ax[0].grid()
    ax[1].plot(t, CHO); ax[1].set_ylabel("CHO (g)");    ax[1].grid()
    plt.show()
