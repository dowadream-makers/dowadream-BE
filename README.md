# 도와드림 백엔드

「도와드림」의 API 서버입니다. Spring Boot 4 + Kotlin, PostgreSQL(Supabase), Render(Docker)에 배포합니다.
병원 정보와 대기 인원·예상 대기시간을 프론트에 주고, 진료실 앞 레이더 장치(ESP32 + LD6001A)의 보고를 받습니다.

## 실행

JDK 21이 필요합니다. Gradle은 저장소에 든 `./gradlew`를 씁니다.

```bash
docker compose up -d     # 로컬 PostgreSQL (DB/계정/비밀번호 모두 dowadream)
./gradlew bootRun        # http://localhost:8080
./gradlew test           # 테스트
```

- API 문서(Swagger): http://localhost:8080/swagger-ui/index.html
- 상태 확인: http://localhost:8080/actuator/health
- 처음 실행하면 Flyway가 표를 만들고 월계동 내과 3곳을 임시로 넣습니다(`db/migration`).

다른 DB(예: Supabase)를 쓰려면 환경변수로 바꿉니다. 목록은 `.env.example`에 있습니다. **비밀번호·API 키는 커밋하지 말고** Render 환경변수에 넣으세요.

| 환경변수 | 기본값 |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/dowadream` |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | `dowadream` / `dowadream` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` (쉼표로 여러 개, Vercel 주소 추가) |

## 폴더 구조

```
src/main/kotlin/kr/dowadream/
├ hospital/   병원 목록·상세 API
├ waiting/    WaitingCalculator(대기시간 계산, 순수 함수), WaitingStatus(병원별 현재 대기)
├ device/     장치 보고 수신 API
└ config/     CORS
src/main/resources/
├ application.yml
└ db/migration/   V1__init.sql(표), V2__seed_wolgye_sample.sql(임시 병원 3곳)
```

## API

| API | 설명 |
|---|---|
| `GET /api/hospitals?dept=01&lat=&lng=` | 진료과로 거르고, 위치를 주면 가까운 순. 대기 정보 포함 |
| `GET /api/hospitals/{id}` | 병원 하나 |
| `POST /api/devices/{deviceId}/reports` | 장치 1분 집계 (헤더 `X-Device-Key`) |

`waiting`이 `null`이면 "정보 없음"입니다.

## 대기시간 계산 (`WaitingCalculator`)

1. 관리자 입력이 30분 이내면 그 값
2. 아니면 센서 보고가 3분 이내면 `round(최근 3분 평균 인원 × 보정계수 0.8)`
3. 둘 다 없으면 정보 없음
- 예상 분 = `ceil(대기 인원 × 평균 진료시간 ÷ max(의사 수, 1) ÷ 5) × 5`
- 혼잡 단계: 여유 0–2 / 보통 3–5 / 혼잡 6–9 / 포화 10+

## 장치 등록과 테스트

장치 키 원문은 저장하지 않고 SHA-256만 DB에 넣습니다.

```bash
KEY=아주-긴-랜덤-문자열
HASH=$(printf %s "$KEY" | sha256sum | cut -d' ' -f1)
psql ... -c "INSERT INTO device (id, hospital_id, room_name, key_hash) VALUES ('door-01', 1, '진료실1', '$HASH')"

# 장치 없이 curl로 보고 흉내
curl -X POST localhost:8080/api/devices/door-01/reports \
  -H "X-Device-Key: $KEY" -H 'Content-Type: application/json' \
  -d '{"measured_at":"2026-12-07T10:31:00+09:00","people_now":4,"people_avg":3.6,"people_max":5,"dwell_over_30s":4,"rssi":-58,"fw":"0.3.1","seq":812}'
```

## Render 배포

1. Render에서 New > Web Service, 이 저장소 선택, Runtime은 **Docker** (`Dockerfile` 사용)
2. 환경변수에 `DATABASE_URL`(Supabase의 JDBC 주소, `?sslmode=require`), `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `CORS_ALLOWED_ORIGINS`(Vercel 주소)
3. Health Check Path: `/actuator/health`

무료 플랜은 RAM 512MB라 `Dockerfile`에서 힙을 300MB로 잡아 두었습니다.

## 아직 없는 것 (계획서 순서)

- 공공데이터(심평원·국립중앙의료원) 하루 1번 동기화 → 좌표·의사 수·진료시간 채우기
- 진료시간 밖이면 "진료 시간이 아니에요"
- 관리자 로그인과 대기 인원 입력 (`/api/admin/**`, Spring Security)
- 시연용 레이더 점 화면 (`/frames`, `/live` SSE)
