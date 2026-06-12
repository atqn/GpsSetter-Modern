package com.android1500.gpssetter.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.android1500.gpssetter.BuildConfig
import com.android1500.gpssetter.gsApp
import com.android1500.gpssetter.selfhook.EnvCheck
import dagger.Reusable
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import rikka.material.app.DayNightDelegate
import java.io.File


@SuppressLint("WorldReadableFiles")

object PrefManager   {

    private const val START = "start"
    private const val LATITUDE = "latitude"
    private const val LONGITUDE = "longitude"
    private const val HOOKED_SYSTEM = "isHookedSystem"
    private const val MOVEMENT_MODE = "movement_mode"
    private const val MOVEMENT_RADIUS = "movement_radius"
    private const val MOVEMENT_SPEED = "movement_speed"
    private const val ACCURACY_SETTING = "accuracy_settings"
    private const val MAP_TYPE = "map_type"
    private const val DARK_THEME = "dark_theme"
    private const val DISABLE_UPDATE = "disable_update"
    private const val HIDE_FROM_APPS = "hide_from_apps"

    /**
     * Movement-mode presets. Keep in sync with values/arrays.xml
     * (movement_mode_values) and the resolver below.
     */
    const val MODE_OFF = "off"
    const val MODE_SMALL = "small"
    const val MODE_MEDIUM = "medium"
    const val MODE_WIDE = "wide"
    const val MODE_VERY_WIDE = "very_wide"
    const val MODE_CUSTOM = "custom"

    /** (radiusMeters, maxSpeedMps, accuracyMeters) for each preset. */
    private val PRESETS = mapOf(
        MODE_OFF to Triple(0f, 0f, 8f),
        MODE_SMALL to Triple(3f, 0.3f, 10f),
        MODE_MEDIUM to Triple(10f, 0.5f, 12f),
        MODE_WIDE to Triple(30f, 1.0f, 15f),
        MODE_VERY_WIDE to Triple(50f, 1.4f, 12f)
    )


    private val pref: SharedPreferences by lazy {
         try {
             val prefsFile = "${BuildConfig.APPLICATION_ID}_prefs"
             gsApp.getSharedPreferences(
                 prefsFile,
                 Context.MODE_WORLD_READABLE
             )
         }catch (e:SecurityException){
             val prefsFile = "${BuildConfig.APPLICATION_ID}_prefs"
             gsApp.getSharedPreferences(
                 prefsFile,
                 Context.MODE_PRIVATE
             )
         }

    }


    val isStarted : Boolean
    get() = pref.getBoolean(START, false)

    val getLat : Double
    get() = pref.getFloat(LATITUDE, 40.7128F).toDouble()

    val getLng : Double
    get() = pref.getFloat(LONGITUDE, -74.0060F).toDouble()

    var isHookSystem : Boolean
    get() = pref.getBoolean(HOOKED_SYSTEM, false)
    set(value) { pref.edit().putBoolean(HOOKED_SYSTEM,value).apply() }

    var movementMode: String
    get() = pref.getString(MOVEMENT_MODE, MODE_SMALL) ?: MODE_SMALL
    set(value) { pref.edit().putString(MOVEMENT_MODE, value).apply() }

    var movementRadius : String?
    get() = pref.getString(MOVEMENT_RADIUS, "3")
    set(value) { pref.edit().putString(MOVEMENT_RADIUS, value).apply() }

    var movementSpeed : String?
    get() = pref.getString(MOVEMENT_SPEED, "0.3")
    set(value) { pref.edit().putString(MOVEMENT_SPEED, value).apply() }

    var accuracy : String?
    get() = pref.getString(ACCURACY_SETTING,"10")
    set(value) { pref.edit().putString(ACCURACY_SETTING,value).apply()}

    var mapType : Int
    get() = pref.getInt(MAP_TYPE,1)
    set(value) { pref.edit().putInt(MAP_TYPE,value).apply()}

    var darkTheme: Int
        get() = pref.getInt(DARK_THEME, DayNightDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = pref.edit().putInt(DARK_THEME, value).apply()

    var disableUpdate: Boolean
        get() = pref.getBoolean(DISABLE_UPDATE, false)
        set(value) = pref.edit().putBoolean(DISABLE_UPDATE, value).apply()

    var hideFromApps: Boolean
        get() = pref.getBoolean(HIDE_FROM_APPS, true)
        set(value) {
            pref.edit().putBoolean(HIDE_FROM_APPS, value).apply()
            runInBackground { makeWorldReadable() }
        }



    fun update(start:Boolean, la: Double, ln: Double) {
        runInBackground {
            val prefEditor = pref.edit()
            prefEditor.putFloat(LATITUDE, la.toFloat())
            prefEditor.putFloat(LONGITUDE, ln.toFloat())
            prefEditor.putBoolean(START, start)
            prefEditor.apply()
            makeWorldReadable()
        }

    }


    /**
     *  Make the redirected prefs file world readable ourselves - fixes a bug in Ed/lsposed
     *
     *  This requires the XSharedPreferences file path, which we get via a self hook. It does nothing
     *  when the Xposed module is not enabled.
     */
    @SuppressLint("SetWorldReadable")
    private fun makeWorldReadable(){
        EnvCheck.getCachedPath().let {
            if(it.isNotEmpty()){
                File(it).setReadable(true, false)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun runInBackground(method: suspend () -> Unit){
        GlobalScope.launch(Dispatchers.IO) {
            method.invoke()
        }
    }





}