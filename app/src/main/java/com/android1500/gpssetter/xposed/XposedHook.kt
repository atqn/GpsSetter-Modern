package com.android1500.gpssetter.xposed

import android.app.AndroidAppHelper
import android.app.PendingIntent
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import com.android1500.gpssetter.BuildConfig
import com.android1500.gpssetter.gsApp
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass
import timber.log.Timber
import java.util.Random
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

class XposedHook : IXposedHookLoadPackage {

    companion object {
        @Volatile var newlat: Double = 40.7128
        @Volatile var newlng: Double = -74.0060
        @Volatile var dynamicAccuracy: Float = 10f
        @Volatile var dynamicSpeed: Float = 0f
        @Volatile var dynamicBearing: Float = 0f

        private const val EARTH_RADIUS = 6378137.0
        private const val DEG_PER_RAD = 180.0 / Math.PI
        private const val UPDATE_INTERVAL_MS = 1000L

        private val rand: Random = Random()
        private val settings = Xshare()

        @Volatile private var mLastUpdated: Long = 0L

        // Random-walk state (offsets in meters relative to anchor)
        private var dx: Double = 0.0
        private var dy: Double = 0.0
        private var vx: Double = 0.0
        private var vy: Double = 0.0
        private var idleUntilMs: Long = 0L
        private var lastAnchorLat: Double = Double.NaN
        private var lastAnchorLng: Double = Double.NaN

        private const val SHARED_PREFS_FILENAME = "${BuildConfig.APPLICATION_ID}_prefs"
    }

    private val context by lazy { AndroidAppHelper.currentApplication() as Context }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        if (lpparam == null) return

        if (lpparam.packageName == BuildConfig.APPLICATION_ID) {
            setupSelfHooks(lpparam.classLoader)
        }

        if (settings.isHookedSystem && lpparam.packageName == "android") {
            hookSystemLocationService(lpparam)
        }

        hookLocationGetters(lpparam)
        hookLocationSet(lpparam)
    }

    private fun hookSystemLocationService(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("GS: hooking system_server LocationManagerService")

        val getLastLocationSignatures = arrayOf(
            arrayOf("android.location.LocationRequest", String::class.java),
            arrayOf("android.location.LocationRequest", String::class.java, String::class.java),
            arrayOf("android.location.LocationRequest", String::class.java, String::class.java, String::class.java)
        )

        var hookedAny = false
        val classPaths = arrayOf(
            "com.android.server.location.LocationManagerService",
            "com.android.server.LocationManagerService"
        )
        for (classPath in classPaths) {
            for (sig in getLastLocationSignatures) {
                try {
                    val args = sig + arrayOf(object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (settings.isStarted) {
                                param.result = buildFakeLocation(LocationManager.GPS_PROVIDER)
                            }
                        }
                    })
                    XposedHelpers.findAndHookMethod(classPath, lpparam.classLoader, "getLastLocation", *args)
                    hookedAny = true
                } catch (_: Throwable) {
                    // try next signature / class path
                }
            }
        }
        if (!hookedAny) {
            XposedBridge.log("GS: no getLastLocation signature matched")
        }

        try {
            XposedHelpers.findAndHookMethod(
                "com.android.server.LocationManagerService",
                lpparam.classLoader,
                "requestLocationUpdates",
                "android.location.LocationRequest",
                "android.location.ILocationListener",
                PendingIntent::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!settings.isStarted) return
                        val listener = param.args[1] ?: return
                        val location = buildFakeLocation(LocationManager.GPS_PROVIDER)
                        gsApp.globalScope.launch(Dispatchers.IO) {
                            try {
                                XposedHelpers.callMethod(listener, "onLocationChanged", location)
                            } catch (_: Throwable) { }
                        }
                    }
                }
            )
        } catch (_: Throwable) { /* signature not present on this Android version */ }
    }

    private fun hookLocationGetters(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName ?: return

        XposedHelpers.findAndHookMethod(Location::class.java, "getLatitude", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                maybeAdvanceSimulation()
                if (settings.isStarted && packageName != BuildConfig.APPLICATION_ID) {
                    param.result = newlat
                }
            }
        })

        XposedHelpers.findAndHookMethod(Location::class.java, "getLongitude", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                maybeAdvanceSimulation()
                if (settings.isStarted && packageName != BuildConfig.APPLICATION_ID) {
                    param.result = newlng
                }
            }
        })

        XposedHelpers.findAndHookMethod(Location::class.java, "getAccuracy", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                maybeAdvanceSimulation()
                if (settings.isStarted && packageName != BuildConfig.APPLICATION_ID) {
                    param.result = dynamicAccuracy
                }
            }
        })

        XposedHelpers.findAndHookMethod(Location::class.java, "getSpeed", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (settings.isStarted && packageName != BuildConfig.APPLICATION_ID) {
                    param.result = dynamicSpeed
                }
            }
        })

        XposedHelpers.findAndHookMethod(Location::class.java, "getBearing", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (settings.isStarted && packageName != BuildConfig.APPLICATION_ID) {
                    param.result = dynamicBearing
                }
            }
        })
    }

    private fun hookLocationSet(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName ?: return

        XposedHelpers.findAndHookMethod(
            Location::class.java, "set", Location::class.java,
            object : XC_MethodHook() {
                @RequiresApi(Build.VERSION_CODES.P)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    maybeAdvanceSimulation()
                    if (!settings.isStarted || packageName == BuildConfig.APPLICATION_ID) return

                    val origin = param.args[0] as? Location
                    val location: Location
                    if (origin == null) {
                        location = Location(LocationManager.GPS_PROVIDER)
                        location.time = System.currentTimeMillis() - rand.nextInt(900) - 100
                    } else {
                        location = Location(origin.provider)
                        location.time = origin.time
                        location.bearingAccuracyDegrees = origin.bearingAccuracyDegrees
                        location.elapsedRealtimeNanos = origin.elapsedRealtimeNanos
                        location.verticalAccuracyMeters = origin.verticalAccuracyMeters
                    }
                    location.latitude = newlat
                    location.longitude = newlng
                    location.altitude = realisticAltitude()
                    location.speed = dynamicSpeed
                    location.bearing = dynamicBearing
                    location.accuracy = dynamicAccuracy
                    location.speedAccuracyMetersPerSecond = 0.5f

                    try {
                        HiddenApiBypass.invoke(location.javaClass, location, "setIsFromMockProvider", false)
                    } catch (_: Throwable) { }
                    param.args[0] = location
                }
            }
        )
    }

    private fun buildFakeLocation(provider: String): Location {
        maybeAdvanceSimulation()
        val l = Location(provider)
        l.time = System.currentTimeMillis() - rand.nextInt(900) - 100
        l.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        l.latitude = newlat
        l.longitude = newlng
        l.altitude = realisticAltitude()
        l.accuracy = dynamicAccuracy
        l.speed = dynamicSpeed
        l.bearing = dynamicBearing
        l.speedAccuracyMetersPerSecond = 0.5f
        try {
            HiddenApiBypass.invoke(l.javaClass, l, "setIsFromMockProvider", false)
        } catch (_: Throwable) { }
        return l
    }

    private fun realisticAltitude(): Double = 10.0 + (rand.nextDouble() - 0.5) * 4.0

    private fun maybeAdvanceSimulation() {
        val now = System.currentTimeMillis()
        if (now - mLastUpdated < UPDATE_INTERVAL_MS) return
        synchronized(this::class.java) {
            val now2 = System.currentTimeMillis()
            val elapsed = now2 - mLastUpdated
            if (elapsed < UPDATE_INTERVAL_MS) return
            try {
                advanceSimulation(if (mLastUpdated == 0L) 1.0 else elapsed / 1000.0)
            } catch (e: Throwable) {
                Timber.tag("GPS Setter").e(e, "advanceSimulation failed")
            }
            mLastUpdated = now2
        }
    }

    private fun advanceSimulation(deltaSeconds: Double) {
        val anchorLat = settings.getLat
        val anchorLng = settings.getLng
        val movementEnabled = settings.isMovementEnabled
        val radius = settings.effectiveRadius.toDouble().coerceIn(0.5, 1000.0)
        val maxSpeed = settings.effectiveMaxSpeed.toDouble().coerceIn(0.05, 50.0)
        val baseAccuracy = settings.effectiveAccuracy.coerceAtLeast(1f)

        if (lastAnchorLat != anchorLat || lastAnchorLng != anchorLng) {
            dx = 0.0; dy = 0.0; vx = 0.0; vy = 0.0
            idleUntilMs = 0L
            lastAnchorLat = anchorLat
            lastAnchorLng = anchorLng
        }

        if (!movementEnabled) {
            newlat = anchorLat
            newlng = anchorLng
            dynamicSpeed = 0f
            dynamicBearing = 0f
            dynamicAccuracy = baseAccuracy * (0.9f + rand.nextFloat() * 0.2f)
            return
        }

        val now = System.currentTimeMillis()
        if (now < idleUntilMs) {
            vx *= 0.5
            vy *= 0.5
        } else {
            if (idleUntilMs == 0L && rand.nextFloat() < 0.6f) {
                idleUntilMs = now + 2000L + rand.nextInt(4000)
                vx *= 0.3
                vy *= 0.3
            } else {
                idleUntilMs = 0L
                val accelScale = maxSpeed * 0.8
                vx += (rand.nextDouble() - 0.5) * 2.0 * accelScale * deltaSeconds
                vy += (rand.nextDouble() - 0.5) * 2.0 * accelScale * deltaSeconds
            }
        }

        val dist = sqrt(dx * dx + dy * dy)
        if (dist > radius * 0.8 && dist > 0.0) {
            val pull = (dist - radius * 0.8) / (radius * 0.2 + 1e-6)
            vx -= dx / dist * pull * maxSpeed
            vy -= dy / dist * pull * maxSpeed
        }

        val speed = sqrt(vx * vx + vy * vy)
        if (speed > maxSpeed) {
            vx *= maxSpeed / speed
            vy *= maxSpeed / speed
        }

        dx += vx * deltaSeconds
        dy += vy * deltaSeconds

        val newDist = sqrt(dx * dx + dy * dy)
        if (newDist > radius) {
            dx *= radius / newDist
            dy *= radius / newDist
            vx = -vx * 0.5
            vy = -vy * 0.5
        }

        val cosLat = max(0.01, cos(anchorLat / DEG_PER_RAD))
        val dlat = dy / EARTH_RADIUS * DEG_PER_RAD
        val dlng = dx / (EARTH_RADIUS * cosLat) * DEG_PER_RAD
        newlat = anchorLat + dlat
        newlng = anchorLng + dlng

        val speedNow = sqrt(vx * vx + vy * vy).toFloat()
        dynamicSpeed = speedNow
        if (speedNow > 0.05f) {
            val deg = Math.toDegrees(Math.atan2(vx, vy)).toFloat()
            dynamicBearing = ((deg % 360f) + 360f) % 360f
        }

        dynamicAccuracy = baseAccuracy * (0.85f + rand.nextFloat() * 0.3f)
    }

    private fun setupSelfHooks(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.android1500.gpssetter.selfhook.EnvCheck",
                classLoader, "getCheckedState",
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any = true
                }
            )
        } catch (_: Throwable) { }
        try {
            XposedHelpers.findAndHookMethod(
                "com.android1500.gpssetter.selfhook.EnvCheck",
                classLoader, "getCachedPath",
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        return XSharedPreferences(BuildConfig.APPLICATION_ID, SHARED_PREFS_FILENAME).file.absolutePath
                    }
                }
            )
        } catch (_: Throwable) { }
    }
}
