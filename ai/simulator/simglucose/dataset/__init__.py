"""Synthetic dataset generation for ML pipeline cold-start.

Modules:
  persona_sampler   — Sample diverse personas from realistic distributions
  scenario_sampler  — Sample meal/activity scenarios per persona
  simulator_runner  — Run ODE simulation given persona + scenario
  unified_schema    — Convert simulator output to unified ML-ready schema

Entry point: ``generate_dataset.py`` at project root.
"""

from .persona_sampler import (
    sample_persona,
    enumerate_categories,
    category_id,
    category_describe,
    persona_to_category,
)
from .scenario_sampler import sample_scenario
from .simulator_runner import simulate
from .unified_schema import to_glucose_readings, to_meal_events, to_user_row
from .orchestrator import generate_from_plan, save_dataset, save_dataset_per_category

__all__ = [
    "sample_persona",
    "enumerate_categories",
    "category_id",
    "category_describe",
    "persona_to_category",
    "sample_scenario",
    "simulate",
    "to_glucose_readings",
    "to_meal_events",
    "to_user_row",
    "generate_from_plan",
    "save_dataset",
    "save_dataset_per_category",
]
