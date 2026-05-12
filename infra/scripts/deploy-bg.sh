#!/bin/bash
set -euo pipefail

DEPLOY_DIR="/home/ubuntu/S14P31S309"
INFRA_DIR="${DEPLOY_DIR}/infra"
NGINX_UPSTREAM="${INFRA_DIR}/nginx/upstream.conf"
ACTIVE_COLOR_FILE="${INFRA_DIR}/.active-color"

if [ -f "${ACTIVE_COLOR_FILE}" ]; then
    CURRENT=$(cat "${ACTIVE_COLOR_FILE}")
else
    CURRENT="blue"
fi

if [ "${CURRENT}" = "blue" ]; then
    NEXT="green"
else
    NEXT="blue"
fi

echo "[blue-green] 현재 활성: ${CURRENT} → 다음 배포: ${NEXT}"

# 1. 새 색상 컨테이너 빌드 및 시작
echo "[blue-green] ${NEXT} 환경 빌드 및 시작..."
docker compose \
    -f "${INFRA_DIR}/docker-compose.infra.yml" \
    -f "${INFRA_DIR}/docker-compose.${NEXT}.yml" \
    --env-file "${INFRA_DIR}/.env" \
    up -d --build backend-${NEXT} ai-${NEXT}

# 2. 헬스체크: 최대 3분 대기
echo "[blue-green] 헬스체크 대기 중..."
MAX_WAIT=180
ELAPSED=0
until docker inspect --format='{{.State.Health.Status}}' s309-backend-${NEXT} 2>/dev/null | grep -q "healthy"; do
    if [ ${ELAPSED} -ge ${MAX_WAIT} ]; then
        echo "[blue-green] 헬스체크 타임아웃. 배포 중단 후 ${NEXT} 정리..."
        docker compose \
            -f "${INFRA_DIR}/docker-compose.infra.yml" \
            -f "${INFRA_DIR}/docker-compose.${NEXT}.yml" \
            --env-file "${INFRA_DIR}/.env" \
            stop backend-${NEXT} ai-${NEXT}
        docker compose \
            -f "${INFRA_DIR}/docker-compose.infra.yml" \
            -f "${INFRA_DIR}/docker-compose.${NEXT}.yml" \
            --env-file "${INFRA_DIR}/.env" \
            rm -f backend-${NEXT} ai-${NEXT}
        exit 1
    fi
    sleep 5
    ELAPSED=$((ELAPSED + 5))
done
echo "[blue-green] ${NEXT} 헬스체크 통과 (${ELAPSED}s)"

# 3. nginx upstream 전환 (reload = 무중단)
echo "[blue-green] nginx upstream → ${NEXT} 전환..."
cp "${INFRA_DIR}/nginx/upstream.${NEXT}.conf" "${NGINX_UPSTREAM}"
docker exec s309-nginx nginx -s reload

# 4. 활성 색상 기록
echo "${NEXT}" > "${ACTIVE_COLOR_FILE}"
echo "[blue-green] 트래픽 전환 완료: ${CURRENT} → ${NEXT}"

# 5. 이전 색상 컨테이너 정리 (60초 grace period)
echo "[blue-green] 60초 후 ${CURRENT} 환경 정리..."
sleep 60
echo "[blue-green] ${CURRENT} 환경 정리..."
docker compose \
    -f "${INFRA_DIR}/docker-compose.infra.yml" \
    -f "${INFRA_DIR}/docker-compose.${CURRENT}.yml" \
    --env-file "${INFRA_DIR}/.env" \
    stop backend-${CURRENT} ai-${CURRENT}

docker compose \
    -f "${INFRA_DIR}/docker-compose.infra.yml" \
    -f "${INFRA_DIR}/docker-compose.${CURRENT}.yml" \
    --env-file "${INFRA_DIR}/.env" \
    rm -f backend-${CURRENT} ai-${CURRENT}

echo "[blue-green] 배포 완료. 활성 환경: ${NEXT}"
