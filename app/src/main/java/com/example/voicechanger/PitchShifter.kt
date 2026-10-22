package com.example.voicechanger

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Biquad filter (RBJ cookbook). */
class Biquad(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) {
    private val B0 = (b0 / a0).toFloat(); private val B1 = (b1 / a0).toFloat()
    private val B2 = (b2 / a0).toFloat(); private val A1 = (a1 / a0).toFloat()
    private val A2 = (a2 / a0).toFloat()
    private var x1 = 0f; private var x2 = 0f; private var y1 = 0f; private var y2 = 0f

    fun process(x: Float): Float {
        val y = B0 * x + B1 * x1 + B2 * x2 - A1 * y1 - A2 * y2
        x2 = x1; x1 = x; y2 = y1; y1 = y
        return y
    }

    companion object {
        fun highPass(fs: Float, f: Float, q: Float = 0.707f): Biquad {
            val w = 2 * PI * f / fs; val c = cos(w); val al = sin(w) / (2 * q)
            return Biquad((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al)
        }
        fun highShelf(fs: Float, f: Float, gainDb: Float): Biquad {
            val a = 10.0.pow(gainDb / 40.0); val w = 2 * PI * f / fs
            val c = cos(w); val al = sin(w) / 2 * sqrt(2.0) // shelf slope S = 1
            val sa = 2 * sqrt(a) * al
            return Biquad(
                a * ((a + 1) + (a - 1) * c + sa),
                -2 * a * ((a - 1) + (a + 1) * c),
                a * ((a + 1) + (a - 1) * c - sa),
                (a + 1) - (a - 1) * c + sa,
                2 * ((a - 1) - (a + 1) * c),
                (a + 1) - (a - 1) * c - sa)
        }
    }
}

/** Real-time pitch shifter (two crossfaded delay taps) + optional "female" EQ. ratio > 1 = higher pitch. */
class PitchShifter(private val windowSize: Int = 1024, sampleRate: Float = 44100f) {
    private val mask = 8191
    private val buf = FloatArray(mask + 1)
    private var w = 0
    private var phase = 0f
    @Volatile var ratio = 1f
    /** true = male chest rumble kam + upar ki frequencies tez (zyada female / airy). */
    @Volatile var eq = false
    private val hp = Biquad.highPass(sampleRate, 150f)
    private val shelf = Biquad.highShelf(sampleRate, 3500f, 5f)

    fun process(data: ShortArray, len: Int) {
        val r = ratio
        val useEq = eq
        for (i in 0 until len) {
            buf[w] = data[i] / 32768f
            val p2 = (phase + 0.5f) % 1f
            val g1 = 0.5f - 0.5f * cos(2.0 * PI * phase).toFloat()
            val g2 = 0.5f - 0.5f * cos(2.0 * PI * p2).toFloat()
            var out = read(phase * windowSize) * g1 + read(p2 * windowSize) * g2
            if (useEq) out = shelf.process(hp.process(out))
            data[i] = (out.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            phase += (1f - r) / windowSize
            if (phase >= 1f) phase -= 1f
            if (phase < 0f) phase += 1f
            w = (w + 1) and mask
        }
    }

    private fun read(delay: Float): Float {
        val pos = w - delay
        val i0 = Math.floor(pos.toDouble()).toInt()
        val frac = pos - i0
        val a = buf[i0 and mask]
        val b = buf[(i0 + 1) and mask]
        return a + (b - a) * frac
    }
}
