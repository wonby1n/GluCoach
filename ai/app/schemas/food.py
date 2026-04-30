from pydantic import BaseModel, Field


class BBox(BaseModel):
    x1: float
    y1: float
    x2: float
    y2: float


class DetectionResult(BaseModel):
    name_ko: str
    name_en: str
    confidence: float = Field(ge=0.0, le=1.0)
    bbox: BBox


class DetectResponse(BaseModel):
    count: int
    detections: list[DetectionResult]
