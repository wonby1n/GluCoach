-- guardians 테이블에 SOS 연락 순서 컬럼 추가
ALTER TABLE guardians
    ADD COLUMN priority INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN guardians.priority IS 'SOS 연락 순서 (낮을수록 먼저 연락, 0부터 시작)';

CREATE INDEX idx_guardian_priority ON guardians (user_id, priority);
