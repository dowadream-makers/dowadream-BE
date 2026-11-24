# 도와드림 백엔드 문서

| 문서 | 내용 | 누가 보나 |
|---|---|---|
| [api-conventions.md](api-conventions.md) | API 공통 규약 (주소, JSON, 날짜, 오류 형식, 인증, 버전) | 모두. 새 API를 만들기 전에 먼저 |
| [api-spec.md](api-spec.md) | API 하나하나의 요청·응답 (구현됨 / 예정) | BE, FE, 장치 담당 |
| [fe-integration.md](fe-integration.md) | 프론트가 화면별로 어떤 API를 어떻게 부르는지 | FE |
| [social-login.md](social-login.md) | 구글·카카오·네이버 로그인 흐름, Render 환경변수, 키 발급 방법 | BE, 팀 (키 발급 담당) |
| [backend-roadmap.md](backend-roadmap.md) | 백엔드 고도화 계획 (노원구 공공데이터, 필터, 진료시간, 관리자, 시연 SSE) | BE, 팀 |

- 실행 중인 서버의 자동 문서: `/swagger-ui/index.html` (로컬: http://localhost:8080/swagger-ui/index.html)
- 전체 설계: 프로젝트 공유 폴더 `architecture/software-architecture-plan.md`
- 화면 기준: 최종 시안 `ux-v9/dowadream-final-screens.html` (화면 번호 01~18)

**문서를 고치는 규칙**: API를 추가하거나 바꾸면 같은 커밋에서 `api-spec.md`의 표와 상태를 같이 고칩니다. 응답 모양을 바꾸면 `fe-integration.md`의 타입도 고칩니다.
