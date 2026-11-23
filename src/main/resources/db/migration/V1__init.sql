-- 도와드림 초기 스키마 (software-architecture-plan.md 4장)

CREATE TABLE hospital (
    id               BIGSERIAL PRIMARY KEY,
    ykiho            TEXT UNIQUE,                 -- 심평원 암호화 요양기호
    hpid             TEXT,                        -- 국립중앙의료원 기관ID
    name             TEXT NOT NULL,
    type_code        TEXT,
    type_name        TEXT,
    phone            TEXT,
    address          TEXT,
    lat              DOUBLE PRECISION,
    lng              DOUBLE PRECISION,
    doctor_total     INT,
    specialist_total INT,
    avg_consult_min  DOUBLE PRECISION NOT NULL DEFAULT 5,
    waiting_factor   DOUBLE PRECISION NOT NULL DEFAULT 0.8,
    is_demo          BOOLEAN NOT NULL DEFAULT FALSE,
    synced_at        TIMESTAMPTZ
);

CREATE TABLE hospital_department (
    hospital_id      BIGINT NOT NULL REFERENCES hospital (id) ON DELETE CASCADE,
    dept_code        TEXT NOT NULL,               -- 심평원 진료과목코드 (01=내과 ...)
    dept_name        TEXT NOT NULL,
    specialist_count INT,
    PRIMARY KEY (hospital_id, dept_code)
);

CREATE TABLE hospital_hours (
    hospital_id  BIGINT NOT NULL REFERENCES hospital (id) ON DELETE CASCADE,
    day_of_week  INT NOT NULL CHECK (day_of_week BETWEEN 1 AND 8), -- 1=월 ... 7=일, 8=공휴일
    open_time    TIME,
    close_time   TIME,
    PRIMARY KEY (hospital_id, day_of_week)
);

CREATE TABLE symptom_rule (
    id           BIGSERIAL PRIMARY KEY,
    symptom      TEXT NOT NULL,
    dept_code    TEXT,
    is_emergency BOOLEAN NOT NULL DEFAULT FALSE,
    priority     INT NOT NULL DEFAULT 0
);

CREATE TABLE device (
    id           TEXT PRIMARY KEY,                -- 예: door-01
    hospital_id  BIGINT NOT NULL REFERENCES hospital (id),
    room_name    TEXT,
    key_hash     TEXT NOT NULL,                   -- X-Device-Key의 SHA-256 (16진수)
    last_seen_at TIMESTAMPTZ,
    last_rssi    INT,
    fw_version   TEXT
);

-- 장치 1분 집계 원본 (90일 보관 후 삭제 예정)
CREATE TABLE device_report (
    id             BIGSERIAL PRIMARY KEY,
    device_id      TEXT NOT NULL REFERENCES device (id),
    measured_at    TIMESTAMPTZ NOT NULL,
    people_now     INT NOT NULL,
    people_avg     DOUBLE PRECISION NOT NULL,
    people_max     INT NOT NULL,
    dwell_over_30s INT NOT NULL,
    received_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_device_report_device_time ON device_report (device_id, measured_at DESC);

-- 병원별 '지금' 대기 상태 1줄. 센서 값과 관리자 입력값은 다른 칸에 둔다.
CREATE TABLE waiting_status (
    hospital_id       BIGINT PRIMARY KEY REFERENCES hospital (id) ON DELETE CASCADE,
    admin_waiting     INT,
    admin_updated_at  TIMESTAMPTZ,
    sensor_waiting    INT,
    sensor_updated_at TIMESTAMPTZ
);

CREATE TABLE admin_user (
    id            BIGSERIAL PRIMARY KEY,
    hospital_id   BIGINT REFERENCES hospital (id),
    login_id      TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL
);
