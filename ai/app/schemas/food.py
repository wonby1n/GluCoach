from pydantic import BaseModel, Field


class DetectionResult(BaseModel):
    # DB 키 그대로. AI Hub 코드(예: "01011001") 또는 한글명(예: "마라탕"). BE 가 foods 테이블 매칭.
    name_ko: str
    # cosine similarity. -1~1 이지만 음식 도메인에선 보통 0~1. BE 가 0.6 임계로 게이트.
    confidence: float = Field(ge=-1.0, le=1.0)


class DetectResponse(BaseModel):
    count: int
    detections: list[DetectionResult]
