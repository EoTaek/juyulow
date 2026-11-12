# ID 없는 좌표 기반 NaviHelper PoC

2026-11-12, Android 14 Pleos 에뮬레이터 `emulator-5554`, 활성 사용자 10, NaviHelper 3.0.5.

## 목적과 입력

내비 POI ID 없이 WGS84 위도·경도만으로 Trip의 거리·ETA와 목적지 설정이 가능한지 확인했다.
기존 실험에서 위치를 확인한 위도 37.535225, 경도 127.009125를 사용했다.
KATEC 변환을 수행한 실험은 아니며 변환 정확성을 입증하지 않는다.

- TripRequest: poiId 빈 문자열, poiSubId null, poiName `CoordinateOnlyPoc`.
- RouteInfo: poiId와 poiSubId 모두 빈 문자열, address 빈 문자열, 현재 경로 옵션 유지.
- SDK 생성자에서 필수 String 인자를 생략할 수 없어 빈 문자열로 전달했다. 가짜 POI ID나 sub-ID `0`은 사용하지 않았다.

## 관측 결과

- Trip: 거리 **17,593m**, ETA **2,674초**, 응답 poiId 빈 문자열.
- Trip 응답 목적지: 위도 37.5351374, 경도 127.0089799. 입력과 달라 도로 스냅을 고려해야 한다.
- 목적지 설정 후 약 7초에 getDestinationInfo에서 요청 이름·입력 좌표·빈 poiId를 확인했다.
- 목적지 응답: 남은 거리 17,000m, 남은 시간 2,760초, 주소 용산구 한남동, poiSubId null.
- finally에서 cancelRoute를 호출하고 초기 목적지 없음 상태로 복원된 것을 재조회로 확인했다.
- 첫 실행 `OK (1 test)`. 거리·ETA·목적지 반영 단언을 추가한 최종 재실행도 `OK (1 test)`이며 Trip 17,593m·2,680초, 복원 성공을
  확인했다. 이 결과는 해당 에뮬레이터의 단일 좌표에 대한 관측이다.

## 재현

`CoordinateOnlyNavigationPocTest`는 기본 실행에서 건너뛰며 `coordinateOnlyProbe=true`일 때만 실행한다.
선택 인자 `latitude`, `longitude`로 좌표를 변경할 수 있다. 기존 경유지가 있거나 원래 목적지의 복원 정보가 없으면 변경 전에 중단한다.

```powershell
$env:GRADLE_USER_HOME = 'C:\Users\ddddd\.gradle'
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
adb -s emulator-5554 install -r --user 10 app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r --user 10 app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument --user 10 -w -e class eotaek.juyulow.CoordinateOnlyNavigationPocTest -e coordinateOnlyProbe true eotaek.juyulow.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 logcat -d -v brief -s CoordinateOnlyPoc:I
```

## 해석과 미검증 범위

이 환경에서는 외부 주유소를 반드시 내비 POI ID로 매칭해야 한다는 가정이 성립하지 않았다. 좌표만으로 연동하는 구현을 검토할 수 있다.
다만 운영 저장소는 현재 빈 ID를 거부하므로 이 실험만으로 앱의 통합이 완료되지는 않는다.
빈 ID 응답의 요청 귀속, 여러 후보 순차 조회와 지연 응답, 재조회, 경유지 추가, 실제 오피넷 좌표·변환, 실차·다른 내비 버전은 검증하지 않았다.
