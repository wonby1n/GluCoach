#!/bin/bash
# crontab에 등록되어 주기적으로 실행되는 SSL(Let's Encrypt) 갱신 스크립트.
# 실제 파일 복사/nginx reload는 certbot-deploy-hook.sh(배포 훅)가 갱신 성공 시 처리한다.
#
# 사전 조건 (1회성, 별도로 적용):
#   1) k14s309.p.ssafy.io 인증서 인증 방식이 standalone -> webroot 로 전환되어 있어야 함
#      (standalone은 s309-nginx가 80번 포트를 상시 점유 중이라 항상 실패한다)
#   2) /etc/letsencrypt/renewal-hooks/deploy/copy-to-nginx.sh 로
#      certbot-deploy-hook.sh 가 설치되어 있어야 함
#
# --cert-name으로 우리 도메인만 갱신 대상으로 지정한다.
# (이 서버엔 우리와 무관한 p.ssafy.io 인증서도 등록되어 있는데, 인증 방식이
#  고장나 있어 --cert-name 없이 "certbot renew"를 돌리면 매번 실패로 잡히고
#  처리가 5분 이상 걸린다. 우리 책임이 아닌 인증서라 건드리지 않는다.)
set -uo pipefail

DOMAIN="k14s309.p.ssafy.io"
LOG_FILE="/home/ubuntu/S14P31S309/infra/scripts/ssl-renew.log"

{
    echo "===== $(date '+%Y-%m-%d %H:%M:%S') SSL renew start ====="
    certbot renew --quiet --cert-name "${DOMAIN}"
    STATUS=$?
    echo "===== $(date '+%Y-%m-%d %H:%M:%S') SSL renew end (exit=${STATUS}) ====="
} >> "${LOG_FILE}" 2>&1
