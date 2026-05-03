"""Run an ODE simulation given a persona + meal scenario.

Extracted from app.py:run_sim_with_patient — same logic, no Streamlit dependency.
"""

import copy
from datetime import datetime, timedelta

import pandas as pd

from simglucose.actuator.pump import InsulinPump
from simglucose.controller.basal_bolus_ctrller import BBController
from simglucose.patient import factory as patient_factory
from simglucose.sensor.cgm import CGMSensor
from simglucose.simulation.env import T1DSimEnv
from simglucose.simulation.scenario import CustomScenario


_DEFAULT_CGM = "Dexcom"
_DEFAULT_PUMP = "Insulet"


def simulate(persona, meal_events, sim_hours, cgm_seed,
             cgm_name=_DEFAULT_CGM, pump_name=_DEFAULT_PUMP,
             start_time=None):
    """Run one simulation for one persona.

    Args:
        persona: 14-item persona dict (see persona_builder)
        meal_events: list of (datetime, carbs_g) tuples
        sim_hours: simulation duration in hours
        cgm_seed: seed for CGM noise
        cgm_name: sensor model
        pump_name: pump model (used for T1D/T2D only)
        start_time: simulation start datetime; if None, derived from first meal

    Returns:
        DataFrame indexed by Time with columns [BG, CGM, CHO]
    """
    if start_time is None:
        if meal_events:
            first_meal = meal_events[0][0]
            start_time = datetime(first_meal.year, first_meal.month,
                                  first_meal.day, 0, 0)
        else:
            start_time = datetime.now().replace(hour=0, minute=0, second=0,
                                                microsecond=0)

    scenario = CustomScenario(start_time=start_time, scenario=meal_events)
    sim_time = timedelta(hours=sim_hours)

    patient_type, patient = patient_factory.create_patient_from_persona(persona)

    if patient_factory.is_insulin_capable(patient_type):
        return _simulate_insulin(patient, scenario, sim_time,
                                 cgm_name, cgm_seed, pump_name)
    return _simulate_meal_only(patient, patient_type, scenario, sim_time,
                               cgm_name, cgm_seed)


def _simulate_insulin(patient, scenario, sim_time,
                      cgm_name, cgm_seed, pump_name):
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

    return env.show_history()


def _simulate_meal_only(patient, patient_type, scenario, sim_time,
                        cgm_name, cgm_seed):
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

    return pd.DataFrame(
        {"BG": BG_hist, "CGM": CGM_hist, "CHO": CHO_hist},
        index=pd.DatetimeIndex(time_hist, name="Time"),
    )
