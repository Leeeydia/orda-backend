from __future__ import annotations

from typing import Optional


def round_one_decimal(value: float) -> float:
    return round(value, 1)


def calculate_elevation_gain(
    elevation_start_m: float,
    elevation_end_m: float,
) -> float:
    """
    elevation_gain_m = elevation_end_m - elevation_start_m
    """
    return round_one_decimal(elevation_end_m - elevation_start_m)


def calculate_slope_percent(
    elevation_gain_m: float,
    distance_m: float,
) -> Optional[float]:
    """
    slope_percent = (elevation_gain_m / distance_m) * 100

    distance_m이 0 이하이면 계산 불가로 보고 None 반환
    """
    if distance_m <= 0:
        return None

    slope_percent = (elevation_gain_m / distance_m) * 100
    return round_one_decimal(slope_percent)


def classify_difficulty(slope_percent: Optional[float]) -> Optional[str]:
    """
    difficulty는 abs(slope_percent) 기준 분류
    - 0 이상 5 미만: easy
    - 5 이상 12 미만: medium
    - 12 이상: hard

    slope_percent가 None이면 분류 불가이므로 None 반환
    """
    if slope_percent is None:
        return None

    slope_abs = abs(slope_percent)

    if 0 <= slope_abs < 5:
        return "easy"
    if 5 <= slope_abs < 12:
        return "medium"
    return "hard"