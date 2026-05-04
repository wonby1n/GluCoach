package com.ssafy.s309.domain.prediction.dto;

import com.ssafy.s309.domain.prediction.client.dto.GlucosePoint;
import java.util.List;

public record PredictResponse(
    Integer predictionId,
    List<GlucosePoint> curve,
    Double peakMgdl,
    Integer peakMinute,
    Integer returnMinute,
    Double confidence) {}
