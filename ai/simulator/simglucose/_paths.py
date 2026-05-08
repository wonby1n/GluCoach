"""Centralized path resolution for project-owned cohort CSVs.

These files moved out of `simglucose/params/` (vendor) to project-root
`params/` (this project's assets). All other modules should call
``cohort_params_path(patient_type)`` instead of hardcoding paths.

See ``params/README.md`` for ownership rationale.
"""

from pathlib import Path


# This file lives at <project_root>/simglucose/_paths.py
# Project root = parent of the "simglucose" package directory.
PROJECT_ROOT = Path(__file__).resolve().parent.parent
COHORT_PARAMS_DIR = PROJECT_ROOT / "params"


def cohort_params_path(patient_type: str) -> str:
    """Filesystem path to the cohort CSV for a patient type.

    patient_type is case-insensitive: "T1D" → params/t1d.csv,
    "T2D" → params/t2d.csv, "Normal" → params/normal.csv.
    """
    fname = f"{patient_type.lower()}.csv"
    path = COHORT_PARAMS_DIR / fname
    return str(path)
