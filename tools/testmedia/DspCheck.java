/**
 * Standalone DSP verification for T9 (Parametric EQ per-channel filtering & Band Pass).
 * Mirrors Biquad in app/src/main/java/com/musicamp/poweramp/Dsp.kt.
 */
public class DspCheck {
    enum Type { PEAK, LOW_SHELF, HIGH_SHELF, LOW_PASS, HIGH_PASS, BAND_PASS }

    static class Biquad {
        double b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0;
        double z1L = 0, z2L = 0, z1R = 0, z2R = 0;

        void set(Type type, int fs, double freq, double gainDb, double q) {
            double f = Math.max(10.0, Math.min(fs * 0.49, freq));
            double w0 = 2.0 * Math.PI * f / fs;
            double cw = Math.cos(w0), sw = Math.sin(w0);
            double qq = Math.max(0.05, Math.min(20.0, q));
            double alpha = sw / (2.0 * qq);
            double a = Math.pow(10.0, gainDb / 40.0);
            double nb0 = 1, nb1 = 0, nb2 = 0, na0 = 1, na1 = 0, na2 = 0;
            switch (type) {
                case PEAK:
                    nb0 = 1 + alpha * a; nb1 = -2 * cw; nb2 = 1 - alpha * a;
                    na0 = 1 + alpha / a; na1 = -2 * cw; na2 = 1 - alpha / a;
                    break;
                case BAND_PASS:
                    nb0 = alpha; nb1 = 0; nb2 = -alpha;
                    na0 = 1 + alpha; na1 = -2 * cw; na2 = 1 - alpha;
                    break;
                default:
                    break;
            }
            b0 = nb0 / na0; b1 = nb1 / na0; b2 = nb2 / na0; a1 = na1 / na0; a2 = na2 / na0;
        }

        void processStereo(float[] buf, int frames) {
            for (int n = 0, i = 0; n < frames; n++, i += 2) {
                double xl = buf[i];
                double yl = b0 * xl + z1L;
                z1L = b1 * xl - a1 * yl + z2L;
                z2L = b2 * xl - a2 * yl;
                buf[i] = (float) yl;

                double xr = buf[i + 1];
                double yr = b0 * xr + z1R;
                z1R = b1 * xr - a1 * yr + z2R;
                z2R = b2 * xr - a2 * yr;
                buf[i + 1] = (float) yr;
            }
        }

        void processLeft(float[] buf, int frames) {
            for (int n = 0, i = 0; n < frames; n++, i += 2) {
                double xl = buf[i];
                double yl = b0 * xl + z1L;
                z1L = b1 * xl - a1 * yl + z2L;
                z2L = b2 * xl - a2 * yl;
                buf[i] = (float) yl;
            }
        }
    }

    static double rmsDb(float[] buf, int frames, int ch) {
        double sum = 0;
        int start = frames / 4; // skip transient
        int count = frames - start;
        for (int n = start; n < frames; n++) {
            double v = buf[n * 2 + ch];
            sum += v * v;
        }
        double rms = Math.sqrt(sum / count);
        return 20.0 * Math.log10(Math.max(rms, 1e-12));
    }

    static float[] stereoSine(int fs, int frames, double hz) {
        float[] buf = new float[frames * 2];
        for (int n = 0; n < frames; n++) {
            float s = (float) (0.25 * Math.sin(2.0 * Math.PI * hz * n / fs));
            buf[n * 2] = s;
            buf[n * 2 + 1] = s;
        }
        return buf;
    }

    public static void main(String[] args) {
        int fs = 48000, frames = 24000;

        // 1. +12 dB PEAK at 1 kHz on LEFT only
        float[] ref = stereoSine(fs, frames, 1000.0);
        double refL = rmsDb(ref, frames, 0);
        double refR = rmsDb(ref, frames, 1);
        float[] buf1 = stereoSine(fs, frames, 1000.0);
        Biquad peakL = new Biquad();
        peakL.set(Type.PEAK, fs, 1000.0, 12.0, 1.41);
        peakL.processLeft(buf1, frames);
        double deltaL = rmsDb(buf1, frames, 0) - refL;
        double deltaR = rmsDb(buf1, frames, 1) - refR;
        System.out.printf("PEAK +12dB @1kHz LEFT-only -> L: %+.2f dB, R: %+.2f dB%n", deltaL, deltaR);

        // 2. BAND_PASS @ 1 kHz (Q = 1.41): 1 kHz vs 2 kHz (one octave away)
        float[] bp1k = stereoSine(fs, frames, 1000.0);
        double ref1k = rmsDb(bp1k, frames, 0);
        Biquad bpA = new Biquad();
        bpA.set(Type.BAND_PASS, fs, 1000.0, 0.0, 1.41);
        bpA.processStereo(bp1k, frames);
        double gain1k = rmsDb(bp1k, frames, 0) - ref1k;

        float[] bp2k = stereoSine(fs, frames, 2000.0);
        double ref2k = rmsDb(bp2k, frames, 0);
        Biquad bpB = new Biquad();
        bpB.set(Type.BAND_PASS, fs, 1000.0, 0.0, 1.41);
        bpB.processStereo(bp2k, frames);
        double gain2k = rmsDb(bp2k, frames, 0) - ref2k;
        System.out.printf("BAND_PASS @1kHz (Q=1.41) -> 1kHz: %+.2f dB, 2kHz (+1 oct): %+.2f dB%n", gain1k, gain2k);
    }
}
