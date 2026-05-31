# Stage 1: 의존성 설치
FROM python:3.12-slim AS builder

WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends gcc libffi-dev && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
RUN pip install --no-cache-dir --prefix=/install -r requirements.txt

# Stage 2: 실행
FROM python:3.12-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 \
    libglib2.0-0 \
    libgomp1 \
    fonts-noto-cjk \
    libpango-1.0-0 \
    libpangoft2-1.0-0 \
    libharfbuzz0b \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /install /usr/local
COPY app app
COPY models models
COPY models models_bak
COPY docker-entrypoint.sh .
RUN sed -i 's/\r//' docker-entrypoint.sh && chmod +x docker-entrypoint.sh

# matplotlib 폰트 캐시 (패키지 복사 이후에 실행)
RUN python -c "import matplotlib.font_manager; matplotlib.font_manager._load_fontmanager(try_read_cache=False)"

EXPOSE 8000

CMD ["/app/docker-entrypoint.sh"]
