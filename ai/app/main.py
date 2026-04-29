from fastapi import FastAPI

from app.api import glucose as glucose_router
from app.core.config import settings

app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
)

app.include_router(glucose_router.router)


@app.get("/health")
async def health():
    return {"status": "UP"}


# TODO: CORS 미들웨어 설정 (백엔드 호출 시 필요하면 추가)
