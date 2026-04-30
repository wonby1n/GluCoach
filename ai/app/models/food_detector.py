from pathlib import Path

from PIL import Image


FOOD_LABELS = {
    0: {"en": "bibimbap",     "ko": "비빔밥"},
    1: {"en": "kimchi",       "ko": "김치"},
    2: {"en": "nangmyeon",    "ko": "냉면"},
    3: {"en": "jjajangmyeon", "ko": "짜장면"},
    4: {"en": "pajeon",       "ko": "파전"},
}


class FoodDetector:
    """YOLOv8 기반 음식 탐지 모델."""

    def __init__(self, model_path: str | Path, device: str = "cpu"):
        from ultralytics import YOLO
        self.model = YOLO(str(model_path))
        self.device = device

    def predict(self, image: Image.Image, conf: float = 0.25) -> list[dict]:
        results = self.model.predict(
            source=image,
            conf=conf,
            device=self.device,
            verbose=False,
        )

        detections = []
        for r in results:
            for box in r.boxes:
                cls_id = int(box.cls[0])
                label = FOOD_LABELS.get(cls_id, {"en": "unknown", "ko": "알 수 없음"})
                x1, y1, x2, y2 = [round(v, 1) for v in box.xyxy[0].tolist()]
                detections.append({
                    "name_ko": label["ko"],
                    "name_en": label["en"],
                    "confidence": round(float(box.conf[0]), 4),
                    "bbox": {"x1": x1, "y1": y1, "x2": x2, "y2": y2},
                })

        detections.sort(key=lambda d: d["confidence"], reverse=True)
        return detections
