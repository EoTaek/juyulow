# 디자인 토큰

## 출처와 적용 범위

코드로 제공하는 색상·타이포그래피·그림자 토큰은 `core:design`의 `theme` 패키지에 둔다. 아이콘 제작·선택 원칙은 [ICONOGRAPHY.md](ICONOGRAPHY.md)에 정리한다. 아이콘 에셋과 공통 UI 컴포넌트는 아직 제공하지 않는다.

색상 기준은 [`pleos_sdk_guide/앱 기획·설계 가이드/디자인 가이드/Foundations/Colors.md`](../pleos_sdk_guide/앱%20기획·설계%20가이드/디자인%20가이드/Foundations/Colors.md)다. `Color.kt`에 기본·알파·고정 색상과 모든 시스템 색상 149개를 제공한다.

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

## Typography

기준은 [Pleos Typography](../pleos_sdk_guide/앱%20기획·설계%20가이드/디자인%20가이드/Foundations/Typography.md)다. `Type.kt`의 `PleosTypography`를 `JUYULOWTheme.typography`로 제공한다. 글자 크기와 행간은 `sp`로 표현한다.

| 토큰 | 글자 크기 / 행간 (sp) | 굵기 변형 |
| --- | --- | --- |
| `headlineLarge` / `headlineMedium` / `headlineSmall` | 60/80 · 56/72 · 48/64 | 기본 Bold(700), `ExtraBold`(800) 접미사 |
| `titleLarge` / `titleMedium` / `titleSmall` | 40/52 · 36/44 · 32/40 | 기본 Regular(400), `Bold`(700)·`ExtraBold`(800) 접미사 |
| `bodyLarge` / `bodyMedium` / `bodySmall` | 30/38 · 28/36 · 26/34 | 기본 Normal(400), `Strong`(600) 접미사 |
| `labelMedium` / `labelSmall` | 24/28 · 20/24 | Normal(400) |

예: `JUYULOWTheme.typography.titleLargeBold`, `JUYULOWTheme.typography.bodyMediumStrong`.

가이드의 `Strong`에는 숫자 굵기가 없어 프로젝트에서 SemiBold(600)로 해석한다. 자간도 별도 수치가 없어 `0.sp`로 설정한다. 기본 서체는 `FontFamily.SansSerif`다. 가이드의 System UI 서체는 Asta Sans지만 저장소에 폰트 파일이 없으며, 차량에 설치되어 있다고 가정하지 않는다. 추후 폰트를 번들링하면 `PleosTypography(fontFamily = ...)`를 만들어 `JUYULOWTheme(typography = ...)`에 전달할 수 있다.

`MaterialTheme.typography`도 같은 토큰을 사용한다. 아래 대응은 프로젝트의 매핑이며, Pleos가 Material 역할을 정의한 것은 아니다.

| Material 역할 | Pleos 토큰 |
| --- | --- |
| `displayLarge` / `displayMedium` / `displaySmall` | 같은 크기 단계의 `headline*` |
| `headline*`, `title*`, `body*` | 같은 이름의 기본 굵기 토큰 |
| `labelLarge`, `labelMedium` | `labelMedium` |
| `labelSmall` | `labelSmall` |

기존 카운터 제목·숫자·버튼도 이 매핑으로 차량용 글자 크기를 사용한다. `sp`는 사용자의 글꼴 배율에 따라 변하므로 실제 픽셀 크기나 고정 레이아웃 크기로 취급하지 않는다.

## Shadow

기준은 [Pleos Shadow](../pleos_sdk_guide/앱%20기획·설계%20가이드/디자인%20가이드/Foundations/Shadow.md)다. `Shadow.kt`의 `JUYULOWTheme.shadows.level1`~`level3`을 사용한다. 각 단계는 `primary`와 선택적인 `secondary` 레이어를 가지며, 두 레이어가 있는 단계는 함께 그린다.

| 단계 | 레이어 | X / Y / Blur / Spread (dp) | ARGB 색상 |
| --- | --- | --- | --- |
| `level1` | `primary` | 0 / 6 / 24 / 0 | `#14000000` |
| `level2` | `primary` | 0 / 8 / 32 / 0 | `#29000000` |
| `level2` | `secondary` | 0 / 2 / 2 / 0 | `#14000000` |
| `level3` | `primary` | 0 / 20 / 40 / 0 | `#3D000000` |
| `level3` | `secondary` | 0 / 2 / 4 / 0 | `#33000000` |

문서의 공간 수치를 `dp`, Blur를 Compose `Shadow.radius`로 옮긴다. 색상은 원문의 ARGB 값을 그대로 보존하며 별도의 알파를 중복 적용하지 않는다. 다크 모드별 값이 따로 정의되지 않아 두 테마가 같은 토큰을 사용한다.

레이어는 Compose의 `androidx.compose.ui.graphics.shadow.Shadow`이므로 `Modifier.dropShadow`에 전달할 수 있다. 예를 들어 단일 레이어는 `Modifier.dropShadow(shape, JUYULOWTheme.shadows.level1.primary)`로 적용한다. 두 레이어는 각각 `dropShadow`를 적용한 후 배경을 그리며, 그림자가 잘리지 않도록 부모의 clipping도 확인한다. Material의 `shadowElevation` 하나로 치환하면 블러·오프셋·레이어 구성이 보존되지 않는다. 실제 그림자 표현은 디바이스에서 확인해야 한다.
