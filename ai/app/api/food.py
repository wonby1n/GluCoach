import io

from fastapi import APIRouter, File, HTTPException, UploadFile
from PIL import Image, UnidentifiedImageError

from app.core.config import settings
from app.models.food_predictor import FoodPredictor
from app.models.hybrid_predictor import HybridFoodPredictor
from app.models.yolo_detector import YoloFoodDetector
from app.schemas.food import DetectResponse

router = APIRouter(prefix="/food", tags=["food"])

_predictor: HybridFoodPredictor | None = None


def get_predictor() -> HybridFoodPredictor:
    """Lazy 초기화. 첫 요청 시 YOLO + EfficientNet 모두 로드."""
    global _predictor
    if _predictor is None:
        yolo = YoloFoodDetector(
            model_path=settings.yolo_model_path,
            code_to_name_path=settings.yolo_class_to_name_path,
        )
        efficientnet = FoodPredictor(
            model_path=settings.food_model_path,
            db_path=settings.food_db_path,
            num_classes=settings.food_num_classes,
            device=settings.model_device,
            code_to_name_path=settings.food_code_to_name_path,
        )
        _predictor = HybridFoodPredictor(
            yolo=yolo,
            efficientnet=efficientnet,
            yolo_min_confidence=settings.yolo_min_confidence,
        )
    return _predictor


@router.post("/detect", response_model=DetectResponse, summary="음식 인식 (YOLO → EfficientNet fallback)")
async def detect_food(file: UploadFile = File(..., description="음식 이미지 (jpg/png)")):
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="이미지 파일만 업로드 가능합니다.")

    contents = await file.read()
    try:
        image = Image.open(io.BytesIO(contents)).convert("RGB")
    except UnidentifiedImageError as e:
        raise HTTPException(status_code=400, detail="이미지 디코딩 실패") from e

    detections = get_predictor().predict(image, top_k=settings.model_top_k)
    return DetectResponse(count=len(detections), detections=detections)
