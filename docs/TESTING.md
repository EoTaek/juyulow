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

`AppNavigationTest`는 설정 저장 중 뒤로가기·팝업 닫기 요청이 entry를 유지하고, 저장 상태가 해제되면 닫히는지 검증한다. 저장 상태는 닫기 요청 시점에 읽어 재구성 이전의 요청도 차단한다.

`PleosNaviClientTest`는 Fake SDK 경계로 연결 전 요청 차단, SDK 인자·조회/Trip/경로/오류 콜백 전달, 초기화 실패·동기 예외, 지연 연결 결과와
중복 해제, 동일 리스너 제거 후 SDK 해제를 검증한다. 실제 내비 서비스·권한 허용·경로 설정은 검증하지 않는다. 어댑터
계약은 [NAVIHELPER.md](NAVIHELPER.md)를 참조한다.

`PleosFusedLocationClientTest`, `PleosVehicleFuelClientTest`는 Fake SDK로 미지원·초기화 실패·capability·원본
단위/nullable 값·오류 전달·중복 구독 차단·동일 리스너 해제·정리 실패 후 SDK 해제·지연 콜백 무시를 검증한다. 실제 서비스 연결과 런타임 권한 허용/회수는 검증하지
않는다. 계약은 [LOCATION_VEHICLE.md](LOCATION_VEHICLE.md)를 참조한다.

버전은 `gradle/libs.versions.toml`에서 관리하고 각 모듈의 `testImplementation`으로 선언한다.

| 모듈 | 테스트 의존성 |
| --- | --- |
| `app`, `core:model` | JUnit4 `4.13.2` |
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
  검색, Activity 재생성 후 선택 유지, 설정 취소·적용·다시 열기를 검증한다. 테스트 전의 주유 조건은 종료 시 복원한다.
- `PrototypeLayoutTest`: 논리 앱 영역 1700 × 1184dp와 840 × 1184dp를 두 테마로 렌더링해 동일 너비 좌우 배치·마지막 항목 스크롤·버튼
  비겹침·텍스트 overflow를 검증한다. Pleos 호스트 창에 들어가도록 테스트에서만 Density를 0.5로 설정한다. 이는 실제 Pleos 폼 팩터 테스트를 대체하지
  않는다.
- 레이아웃 테스트 PNG는 디바이스 대상 앱 내부 파일 영역의 `prototype-screenshots`에 저장한다. 디버그 앱의 `run-as`로 읽을 수 있으며 실제 앱은
  테스트용 Density를 사용하지 않는다.
- 레이아웃 테스트 전용 `PrototypeTestActivity`는 debug 빌드에만 포함하며 런처 진입점은 없다. Pleos의 자동 창 재배치로 테스트가 주입한
  composition이 소실되지 않도록 구성 변경을 처리한다. 실제 MainActivity의 재생성·상태 유지는 별도 흐름 테스트가 검증한다.
- mock 검색은 850ms 지연 후 3~8곳을 생성한다. 앱에 실패 시나리오 선택 메뉴는 없으며, 검색·경로·저장 실패는 로컬 단위 테스트의 Fake로 주입한다.
- 정차 시연용이다. SDK·네트워크 호출, 실제 내비 설정, 주행 규제 연동과 주행 중 사용 적합성은 검증 범위에 포함하지 않는다. 과거 PoC
  결과는 [실험 보고서](poc/POC_TRIP_REPORT.md)에 보존한다.
