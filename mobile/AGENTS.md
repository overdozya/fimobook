# Fimobook Mobile

이 디렉터리는 피모북의 최종 사용자용 모바일 앱이다. 루트 `AGENTS.md`의 도메인·API·
데이터 갱신 원칙을 상속하며, 충돌할 때는 루트 문서와 사용자의 최신 지시를 우선한다.

## Stack

- Expo SDK 57
- React Native
- TypeScript strict mode
- Expo Router의 file-based routing
- `expo-image`를 사용한 원격 카드 이미지 표시와 cache

Expo SDK나 Router의 주요 API를 변경할 때는 설치된 버전과 Expo 57 공식 문서를 먼저
대조한다. Expo가 생성한 예제 화면이나 범용 데모 구조를 제품 코드에 남기지 않는다.

## Architecture

```text
src/app                 route와 화면 조립
src/features            도메인별 타입·API·표현 로직
src/components          재사용 가능한 UI
src/services            공통 HTTP와 환경 설정
src/constants           색상과 공통 디자인 값
```

- 화면에서 URL 문자열을 직접 조립하지 않는다.
- 모바일 앱은 MariaDB나 FC Mobile 공식 서버에 직접 접근하지 않는다.
- 모든 선수·인증·스쿼드·리뷰 데이터는 Spring REST API를 통한다.
- 공개 API 주소는 `EXPO_PUBLIC_API_URL`로 주입한다. 이 값에 secret을 넣지 않는다.
- 개발 중 환경변수가 없으면 Expo dev host와 Spring 기본 포트 8080을 사용한다.

## Player Flow

- 검색 목록은 `/api/players/search` pagination API만 사용한다.
- 기본 page size는 20이다.
- 다음 페이지는 스크롤 하단에서 한 번씩 요청하고 동시에 중복 요청하지 않는다.
- 검색어나 필터가 바뀌면 이전 요청을 취소하고 목록·page를 초기화한다.
- 목록의 React key와 상세 route는 `pid`가 아니라 `cid`다.
- 상세 화면은 `/api/players/{cid}`의 기존 upstream 호환 필드를 소비한다.
- 가격 갱신 여부나 시각을 사용자 화면에 노출하거나 별도 갱신 버튼을 만들지 않는다.

## UI Rules

- 현재 단계에서는 검색 → 카드 목록 → 상세의 vertical slice를 먼저 완성한다.
- 목록은 20장 단위로 렌더링하고 빈 화면·로딩·오류·마지막 페이지 상태를 구분한다.
- 카드 이미지 비율을 보존하며 `expo-image`의 disk/memory cache를 사용한다.
- API의 `/api/assets/...` 상대 URL은 `apiBaseUrl`을 붙인 뒤 사용한다.
- 카드 위 OVR·포지션·선수명은 공식 좌표와 `FCOAllSans`를 사용하는 공통
  `PlayerCardVisual` 컴포넌트에서만 합성한다.
- 공식 이미지 전체를 앱 bundle에 넣지 않는다. API가 반환하는 원격 URL을 지연 로딩하고,
  운영에서는 수집된 자산을 피모북 오브젝트 스토리지/CDN URL로 전환한다.
- 이미지 원본 URL이 없거나 upstream 파일이 사라진 카드는 플랫폼 공통 placeholder를 쓴다.
- 플랫폼 기본 접근성 속성과 충분한 touch target을 유지한다.
- 기능이 없는 버튼이나 가짜 데이터는 만들지 않는다.

## Verification

모바일 변경 후 최소한 다음을 실행한다.

```sh
npm run typecheck
npm run lint
npx expo export --platform web
```

가능하면 Spring을 실행한 뒤 실제 검색·상세 API와 에뮬레이터 또는 기기에서도 확인한다.
실기기에서 확인하지 못한 경우 완료했다고 보고하지 않는다.
