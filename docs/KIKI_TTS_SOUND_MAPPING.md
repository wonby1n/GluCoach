# 키키 TTS 사운드 매핑

TTS API 대신 로컬 MP3 파일(`res/raw/`)을 사용해 키키 음성을 재생한다.  
FCM `alertType` → MP3 파일 매핑은 `FcmService.kt` `resIdForAlertType()` 에서 관리한다.

---

## 현재 등록된 파일

| 파일명 | 녹음 텍스트 | alertType | 상태 |
|--------|------------|-----------|------|
| `kiki_morning.mp3` | 좋은 아침이에요. 어젯밤 수면이 부족했어요. 저녁 혈당이 200까지 올라서 조심하셔야 돼요. 오늘 식사를 같이 확인해볼까요? | `AGENT_WAKE_UP` | ✅ |
| `kiki_walk.mp3` | 지금 10분만 걸으면 좋아요. | `AGENT_MEAL_FOLLOWUP` | ✅ |
| `kiki_stretch.mp3` | 회의 끝났나요? 잠깐 스트레칭 어때요? | `AGENT_MEAL_RETRY` | ✅ |
| `kiki_daily_done.mp3` | 오늘 하루 수고했어요. 어제보다 혈당 변동 폭이 안정적이에요. | `AGENT_SLEEP_INSIGHT` | ✅ |

---

## 앱 내부 트리거 (FCM 아님)

FCM 없이 앱 로직에서 직접 `ttsManager.playSound()`를 호출해야 하는 케이스.  
해당 ViewModel 또는 Screen에서 직접 추가해야 한다.

| 파일명 | 녹음 텍스트 | 트리거 위치 |
|--------|------------|------------|
| `kiki_meal_spike.mp3` | 현재 식사 기준 혈당 상승 가능성이 높아요. | 음식 분석 결과 화면 |
| `kiki_good.mp3` | 좋아요. | 스트레칭 완료 응답 시 |
| `kiki_compare_result.mp3` | 마라탕보다 샤브샤브가 더 안정적이에요. | 음식 비교 결과 화면 |
| `kiki_maratang.mp3` | 잠깐. 오늘 마라탕은 혈당이 많이 오를 수 있어요. 다른 메뉴도 같이 볼까요? | `AGENT_GENERIC` FCM 또는 키보드 개입 |

---

## 혈당 경보 사운드 (BLE 트리거)

`GlucoseAlertManager.kt` 에서 혈당 수치에 따라 자동 매핑된다.

| 파일명 | 조건 |
|--------|------|
| `alert_very_low.mp3` | 혈당 55 미만 |
| `alert_low.mp3` | 혈당 55 ~ 65 |
| `alert_borderline.mp3` | 혈당 65 ~ 70 |
| `alert_very_high.mp3` | 혈당 250 초과 |
| `alert_high.mp3` | 혈당 200 ~ 250 |
| `alert_slightly_high.mp3` | 혈당 180 ~ 200 |
| `alert_rising.mp3` | 빠르게 상승 중 |
| `alert_falling.mp3` | 빠르게 하강 중 |

---

## 파일 추가 방법

1. MP3 파일을 `frontend/app/src/main/res/raw/` 에 넣는다  
   (파일명: 소문자 + 언더스코어만 허용)
2. `FcmService.kt` `resIdForAlertType()` 에 alertType → `R.raw.파일명` 매핑 추가
3. 앱 내부 트리거라면 해당 ViewModel에 `ttsManager.playSound(R.raw.파일명)` 추가
