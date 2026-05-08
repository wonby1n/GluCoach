"""학습 데이터 → 클래스별 prototype 벡터 DB 빌드.

ImageFolder 형식 디렉터리에서 클래스별로 이미지 임베딩 평균(=prototype) 계산. L2 정규화 후
{class_name: vec} 형태 dict 로 저장.
"""

import argparse
import sys
from pathlib import Path

import torch
from PIL import Image
from torch.utils.data import DataLoader, Dataset
from torch.utils.data.dataloader import default_collate
from tqdm import tqdm

# 학습 시점 transform 과 일치 보장 — feature_extractor 의 TRANSFORM 을 단일 source 로 재사용.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from app.models.feature_extractor import TRANSFORM, load_extractor  # noqa: E402


def skip_none_collate(batch):
    """손상 이미지로 인해 None 반환된 샘플을 제외하고 표준 collate. 모두 None 이면 None 리턴."""
    batch = [b for b in batch if b is not None]
    if not batch:
        return None
    return default_collate(batch)


class AllFoodDataset(Dataset):
    def __init__(self, data_dir):
        self.samples = []
        self.classes = sorted([d.name for d in Path(data_dir).iterdir() if d.is_dir()])
        self.class_to_idx = {c: i for i, c in enumerate(self.classes)}
        for class_dir in Path(data_dir).iterdir():
            if not class_dir.is_dir():
                continue
            for f in (
                list(class_dir.glob("*.jpg"))
                + list(class_dir.glob("*.jpeg"))
                + list(class_dir.glob("*.png"))
            ):
                self.samples.append((f, class_dir.name))

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        path, class_name = self.samples[idx]
        try:
            img = TRANSFORM(Image.open(path).convert("RGB"))
            return img, class_name
        except Exception as e:
            # 손상 이미지는 None → collate 에서 제외. zero-tensor 로 대체하면 prototype 평균이 오염됨.
            print(f"  skip broken image: {path.name} ({e})")
            return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-path", default="models/food/best.pt")
    parser.add_argument("--data-dir", default="data/food/dataset/train")
    parser.add_argument("--output-path", default="models/food/prototype_db.pt")
    parser.add_argument("--num-classes", type=int, default=307)
    parser.add_argument("--batch-size", type=int, default=512)
    parser.add_argument("--num-workers", type=int, default=16)
    parser.add_argument(
        "--device", default="cuda" if torch.cuda.is_available() else "cpu"
    )
    args = parser.parse_args()

    extractor = load_extractor(args.model_path, args.num_classes, args.device)
    dataset = AllFoodDataset(args.data_dir)
    loader = DataLoader(
        dataset,
        batch_size=args.batch_size,
        num_workers=args.num_workers,
        pin_memory=True,
        shuffle=False,
        collate_fn=skip_none_collate,
    )

    print(
        f"Device: {args.device} | 전체 이미지: {len(dataset)}장 | 클래스: {len(dataset.classes)}개"
    )

    class_vecs: dict[str, list[torch.Tensor]] = {}
    for batch in tqdm(loader, desc="벡터 추출"):
        if batch is None:
            continue
        imgs, class_names = batch
        imgs = imgs.to(args.device)
        with torch.no_grad():
            vecs = extractor(imgs).cpu()
        for vec, cname in zip(vecs, class_names):
            class_vecs.setdefault(cname, []).append(vec)

    db = {}
    skipped = 0
    for cname, vecs in class_vecs.items():
        proto = torch.stack(vecs).mean(0)
        norm = proto.norm()
        if norm.item() == 0:
            # #2 fix 가 들어와도 모든 임베딩이 정확히 상쇄되는 극단 케이스 — 방어선.
            print(f"[skip] '{cname}' — prototype norm 0")
            skipped += 1
            continue
        db[cname] = proto / norm

    Path(args.output_path).parent.mkdir(parents=True, exist_ok=True)
    torch.save(db, args.output_path)
    print(f"완료: {args.output_path} ({len(db)}개 클래스, {skipped}개 스킵)")


if __name__ == "__main__":
    main()
