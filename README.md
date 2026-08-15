# 피모북 (Fimobook)

FC Mobile 선수 카드를 검색하고 상세 정보와 가격을 확인하며, 나만의 스쿼드를 구성할 수 있는 스쿼드 메이커입니다.
데이터 적재, 부분 검색, JWT 인증, 리뷰 CRUD, 사용자별 스쿼드 저장까지 전체 데이터 흐름을 직접 구현하는 것을 목표로 합니다.

*// Vue숙달을 위한 프로젝트이나 의외로 쓸모있는것으로 판단 버전관리 하겠음. 

## 최근 업데이트

- 공식 FC Mobile 카드 33,371장과 이미지 자산 수집·MariaDB 적재
- 거래 가능 카드 기본 검색, 20장 페이지네이션과 상세 가격 3시간 캐시
- Vue와 Expo 앱에 공식 카드 레이어·검색 필터·상세 화면 연결
- 로컬 스쿼드와 로그인 사용자의 서버 저장, 동일 `pid` 중복 방지
- JWT Access/Refresh Token, SecureStore, 리뷰와 사용자별 반응 구현
- Spring·Vue·Expo 자동 빌드 및 실제 MariaDB HTTP 흐름 검증

## 주요 기능

### 선수

- 한글 선수명 부분 검색
- 거래 가능 카드만 기본 노출하며 거래 불가 원본은 DB에 보존
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
| Web client | Vue 3, Vue Router, JavaScript, Vite |
| Mobile app | Expo SDK 57, React Native, TypeScript, Expo Router |
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
    ├── player_profiles / players
    ├── card_classes / card_positions
    ├── card_prices_current / card_price_history
    ├── price_refresh_jobs
    ├── users
    ├── reviews
    ├── squads
    └── squad_players
```

최종 클라이언트는 React Native + Expo + TypeScript 모바일 앱을 목표로 합니다. 현재 Vue 앱은 기능과 API 호환을 검증하는 웹 클라이언트로 유지합니다. 어떤 클라이언트도 MariaDB에 직접 연결하지 않고 동일한 Spring REST API를 사용합니다.

## 선수 데이터 설계

초기 데이터는 FC Mobile 스쿼드 메이커 응답을 저장한 `players.json`에서 시작했습니다. 현재 수집기는 공식 스쿼드 메이커의 익명 세션에서 전체 카드 snapshot을 만들며, 서비스 실행 중 선수 검색은 JSON 파일이 아니라 MariaDB에서 수행합니다.

```text
공식 SquadMaker 응답
    ↓ scripts/collect_fcmobile.py
cards.json + manifest.json
    ↓ PlayerDataImporter
cid 기준 INSERT ... ON DUPLICATE KEY UPDATE
    ↓
MariaDB players
    ↓
Spring REST API
    ↓
Vue
```

`players`는 검색과 필터에 필요한 값을 일반 컬럼으로 저장하고, 상세 화면에서 카드 단위로 읽는 데이터는 JSON 컬럼으로도 보존하는 하이브리드 구조입니다. 포지션, 클래스, 특성, 플레이스타일과 현재 가격은 별도 관계 테이블로 검색할 수 있습니다.

- 일반 컬럼: `cid`, `pid`, 이름, OVR, 포지션, 팀, 리그, 국가, 이미지 URL 등
- `stats_data`: 세부 능력치
- `prices_data`: 기존 응답 호환용 0진~15진 가격 snapshot
- `traits_data`: 선수 특성 배열
- `raw_data`: 원본 데이터 손실 방지용 전체 JSON

공식 클래스 필터는 서로 겹치므로 카드에 단일 클래스를 억지로 지정하지 않습니다. `player_classes`와 `card_classes`의 다대다 관계로 공식 필터 포함 여부를 그대로 보존합니다.

### 가격 갱신

상세 조회는 MariaDB의 현재 값을 즉시 반환합니다. 거래 가능한 카드 가격이 3시간보다 오래됐을 때만 `pid` 기준 작업을 하나 queue하고, background worker가 공식 `PlayerClass` 응답으로 같은 실제 선수의 거래 가능 카드 가격을 갱신합니다.

- 같은 카드를 반복 조회해도 3시간 안에는 공식 요청이 다시 발생하지 않음
- 동시에 같은 선수를 조회해도 `pid` 기준 한 작업만 생성
- 공식 요청 실패가 상세 API 응답을 실패시키지 않음
- 0진~15진 현재 가격 저장, 가격이 바뀔 때만 이력 추가

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
├── mobile/                      # Expo + React Native 모바일 앱
│   ├── src/app/                 # 검색과 cid 상세 route
│   ├── src/features/players/    # 선수 API 타입·호출·표현 로직
│   ├── src/components/          # 모바일 공통 UI
│   └── src/services/api.ts      # API base URL과 공통 HTTP 처리
├── src/                         # Vue 프런트엔드
│   ├── components/              # 로그인, 검색, 카드, 상세 컴포넌트
│   ├── views/SquadView.vue      # 스쿼드 빌더
│   ├── auth.js                  # JWT 세션과 API 요청 유틸리티
│   └── data/players.json        # 초기 소규모 원본 백업
├── public/players/              # 로컬 선수 이미지 자산
├── backend/
│   ├── src/main/java/...        # Spring API, 인증, Repository, importer
│   ├── src/main/resources/
│   │   ├── data/players.json    # 초기 소규모 원본 백업
│   │   └── db/schema-mariadb.sql
│   └── src/test/                # DB 연결·Repository 테스트
├── scripts/collect_fcmobile.py  # 공식 전체 snapshot 수집기
├── scripts/collect_fcmobile_assets.py # 공식 이미지 인벤토리·다운로드
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
GET /api/player-metadata
GET /api/player-metadata/teams?leagueId=39&name=&limit=100
GET /api/players/{cid}/reviews
```

선수 검색의 `tradeable` 기본값은 `true`입니다. 일반 앱은 거래 가능 카드만 보여주며,
거래 불가 카드는 공식 원본 보존과 기존 `cid` 참조를 위해 MariaDB에서 삭제하지 않습니다.

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

### 2. 공식 선수 snapshot 수집

프로젝트 루트에서 실행합니다. 같은 출력 디렉터리로 재실행하면 저장된 페이지 다음부터 이어받습니다.

```sh
python3 scripts/collect_fcmobile.py \
  --output-dir data/fcmobile/snapshots/20260813-official \
  --delay-seconds 0.5 \
  --workers 2
```

`manifest.json`이 `state=COMPLETE`, `complete=true`이고 전체 reported count와 unique card count가 일치해야 완성 snapshot입니다. 대용량 원본 응답은 재현 가능한 로컬 데이터이므로 Git에서 제외됩니다.

`--workers`는 초기 전체 snapshot의 무필터 페이지 수집에만 적용되며 `1` 또는 `2`만
허용합니다. 두 worker를 사용해도 각 페이지는 한 번만 요청합니다. 서비스 중 상세 가격
갱신 worker는 이 옵션과 무관하게 한 작업씩 처리합니다.

각 카드에는 원본 페이지 관측시각이 함께 기록됩니다. 이후 snapshot을 재적재해도 DB에 더 최근 공식 가격이 있으면 오래된 목록 가격으로 되돌리지 않습니다.

2026-08-14에 실제로 검증한 현재 snapshot과 DB 수치:

- 공식 reported 카드: 33,371장
- 고유 `cid`: 33,371개, 중복 0개
- 고유 `pid`: 17,469개
- 사용자 검색 대상 거래 가능 카드: 25,045장
- DB에만 보존하는 거래 불가 카드: 8,326장
- 공식 클래스 관계: 18,633개 (`card_classes`)
- 두 개 이상 클래스 필터에 포함된 카드: 1,408장
- 클래스 필터에 포함되지 않는 카드: 16,146장
- 현재 가격: 533,936행 (`33,371 × 16`), 가격 단계 누락 카드 0장
- 거래 가능 `크루이프` 부분 검색: 6장
- 깨진 한글 선수명 검사: 0건

### 3. 공식 이미지 자산 수집

선수 snapshot과 현재 공식 SquadMaker 프론트의 이미지 매핑을 결합해 이미지 인벤토리를
만든 뒤 다운로드합니다. 같은 출력 디렉터리로 재실행하면 이미 검증된 파일은 다시 받지
않습니다.

```sh
python3 scripts/collect_fcmobile_assets.py \
  --snapshot-dir data/fcmobile/snapshots/20260813-official \
  --output-dir data/fcmobile/assets \
  --workers 8
```

URL 목록만 먼저 확인하려면 `--inventory-only`, 일부 종류만 받으려면
`--category player`처럼 실행합니다. 결과는 다음 위치에 생성됩니다.

- `data/fcmobile/assets/files/`: 원본 host/path를 보존한 이미지 파일
- `data/fcmobile/assets/manifests/inventory.json`: 수집 대상 전체 목록
- `data/fcmobile/assets/manifests/latest.json`: HTTP 상태, 크기, SHA-256 검증 결과
- `data/fcmobile/assets/manifests/download-journal.jsonl`: 실행 중 진행 기록

이미지 바이너리와 manifest는 용량이 큰 재현 가능한 로컬 데이터라 Git에서 제외됩니다.
운영에서는 이 파일을 R2/S3 계열 오브젝트 스토리지와 CDN으로 배포하고 MariaDB에는
이미지 BLOB 대신 원본 URL, storage key, hash와 도메인 연결만 저장합니다.

2026-08-14 실제 수집·재검증 결과:

- 이미지 인벤토리 URL: 31,553개
- visual asset 인벤토리: 31,557개
- 정상 저장 파일: 31,398개, 927,593,424 bytes
- 서로 다른 SHA-256 내용: 30,758개, 같은 내용의 중복 URL 파일 640개
- 선수 이미지: 29,155/29,267개
- 카드 배경: 574/574개
- 클래스 로고: 113/113개
- 국가: 212/212개, 팀: 753/753개
- 특성: 41/41개, 스킬 이미지: 255/255개
- 진화: 16/16개, 훈련: 11/11개, 포메이션: 34/34개
- `FCOAllSans` 글꼴: 3/3개, 카드 글자색 매핑: 1/1개
- 공식 CDN에 원본이 없는 URL: 159개

원본 누락 159개 중 112개는 선수 이미지 URL이며 133장의 카드가 영향을 받습니다. 공식
응답에는 URL이 있지만 CDN은 403을 반환하므로 앱에서는 수집된 연도별 `p0.png`
placeholder를 사용해야 합니다. 월드컵 전용 팀 로고 34개, 사용 중인 플레이스타일 이미지
6개와 기타 7개도 공식 경로 자체가 403입니다. 이 때문에 수집기는 엄격 검증 기준으로
종료 코드 1을 반환하며, 누락 목록은 `manifests/latest.json`에 남습니다.

로컬에서는 Spring이 저장된 파일을 다음 형태로 제공합니다.

```text
/api/assets/fco.vod.nexoncdn.co.kr/jade_assets/...
/api/assets/ssl.nexon.com/...
```

API는 로컬 파일이 존재할 때 선수·배경·국기·팀·리그·클래스·특성·플레이스타일 URL을
위 상대 경로로 반환합니다. Vue는 Vite proxy로, Expo 앱은 API base URL을 붙여 같은 파일을
읽습니다. 카드 위 OVR·포지션·선수명·아이콘은 공식 256×256 레이어 좌표와 배경별 글자색으로
클라이언트에서 합성합니다.

### 4. 선수 데이터 적재

완성된 공식 snapshot을 `cid` 기준으로 upsert합니다.

```sh
cd backend
./gradlew bootRun \
  --args="--fimo.players.import-enabled=true --fimo.players.import-path=../data/fcmobile/snapshots/20260813-official/cards.json --spring.main.web-application-type=none"
```

Importer는 `cid` 기준 upsert이므로 여러 번 실행해도 중복 행을 만들지 않습니다.

### 5. Spring Boot 실행

```sh
cd backend
./gradlew bootRun
```

API: `http://localhost:8080`

### 6. Vue 실행

```sh
npm install
npm run dev
```

기본 주소: `http://localhost:5173`

5173을 사용 중이라면:

```sh
npm run dev -- --port 5174
```

### 7. 모바일 앱 실행

Spring을 먼저 실행한 뒤:

```sh
cd mobile
npm install
npm start
```

모바일은 `EXPO_PUBLIC_API_URL`이 있으면 해당 Spring 주소를 사용합니다. 값이 없으면
개발 중 Expo host의 8080 포트를 추론하며, 실제 기기에서는 필요할 경우 같은 Wi-Fi의
Mac LAN 주소를 `mobile/.env.local`에 설정합니다. 자세한 내용은
[mobile/README.md](mobile/README.md)를 참고합니다.

## 환경변수

로컬 개발 기본값이 설정되어 있으며 필요할 때 다음 값으로 덮어쓸 수 있습니다.

| 환경변수 | 설명 |
|---|---|
| `FIMO_DB_URL` | MariaDB JDBC URL |
| `FIMO_DB_USERNAME` | DB 사용자 |
| `FIMO_DB_PASSWORD` | DB 비밀번호 |
| `FIMO_DB_ROOT_PASSWORD` | Docker MariaDB root 비밀번호 |
| `FIMO_PLAYERS_IMPORT_ENABLED` | 명시적인 선수 import maintenance 실행 여부 |
| `FIMO_PLAYERS_IMPORT_PATH` | 완성된 `cards.json` 경로 |
| `FIMO_JWT_SECRET` | JWT HMAC 서명 키, 운영 환경에서 반드시 변경 |
| `FIMO_JWT_EXPIRATION_SECONDS` | JWT 만료 시간 |
| `FIMO_JWT_REFRESH_EXPIRATION_DAYS` | Refresh Token 만료 일수, 기본 30일 |
| `FIMO_PRICE_REFRESH_ENABLED` | 상세 조회 기반 가격 갱신 worker 활성화 |
| `FIMO_PRICE_REFRESH_CACHE_HOURS` | 공식 가격 재확인 간격, 기본 3시간 |
| `FIMO_PRICE_REFRESH_POLL_DELAY_MS` | queue polling 간격, 기본 500ms |
| `FIMO_PRICE_REFRESH_LOCK_TIMEOUT_SECONDS` | 중단된 `RUNNING` 작업 복구 기준, 기본 60초 |

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

모바일:

```sh
cd mobile
npm run typecheck
npm run lint
npx expo-doctor
npx expo export --platform web
```

모바일 화면을 브라우저에서 임시 확인하려면 Spring을 먼저 실행한 뒤 다음 명령을 사용합니다.

```sh
cd mobile
npm run web -- --port 8081
```

접속 주소는 `http://localhost:8081`입니다. 같은 개발 서버의 QR 코드는 Expo Go에서 열 수
있으며, 실제 기기에서는 Mac과 휴대폰이 같은 네트워크에 있어야 합니다.

현재 검증한 주요 흐름:

- MariaDB 연결과 `SELECT 1`
- `cid` 기준 선수 데이터 upsert 및 중복 방지
- 한글 선수명 부분 검색
- 검색 페이지네이션과 포지션·클래스·팀·리그·국가·거래·가격 필터
- 선수 상세 JSON 호환
- 상세 API의 3시간 가격 cache, `pid` queue, 공식 가격 16단계 갱신
- BCrypt 회원가입과 JWT 로그인
- 리뷰 생성·수정·삭제
- 사용자별 스쿼드 저장·조회
- 같은 `pid`의 다른 카드 중복 등록 차단
- Vue production build
- Expo SDK 호환성 검사 20/20
- 모바일 TypeScript/ESLint와 Expo Web production bundle
- 모바일 검색 20장 pagination, 다음 페이지와 `cid` 상세 API 연동
- 모바일 전체 검색 필터와 팀 이름 검색
- 모바일 SecureStore JWT 로그인
- 회전형 Refresh Token과 로그아웃 시 토큰 폐기
- 로그인 없는 로컬 스쿼드와 로그인 사용자의 서버 동기화
- 모바일 카드 리뷰 CRUD와 사용자별 좋아요·싫어요 토글

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
- 이메일 인증과 비밀번호 재설정 미구현
- 기본 스쿼드 하나만 저장 가능
- 전체 선수 snapshot 수집은 수동 실행이며 운영 scheduler/관리자 기능 미구현
- Vue API 주소가 프런트 코드에 로컬 주소로 고정됨
- 실제 Android/iOS 기기에서 네트워크와 이미지 렌더링 미검증
- Expo 앱 아이콘, splash와 EAS 운영 배포 설정 미완성

다음 단계 후보:

1. 실제 Android/iOS 기기 검증과 EAS development build
2. 운영 이미지 오브젝트 스토리지/CDN 이전
3. 스테이징 Spring·MariaDB, HTTPS와 비밀값 분리
4. 소셜 로그인과 스토어 정책 대응
5. 이메일 인증과 비밀번호 재설정

## 데이터 및 이미지 안내

이 저장소는 개인 학습·포트폴리오 목적의 비공식 프로젝트입니다. FC Mobile 및 관련 명칭과 자산의 권리는 각 권리자에게 있으며, 공식 서비스와 관계가 없습니다. 공개 서비스 또는 수익화 전에는 데이터와 이미지 사용 조건을 별도로 확인해야 합니다.
