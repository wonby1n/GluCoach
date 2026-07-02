#!/bin/bash
# certbot deploy hook: 인증서 갱신 성공 시 certbot이 자동으로 호출한다.
# 설치 위치: /etc/letsencrypt/renewal-hooks/deploy/copy-to-nginx.sh (root:root, +x)
# certbot은 갱신된 도메인마다 RENEWED_LINEAGE 환경변수를 넣어서 이 훅을 실행한다.
set -euo pipefail

DOMAIN="k14s309.p.ssafy.io"
SSL_DIR="/home/ubuntu/ssl"

# 여러 도메인의 인증서를 한 서버에서 갱신할 수 있으므로,
# 우리가 신경 쓰는 도메인이 갱신된 경우에만 nginx로 복사한다.
if [[ "${RENEWED_LINEAGE:-}" != *"/${DOMAIN}"* ]]; then
    exit 0
fi

cp "${RENEWED_LINEAGE}/fullchain.pem" "${SSL_DIR}/fullchain.pem"
cp "${RENEWED_LINEAGE}/privkey.pem" "${SSL_DIR}/privkey.pem"
chmod 644 "${SSL_DIR}/fullchain.pem"
chmod 600 "${SSL_DIR}/privkey.pem"

docker exec s309-nginx nginx -s reload
