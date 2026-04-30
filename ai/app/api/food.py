from fastapi import APIRouter, File, HTTPException, UploadFile
from PIL import Image
import io

from app.core.config import settings
from app.models.food_detector import FoodDetector
from app.schemas.food import DetectResponse

router = APIRouter(prefix="/food", tags=["food"])

_detector: FoodDetector | None = None


def get_detector() -> FoodDetector:
    global _detector
    if _detector is None:
        _detector = FoodDetector(
            model_path=settings.yolo_model_path,
            device=settings.model_device,
        )
    return _detector


@router.post("/detect", response_model=DetectResponse, summary="음식 탐지")
async def detect_food(
    file: UploadFile = File(..., description="음식 이미지 (jpg/png)"),
    conf: float = 0.25,
):
    if not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="이미지 파일만 업로드 가능합니다.")

    contents = await file.read()
    image = Image.open(io.BytesIO(contents)).convert("RGB")

    detections = get_detector().predict(image, conf=conf)
    return DetectResponse(count=len(detections), detections=detections)
