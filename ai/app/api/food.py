import io

from fastapi import APIRouter, File, HTTPException, UploadFile
from PIL import Image, UnidentifiedImageError

from app.core.config import settings
from app.models.food_predictor import FoodPredictor
from app.schemas.food import DetectResponse

router = APIRouter(prefix="/food", tags=["food"])

_predictor: FoodPredictor | None = None


def get_predictor() -> FoodPredictor:
    """Lazy 초기화. 첫 요청 시 모델 + prototype DB 로드 (~수십 MB RAM, EfficientNet-B0 21MB + DB 1.6MB + 활성화)."""
    global _predictor
    if _predictor is None:
        _predictor = FoodPredictor(
            model_path=settings.food_model_path,
            db_path=settings.food_db_path,
            num_classes=settings.food_num_classes,
            device=settings.model_device,
            code_to_name_path=settings.food_code_to_name_path,
        )
    return _predictor


@router.post("/detect", response_model=DetectResponse, summary="음식 인식 (임베딩 검색)")
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
