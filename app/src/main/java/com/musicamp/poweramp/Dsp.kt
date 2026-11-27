package com.musicamp.poweramp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Filter types for the parametric equalizer (same set AutoEQ exports). */
enum class FilterType(val label: String, val autoEqCode: String) {
    PEAK("Peak", "PK"),
    LOW_SHELF("Low shelf", "LSC"),
    HIGH_SHELF("High shelf", "HSC"),
    LOW_PASS("Low pass", "LPQ"),
    HIGH_PASS("High pass", "HPQ"),
    BAND_PASS("Band pass", "BP");

    companion object {
        fun fromAutoEq(code: String): FilterType? = when (code.uppercase()) {
            "PK", "PEQ", "PEAK" -> PEAK
            "LS", "LSC", "LOWSHELF" -> LOW_SHELF
            "HS", "HSC", "HIGHSHELF" -> HIGH_SHELF
            "LP", "LPQ" -> LOW_PASS
            "HP", "HPQ" -> HIGH_PASS
            "BP", "BPF", "BANDPASS" -> BAND_PASS
            else -> null
        }
    }
}

enum class Channel(val label: String) {
    BOTH("L+R"),
    LEFT("L"),
    RIGHT("R")
}

/** One band of the parametric equalizer. */
data class ParametricBand(
    val type: FilterType = FilterType.PEAK,
    val freqHz: Float = 1000f, // 20..20000
    val gainDb: Float = 0f,    // -24..24 (ignored by pass filters)
    val q: Float = 0.71f,      // 0.1..10
    val enabled: Boolean = true,
    val channel: Channel = Channel.BOTH
)

/**
 * Immutable snapshot of every DSP setting. The controller builds a new one whenever a knob moves and
 * hands it to the running engines; each engine rebuilds its filters when the snapshot changes.
 */
data class DspConfig(
    val bypass: Boolean = false,              // bit-perfect: no processing at all
    val preampDb: Float = 0f,
    val graphicEnabled: Boolean = false,
    val graphicGainsDb: List<Float> = List(10) { 0f },
    val graphicFreqsHz: List<Float> = ISO_10_BAND_HZ,
    val parametricEnabled: Boolean = false,
    val parametricBands: List<ParametricBand> = emptyList(),
    val bassDb: Float = 0f,
    val trebleDb: Float = 0f,
    val limiter: Boolean = true,
    val mono: Boolean = false,
    val stereoWidth: Float = 1f,              // 1 = unchanged, 2 = widest
    val reverb: ReverbParams? = null,
    val volumeDb: Float = 0f,                 // DVC software volume
    val ditherTo16: Boolean = true
)

data class ReverbParams(
    val size: Float, val damp: Float, val filter: Float, val fade: Float,
    val preDelay: Float, val preDelayMix: Float, val mix: Float
)

val ISO_5_BAND_HZ = listOf(60f, 230f, 910f, 3600f, 14000f)
val ISO_10_BAND_HZ = listOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
val ISO_15_BAND_HZ = listOf(25f, 40f, 63f, 100f, 160f, 250f, 400f, 630f, 1000f, 1600f, 2500f, 4000f, 6300f, 10000f, 16000f)
val ISO_31_BAND_HZ = listOf(
    20f, 25f, 31.5f, 40f, 50f, 63f, 80f, 100f, 125f, 160f,
    200f, 250f, 315f, 400f, 500f, 630f, 800f, 1000f, 1250f, 1600f,
    2000f, 2500f, 3150f, 4000f, 5000f, 6300f, 8000f, 10000f, 12500f, 16000f, 20000f
)
val GRAPHIC_BAND_HZ = ISO_10_BAND_HZ.toFloatArray()

/** Log-spaced frequencies from [startHz] to [endHz] for custom N-band graphic EQ (5..32). */
fun logSpacedBands(count: Int, startHz: Float = 31f, endHz: Float = 16000f): List<Float> {
    val n = count.coerceIn(5, 32)
    val lo = startHz.coerceIn(20f, 500f)
    val hi = endHz.coerceIn(lo * 2f, 20000f)
    if (n == 1) return listOf(lo)
    val logLo = ln(lo.toDouble())
    val logHi = ln(hi.toDouble())
    return List(n) { i ->
        exp(logLo + (logHi - logLo) * i / (n - 1)).toFloat()
    }
}

fun isoOrCustomBands(count: Int, startHz: Float = 31f, endHz: Float = 16000f): List<Float> {
    val defaultSpan = abs(startHz - 31f) < 1f && abs(endHz - 16000f) < 1f
    if (defaultSpan) {
        when (count) {
            5 -> return ISO_5_BAND_HZ
            10 -> return ISO_10_BAND_HZ
            15 -> return ISO_15_BAND_HZ
            31 -> return ISO_31_BAND_HZ
        }
    }
    return logSpacedBands(count, startHz, endHz)
}

fun formatFreqLabel(hz: Float): String = when {
    hz >= 1000f -> {
        val k = hz / 1000f
        if (abs(k - k.roundToInt()) < 0.05f) "${k.roundToInt()}K" else "%.1fK".format(k)
    }
    else -> hz.roundToInt().toString()
}

/** Log-frequency linear interpolation of an EQ curve onto [dstFreqs]. */
fun resampleEqCurve(srcFreqs: List<Float>, srcGains: List<Float>, dstFreqs: List<Float>): List<Float> {
    if (srcFreqs.isEmpty() || srcGains.isEmpty()) return List(dstFreqs.size) { 0f }
    val n = minOf(srcFreqs.size, srcGains.size)
    if (n == 1) return List(dstFreqs.size) { srcGains[0] }
    return dstFreqs.map { hz ->
        if (hz <= srcFreqs[0]) return@map srcGains[0]
        if (hz >= srcFreqs[n - 1]) return@map srcGains[n - 1]
        var lo = 0
        while (lo < n - 2 && srcFreqs[lo + 1] < hz) lo++
        val f0 = srcFreqs[lo].coerceAtLeast(1f)
        val f1 = srcFreqs[lo + 1].coerceAtLeast(f0 + 0.01f)
        val t = (ln(hz / f0) / ln(f1 / f0)).coerceIn(0f, 1f)
        srcGains[lo] * (1f - t) + srcGains[lo + 1] * t
    }
}

/** Smooth-summing Q for graphic EQ band [index]: Q = sqrt(2^bw) / (2^bw - 1). */
fun graphicBandQ(freqs: List<Float>, index: Int): Float {
    if (freqs.size < 2) return 1.41f
    val bw = when {
        index <= 0 -> ln((freqs[1] / freqs[0]).coerceAtLeast(1.05f)) / ln(2f)
        index >= freqs.lastIndex -> ln((freqs[index] / freqs[index - 1]).coerceAtLeast(1.05f)) / ln(2f)
        else -> ln((freqs[index + 1] / freqs[index - 1]).coerceAtLeast(1.1f)) / (2f * ln(2f))
    }.coerceIn(0.15f, 3.0f)
    val twoBw = 2.0.pow(bw.toDouble())
    return (sqrt(twoBw) / (twoBw - 1.0)).toFloat().coerceIn(0.5f, 8.0f)
}

/** RBJ cookbook biquad, transposed direct form II, stereo state. */
class Biquad {
    private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0; private var a1 = 0.0; private var a2 = 0.0
    private var z1L = 0.0; private var z2L = 0.0; private var z1R = 0.0; private var z2R = 0.0

    fun set(type: FilterType, fs: Int, freq: Float, gainDb: Float, q: Float) {
        val f = freq.coerceIn(10f, fs * 0.49f).toDouble()
        val w0 = 2.0 * PI * f / fs
        val cw = cos(w0); val sw = sin(w0)
        val qq = q.coerceIn(0.05f, 20f).toDouble()
        val alpha = sw / (2.0 * qq)
        val a = 10.0.pow(gainDb / 40.0)
        var nb0: Double; var nb1: Double; var nb2: Double; var na0: Double; var na1: Double; var na2: Double
        when (type) {
            FilterType.PEAK -> {
                nb0 = 1 + alpha * a; nb1 = -2 * cw; nb2 = 1 - alpha * a
                na0 = 1 + alpha / a; na1 = -2 * cw; na2 = 1 - alpha / a
            }
            FilterType.LOW_SHELF -> {
                val s = 2 * sqrt(a) * alpha
                nb0 = a * ((a + 1) - (a - 1) * cw + s); nb1 = 2 * a * ((a - 1) - (a + 1) * cw); nb2 = a * ((a + 1) - (a - 1) * cw - s)
                na0 = (a + 1) + (a - 1) * cw + s; na1 = -2 * ((a - 1) + (a + 1) * cw); na2 = (a + 1) + (a - 1) * cw - s
            }
            FilterType.HIGH_SHELF -> {
                val s = 2 * sqrt(a) * alpha
                nb0 = a * ((a + 1) + (a - 1) * cw + s); nb1 = -2 * a * ((a - 1) + (a + 1) * cw); nb2 = a * ((a + 1) + (a - 1) * cw - s)
                na0 = (a + 1) - (a - 1) * cw + s; na1 = 2 * ((a - 1) - (a + 1) * cw); na2 = (a + 1) - (a - 1) * cw - s
            }
            FilterType.LOW_PASS -> {
                nb0 = (1 - cw) / 2; nb1 = 1 - cw; nb2 = (1 - cw) / 2
                na0 = 1 + alpha; na1 = -2 * cw; na2 = 1 - alpha
            }
            FilterType.HIGH_PASS -> {
                nb0 = (1 + cw) / 2; nb1 = -(1 + cw); nb2 = (1 + cw) / 2
                na0 = 1 + alpha; na1 = -2 * cw; na2 = 1 - alpha
            }
            FilterType.BAND_PASS -> {
                // RBJ cookbook constant 0 dB peak gain band-pass filter
                nb0 = alpha; nb1 = 0.0; nb2 = -alpha
                na0 = 1 + alpha; na1 = -2 * cw; na2 = 1 - alpha
            }
        }
        b0 = nb0 / na0; b1 = nb1 / na0; b2 = nb2 / na0; a1 = na1 / na0; a2 = na2 / na0
    }

    fun processStereo(buf: FloatArray, frames: Int) {
        var l1 = z1L; var l2 = z2L; var r1 = z1R; var r2 = z2R
        var i = 0
        for (n in 0 until frames) {
            val xl = buf[i].toDouble()
            val yl = b0 * xl + l1
            l1 = b1 * xl - a1 * yl + l2
            l2 = b2 * xl - a2 * yl
            buf[i] = yl.toFloat()
            val xr = buf[i + 1].toDouble()
            val yr = b0 * xr + r1
            r1 = b1 * xr - a1 * yr + r2
            r2 = b2 * xr - a2 * yr
            buf[i + 1] = yr.toFloat()
            i += 2
        }
        // Flush denormals
        z1L = if (abs(l1) < 1e-20) 0.0 else l1; z2L = if (abs(l2) < 1e-20) 0.0 else l2
        z1R = if (abs(r1) < 1e-20) 0.0 else r1; z2R = if (abs(r2) < 1e-20) 0.0 else r2
    }

    fun processLeft(buf: FloatArray, frames: Int) {
        var l1 = z1L; var l2 = z2L
        var i = 0
        for (n in 0 until frames) {
            val xl = buf[i].toDouble()
            val yl = b0 * xl + l1
            l1 = b1 * xl - a1 * yl + l2
            l2 = b2 * xl - a2 * yl
            buf[i] = yl.toFloat()
            i += 2
        }
        z1L = if (abs(l1) < 1e-20) 0.0 else l1; z2L = if (abs(l2) < 1e-20) 0.0 else l2
    }

    fun processRight(buf: FloatArray, frames: Int) {
        var r1 = z1R; var r2 = z2R
        var i = 1
        for (n in 0 until frames) {
            val xr = buf[i].toDouble()
            val yr = b0 * xr + r1
            r1 = b1 * xr - a1 * yr + r2
            r2 = b2 * xr - a2 * yr
            buf[i] = yr.toFloat()
            i += 2
        }
        z1R = if (abs(r1) < 1e-20) 0.0 else r1; z2R = if (abs(r2) < 1e-20) 0.0 else r2
    }
}

/** Freeverb-style reverb (8 combs + 4 allpasses per channel) with pre-delay. */
class Reverb(fs: Int) {
    private val scale = fs / 44100f
    private val combTuning = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
    private val apTuning = intArrayOf(556, 441, 341, 225)
    private val spread = 23
    private val combL = combTuning.map { FloatArray((it * scale).toInt().coerceAtLeast(1)) }
    private val combR = combTuning.map { FloatArray(((it + spread) * scale).toInt().coerceAtLeast(1)) }
    private val combIdxL = IntArray(8); private val combIdxR = IntArray(8)
    private val combStoreL = FloatArray(8); private val combStoreR = FloatArray(8)
    private val apL = apTuning.map { FloatArray((it * scale).toInt().coerceAtLeast(1)) }
    private val apR = apTuning.map { FloatArray(((it + spread) * scale).toInt().coerceAtLeast(1)) }
    private val apIdxL = IntArray(4); private val apIdxR = IntArray(4)
    private val preBuf = FloatArray((fs * 0.25f).toInt() * 2 + 2)
    private var preIdx = 0
    private var feedback = 0.84f; private var damp = 0.2f; private var wet = 0.3f; private var dry = 1f
    private var preDelayFrames = 0; private var preMix = 0f
    private var toneCoef = 0f; private var toneL = 0f; private var toneR = 0f
    private val fsF = fs.toFloat()

    fun configure(p: ReverbParams) {
        // size + fade -> decay time, damp -> HF damping in the tank, filter -> tone of the wet signal
        feedback = (0.70f + 0.28f * (0.6f * p.size + 0.4f * p.fade)).coerceIn(0.5f, 0.985f)
        damp = (p.damp * 0.8f).coerceIn(0f, 0.95f)
        wet = (p.mix * 0.6f).coerceIn(0f, 1f)
        dry = 1f - p.mix * 0.35f
        preDelayFrames = (p.preDelay * 0.2f * fsF).toInt().coerceIn(0, preBuf.size / 2 - 1)
        preMix = p.preDelayMix.coerceIn(0f, 1f)
        val cutoff = 20000f - p.filter * 17000f
        toneCoef = exp(-2f * PI.toFloat() * cutoff / fsF)
    }

    fun process(buf: FloatArray, frames: Int) {
        val half = preBuf.size / 2
        var i = 0
        for (n in 0 until frames) {
            val inL = buf[i]; val inR = buf[i + 1]
            preBuf[preIdx * 2] = inL; preBuf[preIdx * 2 + 1] = inR
            val rd = (preIdx - preDelayFrames + half) % half
            val dl = preBuf[rd * 2]; val dr = preBuf[rd * 2 + 1]
            preIdx = (preIdx + 1) % half
            val input = ((dl + dr) * (0.5f + 0.5f * preMix) + (inL + inR) * (0.5f - 0.5f * preMix)) * 0.015f
            var outL = 0f; var outR = 0f
            for (c in 0 until 8) {
                val bl = combL[c]; val il = combIdxL[c]
                val yl = bl[il]
                combStoreL[c] = yl * (1 - damp) + combStoreL[c] * damp
                bl[il] = input + combStoreL[c] * feedback
                combIdxL[c] = if (il + 1 >= bl.size) 0 else il + 1
                outL += yl
                val br = combR[c]; val ir = combIdxR[c]
                val yr = br[ir]
                combStoreR[c] = yr * (1 - damp) + combStoreR[c] * damp
                br[ir] = input + combStoreR[c] * feedback
                combIdxR[c] = if (ir + 1 >= br.size) 0 else ir + 1
                outR += yr
            }
            for (a in 0 until 4) {
                val bl = apL[a]; val il = apIdxL[a]
                val vl = bl[il]; bl[il] = outL + vl * 0.5f; outL = vl - outL
                apIdxL[a] = if (il + 1 >= bl.size) 0 else il + 1
                val br = apR[a]; val ir = apIdxR[a]
                val vr = br[ir]; br[ir] = outR + vr * 0.5f; outR = vr - outR
                apIdxR[a] = if (ir + 1 >= br.size) 0 else ir + 1
            }
            toneL = outL + (toneL - outL) * toneCoef
            toneR = outR + (toneR - outR) * toneCoef
            buf[i] = inL * dry + toneL * wet * 3f
            buf[i + 1] = inR * dry + toneR * wet * 3f
            i += 2
        }
    }
}

/**
 * Per-engine DSP state. Processes interleaved stereo float in place.
 * Order: preamp/ReplayGain -> graphic EQ -> parametric EQ -> tone -> stereo width / mono ->
 * reverb -> volume & balance (smoothed) -> limiter.
 */
class DspProcessor(private val fs: Int) {
    private var config: DspConfig? = null
    private var graphic: Array<Biquad> = Array(10) { Biquad() }
    private var graphicActive = BooleanArray(10)
    private var parametric: List<Pair<Biquad, Channel>> = emptyList()
    private val bass = Biquad(); private val treble = Biquad()
    private var reverb: Reverb? = null
    private var staticGain = 1f
    private var targetL = 1f; private var targetR = 1f
    private var curL = 1f; private var curR = 1f
    private var limGain = 1f

    /** Gain that does not depend on the DSP snapshot (ReplayGain), in dB. */
    @Volatile var trackGainDb = 0f
    /** Player volume (balance, crossfade, ducking), linear 0..1, set from the controller thread. */
    @Volatile var volL = 1f
    @Volatile var volR = 1f
    /** True when the limiter reduced gain (< 0.99) in the last processed buffer. */
    @Volatile var limiting = false
    /** Peak linear amplitude seen in the last processed buffer (0..1+). */
    @Volatile var peakLevel = 0f

    fun update(c: DspConfig) {
        if (c == config) return
        val old = config
        config = c
        if (old == null || old.graphicGainsDb != c.graphicGainsDb || old.graphicFreqsHz != c.graphicFreqsHz || old.graphicEnabled != c.graphicEnabled) {
            val freqs = c.graphicFreqsHz.ifEmpty { ISO_10_BAND_HZ }
            if (graphic.size != freqs.size) {
                graphic = Array(freqs.size) { Biquad() }
                graphicActive = BooleanArray(freqs.size)
            }
            for (b in freqs.indices) {
                val g = if (c.graphicEnabled) c.graphicGainsDb.getOrElse(b) { 0f } else 0f
                graphicActive[b] = abs(g) > 0.01f
                graphic[b].set(FilterType.PEAK, fs, freqs[b], g, graphicBandQ(freqs, b))
            }
        }
        if (old == null || old.parametricBands != c.parametricBands || old.parametricEnabled != c.parametricEnabled) {
            parametric = if (c.parametricEnabled) c.parametricBands.filter { it.enabled }.map { pb ->
                Biquad().also { it.set(pb.type, fs, pb.freqHz, pb.gainDb, pb.q) } to pb.channel
            } else emptyList()
        }
        if (old == null || old.bassDb != c.bassDb) bass.set(FilterType.LOW_SHELF, fs, 150f, c.bassDb, 0.71f)
        if (old == null || old.trebleDb != c.trebleDb) treble.set(FilterType.HIGH_SHELF, fs, 3000f, c.trebleDb, 0.71f)
        if (c.reverb != null) {
            val r = reverb ?: Reverb(fs).also { reverb = it }
            r.configure(c.reverb)
        } else {
            reverb = null
        }
    }

    fun process(buf: FloatArray, frames: Int) {
        val c = config ?: DspConfig()
        if (c.bypass) {
            limiting = false
            peakLevel = 0f
            return
        }
        staticGain = dbToLin(c.preampDb + trackGainDb)
        if (staticGain != 1f) for (i in 0 until frames * 2) buf[i] *= staticGain
        if (c.graphicEnabled) for (b in graphic.indices) if (graphicActive[b]) graphic[b].processStereo(buf, frames)
        for ((bq, ch) in parametric) {
            when (ch) {
                Channel.BOTH -> bq.processStereo(buf, frames)
                Channel.LEFT -> bq.processLeft(buf, frames)
                Channel.RIGHT -> bq.processRight(buf, frames)
            }
        }
        if (abs(c.bassDb) > 0.01f) bass.processStereo(buf, frames)
        if (abs(c.trebleDb) > 0.01f) treble.processStereo(buf, frames)
        if (c.mono) {
            var i = 0
            for (n in 0 until frames) { val m = (buf[i] + buf[i + 1]) * 0.5f; buf[i] = m; buf[i + 1] = m; i += 2 }
        } else if (abs(c.stereoWidth - 1f) > 0.01f) {
            val w = c.stereoWidth
            var i = 0
            for (n in 0 until frames) {
                val m = (buf[i] + buf[i + 1]) * 0.5f; val s = (buf[i] - buf[i + 1]) * 0.5f * w
                buf[i] = m + s; buf[i + 1] = m - s; i += 2
            }
        }
        reverb?.process(buf, frames)
        // Volume, balance, crossfade: per-sample smoothing avoids zipper noise
        val vol = dbToLin(c.volumeDb)
        targetL = volL * vol; targetR = volR * vol
        val step = 1f / (fs * 0.02f)
        var i = 0
        for (n in 0 until frames) {
            curL += (targetL - curL) * step * 4f
            curR += (targetR - curR) * step * 4f
            buf[i] *= curL; buf[i + 1] *= curR
            i += 2
        }
        if (c.limiter) {
            limit(buf, frames)
        } else {
            limiting = false
            var p = 0f
            for (k in 0 until frames * 2) p = max(p, abs(buf[k]))
            peakLevel = p
        }
    }

    /** Peak limiter: instant attack, ~150 ms release, ceiling -0.3 dBFS. */
    private fun limit(buf: FloatArray, frames: Int) {
        val ceiling = 0.966f
        val release = 1f - exp(-1f / (fs * 0.15f))
        var i = 0
        var minGain = 1f
        var maxPeak = 0f
        for (n in 0 until frames) {
            val peak = max(abs(buf[i]), abs(buf[i + 1]))
            if (peak > maxPeak) maxPeak = peak
            val needed = if (peak * limGain > ceiling) ceiling / peak else 1f
            limGain = if (needed < limGain) needed else limGain + (1f - limGain) * release
            if (limGain < minGain) minGain = limGain
            buf[i] *= limGain; buf[i + 1] *= limGain
            i += 2
        }
        limiting = minGain < 0.99f
        peakLevel = maxPeak
    }

    companion object {
        fun dbToLin(db: Float): Float = if (db == 0f) 1f else 10f.pow(db / 20f)
    }
}

/** Parses an AutoEQ "ParametricEQ.txt" (also Equalizer APO format). */
object AutoEqParser {
    data class Result(val preampDb: Float, val bands: List<ParametricBand>)

    private val filterRe = Regex(
        """Filter\s*\d*\s*:\s*(ON|OFF)\s+([A-Z]+)\s+Fc\s+([\d.]+)\s*Hz(?:\s+Gain\s+(-?[\d.]+)\s*dB)?(?:\s+Q\s+([\d.]+))?""",
        RegexOption.IGNORE_CASE
    )
    private val preampRe = Regex("""Preamp\s*:\s*(-?[\d.]+)\s*dB""", RegexOption.IGNORE_CASE)

    fun parse(text: String): Result? {
        val pre = preampRe.find(text)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        val bands = filterRe.findAll(text).mapNotNull { m ->
            val type = FilterType.fromAutoEq(m.groupValues[2]) ?: return@mapNotNull null
            ParametricBand(
                type = type,
                freqHz = m.groupValues[3].toFloatOrNull()?.coerceIn(10f, 22000f) ?: return@mapNotNull null,
                gainDb = m.groupValues[4].toFloatOrNull()?.coerceIn(-30f, 30f) ?: 0f,
                q = m.groupValues[5].toFloatOrNull()?.coerceIn(0.05f, 20f) ?: 0.71f,
                enabled = m.groupValues[1].equals("ON", true)
            )
        }.toList()
        return if (bands.isEmpty()) null else Result(pre, bands)
    }
}
