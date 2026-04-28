package com.ssafy.s309.domain.prediction.client;

import com.ssafy.s309.domain.prediction.client.dto.GlucosePredictRequest;
import com.ssafy.s309.domain.prediction.client.dto.GlucosePredictResponse;

public interface GlucosePredictClient {

  GlucosePredictResponse predict(GlucosePredictRequest request);
}
