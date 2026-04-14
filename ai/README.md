# S309 AI 서버

FastAPI 기반 AI 서비스

## 실행 방법

### 로컬 실행

```bash
# 의존성 설치
pip install -r requirements.txt

# 서버 실행 (개발 모드)
uvicorn app.main:app --reload

# http://localhost:8000/health 접속하여 확인
# http://localhost:8000/docs 에서 Swagger UI 확인
```

### uv 사용 (권장)

```bash
# 의존성 설치
uv sync

# 서버 실행
uv run uvicorn app.main:app --reload
```

### Docker 실행

```bash
docker build -t s309-ai .
docker run -p 8000:8000 s309-ai
```

## 디렉토리 구조

```
ai/
├── app/
│   ├── main.py          # FastAPI 앱 엔트리포인트
│   ├── api/             # API 라우터
│   ├── schemas/         # Pydantic 모델
│   └── core/
│       └── config.py    # 환경변수 설정
├── pyproject.toml
├── requirements.txt
├── Dockerfile
└── README.md
```
