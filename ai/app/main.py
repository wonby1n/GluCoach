from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import glucose as glucose_router
from app.api.agent import router as agent_router
from app.core.config import settings
from app.api.food import router as food_router

app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
)

app.include_router(glucose_router.router)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(food_router, prefix="/api/v1")
app.include_router(agent_router)


@app.get("/health")
async def health():
    return {"status": "UP"}

