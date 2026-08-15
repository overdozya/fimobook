# Project: 피모북 (Fimobook)

## Mission

피모북은 FC Mobile 사용자가 선수 카드를 검색하고 상세 정보와 가격을 확인하며,
스쿼드를 만들고 저장하고 카드별 평가를 남길 수 있는 실제 서비스 지향 프로젝트다.

이 저장소의 목표는 단순한 화면 데모나 일회성 학습 예제가 아니라 다음을 만족하는
배포 가능한 최종 프로젝트를 만드는 것이다.

- 신뢰할 수 있는 FC Mobile 카드 데이터 수집 및 갱신
- 빠른 선수 검색과 상세 조회
- 사용자 계정, 리뷰, 스쿼드 저장
- 모바일 앱 배포가 가능한 안정적인 REST API
- 운영 환경에서 관찰하고 복구할 수 있는 단순한 구조

기능을 구현하는 과정 자체도 학습 목적이므로, 동작만 하는 코드보다 데이터 흐름과
설계 이유를 이해하기 쉬운 코드를 선호한다.

## Product Direction

최종 사용자 클라이언트의 우선순위는 모바일 앱이다.

- 목표 모바일 스택: React Native + Expo + TypeScript
- 현재 Vue 앱: 기존 기능 확인과 API 호환 검증을 위한 웹 클라이언트로 유지
- Backend: Java + Spring Boot REST API
- Database: MariaDB
- Local infrastructure: Docker Compose

모바일 앱과 Vue는 MariaDB에 직접 연결하지 않는다. 모든 데이터 접근과 인증은 동일한
Spring REST API를 통한다. 모바일 구현은 백엔드 데이터/API가 안정된 뒤 시작한다.

현재 규모에서는 Spring Boot 단일 애플리케이션을 유지한다. MSA, 메시지 브로커,
Kubernetes 같은 운영 복잡도는 실제 부하나 장애 요구가 생기기 전에는 도입하지 않는다.

## Domain Keys and Rules

### Player identity

- `pid`: 실제 축구선수 고유 ID
- `cid`: 시즌·클래스·버전별 선수 카드 고유 ID
- `players.cid`는 Primary Key다.
- `players.pid`는 일반 Index이며 중복될 수 있다.
- 같은 `pid`의 서로 다른 `cid`는 서로 다른 카드다.
- 한 스쿼드에는 같은 `pid`의 카드를 둘 이상 넣을 수 없다.
- 리뷰와 카드 상세 주소는 `pid`가 아니라 `cid`를 기준으로 한다.

### Squad OVR

공식 계산식이 확인되기 전에는 임의 평균값을 만들지 않는다. 팀 OVR은 현재 `0`으로
표시하며, 검증 가능한 계산식이 확보된 뒤 별도 기능으로 구현한다.

### Tradeability

공식 응답의 `noTrade`를 보존한다.

- `noTrade = 0`: 거래 가능
- 그 외: 거래 불가

가격 갱신 대상은 거래 가능한 카드다. 사용자용 검색과 필터 메타데이터는 거래 가능한 카드만
기본 노출한다. 거래 불가 카드는 원본 snapshot과 MariaDB에서 삭제하지 않으며, 직접 `cid`
상세 조회와 명시적인 진단용 `tradeable=false` 검색은 보존한다.

## Official Data Source

선수 원본은 FC Mobile 공식 스쿼드 메이커의 공개 웹 요청을 사용한다.

```text
GET  /DataCenterWeb/SquadMaker
POST /datacenterweb/SquadMakerAjaxInfo?strMethod=Init
POST /datacenterweb/SquadMakerAjaxInfo          (PlayerSearchList)
POST /datacenterweb/SquadMakerAjaxInfo?strMethod=PlayerClass
```

로그인은 필요 없지만, 공식 페이지가 발급하는 쿠키와 검증 토큰을 가진 익명 세션에서
정상적인 폼 요청을 보내야 한다. 토큰·쿠키를 저장소나 로그에 기록하지 않는다.

데이터 수집기는 `scripts/collect_fcmobile.py`에 둔다. 수집 원칙은 다음과 같다.

- 전체 무필터 검색 결과를 카드 목록의 authoritative source로 사용한다.
- 원본 페이지 응답을 먼저 저장하고, 파생 snapshot은 원본에서 재생성 가능하게 한다.
- 중간 실패 후 이미 저장한 페이지부터 재개할 수 있어야 한다.
- 요청 간 지연, 재시도, 지수 backoff를 적용한다.
- `cid`로 중복을 제거하고 원본 건수와 고유 카드 수를 검증한다.
- 대용량 snapshot은 재현 가능한 로컬 산출물이므로 Git에 커밋하지 않는다.

공식 이미지 자산은 `scripts/collect_fcmobile_assets.py`로 별도 수집한다.

- 직접 URL: 선수 이미지, 카드 배경, 클래스 로고
- ID 조립 URL: 국가, 리그, 팀, 특성, 스킬, 플레이스타일, 진화·훈련 아이콘
- 카드 표시용 `FCOAllSans` 글꼴과 공식 `card_colors.json`도 visual asset으로 보존한다.
- 공식 프론트 bundle의 ID→파일명 매핑을 manifest에 고정한다.
- URL과 로컬 파일은 중복 제거하며 중단 후 이미 받은 정상 파일을 재사용한다.
- 파일은 MariaDB BLOB이나 앱 bundle에 넣지 않고 로컬 asset 디렉터리/오브젝트
  스토리지에 둔다. MariaDB는 URL, storage key, hash와 연결 관계만 관리한다.
- 로컬 수집물은 Git에 커밋하지 않는다. 운영 앱은 최종적으로 피모북 CDN URL을 사용한다.
- upstream에 실제로 없는 URL은 임의 이미지로 성공 처리하지 않고 manifest에 남긴다.

로컬 개발에서는 Spring이 수집된 파일을 `/api/assets/{host}/{path}`로 제공한다. DB에는
원본 공식 URL을 유지하고, API 응답을 만들 때 로컬 파일이 실제 존재하는 URL만 이 경로로
치환한다. Vue는 Vite `/api` proxy를 사용하고 모바일은 `EXPO_PUBLIC_API_URL`을 기준으로
상대 자산 URL을 절대 URL로 변환한다.

공식 기본 카드 합성 좌표는 256×256 정사각형을 기준으로 유지한다.

- OVR: `left 22%`, `top 10%`, `FCOAllSans-Bold`
- 포지션: `left 23%`, `top 24%`, `FCOAllSans-Regular`
- 선수명: `bottom 25.5%`, 중앙 `width 55%`, `FCOAllSans-Regular`
- 국기·리그·팀 아이콘: `bottom 16.1%`, 아이콘 높이 카드의 `1/12`
- 글자색: 배경 파일명에 대응하는 공식 `card_colors.json`; 없으면 흰색

공식 `ClassInfos`의 항목은 카드에 포함된 단일 시즌 값이라고 가정하면 안 된다. 클래스
필터 결과는 서로 겹칠 수 있고 모든 카드를 포함하지도 않는다. 따라서 카드와 공식 클래스
필터는 `card_classes` 다대다 관계로 저장한다. upstream이 직접 주지 않은 canonical class를
임의로 추측하지 않는다.

## Price Refresh Business Rule

가격은 0진부터 15진까지 16단계다. 모든 카드 가격을 주기적으로 전수 조회하지 않는다.
상세 조회를 실제 수요 신호로 사용하는 cache-aside 방식으로 동작한다.

```text
GET /api/players/{cid}
        │
        ├─ DB의 현재 상세/가격을 즉시 응답
        │
        └─ 거래 가능 + 마지막 가격 확인이 3시간보다 오래됨
              └─ pid 기준 갱신 작업을 한 번만 queue
                    └─ background worker가 공식 PlayerClass(pid) 요청
                          └─ 같은 pid 카드의 0~15진 가격 upsert
```

- 사용자는 갱신 버튼을 누르지 않는다.
- 화면에 갱신 중, 몇 분 전 같은 운영 상태를 노출할 필요가 없다.
- 3시간 안에는 같은 상세를 몇 번 열어도 공식 서버 요청을 다시 보내지 않는다.
- 동일 `pid` 작업은 DB Primary Key로 중복 제거한다.
- worker는 제한된 속도로 한 작업씩 처리해 순간 트래픽을 흡수한다.
- 공식 요청 실패는 사용자 상세 응답을 실패시키지 않으며 재시도한다.
- 가격이 실제로 바뀐 경우에만 `card_price_history`에 이력을 남긴다.
- 클래스별 공식 갱신 시각을 추측하거나 그대로 복제하지 않는다.

## Database Model

MariaDB 스키마의 source of truth는
`backend/src/main/resources/db/schema-mariadb.sql`이다.

핵심 테이블:

- `player_profiles`: `pid` 기준 실제 선수 공통 정보
- `players`: `cid` 기준 카드, 검색 컬럼, 원본/상세 JSON
- `player_classes`: 공식 클래스 필터 메타데이터
- `card_classes`: 카드와 공식 클래스 필터의 다대다 관계
- `nations`, `leagues`, `teams`: 검색 필터 및 이미지 메타데이터
- `card_positions`: 주·부 포지션 필터
- `traits`, `card_traits`: 특성 메타데이터 및 카드 관계
- `play_styles`, `card_play_styles`: 플레이스타일 및 카드 관계
- `skills`, `card_skills`: 개인기/스킬 및 카드 관계
- `card_prices_current`: 카드별 0~15진 현재 가격
- `card_price_history`: 변경된 가격 이력
- `price_refresh_jobs`: `pid` 단위 가격 갱신 queue
- `users`, `reviews`, `squads`, `squad_players`: 사용자 기능

검색·정렬·관계에 필요한 값은 일반 컬럼이나 정규화 테이블로 저장한다. 상세 화면에서 한
카드와 함께 읽는 능력치 묶음과 원본 보존은 JSON 컬럼을 함께 사용하는 하이브리드 구조다.

- `stats_data`: 상세 능력치
- `prices_data`: 기존 응답 호환용 0~15진 가격 snapshot
- `traits_data`, `positions_data`, `play_styles_data`, `skills_data`: 기존 구조 보존
- `raw_data`: upstream 원본 전체 보존

Importer는 `cid` 기준 upsert로 여러 번 실행해도 중복 행이 생기지 않아야 한다. 완전한
snapshot을 적재할 때 source에서 사라진 카드는 삭제하지 않고 `is_active = false`로 둔다.

## API Contract

기존 Vue 호환 API는 유지한다.

```http
GET /api/players?name=크루이프
```

모바일과 확장 검색은 pagination API를 사용한다.

```http
GET /api/players/search?name=&position=&classId=&leagueId=&teamId=&nationId=
    &tradeable=&minOvr=&maxOvr=&priceLevel=0&minPrice=&maxPrice=
    &traitId=&playStyleId=&sort=ovrDesc&page=0&size=20
GET /api/players/{cid}
GET /api/player-metadata
GET /api/player-metadata/teams?leagueId=&name=&limit=
```

`tradeable`의 기본값은 `true`다. 모바일과 일반 사용자 화면은 `false`를 보내지 않는다.

상세 응답은 현재 Vue가 사용하는 upstream 키를 계속 제공한다.

- `playerKor`, `ovr`, `position`, `team`, `pimage`, `bimage`
- 상세 능력치
- `n8Price0` ~ `n8Price15`
- `Trait`, 스킬, 플레이스타일
- 국가·팀·리그·클래스 이미지 정보

공개 조회는 로그인 없이 가능하다. 로그인은 이메일/비밀번호 + JWT로 유지하며 OAuth는
현재 범위가 아니다. 리뷰 작성·수정·삭제와 서버 스쿼드 저장만 인증이 필요하다.

## Implementation Roadmap

다음 순서가 현재의 기본 실행 계획이다.

### Phase 1 — Data foundation

1. [x] 공식 전체 카드 snapshot 수집을 완주하고 건수를 검증한다.
2. [x] 클래스 중복을 `card_classes` 다대다 관계로 보존한다.
3. [x] MariaDB에 전체 카드를 idempotent upsert한다.
4. [x] 한글, 카드 수, 가격 16단계, 관계 테이블 수를 검증한다.
5. [x] 과거 장난용 김찬우 카드/이미지를 공식 손흥민 데이터로 복구한다.
6. [x] 공식 이미지·글꼴·카드 테마 자산 31,557개를 인벤토리화하고 31,398개를 저장했다.
   공식 CDN에 실제로 없는 159개는 성공 처리하지 않고 manifest에 기록했다.

### Phase 2 — Backend read and refresh API

1. [x] 선수명 부분 검색 및 필터/pagination을 실제 DB로 검증한다.
2. [x] 상세 응답이 기존 Vue JSON 계약과 호환되는지 검증한다.
3. [x] 3시간 가격 cache와 `pid` queue/worker를 실제 공식 응답으로 검증한다.
4. [x] API·Repository·DB 연결 자동 테스트를 추가한다.
5. [x] 수집·적재·실행·복구 방법을 README와 DB 문서에 기록한다.

### Phase 3 — Mobile MVP

1. [x] Expo + React Native + TypeScript 앱 workspace를 추가한다.
2. [x] 개발/운영 API base URL과 환경 설정을 분리한다.
3. [x] 선수 검색, 20장 pagination/무한 스크롤, 카드 목록과 상세 화면을 구현한다.
4. [x] 로컬 수집 자산과 공식 카드 레이어를 Vue·React Native 공통 API에 연결한다.
5. [x] 공식 메타데이터 API를 이용한 검색 필터 UI를 구현한다.
6. [x] JWT 회원가입·로그인과 안전한 토큰 저장을 연결한다.
7. [x] 로컬 스쿼드 빌더, 선택적 서버 저장과 카드 리뷰를 연결한다.
8. [ ] 실제 Android/iOS 기기에서 네트워크·이미지·인증을 검증한다.

### Phase 4 — Production readiness

1. 운영 DB migration 도구를 도입한다. 이 시점에는 Flyway를 우선 검토한다.
2. 비밀값과 환경 설정을 저장소 밖으로 분리한다.
3. API 오류 응답, timeout, rate limit, 로그와 health check를 정리한다.
4. DB backup/restore와 데이터 수집 실패 복구 절차를 만든다.
5. 권리·이용약관·개인정보·공식 데이터/이미지 사용 조건을 검토한다.
6. Expo EAS Build/Submit 또는 확정된 배포 파이프라인으로 스토어 테스트를 진행한다.

## Definition of Done

기능은 코드를 작성한 것만으로 완료 처리하지 않는다. 관련 범위에서 실제로 다음을 확인한다.

- MariaDB container healthy
- schema가 기존 DB와 fresh DB 모두에 적용 가능
- 수집 reported count와 snapshot unique count 검증
- DB active card count와 snapshot card count 일치
- 한글 선수명과 이미지 URL이 정상
- `크루이프` 부분 검색 결과 확인
- 특정 `cid` 상세 JSON과 0~15진 가격 확인
- 상세 진입의 fresh-cache 무요청 및 stale-cache 단일 queue 확인
- worker 성공/실패·중복 제거 확인
- Spring 자동 테스트 통과
- 기존 Vue production build 및 핵심 기능 회귀 없음
- 확인하지 못한 항목은 완료했다고 보고하지 않음

## Engineering Rules

- 기존 사용자의 작업과 unrelated dirty file을 덮어쓰지 않는다.
- Vue는 모바일 전환 전까지 회귀 검증 클라이언트로 보존한다. 백엔드 작업 때문에 불필요하게
  UI를 고치지 않는다.
- Controller에 긴 SQL이나 upstream 호출 로직을 넣지 않는다.
- 현재 규모에서는 JdbcClient/JdbcTemplate을 유지하고 불필요한 repository abstraction이나
  새 프레임워크를 추가하지 않는다.
- upstream 데이터를 추측해 채우지 않는다. 불확실하면 nullable 또는 원본 JSON으로 보존한다.
- DB에 없는 대량 결과를 메모리에서 매 요청 필터링하지 않는다.
- 파괴적인 DB 초기화보다 additive schema와 upsert를 우선한다.
- 실패를 숨기는 우회 구현을 하지 않는다. 실제 에러 원인과 미검증 범위를 먼저 기록한다.
- 외부 요청은 최소화하고 지연·timeout·retry·deduplication을 둔다.
- local secret, token, cookie, 대용량 snapshot, build artifact는 Git에 커밋하지 않는다.

## Local Development Defaults

```text
Vue:        http://localhost:5173 (사용 중이면 5174)
Spring:     http://localhost:8080
MariaDB:    localhost:3306
Database:   fimobook
Username:   fimobook
Password:   fimobook-local
```

기본 명령:

```sh
docker compose up -d
docker compose ps

python3 scripts/collect_fcmobile.py \
  --output-dir data/fcmobile/snapshots/<snapshot-name>

cd backend
./gradlew bootRun \
  --args="--fimo.players.import-enabled=true --fimo.players.import-path=../data/fcmobile/snapshots/<snapshot-name>/cards.json --spring.main.web-application-type=none"

FIMO_DB_TEST=true ./gradlew clean test
./gradlew bootRun
```

자세한 실행 방법과 현재 검증 결과는 `README.md`와 `backend/DB_SCHEMA.md`를 함께 갱신한다.
