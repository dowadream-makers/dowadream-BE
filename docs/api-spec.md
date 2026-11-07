# API 명세

공통 규칙(JSON 모양, 오류, 날짜, 인증)은 [api-conventions.md](api-conventions.md)를 따릅니다. 여기엔 API별 내용만 적습니다.

상태: ✅ 구현됨 · 🕐 예정(모양 확정, 아직 없음) · 💭 미정(필요하면 논의)

## 한눈에 보기

| 상태 | 메서드·주소 | 설명 | 쓰는 곳 |
|---|---|---|---|
| ✅ | `GET /api/hospitals` | 병원 목록 (진료과·이름·id로 거르기, 거리순) | 06, 07, 15 |
| ✅ | `GET /api/hospitals/{id}` | 병원 하나 | 08, 09, 10 |
| ✅ | `GET /api/symptoms` | 증상 칩과 진료과 안내 | 03, 04, 06 안내 줄 |
| ✅ | `POST /api/devices/{deviceId}/reports` | 장치 1분 집계 보고 | ESP32 |
| 🕐 | `POST /api/admin/login` | 관리자 로그인 → 토큰 | 관리자 화면 |
| 🕐 | `GET /api/admin/hospitals/{id}` | 관리자용 병원 상태 (센서 마지막 수신 등) | 관리자 화면 |
| 🕐 | `PUT /api/admin/hospitals/{id}/waiting` | 접수 대기 인원 입력 | 관리자 화면 |
| 🕐 | `PUT /api/admin/hospitals/{id}/settings` | 평균 진료시간·보정계수 | 관리자 화면 |
| 🕐 | `PUT /api/admin/devices/{deviceId}/demo-mode` | 시연 모드 켜고 끄기 | 관리자 화면 |
| 🕐 | `POST /api/devices/{deviceId}/frames` | (시연 모드) 1초마다 레이더 점 좌표 | ESP32 |
| 🕐 | `GET /api/devices/{deviceId}/live` | (시연 화면) 점 좌표 실시간 SSE | `/admin/radar` |
| 🕐 | 병원 응답에 `openNow`, `hours` 추가 | 진료시간 (국립중앙의료원 동기화 후) | 06, 08 |
| 💭 | `POST /api/help-requests` | 생활 도움 요청 (17, 시안상 "준비 중") | 17 |
| 💭 | 문서 쉽게 보기 (16) | 사진을 서버로 보내지 않는 것이 원칙이라 서버 API 없음 | 16 |

어르신용 회원가입·로그인·관심 병원 저장 API는 **만들지 않습니다** (관심 병원은 브라우저 저장, [api-conventions.md](api-conventions.md) 6장).

---

## ✅ GET /api/hospitals

병원 목록. 조건은 함께 쓸 수 있고, 모두 선택입니다.

| 쿼리 | 타입 | 설명 |
|---|---|---|
| `dept` | string | 진료과목코드. 이 과가 있는 병원만 (`05` = 정형외과) |
| `q` | string | 병원 이름에 이 글자가 들어간 병원만 |
| `ids` | number[] | 이 id들만 (`ids=4,6`). 관심 병원 목록용. 빈 값이면 `[]` |
| `lat`, `lng` | number | 사용자 위치. 둘 다 있으면 `distanceM`을 채우고 **가까운 순**. 없으면 이름순 |

응답 `200`: `Hospital[]`

```json
[
  {
    "id": 4,
    "name": "가상 바른정형외과의원",
    "deptName": "정형외과",
    "departments": ["정형외과"],
    "typeName": "의원",
    "phone": "02-000-0000",
    "address": "가상 병원 (실제 주소 없음)",
    "lat": 37.6583,
    "lng": 127.0568,
    "specialistTotal": 2,
    "distanceM": 446,
    "waiting": {
      "people": 4,
      "minutes": 15,
      "level": "NORMAL",
      "source": "ADMIN",
      "updatedAt": "2026-11-23T11:42:24.847Z"
    },
    "demo": true
  }
]
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `deptName` | string \| null | 카드의 "정형외과 · 450m" 자리. `dept`로 찾았으면 그 과, 아니면 첫 진료과, 진료과 정보가 없으면 종별(`의원`) |
| `departments` | string[] | 이 병원의 진료과 전체 |
| `specialistTotal` | number \| null | 전문의 수. 없으면 "전문의 N명"을 숨김 |
| `distanceM` | number \| null | 직선거리(미터). 위치를 안 보냈거나 병원 좌표가 없으면 null |
| `waiting` | object \| null | **null = "정보 없음"** (최근 30분 관리자 입력도, 최근 3분 센서 보고도 없음) |
| `waiting.people` | number | 대기 인원 |
| `waiting.minutes` | number | 예상 대기 분 (5분 단위로 올림) |
| `waiting.level` | `RELAXED` \| `NORMAL` \| `BUSY` \| `FULL` | 여유 0–2 / 보통 3–5 / 혼잡 6–9 / 포화 10+ |
| `waiting.source` | `ADMIN` \| `SENSOR` | 접수 입력값인지 센서값인지 |
| `waiting.updatedAt` | ISO 시각 | 마지막 확인 시각 ("3분 전 확인") |
| `demo` | boolean | 시연용 가상 병원이면 true |

계산 규칙: README "대기시간 계산" 참고 (`waiting/WaitingCalculator.kt`).

## ✅ GET /api/hospitals/{id}

병원 하나. 응답 모양은 목록의 한 항목과 같습니다. `lat`, `lng` 쿼리를 주면 `distanceM`을 채웁니다.
없는 id면 `404 NOT_FOUND`.

## ✅ GET /api/symptoms

증상 칩 전체. 진단이 아니라 "어느 과로 가 보세요" 안내용입니다.

응답 `200`: `Symptom[]` (화면에 보일 순서대로 정렬됨)

```json
[
  { "id": 6, "name": "무릎이 아파요", "category": "자주 찾는 증상", "deptCode": "05", "deptName": "정형외과", "emergency": false },
  { "id": 7, "name": "콧물·코막힘", "category": "감기·코·목", "deptCode": "13", "deptName": "이비인후과", "emergency": false }
]
```

- `category == "자주 찾는 증상"` → 03 화면의 첫 칩 6개. 나머지는 04 화면에서 `category`별로 묶어 보여 줍니다.
- `emergency: true`인 증상은 (현재 없음) 고르면 119 안내(11)로 보냅니다.
- 데이터는 `db/migration/V3__symptom_rules.sql`. 진료과 매칭은 **[추정]**이라 팀·자문 확인 후 새 마이그레이션으로 고칩니다.

## ✅ POST /api/devices/{deviceId}/reports

장치(ESP32)의 1분 집계 보고. **이 API만 JSON이 snake_case**입니다.

헤더: `X-Device-Key: <장치 키>` (없거나 틀리면 `401`)

```json
{
  "measured_at": "2026-12-07T10:31:00+09:00",
  "people_now": 4,
  "people_avg": 3.6,
  "people_max": 5,
  "dwell_over_30s": 4,
  "rssi": -58,
  "fw": "0.3.1",
  "seq": 812
}
```

응답 `200`: `{ "ok": true, "demo_mode": false }`

- 받으면 최근 3분 `people_avg` 평균 × 병원 보정계수(기본 0.8)를 반올림해 그 병원의 센서 대기 인원으로 저장합니다.
- `demo_mode`가 true면 장치는 1초마다 `/frames`를 보냅니다(예정, 지금은 항상 false).
- 등록되지 않은 `deviceId`면 `404`.

---

## 🕐 관리자 API (예정)

모두 `Authorization: Bearer <토큰>` 필요. 다른 병원 데이터를 고치면 `403 FORBIDDEN`.

### POST /api/admin/login
```json
요청 { "loginId": "wolgye-01", "password": "..." }
응답 200 { "token": "...", "hospitalId": 4, "expiresAt": "2026-11-24T11:00:00Z" }
실패 401 UNAUTHORIZED
```

### GET /api/admin/hospitals/{id}
```json
{
  "hospital": { ...Hospital... },
  "adminWaiting": 3, "adminUpdatedAt": "...",
  "sensorWaiting": 4, "sensorUpdatedAt": "...",
  "avgConsultMin": 5, "waitingFactor": 0.8,
  "devices": [ { "id": "door-01", "roomName": "진료실1", "lastSeenAt": "...", "lastRssi": -58, "fwVersion": "0.3.1", "demoMode": false } ]
}
```

### PUT /api/admin/hospitals/{id}/waiting
```json
요청 { "people": 3 }        // 0 이상
응답 200 { ...Hospital... } // 계산된 waiting 포함
```

### PUT /api/admin/hospitals/{id}/settings
```json
요청 { "avgConsultMin": 7, "waitingFactor": 0.8 }
응답 200 { ...Hospital... }
```

### PUT /api/admin/devices/{deviceId}/demo-mode
```json
요청 { "enabled": true }
응답 200 { "id": "door-01", "demoMode": true }
```

## 🕐 시연용 레이더 (예정)

### POST /api/devices/{deviceId}/frames (장치, snake_case)
```json
{ "measured_at": "...", "targets": [ { "x": -0.4, "y": 1.6 }, { "x": 1.1, "y": 2.3 } ] }
```
DB에 저장하지 않고 메모리에서 `/live` 구독자에게 바로 넘깁니다.

### GET /api/devices/{deviceId}/live (SSE, 관리자 토큰 필요)
`Content-Type: text/event-stream`, 이벤트 이름 `frame`, 데이터는 위 frames 본문을 camelCase로 바꾼 것.

## 🕐 진료시간 (예정)

국립중앙의료원 진료시간 동기화가 생기면 `Hospital`에 아래 필드를 **추가**합니다(기존 필드는 그대로).

| 필드 | 타입 | 설명 |
|---|---|---|
| `openNow` | boolean \| null | 지금 진료 중인지. null = 진료시간 정보 없음 |
| `hours` | `{ day: 1..8, open: "09:00", close: "18:00" }[]` | 1=월 … 7=일, 8=공휴일 (상세에서만) |

`openNow == false`이면 `waiting`은 null이고 화면은 "진료 시간이 아니에요"를 보여 줍니다.
