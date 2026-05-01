-- V3: password 컬럼 추가 (V1에 이미 포함된 경우 무시)
ALTER TABLE users ADD COLUMN IF NOT EXISTS password VARCHAR(255);
