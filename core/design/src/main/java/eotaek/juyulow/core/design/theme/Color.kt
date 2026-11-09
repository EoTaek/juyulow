package eotaek.juyulow.core.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Pleos Connect Foundations/Colors tokens; see docs/DESIGN.md for source corrections. */
@Immutable
class PleosColors internal constructor(darkTheme: Boolean) {

    // Grayscale
    val basic00 = if (darkTheme) Color(0xFF131417) else Color(0xFFFFFFFF)
    val basic50 = if (darkTheme) Color(0xFF1D1E21) else Color(0xFFF7F8FA)
    val basic100 = if (darkTheme) Color(0xFF313236) else Color(0xFFEDEEF2)
    val basic200 = if (darkTheme) Color(0xFF44464E) else Color(0xFFE0E1E6)
    val basic300 = if (darkTheme) Color(0xFF686A72) else Color(0xFFC7C9CE)
    val basic400 = Color(0xFFA4A8AE)
    val basic500 = if (darkTheme) Color(0xFFC7C9CE) else Color(0xFF686A72)
    val basic600 = if (darkTheme) Color(0xFFE0E1E6) else Color(0xFF44464E)
    val basic700 = if (darkTheme) Color(0xFFEDEEF2) else Color(0xFF313236)
    val basic800 = if (darkTheme) Color(0xFFF7F8FA) else Color(0xFF1D1E21)
    val basic900 = if (darkTheme) Color(0xFFFFFFFF) else Color(0xFF131417)

    // Alpha Color
    val basicAlphaLight50 =
        if (darkTheme) Color(0xFFFFFFFF).copy(alpha = 0.05f) else Color(0xFF131417).copy(alpha = 0.05f)
    val basicAlphaLight100 =
        if (darkTheme) Color(0xFFFFFFFF).copy(alpha = 0.1f) else Color(0xFF131417).copy(alpha = 0.1f)
    val basicAlphaLight200 =
        if (darkTheme) Color(0xFFFFFFFF).copy(alpha = 0.2f) else Color(0xFF131417).copy(alpha = 0.2f)
    val basicAlphaLight300 =
        if (darkTheme) Color(0xFFFFFFFF).copy(alpha = 0.3f) else Color(0xFF131417).copy(alpha = 0.3f)
    val basicAlphaLight400 =
        if (darkTheme) Color(0xFFFFFFFF).copy(alpha = 0.4f) else Color(0xFF131417).copy(alpha = 0.4f)
    val basicAlphaLight500 =
        if (darkTheme) Color(0xFFFFFFFF).copy(alpha = 0.5f) else Color(0xFF131417).copy(alpha = 0.5f)
    val basicAlphaDark50 =
        if (darkTheme) Color(0xFF131417).copy(alpha = 0.05f) else Color(0xFFFFFFFF).copy(alpha = 0.05f)
    val basicAlphaDark100 =
        if (darkTheme) Color(0xFF131417).copy(alpha = 0.1f) else Color(0xFFFFFFFF).copy(alpha = 0.1f)
    val basicAlphaDark200 =
        if (darkTheme) Color(0xFF131417).copy(alpha = 0.2f) else Color(0xFFFFFFFF).copy(alpha = 0.2f)
    val basicAlphaDark300 =
        if (darkTheme) Color(0xFF131417).copy(alpha = 0.3f) else Color(0xFFFFFFFF).copy(alpha = 0.3f)
    val basicAlphaDark400 =
        if (darkTheme) Color(0xFF131417).copy(alpha = 0.4f) else Color(0xFFFFFFFF).copy(alpha = 0.4f)
    val basicAlphaDark500 =
        if (darkTheme) Color(0xFF131417).copy(alpha = 0.5f) else Color(0xFFFFFFFF).copy(alpha = 0.5f)

    // Static Color
    val basicStaticLight100 = Color(0xFFFFFFFF)
    val basicStaticLight200 = Color(0xFFFFFFFF).copy(alpha = 0.84f)
    val basicStaticLight300 = Color(0xFFFFFFFF).copy(alpha = 0.64f)
    val basicStaticLight400 = Color(0xFFFFFFFF).copy(alpha = 0.32f)
    val basicStaticDark100 = Color(0xFF131417)
    val basicStaticDark200 = Color(0xFF131417).copy(alpha = 0.84f)
    val basicStaticDark300 = Color(0xFF131417).copy(alpha = 0.64f)
    val basicStaticDark400 = Color(0xFF131417).copy(alpha = 0.32f)

    // Background
    val backgroundPrimary = basic50
    val backgroundSecondary = basic00
    val backgroundPopup = if (darkTheme) basic100 else basic00

    // Button
    val buttonBasicEnabled = basicAlphaDark50
    val buttonBasicPressed = basicAlphaDark100
    val buttonBasicDisabled = basicAlphaLight200
    val buttonSwitchEnabled = if (darkTheme) basicAlphaDark200 else basic00
    val buttonSwitchPressed = if (darkTheme) basicAlphaDark200 else basicAlphaLight500
    val buttonSwitchDisabled = if (darkTheme) basicAlphaDark300 else basicAlphaLight300
    val buttonFilledEnabled = basic600
    val buttonFilledPressed = if (darkTheme) basic500 else basic700
    val buttonFilledDisabled = basicAlphaDark50

    // Controller
    val controllerSliderNormal = if (darkTheme) basicAlphaDark100 else basicAlphaDark200
    val controllerSliderPressed = if (darkTheme) basicAlphaDark200 else basicAlphaDark300
    val controllerSliderKnob = if (darkTheme) basicAlphaDark300 else basic00
    val controllerTabBackground = basicAlphaDark50
    val controllerStepperBackground = if (darkTheme) basicAlphaLight100 else basic00

    // Climate
    val climateButtonEnabled = if (darkTheme) basic50 else basic200
    val climateButtonSelected = if (darkTheme) basic200 else basic00
    val climateButtonBorderEnabled = if (darkTheme) basicAlphaDark100 else basicAlphaDark200
    val climateButtonBorderDisabled = basicAlphaDark50
    val climateToggleSelected = if (darkTheme) basicAlphaLight300 else basicAlphaDark100
    val climateTogglePressed = if (darkTheme) basicAlphaLight400 else basicAlphaDark200
    val climateSliderBackground = if (darkTheme) basic100 else basic00
    val climateAirventButtonOpened = if (darkTheme) basic600 else basic00
    val climateAirventButtonClosed = if (darkTheme) basicAlphaDark200 else basicAlphaLight400
    val climateAirventSymbolOpened = basicStaticDark200
    val climateAirventSymbolClosed = if (darkTheme) basicStaticDark300 else basicStaticDark400
    val climateAirventOpened = if (darkTheme) basicStaticLight400 else basicStaticLight300
    val climateAirventClosed = if (darkTheme) basicAlphaDark100 else basicAlphaLight200

    // Dropdown
    val dropdownNormal = if (darkTheme) basicAlphaDark100 else basic00
    val dropdownPressed = if (darkTheme) basicAlphaDark300 else basic100

    // Dim
    val dimDarkPrimary = basicAlphaDark50
    val dimDarkSecondary = basicAlphaDark100
    val dimDarkTertiary = basicAlphaDark200
    val dimLightPrimary = basicAlphaLight500
    val dimLightSecondary = basicAlphaLight400
    val dimLightTertiary = basicAlphaLight300

    // Driving
    val gearshiftBackground = if (darkTheme) basic100 else basic900
    val chargingNormal = Color(0xFF00DB25)
    val chargingDeep = if (darkTheme) Color(0xFF0082FC) else Color(0xFF00B975)
    val gradientEnd = basic00
    val gradientStart =
        if (darkTheme) Color(0xFF131417).copy(alpha = 0f) else Color(0xFFFFFFFF).copy(alpha = 0f)

    // Fields
    val fieldBackground = basic00
    val fieldFocused = basic700

    // Gleo AI
    val gleoSurfacePrimary = if (darkTheme) basic200 else basic00
    val gleoSurfaceSecondary = if (darkTheme) basicAlphaLight200 else basicAlphaDark50
    val gleoBubblePrimary = if (darkTheme) Color(0xFF545F93) else Color(0xFF9BA8E2)
    val gleoBubbleSecondary = if (darkTheme) Color(0xFF3E4976) else Color(0xFF8896D3)
    val gleoBubbleTertiary = if (darkTheme) basic100 else basic00
    val gleoBubbleSpeaker = if (darkTheme) Color(0xFFB6C1F3) else Color(0xFF3F4B81)

    // Icon
    val iconDarkPrimary = if (darkTheme) basicStaticLight100 else basicStaticDark100
    val iconDarkSecondary = if (darkTheme) basicStaticLight200 else basicStaticDark200
    val iconDarkTertiary = if (darkTheme) basicStaticLight300 else basicStaticDark300
    val iconDarkQuaternary = if (darkTheme) basicStaticLight400 else basicStaticDark400
    val iconLightPrimary = if (darkTheme) basicStaticDark100 else basicStaticLight100
    val iconLightSecondary = if (darkTheme) basicStaticDark200 else basicStaticLight200
    val iconLightTertiary = if (darkTheme) basicStaticDark300 else basicStaticLight300
    val iconLightQuaternary = if (darkTheme) basicStaticDark400 else basicStaticLight400

    // Informative
    val informativeActive = if (darkTheme) Color(0xFF00E074) else Color(0xFF02C265)
    val informativePositive = if (darkTheme) Color(0xFF0082FF) else Color(0xFF0064FF)
    val informativeNegative = if (darkTheme) Color(0xFFFF4C28) else Color(0xFFFE3D16)
    val informativeAutonomousDriving = if (darkTheme) Color(0xFF6A57FF) else Color(0xFF5A46FA)

    // Keyboard
    val keyboardNormalPrimaryNormal = if (darkTheme) basic300 else basic00
    val keyboardNormalPrimaryPressed = if (darkTheme) basic200 else basic100
    val keyboardNormalSecondaryNormal = if (darkTheme) basic200 else basic300
    val keyboardNormalSecondaryPressed = if (darkTheme) basic200 else basic400
    val keyboardNormalActiveNormal = if (darkTheme) basic300 else basic600
    val keyboardNormalActivePressed = if (darkTheme) basic200 else basic700
    val keyboardNormalAccentsNormal = if (darkTheme) basic500 else basic600
    val keyboardNormalAccentsPressed = if (darkTheme) basic400 else basic700
    val keyboardNormalBackground = if (darkTheme) basic100 else basic200
    val keyboardStaticPrimaryNormal = if (darkTheme) basic300 else basic500
    val keyboardStaticPrimaryPressed = if (darkTheme) basic200 else basic600
    val keyboardStaticSecondaryNormal = if (darkTheme) basic200 else basic600
    val keyboardStaticSecondaryPressed = if (darkTheme) basic200 else basic500
    val keyboardStaticActiveNormal = if (darkTheme) basic300 else basic500
    val keyboardStaticActivePressed = if (darkTheme) basic200 else basic700
    val keyboardStaticAccentsNormal = if (darkTheme) basic500 else basic300
    val keyboardStaticAccentsPressed = basic400
    val keyboardStaticBackground = if (darkTheme) basic100 else basic900
    val keyboardStaticIconLight = if (darkTheme) basic400 else basic00
    val keyboardStaticIconDark = if (darkTheme) basic400 else basic900
    val keyboardStaticIconDisabled = if (darkTheme) basicAlphaDark100 else basicAlphaLight100
    val keyboardStaticText = if (darkTheme) basic400 else basic700

    // Media
    val radioPrimary = Color(0xFFC911E7)
    val musicPrimary = Color(0xFF4781FF)
    val radioGradientStart = if (darkTheme) Color(0x001D1E21) else Color(0x00FFFFFF)
    val radioGradientEnd = if (darkTheme) Color(0xFF1D1E21) else Color(0xFFFFFFFF)

    // Overlay
    val overlayDefault = if (darkTheme) basicAlphaLight500 else basicAlphaDark200

    // Phone
    val phoneCallNormal = if (darkTheme) Color(0xFF3BD665) else Color(0xFF32B957)
    val phoneCallPressed = if (darkTheme) Color(0xFF2DB752) else Color(0xFF279E47)
    val phoneEndNormal = if (darkTheme) Color(0xFFFF4C28) else Color(0xFFFE3D16)
    val phoneEndPressed = if (darkTheme) Color(0xFFDF4323) else Color(0xFFE73612)

    // Regulation
    val regulationBlue = if (darkTheme) Color(0xFF0082FF) else Color(0xFF0064FF)
    val regulationGreen = if (darkTheme) Color(0xFF55F165) else Color(0xFF00BA13)
    val regulationYellow = Color(0xFFFFC224)
    val regulationOrange = Color(0xFFFF8A00)
    val regulationRed = if (darkTheme) Color(0xFFFF4339) else Color(0xFFF62E24)
    val regulationBluetooth = Color(0xFF0082FC)

    // Surface
    val surfaceBasic = if (darkTheme) basic50 else basic00
    val surfaceLow = if (darkTheme) basic00 else basic50
    val surfaceHigh = basic100
    val surfaceAccent = basic200

    // Switch
    val switchOn = informativeActive
    val switchOff = if (darkTheme) basicAlphaDark100 else basicAlphaDark300
    val switchDisabled = basicAlphaDark100
    val switchKnobOn = if (darkTheme) basic900 else basic00
    val switchKnobOff = if (darkTheme) basic400 else basic00
    val switchKnobDisabled = if (darkTheme) basicAlphaDark200 else basic00

    // Text
    val textDarkPrimary = if (darkTheme) basicStaticLight100 else basicStaticDark100
    val textDarkSecondary = if (darkTheme) basicStaticLight200 else basicStaticDark200
    val textDarkTertiary = if (darkTheme) basicStaticLight300 else basicStaticDark300
    val textDarkQuaternary = if (darkTheme) basicStaticLight400 else basicStaticDark400
    val textLightPrimary = if (darkTheme) basicStaticDark100 else basicStaticLight100
    val textLightSecondary = if (darkTheme) basicStaticDark200 else basicStaticLight200
    val textLightTertiary = if (darkTheme) basicStaticDark300 else basicStaticLight300
    val textLightQuaternary = if (darkTheme) basicStaticDark400 else basicStaticLight400
}

internal val LightPleosColors = PleosColors(darkTheme = false)
internal val DarkPleosColors = PleosColors(darkTheme = true)
