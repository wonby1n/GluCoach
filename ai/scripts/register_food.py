import argparse, torch, sys
from pathlib import Path
from tqdm import tqdm
from PIL import Image, UnidentifiedImageError

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from feature_extractor import load_extractor, TRANSFORM

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-path",  default="outputs/food_v2/best.pt")
    parser.add_argument("--db-path",     default="outputs/food_v2/prototype_db.pt")
    parser.add_argument("--name",        required=True)
    parser.add_argument("--images-dir",  required=True)
    parser.add_argument("--num-classes", type=int, default=307)
    parser.add_argument("--device",      default="cuda" if torch.cuda.is_available() else "cpu")
    args = parser.parse_args()

    db = torch.load(args.db_path, weights_only=False) if Path(args.db_path).exists() else {}
    print(f"기존 DB 로드: {len(db)}개 클래스")

    extractor = load_extractor(args.model_path, args.num_classes, args.device)

    images_dir = Path(args.images_dir)
    # 대소문자 모두 처리
    image_files = [f for f in images_dir.iterdir()
                   if f.suffix.lower() in ('.jpg', '.jpeg', '.png')]
    print(f"이미지 {len(image_files)}장 처리 중...")

    vectors = []
    for img_path in tqdm(image_files):
        try:
            img = Image.open(img_path).convert("RGB")
            tensor = TRANSFORM(img).unsqueeze(0).to(args.device)
            with torch.no_grad():
                vec = extractor(tensor).squeeze(0).cpu()
            vectors.append(vec)
        except Exception as e:
            print(f"  skip: {img_path.name} ({e})")

    if not vectors:
        print("유효한 이미지가 없습니다.")
        return

    prototype = torch.stack(vectors).mean(0)
    db[args.name] = prototype / prototype.norm()
    torch.save(db, args.db_path)
    print(f"✅ '{args.name}' 등록 완료 ({len(vectors)}장)")
    print(f"DB 총 클래스 수: {len(db)}")

if __name__ == "__main__":
    main()
