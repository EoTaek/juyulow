# 검증

프로젝트 루트의 PowerShell에서 호스트 Gradle 캐시·데몬을 사용한다.

```powershell
$env:GRADLE_USER_HOME = 'C:\Users\ddddd\.gradle'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :core:model:test :core:data:test :feature:main:testDebugUnitTest :feature:settings:testDebugUnitTest lintDebug --console=plain
```

- `assembleDebug`: 앱과 연결된 모든 Android/Kotlin 모듈의 빌드·패키징을 확인한다.
- `testDebugUnitTest`: app의 백스택 동작, feature:main의 검색·선택·정렬·스냅샷·경로 설정, feature:settings의 초안·적용·저장 실패
  후 재시도를 검증한다.
- `:core:model:test`, `:core:data:test`: 세 이동 방식의 거리·시간·비용·경계값, mock 데이터 생성·목적지 반영, 실제 Preferences
  DataStore 파일의 저장·재로딩을 검증한다.
- `lintDebug`: Android 모듈의 정적 분석을 수행한다.

현재 Lifecycle 의존성의 custom lint와 Kotlin 분석 API 호환성 문제로 `ObsoleteLintCustomCheck` 경고가 발생하며
`UnsafeLifecycleWhenUsage`, `RepeatOnLifecycleWrongUsage` 두 검사는 제외된다. 빌드와 나머지 lint는 실행되지만 이 두 항목의 정적
검증 완료를 의미하지 않는다. Flow 수집은 `collectAsStateWithLifecycle`을 사용하며 화면 전환·재생성은 연결 테스트로 별도 확인한다.

## 로컬 단위 테스트 구성

`PleosNavigationRepositoryTest`는 실제 저장소의 조회·미수신 잠금·목적지 없음 구분·확정 전 문맥 변경·목적지 대체·
경유 순서·2개 제한·중복/미검증 후보 차단·Trip 타임아웃 잠금·동시 요청 거부·해제를 Fake로 검증한다.
`PleosNaviClientTest`는 requestRouteWithWaypoints/cancelRoute의 연결 게이트와 인자 보존도 검증한다.

`NavigationRepositoryIntegrationTest`는 기본 실행에서 건너뛴다. Pleos 기기 연결 후 아래처럼 상태 조회를 활성화한다.

```powershell
$env:GRADLE_USER_HOME = 'C:\Users\ddddd\.gradle'
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=eotaek.juyulow.NavigationRepositoryIntegrationTest' '-Pandroid.testInstrumentationRunnerArguments.navigationProbe=true' --console=plain
```

경로 변경 검증은 별도 인자 `navigationMutationProbe=true`와 `latitude`, `longitude`, `poiName`, `poiId`, `poiSubId`,
`responseLatitude`, `responseLongitude`, `routeResponseLatitude`, `routeResponseLongitude`, `routeMode=destination` 또는 `waypoint`가 모두 필요하다. 각각
`-Pandroid.testInstrumentationRunnerArguments.<이름>=<값>`으로 전달한다. 검증된 내비 POI와 실측한 도로 스냅 좌표를 사용하며
일반 주유소 좌표나 null sub-ID의 자동 변환을 가정하지 않는다. 두 routeMode를 각각 실행한다.
주행 규제 비활성 상태·기존 경유지 1개 이하·원경로 복원 가능 여부를 확인한 후 Trip과 경로 변경을 실행하고 finally에서
기존 경로를 복원·재조회한다. 복원이 실패하면 테스트도 실패하며 NavigationProbe 로그로 잔여 상태를 확인한다.
앱 데이터 삭제나 uninstall은 수행하지 않는다.

waypoint 모드에서 초기 목적지가 없으면 실측된 소설한남 fixture로 목적지를 먼저 설정하고, 종료 시 원래 목적지 없는 상태로 복원한다.
2026-11-10 에뮬레이터에서 상태 조회 및 소설한남 목적지 설정/복원은 통과했다. 더현대서울 경유 추가는 조회 좌표 `(0,0)`으로
성공 확인이 차단되어 테스트가 실패했으며 원상복구는 확인했다. 버전·실측 좌표와 제한은 [NAVIHELPER.md](NAVIHELPER.md)에 기록했다.
회귀 테스트는 이벤트 이후 지연 반영, 불일치 시간 초과, Trip/경로 좌표 분리, null sub-ID 목적지 보존 및 경유지 좌표 누락도 검증한다.

`AppNavigationTest`는 설정 저장 중 뒤로가기·팝업 닫기 요청이 entry를 유지하고, 저장 상태가 해제되면 닫히는지 검증한다. 저장 상태는 닫기 요청 시점에 읽어 재구성 이전의 요청도 차단한다.

`PleosNaviClientTest`는 Fake SDK 경계로 연결 전 요청 차단, SDK 인자·조회/Trip/경로/오류 콜백 전달, 초기화 실패·동기 예외, 지연 연결 결과와
중복 해제, 동일 리스너 제거 후 SDK 해제를 검증한다. 실제 내비 서비스·권한 허용·경로 설정은 검증하지 않는다. 어댑터
계약은 [NAVIHELPER.md](NAVIHELPER.md)를 참조한다.

`PleosFusedLocationClientTest`, `PleosVehicleFuelClientTest`는 Fake SDK로 미지원·초기화 실패·capability·원본
단위/nullable 값·오류 전달·중복 구독 차단·동일 리스너 해제·정리 실패 후 SDK 해제·지연 콜백 무시를 검증한다. 실제 서비스 연결과 런타임 권한 허용/회수는 검증하지
않는다. 계약은 [LOCATION_VEHICLE.md](LOCATION_VEHICLE.md)를 참조한다.

`VehicleStartupTest`·`PleosStartupSourceTest`·`AaosStartupSourceTest`는 부분 성공·기본값 대체·권한 허용/거부·중복 시작
방지,
5초 제한·지연 콜백 이후 해제·미지원 capability 및 주행 규제 확인을 Fake로 검증한다. `VehicleDefaultsRepositoryTest`와
`VehicleInfoTest`는 기존 설정 호환·기본값 저장/재로딩·단위 변환·비정상 값을 검증한다. release 진단 no-op의 컴파일·패키징도
`:app:assembleRelease`로 확인한다.

`RefuelConditionsTest`는 소수값과 수동 범위 밖 자동값, L/100km 변환, 0L의 이동 비용,
잔량 초과·조회 실패 시 % 대체와 수동 fallback, 누적/현재 연비 우선순위를 검증한다.
`MainViewModelsTest`는 검색 도중 도착한 차량값과 수동 설정 변경이 결과 재검색·완료의 스냅샷을 바꾸지 않고 새 홈 검색에 반영되는지,
0L 검색 허용과 복수·미지 에너지원 표시를 검증한다. `SettingsViewModelTest`는 팝업 편집 중 자동 성공 시 수동 초안 보존과 SDK 값 저장 방지를 확인한다.
`VehicleStartupTest`는 잔량 mL 실패 후 % 대체가 성공해도 원래 실패 진단이 유지되는지 검증한다.

버전은 `gradle/libs.versions.toml`에서 관리하고 각 모듈의 `testImplementation`으로 선언한다.

| 모듈                                            | 테스트 의존성                                              |
|-------------------------------------------------|------------------------------------------------------------|
| `core:model`                                    | JUnit4 `4.13.2`                                            |
| `app`                                           | JUnit4 `4.13.2`, Coroutines Test `1.11.0`                  |
| `core:data`, `feature:main`, `feature:settings` | JUnit4 `4.13.2`, Coroutines Test `1.11.0`, Turbine `1.2.1` |

- **JUnit4**: `org.junit.Test`와 assertion으로 계산 결과·경계값·상태를 검증한다. 기존 테스트와 AndroidJUnit4 구성을 유지한다.
- **Coroutines Test**: `kotlinx-coroutines-test` 버전은 `kotlinx-coroutines-core`와 같은 `coroutines` 버전 키를 사용한다. suspend 함수와 비동기 로직은 `runTest`로 실행하고, 실행 순서와 가상 시간은 `runCurrent`, `advanceTimeBy`, `advanceUntilIdle` 등으로 제어한다. 테스트 Dispatcher는 같은 `testScheduler`를 공유하도록 구성한다.
- **Main Dispatcher**: 로컬 JVM에서 `viewModelScope` 등 `Dispatchers.Main`을 사용하는 테스트는 ViewModel 생성 전에 `Dispatchers.setMain(testDispatcher)`으로 대체하고 종료 시 `Dispatchers.resetMain()`으로 복구한다. `runTest`만으로 Main Dispatcher가 자동 대체되지는 않는다. 반복되는 경우 JUnit4 `MainDispatcherRule`로 공통화한다.
- **Turbine**: Flow의 방출 값·오류·완료를 검증할 때 `test { awaitItem(); ... }`를 사용한다. StateFlow의 현재 상태만 확인하면 되는 경우 `.value`를 직접 검증한다. StateFlow는 빠른 변경의 중간 값을 합칠 수 있으므로 모든 중간 값이 수집된다고 가정하지 않는다.
- 저장소 등 외부 의존성은 필요한 동작만 구현한 Fake로 대체한다. JUnit5와 MockK는 현재 도입하지 않는다.

## 화면 검증

각 화면에 가로 1700 × 1184dp와 분할 창 840 × 1184dp, 라이트·다크 프리뷰를 제공한다. 결과 화면은 두 크기 모두 좌우 2열이며, 목록과 선택 상세는 독립
스크롤한다. 목록의 마지막 항목과 하단 경로 설정 버튼이 겹치지 않는지 확인한다.

로컬 Pleos 에뮬레이터를 실행한 뒤 연결 테스트를 수행한다. `gradle.properties`에서
`android.injected.androidTest.leaveApksInstalledAfterRun=true`를 지정하여 테스트 후 AGP의 APK 자동 제거를 막고 로컬 설정을
유지한다. 호환되지 않는 서명·설치가 있으면 데이터를 지우지 않고 실패 원인을 확인한다.

```powershell
$env:GRADLE_USER_HOME = 'C:\Users\ddddd\.gradle'
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

- `PrototypeFlowTest`: 실제 Koin·Navigation 3·DataStore를 사용하는 홈 → 검색 → 세 이동 방식의 경로 설정·완료·뒤로가기, 목적지 없는
  검색, Activity 재생성 후 선택 유지, 주유 시간의 설정 취소·적용·다시 열기를 검증한다. 테스트 전의 주유 조건은 종료 시 복원한다.
- `PrototypeLayoutTest`: 논리 앱 영역 1700 × 1184dp와 840 × 1184dp를 두 테마로 렌더링해 동일 너비 좌우 배치·마지막 항목 스크롤·버튼
  비겹침·텍스트 overflow를 검증한다. Pleos 호스트 창에 들어가도록 테스트에서만 Density를 0.5로 설정한다. 이는 실제 Pleos 폼 팩터 테스트를 대체하지
  않는다.
- `VehicleConditionsLayoutTest`: 같은 두 크기·두 테마에서 홈·설정·완료의 차량 정보와 소수 조건을 확인한다.
  자동 연비의 스텝퍼 제거와 주유량 편집 버튼 활성화, 상세 조회 정보·적용 버튼의 스크롤 접근, 텍스트 overflow를 검증한다.
  `PrototypeLayoutTest`도 전기 에너지원·소수 주유량과 연비를 사용해 결과 화면을 검증한다.
- `VehicleStartupIntegrationTest`: 실제 startup repository의 항목별 완료와 출처를 확인한다. 에너지 권한이 미허용이면 잔여량은 대기할 수
  있다.
  `VehicleStartupTest` logcat에 공급된 원본 단위·값·상태를 남긴다. 권한을 강제 허용/회수하거나 앱 데이터를 삭제하지 않는다.
- 레이아웃 테스트 PNG는 디바이스 대상 앱 내부 파일 영역의 `prototype-screenshots`에 저장한다. 디버그 앱의 `run-as`로 읽을 수 있으며 실제 앱은
  테스트용 Density를 사용하지 않는다.
- 레이아웃 테스트 전용 `PrototypeTestActivity`는 debug 빌드에만 포함하며 런처 진입점은 없다. Pleos의 자동 창 재배치로 테스트가 주입한
  composition이 소실되지 않도록 구성 변경을 처리한다. 실제 MainActivity의 재생성·상태 유지는 별도 흐름 테스트가 검증한다.
- mock 검색은 850ms 지연 후 3~8곳을 생성한다. 앱에 실패 시나리오 선택 메뉴는 없으며, 검색·경로·저장 실패는 로컬 단위 테스트의 Fake로 주입한다.
- 정차 시연용이다. startup 차량 조회 외 SDK·네트워크 호출, 실제 내비 설정과 앱 전체의 주행 중 사용 적합성은 검증 범위에 포함하지 않는다. 과거 PoC
  결과는 [실험 보고서](poc/POC_TRIP_REPORT.md)에 보존한다.

### Startup 실기 확인 기록 (2026-11-09)

Pleos Connect v2.1.0 / Android 14 에뮬레이터에서 연결 테스트와 `VehicleStartupTest` logcat으로 확인했다.
AAOS 탱크 용량과 잔여량은 각각 15,000mL, 유종 목록은 `[10]`(ELECTRIC)으로 반환됐다.
Pleos 누적/현재 연비·잔량 %는 capability에서 `ERROR_NOT_SUPPORTED_PROPERTY`로 실패했다.
두 연비는 DataStore의 기본 12km/L로 대체되고 잔량 %는 미설정이었으며, 실패 3항목은 debug `VehicleStartup` 로그에 기록됐다.
이 값은 에뮬레이터 시뮬레이션이며 실차 검증이 아니다. 해당 사용자에는 에너지 권한이 이미 허용되어 새 권한 대화상자와 실제 권한 거부/회수는
이 실행에서 확인하지 않았다. 권한 허용/거부 및 지연 응답은 Fake 테스트로 검증한다.

### 차량값 화면·계산 연결 확인 (2026-11-09)

동일 Pleos 에뮬레이터에서 연결 테스트 20개가 통과했다. 홈과 설정에서 에너지원 `전기`, 비교 정책 `휘발유 기준`,
예상 가득 주유량 `0 L (자동 · 잔량 L)`, 연비 `12 km/L (수동)`을 확인했다.
당시 설정에서는 자동 주유량이 읽기 전용이었으며 연비·주유 시간은 수동 편집이 가능했고, 용량·잔량은 각각 15L로 표시됐다.
Pleos 미지원 3항목은 debug logcat에 유지됐다. % 대체와 소수 계산은 Fake로 검증하며,
전기차도 휘발유 비교를 허용한다. 충전·전비 계산은 후속 과제다.

주유량 수정 정책: 자동값에서 수정한 소수 주유량이 늦은 fetch보다 우선하고 적용 전에는 저장소에 영향을 주지 않는지 SettingsViewModelTest로 확인한다.
PreferencesSettingsRepositoryTest는 주유량 override가 같은 실행에서는 유지되고 새 저장소에서는 초기화되는지 검증한다.
