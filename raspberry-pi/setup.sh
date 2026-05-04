#!/bin/bash
# GlucoFit Pi Zero 2W 초기 설정 스크립트
# 실행: chmod +x setup.sh && sudo ./setup.sh

set -e

echo "=== GlucoFit Pi 설정 시작 ==="

# 패키지 설치
apt-get update -q
apt-get install -y mpv xdotool

# 영상 디렉토리 생성
mkdir -p /home/pi/videos
chown pi:pi /home/pi/videos
echo "  [OK] 영상 디렉토리: /home/pi/videos"

# 서버 파일 복사
cp "$(dirname "$0")/server.py" /home/pi/server.py
chmod +x /home/pi/server.py
chown pi:pi /home/pi/server.py
echo "  [OK] server.py 복사 완료"

# HDMI 강제 출력 설정
CONFIG=/boot/config.txt
if ! grep -q "hdmi_force_hotplug" "$CONFIG"; then
    cat >> "$CONFIG" << 'EOF'

# GlucoFit Projection
hdmi_force_hotplug=1
hdmi_group=2
hdmi_mode=87
hdmi_cvt=854 480 60 6 0 0 0
config_hdmi_boost=4
EOF
    echo "  [OK] /boot/config.txt HDMI 설정 추가"
else
    echo "  [SKIP] HDMI 설정 이미 존재"
fi

# systemd 서비스 등록
cp "$(dirname "$0")/projection.service" /etc/systemd/system/projection.service
systemctl daemon-reload
systemctl enable projection.service
echo "  [OK] systemd 서비스 등록 및 자동시작 활성화"

echo ""
echo "=== 설정 완료 ==="
echo "영상 파일을 /home/pi/videos/ 에 복사하세요:"
echo "  - assistant_idle.mp4     (대기 루프)"
echo "  - assistant_briefing.mp4 (브리핑)"
echo "  - assistant_alert.mp4    (고혈당 경고)"
echo ""
echo "서버 시작: sudo systemctl start projection.service"
echo "로그 확인: journalctl -u projection.service -f"
echo "재부팅 후 자동 실행됩니다."
