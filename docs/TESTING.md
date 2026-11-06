# 검증

프로젝트 루트의 PowerShell에서 호스트 Gradle 캐시·데몬을 사용한다.

```powershell
$env:GRADLE_USER_HOME = 'C:\Users\ddddd\.gradle'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :core:model:test :core:data:test :feature:comparison:testDebugUnitTest :feature:settings:testDebugUnitTest lintDebug --console=plain
```

- `assembleDebug`: 앱과 연결된 모든 Android/Kotlin 모듈의 빌드·패키징을 확인한다.
- `testDebugUnitTest`: Android 모듈의 로컬 JVM 테스트를 실행한다. 현재 `feature:comparison`은 카운터 초기값, 증가·감소·초기화 흐름, 0 하한, Koin ViewModel 등록 및 인스턴스 분리를 확인하며, `app`은 기본 산술 예제만 포함한다. `feature:settings`에는 아직 테스트가 없다.
- `:core:model:test`, `:core:data:test`: 순수 Kotlin/JVM 모듈의 테스트를 실행한다. 현재 두 모듈에는 테스트가 없어 `NO-SOURCE`로 표시된다.
- `lintDebug`: Android 모듈의 정적 분석을 수행한다.

## 로컬 단위 테스트 구성

버전은 `gradle/libs.versions.toml`에서 관리하고 각 모듈의 `testImplementation`으로 선언한다.

| 모듈 | 테스트 의존성 |
| --- | --- |
| `app`, `core:model` | JUnit4 `4.13.2` |
| `core:data`, `feature:comparison`, `feature:settings` | JUnit4 `4.13.2`, Coroutines Test `1.11.0`, Turbine `1.2.1` |

- **JUnit4**: `org.junit.Test`와 assertion으로 계산 결과·경계값·상태를 검증한다. 기존 테스트와 AndroidJUnit4 구성을 유지한다.
- **Coroutines Test**: `kotlinx-coroutines-test` 버전은 `kotlinx-coroutines-core`와 같은 `coroutines` 버전 키를 사용한다. suspend 함수와 비동기 로직은 `runTest`로 실행하고, 실행 순서와 가상 시간은 `runCurrent`, `advanceTimeBy`, `advanceUntilIdle` 등으로 제어한다. 테스트 Dispatcher는 같은 `testScheduler`를 공유하도록 구성한다.
- **Main Dispatcher**: 로컬 JVM에서 `viewModelScope` 등 `Dispatchers.Main`을 사용하는 테스트는 ViewModel 생성 전에 `Dispatchers.setMain(testDispatcher)`으로 대체하고 종료 시 `Dispatchers.resetMain()`으로 복구한다. `runTest`만으로 Main Dispatcher가 자동 대체되지는 않는다. 반복되는 경우 JUnit4 `MainDispatcherRule`로 공통화한다.
- **Turbine**: Flow의 방출 값·오류·완료를 검증할 때 `test { awaitItem(); ... }`를 사용한다. StateFlow의 현재 상태만 확인하면 되는 경우 `.value`를 직접 검증한다. StateFlow는 빠른 변경의 중간 값을 합칠 수 있으므로 모든 중간 값이 수집된다고 가정하지 않는다. 현재 카운터처럼 동기적으로 상태를 변경하는 테스트는 기존 검증 방식을 유지한다.
- 저장소 등 외부 의존성은 필요한 동작만 구현한 Fake로 대체한다. JUnit5와 MockK는 현재 도입하지 않는다.

## 화면 검증

화면을 검증할 때는 앱을 실행해 초기값 0, 증가·감소·초기화, 0에서 감소 버튼 비활성화, 화면 회전 후 값 유지를 확인한다. 프로세스 종료 후 값 복원은 현재 지원 범위가 아니다. 단위 테스트는 실제 Compose 클릭이나 Application의 기동을 검증하지 않는다.
