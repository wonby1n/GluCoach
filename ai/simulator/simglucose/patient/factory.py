"""Patient factory — central registry for all patient model types.

Adding a new patient type should be a 2-line change:
  1. Import the class
  2. Add an entry to _REGISTRY (and _INSULIN_CAPABLE if it takes insulin)

All downstream code (user_interface, app, etc.) dispatches through this
factory, so new types become available everywhere automatically.
"""

import sys

from .t1d import T1DPatient
from .normal import NormalPatient
from .t2d import T2DPatient


_REGISTRY = {
    "T1D":    T1DPatient,
    "Normal": NormalPatient,
    "T2D":    T2DPatient,
}

# Types whose Action tuple includes an insulin input.
# Drives UI branching: insulin-capable types use the pump+controller flow,
# others use the meal-only flow.
_INSULIN_CAPABLE = {"T1D", "T2D"}


def create_patient(patient_type, name, **kwargs):
    """Create a patient by type string and name."""
    _check_type(patient_type)
    return _REGISTRY[patient_type].withName(name, **kwargs)


def create_patient_by_id(patient_type, patient_id, **kwargs):
    """Create a patient by type string and integer ID."""
    _check_type(patient_type)
    return _REGISTRY[patient_type].withID(patient_id, **kwargs)


def create_patient_from_persona(persona, **kwargs):
    """Create a patient from a 14-item persona dict.

    The patient_type is selected from persona["diabetes_type"] and the
    parameter Series is built by persona_builder.build_params_from_persona.
    """
    from .persona_builder import build_params_from_persona
    patient_type, params = build_params_from_persona(persona)
    _check_type(patient_type)
    return patient_type, _REGISTRY[patient_type](params, **kwargs)


def available_types():
    """List of currently registered patient types."""
    return list(_REGISTRY.keys())


def is_insulin_capable(patient_type):
    """True if this type accepts an insulin input."""
    _check_type(patient_type)
    return patient_type in _INSULIN_CAPABLE


def get_params_path(patient_type):
    """Path to the params CSV for this type (PATIENT_PARA_FILE in the module)."""
    _check_type(patient_type)
    module = sys.modules[_REGISTRY[patient_type].__module__]
    return module.PATIENT_PARA_FILE


def get_action_type(patient_type):
    """Return the Action namedtuple class for this type.

    T1D / T2D → Action(CHO, insulin)
    Normal    → Action(CHO)
    """
    _check_type(patient_type)
    module = sys.modules[_REGISTRY[patient_type].__module__]
    return module.Action


def _check_type(patient_type):
    if patient_type not in _REGISTRY:
        raise ValueError(
            f"Unknown patient type '{patient_type}'. "
            f"Available: {list(_REGISTRY.keys())}"
        )
