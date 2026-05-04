# GlucoFit 프로젝터 테스트 가이드

매번 테스트할 때 이 순서대로 하면 됩니다.

---

## 준비물 체크

```
□ Raspberry Pi Zero 2W (보조배터리 연결)
□ YG300 프로젝터 (전원 켜기)
□ mini-HDMI → HDMI 케이블 연결 확인
□ PC와 Pi가 같은 Wi-Fi에 연결되어 있는지 확인
```

---

## STEP 1 — YG300 HDMI 입력으로 전환 (매번 필수)

YG300을 켜면 기본적으로 **자체 화면**이 나옵니다.
Pi 화면을 보려면 입력 소스를 바꿔야 합니다.

1. YG300 리모컨 또는 본체 버튼에서 **SOURCE** / **INPUT** 버튼 누르기
2. **HDMI** 선택
3. Pi 데스크탑 화면(바탕화면, 작업표시줄)이 보이면 OK

---

## STEP 2 — SSH 접속

**Windows PowerShell** 열고:

```powershell
ssh kiki@glucofit-pi.local
```

비밀번호 입력 (설정한 비밀번호).

> 안 되면 IP로 직접 접속:
> ```powershell
> ssh kiki@192.168.0.100
> ```

---

## STEP 3 — 서비스 상태 확인

```bash
sudo systemctl status projection.service
```

`Active: active (running)` 이면 OK.

혹시 꺼져 있으면:
```bash
sudo systemctl start projection.service
```

---

## STEP 4 — 소켓 명령어 테스트

SSH 창 두 개 열고:

**창 1 — 로그 실시간 보기:**
```bash
journalctl -u projection.service -f
```

**창 2 — 명령 전송:**

```bash
# 비서 영상 켜기
echo "SHOW" | nc -w 1 localhost 9999

# 비서 영상 끄기
echo "HIDE" | nc -w 1 localhost 9999

# 브리핑 영상 (수면점수:혈당)
echo "BRIEFING:78:105" | nc -w 1 localhost 9999

# 고혈당 경고 영상
echo "ALERT" | nc -w 1 localhost 9999
```

창 1에서 `명령 수신: 'SHOW'` 로그 뜨고 프로젝터에 영상 나오면 성공.

---

## STEP 5 — Android 앱 연결

1. 앱 실행
2. **설정** → **프로젝터 제어**
3. Pi IP 입력: `192.168.0.100`
4. **Pi 연결** 버튼
5. **비서 ON** 버튼 → 프로젝터에 영상 나오면 완성

---

## 자주 있는 문제

| 증상 | 해결 |
|------|------|
| YG300에 Pi 화면 안 나옴 | YG300 INPUT → HDMI 선택했는지 확인 |
| SSH 접속 안 됨 | 같은 Wi-Fi인지 확인, IP로 재시도 |
| `nc` 명령 후 아무 반응 없음 | 서비스 상태 확인 (`systemctl status`) |
| 영상이 안 나오고 로그만 뜸 | `env=_env` 가 Popen에 있는지 server.py 확인 |
| 재부팅 후 서비스 안 뜸 | `sudo systemctl enable projection.service` |

---

## 영상 파일 교체 방법

새 영상으로 바꾸고 싶을 때 (PowerShell에서):

```powershell
scp 새영상.mp4 kiki@glucofit-pi.local:/home/kiki/videos/assistant_idle.mp4
```

파일명은 반드시 아래 3개로 맞추기:
- `assistant_idle.mp4` — 대기 영상 (루프)
- `assistant_briefing.mp4` — 브리핑 영상
- `assistant_alert.mp4` — 고혈당 경고 영상

---

## Pi 재부팅 필요할 때

```bash
sudo reboot
```

1분 후 SSH 재접속하면 서비스 자동 시작됩니다.
