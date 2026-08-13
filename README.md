# 피모북 (Fimobook)

FC Mobile 선수 카드를 검색하고 상세 정보를 확인하며, 나만의 스쿼드를 구성할 수 있는 풀스택 학습 프로젝트입니다.

Vue 프런트엔드, Spring Boot REST API, MariaDB를 하나의 애플리케이션으로 연결했습니다. 단순 화면 구현을 넘어 데이터 적재, 부분 검색, JWT 인증, 리뷰 CRUD, 사용자별 스쿼드 저장까지 전체 데이터 흐름을 직접 구현하는 것을 목표로 합니다.

## 주요 기능

### 선수

- 한글 선수명 부분 검색
- 포지션 필터와 페이지네이션
- 검색 목록과 상세 조회 API 분리
- 카드 이미지, OVR, 팀, 리그, 국가 정보 표시
- 세부 능력치, 특성, 0진~15진 가격 표시
- `cid` 기준 카드별 상세 정보와 리뷰 관리

### 스쿼드

- 11개 포지션 슬롯에 선수 카드 등록
- 같은 실제 선수의 다른 카드 중복 방지
- 로그인 사용자의 스쿼드 MariaDB 저장
- 비로그인 사용자의 스쿼드 브라우저 저장
- 0진 가격을 기준으로 한 스쿼드 MP 합계
- 팀 OVR 계산은 아직 미구현이며 현재 `0`으로 표시

### 회원과 인증

- 이메일과 비밀번호 회원가입·로그인
- BCrypt 비밀번호 해시 저장
- HMAC-SHA256 JWT 발급·검증
- Stateless Bearer 인증
- 인증 사용자만 리뷰 작성과 스쿼드 DB 저장 가능

### 선수 리뷰

- 선수 카드별 별점과 한줄평
- 본인 리뷰 수정·삭제
- 좋아요·싫어요
- 리뷰는 실제 선수 `pid`가 아니라 카드 `cid`를 기준으로 저장

## 기술 스택

| 분야 | 기술 |
|---|---|
| Frontend | Vue 3, Vue Router, JavaScript, Vite |
| Backend | Java 17, Spring Boot 4, Spring Web MVC |
| Database | MariaDB 11.2, Spring JDBC, JdbcClient/JdbcTemplate |
| Security | Spring Security, BCrypt, JWT (HMAC-SHA256) |
| Infrastructure | Docker, Docker Compose |
| Test/Quality | JUnit 5, Spring Boot Test, AssertJ, ESLint, Oxlint |

JPA 대신 현재 규모에서 SQL과 데이터 흐름을 명확하게 확인할 수 있는 Spring JDBC를 사용했습니다.

## 핵심 도메인 키

FC Mobile 선수 데이터에는 `pid`와 `cid`가 있습니다.

| 키 | 의미 | 사용처 |
|---|---|---|
| `pid` | 실제 축구선수 고유 ID | 한 스쿼드 내 동일 선수 중복 검사 |
| `cid` | 시즌·클래스별 선수 카드 고유 ID | `players` PK, 상세 조회, 리뷰, 스쿼드 카드 참조 |
| `user_id` | 피모북 사용자 ID | 리뷰와 스쿼드 소유자 식별 |
| `slot_id` | 스쿼드 포지션 슬롯 ID | `gk`, `cb1`, `st` 등 배치 위치 식별 |

같은 손흥민이라도 시즌 카드가 다르면 `cid`는 다르고 `pid`는 같습니다. 따라서 카드는 각각 조회할 수 있지만 한 스쿼드에는 동시에 등록할 수 없습니다.

```text
실제 선수 손흥민 (pid = 200104)
├── 카드 A (cid = 22506717)
├── 카드 B (cid = 22509100)
└── 카드 C (cid = ...)
```

## 아키텍처

```text
Vue (5173/5174)
    │  REST API + Bearer JWT
    ▼
Spring Boot (8080)
    ├── Controller
    ├── Repository (JdbcClient/JdbcTemplate)
    ├── JWT Authentication Filter
    └── JSON response mapping
          │
          ▼
MariaDB (3306)
    ├── players
    ├── users
    ├── reviews
    ├── squads
    └── squad_players
```

모바일 앱으로 확장할 경우에도 앱이 MariaDB에 직접 연결하지 않고 동일한 Spring REST API를 사용하도록 설계했습니다.

## 선수 데이터 설계

초기 데이터는 FC Mobile 스쿼드 메이커 응답을 저장한 `players.json`에서 시작했습니다. 서비스 실행 중 선수 검색은 JSON 파일이 아니라 MariaDB에서 수행합니다.

```text
players.json
    ↓ PlayerDataImporter
cid 기준 INSERT ... ON DUPLICATE KEY UPDATE
    ↓
MariaDB players
    ↓
Spring REST API
    ↓
Vue
```

`players`는 검색과 필터에 필요한 값을 일반 컬럼으로 저장하고, 상세 화면에서 카드 단위로 읽는 데이터는 JSON 컬럼으로 보존하는 하이브리드 구조입니다.

- 일반 컬럼: `cid`, `pid`, 이름, OVR, 포지션, 팀, 리그, 국가, 이미지 URL 등
- `stats_data`: 세부 능력치
- `prices_data`: 0진~15진 가격
- `traits_data`: 선수 특성 배열
- `raw_data`: 원본 데이터 손실 방지용 전체 JSON

자세한 테이블 설계와 DBeaver 확인 SQL은 [backend/DB_SCHEMA.md](backend/DB_SCHEMA.md)에 있습니다.

## 데이터베이스 관계

```text
users 1 ─── N reviews N ─── 1 players
users 1 ─── N squads
squads 1 ─── N squad_players N ─── 1 players
```

- `players.cid`: Primary Key
- `players.pid`: 일반 Index이며 중복 가능
- 사용자 삭제 시 리뷰와 스쿼드는 cascade 삭제
- 스쿼드에서 참조 중인 선수 카드는 임의 삭제 방지

## 프로젝트 구조

```text
fimo/
├── src/                         # Vue 프런트엔드
│   ├── components/              # 로그인, 검색, 카드, 상세 컴포넌트
│   ├── views/SquadView.vue      # 스쿼드 빌더
│   ├── auth.js                  # JWT 세션과 API 요청 유틸리티
│   └── data/players.json        # 원본 데이터 백업
├── public/players/              # 로컬 선수 이미지 자산
├── backend/
│   ├── src/main/java/...        # Spring API, 인증, Repository, importer
│   ├── src/main/resources/
│   │   ├── data/players.json    # DB 적재 원본
│   │   └── db/schema-mariadb.sql
│   └── src/test/                # DB 연결·Repository 테스트
├── scripts/import_players.py    # 원본 응답 JSON 병합 보조 도구
├── compose.yaml                 # MariaDB 개발 환경
└── package.json
```

## 주요 API

### 공개 API

```http
POST /api/auth/register
POST /api/auth/login

GET /api/players/search?name=크루이프&position=CAM&page=0&size=12
GET /api/players/{cid}
GET /api/players/{cid}/reviews
```

기존 배열 응답과의 호환을 위해 다음 API도 유지합니다.

```http
GET /api/players?name=크루이프
```

### JWT 인증 API

```http
POST   /api/players/{cid}/reviews
PUT    /api/reviews/{id}
DELETE /api/reviews/{id}
POST   /api/reviews/{id}/like
POST   /api/reviews/{id}/dislike

GET /api/squads/me
PUT /api/squads/me
```

보호된 API는 다음 헤더를 사용합니다.

```http
Authorization: Bearer <JWT>
```

## 로컬 실행

### 요구 사항

- Docker Desktop
- Node.js 22.18 이상 또는 24.12 이상
- Java 17

### 1. MariaDB 실행

```sh
docker compose up -d
docker compose ps
```

기본 개발 접속 정보:

```text
Host: localhost
Port: 3306
Database: fimobook
Username: fimobook
Password: fimobook-local
```

Docker volume `fimobook-mariadb-data`를 사용하므로 컨테이너를 재시작해도 데이터가 유지됩니다.

### 2. 선수 데이터 적재

최초 실행 또는 `players.json` 갱신 후 실행합니다.

```sh
cd backend
./gradlew bootRun \
  --args='--fimo.players.import-enabled=true --spring.main.web-application-type=none'
```

Importer는 `cid` 기준 upsert이므로 여러 번 실행해도 중복 행을 만들지 않습니다.

### 3. Spring Boot 실행

```sh
cd backend
./gradlew bootRun
```

API: `http://localhost:8080`

### 4. Vue 실행

```sh
npm install
npm run dev
```

기본 주소: `http://localhost:5173`

5173을 사용 중이라면:

```sh
npm run dev -- --port 5174
```

## 환경변수

로컬 개발 기본값이 설정되어 있으며 필요할 때 다음 값으로 덮어쓸 수 있습니다.

| 환경변수 | 설명 |
|---|---|
| `FIMO_DB_URL` | MariaDB JDBC URL |
| `FIMO_DB_USERNAME` | DB 사용자 |
| `FIMO_DB_PASSWORD` | DB 비밀번호 |
| `FIMO_DB_ROOT_PASSWORD` | Docker MariaDB root 비밀번호 |
| `FIMO_JWT_SECRET` | JWT HMAC 서명 키, 운영 환경에서 반드시 변경 |
| `FIMO_JWT_EXPIRATION_SECONDS` | JWT 만료 시간 |

현재 기본 계정과 JWT secret은 로컬 학습용이며 운영 환경에서 사용하면 안 됩니다.

## 테스트와 품질 검사

MariaDB를 실행한 상태에서:

```sh
cd backend
FIMO_DB_TEST=true ./gradlew clean test
```

프런트엔드:

```sh
npm run build
npx eslint .
npx oxlint .
```

현재 검증한 주요 흐름:

- MariaDB 연결과 `SELECT 1`
- 선수 데이터 201장 적재 및 `cid` 중복 방지
- 한글 선수명 부분 검색
- 검색 페이지네이션과 포지션 필터
- 선수 상세 JSON 호환
- BCrypt 회원가입과 JWT 로그인
- 리뷰 생성·수정·삭제
- 사용자별 스쿼드 저장·조회
- 같은 `pid`의 다른 카드 중복 등록 차단
- Vue production build

## 버전 관리 방식

Git은 변경 이력을 커밋 단위로 기록합니다. 기능 하나를 완성하고 테스트한 시점에 의미 있는 커밋을 만드는 방식을 권장합니다.

```text
feat: 선수 검색 페이지네이션 추가
feat: JWT 로그인 구현
fix: Vue 검색 이벤트가 page 파라미터로 전달되는 문제 수정
docs: 프로젝트 실행 방법 정리
```

배포 가능한 기준점에는 Git tag를 붙일 수 있습니다.

```sh
git tag -a v0.1.0 -m "Fimobook MVP"
git push origin v0.1.0
```

권장 버전 의미:

- `v0.1.0`: 최초 동작하는 MVP
- `v0.2.0`: 기존 기능과 호환되는 새로운 기능 추가
- `v0.2.1`: 버그 수정
- `v1.0.0`: 공개 서비스로 사용할 수 있는 안정 버전

GitHub Release는 tag를 기반으로 변경 내용, 실행 파일, 스크린샷 등을 묶어 배포 기록으로 남길 수 있습니다.

## 현재 한계와 다음 목표

- 팀 OVR 공식 계산식 미적용
- Access Token만 사용하며 Refresh Token 미구현
- 이메일 인증과 비밀번호 재설정 미구현
- 좋아요·싫어요 사용자별 중복 방지 미구현
- 기본 스쿼드 하나만 저장 가능
- 선수 데이터 자동 수집·갱신 파이프라인 미구현
- API 주소가 프런트 코드에 로컬 주소로 고정됨
- 모바일 UI와 운영 배포 설정 추가 필요

다음 단계 후보:

1. 프런트 API 주소 환경변수 분리
2. Refresh Token과 로그아웃 정책
3. 여러 스쿼드 저장 및 이름 관리
4. 선수 데이터 관리자 업로드 또는 배치 갱신
5. 반응형 UI와 PWA/Capacitor 앱 확장

## 데이터 및 이미지 안내

이 저장소는 개인 학습·포트폴리오 목적의 비공식 프로젝트입니다. FC Mobile 및 관련 명칭과 자산의 권리는 각 권리자에게 있으며, 공식 서비스와 관계가 없습니다. 공개 서비스 또는 수익화 전에는 데이터와 이미지 사용 조건을 별도로 확인해야 합니다.

## License

별도의 오픈소스 라이선스는 아직 지정하지 않았습니다. 라이선스가 추가되기 전까지 저장소 공개는 소스 열람을 허용한다는 의미이며, 자동으로 재사용·수정·배포 권한을 부여하지 않습니다.
