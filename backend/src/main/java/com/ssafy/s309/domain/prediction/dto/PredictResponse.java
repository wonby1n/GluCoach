package com.ssafy.s309.domain.prediction.dto;

import java.util.List;

public record PredictResponse(
    Integer predictionId,
    List<CurvePoint> curve,
    Double peakMgdl,
    Integer peakMinute,
    Double confidence) {}
