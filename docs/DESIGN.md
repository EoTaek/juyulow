# 디자인 색상

## 출처와 적용 범위

색상 기준은 [`pleos_sdk_guide/앱 기획·설계 가이드/디자인 가이드/Foundations/Colors.md`](../pleos_sdk_guide/앱%20기획·설계%20가이드/디자인%20가이드/Foundations/Colors.md)다. `core:design`의 `Color.kt`에 기본·알파·고정 색상과 모든 시스템 색상 149개를 제공한다. 타이포그래피·크기·형태·컴포넌트 구현은 이번 적용 범위에 포함하지 않는다.

`JUYULOWTheme`은 시스템의 라이트·다크 모드를 따르며, `darkTheme` 인자로 명시할 수도 있다. Android 배경화면 기반 동적 색상과 `dynamicColor` 인자는 제거했다. 두 색상 API가 같은 Pleos 팔레트를 사용하도록 유지한다.

```kotlin
JUYULOWTheme(darkTheme = false) {
    val colors = JUYULOWTheme.colors
    // 배경: colors.backgroundPrimary
    // 일반 텍스트: colors.textDarkPrimary
    // 긍정 정보: colors.informativePositive
}
```

원본 snake_case 토큰은 camelCase로 제공한다. 예: `basic_00` → `basic00`, `background_primary` → `backgroundPrimary`. Climate 항목은 용도 구분을 위해 `climate` 접두사를 붙인다. 고정 색상(`basicStatic*`)은 모드가 바뀌어도 유지하고, 알파 값은 원문의 비율을 보존한다.

## Material 색상 매핑

Pleos 원문에는 Material 역할과의 대응표가 없어 다음과 같이 프로젝트에서 매핑한다. Material의 기본 보라색이 남지 않도록 모든 색상 역할을 지정한다.

| Material 역할 | Pleos 토큰 / 정책 |
| --- | --- |
| `primary` / `onPrimary` | `buttonFilledEnabled` / `textLightPrimary` |
| `secondary`, `tertiary` | 그레이스케일 색상으로 일반 컨트롤 표현 |
| `background` / `onBackground` | `backgroundPrimary` / `textDarkPrimary` |
| `surface` / `onSurface` | `surfaceBasic` / `textDarkPrimary` |
| Surface container 계열 | `backgroundSecondary`, `surfaceLow`, `surfaceBasic`, `surfaceHigh`, `surfaceAccent` |
| `error` | `informativeNegative` |
| Fixed 계열 | 모드와 관계없이 라이트 팔레트의 동일한 색상 |
| `surfaceTint` | 투명색으로 Material tonal elevation의 추가 착색 방지 |

기존 Material 컴포넌트의 pressed·disabled 색상 계산은 Material 동작을 유지한다. Pleos가 지정한 개별 상태색이 필요한 컴포넌트는 `JUYULOWTheme.colors.buttonFilledPressed` 등의 토큰을 직접 사용해야 한다.

## 원문 표기 보정

- Text와 Icon 표는 앞·뒤 네 행의 토큰명이 중복된다. 뒤 네 행은 값이 반대색이므로 각각 `textLight*`, `iconLight*`로 구분했다. 앞 네 행은 `textDark*`, `iconDark*`다. 이 명칭 구분은 프로젝트의 해석이며, 제공된 이미지에도 중복명이 남아 있다.
- `alpha_*`, `static_*` 참조는 Basic Color의 `basic_alpha_*`, `basic_static_*`를 가리키는 것으로 처리했다. `alpha_dark100`, `basic_alpha_dark100`, `basic_alpha_dark300`의 누락된 밑줄도 보정했다.
- Media의 `radio_primary`(`C911E7FF`)와 `music_primary`(`4781FFFF`)는 제공된 이미지의 보라색·파란색 견본에 맞춰 RGBA → Compose ARGB로 변환했다. `radio_gradient_*`는 이미지의 투명도·색상에 맞춰 ARGB로 해석했다. 나머지 6자리 HEX는 불투명 RGB다.
- 알파 표의 Light/Dark 열은 제목(White/Gray)과 관계없이 표에 명시된 값 그대로 적용했다.
