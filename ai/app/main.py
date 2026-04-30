from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import glucose as glucose_router
from app.core.config import settings
from app.api.food import router as food_router

app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
)

<<<<<<< HEAD
app.include_router(glucose_router.router)
=======
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(food_router, prefix="/api/v1")
>>>>>>> a3ff86eef5523743ba6b4efe11a5c3b0822610fc


@app.get("/health")
async def health():
    return {"status": "UP"}
<<<<<<< HEAD


# TODO: CORS 미들웨어 설정 (백엔드 호출 시 필요하면 추가)
=======
>>>>>>> a3ff86eef5523743ba6b4efe11a5c3b0822610fc
