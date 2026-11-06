# PROJECT.md

## 프로젝트 개요

**돌아갈 만큼 싼가? — 저렴한 주유소로 우회하는 것이 실제로 이득인지 계산한다.**

현재 경로 주변의 주유소 가격뿐 아니라 **주유소까지 우회하는 추가 거리·연료비·시간**을 함께 고려하여, 가까운 곳에서 주유할 때와 비교한 **예상 실질 절약액**을 안내하는 차량용 주유 의사결정 앱이다.

현대자동차그룹 차량용 앱 개발 공모전 프로젝트로, **Pleos Connect 차량 환경에서 사용하는 차량용 애플리케이션**을 개발한다. 일반 스마트폰 앱을 전제로 설계하지 않으며, 차량 내 화면과 조작 환경, 주행 안전 및 주행 규제를 고려한다. 차량 정보와 내비게이션 연동은 현대의 차량용 API 시스템인 Pleos SDK를 기준으로 검토한다.

- 상태: 아이디어 기획 초안. 아래 기능과 연동은 개발 목표이며, 구현 완료를 의미하지 않는다.
- 원안: 기존 추천 20번, 「Idea 6 돌아갈 만큼 싼가? — 주유소 실질 절약액 비교」
- 원안 작성일: 2026-11-05
- 기획 출처: [ChatGPT Space 원문](https://chatgpt.com/space/page_b0bb2fd2e42c819188ddb1c82f55ccdb)

## 현재 구현 및 모듈 구조

현재 실행 화면은 MVVM 구조를 확인하기 위한 카운터 예제다. 0부터 증가·감소·초기화를 지원하며 음수로 내려가지 않는다. 화면 재생성 시 ViewModel이 값을 유지하지만, 프로세스 종료 후의 복원이나 영구 저장은 구현하지 않는다. 아래 주유소 비교·차량 설정·외부 API 연동은 아직 기획 단계다.

| 모듈 | 역할 |
| --- | --- |
| `app` | 유일한 application 모듈. Application에서 Koin 시작, Activity에서 테마와 feature 진입점 연결 |
| `core:design` | 공통 Compose 테마·색상·타이포그래피·그림자 토큰 |
| `core:model` | 공통 데이터 모델을 둘 순수 Kotlin/JVM 모듈. 현재 껍데기만 구성 |
| `core:data` | 저장소·데이터 소스·외부 연동을 둘 순수 Kotlin/JVM 모듈. 현재 껍데기와 의존성만 구성 |
| `feature:comparison` | 주유소 비교 feature. 현재 임시 카운터와 ViewModel, UI state, sealed action, Koin 모듈 포함 |
| `feature:settings` | 연비·예상 주유량 설정 feature. 현재 Gradle 모듈 껍데기만 구성 |

`core:design`은 로컬 Pleos 디자인 가이드의 색상·타이포그래피·그림자 토큰을 `JUYULOWTheme.colors`, `.typography`, `.shadows`로 제공한다. 기존 Material 컴포넌트는 매핑된 `MaterialTheme.colorScheme`과 `.typography`를 사용한다. 출처·매핑·서체 및 원문 해석은 [DESIGN.md](DESIGN.md), 아이콘 제작·사용 지침은 [ICONOGRAPHY.md](ICONOGRAPHY.md)를 참고한다.

의존 방향은 `app → feature/core:design`, `feature → core`, `core:data → core:model`이다. core는 feature나 app에 의존하지 않으며 feature 간 직접 의존도 두지 않는다. 내비 연동은 별도 feature 대신 향후 비교 흐름의 action과 data 연동으로 시작한다.

카운터의 데이터 흐름은 `CounterScreen → CounterAction → CounterViewModel → StateFlow<CounterUiState> → ComparisonRoute → CounterScreen`이다. Route에서 `koinViewModel()`로 ViewModel을 주입하고 `collectAsStateWithLifecycle()`로 상태를 구독한다. Screen은 상태와 action 콜백만 받는다. 카운터 상태는 feature 내부에 두며, 예제를 위해 공통 모델이나 저장소를 만들지 않는다.

의존성 버전은 `gradle/libs.versions.toml`에서 관리한다. Koin BOM과 Android/Compose 연동, Lifecycle, Coroutines를 사용한다. Koin은 constructor DSL로 등록하며, Koin annotation/KSP/컴파일러 플러그인은 추가하지 않는다.

화면 전환·설정 저장·외부 API 연동을 위한 다음 의존성을 등록했다. 현재는 의존성과 플러그인만 구성한 상태이며, 화면 전환·반응형 배치·설정 영구 저장·HTTP 클라이언트 및 API 호출은 아직 구현하지 않았다.

| 모듈 | 의존성 | 용도 |
| --- | --- | --- |
| `app` | Navigation 3 runtime/UI `1.2.1`, Lifecycle ViewModel Navigation 3 `2.11.0` | 앱 내부 화면 전환 및 화면별 ViewModel 수명 관리 |
| `app` | Material 3 Adaptive, Adaptive Layout, Adaptive Navigation 3 `1.3.0` | 창 크기에 따른 배치와 Navigation 3 연동 |
| `app` | Kotlin Serialization core `1.12.0` | 화면 경로 직렬화 |
| `core:data` | Preferences DataStore core `1.3.0` | 연비·주유량 등 소규모 설정 저장 |
| `core:data` | Retrofit 및 Kotlin Serialization converter `3.0.0`, Kotlin Serialization JSON `1.12.0` | 외부 HTTP API와 JSON 데이터 처리 |

Serialization 플러그인은 `app`과 `core:data`에 Kotlin과 같은 버전으로 적용한다. `core:data`는 순수 Kotlin/JVM 구조를 유지하기 위해 Android 의존성이 없는 `datastore-preferences-core`를 사용하며, 저장 경로 등 Android 환경 정보는 실제 저장소 구현 시 외부에서 제공한다. 라이브러리 사용법은 [Navigation 3](https://developer.android.com/guide/navigation/navigation-3/get-started), [Material 3 Adaptive](https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive), [DataStore](https://developer.android.com/topic/libraries/architecture/datastore), [Retrofit](https://github.com/square/retrofit), [Kotlin Serialization](https://github.com/Kotlin/kotlinx.serialization)을 참고한다.

`app`에는 로컬 SDK 가이드 기준으로 Pleos Vehicle `2.3.2`, NaviHelper `3.0.5`, FusedLocation `1.1.3` 의존성을 등록했다. `settings.gradle.kts`의 Pleos Maven 저장소에서 `ai.pleos.playground` 그룹을 받는다. 현재는 의존성만 추가한 상태이며, 기능별 권한 선언과 런타임 권한 요청, SDK 초기화·해제 및 API 호출은 실제 연동 구현 시 추가한다.

## 기획 배경과 해결할 문제

리터당 가격이 조금 싼 주유소를 찾더라도, 그곳까지 돌아가는 데 연료와 시간이 추가로 든다. 가격 정렬만으로는 이번 주유에서 실제로 절약되는 금액을 바로 판단하기 어렵다. 이 앱은 사용자가 **‘얼마나 싼가’보다 ‘지금 돌아갈 가치가 있는가’**를 판단하도록 돕는다.

## 주요 기능 및 차량 특화 요소

- **후보 주유소 비교:** 현재 이동 방향과 목적지를 고려해 주유소 후보를 선택한다.
- **실질 절약액 계산:** 동일한 주유량을 가정하고 가격 차이와 추가 주행 연료비를 함께 계산한다.
- **시간 비용 분리:** 추가 이동 시간은 원화 가치로 단정하지 않고 별도 표시한다.
- **연비·주유량 설정:** 차량에서 연비 정보를 제공하지 않으면 사용자 기준 연비와 예상 주유량을 입력한다.
- **내비 연동:** 사용자가 선택한 주유소를 경유지로 설정한다.

## 주요 사용 시나리오

1. 사용자가 목적지로 이동하는 중 주유소 비교를 실행한다.
2. 앱은 현재 경로 기준 주유소 후보의 가격·예상 추가 이동거리·시간을 계산한다.
3. 사용자는 예상 주유량을 확인하고 결과를 비교한다.
4. ‘예상 절약액이 작아 가까운 곳 선택’ 또는 ‘절약액이 커서 우회 선택’ 중 하나를 결정한다.
5. 선택된 주유소를 차량 내비에 반영한다.

## 계산식 및 예시

| 항목 | 계산식 |
| --- | --- |
| 가격 차이 절감액 | (기준 주유소의 리터당 가격 − 후보 주유소의 리터당 가격) × 예상 주유량(L) |
| 우회 연료비 | 기준 경로 대비 추가 거리(km) ÷ 예상 연비(km/L) × 연료 단가(원/L) |
| 예상 순절약액 | 가격 차이 절감액 − 우회 연료비 |

추가 이동 시간은 순절약액에 금액으로 합산하지 않고 별도로 표시한다.

설명용 예시:

- 가격 차이 50원/L, 30L 주유 → 가격 차이 절감액 **1,500원**.
- 추가 거리 3km, 연비 10km/L, 연료 단가 3,400원/L → 우회 연료비 **1,020원**.
- 예상 순절약액: 1,500원 − 1,020원 = **480원**.

**예시 숫자는 실제 주유소 시세가 아니다.**

## SDK·외부 데이터와 MVP 개발 계획

- **Pleos 개발 문서:** [로컬 Pleos SDK 가이드 목차](../pleos_sdk_guide/README.md)를 진입점으로 사용한다. 앱 설계 원칙·디자인·주행 규제, 개발 환경 구성·테스트 가이드와 각 SDK의 Programming Guide·API Reference를 작업에 맞게 참조한다. SDK 연동 시 권한, 초기화·해제, API 호환성 및 가용성을 확인한다. 아래 연동은 개발 계획이며 현재 구현 완료를 의미하지 않는다.
- **Pleos/NaviHelper:** 현재 위치·목적지 확인, 사용자가 선택한 주유소를 경유지로 요청하는 기능을 연동한다.
- **외부 데이터:** 오피넷의 주유소 가격 정보를 사용하며, 정보 갱신 시각과 제공 범위를 함께 표시한다.
- **외부 경로 계산:** 지도 길찾기 API로 기준 경로와 주유소 경유 경로의 거리·시간 차이를 구한다. NaviHelper가 여러 임의 후보 경로의 모든 비교 계산을 직접 제공한다고 가정하지 않는다.
- **연비:** 차량에서 확인 가능한 범위가 제한되면 사용자 설정값을 사용한다.
- **초기 시연:** 동일 목적지에 대해 주유소 후보 2~3개를 비교하고, 추가거리·시간·순절약액이 다른 화면을 보여준 뒤 한 곳을 내비에 연동한다.

## 차별점·위험·검증 과제

- **차별점:** 현재 주행 경로에서의 예상 순이익을 비교해 주유 전 우회 의사결정을 돕는다.
- **정확성:** 연비, 실시간 교통, 실제 주유량, 유가 변동에 따라 결과가 달라지므로 추정치로 표시한다.
- **외부 의존성:** 오피넷 데이터 이용 조건과 경로 API 비용·쿼터·상업적 사용 가능 여부를 사전에 확인해야 한다.
- **기존 사례와의 관계:** 차계부와 자동차 비용 관리라는 넓은 분야가 인접하므로 ‘주유 전 우회 의사결정’이라는 차별점을 강조한다.
- **안전:** 주행 중 긴 목록 비교나 금액 입력을 요구하지 않도록 한다.

## 참고자료

- [오피넷 – 오픈 API 안내](https://www.opinet.co.kr/user/custapi/custApiInfo.do)
- [Pleos NaviHelper – 경유지 설정](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/waypoint-settings)
