package com.android1500.gpssetter.xposed

import java.util.Random

/**
 * Fabricates a plausible, slowly-evolving GNSS constellation so a spoofed
 * fix is internally consistent.
 *
 * A real device holding a good fix reports a handful of tracked satellites
 * with believable C/N0, azimuth and elevation. A valid-looking fix that is
 * backed by *zero* satellites is one of the clearest spoofing tells, so when
 * the module is active we feed any [android.location.GnssStatus] consumer a
 * synthetic constellation instead.
 *
 * The set is regenerated on a slow cadence ([REGEN_INTERVAL_MS]) so repeated
 * reads inside a short window stay stable, while the constellation still
 * drifts over time the way a real one does. C/N0 carries a small per-read
 * jitter so signal strengths never look frozen.
 */
object GnssSim {

    private const val REGEN_INTERVAL_MS = 30_000L

    // android.location.GnssStatus.CONSTELLATION_* — inlined so this helper has
    // no compile-time dependency on a particular API level.
    private const val GPS = 1
    private const val GLONASS = 3
    private const val BEIDOU = 5
    private const val GALILEO = 6

    private data class Sat(
        val svid: Int,
        val constellation: Int,
        val azimuth: Float,
        val elevation: Float,
        val cn0: Float,
        val usedInFix: Boolean
    )

    private val rand = Random()

    @Volatile private var sats: Array<Sat> = emptyArray()
    @Volatile private var generatedAt: Long = 0L

    /** Number of satellites currently reported. */
    val count: Int
        get() {
            ensureFresh()
            return sats.size
        }

    private fun ensureFresh() {
        val now = System.currentTimeMillis()
        if (sats.isNotEmpty() && now - generatedAt < REGEN_INTERVAL_MS) return
        synchronized(this) {
            val now2 = System.currentTimeMillis()
            if (sats.isNotEmpty() && now2 - generatedAt < REGEN_INTERVAL_MS) return
            sats = generate()
            generatedAt = now2
        }
    }

    private fun generate(): Array<Sat> {
        // 9–14 tracked satellites spread across a few constellations — typical
        // of a modern multi-band receiver with a reasonably open sky view.
        val total = 9 + rand.nextInt(6)
        val used = HashSet<Long>()
        val result = ArrayList<Sat>(total)
        for (i in 0 until total) {
            val constellation = when (rand.nextInt(10)) {
                in 0..4 -> GPS
                in 5..6 -> GALILEO
                in 7..8 -> GLONASS
                else -> BEIDOU
            }
            val svid = randomSvid(constellation, used)
            val elevation = 5f + rand.nextFloat() * 80f
            val azimuth = rand.nextFloat() * 360f
            // Higher birds generally come in stronger.
            val cn0Base = 18f + (elevation / 85f) * 22f
            val cn0 = (cn0Base + (rand.nextFloat() - 0.5f) * 6f).coerceIn(12f, 46f)
            val usedInFix = elevation > 15f && rand.nextFloat() < 0.8f
            result.add(Sat(svid, constellation, azimuth, elevation, cn0, usedInFix))
        }
        return result.toTypedArray()
    }

    private fun randomSvid(constellation: Int, used: HashSet<Long>): Int {
        val range = when (constellation) {
            GPS -> 1..32
            GLONASS -> 1..24
            GALILEO -> 1..36
            BEIDOU -> 1..37
            else -> 1..32
        }
        repeat(20) {
            val svid = range.first + rand.nextInt(range.last - range.first + 1)
            if (used.add(constellation.toLong() * 1000 + svid)) return svid
        }
        return range.first
    }

    private fun satAt(index: Int): Sat? {
        ensureFresh()
        val current = sats
        return current.getOrNull(index)
    }

    fun svid(index: Int): Int = satAt(index)?.svid ?: 0
    fun constellationType(index: Int): Int = satAt(index)?.constellation ?: GPS
    fun azimuth(index: Int): Float = satAt(index)?.azimuth ?: 0f
    fun elevation(index: Int): Float = satAt(index)?.elevation ?: 0f
    fun usedInFix(index: Int): Boolean = satAt(index)?.usedInFix ?: false

    /** Live C/N0 with a touch of per-read jitter so it never looks frozen. */
    fun cn0(index: Int): Float {
        val s = satAt(index) ?: return 0f
        return (s.cn0 + (rand.nextFloat() - 0.5f) * 1.5f).coerceIn(10f, 48f)
    }
}
