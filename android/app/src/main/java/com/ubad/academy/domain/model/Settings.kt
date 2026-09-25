package com.ubad.academy.domain.model

/** The six web themes, keyed by their exact backup/prefs identifiers. */
enum class ThemeId(val key: String) {
    DARK("dark"), OLED("oled"), LIGHT("light"), PAPER("paper"), SAGE("sage"), ROSE("rose");

    val isDark: Boolean get() = this == DARK || this == OLED

    companion object {
        fun from(key: String?): ThemeId = entries.firstOrNull { it.key == key } ?: DARK
        fun isValid(key: String?) = entries.any { it.key == key }
    }
}

enum class AppLanguage(val tag: String) {
    AR("ar"), EN("en");
    companion object { fun from(tag: String?) = if (tag == "en") EN else AR }
}

data class UserSettings(
    val name: String = DEFAULT_NAME,
    val language: AppLanguage = AppLanguage.AR,
    val sound: Boolean = true,
    val theme: ThemeId = ThemeId.DARK,
    val onboarded: Boolean = false,
) {
    companion object { const val DEFAULT_NAME = "Ubad" }
}

/** `state.focus` — daily completed sessions + the user's custom lengths (1–180 min). */
data class FocusSettings(
    val day: String = "",
    val done: Int = 0,
    val focusMins: Int = 25,
    val breakMins: Int = 5,
) {
    companion object {
        const val MIN_LEN = 1
        const val MAX_LEN = 180
    }
}

/** Persisted running-timer state so the timer survives process death. */
data class TimerState(
    val phase: FocusPhase = FocusPhase.FOCUS,
    val running: Boolean = false,
    val endsAt: Long = 0L,
    val remainingSec: Int = -1,
)

enum class FocusPhase(val key: String) { FOCUS("focus"), BREAK("break") }
