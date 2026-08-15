# 피모북 모바일

Expo SDK 57, React Native, TypeScript로 만드는 피모북 모바일 앱입니다. MariaDB나 FC
Mobile 서버에 직접 연결하지 않고 피모북 Spring REST API만 사용합니다.

## 현재 구현

- 선수 전체/이름 검색
- 20장 단위 pagination과 무한 스크롤
- `cid` 기반 카드 상세 route
- 카드 이미지, 기본 정보, 클래스, 특성, 플레이스타일
- 세부 능력치와 0진~15진 가격
- 로딩, 빈 결과, 오류와 재시도 상태
- 포지션·클래스·리그·국가·팀·특성·플레이스타일·OVR·가격 필터
- 이메일 회원가입·JWT 로그인, 회전형 Refresh Token과 네이티브 SecureStore 보관
- 로그인 없이 사용하는 로컬 스쿼드와 로그인 사용자의 서버 동기화
- 동일 `pid` 선수 중복 등록 차단, 팀 OVR은 공식 계산식 확인 전 `0`
- 카드별 평가 작성·수정·삭제와 사용자별 좋아요·싫어요

## API 주소

환경변수가 없으면 개발 중 Expo host의 8080 포트를 자동으로 사용합니다. 명시적으로
설정하려면 `.env.example`을 참고해 `mobile/.env.local`을 만듭니다.

```sh
EXPO_PUBLIC_API_URL=http://localhost:8080
```

- iOS simulator/web: 보통 `http://localhost:8080`
- Android emulator: 보통 `http://10.0.2.2:8080`
- 실제 기기: Mac과 같은 Wi-Fi의 Mac LAN IP 사용

`EXPO_PUBLIC_` 값은 앱 bundle에 포함되므로 secret을 넣으면 안 됩니다.

## 실행

먼저 프로젝트 루트의 MariaDB와 Spring을 실행합니다.

```sh
docker compose up -d
cd backend
./gradlew bootRun
```

다른 터미널에서:

```sh
cd mobile
npm install
npm start
```

Metro 기본 포트는 8081이며 Spring의 8080과 충돌하지 않습니다.

## 검사

```sh
npm run typecheck
npm run lint
npx expo export --platform web
```

실제 Android/iOS 기기 검증은 아직 남아 있습니다.
