# FusedLocation・Vehicle Fuel 어댑터

`app/location`의 `FusedLocationClient` → `PleosFusedLocationClient`, `app/vehicle`의
`VehicleFuelClient` → `PleosVehicleFuelClient`가 실제 SDK를 감싼다. Android SDK 객체 생성은 각각
`AndroidFusedLocationApi`, `AndroidVehicleFuelApi`에 둔다. 화면의 mock 저장소는 유지하며 `core:data`에 startup 차량
정보·기본값 계약을 제공한다.

두 계약은 기존 NaviClient처럼 Koin factory로 등록한다. 등록 자체로 SDK를 초기화하지 않는다. 소유자는 사용할 클라이언트를 한 번 주입받아 보관하고, 메인
스레드에서 초기화·요청·구독 해제·`release()`를 호출한다. 위치 수집의 소유 범위는 앱의 실제 수집 생명주기와 맞추고, 화면마다 중복 생성하지 않는다.

## 공통 계약

- `initialize(listener)`는 한 번만 호출한다. SDK `initialize()`에는 연결 완료 콜백이 없으므로 `onInitialized()`는 초기화 함수가
  반환됐다는 의미이며 서비스 연결·데이터 가용성을 보장하지 않는다.
- 초기화 중 동기 예외는 `onInitializationFailed(Exception)`로 전달한다. 실패·해제 후 재사용할 수 없으며 새 클라이언트가 필요하다. 초기화 전·실패
  후·해제 후의 데이터 요청은 `IllegalStateException`이다.
- 요청·등록·해제의 동기 예외는 호출자에게 전달한다. SDK의 비동기 오류는 각 리스너의 `onError()`로 전달한다. 자동 재시도·타임아웃·값 보정·fallback은
  추가하지 않는다.
- 콜백은 SDK가 호출한 스레드에서 실행한다. UI 작업과 후속 클라이언트 요청은 main dispatcher로 전환한다. 사용자 콜백은 예외를 던지거나 블로킹하지 않는다.
- 중복 구독은 차단한다. 등록 실패도 부분 등록일 수 있어 해제 의무를 보관하며, `unregister…()` 또는 `release()`로 정리한다. 구독 해제 실패 시 콜백
  전달은 중지하되 해제 의무는 유지하여 다음 명시적 해제에서 다시 시도할 수 있다.
- `release()`는 동일한 SDK 리스너를 먼저 해제하고 SDK를 정리한다. 구독 해제 실패에도 SDK 해제를 시도하며 예외는 호출자에게 전달한다. Vehicle은 다른
  구독의 정리도 계속하고 추가 예외를 suppressed로 보관한다. SDK 초기화를 시도하지 않은 세션은 SDK release를 호출하지 않는다. 중복 release는
  무시한다.
- 해제 후 도착한 콜백은 전달하지 않는다. 이미 실행 중인 사용자 콜백이나 원격 요청을 취소하는 기능은 아니다. 같은 구독을 재등록한 후 이전 구독의 지연 응답을 구별한다고
  보장하지 않는다.

## FusedLocation 1.1.3

| API                                 | 동작                                                                                                     |
|-------------------------------------|----------------------------------------------------------------------------------------------------------|
| `isSupported()`                     | 초기화 없이 서비스 패키지 지원 여부 확인. 연결·권한 상태를 의미하지 않음                                 |
| `initialize(listener)`              | 지원 여부를 다시 확인하고 SDK 초기화. 미지원이면 `onInitializationFailed(UnsupportedOperationException)` |
| `getLastKnownLocation()`            | `onLastKnownLocation(LocationData?)` 또는 `onError(Exception)`                                           |
| `registerFusedLocationCallback()`   | 지속 업데이트를 `onChanged(LocationData?)`와 `onError(Exception)`로 전달                                 |
| `unregisterFusedLocationCallback()` | 지속 업데이트 중지                                                                                       |
| `showLocationAccessBlockedPopup()`  | 개인정보 설정으로 위치 접근이 차단된 경우 호출자가 명시적으로 안내 팝업 요청                             |

위치 `null`, 좌표, confidence, timestamp 등은 그대로 전달한다. 마지막 위치 조회는 새 위치 계산 요청이 아니며, 앱이 신선도·정확도·차단 상태를 판단해야
한다. `null`만으로 개인정보 차단이라고 추정해 팝업을 자동 표시하지 않는다. 내비 위치와의 일치도 보장하지 않는다.

매니페스트에 `pleos.car.permission.FUSED_LOCATION`을 선언한다. dangerous 권한이므로 실제 사용 흐름에서 런타임 권한 요청이 필요하다.
Android 구현은 각 조회·구독·구독 해제 직전 권한을 확인하고 미허용 시 `SecurityException`을 던진다. 이번 변경은 구현체만 제공하므로 권한 요청 UI는
추가하지 않는다. 권한 회수로 구독 해제가 실패해도 SDK release는 시도한다. 스마트폰 위치 권한과 Google 위치 서비스는 추가하지 않는다.

## Vehicle 2.3.2 — Fuel 범위

SDK 초기화 후 `getFuel()`로 Fuel 도메인을 얻는다. 누적 연비·현재 주행 세션 연비·연료 잔량에 대해 각각 다음 메서드를 제공한다.

| 데이터    | Capability                                 | 조회                         | 구독·해제                                                               |
|-----------|--------------------------------------------|------------------------------|-------------------------------------------------------------------------|
| 누적 연비 | `checkCumulativeFuelEconomyCapabilities()` | `getCumulativeFuelEconomy()` | `registerCumulativeFuelEconomy()` / `unregisterCumulativeFuelEconomy()` |
| 현재 연비 | `checkCurrentFuelEconomyCapabilities()`    | `getCurrentFuelEconomy()`    | `registerCurrentFuelEconomy()` / `unregisterCurrentFuelEconomy()`       |
| 잔량      | `checkFuelPercentCapabilities()`           | `getFuelPercent()`           | `registerFuelPercent()` / `unregisterFuelPercent()`                     |

결과는 `onCapabilities(FuelProperty, CapabilityConfig)`, `onFuelEconomy(FuelProperty, Float?)`,
`onFuelPercent(Int?)`, `onError(FuelProperty, Exception)`로 전달한다. capability와 값은 원본 그대로이며 지원 여부를 자동
추정하지 않는다. 호출자는 capability의 `AccessType`이 읽기 가능한지 확인한 뒤 해당 조회·구독을 요청한다. 서로 다른 연비 구독은 별도의 SDK 리스너로
관리한다.

연비는 **L/100km**, 잔량은 **%**다. km/L 변환, 정수 반올림, null·0·비정상 값의 사용자 설정 fallback은 상위 계층에서 담당한다. 탱크 용량·예상
주유량은 추정하지 않는다. 권한은 `pleos.car.permission.CAR_ENERGY`만 선언하며 차량 제어 권한은 추가하지 않는다.

Vehicle 가이드에 따라 진행 중인 get·capability 콜백이 끝난 뒤 release를 호출해야 한다. 어댑터는 원격 요청을 취소하거나 강제로 완료시키지 않는다. 응답
누락 시의 타임아웃과 수명 정책은 사용 흐름에서 별도로 결정해야 한다.

## Startup 차량 정보 수집

`JuyulowApplication`이 process-scoped `VehicleRuntime`을 시작한다. `VehicleStartup`은 누적/현재 연비·잔량 %와
AAOS `INFO_FUEL_CAPACITY`·`FUEL_LEVEL`·`INFO_FUEL_TYPE`을 각각 1회 조회한다. Activity 재생성·복귀 시 재조회와 지속 구독은
없다.
화면·검색 계산 연결, 예상 가득 주유량 및 일반/고급휘발유 판별은 이번 범위에 포함하지 않는다.

- AAOS는 SDK의 optional `android.car` library로 컴파일한다. Automotive 기능과 라이브러리가 없는 환경에서는 SDK 객체를 만들지 않고
  미지원 처리한다.
- AAOS `CAR_INFO`와 `CAR_ENERGY`는 Pleos 권한과 별개다. `MainActivity`가 필요한 에너지 권한을 요청한다.
  `CarUxRestrictions`가
  distraction optimization을 요구하지 않는다고 확인된 RESUMED 화면에서만 요청한다. 제한 중/확인 불가 상태는 보류하고 연결 오류는 실패 처리한다.
  권한 응답을 기다리는 동안에도 나머지 항목과 화면은 진행한다. 거부 후 이번 프로세스에서는 재요청하지 않는다.
- config 읽기 접근과 값 상태를 확인하고, AAOS 동기 조회는 IO에서 수행한다. 미지원·권한 부족·연결 실패·일시적 사용 불가·잘못된 값·시간 초과·기타 오류를
  구분한다.
- 각 연결/capability/get 단계 제한은 5초다. 권한 대화상자 대기 시간은 제외한다. 시간 초과 이후 도착한 값은 적용하지 않는다.
  AAOS는 실제 IO 작업이 끝난 후 disconnect한다. Pleos는 모든 실제 콜백이 돌아온 뒤 release한다. 콜백이 영구 누락되면 SDK 객체를 프로세스 수명까지
  보관한다.
- `VehicleInfoRepository.fields`의 각 `VehicleField`는 raw/applicable 값, 원본 단위 (프로퍼티에 정의), 상태·실패 사유·조회
  완료 시각 (epoch ms)·출처를 보관한다.
  `FETCH`가 `DEFAULT`보다 우선하며 기본값도 없으면 `UNSET`이다. null·비유한 값·0 이하 연비/용량·음수 잔여량·범위 밖 %는 실패다.
  AAOS mL와 Pleos %는 서로 대체하지 않는다. 유종은 UNKNOWN/OTHER 및 미래 코드까지 원본 정수 목록으로 보존하며 차량 종류를 추정하지 않는다.
- `VehicleDefaultsRepository.save(property, value)`는 기존 `SettingsStorage`의 `vehicle_default_*` 키에
  저장한다. null은 해당 override를 삭제한다.
  연비 기본값 미지정 시 기존 수동 연비 (미저장 시 12km/L)를 L/100km로 변환해 사용하고 나머지는 미설정이다. fetch 성공값은 저장하지 않는다.
  `VehicleField.efficiencyKmPerL`은 연비 필드에 한해 사용하는 무반올림 변환 값이다. 수동 설정과 차량 기본값은 서로 덮어쓰지 않는다.
- debug 전용 `VehicleStartup` logcat에 실패 항목·공급자·단계·원인·현재 fallback 출처를 기록한다. 기본값 로딩 전 실패면 로그의 출처는
  UNSET일 수 있으며
  로딩 후 repository에 DEFAULT가 반영된다. release source set의 진단 함수는 no-op이다.
- 실제 Vehicle 2.3.2 AAR은 문서의 `VehicleException`을 난독화하고 있어 직접 참조하지 않는다. 배포된 sources의 정확한 `ErrorCode`
  메시지만
  분류하며, 나머지는 표준 예외 또는 기타 오류로 처리한다. 메시지 계약이 바뀌면 세부 분류를 보장하지 않는다.

AAOS
근거: [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds),
[CarPropertyManager](https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager),
[FuelType](https://developer.android.com/reference/android/car/FuelType).

## 검증과 근거

Fake SDK 테스트는 미지원·초기화 실패·nullable 값·오류 전달·구독 수명·동일 리스너 해제·정리 실패·지연 콜백을 검증한다. 실제 서비스 연결, 런타임 권한
허용·회수, 양산 차량 capability는 실기 검증이 필요하다. 실행 명령은 [TESTING.md](TESTING.md)를 따른다.

근거:
로컬 [FusedLocation 생명주기](../pleos_sdk_guide/앱%20개발·연동%20가이드/SDK%20사용%20가이드/FusedLocation%20SDK/Programming%20Guide/Initialization%20_%20Release.md), [FusedLocation 권한](../pleos_sdk_guide/앱%20개발·연동%20가이드/SDK%20사용%20가이드/FusedLocation%20SDK/Programming%20Guide/Declaring%20permission.md), [Vehicle 도메인 획득](../pleos_sdk_guide/앱%20개발·연동%20가이드/SDK%20사용%20가이드/Vehicle%20SDK/Programming%20Guide/Obtaining%20a%20domain%20object.md), [Fuel API](../pleos_sdk_guide/앱%20개발·연동%20가이드/SDK%20사용%20가이드/Vehicle%20SDK/API%20Reference/ai.pleos.playground.vehicle.api/Fuel/Fuel.md).
