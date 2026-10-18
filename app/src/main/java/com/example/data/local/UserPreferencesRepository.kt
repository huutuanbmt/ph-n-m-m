package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.DeviceFormFactorMode
import com.example.ui.theme.IconScaleMode
import com.example.ui.theme.OfficeUiStyle
import com.example.ui.theme.ThemeAccentColor
import com.example.ui.theme.ThemeBrightnessMode
import com.example.ui.theme.ToolbarPositionMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "office_pro_preferences")

data class OfficeUserPreferences(
    val uiStyle: OfficeUiStyle = OfficeUiStyle.CLASSIC_OFFICE,
    val formFactorMode: DeviceFormFactorMode = DeviceFormFactorMode.AUTO,
    val brightnessMode: ThemeBrightnessMode = ThemeBrightnessMode.SYSTEM,
    val accentColor: ThemeAccentColor = ThemeAccentColor.OFFICE_NAVY,
    val toolbarPosition: ToolbarPositionMode = ToolbarPositionMode.TOP,
    val iconScale: IconScaleMode = IconScaleMode.STANDARD,
    val isGridView: Boolean = false,
    val autoSaveEnabled: Boolean = true,
    val preserveOriginalFontsOnSave: Boolean = true,
    val cloudSyncEnabled: Boolean = false,
    val connectedGoogleEmail: String = "",
    val googleAccessToken: String = "",
    val biometricLockEnabled: Boolean = false,
    val stylusPressureEnabled: Boolean = true,
    val externalKeyboardShortcutsEnabled: Boolean = true,
    val visibleToolbarGroupsCsv: String = "HOME,INSERT,LAYOUT,REFERENCES,REVIEW,VIEW",
    val pinnedActionsCsv: String = "SAVE,UNDO,REDO,BOLD,ITALIC,FONT,FIND,EXPORT_PDF",
    val serverEngineUrl: String = ""
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val UI_STYLE = stringPreferencesKey("ui_style")
        val FORM_FACTOR = stringPreferencesKey("form_factor")
        val BRIGHTNESS = stringPreferencesKey("brightness")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val TOOLBAR_POS = stringPreferencesKey("toolbar_pos")
        val ICON_SCALE = stringPreferencesKey("icon_scale")
        val IS_GRID_VIEW = booleanPreferencesKey("is_grid_view")
        val AUTO_SAVE = booleanPreferencesKey("auto_save")
        val PRESERVE_ORIGINAL_FONTS = booleanPreferencesKey("preserve_original_fonts")
        val CLOUD_SYNC_ENABLED = booleanPreferencesKey("cloud_sync_enabled")
        val CONNECTED_GOOGLE_EMAIL = stringPreferencesKey("connected_google_email")
        val GOOGLE_ACCESS_TOKEN = stringPreferencesKey("google_access_token")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val STYLUS_PRESSURE = booleanPreferencesKey("stylus_pressure")
        val KEYBOARD_SHORTCUTS = booleanPreferencesKey("keyboard_shortcuts")
        val VISIBLE_GROUPS = stringPreferencesKey("visible_groups")
        val PINNED_ACTIONS = stringPreferencesKey("pinned_actions")
        val SERVER_ENGINE_URL = stringPreferencesKey("server_engine_url")
    }

    val preferencesFlow: Flow<OfficeUserPreferences> = context.dataStore.data.map { prefs ->
        OfficeUserPreferences(
            uiStyle = runCatching { OfficeUiStyle.valueOf(prefs[Keys.UI_STYLE] ?: "") }
                .getOrDefault(OfficeUiStyle.CLASSIC_OFFICE),
            formFactorMode = runCatching { DeviceFormFactorMode.valueOf(prefs[Keys.FORM_FACTOR] ?: "") }
                .getOrDefault(DeviceFormFactorMode.AUTO),
            brightnessMode = runCatching { ThemeBrightnessMode.valueOf(prefs[Keys.BRIGHTNESS] ?: "") }
                .getOrDefault(ThemeBrightnessMode.SYSTEM),
            accentColor = runCatching { ThemeAccentColor.valueOf(prefs[Keys.ACCENT_COLOR] ?: "") }
                .getOrDefault(ThemeAccentColor.OFFICE_NAVY),
            toolbarPosition = runCatching { ToolbarPositionMode.valueOf(prefs[Keys.TOOLBAR_POS] ?: "") }
                .getOrDefault(ToolbarPositionMode.TOP),
            iconScale = runCatching { IconScaleMode.valueOf(prefs[Keys.ICON_SCALE] ?: "") }
                .getOrDefault(IconScaleMode.STANDARD),
            isGridView = prefs[Keys.IS_GRID_VIEW] ?: false,
            autoSaveEnabled = prefs[Keys.AUTO_SAVE] ?: true,
            preserveOriginalFontsOnSave = prefs[Keys.PRESERVE_ORIGINAL_FONTS] ?: true,
            cloudSyncEnabled = prefs[Keys.CLOUD_SYNC_ENABLED] ?: false,
            connectedGoogleEmail = prefs[Keys.CONNECTED_GOOGLE_EMAIL] ?: "",
            googleAccessToken = prefs[Keys.GOOGLE_ACCESS_TOKEN] ?: "",
            biometricLockEnabled = prefs[Keys.BIOMETRIC_LOCK] ?: false,
            stylusPressureEnabled = prefs[Keys.STYLUS_PRESSURE] ?: true,
            externalKeyboardShortcutsEnabled = prefs[Keys.KEYBOARD_SHORTCUTS] ?: true,
            visibleToolbarGroupsCsv = prefs[Keys.VISIBLE_GROUPS] ?: "HOME,INSERT,LAYOUT,REFERENCES,REVIEW,VIEW",
            pinnedActionsCsv = prefs[Keys.PINNED_ACTIONS] ?: "SAVE,UNDO,REDO,BOLD,ITALIC,FONT,FIND,EXPORT_PDF",
            serverEngineUrl = prefs[Keys.SERVER_ENGINE_URL] ?: ""
        )
    }

    suspend fun updateUiStyle(style: OfficeUiStyle) {
        context.dataStore.edit { it[Keys.UI_STYLE] = style.name }
    }

    suspend fun updateFormFactor(mode: DeviceFormFactorMode) {
        context.dataStore.edit { it[Keys.FORM_FACTOR] = mode.name }
    }

    suspend fun updateBrightness(mode: ThemeBrightnessMode) {
        context.dataStore.edit { it[Keys.BRIGHTNESS] = mode.name }
    }

    suspend fun updateAccentColor(color: ThemeAccentColor) {
        context.dataStore.edit { it[Keys.ACCENT_COLOR] = color.name }
    }

    suspend fun updateToolbarPosition(pos: ToolbarPositionMode) {
        context.dataStore.edit { it[Keys.TOOLBAR_POS] = pos.name }
    }

    suspend fun updateIconScale(scale: IconScaleMode) {
        context.dataStore.edit { it[Keys.ICON_SCALE] = scale.name }
    }

    suspend fun toggleGridView(isGrid: Boolean) {
        context.dataStore.edit { it[Keys.IS_GRID_VIEW] = isGrid }
    }

    suspend fun setAutoSave(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SAVE] = enabled }
    }

    suspend fun setPreserveOriginalFonts(preserve: Boolean) {
        context.dataStore.edit { it[Keys.PRESERVE_ORIGINAL_FONTS] = preserve }
    }

    suspend fun setCloudSync(enabled: Boolean, email: String = "", token: String = "") {
        context.dataStore.edit {
            it[Keys.CLOUD_SYNC_ENABLED] = enabled
            if (email.isNotEmpty()) it[Keys.CONNECTED_GOOGLE_EMAIL] = email
            if (token.isNotEmpty()) it[Keys.GOOGLE_ACCESS_TOKEN] = token
        }
    }

    suspend fun setBiometricLock(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_LOCK] = enabled }
    }

    suspend fun setStylusPressure(enabled: Boolean) {
        context.dataStore.edit { it[Keys.STYLUS_PRESSURE] = enabled }
    }

    suspend fun setKeyboardShortcuts(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KEYBOARD_SHORTCUTS] = enabled }
    }

    suspend fun updateVisibleToolbarGroups(groupsCsv: String) {
        context.dataStore.edit { it[Keys.VISIBLE_GROUPS] = groupsCsv }
    }

    suspend fun updatePinnedActions(actionsCsv: String) {
        context.dataStore.edit { it[Keys.PINNED_ACTIONS] = actionsCsv }
    }

    suspend fun updateServerEngineUrl(url: String) {
        context.dataStore.edit { it[Keys.SERVER_ENGINE_URL] = url }
    }
}
