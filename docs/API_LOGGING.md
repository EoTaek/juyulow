# API Logcat 진단

Timber 5.0.1을 debugImplementation으로 사용한다. Application이 Koin과 차량 startup보다 먼저 DebugTree와 공통 ApiLog
출력을 설정한다. release는 NoOpApiLogger이며 필드 생성 람다도 실행하지 않는다. SDK 자체/플랫폼이 출력하는 로그는 이 정책의 대상이 아니다.

core:data는 JVM 모듈이므로 Timber/Android에 의존하지 않는다. ApiLogger 인터페이스와 프로세스 공통 ApiLog 진입점을 사용한다. 앱 시작 시
출력기를 한 번 설치하며 기본값은 no-op이다. 테스트에서 출력기를 교체했다면 반드시 원복한다. 로거 예외는 업무 호출로 전파하지 않는다.

## 필터와 형식

Android Studio Logcat에서 package:eotaek.juyulow tag~:JY.Api 를 사용한다. 태그는 JY.Api.Navi, Location,
Vehicle, AAOS, Opinet, Kakao, Journey이다. 기본은 결과 요약 모드다. 개별 요청·콜백 진단이 필요하면 디버거에서
`ApiLog.detailed = true`로 설정한다. 원복은 `false`이며 프로세스를 다시 시작해도 기본 요약 모드로 돌아온다. release 출력은 활성화되지 않는다.

공통 필드는 provider, op, event이며 호출은 callId, SDK 인스턴스는 clientId, 내비 저장소 작업은 operationId로 구분한다. returned는
동기 메서드 반환이며 실제 경로 반영 성공은 setRoute의 completed로 판단한다. HTTP callId는 통신 계층 ID이고 저장소 작업 ID와 자동으로 연결되지
않는다. SDK 콜백에는 서버 요청 ID가 없으므로 특정 요청에 임의로 귀속시키지 않는다.

- 기본 출력: 오피넷·카카오 결과, 후보 선택, Trip·경로 설정 완료, 초기화 결과, 경고·오류·타임아웃·비정상 HTTP 상태.
- 기본 생략: 요청·동기 반환, HTTP 정상 응답, 반복 refresh 완료, 원본 콜백·항목·좌표·캐시 로그, 정상 취소, 초기화/해제 시작.
- 상세 모드: 위 이벤트와 모든 구독 콜백을 출력한다. 오류 스택 프레임도 상세 모드에서만 포함한다. 취소 전파와 API 동작은 동일하다.
- 긴 로그는 700자 단위 part=N/M으로 나눈다. 줄바꿈은 이스케이프한다.

## 상세 모드의 출력 필드

Pleos는 명시한 공개 필드만 매핑한다. Navi는 위치·POI·경유 순서·옵션·거리·ETA·경로 이벤트, FusedLocation은
위치·고도·방향·confidence·timestampMillis·null, Vehicle은 capability accessType·누적/현재 연비·잔량을 출력한다. SDK 원본
연비는 L/100km, 잔량은 %, Navi 거리/ETA는 m/초다. AAOS는 속성명·status·원본 값 (mL 또는 유종 코드), 연결·주행 규제를 기록한다.

오피넷은 KATEC 요청 좌표·반경 km·유종, 수신/무효/중복 건수, 유효 주유소 ID·이름·원/L·좌표, WGS84 변환과 선택 건수를 기록한다. 카카오는
origin/destination/waypoints의 경도,위도와 경로 옵션·차량 가정, result_code·m·초·통행료 원을 기록한다. HTTP 인터셉터는
메서드·호스트·경로·상태·소요시간만 기록하며 본문을 소비하지 않는다.

정확한 위치·주소·POI·차량값을 포함하므로 debug 진단 자료를 공유할 때 해당 내용을 확인한다. 인증키·쿼리 문자열·Authorization·원문 본문·임의 예외
메시지/cause·SDK 객체 toString은 출력하지 않는다. Vehicle 2.3.2 AAR은 VehicleException을 난독화하므로 기존 startup 분류와 같은
알려진 ERROR_* 메시지만 허용 목록으로 코드화하며 임의 메시지는 제외한다.

## 상세 모드 예시 (가상 값)

```text
D/JY.Api.Opinet: part=1/1 provider=Opinet op=around event=request x=310000.0 y=550000.0 coordinateSystem=KATEC radiusKm=3 fuel=B027
D/JY.Api.Opinet: part=1/1 provider=Opinet op=http event=response callId=4 status=200 elapsedMs=182
D/JY.Api.Vehicle: part=1/1 provider=Vehicle op=onFuelEconomy event=callback clientId=2 property=CURRENT_ECONOMY value=7.4
I/JY.Api.Navi: part=1/1 provider=Navi op=setRoute event=completed operationId=12 elapsedMs=740
D/JY.Api.Journey: part=1/1 provider=Journey op=trip event=cache_hit stationId=S001
```

로깅은 API 호출·재시도·구독·경로 변경을 추가하지 않는다.
