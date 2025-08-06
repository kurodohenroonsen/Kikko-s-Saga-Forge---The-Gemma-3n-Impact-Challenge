package be.heyman.android.ai.kikko.util

import android.content.Context
import android.content.SharedPreferences

object PreferenceHelper {

    const val PREFS_NAME = "kikko_prefs"

    // Onboarding
    const val PREF_ONBOARDING_COMPLETED = "onboarding_completed"

    // Queen AI Configuration
    const val KEY_SELECTED_FORGE_QUEEN = "selected_forge_queen"
    const val KEY_SELECTED_FORGE_QUEEN_ACCELERATOR = "selected_forge_queen_accelerator"

    // Worker Constraints
    const val KEY_REQUIRE_CHARGING = "require_charging"
    const val KEY_REQUIRE_IDLE = "require_idle"


    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun setBoolean(context: Context, key: String, value: Boolean) {
        getPrefs(context).edit().putBoolean(key, value).apply()
    }

    fun getBoolean(context: Context, key: String, defaultValue: Boolean): Boolean {
        return getPrefs(context).getBoolean(key, defaultValue)
    }

    fun setString(context: Context, key: String, value: String?) {
        getPrefs(context).edit().putString(key, value).apply()
    }

    fun getString(context: Context, key: String, defaultValue: String?): String? {
        return getPrefs(context).getString(key, defaultValue)
    }
}