package com.ssafy.s309.domain.food.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ssafy.s309.config.FoodApiProperties;
import com.ssafy.s309.domain.food.client.dto.FoodApiItem;
import com.ssafy.s309.domain.food.exception.FoodApiException;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@SuppressWarnings("NonAsciiCharacters")
class FoodApiClientImplTest {

  private static final String BASE_URL = "https://apis.data.go.kr/1471000";
  private static final String SERVICE_KEY = "test-service-key";

  private MockRestServiceServer server;
  private FoodApiClientImpl client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    server = MockRestServiceServer.bindTo(builder).build();
    RestClient restClient = builder.build();

    FoodApiProperties properties = new FoodApiProperties(BASE_URL, SERVICE_KEY, 3000, 5000);
    client = new FoodApiClientImpl(restClient, properties);
  }

  @Test
  void 정상_응답_파싱_성공() {
    String body =
        """
        {
          "response": {
            "header": { "resultCode": "00", "resultMsg": "NORMAL SERVICE." },
            "body": {
              "pageNo": 1,
              "numOfRows": 20,
              "totalCount": 1,
              "items": [
                {
                  "foodCd": "D000001",
                  "foodNm": "흰쌀밥",
                  "foodLv3Nm": "밥류",
                  "enerc": "143.00",
                  "chocdf": "31.70",
                  "sugar": "0.10",
                  "prot": "2.50",
                  "fatce": "0.40",
                  "fibtg": "0.30",
                  "fasat": "0.10",
                  "fatrn": "0.00",
                  "chole": "0.00",
                  "nat": "1.00"
                }
              ]
            }
          }
        }
        """;

    server
        .expect(
            requestTo(Matchers.startsWith(BASE_URL + "/FoodNtrCpntDbInfo02/getFoodNtrCpntDbInq02")))
        .andExpect(method(HttpMethod.GET))
        .andExpect(queryParam("serviceKey", SERVICE_KEY))
        .andExpect(queryParam("type", "json"))
        .andExpect(queryParam("pageNo", "1"))
        .andExpect(queryParam("numOfRows", "20"))
        .andExpect(queryParam("foodNm", "%ED%9D%B0%EC%8C%80%EB%B0%A5"))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    List<FoodApiItem> items = client.search("흰쌀밥");

    assertThat(items).hasSize(1);
    FoodApiItem item = items.get(0);
    assertThat(item.foodCd()).isEqualTo("D000001");
    assertThat(item.foodNm()).isEqualTo("흰쌀밥");
    assertThat(item.category()).isEqualTo("밥류");
    assertThat(item.kcal()).isEqualTo("143.00");
    assertThat(item.carbsG()).isEqualTo("31.70");
    assertThat(item.sugarG()).isEqualTo("0.10");
    assertThat(item.proteinG()).isEqualTo("2.50");
    assertThat(item.fatG()).isEqualTo("0.40");
    assertThat(item.fiberG()).isEqualTo("0.30");
    assertThat(item.saturatedFatG()).isEqualTo("0.10");
    assertThat(item.transFatG()).isEqualTo("0.00");
    assertThat(item.cholesterolMg()).isEqualTo("0.00");
    assertThat(item.sodiumMg()).isEqualTo("1.00");
  }

  @Test
  void 비정상_resultCode_빈_리스트_반환() {
    String body =
        """
        {
          "response": {
            "header": { "resultCode": "99", "resultMsg": "UNKNOWN ERROR" },
            "body": null
          }
        }
        """;

    server
        .expect(
            requestTo(Matchers.startsWith(BASE_URL + "/FoodNtrCpntDbInfo02/getFoodNtrCpntDbInq02")))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    List<FoodApiItem> items = client.search("없는음식");

    assertThat(items).isEmpty();
  }

  @Test
  void items_없을_때_빈_리스트_반환() {
    String body =
        """
        {
          "response": {
            "header": { "resultCode": "00", "resultMsg": "NORMAL SERVICE." },
            "body": {
              "pageNo": 1,
              "numOfRows": 20,
              "totalCount": 0,
              "items": []
            }
          }
        }
        """;

    server
        .expect(
            requestTo(Matchers.startsWith(BASE_URL + "/FoodNtrCpntDbInfo02/getFoodNtrCpntDbInq02")))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    List<FoodApiItem> items = client.search("zzzzzz");

    assertThat(items).isEmpty();
  }

  @Test
  void HTTP_5xx_시_FoodApiException() {
    server
        .expect(
            requestTo(Matchers.startsWith(BASE_URL + "/FoodNtrCpntDbInfo02/getFoodNtrCpntDbInq02")))
        .andRespond(withServerError());

    assertThatThrownBy(() -> client.search("쌀밥"))
        .isInstanceOf(FoodApiException.class)
        .hasMessageContaining("HTTP 500");
  }
}
