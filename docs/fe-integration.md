# 프론트엔드 API 연동 가이드

프론트([dowadream-FE](https://github.com/dowadream-makers/dowadream-FE))가 이 서버를 어떻게 부르는지 정리한 문서입니다.
공통 규약은 [api-conventions.md](api-conventions.md), API 상세는 [api-spec.md](api-spec.md)에 있습니다.

## 1. 설정

```bash
# dowadream-FE/.env.local
VITE_API_BASE_URL=http://localhost:8080
```

- Vercel에서는 Project Settings → Environment Variables에 같은 이름으로 Render 주소를 넣습니다.
- 서버의 `CORS_ALLOWED_ORIGINS`에 프론트 주소(`http://localhost:5173`, Vercel 주소)가 있어야 합니다.
- Render 무료 서버는 잠들어 있으면 첫 요청이 **약 1분** 걸릴 수 있습니다. 로딩 문구를 꼭 보여 줍니다.

## 2. 코드 구조

| 파일 | 역할 |
|---|---|
| `src/api/client.ts` | `getJson()` 하나로 모든 요청. 오류면 `ApiError`(status, code, message)를 던짐 |
| `src/api/types.ts` | 서버 응답 타입 (`Hospital`, `Waiting`, `Symptom`). **서버 응답을 바꾸면 여기를 같이 고침** |
| `src/api/hospitals.ts` | `useHospitals()`, `useHospital()` |
| `src/api/symptoms.ts` | `useSymptoms()` |
| `src/lib/storage.ts` | 브라우저 저장(관심 병원, 글자 크기, 지역, 로그인 토큰) |
| `src/lib/location.ts` | 사용자 위치 (실패하면 노원구청 좌표) |

규칙:
- 컴포넌트에서 `fetch`를 직접 부르지 않고 `src/api/`의 훅만 씁니다.
- 서버 데이터는 **TanStack Query**로만 들고 있습니다(`useState`에 복사하지 않음).
- 새 API를 붙일 땐 `types.ts`에 타입 → `api/xxx.ts`에 훅 → 화면에서 사용 순서로 합니다.

## 3. 타입 (서버 응답 그대로)

```ts
export type CrowdLevel = 'RELAXED' | 'NORMAL' | 'BUSY' | 'FULL'

export interface Waiting {
  people: number
  minutes: number
  level: CrowdLevel
  source: 'ADMIN' | 'SENSOR'
  updatedAt: string // ISO, UTC
}

export interface Hospital {
  id: number
  name: string
  deptName: string | null
  departments: string[]
  typeName: string | null
  phone: string | null
  address: string | null
  lat: number | null
  lng: number | null
  specialistTotal: number | null
  distanceM: number | null
  waiting: Waiting | null // null = 정보 없음
  demo: boolean
}

export interface Symptom {
  id: number
  name: string
  category: string // '자주 찾는 증상' 이면 03 첫 화면
  deptCode: string | null
  deptName: string | null
  emergency: boolean
}

export interface ApiErrorBody {
  status: number
  code: 'INVALID_REQUEST' | 'UNAUTHORIZED' | 'FORBIDDEN' | 'NOT_FOUND' | 'INTERNAL_ERROR' | string
  message: string // 쉬운 한국어. 화면에 그대로 보여 줘도 됨
  path: string
  timestamp: string
}
```

## 4. 서버에 보내지 않는 것 (브라우저 저장)

| 키 (localStorage) | 값 | 쓰는 화면 |
|---|---|---|
| `dowadream.favorites` | 관심 병원 id 배열 `[4, 6]` | 06 별, 07, 15 |
| `dowadream.largeText` | `"1"` 이면 글자 크게 | 모든 화면 상단 "Aa" |
| `dowadream.region` | `"서울 노원구"` | 01, 02, 13 |
| `dowadream.login` | `{ token, nickname, provider }` 소셜 로그인 상태 (토큰 30일) | 13/15, 14 |

- 시안상 **관심 병원 저장은 로그인 후에만** 됩니다. 로그인 안 한 상태에서 별을 누르면 14 로그인 화면으로 보냅니다.
- 로그인은 구글·카카오·네이버 소셜 로그인입니다([social-login.md](social-login.md)). 로그아웃은 `dowadream.login`만 지우고, 관심 병원은 남겨 둡니다.
- 증상과 위치는 서버에 저장하지 않습니다. 고른 증상은 주소 쿼리(`/hospitals?dept=05&symptom=무릎이 아파요`)로만 넘깁니다.

## 5. 화면별 연동표 (시안 번호 기준)

| 화면 | 주소 | 부르는 API | 메모 |
|---|---|---|---|
| 01 홈 | `/` | 없음 | 지역은 localStorage |
| 02 지역 변경 | `/` 위 시트 | 없음 | 시안 문구대로 "지역을 바꿔도 병원 목록은 그대로" |
| 03 증상 선택 | `/symptom` | `GET /api/symptoms` | `category === '자주 찾는 증상'` 칩만. 두 개까지 선택 |
| 04 증상 더 보기 | `/symptom` (펼침) | 같은 응답 재사용 | 나머지를 `category`별로 묶음. "증상 이름 찾기"는 받은 목록에서 글자 거르기(서버 호출 없음) |
| 05 확인 경고 | `/symptom` 위 모달 | 없음 | [확인했어요] → `/hospitals?dept={첫 증상 deptCode}&symptom={이름}` |
| 06 병원 목록 | `/hospitals` | `GET /api/hospitals?dept=&lat=&lng=` | 안내 줄 "무릎이 아파요 → 정형외과"는 증상 응답의 `deptName` |
| 06 이름으로 찾기 | `/hospitals?search=1` | `GET /api/hospitals?q=&lat=&lng=` | 홈의 "병원 이름으로 찾기"에서 들어옴. 검색칸 표시 |
| 07 관심 병원 | `/hospitals?tab=fav` | `GET /api/hospitals?ids=4,6&lat=&lng=` | 관심 병원이 없으면 **호출하지 않고** 빈 안내 |
| 08 병원 정보 | `/hospitals/:id` | `GET /api/hospitals/{id}?lat=&lng=` | "3분 전 확인" = `waiting.updatedAt` |
| 09 전화 연결 | 08 위 시트 | 08 응답 재사용 | `tel:` 링크. "이렇게 물어보세요"에 고른 증상 이름 |
| 10 길찾기 | 08 위 시트 | 08 응답 재사용 | 카카오맵 링크로 넘김(아래 7장). 걷는 시간은 거리로 계산 |
| 11·12 119 | `/emergency` | 없음 | 12는 시연용 안내 모달 |
| 13·15 내 정보 | `/my` | 없음 | 로그인 여부로 13/15 전환 |
| 14 로그인 | `/login`, `/login/callback` | `GET /api/auth/providers`, `GET /api/me` | 버튼 → 서버 `/api/auth/{회사}/start`, 돌아오면 `#token` 저장 |
| 16 문서 쉽게 보기 | `/document` | 없음 | 준비 중 |
| 17 생활 도움 요청 | `/help` | 없음 (💭 `POST /api/help-requests`) | 준비 중 |

## 6. 대기 정보 표시 규칙

| `waiting` | 배지 | 사람 아이콘 | 오른쪽 |
|---|---|---|---|
| `null` | 회색 "정보 없음" | 없음 | 없음 |
| `RELAXED` | 터쿼이즈 "여유" | `min(people, 4)`개 | "N명 대기 중" / "약 M분" |
| `NORMAL` | 황토 "보통" | 〃 | 〃 |
| `BUSY` | 주황 "혼잡" | 4개 + "…" (5명 이상이면) | 〃 |
| `FULL` | 빨강 "포화" | 4개 + "…", 숫자도 빨강 | 〃 |

- 단계는 **서버의 `level`을 그대로** 씁니다. 프론트에서 인원수로 다시 계산하지 않습니다(기준이 바뀌면 서버만 고침).
- 대기 정보는 **30초마다 자동 갱신**합니다(`refetchInterval: 30_000`). 목록·상세 모두.

## 7. 위치와 지도

- 위치는 `navigator.geolocation`으로 한 번 받습니다. 거절·실패하면 **노원구청 좌표(37.6543, 127.0565)**를 씁니다.
- 위치는 쿼리로 보내 거리 계산에만 쓰고, 서버는 저장하지 않습니다.
- 길찾기는 카카오맵 링크로 넘깁니다(API 키 불필요):
  - 걸어서: `https://map.kakao.com/link/by/walk/{병원 이름},{lat},{lng}`
  - 버스·지하철: `https://map.kakao.com/link/by/traffic/{병원 이름},{lat},{lng}`
  - 병원 좌표(`lat`, `lng`)가 null이면 길찾기 버튼을 숨깁니다.
- 걷는 시간 = `ceil(distanceM / 67)`분 (시속 4km 기준 [추정]). 대중교통 시간은 무료 API가 없어 표시하지 않습니다.

## 8. 로딩·오류 처리

| 상태 | 보여 줄 것 |
|---|---|
| 로딩 | "병원을 불러오는 중이에요…" (서버가 깨는 중이면 1분까지 걸릴 수 있음) |
| `ApiError` 404 | "찾는 정보가 없어요." + 뒤로 가기 |
| 그 밖의 오류, 네트워크 실패 | `message`가 있으면 그대로, 없으면 "정보를 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요." |
| 빈 목록 `[]` | "조건에 맞는 병원이 없어요." |

- TanStack Query는 실패 시 3번 재시도합니다(기본값). 404는 재시도하지 않게 `retry`에서 걸러 둡니다(`main.tsx`).

## 9. 로컬에서 대기 정보 띄워 보기

관리자 API가 생기기 전까지는 DB에 직접 넣습니다(30분 동안 유지).

```sql
INSERT INTO waiting_status (hospital_id, admin_waiting, admin_updated_at) VALUES (4, 4, now()), (5, 12, now()), (6, 1, now())
ON CONFLICT (hospital_id) DO UPDATE SET admin_waiting = EXCLUDED.admin_waiting, admin_updated_at = now();
```

4~6번은 `V4__demo_hospitals.sql`의 시연용 가상 병원입니다.
