from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.api.food import router as food_router

app = FastAPI()
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])
app.include_router(food_router, prefix="/api/v1")

@app.get("/health")
async def health():
    return {"status": "UP"}
