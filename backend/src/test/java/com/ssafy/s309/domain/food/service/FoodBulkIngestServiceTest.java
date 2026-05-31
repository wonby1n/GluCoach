package com.ssafy.s309.domain.food.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ssafy.s309.domain.food.dto.FoodIngestResult;
import com.ssafy.s309.domain.food.entity.Food;
import com.ssafy.s309.domain.food.repository.FoodRepository;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class FoodBulkIngestServiceTest {

  @Mock private FoodRepository foodRepository;
  @InjectMocks private FoodBulkIngestService service;

  private static final String HEADER =
      "FOOD_CD,FOOD_NM_KR,FOOD_CAT1_NM,SERVING_SIZE,"
          + "AMT_NUM1,AMT_NUM3,AMT_NUM4,AMT_NUM6,AMT_NUM7,"
          + "AMT_NUM8,AMT_NUM13,AMT_NUM23,AMT_NUM24,AMT_NUM25";

  @Test
  void 신규_row_INSERT_및_is_customized_false() throws IOException {
    given(foodRepository.findByFoodApiId(anyString())).willReturn(Optional.empty());
    given(foodRepository.save(any(Food.class))).willAnswer(inv -> inv.getArgument(0));
    String csv = HEADER + "\n" + "D000007,현미밥,밥류,100g,150,5.0,1.0,32.0,0.2,0.5,0.0,0.0,0.0,0.0\n";

    FoodIngestResult result = service.ingest(new StringReader(csv));

    assertThat(result.inserted()).isEqualTo(1);
    assertThat(result.updated()).isZero();
    ArgumentCaptor<Food> captor = ArgumentCaptor.forClass(Food.class);
    verify(foodRepository).save(captor.capture());
    Food saved = captor.getValue();
    assertThat(saved.getFoodApiId()).isEqualTo("D000007");
    assertThat(saved.getName()).isEqualTo("현미밥");
    assertThat(saved.getCategory()).isEqualTo("밥류");
    assertThat(saved.getKcal()).isEqualByComparingTo("150");
    assertThat(saved.getCarbsG()).isEqualByComparingTo("32.0");
    assertThat(saved.isCustomized()).isFalse();
    assertThat(saved.getSearchCount()).isZero();
    assertThat(saved.getServingSize()).isEqualByComparingTo("100");
  }

  @Test
  void 기존_non_customized_row는_refresh되고_searchCount는_보존() throws IOException {
    Food existing =
        Food.builder()
            .foodApiId("D000007")
            .name("현미밥")
            .kcal(new BigDecimal("100"))
            .searchCount(5)
            .isCustomized(false)
            .cachedAt(LocalDateTime.now().minusDays(60))
            .build();
    given(foodRepository.findByFoodApiId("D000007")).willReturn(Optional.of(existing));
    String csv = HEADER + "\n" + "D000007,현미밥,밥류,100g,155,5.5,1.1,33.0,0.3,0.6,0.0,0.0,0.0,0.0\n";

    FoodIngestResult result = service.ingest(new StringReader(csv));

    assertThat(result.updated()).isEqualTo(1);
    assertThat(result.inserted()).isZero();
    verify(foodRepository, never()).save(any(Food.class));
    assertThat(existing.getKcal()).isEqualByComparingTo("155");
    assertThat(existing.getCarbsG()).isEqualByComparingTo("33.0");
    assertThat(existing.getSearchCount()).isEqualTo(5);
  }

  @Test
  void FOOD_CD_누락_row는_skippedInvalid() throws IOException {
    String csv = HEADER + "\n" + ",이름만,밥류,100g,150,5.0,1.0,32.0,0.2,0.5,0.0,0.0,0.0,0.0\n";

    FoodIngestResult result = service.ingest(new StringReader(csv));

    assertThat(result.skippedInvalid()).isEqualTo(1);
    assertThat(result.inserted()).isZero();
    verify(foodRepository, never()).save(any(Food.class));
  }

  @Test
  void 빈줄은_totalRows에_안_잡힘() throws IOException {
    given(foodRepository.findByFoodApiId(anyString())).willReturn(Optional.empty());
    given(foodRepository.save(any(Food.class))).willAnswer(inv -> inv.getArgument(0));
    String csv =
        HEADER
            + "\n"
            + "\n"
            + "D000007,현미밥,밥류,100g,150,5.0,1.0,32.0,0.2,0.5,0.0,0.0,0.0,0.0\n"
            + "\n"
            + "D000008,잡곡밥,밥류,100g,160,6.0,1.2,34.0,0.3,0.7,0.0,0.0,0.0,0.0\n";

    FoodIngestResult result = service.ingest(new StringReader(csv));

    assertThat(result.totalRows()).isEqualTo(2);
    assertThat(result.inserted()).isEqualTo(2);
  }

  @Test
  void 따옴표_있는_필드_정상_파싱() throws IOException {
    given(foodRepository.findByFoodApiId(anyString())).willReturn(Optional.empty());
    given(foodRepository.save(any(Food.class))).willAnswer(inv -> inv.getArgument(0));
    String csv =
        HEADER
            + "\n"
            + "D000099,\"국밥, 돼지머리\",밥류,100g,137,6.7,5.16,15.94,0.16,0.7,181,23.82,1.47,0.03\n";

    FoodIngestResult result = service.ingest(new StringReader(csv));

    assertThat(result.inserted()).isEqualTo(1);
    ArgumentCaptor<Food> captor = ArgumentCaptor.forClass(Food.class);
    verify(foodRepository).save(captor.capture());
    assertThat(captor.getValue().getName()).isEqualTo("국밥, 돼지머리");
  }

  @Test
  void SERVING_SIZE_파싱_불가시_기본값_100() throws IOException {
    given(foodRepository.findByFoodApiId(anyString())).willReturn(Optional.empty());
    given(foodRepository.save(any(Food.class))).willAnswer(inv -> inv.getArgument(0));
    String csv = HEADER + "\n" + "D000007,현미밥,밥류,,150,5.0,1.0,32.0,0.2,0.5,0.0,0.0,0.0,0.0\n";

    service.ingest(new StringReader(csv));

    ArgumentCaptor<Food> captor = ArgumentCaptor.forClass(Food.class);
    verify(foodRepository).save(captor.capture());
    assertThat(captor.getValue().getServingSize()).isEqualByComparingTo("100");
  }
}
