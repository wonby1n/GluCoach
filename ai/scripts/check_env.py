"""환경 검증 스크립트.

실행: cd ai/ && python scripts/check_env.py
모든 항목이 ✓ 면 후속 단계 진행 가능.
"""

from __future__ import annotations

import importlib
import os
import sys
from pathlib import Path


CHECK = "✓"
CROSS = "✗"
WARN = "⚠"


def line(label: str, ok: bool, detail: str = "") -> None:
    mark = CHECK if ok else CROSS
    print(f"  {mark} {label}" + (f"  ({detail})" if detail else ""))


def check_python() -> bool:
    v = sys.version_info
    ok = v >= (3, 11)
    line(f"Python {v.major}.{v.minor}.{v.micro}", ok, "3.11+ 권장")
    return ok


def check_torch() -> bool:
    try:
        import torch
    except ImportError:
        line("torch import", False, "pip install torch")
        return False
    cuda = torch.cuda.is_available()
    detail = f"CUDA={cuda}"
    if cuda:
        detail += f", device={torch.cuda.get_device_name(0)}"
    line(f"torch {torch.__version__}", True, detail)
    return True


def check_modules() -> bool:
    required = [
        "fastapi",
        "pydantic",
        "uvicorn",
        "numpy",
        "pandas",
        "sklearn",
        "matplotlib",
    ]
    optional = ["loguru", "anthropic", "dotenv", "tqdm"]
    all_ok = True
    for name in required:
        try:
            importlib.import_module(name)
            line(f"import {name}", True)
        except ImportError:
            line(f"import {name}", False, "필수")
            all_ok = False
    for name in optional:
        try:
            importlib.import_module(name)
            line(f"import {name}", True)
        except ImportError:
            print(f"  {WARN} import {name}  (선택)")
    return all_ok


def check_env_file() -> bool:
    env_path = Path(".env")
    if not env_path.exists():
        line(".env 파일", False, ".env.example 복사 후 키 입력")
        return False
    line(".env 파일", True)
    has_key = False
    for raw in env_path.read_text(encoding="utf-8").splitlines():
        if raw.strip().startswith("ANTHROPIC_API_KEY"):
            value = raw.split("=", 1)[1].strip() if "=" in raw else ""
            has_key = bool(value) and value not in {'""', "''", "your-key-here"}
            break
    line("ANTHROPIC_API_KEY 설정", has_key, "LLM 리포트용")
    return has_key


def check_data_paths() -> bool:
    paths = {
        "data/": "데이터 루트",
        "data/processed/": "전처리 결과 (데이터 담당자가 채움)",
        "models/": "모델 가중치 + scaler",
    }
    all_ok = True
    for p, desc in paths.items():
        path = Path(p)
        exists = path.exists()
        if exists:
            line(f"{p}", True, desc)
        else:
            print(f"  {WARN} {p}  ({desc} — 없으면 자동 생성됨)")
    return all_ok


def main() -> int:
    print("=" * 60)
    print("환경 검증")
    print("=" * 60)

    print("\n[Python]")
    py_ok = check_python()

    print("\n[PyTorch / GPU]")
    torch_ok = check_torch()

    print("\n[필수 모듈]")
    mod_ok = check_modules()

    print("\n[.env]")
    env_ok = check_env_file()

    print("\n[데이터 경로]")
    check_data_paths()

    print()
    print("=" * 60)
    critical = py_ok and torch_ok and mod_ok
    if critical and env_ok:
        print(f"{CHECK} 환경 OK. 다음 단계 진행 가능.")
        return 0
    if critical and not env_ok:
        print(f"{WARN} 환경 OK, 단 ANTHROPIC_API_KEY 미설정. LLM 모듈은 동작 X.")
        return 0
    print(f"{CROSS} 필수 항목 누락. 위 ✗ 항목부터 해결.")
    return 1


if __name__ == "__main__":
    os.chdir(Path(__file__).resolve().parent.parent)
    raise SystemExit(main())
