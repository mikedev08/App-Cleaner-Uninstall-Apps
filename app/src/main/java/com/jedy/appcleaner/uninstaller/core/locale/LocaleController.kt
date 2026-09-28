package com.jedy.appcleaner.uninstaller.core.locale

import android.content.Context
import androidx.core.os.ConfigurationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves which of our eight languages is in force.
 *
 * V1 applies the choice inside the composition (see [LocalizedContent]) rather than through
 * per-app locales, so selecting a language flips the UI instantly with no activity recreation —
 * which is what the language picker needs in order to preview its own effect. Moving to
 * `AppCompatDelegate.setApplicationLocales` later would additionally surface the choice in
 * Android 13's system settings; nothing else would have to change.
 */
@Singleton
class LocaleController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun deviceDefault(): AppLanguage = AppLanguage.forDevice(
        ConfigurationCompat.getLocales(context.resources.configuration)[0]?.toLanguageTag()
    )

    fun resolve(storedTag: String?): AppLanguage = AppLanguage.fromTag(storedTag) ?: deviceDefault()
}
