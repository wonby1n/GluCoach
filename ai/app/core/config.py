from typing import Optional

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    app_name: str = "S309 AI"
    app_version: str = "0.1.0"
    debug: bool = False

    # 음식 인식 — YOLO (1차) + EfficientNet-B0 임베딩 (fallback)
    # YOLO
    yolo_model_path: str = "models/yolo_food/best.pt"
    # YOLO 클래스명(영문 발음 표기) → 한국어 매핑
    yolo_class_to_name_path: str = "models/yolo_food/class_to_name.json"
    # YOLO top-1 confidence 가 이 값 미만이면 EfficientNet 으로 fallback
    yolo_min_confidence: float = 0.9

    # EfficientNet-B0 (prototype DB cosine 검색)
    food_model_path: str = "models/food/best.pt"
    food_db_path: str = "models/food/prototype_db.pt"
    # AI Hub 코드 → 한글 음식명 매핑. PDF 가이드 표에서 추출 (96% 커버리지).
    food_code_to_name_path: str = "models/food/code_to_name.json"
    # 학습 시점 클래스 수. state_dict 모양 매칭에만 사용 — 추론은 head 거치지 않음.
    food_num_classes: int = 307
    model_device: str = "cpu"
    model_top_k: int = 5

    # OpenAI API (주간 보고서 LLM)
    openai_api_key: Optional[str] = None
    openai_base_url: str = "https://gms.ssafy.io/gmsapi/api.openai.com/v1"

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8", "extra": "ignore"}


settings = Settings()
