# 백엔드 고도화 계획 (회로 제외)

- 작성: 2026-11-23 · 상태: **계획 (팀 확인 후 구현)**
- 범위: 공공데이터로 **노원구 병원만** 채우기, 병원 찾기 필터, 진료시간, 관리자 로그인·대기 입력, 시연용 레이더 화면의 서버 부분
- 제외: 회로·펌웨어 (장치가 보내는 JSON 모양은 [api-spec.md](api-spec.md) 그대로 유지)
- **[확인 필요]** = 공공데이터 키를 받은 뒤 실제 응답으로 확인할 것, **[추정]** = 제가 정한 값

---

## 0. 한눈에

| 단계 | 회차 | 할 일 | 끝나면 화면에서 |
|---|---|---|---|
| 1 | 11/30 | 공공데이터 연결, 노원구 병원 동기화 | 가상 병원 대신 노원구 실제 병원·좌표·전문의 수 |
| 2 | 11/30 | 병원 찾기 필터 확장 (진료과 여러 개, 반경, 종별, 정렬) | 06 목록이 증상·거리에 맞게 정렬 |
| 3 | 12/07 | 진료시간 동기화, `openNow` | "진료 시간이 아니에요" 표시 |
| 4 | 12/07 | 관리자 로그인(Spring Security), 대기 인원 입력 | 접수 담당자가 대기 인원을 넣으면 카드에 반영 |
| 5 | 12/14 | 시연 모드, 레이더 점 SSE | `/admin/radar`에서 점이 움직임 |
| 6 | 12/21~28 | 운영 정리 (데이터 정리, 모니터링, 테스트 보강) | 8시간 연속 시험 통과 |

---

## 1. 공공데이터 → 노원구 병원만 가져오기

> **구현 (2026-11-23)**: `publicdata/` 패키지와 `V5__public_data_sync.sql`. 매일 04:00 + `SYNC_ON_STARTUP=true`면 서버 시작 직후 1번.
> - 노원구 코드는 설정에 적지 않고, 동기화 때마다 노원구청 좌표 주변 응답의 `sgguCdNm=노원구` 항목에서 읽습니다.
> - 2026-11-23 실제 호출로 확인: 노원구 `sidoCd=110000`, `sgguCd=110022`, 전체 **766곳**. 응답 필드 `ykiho, yadmNm, clCd, clCdNm, addr, telno, XPos, YPos, drTotCnt, mdeptSdrCnt…` 확인. 연결이 자주 끊겨 재시도 6번.
> - 응답은 공공 API 기본값인 XML로 받습니다(`_type=json` 지원 여부와 상관없게).
> - ②는 `getDgsbjtInfo2.8`(진료과목, `dgsbjtCd`·`dgsbjtCdNm`·`dgsbjtPrSdrCnt`=과목별 전문의 수) 1번만 부릅니다. 기본 10건이라 `numOfRows=100`으로 부릅니다. ③은 노원구 755건, 쉬는 요일은 `dutyTime` 필드가 없습니다. (둘 다 2026-11-23 실제 응답으로 확인)
> - V2 임시 병원 3곳은 전화번호가 같은 심평원 병원이 오면 그 줄을 이어 쓰고(id 유지), 못 이어 쓰면 비활성.
> - 수동 실행 `POST /api/admin/sync`와 CSV 가져오기는 4단계(관리자 로그인)와 함께 만듭니다.

### 1-1. 왜 "노원구 코드"로 거르나
공공 API는 전국 데이터지만 세 API 모두 **지역으로 거르는 요청 값**이 있습니다. 반경(좌표 + m)으로 거르면 노원구 경계 밖(도봉·중랑·의정부) 병원이 섞이고 안쪽 일부가 빠지므로, **행정구역 코드로 노원구 전체**를 받고 거리 계산은 우리 서버가 합니다.

| API | 노원구로 거르는 값 | 노원구 전체 예상 건수 | 하루 호출 수 |
|---|---|---|---|
| ① 심평원 병원정보서비스 `getHospBasisList` | `sidoCd`(서울) + `sgguCd`(노원구) | 약 600~700곳 (치과·한의원 포함) **[추정]** | 100건씩 약 7번 |
| ② 심평원 의료기관별상세정보 (진료과목) | ①에서 받은 `ykiho`로 병원마다 | ①과 같음 | 병원당 1~2번 = 약 700~1,400번 (한도 10,000) |
| ③ 국립중앙의료원 병·의원 찾기 `getHsptlMdcncListInfoInqire` | `Q0=서울특별시`, `Q1=노원구` | ①과 비슷 | 100건씩 약 7번 (한도 1,000) |

**노원구 `sgguCd` 값 찾는 법 [확인 필요]**: 키를 받으면 노원구청 좌표(`xPos=127.0565&yPos=37.6543&radius=500`)로 ①을 한 번 부르면 응답 항목마다 `sidoCd`, `sgguCd`, `sgguCdNm`이 들어 있습니다. 거기서 `sgguCdNm=노원구`인 값을 `application.yml`에 적습니다. (보건의료빅데이터개방시스템 코드 조회로도 확인 가능)

### 1-2. 종별(clCd)과 치과·한의원
- 동기화는 **종별 상관없이 노원구 전체**를 받아 `type_code`에 저장합니다(나중에 바꾸기 쉬움).
- 화면에 보일지는 조회할 때 거릅니다. 기본값은 설정 `app.hospital.default-types`로 바꿀 수 있습니다.
- **치과·한의원은 기본 포함** (2026-11-23 결정). 어르신이 실제로 많이 가는 곳이기 때문입니다.
  - 한의원: 무릎·허리·어깨 통증에 침·물리치료를 받으러 많이 갑니다. 월계동 자료에서도 15곳으로 내과(13곳)보다 많습니다.
  - 치과: 65세 이상은 틀니·임플란트가 건강보험 적용이라 이용이 많고, 월계동 자료에서 21곳으로 가장 많습니다.
  - 그래서 증상 칩에 "이가 아파요·잇몸이 부어요 → 치과", "틀니가 불편해요 → 치과"를 추가하고, 근육·관절 통증은 정형외과를 먼저, 한의원을 함께 보여 주는 방식을 제안합니다(2-3, 매칭은 팀·자문 확인).
- 우리 쪽 분류 (심평원 `clCd` → 앱 분류) **[확인 필요: 코드값]**

| 앱 분류 `type` | 심평원 종별 | 기본 포함 |
|---|---|---|
| `CLINIC` 의원 | 31 의원 | ✅ |
| `HOSPITAL` 병원 | 21 병원, 11 종합병원, 01 상급종합 | ✅ |
| `PUBLIC` 보건소 | 71 보건소 등 | ✅ |
| `DENTAL` 치과 | 41 치과병원, 51 치과의원 | ✅ |
| `KOREAN_MED` 한방 | 92 한방병원, 93 한의원 | ✅ |
| `CARE` 요양·정신 | 28 요양병원, 29 정신병원 | ❌ (어르신 외래 안내 목적과 다름) |

### 1-3. 동기화 순서 (`publicdata/HospitalSyncJob`)

```
매일 04:00 (Asia/Seoul) + 관리자가 수동 실행 가능(POST /api/admin/sync)
 1) ① 노원구 전체 목록 → hospital upsert (ykiho 기준)
       이름, 종별, 전화, 주소, 좌표(XPos/YPos), 의사 총수, 전문의 수
 2) 이번에 안 나온 병원 → active = false (지우지 않음: 장치·관리자 계정이 걸려 있을 수 있음)
 3) ② 병원마다 진료과목 + 과목별 전문의 수 → hospital_department 통째로 교체
       (이미 오늘 받은 병원은 건너뜀 → 실패 후 다시 돌려도 한도 걱정 없음)
 4) ③ 노원구 목록 → ①의 병원과 짝짓기 → hospid 저장, hospital_hours 교체
 5) sync_run 표에 결과 기록 (시작·끝, 추가/변경/비활성/실패 수, 오류 메시지)
```

- **짝짓기(③↔①)**: ③에는 `ykiho`가 없어서 ① 전화번호 숫자만 같음 → ② 정규화한 이름이 같고 좌표 100m 이내 → ③ 둘 다 아니면 짝 없음(진료시간 없음으로 둠). 짝 못 찾은 병원 목록은 `sync_run`에 남겨 사람이 확인. **[추정: 기준값은 실제 데이터로 조정]**
- 이름 정규화: 공백·괄호 제거, "의원/병원" 앞의 "의료법인" 등 접두어 제거.
- **호출 방식**: Spring `RestClient`, 요청마다 타임아웃 10초, 실패 시 2번 재시도(1초, 3초 뒤), 공공 API 응답의 `resultCode`가 `00`이 아니면 실패로 기록.
- **키**: 환경변수 `PUBLIC_DATA_KEY`에 **Decoding 키**. URI 빌더가 한 번만 인코딩하게 해 `SERVICE_KEY_IS_NOT_REGISTERED_ERROR`를 피합니다.
- **키가 없으면 동기화를 건너뛰고** 로그만 남깁니다(로컬 개발은 기존 시드로 계속 가능).
- 응답 형식: `_type=json` 지원 여부 **[확인 필요]**. 안 되면 XML 파서(Jackson XML)로 받습니다.

### 1-4. 키 승인 전·장애 때
- 월계동 스프레드시트(83곳)를 CSV로 내보내 `POST /api/admin/hospitals/import`(관리자)로 올리면 같은 upsert 로직으로 들어갑니다. 좌표가 없으면 거리 정렬에서 뒤로 갑니다.
- 시연용 가상 병원(`is_demo`)은 동기화가 건드리지 않습니다. 시연 때만 켜도록 조회 기본값에서 **제외**하고 `demo=true` 쿼리로 볼 수 있게 바꿉니다 **[추정]**.

### 1-5. 표 변경 (Flyway V5~)

```sql
ALTER TABLE hospital ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE hospital ADD COLUMN app_type TEXT;            -- CLINIC, HOSPITAL ...
ALTER TABLE hospital ADD COLUMN sggu_cd TEXT;             -- 나중에 다른 구로 넓힐 때 대비
CREATE INDEX idx_hospital_dept_code ON hospital_department (dept_code);

CREATE TABLE sync_run (
  id BIGSERIAL PRIMARY KEY, source TEXT NOT NULL,        -- HIRA_BASIS, HIRA_DETAIL, NMC
  started_at TIMESTAMPTZ NOT NULL, finished_at TIMESTAMPTZ,
  inserted INT, updated INT, deactivated INT, failed INT,
  unmatched JSONB, error TEXT
);
```

### 1-6. 테스트
- 키를 받은 날 실제 응답 3개(①②③)를 `src/test/resources/publicdata/*.json`에 저장 → 파싱 테스트.
- `MockRestServiceServer`로 공공 API를 흉내 내 동기화 전체 흐름 테스트 (페이지 넘김, 실패 재시도, 비활성 처리).
- 짝짓기 함수는 순수 함수로 두고 경계값 테스트.

---

## 2. 병원 찾기 필터

### 2-1. `GET /api/hospitals` 쿼리 (기존 + 추가)

| 쿼리 | 상태 | 설명 | 기본값 |
|---|---|---|---|
| `dept` | 확장 | 진료과 코드, **쉼표로 여러 개** (`05,21` = 정형외과 또는 재활의학과) | 없음 |
| `q` | 있음 | 병원 이름 일부 | 없음 |
| `ids` | 있음 | 관심 병원 | 없음 |
| `lat`, `lng` | 있음 | 사용자 위치 | 없으면 노원구청 좌표 |
| `radius` | 신규 | 이 거리(m) 안만 | **3000** [추정] (노원구 대부분이 들어옴) |
| `types` | 신규 | `CLINIC,HOSPITAL,PUBLIC,DENTAL,KOREAN_MED` | 1-2 표의 기본 포함 (요양·정신병원만 제외) |
| `openNow` | 신규 | `true`면 지금 진료 중인 곳만 | false (전부 보여 주고 닫힌 곳은 뒤로) |
| `sort` | 신규 | `recommended`(기본) / `distance` / `waiting` / `name` | `recommended` |
| `limit` | 신규 | 최대 개수 | 30 |
| `demo` | 신규 | `true`면 시연용 가상 병원 포함 | false |

- **`recommended` 정렬** [추정]: ① 지금 진료 중 ② 대기 정보 있는 곳 중 예상 시간 짧은 순 ③ 거리순. 어르신에게 "지금 갈 수 있는 가까운 곳"이 먼저 보이게.
- 거리 계산·정렬은 지금처럼 서버 메모리에서 합니다. 노원구 수백 건이라 PostGIS 같은 확장은 쓰지 않습니다(이전 쉬움).
- 진료과로 찾았을 땐 카드의 "전문의 N명"을 **그 과의 전문의 수**(`hospital_department.specialist_count`)로 보여 줍니다. 지금은 병원 전체 수.

### 2-2. 응답 추가 필드 (기존 필드는 그대로)

| 필드 | 설명 |
|---|---|
| `type` | `CLINIC` 등 앱 분류 |
| `openNow` | true / false / null(진료시간 정보 없음) |
| `todayHours` | `{ "open": "09:00", "close": "18:00" }` 또는 null |
| `specialistInDept` | `dept`로 찾았을 때 그 과의 전문의 수 |
| `departments` | 지금 이름 배열 → `{ code, name, specialists }[]`로 변경 **(프론트 타입 같이 수정)** |

### 2-3. 증상 → 진료과 여러 개
- 지금 `symptom_rule`은 증상 하나에 과 하나. **증상 하나에 과 여러 개**(예: 무릎 → 정형외과, 재활의학과)로 바꿉니다.
  - `symptom_dept (symptom_id, dept_code, dept_name, rank)` 표 추가, `/api/symptoms` 응답에 `depts: [{code, name}]`.
  - 프론트는 `dept=05,21`로 목록 요청, 안내 줄은 첫 과 이름.
- 매칭표는 **팀·자문 확인 후** 마이그레이션으로 고칩니다 (지금 값은 [추정]).
- 제안 [추정]: 무릎·허리·어깨·손발 저림 → 정형외과(1순위) + 한방(2순위), 이·잇몸·틀니 → 치과. 한방은 심평원 진료과목 코드(한방내과 등 80번대 [확인 필요])로 묶습니다.

### 2-4. 진료과 코드
- 우리 `dept_code`는 **심평원 진료과목코드**(②의 `dgsbjtCd`) 하나로 통일합니다. ③(국립중앙의료원)의 `D001~` 코드는 진료시간만 쓰므로 저장하지 않습니다.
- 코드표는 `department` 표(코드, 이름, 앱에 보일지)로 만들어 하드코딩을 없앱니다.

---

## 3. 진료시간과 "진료 시간이 아니에요"

- ③의 `dutyTime{1..8}s/c` (예: `0830`/`1830`, 1=월 … 7=일, 8=공휴일) → `hospital_hours`.
- **지금 진료 중 판단** (`OpeningHours` 순수 함수, Asia/Seoul 기준):
  - 오늘이 공휴일이면 8번 줄, 아니면 요일 줄
  - 시작 ≤ 지금 < 종료면 진료 중. 종료가 `2400` 이상이면 자정 넘김 처리
  - 그날 줄이 없으면 휴진, 병원에 시간 정보가 아예 없으면 null
  - 점심시간 정보는 공공데이터에 없음 → 고려하지 않고 문서·화면에 "점심시간은 병원에 확인" 안내
- **공휴일**: 시연 기간(12~1월)만 필요하므로 `public_holiday(date, name)` 표에 2026-12-25, 2027-01-01 등을 마이그레이션으로 넣습니다 [추정]. 필요하면 한국천문연구원 특일정보 API를 나중에 붙입니다.
- `WaitingCalculator`: `openNow == false`면 `waiting = null`, 화면은 "진료 시간이 아니에요". (`openNow == null`이면 지금처럼 대기 정보만 판단)
- 테스트: 요일·공휴일·자정 넘김·정보 없음 경계값.

---

## 4. 관리자 로그인과 대기 인원 입력

### 4-1. 인증 방식
- **Spring Security + JWT(HS256) Bearer 토큰**. 프론트(Vercel)와 서버(Render) 주소가 달라서 쿠키 세션보다 단순합니다.
  - 의존성: `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server` (JWT 검증 내장)
  - 비밀값 `ADMIN_JWT_SECRET`(32바이트 이상, Render 환경변수), 유효기간 12시간
- 경로별 권한

| 경로 | 권한 |
|---|---|
| `/api/hospitals/**`, `/api/symptoms`, `/actuator/health`, Swagger | 누구나 |
| `/api/devices/*/reports`, `/frames` | 장치 키 필터 (`X-Device-Key`) — 지금 서비스 안의 검사를 Security 필터로 옮김 |
| `/api/admin/login` | 누구나 (5분에 5번 실패하면 잠금 [추정]) |
| `/api/admin/**` | `ROLE_HOSPITAL_ADMIN` (자기 병원만) / `ROLE_SUPER_ADMIN` (팀, 전체 + 동기화 실행) |

- **계정 만들기**: 회원가입 화면 없음. 서버 시작 시 환경변수 `ADMIN_BOOTSTRAP_LOGIN` / `ADMIN_BOOTSTRAP_PASSWORD`가 있으면 슈퍼 관리자를 만들고, 병원 계정은 슈퍼 관리자가 `POST /api/admin/users`로 만듭니다. 비밀번호는 BCrypt.
- `admin_user`에 `role` 칸 추가.

### 4-2. API (모양은 [api-spec.md](api-spec.md) 관리자 절 그대로)
- `POST /api/admin/login`, `GET /api/admin/hospitals/{id}`, `PUT .../waiting`, `PUT .../settings`
- 추가: `POST /api/admin/users`(슈퍼), `POST /api/admin/sync`(슈퍼, 1장 수동 동기화), `POST /api/admin/hospitals/import`(슈퍼, CSV)
- 다른 병원을 고치면 `403 FORBIDDEN`.
- 대기 인원 입력은 `waiting_status.admin_waiting`만 바꾸고 센서 칸은 건드리지 않습니다(계획서 요구).

### 4-3. 프론트 (참고)
`/admin` 로그인 → 대기 인원 +/− 버튼 화면 → 설정. 어르신 화면에서는 링크하지 않습니다. 시안이 없으니 기존 컴포넌트로 단순하게.

---

## 5. 시연용 레이더 화면 (서버 부분만)

- `device.demo_mode BOOLEAN` 칸 추가. 관리자가 `PUT /api/admin/devices/{id}/demo-mode`로 켜면 다음 `/reports` 응답이 `demo_mode: true` → 장치가 1초마다 `/frames` 전송.
- `LiveFrameHub`: 장치 id별 `SseEmitter` 목록을 메모리에 들고, `/frames`가 오면 구독자에게 바로 보냄. **DB에 저장하지 않음**(개인정보 원칙).
- `GET /api/devices/{id}/live` (관리자 토큰): `text/event-stream`, 15초마다 빈 주석으로 연결 유지(Render 프록시가 끊지 않게), 끊기면 브라우저 `EventSource`가 자동 재연결.
- 10분 동안 `/frames`가 없으면 시연 모드를 자동으로 끔 (장치가 계속 1초 전송하는 것 방지) [추정].
- Render 무료는 서버 1대라 메모리 허브로 충분. 여러 대가 되면 Redis pub/sub으로 바꿉니다(지금은 하지 않음).
- `EventSource`는 헤더를 못 붙이므로 토큰은 `?token=`이 아니라 **짧은 1회용 티켓**(`POST /api/admin/live-ticket` → 60초 유효)으로 넘깁니다. (규약: 토큰을 URL에 넣지 않음)

---

## 6. 운영 정리

| 항목 | 방법 |
|---|---|
| 오래된 장치 보고 정리 | 매일 04:30, `device_report` 90일 지난 줄 삭제 |
| 동기화 실패 알림 | `sync_run` 실패 시 관리자 화면 상단 경고 + 로그 ERROR |
| 상태 확인 | `/actuator/health`에 DB + "마지막 동기화 26시간 넘음" 경고 포함 |
| 시간대 | DB·응답은 UTC, 진료시간·스케줄 판단만 `Asia/Seoul` (`app.zone`) |
| 테스트 | 순수 함수(대기·진료시간·짝짓기)는 단위 테스트, API·DB는 GitHub Actions에서 Testcontainers(PostgreSQL)로 통합 테스트 |
| 메모리 | 의존성 추가(Security) 후에도 Render 512MB 안인지 배포 뒤 확인 |

---

## 7. 팀이 정할 것

1. ~~치과·한의원을 기본 목록에 넣을지~~ → **넣기로 함** (1-2). 증상 칩 추가안(2-3)만 확인
2. 기본 **검색 반경**(3km)과 **추천 정렬** 순서(진료 중 → 대기 짧음 → 거리)가 맞는지
3. **증상 → 진료과 매칭표** 확인 (V3 값과 2-3의 여러 과 매칭)
4. 시연 때 **가상 병원**을 실제 병원과 섞어 보여 줄지, 따로 켤지
5. 관리자 계정: 팀용 슈퍼 관리자 1개 + 시연 병원 계정 몇 개
6. **공공데이터 키 신청**: 아직 안 함 (2026-11-23). 1단계는 키가 있어야 시작할 수 있습니다 (`architecture/public-data-guide.md` 3장)
