"""
AIHub 음식 이미지 데이터를 ImageFolder 형식으로 변환.

사용법:
    python scripts/prepare_data.py \
        --src data/New_sample/원천데이터/합본_양추정_이미지_TRAIN/image \
        --dst data/food \
        --val-ratio 0.2

변환 전:
    src/김밥/Q3/*.JPG
    src/김밥/Q4/*.JPG

변환 후:
    dst/train/김밥/xxx.JPG
    dst/val/김밥/xxx.JPG
"""

import argparse
import random
import shutil
from pathlib import Path


def main(args):
    src = Path(args.src)
    dst = Path(args.dst)
    val_ratio = args.val_ratio

    if not src.exists():
        print(f"소스 경로가 존재하지 않습니다: {src}")
        return

    train_dir = dst / "train"
    val_dir = dst / "val"

    extensions = {".jpg", ".jpeg", ".png", ".bmp"}
    total_train, total_val = 0, 0

    for class_dir in sorted(src.iterdir()):
        if not class_dir.is_dir():
            continue

        class_name = class_dir.name

        images = []
        for f in class_dir.rglob("*"):
            if f.is_file() and f.suffix.lower() in extensions:
                images.append(f)

        if not images:
            continue

        random.seed(42)
        random.shuffle(images)

        split_idx = max(1, int(len(images) * (1 - val_ratio)))
        train_images = images[:split_idx]
        val_images = images[split_idx:]

        train_class_dir = train_dir / class_name
        val_class_dir = val_dir / class_name
        train_class_dir.mkdir(parents=True, exist_ok=True)
        val_class_dir.mkdir(parents=True, exist_ok=True)

        for img in train_images:
            shutil.copy2(img, train_class_dir / img.name)
        for img in val_images:
            shutil.copy2(img, val_class_dir / img.name)

        total_train += len(train_images)
        total_val += len(val_images)
        print(f"  {class_name}: train={len(train_images)}, val={len(val_images)}")

    print(f"\n완료. 총 train={total_train}, val={total_val}")
    print(f"저장 위치: {dst}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="AIHub 데이터 → ImageFolder 변환")
    parser.add_argument("--src", type=str, required=True, help="원천데이터 image 폴더")
    parser.add_argument("--dst", type=str, default="data/food", help="출력 경로")
    parser.add_argument("--val-ratio", type=float, default=0.2, help="검증 데이터 비율")
    main(parser.parse_args())
