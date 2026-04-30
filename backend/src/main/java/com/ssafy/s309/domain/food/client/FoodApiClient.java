package com.ssafy.s309.domain.food.client;

import com.ssafy.s309.domain.food.client.dto.FoodApiItem;
import java.util.List;

public interface FoodApiClient {

  List<FoodApiItem> search(String query);
}
