from fastapi import FastAPI

from app.core.config import settings

app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
)


@app.get("/health")
async def health():
    return {"status": "UP"}


# TODO: 라우터 등록 - app.include_router(...)
# TODO: CORS 미들웨어 설정
# TODO: AI 모델 로딩 및 추론 엔드포인트 구현
