package com.ssafy.s309.domain.prediction.client.dto;

import java.util.List;

public record GlucosePredictResponse(
    List<GlucosePoint> curve,
    Double peakMgdl,
    Integer peakMinute,
    Integer returnMinute,
    String modelType,
    Double confidence) {}
