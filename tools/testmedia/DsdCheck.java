import java.io.*;
import java.nio.*;
import java.nio.file.*;

/** Port of DsdSource's PCM path (same table build, ring order, bit reversal) to check its output. */
public class DsdCheck {
    public static void main(String[] a) throws Exception {
        byte[] f = Files.readAllBytes(Paths.get(a[0]));
        ByteBuffer h = ByteBuffer.wrap(f).order(ByteOrder.LITTLE_ENDIAN);
        int ch = h.getInt(28 + 24), rate = h.getInt(28 + 28), block = h.getInt(28 + 44);
        long fmtSize = h.getLong(28 + 4);
        int data = (int) (28 + fmtSize + 12);
        int pcmRate = 88200, bytesPerOut = rate / pcmRate / 8, firBytes = 24 * bytesPerOut, taps = firBytes * 8;
        double fc = 24000.0 / rate, m = taps - 1, sum = 0;
        double[] hh = new double[taps];
        for (int n = 0; n < taps; n++) {
            double x = n - m / 2.0;
            double sinc = x == 0 ? 2 * fc : Math.sin(2 * Math.PI * fc * x) / (Math.PI * x);
            double w = 0.35875 - 0.48829 * Math.cos(2 * Math.PI * n / m) + 0.14128 * Math.cos(4 * Math.PI * n / m) - 0.01168 * Math.cos(6 * Math.PI * n / m);
            hh[n] = sinc * w; sum += hh[n];
        }
        for (int n = 0; n < taps; n++) hh[n] /= sum;
        float[][] table = new float[firBytes][256];
        for (int k = 0; k < firBytes; k++) for (int v = 0; v < 256; v++) {
            double acc = 0;
            for (int b = 0; b < 8; b++) acc += hh[k * 8 + b] * (((v >> b) & 1) == 1 ? 1 : -1);
            table[k][v] = (float) acc;
        }
        for (int c = 0; c < 2; c++) {
            byte[] ring = new byte[firBytes];
            java.util.Arrays.fill(ring, (byte) 0x69);
            int head = 0;
            int outN = pcmRate; // 1 second
            double[] y = new double[outN];
            long pos = 0;
            for (int o = 0; o < outN; o++) {
                for (int k = 0; k < bytesPerOut; k++, pos++) {
                    long g = pos / block; int off = (int) (pos % block);
                    byte raw = f[(int) (data + g * block * ch + (long) c * block + off)];
                    byte msb = (byte) (Integer.reverse(raw & 0xFF) >>> 24);
                    head = (head + 1) % firBytes;
                    ring[head] = msb;
                }
                float acc = 0; int idx = head;
                for (int k = 0; k < firBytes; k++) { acc += table[k][ring[idx] & 0xFF]; idx = idx == 0 ? firBytes - 1 : idx - 1; }
                y[o] = acc;
            }
            for (double hz : new double[]{1000, 1500, 3000}) {
                double re = 0, im = 0;
                for (int i = 8000; i < outN; i++) { re += y[i] * Math.cos(2 * Math.PI * hz * i / pcmRate); im += y[i] * Math.sin(2 * Math.PI * hz * i / pcmRate); }
                double amp = 2 * Math.hypot(re, im) / (outN - 8000);
                System.out.printf("channel %d  %5.0f Hz amplitude %.3f%n", c, hz, amp);
            }
        }
    }
}
