"""YOLO → EfficientNet 두 단계 음식 인식.

YOLO top-1 confidence가 임계값 이상이면 YOLO 결과를 그대로 반환.
그렇지 않으면 EfficientNet(prototype DB cosine 검색)으로 fallback.
"""

import logging

from PIL import Image

from app.models.food_predictor import FoodPredictor
from app.models.yolo_detector import YoloFoodDetector

logger = logging.getLogger(__name__)


class HybridFoodPredictor:
    """YOLO 먼저, confidence 부족 시 EfficientNet으로 fallback."""

    def __init__(
        self,
        yolo: YoloFoodDetector,
        efficientnet: FoodPredictor,
        yolo_min_confidence: float = 0.7,
    ):
        self.yolo = yolo
        self.efficientnet = efficientnet
        self.yolo_min_confidence = yolo_min_confidence

    def predict(self, image: Image.Image, top_k: int = 5) -> list[dict]:
        yolo_results = self.yolo.predict(image, top_k=top_k)

        if yolo_results and yolo_results[0]["confidence"] >= self.yolo_min_confidence:
            logger.debug(
                "YOLO 인식 성공 (top1=%s, conf=%.3f)",
                yolo_results[0]["name_ko"],
                yolo_results[0]["confidence"],
            )
            return yolo_results

        logger.debug(
            "YOLO 결과 부족 (top1_conf=%.3f < %.3f) → EfficientNet fallback",
            yolo_results[0]["confidence"] if yolo_results else 0.0,
            self.yolo_min_confidence,
        )
        return self.efficientnet.predict(image, top_k=top_k)
