## BACKLOG.md

## 후속 작업

- 한 항목은 가능한 한 한 줄 요약으로 시작한다.
- 3단 워크플로우에서 구현 스펙 수준의 긴 내용은 `HANDOFF.md`로 옮긴다. 그 밖의 작업은 항목에 필요한 범위만 유지한다.
- 구현 중 발견된 기술 부채는 이 문서에 저장한다.
- 개인 메모리, 임시 생각, 이미 해결된 디버깅 로그는 남기지 않는다.
- 날짜는 `YYYY-MM-DD` 형식으로 쓴다.
- 상태는 섹션으로 관리하고, 항목 자체는 목록으로 누적한다.

- 주행 규제 상태(운전 중, 정차 중)에 대한 처리
- 전기차에 대한 처리
- 2026-11-10: NaviHelper 경유지 조회 좌표 누락과 경로 성공 확인 정책을 검토한다 (#9). Pleos Connect v2.1.0 / Navigation 3.3.2-rc5-hotfix.3 / NaviHelper 3.0.5에서 소설한남 목적지를 유지한 더현대서울 경유 추가 후 POI ID는 일치했으나 `getWaypointInfo()` 좌표가 `(0,0)`으로 반환되어 `NavigationRepositoryIntegrationTest.verifiedFixtureTripAndRouteRoundTrip`이 `ROUTE_NOT_CONFIRMED`로 실패했다. 초기 경로 복원은 확인했다. SDK의 좌표 제공 계약을 확인하고, 검증 근거 없이 좌표 검사를 완화하지 않은 상태에서 성공 확인 방식을 결정한 뒤 경유지 0/1/2개·순서 보존을 실제 내비에서 재검증한다. 실측 결과와 재현 인자는 [NAVIHELPER.md](NAVIHELPER.md#에뮬레이터-확인-2026-11-10), [TESTING.md](TESTING.md#로컬-단위-테스트-구성)를 참조한다.

## 추가 지침

- 
