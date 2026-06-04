package com.zandaulion.bpdigitized.presentation.locale

/** A selectable app language. [code] is an IETF tag, or "" to follow the system locale. */
data class LangOption(val code: String, val nativeName: String)

/**
 * The languages offered in the in-app language picker. Native names are written in
 * their own script so users recognise their language regardless of the active locale.
 * The empty-code entry means "follow system"; its label is resolved from
 * `R.string.language_system` at the call site.
 */
val APP_LANGUAGES = listOf(
    LangOption("",   ""),
    LangOption("en", "English"),
    LangOption("es", "Español"),
    LangOption("fr", "Français"),
    LangOption("pt", "Português"),
    LangOption("de", "Deutsch"),
    LangOption("ja", "日本語"),
    LangOption("zh", "简体中文"),
    LangOption("hi", "हिन्दी"),
    LangOption("ar", "العربية"),
    LangOption("uk", "Українська"),
    LangOption("ko", "한국어"),
    LangOption("ro", "Română"),
)
