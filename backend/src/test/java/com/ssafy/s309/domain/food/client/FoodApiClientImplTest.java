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
          "header": { "resultCode": "00", "resultMsg": "NORMAL SERVICE." },
          "body": {
            "pageNo": 1,
            "totalCount": 1,
            "numOfRows": 1,
            "items": [
              {
                "NUM": "1",
                "FOOD_CD": "D101-004160000-0001",
                "FOOD_NM_KR": "국밥_돼지머리",
                "FOOD_CAT1_NM": "밥류",
                "SERVING_SIZE": "100g",
                "AMT_NUM1": "137.000",
                "AMT_NUM3": "6.70",
                "AMT_NUM4": "5.16",
                "AMT_NUM6": "15.94",
                "AMT_NUM7": "0.16",
                "AMT_NUM8": "0.70",
                "AMT_NUM13": "181.000",
                "AMT_NUM23": "23.82",
                "AMT_NUM24": "1.47",
                "AMT_NUM25": "0.03"
              }
            ]
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
        .andExpect(queryParam("foodNm", "%EA%B5%AD%EB%B0%A5"))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    List<FoodApiItem> items = client.search("국밥");

    assertThat(items).hasSize(1);
    FoodApiItem item = items.get(0);
    assertThat(item.foodCd()).isEqualTo("D101-004160000-0001");
    assertThat(item.foodNm()).isEqualTo("국밥_돼지머리");
    assertThat(item.categoryNm()).isEqualTo("밥류");
    assertThat(item.servingSize()).isEqualTo("100g");
    assertThat(item.kcal()).isEqualTo("137.000");
    assertThat(item.proteinG()).isEqualTo("6.70");
    assertThat(item.fatG()).isEqualTo("5.16");
    assertThat(item.carbsG()).isEqualTo("15.94");
    assertThat(item.sugarG()).isEqualTo("0.16");
    assertThat(item.fiberG()).isEqualTo("0.70");
    assertThat(item.sodiumMg()).isEqualTo("181.000");
    assertThat(item.cholesterolMg()).isEqualTo("23.82");
    assertThat(item.saturatedFatG()).isEqualTo("1.47");
    assertThat(item.transFatG()).isEqualTo("0.03");
  }

  @Test
  void 비정상_resultCode_빈_리스트_반환() {
    String body =
        """
        {
          "header": { "resultCode": "99", "resultMsg": "UNKNOWN ERROR" },
          "body": null
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
          "header": { "resultCode": "00", "resultMsg": "NORMAL SERVICE." },
          "body": {
            "pageNo": 1,
            "totalCount": 0,
            "numOfRows": 20,
            "items": []
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
