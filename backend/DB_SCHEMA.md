# MariaDB 데이터 모델

스키마 source of truth는
`backend/src/main/resources/db/schema-mariadb.sql`이다. MariaDB 11.2와
`utf8mb4_unicode_ci`를 기준으로 하며, 애플리케이션이 기록하는 관측시각은 UTC로 통일한다.

## 핵심 식별자

- `pid`: 실제 선수 고유 ID. 한 선수가 여러 카드를 가질 수 있다.
- `cid`: 카드 고유 ID. `players`의 Primary Key다.
- 리뷰와 스쿼드는 카드 `cid`를 참조한다.
- 스쿼드 저장 시 서로 다른 `cid`라도 같은 `pid`면 중복 선수로 차단한다.

## 선수 데이터 구조

### 선수와 카드

| 테이블 | 키 | 역할 |
|---|---|---|
| `player_profiles` | `pid` | 이름, 국적, 생일, 신체 정보 등 실제 선수 공통값 |
| `players` | `cid` | OVR, 포지션, 팀, 리그, 이미지 등 카드별 값 |

`players`에는 목록 검색과 정렬에 필요한 값을 일반 컬럼으로 둔다.

- 이름: `player_name_kor`, `player_name_eng`
- 검색/필터: `overall_rating`, `primary_position`, `team_id`, `league_id`, `nationality_id`
- 이미지: `player_image_url`, `background_image_url`
- 상태: `is_tradeable`, `is_active`, `source_seen_at`
- 가격 cache: `price_checked_at`

사용자 카탈로그는 `is_active=true AND is_tradeable=true`인 카드만 기본 검색한다. 거래 불가
카드도 공식 원본 보존과 기존 참조 무결성을 위해 삭제하지 않는다.

상세 화면에서 카드와 함께 읽고 원본 호환이 필요한 묶음은 JSON으로도 보존한다.

- `stats_data`: 상세 능력치
- `prices_data`: `n8Price0`~`n8Price15` 호환 snapshot
- `traits_data`, `positions_data`, `play_styles_data`, `skills_data`
- `raw_data`: upstream 원본 전체

### 검색 필터와 이미지 메타데이터

| 기준 테이블 | 관계 테이블 | 용도 |
|---|---|---|
| `player_classes` | `card_classes` | 공식 클래스 필터와 카드의 다대다 관계 |
| `nations` | `players.nationality_id` | 국가명과 국기 URL |
| `leagues` | `players.league_id` | 리그명과 로고 URL |
| `teams` | `players.team_id` | 팀명과 로고 URL |
| - | `card_positions` | 주·잠재·부 포지션 검색 |
| `traits` | `card_traits` | 특성 검색과 아이콘 |
| `play_styles` | `card_play_styles` | 플레이스타일 검색과 아이콘 |
| `skills` | `card_skills` | 카드별 스킬과 레벨 |

공식 `ClassInfos` 필터는 서로 겹치며 전체 카드의 단일 시즌 값을 직접 제공하지 않는다.
따라서 `players.class_id` 같은 단일 컬럼은 사용하지 않고 `card_classes`만을 source of
truth로 사용한다.

### 가격

| 테이블 | 역할 |
|---|---|
| `card_prices_current` | `(cid, enhancement_level)`별 현재 가격 |
| `card_price_history` | 실제 가격이 바뀐 시점의 이력 |
| `price_refresh_jobs` | `pid` 기준 중복 제거된 백그라운드 갱신 작업 |

상세 API는 DB 값을 즉시 반환한다. 거래 가능 카드의 `price_checked_at`이 3시간보다
오래됐으면 `pid` 작업을 하나 queue한다. worker는 공식 `PlayerClass(pid)` 결과에서 같은
실제 선수의 거래 가능 카드 가격 16단계를 갱신한다.

worker 실행 중 프로세스가 종료된 경우 `locked_at`이 설정 시간보다 오래된 `RUNNING`
작업을 다시 `PENDING`으로 복구한다.

## 사용자 데이터

- `users`: 이메일, BCrypt 비밀번호 해시, 표시 이름
- `auth_refresh_tokens`: 원문을 저장하지 않는 SHA-256 Refresh Token 해시와 만료·폐기 시각
- `reviews`: 사용자와 카드 `cid`에 연결된 별점·한줄평. `(user_id, cid)`가 UNIQUE
- `review_reactions`: 리뷰별 사용자 좋아요·싫어요. `(review_id, user_id)`가 PK
- `squads`: 사용자 소유 스쿼드
- `squad_players`: 스쿼드 슬롯과 카드 `cid`

사용자 삭제 시 해당 리뷰와 스쿼드는 cascade 삭제된다. 스쿼드에서 참조 중인 카드는
임의 삭제하지 않고, 공식 snapshot에서 사라진 카드는 `is_active=false`로 비활성화한다.

## 스키마 적용

신규 Docker volume은 Compose가 스키마를 자동 적용한다.

```sh
docker compose up -d
docker compose ps
```

기존 개발 DB에는 다음 명령으로 additive schema와 명시된 호환 정리를 다시 적용한다.

```sh
docker exec -i fimobook-mariadb \
  mariadb -ufimobook -pfimobook-local fimobook \
  < backend/src/main/resources/db/schema-mariadb.sql
```

## 공식 snapshot 수집 및 적재

프로젝트 루트에서:

```sh
python3 scripts/collect_fcmobile.py \
  --output-dir data/fcmobile/snapshots/20260813-official \
  --delay-seconds 0.5
```

페이지는 원자적으로 저장되며 같은 명령을 다시 실행하면 이미 받은 페이지를 건너뛴다.
수집 도중 `manifest.json`은 `state=COLLECTING`, `complete=false`다. 전체 건수와 고유
`cid`가 일치한 뒤에만 `state=COMPLETE`, `complete=true`가 된다.

완성된 snapshot을 적재한다.

```sh
cd backend
./gradlew bootRun \
  --args="--fimo.players.import-enabled=true --fimo.players.import-path=../data/fcmobile/snapshots/20260813-official/cards.json --spring.main.web-application-type=none"
```

Importer는 `cid` 기준 upsert다. 완성 manifest의 `reportedCardCount`, `uniqueCardCount`,
실제 `cards.json` 길이가 일치하지 않으면 완성 snapshot으로 처리하지 않는다. 전체 import가
끝난 뒤에만 이전 source에서 사라진 카드를 비활성화한다. 각 카드의
`sourceObservedAt`보다 DB 가격의 `observed_at`이 더 최신이면 수집 목록의 오래된 가격으로
되돌리지 않는다.

## DBeaver 연결

| 항목 | 값 |
|---|---|
| Host | `localhost` |
| Port | `3306` |
| Database | `fimobook` |
| Username | `fimobook` |
| Password | `fimobook-local` |

## 확인 SQL

```sql
SHOW TABLES;

SELECT COUNT(*) AS total_cards,
       SUM(is_active) AS active_cards,
       COUNT(DISTINCT pid) AS real_players
FROM players;

SELECT cid, pid, player_name_kor, overall_rating, primary_position,
       is_tradeable, price_checked_at
FROM players
WHERE player_name_kor LIKE '%크루이프%'
ORDER BY overall_rating DESC;

SELECT *
FROM players
WHERE cid = 22901979;

SELECT enhancement_level, price, observed_at, changed_at
FROM card_prices_current
WHERE cid = 22901979
ORDER BY enhancement_level;

SELECT pc.class_id, pc.class_name
FROM card_classes cc
JOIN player_classes pc ON pc.class_id = cc.class_id
WHERE cc.cid = 22901979;

SELECT pid, requested_cid, status, attempts, available_at, locked_at, last_error
FROM price_refresh_jobs
ORDER BY created_at;
```

실제 DB 연결 테스트:

```sh
cd backend
FIMO_DB_TEST=true ./gradlew clean test
```
