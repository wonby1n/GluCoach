from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import glucose as glucose_router
from app.core.config import settings

app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(glucose_router.router)


@app.get("/health")
async def health():
    return {"status": "UP"}
