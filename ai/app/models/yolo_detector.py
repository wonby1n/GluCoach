"""YOLO 기반 음식 인식. classify / detect 모두 처리.

YOLOv8 classify: results[0].probs → top-k 확률
YOLOv8 detect:   results[0].boxes → bbox별 class + conf, 동일 class 중 최고만 살림
"""

import json
import logging
from pathlib import Path

from PIL import Image
from ultralytics import YOLO

logger = logging.getLogger(__name__)


class YoloFoodDetector:
    def __init__(
        self,
        model_path: str | Path,
        code_to_name_path: str | Path | None = None,
    ):
        self.model = YOLO(str(model_path))
        self.task = self.model.task  # "classify" or "detect"

        self.code_to_name: dict[str, str] = {}
        if code_to_name_path and Path(code_to_name_path).exists():
            try:
                self.code_to_name = json.loads(
                    Path(code_to_name_path).read_text(encoding="utf-8")
                )
            except (json.JSONDecodeError, OSError) as e:
                logger.warning("code_to_name 로드 실패: %s", e)

        logger.info("YoloFoodDetector 로드 완료 (task=%s, path=%s)", self.task, model_path)

    def _display_name(self, raw: str) -> str:
        return self.code_to_name.get(raw, raw)

    def predict(self, image: Image.Image, top_k: int = 5) -> list[dict]:
        """PIL 이미지 → top-k {name_ko, confidence} 리스트 (confidence DESC).

        YOLO가 아무것도 감지 못하면 빈 리스트 반환 → hybrid predictor가 fallback 처리.
        """
        results = self.model(image, verbose=False)
        if not results:
            return []

        if self.task == "classify":
            return self._from_classify(results[0], top_k)
        return self._from_detect(results[0], top_k)

    def _from_classify(self, result, top_k: int) -> list[dict]:
        probs = result.probs
        if probs is None:
            return []

        names: dict[int, str] = result.names
        indices = probs.top5[:top_k]
        confs = probs.top5conf[:top_k].tolist()

        merged: dict[str, float] = {}
        for idx, conf in zip(indices, confs):
            raw = names.get(int(idx), str(idx))
            display = self._display_name(raw)
            if display not in merged or conf > merged[display]:
                merged[display] = conf

        ranked = sorted(merged.items(), key=lambda x: x[1], reverse=True)
        return [{"name_ko": n, "confidence": round(c, 4)} for n, c in ranked]

    def _from_detect(self, result, top_k: int) -> list[dict]:
        boxes = result.boxes
        if boxes is None or len(boxes) == 0:
            return []

        names: dict[int, str] = result.names
        merged: dict[str, float] = {}
        for cls_idx, conf in zip(boxes.cls.tolist(), boxes.conf.tolist()):
            raw = names.get(int(cls_idx), str(int(cls_idx)))
            display = self._display_name(raw)
            if display not in merged or conf > merged[display]:
                merged[display] = conf

        ranked = sorted(merged.items(), key=lambda x: x[1], reverse=True)[:top_k]
        return [{"name_ko": n, "confidence": round(c, 4)} for n, c in ranked]
