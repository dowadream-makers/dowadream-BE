-- 공공데이터 동기화 (docs/backend-roadmap.md 1장)

ALTER TABLE hospital ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;  -- 이번 동기화에 안 나온 병원은 지우지 않고 FALSE
ALTER TABLE hospital ADD COLUMN app_type TEXT;                         -- CLINIC, HOSPITAL, PUBLIC, DENTAL, KOREAN_MED, CARE, OTHER
ALTER TABLE hospital ADD COLUMN sggu_cd TEXT;                          -- 심평원 시군구 코드
ALTER TABLE hospital ADD COLUMN dept_synced_at TIMESTAMPTZ;            -- 진료과목(상세정보) 마지막 동기화
CREATE INDEX idx_hospital_dept_code ON hospital_department (dept_code);

CREATE TABLE sync_run (
    id          BIGSERIAL PRIMARY KEY,
    source      TEXT NOT NULL,                 -- HIRA_BASIS, HIRA_DETAIL, NMC
    started_at  TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    inserted    INT,
    updated     INT,
    deactivated INT,
    failed      INT,
    unmatched   JSONB,                         -- NMC 진료시간을 붙이지 못한 병원 이름
    error       TEXT
);
