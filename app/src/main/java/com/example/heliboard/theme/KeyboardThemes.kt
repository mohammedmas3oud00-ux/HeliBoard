package com.example.heliboard.theme

import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.example.heliboard.data.CustomThemeSpec

data class KeyboardColorPalette(
    val id: String,
    val displayName: String,
    val isDark: Boolean,
    val accent: Color,
    val background: Color,
    val keyBackground: Color,
    val functionalKeyBackground: Color,
    val spaceBarBackground: Color,
    val keyText: Color,
    val keyHintText: Color,
    val suggestionText: Color,
    val spaceBarText: Color,
    val actionKeyIcon: Color,
    val stripBackground: Color,
    val popupBackground: Color
)

object KeyboardThemes {

    val PRESET_THEMES = listOf(
        "light" to "Light",
        "dark" to "Dark",
        "darker" to "Darker",
        "black" to "AMOLED Black",
        "dynamic" to "Dynamic (Material You)",
        "blue_gray" to "Blue Gray",
        "brown" to "Brown",
        "chocolate" to "Chocolate",
        "cloudy" to "Cloudy",
        "forest" to "Forest",
        "indigo" to "Indigo",
        "ocean" to "Ocean",
        "pink" to "Pink",
        "sand" to "Sand",
        "violette" to "Violette",
        "holo_white" to "Holo White"
    )

    fun resolvePalette(
        themeId: String,
        context: Context,
        systemDark: Boolean,
        customThemes: List<CustomThemeSpec> = emptyList()
    ): KeyboardColorPalette {
        customThemes.firstOrNull { it.name == themeId }?.let { custom ->
            return KeyboardColorPalette(
                id = custom.name,
                displayName = custom.name,
                isDark = custom.isDark,
                accent = Color(custom.accentColor),
                background = Color(custom.backgroundColor),
                keyBackground = Color(custom.keyColor),
                functionalKeyBackground = Color(custom.functionalKeyColor),
                spaceBarBackground = Color(custom.spaceBarColor),
                keyText = Color(custom.textColor),
                keyHintText = Color(custom.hintTextColor),
                suggestionText = Color(custom.textColor),
                spaceBarText = Color(custom.hintTextColor).copy(alpha = 0.72f),
                actionKeyIcon = if (Color(custom.accentColor).luminance() > 0.55f) Color(0xFF1A1C1E) else Color.White,
                stripBackground = shiftTone(Color(custom.backgroundColor), custom.isDark, 0.04f),
                popupBackground = shiftTone(Color(custom.keyColor), custom.isDark, 0.08f)
            )
        }

        return when (themeId) {
            "dynamic" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val accent = Color(context.getColor(if (systemDark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600))
                    val bg = Color(context.getColor(if (systemDark) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50))
                    val keyBg = Color(context.getColor(if (systemDark) android.R.color.system_neutral1_800 else android.R.color.system_neutral1_0))
                    val funcBg = Color(context.getColor(if (systemDark) android.R.color.system_accent2_700 else android.R.color.system_accent2_100))
                    val text = Color(context.getColor(if (systemDark) android.R.color.system_neutral1_50 else android.R.color.system_neutral1_900))
                    val hint = Color(context.getColor(if (systemDark) android.R.color.system_neutral2_300 else android.R.color.system_neutral2_600))
                    KeyboardColorPalette(
                        id = "dynamic",
                        displayName = "Dynamic",
                        isDark = systemDark,
                        accent = accent,
                        background = bg,
                        keyBackground = keyBg,
                        functionalKeyBackground = funcBg,
                        spaceBarBackground = keyBg,
                        keyText = text,
                        keyHintText = hint,
                        suggestionText = text,
                        spaceBarText = hint.copy(alpha = 0.75f),
                        actionKeyIcon = if (accent.luminance() > 0.55f) Color(0xFF111318) else Color.White,
                        stripBackground = shiftTone(bg, systemDark, 0.04f),
                        popupBackground = shiftTone(keyBg, systemDark, 0.07f)
                    )
                } else {
                    resolvePalette(if (systemDark) "dark" else "light", context, systemDark)
                }
            }
            "light" -> KeyboardColorPalette(
                id = "light",
                displayName = "Light",
                isDark = false,
                accent = Color(0xFF0B57D0),
                background = Color(0xFFEEF0F5),
                keyBackground = Color(0xFFFFFFFF),
                functionalKeyBackground = Color(0xFFDCE1EA),
                spaceBarBackground = Color(0xFFFFFFFF),
                keyText = Color(0xFF1B1C1E),
                keyHintText = Color(0xFF5F6368),
                suggestionText = Color(0xFF1F1F1F),
                spaceBarText = Color(0xFF5F6368),
                actionKeyIcon = Color.White,
                stripBackground = Color(0xFFEEF0F5),
                popupBackground = Color(0xFFFFFFFF)
            )
            "dark" -> KeyboardColorPalette(
                id = "dark",
                displayName = "Dark",
                isDark = true,
                accent = Color(0xFFA8C7FA),
                background = Color(0xFF131314),
                keyBackground = Color(0xFF2C2D30),
                functionalKeyBackground = Color(0xFF1E1F22),
                spaceBarBackground = Color(0xFF2C2D30),
                keyText = Color(0xFFF2F3F5),
                keyHintText = Color(0xFFA4A8AF),
                suggestionText = Color(0xFFE3E3E3),
                spaceBarText = Color(0xFFB4B8BF),
                actionKeyIcon = Color(0xFF062E6F),
                stripBackground = Color(0xFF131314),
                popupBackground = Color(0xFF35373C)
            )
            "darker" -> KeyboardColorPalette(
                id = "darker",
                displayName = "Darker",
                isDark = true,
                accent = Color(0xFFA8C7FA),
                background = Color(0xFF0E0E10),
                keyBackground = Color(0xFF222326),
                functionalKeyBackground = Color(0xFF17181B),
                spaceBarBackground = Color(0xFF222326),
                keyText = Color(0xFFF2F3F5),
                keyHintText = Color(0xFF9CA3AF),
                suggestionText = Color(0xFFE3E3E3),
                spaceBarText = Color(0xFF9CA3AF),
                actionKeyIcon = Color(0xFF062E6F),
                stripBackground = Color(0xFF0E0E10),
                popupBackground = Color(0xFF2E3035)
            )
            "black" -> KeyboardColorPalette(
                id = "black",
                displayName = "AMOLED Black",
                isDark = true,
                accent = Color(0xFFA8C7FA),
                background = Color(0xFF000000),
                keyBackground = Color(0xFF1F2023),
                functionalKeyBackground = Color(0xFF121316),
                spaceBarBackground = Color(0xFF1F2023),
                keyText = Color(0xFFFFFFFF),
                keyHintText = Color(0xFF9CA3AF),
                suggestionText = Color(0xFFF2F3F5),
                spaceBarText = Color(0xFF9CA3AF),
                actionKeyIcon = Color(0xFF062E6F),
                stripBackground = Color(0xFF000000),
                popupBackground = Color(0xFF2A2B30)
            )
            "holo_white" -> KeyboardColorPalette(
                id = "holo_white",
                displayName = "Holo White",
                isDark = true,
                accent = Color(0xFF33B5E5),
                background = Color(0xFF282828),
                keyBackground = Color(0xFF4A4A4A),
                functionalKeyBackground = Color(0xFF3A3A3A),
                spaceBarBackground = Color(0xFF4A4A4A),
                keyText = Color(0xFFFFFFFF),
                keyHintText = Color(0xFFBDBDBD),
                suggestionText = Color(0xFFFFFFFF),
                spaceBarText = Color(0xFFBDBDBD),
                actionKeyIcon = Color.White,
                stripBackground = Color(0xFF202020),
                popupBackground = Color(0xFF555555)
            )
            "blue_gray" -> KeyboardColorPalette(
                id = "blue_gray",
                displayName = "Blue Gray",
                isDark = false,
                accent = Color(0xFF546E7A),
                background = Color(0xFFECEFF1),
                keyBackground = Color(0xFFFFFFFF),
                functionalKeyBackground = Color(0xFFCFD8DC),
                spaceBarBackground = Color(0xFFFFFFFF),
                keyText = Color(0xFF102027),
                keyHintText = Color(0xFF455A64),
                suggestionText = Color(0xFF102027),
                spaceBarText = Color(0xFF546E7A),
                actionKeyIcon = Color.White,
                stripBackground = Color(0xFFE1E6EA),
                popupBackground = Color(0xFFCFD8DC)
            )
            "brown" -> KeyboardColorPalette(
                id = "brown",
                displayName = "Brown",
                isDark = false,
                accent = Color(0xFF8D6E63),
                background = Color(0xFFEFEBE9),
                keyBackground = Color(0xFFFFFFFF),
                functionalKeyBackground = Color(0xFFD7CCC8),
                spaceBarBackground = Color(0xFFFFFFFF),
                keyText = Color(0xFF271C19),
                keyHintText = Color(0xFF5D4037),
                suggestionText = Color(0xFF271C19),
                spaceBarText = Color(0xFF6D4C41),
                actionKeyIcon = Color.White,
                stripBackground = Color(0xFFE6DFDC),
                popupBackground = Color(0xFFD7CCC8)
            )
            "chocolate" -> KeyboardColorPalette(
                id = "chocolate",
                displayName = "Chocolate",
                isDark = true,
                accent = Color(0xFF5080FF),
                background = Color(0xFF8C705E),
                keyBackground = Color(0xFFC1A392),
                functionalKeyBackground = Color(0xFFA87F67),
                spaceBarBackground = Color(0xFFC1A392),
                keyText = Color(0xFFFFFFFF),
                keyHintText = Color(0xFFFDF6F0),
                suggestionText = Color(0xFFFFFFFF),
                spaceBarText = Color(0xFFF3E7DF),
                actionKeyIcon = Color.White,
                stripBackground = Color(0xFF7D6252),
                popupBackground = Color(0xFFA87F67)
            )
            "cloudy" -> KeyboardColorPalette(
                id = "cloudy",
                displayName = "Cloudy",
                isDark = true,
                accent = Color(0xFFFF7181),
                background = Color(0xFF516171),
                keyBackground = Color(0xFF75808E),
                functionalKeyBackground = Color(0xFF636D79),
                spaceBarBackground = Color(0xFF75808E),
                keyText = Color(0xFFFFFFFF),
                keyHintText = Color(0xFFE2E8F0),
                suggestionText = Color(0xFFFFFFFF),
                spaceBarText = Color(0xFFCBD5E1),
                actionKeyIcon = Color.White,
                stripBackground = Color(0xFF475563),
                popupBackground = Color(0xFF636D79)
            )
            "forest" -> KeyboardColorPalette(
                id = "forest",
                displayName = "Forest",
                isDark = false,
                accent = Color(0xFF4B6E4B),
                background = Color(0xFFB57D58),
                keyBackground = Color(0xFFE4D4BF),
                functionalKeyBackground = Color(0xFFD4BA99),
                spaceBarBackground = Color(0xFFE4D4BF),
                keyText = Color(0xFF003200),
                keyHintText = Color(0xFF005000),
                suggestionText = Color(0xFF003200),
                spaceBarText = Color(0xFF1B4D1B),
                actionKeyIcon = Color.White,
                stripBackground = Color(0xFFA6714F),
                popupBackground = Color(0xFFD4BA99)
            )
            "indigo" -> KeyboardColorPalette(
                id = "indigo",
                displayName = "Indigo",
                isDark = false,
                accent = Color(0xFF5C6BC0),
                background = Color(0xFFE8EAF6),
                keyBackground = Color(0xFFFFFFFF),
                functionalKeyBackground = Color(0xFFC5CAE9),
                spaceBarBackground = Color(0xFFFFFFFF),
                keyText = Color(0xFF1A237E),
                keyHintText = Color(0xFF3949AB),
                suggestionText = Color(0xFF1A237E),
                spaceBarText = Color(0xFF5C6BC0),
                actionKeyIcon = Color.White,
                stripBackground = Color(0xFFDDE1F2),
                popupBackground = Color(0xFFC5CAE9)
            )
            "ocean" -> KeyboardColorPalette(
                id = "ocean",
                displayName = "Ocean",
                isDark = true,
                accent = Color(0xFFFF7C00),
                background = Color(0xFF596D9B),
                keyBackground = Color(0xFF849DD4),
                functionalKeyBackground = Color(0xFF5174C2),
                spaceBarBackground = Color(0xFF849DD4),
                keyText = Color(0xFFFFFFFF),
                keyHintText = Color(0xFFEFF6FF),
                suggestionText = Color(0xFFFFFFFF),
                spaceBarText = Color(0xFFDBEAFE),
                actionKeyIcon = Color.White,
                stripBackground = Color(0xFF4E608A),
                popupBackground = Color(0xFF5174C2)
            )
            "pink" -> KeyboardColorPalette(
                id = "pink",
                displayName = "Pink",
                isDark = false,
                accent = Color(0xFFEC407A),
                background = Color(0xFFFCE4EC),
                keyBackground = Color(0xFFFFFFFF),
                functionalKeyBackground = Color(0xFFF8BBD0),
                spaceBarBackground = Color(0xFFFFFFFF),
                keyText = Color(0xFF4A0E24),
                keyHintText = Color(0xFFAD1457),
                suggestionText = Color(0xFF4A0E24),
                spaceBarText = Color(0xFFC2185B),
                actionKeyIcon = Color.White,
                stripBackground = Color(0xFFF8D6E2),
                popupBackground = Color(0xFFF8BBD0)
            )
            "sand" -> KeyboardColorPalette(
                id = "sand",
                displayName = "Sand",
                isDark = false,
                accent = Color(0xFF6E9BFF),
                background = Color(0xFFF2E8DA),
                keyBackground = Color(0xFFFFFFFF),
                functionalKeyBackground = Color(0xFFEAD3B9),
                spaceBarBackground = Color(0xFFFFFFFF),
                keyText = Color(0xFF2D241C),
                keyHintText = Color(0xFF5C4B3B),
                suggestionText = Color(0xFF2D241C),
                spaceBarText = Color(0xFF74614E),
                actionKeyIcon = Color.White,
                stripBackground = Color(0xFFEAE0D0),
                popupBackground = Color(0xFFEAD3B9)
            )
            "violette" -> KeyboardColorPalette(
                id = "violette",
                displayName = "Violette",
                isDark = true,
                accent = Color(0xFFFF60FF),
                background = Color(0xFF7070AE),
                keyBackground = Color(0xFF9696D8),
                functionalKeyBackground = Color(0xFF7B7BCE),
                spaceBarBackground = Color(0xFF9696D8),
                keyText = Color(0xFFFFFFFF),
                keyHintText = Color(0xFFF5F3FF),
                suggestionText = Color(0xFFFFFFFF),
                spaceBarText = Color(0xFFEDE9FE),
                actionKeyIcon = Color.White,
                stripBackground = Color(0xFF63639C),
                popupBackground = Color(0xFF7B7BCE)
            )
            else -> resolvePalette(if (systemDark) "dark" else "light", context, systemDark)
        }
    }

    fun defaultCustomTheme(name: String, isDark: Boolean): CustomThemeSpec {
        return if (isDark) {
            CustomThemeSpec(
                name = name,
                isDark = true,
                accentColor = Color(0xFF38BDF8).toArgb(),
                backgroundColor = Color(0xFF0F172A).toArgb(),
                keyColor = Color(0xFF1E293B).toArgb(),
                functionalKeyColor = Color(0xFF334155).toArgb(),
                spaceBarColor = Color(0xFF1E293B).toArgb(),
                textColor = Color(0xFFF8FAFC).toArgb(),
                hintTextColor = Color(0xFF94A3B8).toArgb()
            )
        } else {
            CustomThemeSpec(
                name = name,
                isDark = false,
                accentColor = Color(0xFF0284C7).toArgb(),
                backgroundColor = Color(0xFFE2E8F0).toArgb(),
                keyColor = Color(0xFFFFFFFF).toArgb(),
                functionalKeyColor = Color(0xFFCBD5E1).toArgb(),
                spaceBarColor = Color(0xFFFFFFFF).toArgb(),
                textColor = Color(0xFF0F172A).toArgb(),
                hintTextColor = Color(0xFF475569).toArgb()
            )
        }
    }

    private fun shiftTone(color: Color, isDark: Boolean, delta: Float): Color {
        return if (isDark) {
            Color(
                red = (color.red + delta).coerceAtMost(1f),
                green = (color.green + delta).coerceAtMost(1f),
                blue = (color.blue + delta).coerceAtMost(1f),
                alpha = color.alpha
            )
        } else {
            Color(
                red = (color.red - delta).coerceAtLeast(0f),
                green = (color.green - delta).coerceAtLeast(0f),
                blue = (color.blue - delta).coerceAtLeast(0f),
                alpha = color.alpha
            )
        }
    }
}
