"""음식 인식 추론 — 이미지 → 임베딩 → prototype DB cosine 유사도 → top-k 음식명.

분류기 출력 대신 임베딩 검색 방식. 신규 음식은 register_food 스크립트로 prototype 벡터를
DB 에 추가만 하면 추론 가능 — 재학습 불필요.

DB 키 가운데 AI Hub 코드(예: "01011001") 와 한글명(예: "마라탕") 이 섞여 있음. 응답에선
DB 키를 그대로 name_ko 로 흘려보낸다 — 코드→한글 매핑은 BE 측 후속 작업.
"""

from pathlib import Path

import torch
from PIL import Image

from app.models.feature_extractor import TRANSFORM, load_extractor


class FoodPredictor:
    """모델·DB 를 한 번 로드해 보관하고 이미지당 cosine top-k 를 뽑는다."""

    def __init__(
        self,
        model_path: str | Path,
        db_path: str | Path,
        num_classes: int = 307,
        device: str = "cpu",
    ):
        self.device = device
        self.extractor = load_extractor(str(model_path), num_classes=num_classes, device=device)

        # weights_only=False — DB 는 dict[str, Tensor] 라 pickle 디시리얼라이저 필요.
        # 신뢰된 내부 산출물(build_db.py)이라 보안 위험 없음.
        db = torch.load(str(db_path), weights_only=False, map_location=device)
        self.class_names: list[str] = list(db.keys())
        # (N, 1280) 행렬. 모든 prototype 은 빌드 시 L2 정규화돼 들어옴.
        self.prototypes: torch.Tensor = torch.stack(list(db.values())).to(device)

    def predict(self, image: Image.Image, top_k: int = 5) -> list[dict]:
        """PIL 이미지 → top-k {name_ko, confidence} 리스트 (confidence DESC).

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
            {"name_ko": self.class_names[idx], "confidence": round(val, 4)}
            for val, idx in zip(top_vals.tolist(), top_idx.tolist())
        ]
