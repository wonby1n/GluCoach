from typing import Optional
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    app_name: str = "S309 AI"
    app_version: str = "0.1.0"
    debug: bool = False

    model_dir: str = "outputs/food_v1"
    model_device: str = "cpu"
    model_top_k: int = 5

    yolo_model_path: str = "models/yolo_food/best.pt"

    # OpenAI API (주간 보고서 LLM)
    openai_api_key: Optional[str] = None

    # S3 설정 (주간 보고서 PDF 업로드)
    aws_access_key_id: Optional[str] = None
    aws_secret_access_key: Optional[str] = None
    aws_region: str = "ap-northeast-2"
    aws_s3_bucket: Optional[str] = None
    aws_s3_endpoint: Optional[str] = None  # LocalStack/MinIO용

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8", "extra": "ignore"}


settings = Settings()
