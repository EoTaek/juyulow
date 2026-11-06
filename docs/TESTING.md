# 검증

프로젝트 루트의 PowerShell에서 호스트 Gradle 캐시·데몬을 사용한다.

```powershell
$env:GRADLE_USER_HOME = 'C:\Users\ddddd\.gradle'
.\gradlew.bat :app:assembleDebug :feature:comparison:testDebugUnitTest lintDebug --console=plain
```

- `assembleDebug`: 앱과 연결된 모든 Android/Kotlin 모듈의 빌드·패키징을 확인한다.
- `testDebugUnitTest`: 카운터 초기값, 증가·감소·초기화 흐름, 0 하한, Koin ViewModel 등록 및 인스턴스 분리를 확인한다.
- `lintDebug`: Android 모듈의 정적 분석을 수행한다.

화면을 검증할 때는 앱을 실행해 초기값 0, 증가·감소·초기화, 0에서 감소 버튼 비활성화, 화면 회전 후 값 유지를 확인한다. 프로세스 종료 후 값 복원은 현재 지원 범위가 아니다. 단위 테스트는 실제 Compose 클릭이나 Application의 기동을 검증하지 않는다.
