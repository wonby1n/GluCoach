import argparse, torch, sys
from pathlib import Path
from tqdm import tqdm
from PIL import Image
from torch.utils.data import Dataset, DataLoader
from torchvision import transforms

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from feature_extractor import load_extractor

TRANSFORM = transforms.Compose([
    transforms.Resize(256), transforms.CenterCrop(224),
    transforms.ToTensor(),
    transforms.Normalize([0.485,0.456,0.406],[0.229,0.224,0.225]),
])

class AllFoodDataset(Dataset):
    def __init__(self, data_dir):
        self.samples = []
        self.classes = sorted([d.name for d in Path(data_dir).iterdir() if d.is_dir()])
        self.class_to_idx = {c: i for i, c in enumerate(self.classes)}
        for class_dir in Path(data_dir).iterdir():
            if not class_dir.is_dir(): continue
            for f in list(class_dir.glob("*.jpg")) + list(class_dir.glob("*.jpeg")) + list(class_dir.glob("*.png")):
                self.samples.append((f, class_dir.name))

    def __len__(self): return len(self.samples)

    def __getitem__(self, idx):
        path, class_name = self.samples[idx]
        try:
            img = TRANSFORM(Image.open(path).convert("RGB"))
        except:
            img = torch.zeros(3, 224, 224)
        return img, class_name

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-path",  default="outputs/food_v2/best.pt")
    parser.add_argument("--data-dir",    default="data/food/dataset/train")
    parser.add_argument("--output-path", default="outputs/food_v2/prototype_db.pt")
    parser.add_argument("--num-classes", type=int, default=307)
    parser.add_argument("--batch-size",  type=int, default=512)
    parser.add_argument("--num-workers", type=int, default=16)
    parser.add_argument("--device",      default="cuda" if torch.cuda.is_available() else "cpu")
    args = parser.parse_args()

    extractor = load_extractor(args.model_path, args.num_classes, args.device)
    dataset = AllFoodDataset(args.data_dir)
    loader = DataLoader(dataset, batch_size=args.batch_size, num_workers=args.num_workers,
                        pin_memory=True, shuffle=False)

    print(f"Device: {args.device} | 전체 이미지: {len(dataset)}장 | 클래스: {len(dataset.classes)}개")

    class_vecs = {}
    for imgs, class_names in tqdm(loader, desc="벡터 추출"):
        imgs = imgs.to(args.device)
        with torch.no_grad():
            vecs = extractor(imgs).cpu()
        for vec, cname in zip(vecs, class_names):
            class_vecs.setdefault(cname, []).append(vec)

    db = {}
    for cname, vecs in class_vecs.items():
        proto = torch.stack(vecs).mean(0)
        db[cname] = proto / proto.norm()

    Path(args.output_path).parent.mkdir(parents=True, exist_ok=True)
    torch.save(db, args.output_path)
    print(f"완료: {args.output_path} ({len(db)}개 클래스)")

if __name__ == "__main__":
    main()
