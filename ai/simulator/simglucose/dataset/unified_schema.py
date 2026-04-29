"""Convert simulator output to the unified ML-ready schema.

The unified schema is shared by all data sources (simulator + real-world),
so adapters for other sources can produce the same shape downstream.
"""

import pandas as pd


def to_glucose_readings(sim_df, user_id, source="simulator", interval_min=5):
    """Convert simulation DataFrame to long-format CGM readings.

    Args:
        sim_df: DataFrame indexed by Time with at least a CGM column
                (BG used as fallback)
        user_id: synthetic user identifier
        source: data source label
        interval_min: downsample interval in minutes (CGM standard = 5)

    Returns:
        DataFrame with columns [source, user_id, time, glucose]
    """
    if "CGM" in sim_df.columns:
        glucose = sim_df["CGM"]
    elif "BG" in sim_df.columns:
        glucose = sim_df["BG"]
    else:
        raise ValueError("sim_df must contain 'CGM' or 'BG' column")

    series = glucose.copy()
    series.index = pd.to_datetime(series.index)
    series = series.dropna()

    if interval_min and interval_min > 0:
        rule = f"{interval_min}min"
        series = series.resample(rule).first().dropna()

    # Round to 2 decimal places (mg/dL) — preserves a bit of CGM precision
    # while avoiding spurious 14-digit floats from raw float64.
    glucose_rounded = series.values.round(2)

    return pd.DataFrame({
        "source":   source,
        "user_id":  user_id,
        "time":     series.index,
        "glucose":  glucose_rounded,
    })


def to_meal_events(meal_events, user_id, source="simulator"):
    """Convert (datetime, carbs) list to meal_events schema.

    Args:
        meal_events: list of (datetime, carbs_g) tuples
        user_id: synthetic user identifier
        source: data source label

    Returns:
        DataFrame with columns [source, user_id, time, carbs]
    """
    if not meal_events:
        return pd.DataFrame(columns=["source", "user_id", "time", "carbs"])

    times, carbs = zip(*meal_events)
    return pd.DataFrame({
        "source":  source,
        "user_id": user_id,
        "time":    pd.to_datetime(list(times)),
        "carbs":   [float(c) for c in carbs],
    })


def to_user_row(persona, user_id, source="simulator"):
    """Convert persona dict to one row of the users schema.

    Returns a dict; concatenate many to build the users table.
    """
    bd = persona.get("birthdate")
    bd_str = bd.isoformat() if bd is not None else None

    return {
        "source":            source,
        "user_id":           user_id,
        "diabetes_type":     persona.get("diabetes_type"),
        "birthdate":         bd_str,
        "sex":               persona.get("sex"),
        "height_cm":         persona.get("height_cm"),
        "weight_kg":         persona.get("weight_kg"),
        "treatment":         persona.get("treatment"),
        "medication_timing": persona.get("medication_timing"),
        "medication":        persona.get("medication"),
        "diagnosis_years":   persona.get("diagnosis_years"),
        "hba1c":             persona.get("hba1c"),
        "fasting_bg":        persona.get("fasting_bg"),
        "activity":          persona.get("activity"),
        "meal_pattern":      persona.get("meal_pattern"),
        "target_bg":         persona.get("target_bg"),
    }
