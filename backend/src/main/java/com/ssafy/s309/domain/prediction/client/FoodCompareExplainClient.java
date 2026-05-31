package com.ssafy.s309.domain.prediction.client;

import com.ssafy.s309.domain.prediction.client.dto.FoodCompareAiRequest;
import com.ssafy.s309.domain.prediction.client.dto.FoodCompareAiResponse;

public interface FoodCompareExplainClient {
  FoodCompareAiResponse explain(FoodCompareAiRequest request);
}
