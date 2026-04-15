-- S309 초기 데이터베이스 설정
-- 이 파일은 PostgreSQL 컨테이너 최초 실행 시 자동으로 실행됩니다.
-- POSTGRES_DB 환경변수가 데이터베이스를 자동 생성하므로, 여기서는 확장 기능만 활성화합니다.

-- JSONB 고속 조회용 GIN 인덱스 지원
-- (PostgreSQL 기본 설치에 포함되어 있어 별도 설치 불필요)

-- UUID 생성 함수
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 한국어 전문 검색용 (선택)
-- CREATE EXTENSION IF NOT EXISTS pg_trgm;
