import json
from pathlib import Path

import torch
import torch.nn as nn
from torchvision import models, transforms
from PIL import Image


class FoodClassifier:
    """EfficientNet-B0 기반 음식 분류 모델."""

    TRANSFORM = transforms.Compose([
        transforms.Resize(256),
        transforms.CenterCrop(224),
        transforms.ToTensor(),
        transforms.Normalize(
            mean=[0.485, 0.456, 0.406],
            std=[0.229, 0.224, 0.225],
        ),
    ])

    def __init__(self, model_dir: str | Path, device: str = "cpu"):
        self.device = torch.device(device)
        model_dir = Path(model_dir)

        with open(model_dir / "classes.json", encoding="utf-8") as f:
            self.classes: list[str] = json.load(f)

        self.model = self._build_model(len(self.classes))
        state = torch.load(model_dir / "best.pt", map_location=self.device, weights_only=True)
        self.model.load_state_dict(state)
        self.model.to(self.device)
        self.model.eval()

    def predict(self, image: Image.Image, top_k: int = 5) -> list[dict]:
        tensor = self.TRANSFORM(image.convert("RGB")).unsqueeze(0).to(self.device)

        with torch.no_grad():
            logits = self.model(tensor)
            probs = torch.softmax(logits, dim=1)

        top_probs, top_indices = probs.topk(top_k, dim=1)

        return [
            {"name": self.classes[idx], "confidence": round(prob, 4)}
            for prob, idx in zip(
                top_probs.squeeze().tolist(),
                top_indices.squeeze().tolist(),
            )
        ]

    @staticmethod
    def _build_model(num_classes: int) -> nn.Module:
        model = models.efficientnet_b0(weights=None)
        model.classifier[1] = nn.Linear(model.classifier[1].in_features, num_classes)
        return model


def build_model_for_training(num_classes: int, pretrained: bool = True) -> nn.Module:
    """학습용 모델 생성. classifier만 학습하고 backbone은 freeze."""
    weights = models.EfficientNet_B0_Weights.DEFAULT if pretrained else None
    model = models.efficientnet_b0(weights=weights)

    for param in model.features.parameters():
        param.requires_grad = False

    model.classifier[1] = nn.Linear(model.classifier[1].in_features, num_classes)
    return model
