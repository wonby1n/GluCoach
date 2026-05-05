from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    app_name: str = "S309 AI"
    app_version: str = "0.1.0"
    debug: bool = False

    model_dir: str = "outputs/food_v1"
    model_device: str = "cpu"
    model_top_k: int = 5

    yolo_model_path: str = "models/yolo_food/best.pt"

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8", "extra": "ignore"}


settings = Settings()
