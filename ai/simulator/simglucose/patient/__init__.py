"""Patient package — re-exports for convenient and backward-compatible imports.

Preferred import (forward):
    from simglucose.patient import T1DPatient, NormalPatient, T2DPatient
    from simglucose.patient import create_patient, available_types

Legacy module paths (t1dpatient, normal_patient, patient_factory) are still
supported via compatibility aliases below so existing callers keep working.
"""

from .t1d import T1DPatient
from .normal import NormalPatient
from .t2d import T2DPatient
from .factory import (
    create_patient,
    create_patient_by_id,
    available_types,
    is_insulin_capable,
    get_action_type,
    get_params_path,
)

# ── Backward-compatibility aliases ────────────────────────────────────────
# Old import paths remain functional:
#   from simglucose.patient.t1dpatient   import T1DPatient
#   from simglucose.patient.normal_patient import NormalPatient
#   from simglucose.patient.patient_factory import create_patient
import sys as _sys

from . import t1d as _t1d
from . import normal as _normal
from . import factory as _factory

_sys.modules[__name__ + ".t1dpatient"]       = _t1d
_sys.modules[__name__ + ".normal_patient"]   = _normal
_sys.modules[__name__ + ".patient_factory"]  = _factory

__all__ = [
    "T1DPatient", "NormalPatient", "T2DPatient",
    "create_patient", "create_patient_by_id",
    "available_types", "is_insulin_capable",
    "get_action_type", "get_params_path",
]
