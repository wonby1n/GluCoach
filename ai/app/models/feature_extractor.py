"""EfficientNet-B0 backbone 에서 classifier 를 제거하고 이미지 → 1280차원 임베딩 추출.

분류 head 를 거치지 않고 prototype DB 와 cosine 유사도로 매칭하기 위한 특징 추출기.
"""

import torch
import torch.nn as nn
from PIL import Image
from torchvision import models, transforms

# EfficientNet-B0 ImageNet 표준 전처리. prototype DB 도 동일 transform 으로 빌드됨 (build_db.py 참조).
TRANSFORM = transforms.Compose(
    [
        transforms.Resize(256),
        transforms.CenterCrop(224),
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),
    ]
)


class FeatureExtractor(nn.Module):
    """학습된 EfficientNet-B0 의 features + avgpool 만 사용해 1280-D L2 정규화 벡터 출력."""

    def __init__(self, model_path: str, num_classes: int = 307):
        super().__init__()

        # 학습 시 구조와 동일하게 만들어야 state_dict 매칭 — classifier head 모양까지 일치.
        # head 는 곧 버리지만 load_state_dict 가 strict 라서 num_classes 가 학습 시점과 같아야 함.
        base = models.efficientnet_b0(weights=None)
        base.classifier[1] = nn.Linear(base.classifier[1].in_features, num_classes)
        state = torch.load(model_path, map_location="cpu", weights_only=True)
        base.load_state_dict(state)

        self.features = base.features
        self.avgpool = base.avgpool

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        x = self.features(x)
        x = self.avgpool(x)
        x = torch.flatten(x, 1)
        # cosine similarity = 정규화된 벡터 내적. prototype DB 도 정규화돼 있음.
        return nn.functional.normalize(x, dim=1)


def load_extractor(
    model_path: str, num_classes: int = 307, device: str = "cpu"
) -> FeatureExtractor:
    extractor = FeatureExtractor(model_path, num_classes)
    extractor.to(device)
    extractor.eval()
    return extractor


def extract_vector(
    extractor: FeatureExtractor, image: Image.Image, device: str = "cpu"
) -> torch.Tensor:
    """단일 PIL 이미지 → 1280-D 벡터."""
    tensor = TRANSFORM(image.convert("RGB")).unsqueeze(0).to(device)
    with torch.no_grad():
        vec = extractor(tensor)
    return vec.squeeze(0).cpu()
