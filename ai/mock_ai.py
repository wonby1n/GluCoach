"""BE 통합 스모크 테스트용 mock AI 서버.

진짜 AI 모델 대신 고정 응답을 반환해서 BE from-image 흐름(인증 → orchestrator →
foods 해석 → 예측)을 검증한다. 쿼리 파라미터로 응답 case 를 강제할 수 있어 BE 의 OK /
LOW_CONFIDENCE / PENDING_NUTRITION 분기를 모두 손쉽게 트리거 가능.

실행:
    .venv/Scripts/python -m uvicorn mock_ai:app --port 8000

사용 예 (BE 가 호출):
    POST /api/v1/food/detect                       → 비빔밥 (high confidence) 반환 (OK 경로)
    POST /api/v1/food/detect?case=low              → confidence 0.3 (LOW_CONFIDENCE)
    POST /api/v1/food/detect?case=empty            → 빈 detections (LOW_CONFIDENCE)
    POST /api/v1/food/detect?case=blank_ko         → name_ko 빈 문자열 (LOW_CONFIDENCE 폴백)
    POST /api/v1/food/detect?case=unknown          → 학습 안 된 음식명 (PENDING_NUTRITION)

혈당 예측 모델은 실제 .pt 파일 사용 — mock 하지 않음.
"""

from fastapi import FastAPI, File, HTTPException, Query, UploadFile
from typing import Literal

# 실제 AI 서버의 glucose 라우터 그대로 임포트해서 혈당 예측만 진짜로 동작하게.
# (agent 라우터는 BE from-image 흐름과 무관 + LLM/MCP 의존성 끌어와 부팅 무거워져 제외)
from app.api import glucose as glucose_router

app = FastAPI(title="S309 mock AI (food detect 만 mock)")
app.include_router(glucose_router.router)


@app.get("/health")
async def health():
    return {"status": "UP"}


@app.post("/api/v1/food/detect")
async def detect(
    file: UploadFile = File(...),
    case: Literal["ok", "low", "empty", "blank_ko", "unknown"] = Query("ok"),
):
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="이미지 파일만 업로드 가능합니다.")

    if case == "empty":
        return {"count": 0, "detections": []}

    if case == "low":
        return {
            "count": 1,
            "detections": [{"name_ko": "비빔밥", "name_en": "bibimbap", "confidence": 0.30}],
        }

    if case == "blank_ko":
        return {
            "count": 1,
            "detections": [{"name_ko": "", "name_en": "unknown_class", "confidence": 0.95}],
        }

    if case == "unknown":
        # foods 테이블·식약처 API 모두 미스 가능성 높은 가짜 음식명 → PENDING_NUTRITION 유도.
        return {
            "count": 1,
            "detections": [
                {"name_ko": "절대로_존재하지_않을_음식_xyz123", "name_en": "fake", "confidence": 0.92}
            ],
        }

    # default: ok — 비빔밥 high confidence
    return {
        "count": 1,
        "detections": [{"name_ko": "비빔밥", "name_en": "bibimbap", "confidence": 0.92}],
    }
