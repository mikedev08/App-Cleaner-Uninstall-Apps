package com.jedy.appcleaner.uninstaller.core.locale

import java.util.Locale

/**
 * The eight languages App Cleaner V1 ships (PRD §1).
 *
 * [isRtl] drives the one exception to the global LTR rule: only Arabic and Hebrew flip the
 * layout. Every other language renders LTR regardless of the device's own direction.
 */
enum class AppLanguage(
    val tag: String,
    val englishName: String,
    val nativeName: String,
    val isRtl: Boolean = false,
) {
    ENGLISH("en", "English", "English"),
    SPANISH("es", "Spanish", "Español"),
    FRENCH("fr", "French", "Français"),
    GERMAN("de", "German", "Deutsch"),
    MANDARIN("zh", "Mandarin", "中文"),
    HINDI("hi", "Hindi", "हिन्दी"),
    ARABIC("ar", "Arabic", "العربية", isRtl = true),
    HEBREW("iw", "Hebrew", "עברית", isRtl = true);

    /**
     * Hebrew is stored under its legacy code because that is what the `values-iw` resource folder
     * and the `localeFilters` entry use. "he" is the canonical BCP-47 tag, so that is what we hand
     * to the framework — both spellings happen to resolve on current Android and JVM, but only the
     * canonical one is guaranteed to.
     */
    fun toLocale(): Locale = Locale.forLanguageTag(if (tag == "iw") "he" else tag)

    companion object {
        val DEFAULT = ENGLISH

        /** Tolerates both the modern ("he") and legacy ("iw") Hebrew codes. */
        fun fromTag(tag: String?): AppLanguage? {
            val language = tag?.takeIf { it.isNotBlank() }
                ?.let { Locale.forLanguageTag(it).language }
                ?: return null
            return entries.firstOrNull { it.tag == language }
                ?: if (language == "he") HEBREW else null
        }

        /** The device's language if we support it, otherwise English. */
        fun forDevice(deviceTag: String?): AppLanguage = fromTag(deviceTag) ?: DEFAULT
    }
}
