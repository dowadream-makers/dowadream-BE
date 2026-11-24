# 소셜 로그인 (구글·카카오·네이버)

회원가입 화면 없이 **쓰던 계정으로 바로 가입·로그인**합니다. 서버는 각 회사가 준 **회원 번호와 별명만** 저장하고(`member` 표), 이름·이메일·전화번호는 요청하지 않습니다.

## 흐름

```
프론트 /login  ── 버튼 ──▶ 서버 GET /api/auth/{google|kakao|naver}/start
                              │ (state 쿠키 저장, 10분)
                              ▼
                         각 회사 로그인·동의 화면
                              │
                              ▼
서버 GET /api/auth/{회사}/callback?code=..&state=..
   1) state 확인  2) code → access_token  3) 회원 번호·별명 조회
   4) member 가입/로그인  5) 회원 토큰(JWT, 30일) 발급
                              │
                              ▼
프론트 /login/callback#token=...  →  GET /api/me 로 확인 → 원래 화면으로
```

- 토큰은 주소의 `#` 뒤로 넘겨서 서버 로그·Referer에 남지 않고, 프론트가 받자마자 주소창에서 지웁니다.
- 실패하면 `/login/callback#error=cancelled|state|failed` → 프론트가 "다시 로그인하기"를 보여 줍니다.
- `client-id`가 비어 있는 회사는 `GET /api/auth/providers` 목록에서 빠지고, 로그인 화면에 버튼이 안 나옵니다. 키를 하나씩 넣어도 됩니다.

## Render 환경변수

| 이름 | 값 |
|---|---|
| `AUTH_JWT_SECRET` | 32자 이상 무작위 문자열 (예: `openssl rand -base64 48`). 없으면 서버를 켤 때마다 바뀌어서 재배포 후 모두 다시 로그인해야 함 |
| `BACKEND_URL` | 이 서버 주소, 예: `https://dowadream-be.onrender.com` (콜백 주소를 만듦) |
| `FRONTEND_URL` | 프론트 주소, 예: `https://dowadream.vercel.app` |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | 구글 클라우드 콘솔 |
| `KAKAO_CLIENT_ID` | 카카오 **REST API 키** |
| `KAKAO_CLIENT_SECRET` | 카카오에서 "Client Secret"을 켰을 때만 |
| `NAVER_CLIENT_ID` / `NAVER_CLIENT_SECRET` | 네이버 개발자센터 |

로컬에서는 프로젝트 폴더의 `.env`(커밋 안 됨)에 같은 이름으로 넣으면 됩니다.

## 키 발급 (각 회사에 등록할 콜백 주소)

콜백 주소 = `{BACKEND_URL}/api/auth/{회사}/callback`. 로컬 시험용 `http://localhost:8080/...`도 같이 등록해 두면 편합니다.

### 카카오 — https://developers.kakao.com
1. 내 애플리케이션 → 애플리케이션 추가 (앱 이름 "도와드림")
2. 앱 키의 **REST API 키** → `KAKAO_CLIENT_ID`
3. 제품 설정 → 카카오 로그인 → 활성화 ON, Redirect URI에 `https://<백엔드>/api/auth/kakao/callback`
4. 동의항목 → **닉네임(profile_nickname)** 만 "필수 동의". 나머지는 사용 안 함
5. 앱 설정 → 플랫폼 → Web에 프론트 주소 등록

### 네이버 — https://developers.naver.com/apps
1. Application 등록 → 사용 API "네이버 로그인"
2. 제공 정보: **별명**만 필수로 체크
3. 서비스 URL = 프론트 주소, Callback URL = `https://<백엔드>/api/auth/naver/callback`
4. Client ID / Client Secret → `NAVER_CLIENT_ID` / `NAVER_CLIENT_SECRET`
5. 검수 전에는 "멤버관리"에 등록한 네이버 계정만 로그인됩니다. 팀원 계정을 넣고 시연하거나 검수를 요청합니다.

### 구글 — https://console.cloud.google.com
1. 프로젝트 만들기 → API 및 서비스 → OAuth 동의 화면 (외부, 앱 이름 "도와드림", 범위는 기본 `openid`, `profile`만)
2. 사용자 인증 정보 → OAuth 클라이언트 ID → 웹 애플리케이션
3. 승인된 리디렉션 URI = `https://<백엔드>/api/auth/google/callback`
4. 클라이언트 ID / 보안 비밀 → `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`
5. "테스트" 상태에서는 테스트 사용자로 넣은 구글 계정만 로그인됩니다. 시연 전에 "프로덕션으로 게시"합니다(기본 범위만 쓰면 심사 없이 됨).

## 코드 위치
- `auth/OAuthController.kt` (start, callback, providers), `auth/OAuthProviders.kt` (회사별 주소·응답 읽기), `auth/Jwt.kt`, `auth/MemberTokens.kt`
- `member/` (회원 표, `GET /api/me`, `DELETE /api/me`), `db/migration/V6__member.sql`
- 프론트: `src/pages/LoginPage.tsx`, `src/pages/LoginCallbackPage.tsx`, `src/api/auth.ts`, `src/lib/storage.ts`(`useLogin`)
