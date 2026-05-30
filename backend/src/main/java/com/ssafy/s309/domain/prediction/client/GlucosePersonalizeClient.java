package com.ssafy.s309.domain.prediction.client;

import com.ssafy.s309.domain.prediction.client.dto.PersonalizeRequest;
import com.ssafy.s309.domain.prediction.client.dto.PersonalizeResponse;

public interface GlucosePersonalizeClient {

  PersonalizeResponse personalize(PersonalizeRequest request);
}
