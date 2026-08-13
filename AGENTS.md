# Project: 피모북

## Project Overview

피모북은 FC Mobile 유저를 위한 선수 정보 및 스쿼드 관리 서비스다.

단순 학습용 데모가 아니라,
장기적으로 실제 사용자가 사용할 수 있는 서비스로 발전시키는 것을 목표로 한다.

현재는 Vue + Spring Boot + MariaDB 기반의 단일 애플리케이션으로 개발한다.

이 프로젝트는 동시에 개발 학습용 프로젝트이기도 하므로,
기능이 실제로 동작하는 것과 전체 데이터 흐름을 이해할 수 있는 구조를 중요하게 생각한다.

## Core Product Goals

피모북의 핵심 기능은 다음과 같다.

1. FC Mobile 선수 검색
2. 선수 카드별 상세 정보 조회
3. 스쿼드 빌더
4. 선수 카드별 평가 및 한줄평
5. 사용자가 만든 스쿼드 저장
6. 진화 시뮬레이터
7. 사용자 계정 및 로그인
8. 이후 커뮤니티 기능 확장
9. 필요할 경우 AI 기반 기능 추가

## Current Stack

### Frontend

- Vue
- Vue Router
- JavaScript

### Backend

- Java
- Spring Boot
- REST API

### Database

- MariaDB

### Infrastructure

- Docker
- Docker Compose 사용 가능
- DBeaver 사용 가능

### Development Ports

- Vue: 5173
- Spring Boot: 8080
- MariaDB: 3306

## Current Project State

현재 구현된 기능:

- 선수 검색 UI
- 선수 카드 UI
- 선수 상세보기
- 선수 세부 능력치 표시
- 카드 이미지 + 선수 이미지 표시
- 0진 ~ 15진 가격 표시
- 선수 한줄평 UI
- 스쿼드 필드 UI
- 스쿼드 슬롯 선수 등록
- 동일 실제 선수 중복 등록 방지
- Vue → Spring REST API 통신
- GET /api/players?name=... 선수 검색 API

현재 선수 데이터는 players.json에서 가져오고 있다.

현재 흐름:

Vue
→ REST API
→ Spring Boot
→ players.json
→ Spring Boot
→ JSON Response
→ Vue

## Immediate Goal

players.json을 직접 조회하는 구조를 MariaDB 기반으로 전환한다.

목표 흐름:

Vue
→ REST API
→ Spring Boot
→ MariaDB
→ Spring Boot
→ JSON Response
→ Vue

기존 Vue의 선수 검색 및 상세 UI는 DB 전환 이후에도 정상적으로 동작해야 한다.

## Player Domain Model

FC Mobile 데이터에서 중요한 식별자는 `pid`와 `cid`다.

### pid

실제 축구선수의 ID.

예:

손흥민이라는 실제 선수는 하나의 pid를 가진다.

같은 실제 선수의 서로 다른 시즌 카드는 같은 pid를 공유할 수 있다.

### cid

FC Mobile 선수 카드의 고유 ID.

시즌이나 클래스가 다르면 같은 실제 선수라도 서로 다른 cid를 가진다.

따라서:

- cid = 선수 카드의 Primary Key
- pid = 실제 선수 식별자
- 하나의 pid가 여러 cid를 가질 수 있음

예:

손흥민 UTOTY
pid = 200104
cid = A

손흥민 다른 시즌
pid = 200104
cid = B

두 카드는 서로 다른 선수 카드 데이터다.

## Squad Domain Rules

스쿼드는 기본적으로 11명의 선수를 가진다.

한 스쿼드 안에서는 같은 실제 선수를 중복 사용할 수 없다.

즉:

cid가 달라도 pid가 같으면 같은 스쿼드에 동시에 등록할 수 없다.

예:

손흥민 UTOTY + 손흥민 다른 시즌

→ 같은 pid이므로 같은 스쿼드에서 중복 사용 불가.

스쿼드 저장 기능이 추가되면
사용자별로 여러 스쿼드를 저장할 수 있도록 설계한다.

## Player Data

현재 players.json에는 다음 종류의 정보가 존재한다.

- cid
- pid
- 선수 이름
- 클래스 / 시즌
- 선수 이미지
- 카드 배경 이미지
- 포지션
- 서브 포지션
- OVR
- 팀
- 리그
- 국가
- 키
- 몸무게
- 주발
- 약발
- 개인기
- 특성
- 플레이스타일
- 상세 능력치
- 0진 ~ 15진 가격
- 기타 FC Mobile 원본 데이터

DB 설계 시 모든 원본 값을 반드시 개별 컬럼으로 만들 필요는 없다.

검색, 정렬, 필터링, 관계 설정에 자주 사용하는 데이터는 일반 컬럼으로 저장하고,
복잡하거나 자주 검색하지 않는 상세 데이터는 JSON 컬럼 등 적절한 방식으로 저장해도 된다.

구현 방식은 프로젝트 유지보수성과 기능 요구사항을 기준으로 판단한다.

## Reviews

선수 평가는 실제 선수(pid)가 아니라 선수 카드(cid)를 기준으로 한다.

즉 같은 실제 선수라도 시즌 카드가 다르면 별도의 평가 공간을 가진다.

평가 기본 구조:

- user
- cid
- rating (1 ~ 5)
- short review
- created_at
- updated_at

향후 필요하면:

- 좋아요
- 싫어요

등을 확장할 수 있다.

댓글형 리뷰 시스템은 현재 핵심 요구사항이 아니다.

## Squad Persistence

향후 DB 저장 구조에서는 최소한 다음 개념이 필요하다.

- users
- squads
- squad_players

squad_players는 어떤 스쿼드의 어떤 포지션 슬롯에 어떤 cid가 등록되어 있는지를 표현한다.

구체적인 테이블 구조는 구현 시점에 가장 적절한 방식으로 설계한다.

## Future Community

커뮤니티 기능은 현재 우선순위가 높지 않다.

향후 필요하면 다음 개념을 추가할 수 있다.

- posts
- comments
- likes

현재 단계에서 사용하지 않는 커뮤니티 구조를 미리 과도하게 구현할 필요는 없다.

## Evolution Simulator

선수 카드의 진화는 최대 15진까지 표현한다.

초기 버전에서는 실제 FC Mobile의 모든 강화 재료 및 확률 공식을 완벽하게 복제할 필요는 없다.

우선 다음 기능을 목표로 한다.

- 현재 진화 단계
- 목표 진화 단계
- 게이지
- 성공 / 실패
- 랜덤 결과

향후 실제 데이터 또는 더 정확한 공식이 확보되면 확장할 수 있다.

## Authentication

향후 사용자 기능을 위해 로그인 시스템을 추가한다.

로그인 전에도 다음 기능은 사용할 수 있도록 하는 방향을 선호한다.

- 선수 검색
- 선수 상세 조회
- 선수 평가 조회
- 스쿼드 구성

로그인이 필요한 기능:

- 선수 평가 작성
- 선수 평가 수정 / 삭제
- 스쿼드 저장
- 저장된 스쿼드 관리

Google 로그인 등의 OAuth 로그인은 이후 추가할 수 있다.

## API Direction

Frontend와 Backend는 REST API를 통해 통신한다.

예상 API 형태:

GET /api/players
GET /api/players/{cid}

GET /api/reviews
POST /api/reviews
PUT or PATCH /api/reviews/{id}
DELETE /api/reviews/{id}

GET /api/squads
POST /api/squads
GET /api/squads/{id}
PUT /api/squads/{id}
DELETE /api/squads/{id}

실제 URI와 DTO 구조는 구현 과정에서 더 적절한 형태가 있다면 변경해도 된다.

## Architecture Direction

현재 단계에서는 하나의 Spring Boot Backend를 중심으로 개발한다.

예상 구조는 대략 다음과 같다.

Controller
→ Service
→ Repository
→ Database

하지만 프로젝트 규모에 비해 불필요한 계층이나 추상화는 만들 필요가 없다.

반대로 현재 구조가 기능 확장에 방해가 된다면 리팩터링해도 된다.

Vue 역시 필요하다면:

- views
- components
- services
- stores

등으로 구조를 개선할 수 있다.

## Development Philosophy

최우선 순위는 실제 동작하는 서비스를 만드는 것이다.

기존 구조를 무조건 보존할 필요는 없다.

더 나은 구현을 위해 필요한 경우:

- 파일 이동
- 컴포넌트 분리
- 클래스 분리
- DB 스키마 변경
- API 구조 개선
- 리팩터링
- 테스트 추가
- Docker 구성 추가

등을 자율적으로 수행해도 된다.

단순히 기존 코드를 유지하기 위해 좋지 않은 구조를 계속 사용할 필요는 없다.

기존 구현보다 명확하고 유지보수하기 좋은 방법이 있다면 개선한다.

## Engineering Preferences

다음 방향을 선호한다.

- 구현 가능한 단순한 구조
- 이해 가능한 코드
- 실제 실행 및 테스트
- 명확한 데이터 흐름
- 과도한 추상화 회피
- 현재 규모에 맞는 설계
- 기능 확장이 가능한 구조
- 데이터 손실 방지
- 기존 사용자 기능이 깨지지 않도록 검증

## Technology Direction

새로운 기술 도입 자체가 목적은 아니다.

현재 Vue + Spring Boot + MariaDB로 충분히 해결 가능한 문제라면 해당 스택을 우선 사용한다.

하지만 기능 구현이나 유지보수에 명확한 이점이 있다면 필요한 라이브러리나 도구를 추가해도 된다.

현재 단계에서는 MSA가 필수 요구사항은 아니다.

서비스가 충분히 커지고 분리할 이유가 생기면 이후 MSA를 검토한다.

AI 기능 역시 서비스에 실제 가치가 있을 때 추가한다.

## Testing and Validation

코드를 수정한 경우 가능한 범위에서 실제 실행으로 검증한다.

예:

- Vue build
- Spring build
- Spring tests
- REST API 호출
- DB connection
- SQL query
- Docker container health

실행하지 않은 테스트를 성공했다고 가정하지 않는다.

에러가 발생하면 임시로 숨기기보다 원인을 파악하고 해결하는 것을 우선한다.

## Data Migration

players.json은 현재 중요한 원본 데이터다.

MariaDB 전환 과정에서는 데이터 손실이 없어야 한다.

DB 적재가 정상적으로 완료되고 API가 MariaDB를 통해 동작하더라도,
players.json은 당분간 원본 데이터 또는 백업 데이터로 유지할 수 있다.

## Near-Term Roadmap

### Phase 1

Player data persistence

players.json
→ MariaDB

완료 조건:

Vue
→ Spring REST API
→ MariaDB
→ JSON
→ Vue

전체 검색 흐름이 정상 작동한다.

### Phase 2

Review persistence

현재 임시 선수평 저장 방식
→ Spring REST API + MariaDB CRUD

### Phase 3

Squad persistence

현재 Vue 메모리 기반 스쿼드
→ 사용자별 DB 저장

### Phase 4

Authentication

회원 및 로그인 시스템 추가

### Phase 5

Evolution Simulator

진화 시뮬레이션 기능 구현

### Phase 6

Community and AI

필요에 따라 커뮤니티 및 AI 기능 확장

## Agent Autonomy

프로젝트 목표를 달성하기 위해 필요한 구현 판단은 자율적으로 해도 된다.

현재 코드가 좋지 않거나 확장에 방해가 된다면 수정하거나 재구성해도 된다.

단순히 최소 수정만 하는 것보다
최종적으로 정상 동작하고 유지보수 가능한 결과를 만드는 것을 우선한다.

작업 중 문제가 발견되면 가능한 범위에서 직접 분석하고 해결한다.

명백하게 사용자 판단이 필요한 제품 정책이나
돌이키기 어려운 선택이 아니라면
사소한 구현 결정마다 사용자에게 확인을 요구하지 않아도 된다.
