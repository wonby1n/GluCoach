"""음식 인식 추론 — 이미지 → 임베딩 → prototype DB cosine 유사도 → top-k 음식명.

분류기 출력 대신 임베딩 검색 방식. 신규 음식은 register_food 스크립트로 prototype 벡터를
DB 에 추가만 하면 추론 가능 — 재학습 불필요.

DB 키는 AI Hub 코드(예: "01011001") 또는 한글명(예: "마라탕"). code_to_name.json 으로 코드 →
한글명 매핑을 거쳐 응답한다. 매핑에 없는 키는 키 자체를 fallback 으로 흘려보냄(점진 채움 가능).
"""

import json
import logging
from pathlib import Path

import torch
from PIL import Image

from app.models.feature_extractor import TRANSFORM, load_extractor

logger = logging.getLogger(__name__)


class FoodPredictor:
    """모델·DB 를 한 번 로드해 보관하고 이미지당 cosine top-k 를 뽑는다."""

    def __init__(
        self,
        model_path: str | Path,
        db_path: str | Path,
        num_classes: int = 307,
        device: str = "cpu",
        code_to_name_path: str | Path | None = None,
    ):
        self.device = device
        self.extractor = load_extractor(str(model_path), num_classes=num_classes, device=device)

        # weights_only=False — DB 는 dict[str, Tensor] 라 pickle 디시리얼라이저 필요.
        # 신뢰된 내부 산출물(build_db.py)이라 보안 위험 없음.
        db = torch.load(str(db_path), weights_only=False, map_location=device)
        self.class_names: list[str] = list(db.keys())
        # (N, 1280) 행렬. 모든 prototype 은 빌드 시 L2 정규화돼 들어옴.
        self.prototypes: torch.Tensor = torch.stack(list(db.values())).to(device)

        # 코드 → 한글명 매핑. 파일이 없거나 키가 없으면 키 자체로 fallback.
        # 매핑은 부가 기능이라 손상 시 추론 자체는 계속 가능하도록 빈 dict 폴백.
        self.code_to_name: dict[str, str] = {}
        if code_to_name_path and Path(code_to_name_path).exists():
            try:
                self.code_to_name = json.loads(
                    Path(code_to_name_path).read_text(encoding="utf-8")
                )
            except (json.JSONDecodeError, OSError) as e:
                logger.warning(
                    "code_to_name 로드 실패 (path=%s) — DB 키를 그대로 응답합니다: %s",
                    code_to_name_path,
                    e,
                )

    def predict(self, image: Image.Image, top_k: int = 5) -> list[dict]:
        """PIL 이미지 → top-k {name_ko, confidence} 리스트 (confidence DESC).

        name_ko 는 code_to_name 매핑 적용된 한글명 (매핑에 없으면 DB 키 그대로 — 코드 또는 한글).
        confidence 는 cosine similarity (-1~1, 같은 도메인 이미지면 보통 0~1).
        BE 측에서 0.6 임계로 LOW_CONFIDENCE 게이트.
        """
        tensor = TRANSFORM(image.convert("RGB")).unsqueeze(0).to(self.device)

        with torch.no_grad():
            vec = self.extractor(tensor).squeeze(0)

        sims = (self.prototypes @ vec).cpu()
        k = min(top_k, len(self.class_names))
        top_vals, top_idx = sims.topk(k)

        return [
            {
                "name_ko": self.code_to_name.get(self.class_names[idx], self.class_names[idx]),
                "confidence": round(val, 4),
            }
            for val, idx in zip(top_vals.tolist(), top_idx.tolist())
        ]
