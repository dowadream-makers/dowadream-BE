-- 회원 (소셜 로그인: 구글·카카오·네이버). 이름·이메일·전화번호는 저장하지 않는다.
CREATE TABLE member (
    id            BIGSERIAL PRIMARY KEY,
    nickname      TEXT NOT NULL,
    auth_provider TEXT NOT NULL,                 -- GOOGLE, KAKAO, NAVER
    provider_id   TEXT NOT NULL,                 -- 각 회사가 준 회원 번호
    large_text    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ,
    deleted_at    TIMESTAMPTZ,                   -- 탈퇴하면 채움 (운영자 화면 집계에서 뺌)
    UNIQUE (auth_provider, provider_id)
);
CREATE INDEX idx_member_created ON member (created_at);

-- 관심 병원을 서버에 저장 (지금은 브라우저에만 저장, 화면 연결은 다음 단계)
CREATE TABLE member_favorite (
    member_id   BIGINT NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    hospital_id BIGINT NOT NULL REFERENCES hospital (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (member_id, hospital_id)
);
