package com.davidadrianrg.atril.core.settings

/**
 * Top-level groups of the Settings screen (Android and desktop share them so
 * both platforms present the same structure). [labelKey] is an i18n key.
 */
enum class SettingsCategory(val labelKey: String) {
    CONNECTION("settings.cat.connection"),
    APPEARANCE("settings.cat.appearance"),
    LOCAL("settings.cat.local"),
    SYNC("settings.cat.sync"),
    DOWNLOADS("settings.cat.downloads"),
    ADVANCED("settings.cat.advanced"),
    ABOUT("settings.cat.about"),
}
