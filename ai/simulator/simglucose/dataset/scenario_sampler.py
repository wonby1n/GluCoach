"""Sample 1-day meal scenarios for synthetic users.

A scenario is a list of (datetime, carbs_g) tuples covering exactly one day,
deterministically shaped by the persona's `meal_pattern` category.

11 meal_pattern categories (강도 A — 결정론적 끼니 구성, 시간 jitter만 변동):
    regular_3              B+L+D, ±30분 jitter
    irregular              B+L+D, ±2시간 jitter
    frequent_small         B+L+D + 간식 2~3회, mean × 0.7
    skip_breakfast         L+D
    skip_lunch             B+D
    skip_dinner            B+L
    skip_breakfast_lunch   D만 (OMAD 저녁형)
    skip_breakfast_dinner  L만 (낮 한 끼)
    skip_lunch_dinner      B만
    fasting_day            0끼 (종일 단식)
    late_dinner            B+L+D, 저녁만 22시로 시프트
"""

from datetime import datetime, timedelta


_DEFAULT_TIMES = {
    "breakfast": (8, 0),
    "lunch":     (12, 0),
    "dinner":    (19, 0),
    "snack":     (15, 0),
}

_MEAL_CARB_DIST = {
    "breakfast": {"mean": 50.0, "std": 15.0, "low": 25.0, "high": 100.0},
    "lunch":     {"mean": 60.0, "std": 15.0, "low": 25.0, "high": 100.0},
    "dinner":    {"mean": 65.0, "std": 18.0, "low": 25.0, "high": 100.0},
    "snack":     {"mean": 20.0, "std": 5.0,  "low": 10.0, "high": 40.0},
}


def _entry(meals, jitter=30, carb_mult=1.0, snacks=0,
           late_dinner=False):
    """Build one _PATTERN_CONFIG row.

    meals: dict {breakfast: bool, lunch: bool, dinner: bool}
    jitter: minutes (uniform ±jitter applied to each meal time)
    carb_mult: multiplier on the truncated-normal mean for all main meals
    snacks: number of snack events (deterministic, not probabilistic)
    late_dinner: if True, dinner shifts to 22:00
    """
    return {
        "meals":       meals,
        "jitter":      jitter,
        "carb_mult":   carb_mult,
        "snacks":      snacks,
        "late_dinner": late_dinner,
    }


_PATTERN_CONFIG = {
    "regular_3":             _entry({"breakfast": True,  "lunch": True,  "dinner": True}),
    "irregular":             _entry({"breakfast": True,  "lunch": True,  "dinner": True}, jitter=120),
    "frequent_small":        _entry({"breakfast": True,  "lunch": True,  "dinner": True}, carb_mult=0.7, snacks=2),
    "skip_breakfast":        _entry({"breakfast": False, "lunch": True,  "dinner": True}),
    "skip_lunch":            _entry({"breakfast": True,  "lunch": False, "dinner": True}),
    "skip_dinner":           _entry({"breakfast": True,  "lunch": True,  "dinner": False}),
    "skip_breakfast_lunch":  _entry({"breakfast": False, "lunch": False, "dinner": True}),
    "skip_breakfast_dinner": _entry({"breakfast": False, "lunch": True,  "dinner": False}),
    "skip_lunch_dinner":     _entry({"breakfast": True,  "lunch": False, "dinner": False}),
    "fasting_day":           _entry({"breakfast": False, "lunch": False, "dinner": False}),
    "late_dinner":           _entry({"breakfast": True,  "lunch": True,  "dinner": True}, late_dinner=True),
}

MEAL_PATTERNS = tuple(_PATTERN_CONFIG.keys())


def _truncated_normal(rng, mean, std, low, high):
    for _ in range(100):
        v = rng.normal(mean, std)
        if low <= v <= high:
            return float(v)
    return float(min(max(mean, low), high))


def _meal_time(start_date, hour, minute, rng, jitter_minutes):
    base = datetime(start_date.year, start_date.month, start_date.day,
                    hour, minute)
    if jitter_minutes <= 0:
        return base
    jitter = rng.uniform(-jitter_minutes, jitter_minutes)
    return base + timedelta(minutes=float(jitter))


def sample_scenario(rng, meal_pattern="regular_3", start_date=None):
    """Generate one day of meal events for the given pattern.

    Returns a list of (datetime, carbs_g) tuples sorted by time. May be empty
    (e.g., for `fasting_day`).
    """
    if meal_pattern not in _PATTERN_CONFIG:
        raise ValueError(
            f"unknown meal_pattern {meal_pattern!r}; "
            f"valid options: {MEAL_PATTERNS}"
        )

    if start_date is None:
        start_date = datetime.now().date()

    cfg = _PATTERN_CONFIG[meal_pattern]
    events = []

    for meal_name in ("breakfast", "lunch", "dinner"):
        if not cfg["meals"][meal_name]:
            continue
        if meal_name == "dinner" and cfg["late_dinner"]:
            hour, minute = 22, 0
        else:
            hour, minute = _DEFAULT_TIMES[meal_name]
        t = _meal_time(start_date, hour, minute, rng, cfg["jitter"])
        carbs = _truncated_normal(rng, **_MEAL_CARB_DIST[meal_name]) * cfg["carb_mult"]
        events.append((t, round(carbs, 1)))

    n_snacks = cfg["snacks"]
    if n_snacks > 0:
        # Spread snacks uniformly across waking hours (10:00–22:00) excluding meal times.
        # Use a simple jittered grid to avoid collisions with main meals.
        snack_hours = [10, 15, 21][:n_snacks] if n_snacks <= 3 else [10, 15, 21]
        for hour in snack_hours:
            t = _meal_time(start_date, hour, 0, rng, jitter_minutes=30)
            carbs = _truncated_normal(rng, **_MEAL_CARB_DIST["snack"])
            events.append((t, round(carbs, 1)))

    events.sort(key=lambda x: x[0])
    return events
