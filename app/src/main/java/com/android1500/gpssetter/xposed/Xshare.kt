package com.android1500.gpssetter.xposed

import com.android1500.gpssetter.BuildConfig
import de.robv.android.xposed.XSharedPreferences

class Xshare {

    private val xPref: XSharedPreferences by lazy {
        XSharedPreferences(BuildConfig.APPLICATION_ID, "${BuildConfig.APPLICATION_ID}_prefs")
    }

    private fun pref(): XSharedPreferences {
        xPref.reload()
        return xPref
    }

    val isStarted: Boolean
        get() = pref().getBoolean("start", false)

    val getLat: Double
        get() = pref().getFloat("latitude", 22.2855200.toFloat()).toDouble()

    val getLng: Double
        get() = pref().getFloat("longitude", 114.1576900.toFloat()).toDouble()

    val isHookedSystem: Boolean
        get() = pref().getBoolean("isHookedSystem", false)

    /** One of MODE_OFF / MODE_SMALL / MODE_MEDIUM / MODE_WIDE / MODE_VERY_WIDE / MODE_CUSTOM. */
    val movementMode: String
        get() = pref().getString("movement_mode", MODE_SMALL) ?: MODE_SMALL

    /** True iff the simulation should produce any drift. False for MODE_OFF. */
    val isMovementEnabled: Boolean
        get() = movementMode != MODE_OFF

    /** Resolved radius in meters for the current mode. */
    val effectiveRadius: Float
        get() = when (val m = movementMode) {
            MODE_OFF -> 0f
            MODE_CUSTOM -> pref().getString("movement_radius", "3")?.toFloatOrNull() ?: 3f
            else -> PRESETS[m]?.first ?: 3f
        }

    /** Resolved max speed in m/s for the current mode. */
    val effectiveMaxSpeed: Float
        get() = when (val m = movementMode) {
            MODE_OFF -> 0f
            MODE_CUSTOM -> pref().getString("movement_speed", "0.3")?.toFloatOrNull() ?: 0.3f
            else -> PRESETS[m]?.second ?: 0.3f
        }

    /** Resolved base accuracy in meters for the current mode. */
    val effectiveAccuracy: Float
        get() = when (val m = movementMode) {
            MODE_CUSTOM -> pref().getString("accuracy_settings", "10")?.toFloatOrNull() ?: 10f
            else -> PRESETS[m]?.third ?: 10f
        }

    companion object {
        const val MODE_OFF = "off"
        const val MODE_SMALL = "small"
        const val MODE_MEDIUM = "medium"
        const val MODE_WIDE = "wide"
        const val MODE_VERY_WIDE = "very_wide"
        const val MODE_CUSTOM = "custom"

        /** Triple(radius m, max speed m/s, accuracy m). Mirrors PrefManager.PRESETS. */
        private val PRESETS = mapOf(
            MODE_OFF to Triple(0f, 0f, 8f),
            MODE_SMALL to Triple(3f, 0.3f, 10f),
            MODE_MEDIUM to Triple(10f, 0.5f, 12f),
            MODE_WIDE to Triple(30f, 1.0f, 15f),
            MODE_VERY_WIDE to Triple(50f, 1.4f, 12f)
        )
    }
}
