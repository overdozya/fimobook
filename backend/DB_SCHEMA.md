# MariaDB 선수 스키마 설계

## 설계 기준

- `cid`는 카드 한 장의 고유 ID이므로 `players`의 기본 키로 사용한다.
- `pid`는 실제 선수 ID이며 같은 선수의 시즌별 카드가 여러 개 존재할 수 있으므로 유일 키로 만들지 않고 일반 인덱스만 둔다.
- 현재 검색과 카드 목록에서 자주 쓰는 값은 일반 컬럼으로 저장한다.
- 상세 화면에서 묶음으로 읽고 개별 조건 검색을 하지 않는 능력치, 가격, 특성은 JSON 컬럼으로 저장한다.
- 현재 쓰지 않는 원본 필드는 `raw_data`에 보존해 초기 적재 과정에서 정보가 유실되지 않게 한다.

## 필드 분류

### 현재 기능에 직접 필요한 일반 컬럼

| JSON 필드 | DB 컬럼 | 용도 |
|---|---|---|
| `cid` | `cid` | 카드 식별, Vue 목록 key, 리뷰 key |
| `pid` | `pid` | 동일 선수의 다른 카드 판별 |
| `playerKor`, `playerEng` | `player_name_kor`, `player_name_eng` | 검색 및 상세 표시 |
| `pimage`, `bimage` | `player_image_url`, `background_image_url` | 카드 이미지 |
| `ovr`, `position`, `potentialPosition` | `overall_rating`, `primary_position`, `potential_position` | 카드 및 상세 표시 |
| `teamid`, `team`, `leagueid`, `league` | 대응 팀·리그 컬럼 | 상세 표시와 향후 필터 |
| `nationality`, `nation` | 대응 국가 컬럼 | 상세 표시와 향후 필터 |
| `height`, `weight`, `mainFoot`, `WFA` | 대응 신체·주발 컬럼 | 상세 표시 |
| `skillMovesLevel`, `skillMovesName` | 대응 개인기 컬럼 | 상세 표시 |
| `PlayerYear` | `player_year` | 카드 구분 및 향후 필터 |

### 현재 필요하지만 묶어서 저장하는 필드

- `stats_data`: `ACC`, `SPD`, `FIN`, `SHO`, `LSA`, `VOL`, `PEN`, `SPA`, `LPA`, `VIS`, `CRO`, `CUR`, `FRK`, `DRI`, `BAC`, `AGI`, `REA`, `BAL`, `MRK`, `STT`, `SLT`, `AWR`, `HEA`, `STR`, `AGG`, `JMP`, `STA`
- `prices_data`: `n8Price0`부터 `n8Price15`
- `traits_data`: `Trait` 배열

이 값들은 현재 상세 화면에서 카드 한 장과 함께 전부 읽는다. 이번 단계에서 개별 컬럼이나 별도 테이블로 과도하게 정규화하지 않는다. 가격 이력 검색이나 능력치 조건 검색이 실제 요구사항이 되면 그때 별도 테이블 또는 컬럼으로 승격한다.

### 나중에 검토할 필드

현재 UI가 사용하지 않는 `enhance`, `grade`, `training`, `cardtype`, `skillInfo`, 보조 포지션, 워크레이트, 골키퍼 능력치 등은 우선 `raw_data`에 보존한다. 검색·정렬 요구가 생긴 필드만 일반 컬럼으로 이동한다.

## 실행, 적용 및 연결 확인

스키마 파일은 `src/main/resources/db/schema-mariadb.sql`에 있다. 프로젝트 루트의 Compose 환경은 빈 volume을 최초 초기화할 때 이 파일을 자동 적용한다. 이미 생성된 DB에 스키마를 다시 적용할 때는 다음 명령을 사용한다.

```sh
docker exec -i fimobook-mariadb \
  mariadb -ufimobook -pfimobook-local fimobook \
  < backend/src/main/resources/db/schema-mariadb.sql
```

연결 정보는 다음 환경변수로 전달한다.

```text
FIMO_DB_URL=jdbc:mariadb://localhost:3306/fimobook
FIMO_DB_USERNAME=fimobook
FIMO_DB_PASSWORD=fimobook-local
```

로컬 개발 기본값은 `application.properties` 및 `compose.yaml`에 맞춰져 있어 별도 환경변수 없이도 연결된다. 다른 비밀번호를 사용할 때만 환경변수를 덮어쓴다.

선수 원본을 `cid` 기준 upsert로 적재한다.

```sh
./gradlew bootRun \
  --args='--fimo.players.import-enabled=true --spring.main.web-application-type=none'
```

여러 번 실행해도 같은 `cid`는 갱신되며 행이 중복 생성되지 않는다.

스키마를 적용하고 위 접속 정보를 설정한 뒤 다음 명령으로 실제 MariaDB 연결과 `SELECT 1`을 확인한다.

```sh
FIMO_DB_TEST=true ./gradlew test --tests '*DatabaseConnectionTests'
```

`FIMO_DB_TEST=true`가 없으면 DB 통합 테스트는 건너뛴다.

## 조회 흐름

`PlayerController`는 더 이상 JSON 파일을 읽지 않는다. `PlayerRepository`가 `player_name_kor` 컬럼으로 부분 검색하고, 일반 컬럼 및 JSON 컬럼을 기존 FC Mobile JSON 키로 조립해 반환한다. `raw_data`는 원본 손실 방지와 아직 승격하지 않은 필드 보존에 사용한다.

## DBeaver

| 항목 | 값 |
|---|---|
| Host | `localhost` |
| Port | `3306` |
| Database | `fimobook` |
| Username | `fimobook` |
| Password | `fimobook-local` |

직접 확인할 SQL:

```sql
SHOW TABLES;

SELECT COUNT(*) FROM players;

SELECT cid, pid, player_name_kor, overall_rating, primary_position
FROM players
WHERE player_name_kor LIKE '%크루이프%'
ORDER BY overall_rating DESC;

SELECT *
FROM players
WHERE cid = 22901979;
```

## 사용자 기능 테이블

- `users`: 이메일, BCrypt 비밀번호 해시, 닉네임
- `reviews`: 사용자와 선수 카드 `cid`에 연결된 별점·한줄평
- `squads`: 사용자별 스쿼드
- `squad_players`: 슬롯과 선수 카드 `cid` 연결

`users` 삭제 시 해당 리뷰와 스쿼드는 cascade 삭제된다. 선수 카드는 원본 데이터이므로 스쿼드에서 참조 중일 때 임의 삭제되지 않는다.
