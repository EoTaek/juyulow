# NaviHelper 어댑터

`app`의 `navigation` 패키지는 NaviHelper 3.0.5의 저수준 어댑터를 제공한다. `NaviClient` 계약을 `PleosNaviClient`가 구현하며,
`AndroidNaviHelperApi`만 실제 SDK 객체를 생성한다. 순수 JVM인 `core:data`에는 SDK 타입이나 Android 의존성을 추가하지 않는다.

Koin에 `factory<NaviClient>`로 등록한다. 요청할 때마다 새 클라이언트를 받는 것이 아니라, 사용 범위의 소유자가 한 번 주입받아 보관하고 종료 시
`release()`를 호출한다. 등록 자체로 SDK를 초기화하지 않는다. 화면의 LiveJourneyRepository가 하나의 NavigationRepository 세션을
공유하며 앱 루트 ViewModel이 이를 소유한다.

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

### 실제 저장소 (화면 연결 전)

`core:data/NavigationRepository`와 `core:model/Navigation.kt`는 SDK 독립 계약이다. `app/navigation/PleosNavigationRepository`를
Koin factory로 주입하고 소유자가 initialize → refresh(복귀 시 포함) → release를 호출한다. 등록 자체로 SDK를 연결하지 않는다.
화면은 LiveJourneyRepository로 실제 내비 저장소에 연결했다. 변경 이벤트는 idle 시 재조회를 예약한다. 요청·콜백 처리는 main dispatcher로
직렬화한다.

- 4종 상태를 모두 수신해야 스냅샷을 제공하며 수신 필드 목록으로 부분 응답을 드러낸다. 이전 스냅샷은 오류 상태에서 과거 관측값으로만 유지한다.
  스냅샷은 트랜잭션 보장이 아니다. 연결/조회 10초, Trip/경로 이벤트 대기 30초다. 조회 귀속이 불명확해지면 해당 저장소의 후속 조회를 차단한다.
- 목적지 없음은 SAFE_GUIDING과 명시적인 빈 목적지 응답이 함께 있는 경우만 인정한다. 아래 에뮬레이터에서 이 형태를 확인했다.
  NOT_WORKING은 가용 상태로 취급하지 않는다. 무응답은 목적지 없음이 아니다.
- 후보는 외부 주유소 ID가 아닌 내비 POI ID, 명시적인 sub-ID와 검증된 응답 좌표가 필요하다. sub-ID가 null인 후보는 자동으로 0으로 바꾸지 않는다.
  기존 PoC의 null sub-ID fixture를 그대로 경로 설정에 사용할 수 있다고 가정하지 않는다.
- Trip은 후보별 순차 요청이며 단일 응답 목적지 ID와 축별 0.000001도 좌표를 대조한다. 편도 m/초에 주유시간을 더하지 않는다.
  편도 Trip 배치 전후에는 출발 위치 50m 이내·동일 경로 옵션을 확인하며, 기존 목적지·경유지·안내 상태 변경은 실패 조건에서 제외한다. 요청 후보는 프로세스 내
  재조회하지 않으며
  오류/타임아웃/취소/귀속 불명 시 공유 ledger가 후속 Trip을 차단한다. 프로세스 재시작이 서버 취소를 보장하지 않는다.
- 경로 확정 직전 전체 내비 문맥을 대조한다. 목적지 대체는 경유지 제거를 확인한다. 경유 추가는 addWaypoint (FIRST)를 사용하며 목적지와 기존 경유지 순서를
  보존한다. 경유지 2개·중복 POI는 거부한다. 조회 좌표가 정확히 (0,0)인 경유지만 좌표 검사에서 제외하며, 목적지·경유 ID/개수/순서·옵션·안내 상태는 최초 요청과
  재확인에서 같은 함수로 검증한다.
- 관련 경로 이벤트 이후 재조회한 ID·좌표·경유 순서·경로 옵션·안내 모드가 요청과 맞아야 성공이다.
  결과 불명 시 자동 재제출하지 않는다. 명시적인 refresh에서 기대 경로가 확인되면 불명 상태를 해소한다.
  부분 반영 상태를 자동 복구하거나 덮어쓰지 않는다. 내비 화면 열기 성공은 요청 제출만 의미한다.
- SDK 경계에 requestRouteWithWaypoints 및 cancelRoute를 추가했다. cancelRoute는 연결 테스트의 원상복구에도 사용한다.
  경유 추가거리·왕복·통행료 계산과 일반 POI 매핑은 제공하지 않는다. 주행 중 UX 연결은 화면 통합의 후속 범위다.

경로 이벤트가 안내 상태 갱신보다 먼저 올 수 있으므로, 이벤트 이후 250ms 간격으로 상태를 재조회해 반영을 기다린다.
30초 예산 이후 새 조회를 시작하지 않으며 마지막 조회는 자체 10초 제한까지 걸릴 수 있다. 전체 대기로 진행 중 조회를 취소해
복원용 재조회까지 잠그지 않는다. 실제 조회 자체의 누락/취소 시에는 기존 귀속 불명 차단 정책을 유지한다.

### 에뮬레이터 확인 (2026-11-10)

- Pleos Connect v2.1.0 / Android 14 / emulator-5554, 이미지 `RELEASE.release_connect_v2.1.0`,
  Navigation `ai.umos.maps.android.navigation.app` `3.3.2-rc5-hotfix.3`, NaviHelper 3.0.5. 주행 규제 비활성을 확인했다.
- 4종 조회 성공: 위치 `(37.4127212,127.094681)`, 목적지 없음, 경유지 없음, SAFE_GUIDING, RECOMMENDED.
- 소설한남 고정 POI `36khq926enpkcm5nqhe4`, 명시적 요청 sub-ID `0`으로 Trip 17,593m / 2,091초,
  목적지 설정 및 목적지 없는 초기 상태 복원 성공. 응답 목적지의 sub-ID는 null이었다.
- 같은 POI의 요청 좌표 `(37.535225,127.009125)`, Trip 응답 `(37.5351374,127.0089799)`,
  목적지 조회 `(37.53539166,127.00923888)`가 다르다. `NaviCandidate.routeResponseLatitude/Longitude`로
  Trip 대조 좌표와 경로 반영 대조 좌표를 분리했다. 임의 후보에 이 좌표나 sub-ID 규칙을 일반화하지 않는다.
- 소설한남을 목적지로 설정한 뒤 더현대서울 `36khqkj6n9rfcm5nqhe3`을 addWaypoint(FIRST)로 추가했다.
  Trip은 23,638m / 3,004초였다. 목적지는 보존됐고 경유 POI ID도 맞았으나 getWaypointInfo의 위도/경도는 `(0,0)`이었다.
  저장소는 이를 검증된 좌표로 간주하지 않아 ROUTE_NOT_CONFIRMED를 반환했고 연결 테스트는 실패했다. 초기 상태 복원은 성공했다.
- 당시 경유지 성공 확인은 이 환경에서 **채택 보류**였다. `(0,0)`을 정답 fixture로 등록하거나 ID만으로 성공 판정을 완화하지 않았다.
  기존 경유지 1개에서의 실제 삽입 순서 및 requestRouteWithWaypoints 실제 동작은 미검증이다. 관련 Fake 검증과 구분한다.
- 초기 실패 실험 중 전체 타임아웃이 재조회를 잠근 문제도 확인해 대기 구조를 수정했다. 수정 후 목적지 성공 및 경유지 실패 실험에서
  모두 초기 상태 복원을 재조회로 확인했다. 연결 테스트는 명시적 실행 인자로만 활성화한다. 실차/주행 중 검증은 아니다.

앱 매니페스트에 `pleos.car.permission.NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`, `NAVI_CUSTOM_MAP`을 선언한다. 로컬 API
Reference에서 세 권한은 normal로 명시하며, 스마트폰 위치 권한을 추가하지 않는다.

`PleosNaviClientTest`는 Fake SDK 경계로 연결 게이트, 요청 데이터 보존, 조회/Trip/경로/오류 콜백, 중복 응답, 초기화 실패, 동기 예외, 지연 연결
결과, 리스너 제거 및 해제를 검증한다. 실제 내비 서비스 동작을 검증하는 테스트는 아니다. 실행 명령은 [TESTING.md](TESTING.md)를 따른다.

구현 근거:
로컬 [초기화·해제](../pleos_sdk_guide/앱%20개발·연동%20가이드/SDK%20사용%20가이드/NaviHelper%20SDK/Programming%20Guide/Initialization.md), [권한](../pleos_sdk_guide/앱%20개발·연동%20가이드/SDK%20사용%20가이드/NaviHelper%20SDK/Programming%20Guide/Declaring%20permissions.md), [API Reference](../pleos_sdk_guide/앱%20개발·연동%20가이드/SDK%20사용%20가이드/NaviHelper%20SDK/API%20Reference/ai.pleos.playground.navi.helper/NaviHelper/NaviHelper.md).

## 고정 주유소 UI 연결 (2026-11-10)

`LiveJourneyRepository`는 StationRepository/JourneyRepository를 함께 구현한다. 앱 루트 ViewModel이 세션을 초기화하고,
포그라운드 복귀 시 상태를 재확인하며 종료 시 해제한다. 재구성·화면 이동은 이미 제출한 SDK 작업을 취소하거나 중복 제출하지 않는다.
홈의 목적지 유무 스위치·고정 지역명·임의 ETA는 실제 화면에서 제거했다. 목적지 교체는 기존 목적지와 경유지 제거를 확인받는다.
SDK의 실제 반영을 확인한 뒤 완료하고, 결과 불명은 재제출 없이 명시적 상태 재확인으로만 복구한다. 완료 화면의 내비 열기는 별도 요청이다.

`VerifiedStations.kt`는 내비 공식 검색의 DRIVING guidePoint를 입력으로 사용한다. 명시적 sub-ID `0`을 이 8곳에서
검증했으며 다른 POI의 null sub-ID를 0으로 보정하는 정책은 아니다. Trip 응답 좌표와 목적지 조회 좌표는 별도로 저장한다.
아래 거리·시간은 고정 내비 위치 `(37.4127212,127.094681)`에서의 측정 기록이며 런타임 계산에는 새 Trip 응답을 사용한다.

| 주유소                           | 내비 POI ID            | 측정 편도 m / 초 |
|----------------------------------|------------------------|------------------|
| HD현대오일뱅크 세종주유소        | `36khp23245r6cm5nqhe3` | 1718 / 370       |
| S-OIL 셀프 분당제일에너지주유소  | `36khp274nejqcm5nqhe3` | 1962 / 393       |
| GS칼텍스 셀프 판교서일주유소     | `36khp265rmp5cm5nqhe3` | 2078 / 467       |
| HD현대오일뱅크 셀프 동판교주유소 | `36khp24k4r58cm5nqhe3` | 2488 / 523       |
| SK에너지 스타팅광동주유소        | `36khde5jsnq3cm5nqhe3` | 2865 / 589       |
| SK에너지 판교운중주유소          | `36khde5j4sc5cm5nqhe3` | 3202 / 706       |
| SK에너지 셀프 한강에너지주유소   | `36khp2d3qjj5cm5nqhe3` | 4795 / 625       |
| SK에너지 셀프 퍼스트주유소       | `36khp4qgm7fscm5nqhe3` | 11781 / 1756     |

8곳 모두 원시 SDK 측정과 NavigationRepository의 ID·좌표·이벤트·안내 상태 검증을 통과했고, 각 목적지 설정 후
목적지·경유지 없는 SAFE_GUIDING 초기 상태 복원을 확인했다. 지도/내비 버전·출발 위치 변경 시 좌표 재검증이 필요하다.
가격은 1590~1740원/L의 고정 mock이며 화면에 가격 출처 배지를 추가하지 않는다. 오피넷 연결과 일반 POI 매핑은 후속 작업이다.
통행료는 미조회 (null)로 계산에서 제외하며 UI에 제외를 표시한다. 경유 추가는 목적지가 있고 경유지가 2개 미만이면 활성화한다. 왕복은 비활성 상태다.

최초 출발 위치 50m 이내·동일 경로 옵션이면 최초 편도 Trip과 검색 시점을 재사용한다. route ID/index·기존 목적지·경유 순서·안내 상태의 변경이나 목적지 제거는
재검색을 막지 않는다.
출발 위치가 재사용 범위를 벗어나거나 경로 옵션이 달라진 동안만 캐시 재사용을 차단한다. 원래 조건으로 돌아오면 재사용할 수 있다. 목적지 설정 성공만으로 캐시를 만료하지 않는다.
결과 화면도 최신 목적지로 갱신하며, 대체 확인 후 제출 직전 목적지·경유 순서·옵션·안내 상태가 달라졌으면 재확인을 요구한다. 현재 세션에서 같은 POI의 Trip을 재요청하지
않는다.
SDK 조회 불명/오류 역시 자동 재시도하지 않는다. 프로세스 재시작은 서버 요청 취소의 증명이 아니다.
검색과 목적지 제출 직전 CarUxRestrictions가 정차 UI를 허용하는지 확인하며, 규제 상태를 확인할 수 없어도 제출을 차단한다.

### 경유 추가 정책 변경

경유 추가와 목적지 설정 모두 비교 값은 현재 위치 → 주유소 기준이다. 추가 거리·ETA 변화나 최종 목적지까지 전체 소요시간으로 표시하지 않는다. 경유 좌표 (0,0) 예외는
사용자 요청으로 적용한 정책이며 좌표 누락의 SDK 원인 해결을 뜻하지 않는다.
