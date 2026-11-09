# NaviHelper 어댑터

`app`의 `navigation` 패키지는 NaviHelper 3.0.5의 저수준 어댑터를 제공한다. `NaviClient` 계약을 `PleosNaviClient`가 구현하며,
`AndroidNaviHelperApi`만 실제 SDK 객체를 생성한다. 순수 JVM인 `core:data`에는 SDK 타입이나 Android 의존성을 추가하지 않는다.

Koin에 `factory<NaviClient>`로 등록한다. 요청할 때마다 새 클라이언트를 받는 것이 아니라, 사용 범위의 소유자가 한 번 주입받아 보관하고 종료 시
`release()`를 호출한다. 등록 자체로 SDK를 초기화하지 않는다. 현재 화면의 mock 저장소는 그대로이며 화면에서 이 클라이언트를 사용하지 않는다.

## 사용 순서

1. `NaviClient`를 주입받거나 `PleosNaviClient(applicationContext)`를 생성한다.
2. `initialize(listener)`를 한 번 호출한다.
3. `NaviClientListener.onConnected()` 이후 요청한다. 콜백 스레드는 SDK가 결정하므로, UI 갱신과 클라이언트 요청은 소유자의 main
   dispatcher로 전환한다.
4. 조회·Trip·경로 이벤트와 `onError()`를 리스너에서 처리한다. 콜백은 빠르게 반환하며 예외를 던지지 않는다.
5. 사용 범위 종료 시 메인 스레드에서 `release()`를 호출한다. 같은 SDK 리스너를 제거한 뒤 SDK를 해제한다. 해제는 중복 호출할 수 있다.

초기화·요청·해제는 메인 스레드에서 호출한다. 연결 완료 전, 초기화 실패 후, 해제 후의 요청은 `IllegalStateException`을 던진다. 초기 연결 실패와
초기화/리스너 등록의 동기 예외는 `onConnectionFailed(Throwable)`로 전달한다. 실패·해제 후에는 새 클라이언트가 필요하며 자동 재시도하지 않는다. 요청의
동기 예외는 호출자에게 그대로 전달한다. 해제 도중 리스너 제거가 실패해도 SDK 해제를 시도하며, 해제 예외는 호출자가 처리한다.

## 요청과 결과

| 메서드                                             | 결과 콜백 / 의미                                                                           |
|----------------------------------------------------|--------------------------------------------------------------------------------------------|
| `getCurrentLocationInfo()`                         | `onCurrentLocationInfo(CurrentLocationInfo)`                                               |
| `getDestinationInfo()`                             | `onDestinationInfo(DestinationInfo)`                                                       |
| `getWaypointInfo()`                                | `onWayPointInfo(List<WaypointInfo>)` — SDK의 대소문자 표기 유지                            |
| `getRouteStateInfo()`                              | `onRouteStateInfo(RouteStateInfo)`                                                         |
| `requestTrip(TripRequest)`                         | `onTripInfo(TripInfo)` — 거리 m, ETA 초, 원본 응답 전달                                    |
| `requestRoute(RouteInfo)`                          | `onRouteStarted`, `onRoutingCompleted`, `onDestinationChanged`, `onError` 등으로 결과 확인 |
| `addWaypoint(RequestWaypointInfo)`                 | `onWaypointChanged`, `onWayPointInfo`, `onError` 등으로 결과 확인                          |
| `requestNavigationScreen(NavigationScreenRequest)` | SDK 화면 요청 제출. 화면 표시 완료를 보장하는 반환값은 없음                                |

`onRouteCancelled()`도 전달한다. 위 리스너 계약에 없는 SDK 이벤트는 전달하지 않는다. 초기화 도중 해제한 세션의 지연 연결 결과와 해제 후 수신한 이벤트는
무시한다. 이미 실행 중인 사용자 콜백을 취소하는 기능은 없다.

## 호출자가 담당할 정책

- 요청 반환은 결과 수신이나 경로 설정 완료가 아니다. 필요한 콜백의 타임아웃과 성공 판정은 사용 흐름에서 구현한다.
- SDK 응답에는 요청 ID가 없다. 어댑터는 조회·Trip 응답을 특정 요청의 suspend 반환값으로 바꾸거나 지연·중복 응답을 새 요청에 귀속하지 않는다. 후보 순차
  조회·POI 대조·동일 후보 재조회 제한 등은 상위 계층의 책임이다. 기존 [PoC 한계](poc/POC_TRIP_REPORT.md)를 따른다.
- 경로·경유지 요청 전 현재 목적지와 기존 경유지를 확인하고 변경 후 재조회한다. 경유지는 최대 2개이며 기존 경유지를 보존하는 정책은 상위 계층에서 결정한다.
- 외부 주유소 ID를 내비 POI ID로 가정하지 않는다. 좌표·POI·경유지 인덱스는 해당 내비에서 검증한 값을 전달한다.
- 임의 출발점·경유·왕복 경로 계산, 차량 위치와 다른 위치 공급자의 일치, `isRouting`만으로 안내 활성 여부 판정은 보장하지 않는다.
- 내비 탑재 여부와 실제 Navigation 앱 구현에 따라 연결·이벤트 제공이 달라질 수 있다. 주행 규제 연동 및 주행 중 사용 적합성은 별도 작업이다.

## 권한과 검증

앱 매니페스트에 `pleos.car.permission.NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`, `NAVI_CUSTOM_MAP`을 선언한다. 로컬 API
Reference에서 세 권한은 normal로 명시하며, 스마트폰 위치 권한을 추가하지 않는다.

`PleosNaviClientTest`는 Fake SDK 경계로 연결 게이트, 요청 데이터 보존, 조회/Trip/경로/오류 콜백, 중복 응답, 초기화 실패, 동기 예외, 지연 연결
결과, 리스너 제거 및 해제를 검증한다. 실제 내비 서비스 동작을 검증하는 테스트는 아니다. 실행 명령은 [TESTING.md](TESTING.md)를 따른다.

구현 근거:
로컬 [초기화·해제](../pleos_sdk_guide/앱%20개발·연동%20가이드/SDK%20사용%20가이드/NaviHelper%20SDK/Programming%20Guide/Initialization.md), [권한](../pleos_sdk_guide/앱%20개발·연동%20가이드/SDK%20사용%20가이드/NaviHelper%20SDK/Programming%20Guide/Declaring%20permissions.md), [API Reference](../pleos_sdk_guide/앱%20개발·연동%20가이드/SDK%20사용%20가이드/NaviHelper%20SDK/API%20Reference/ai.pleos.playground.navi.helper/NaviHelper/NaviHelper.md).
