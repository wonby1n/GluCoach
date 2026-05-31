"""주간 보고서 LLM 프롬프트 빌더."""

from app.schemas.report import WeeklyReportRequest


def _diabetes_label(dtype: str) -> str:
    return {"1": "1형", "2": "2형"}.get(dtype, "")


def _format_foods(foods: list) -> str:
    if not foods:
        return "데이터 없음"
    return ", ".join(f"{f.food_name}(기울기 {f.avg_slope:.1f})" for f in foods[:5])


def _format_sleep(minutes: float | None) -> str:
    if minutes is None:
        return "데이터 없음"
    h, m = divmod(int(minutes), 60)
    return f"{h}시간 {m}분"


def _find_vulnerable_hours(hourly: list) -> list[tuple[int, float]]:
    """평균 혈당이 높은 상위 3개 시간대를 반환한다."""
    if not hourly:
        return []
    sorted_h = sorted(hourly, key=lambda x: x.avg, reverse=True)
    return [(h.hour, h.avg) for h in sorted_h[:3]]


def build_weekly_report_prompt(req: WeeklyReportRequest) -> str:
    """집계 데이터를 LLM 프롬프트로 변환한다."""
    gmi = 3.31 + (0.02392 * req.avg_glucose)
    cv = (req.glucose_sd / req.avg_glucose * 100) if req.avg_glucose > 0 else 0
    diabetes_label = _diabetes_label(req.diabetes_type)
    vulnerable_hours = _find_vulnerable_hours(req.hourly_avg_glucose)
    vulnerable_str = ", ".join(
        f"{h}시(평균 {avg:.0f} mg/dL)" for h, avg in vulnerable_hours
    ) if vulnerable_hours else "특이 패턴 없음"

    steps_str = f"{req.weekly_avg_steps:,.0f}보" if req.weekly_avg_steps is not None else "데이터 없음"
    calories_str = f"{req.weekly_total_calories:,.0f} kcal" if req.weekly_total_calories is not None else "데이터 없음"
    med_str = f"{req.medication_count}회" if req.medication_count is not None else "데이터 없음"

    return f"""당신은 당뇨 환자의 주간 혈당 관리 리포트를 작성하는 전문 AI 코치입니다.
아래 데이터를 바탕으로 {req.user_name}님의 이번 주 리포트를 작성해주세요.

[기간]
{req.week_start} ~ {req.week_end}

[사용자 정보]
- 이름: {req.user_name}
- 당뇨 유형: {diabetes_label + "형 당뇨" if diabetes_label else "일반"}
- 목표 혈당 범위: {req.target_low:.0f} ~ {req.target_high:.0f} mg/dL

[이번 주 혈당 통계]
- 평균 혈당: {req.avg_glucose:.1f} mg/dL
- 최저 / 최고: {req.min_glucose:.1f} / {req.max_glucose:.1f} mg/dL
- 표준편차(혈당 변동폭): {req.glucose_sd:.1f} mg/dL
- GMI(예상 당화혈색소): {gmi:.1f}%
- CV%(변동계수): {cv:.1f}%  (36% 이하 권장)
- TIR(목표 범위 내 시간): {req.time_in_range:.1f}%  (70% 이상 목표)
- TAR(고혈당 구간): {req.time_above_range:.1f}%
- TBR(저혈당 구간): {req.time_below_range:.1f}%

[혈당 취약 시간대 상위 3개]
{vulnerable_str}

[이번 주 식사]
- 총 식사 기록: {req.meal_count}회
- 혈당 반응 좋은 음식(GOOD): {_format_foods(req.good_foods)}
- 혈당 반응 나쁜 음식(BAD): {_format_foods(req.bad_foods)}

[활동]
- 일평균 걸음수: {steps_str}
- 주간 소모 칼로리: {calories_str}

[수면]
- 일평균 수면: {_format_sleep(req.weekly_avg_sleep_minutes)}

[복약]
- 이번 주 복약 기록: {med_str}

---
위 데이터를 종합하여 아래 JSON 형식으로 리포트를 작성하세요.
반드시 JSON만 반환하고 다른 설명은 붙이지 마세요.

{{
  "ai_summary": "이번 주 혈당 흐름과 패턴을 종합한 요약. 혈당 통계 해석, 취약 시간대 특이사항, 식사와 혈당 반응 관계, 잘 된 점과 아쉬운 점을 포함. 300~500자. 부드럽고 격려하는 톤.",
  "ai_suggest": "다음 주를 위한 실천 가능한 코칭 제안 2~3가지와 목표 1가지. 식사 조정, 활동, 수면, 복약 중 가장 임팩트가 큰 항목 우선. 200~350자. '~해볼까요?' 형식 권장."
}}

[작성 규칙]
- 수치를 언급할 때는 반드시 의미와 함께 설명 (예: "TIR 75%로 목표에 근접했어요")
- 의학 전문 용어보다 쉬운 표현 사용 (TIR → "목표 범위 안에 있던 시간")
- 저혈당 TBR이 4% 초과이면 반드시 언급
- 고혈당 TAR이 25% 초과이면 반드시 언급
- CV%가 36% 초과면 혈당 변동이 크다는 점 언급
- "위험", "경고", "반드시", "꼭" 같은 불안감을 주는 표현 금지
- 데이터가 없는 항목("데이터 없음")은 리포트에서 언급하지 말 것"""
