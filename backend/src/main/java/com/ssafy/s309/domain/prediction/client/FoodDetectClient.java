package com.ssafy.s309.domain.prediction.client;

import com.ssafy.s309.domain.prediction.client.dto.FoodDetectResponse;
import org.springframework.web.multipart.MultipartFile;

public interface FoodDetectClient {

  FoodDetectResponse detect(MultipartFile image);
}
