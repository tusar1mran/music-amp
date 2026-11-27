import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;

/** Writes test media: an uncompressed (VERBATIM) FLAC with tags, a DSF (DSD64) and a WAV. */
public class Gen {
    public static void main(String[] a) throws Exception {
        String out = a[0];
        flac(out + "/cue_album.flac");
        plainFlac(out + "/plain_album.flac");
        dsf(out + "/dsd_tone.dsf");
        wav(out + "/plain_tone.wav");
    }

    // ---------------------------------------------------------------- FLAC
    static int crc8(byte[] b, int off, int len) {
        int c = 0;
        for (int i = off; i < off + len; i++) {
            c ^= b[i] & 0xFF;
            for (int k = 0; k < 8; k++) c = (c & 0x80) != 0 ? ((c << 1) ^ 0x07) & 0xFF : (c << 1) & 0xFF;
        }
        return c;
    }

    static int crc16(byte[] b, int off, int len) {
        int c = 0;
        for (int i = off; i < off + len; i++) {
            c ^= (b[i] & 0xFF) << 8;
            for (int k = 0; k < 8; k++) c = (c & 0x8000) != 0 ? ((c << 1) ^ 0x8005) & 0xFFFF : (c << 1) & 0xFFFF;
        }
        return c;
    }

    static void flac(String path) throws Exception {
        int rate = 8000, block = 4096;
        long total = rate * 60L * 10 + 1234; // a little over 10 minutes, mono 16-bit
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write("fLaC".getBytes(StandardCharsets.ISO_8859_1));
        // STREAMINFO
        ByteBuffer si = ByteBuffer.allocate(34).order(ByteOrder.BIG_ENDIAN);
        si.putShort((short) block).putShort((short) block);
        si.put(new byte[6]);
        long packed = ((long) rate << 44) | (0L << 41) | (15L << 36) | total; // channels-1=0, bps-1=15
        si.putLong(packed);
        si.put(new byte[16]);
        o.write(new byte[]{0x00, 0, 0, 34});
        o.write(si.array());
        // VORBIS_COMMENT (last block)
        String lyrics = "[ti:Test]\n[00:01.00]First line\n[00:04.00]Second line\n[00:07.50]Third line\n";
        String cue = "PERFORMER \"Test Artist\"\nTITLE \"Cue Album\"\nFILE \"cue_album.flac\" WAVE\n" +
                "  TRACK 01 AUDIO\n    TITLE \"Opening\"\n    INDEX 01 00:00:00\n" +
                "  TRACK 02 AUDIO\n    TITLE \"Middle\"\n    INDEX 01 03:00:00\n" +
                "  TRACK 03 AUDIO\n    TITLE \"Ending\"\n    INDEX 01 06:30:37\n";
        String[] tags = {"TITLE=Cue Album Image", "ARTIST=Test Artist", "ALBUM=Cue Album",
                "REPLAYGAIN_TRACK_GAIN=-6.00 dB", "REPLAYGAIN_TRACK_PEAK=0.5", "LYRICS=" + lyrics, "CUESHEET=" + cue};
        ByteArrayOutputStream vc = new ByteArrayOutputStream();
        byte[] vendor = "MusicAmp test".getBytes(StandardCharsets.UTF_8);
        vc.write(le32(vendor.length)); vc.write(vendor);
        vc.write(le32(tags.length));
        for (String t : tags) { byte[] b = t.getBytes(StandardCharsets.UTF_8); vc.write(le32(b.length)); vc.write(b); }
        byte[] v = vc.toByteArray();
        o.write(new byte[]{(byte) 0x84, (byte) (v.length >> 16), (byte) (v.length >> 8), (byte) v.length});
        o.write(v);
        // Frames: VERBATIM mono 16-bit, a different tone per cue track
        long frameNo = 0;
        for (long s = 0; s < total; s += block) {
            int n = (int) Math.min(block, total - s);
            ByteArrayOutputStream f = new ByteArrayOutputStream();
            f.write(0xFF); f.write(0xF8);
            int bsCode = n == block ? 0xC : 0x7;
            f.write((bsCode << 4) | 0x0);     // block size code, rate from STREAMINFO
            f.write((0x0 << 4) | (0x4 << 1)); // mono, 16 bits
            if (frameNo < 0x80) f.write((int) frameNo);
            else { f.write(0xC0 | (int) (frameNo >> 6)); f.write(0x80 | (int) (frameNo & 0x3F)); }
            if (bsCode == 0x7) { f.write((n - 1) >> 8); f.write((n - 1) & 0xFF); }
            byte[] h = f.toByteArray();
            f.write(crc8(h, 0, h.length));
            f.write(0x02); // subframe VERBATIM
            for (int i = 0; i < n; i++) {
                double t = (s + i) / (double) rate;
                double hz = t < 180 ? 440 : (t < 390.5 ? 660 : 880);
                int smp = (int) Math.round(Math.sin(2 * Math.PI * hz * t) * 0.5 * 32767);
                f.write(smp >> 8); f.write(smp & 0xFF);
            }
            byte[] fr = f.toByteArray();
            int c = crc16(fr, 0, fr.length);
            o.write(fr); o.write(c >> 8); o.write(c & 0xFF);
            frameNo++;
        }
        try (FileOutputStream fo = new FileOutputStream(path)) { fo.write(o.toByteArray()); }
    }

    /** 40-second non-CUE FLAC for tag writing tests. */
    static void plainFlac(String path) throws Exception {
        int rate = 8000, block = 4096;
        long total = rate * 40L;
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write("fLaC".getBytes(StandardCharsets.ISO_8859_1));
        ByteBuffer si = ByteBuffer.allocate(34).order(ByteOrder.BIG_ENDIAN);
        si.putShort((short) block).putShort((short) block);
        si.put(new byte[6]);
        long packed = ((long) rate << 44) | (0L << 41) | (15L << 36) | total;
        si.putLong(packed);
        si.put(new byte[16]);
        o.write(new byte[]{0x00, 0, 0, 34});
        o.write(si.array());
        String[] tags = {"TITLE=Plain Flac Track", "ARTIST=Test Artist", "ALBUM=Plain Album", "DATE=2026"};
        ByteArrayOutputStream vc = new ByteArrayOutputStream();
        byte[] vendor = "MusicAmp test".getBytes(StandardCharsets.UTF_8);
        vc.write(le32(vendor.length)); vc.write(vendor);
        vc.write(le32(tags.length));
        for (String t : tags) { byte[] b = t.getBytes(StandardCharsets.UTF_8); vc.write(le32(b.length)); vc.write(b); }
        byte[] v = vc.toByteArray();
        o.write(new byte[]{(byte) 0x84, (byte) (v.length >> 16), (byte) (v.length >> 8), (byte) v.length});
        o.write(v);
        long frameNo = 0;
        for (long s = 0; s < total; s += block) {
            int n = (int) Math.min(block, total - s);
            ByteArrayOutputStream f = new ByteArrayOutputStream();
            f.write(0xFF); f.write(0xF8);
            int bsCode = n == block ? 0xC : 0x7;
            f.write((bsCode << 4) | 0x0);
            f.write((0x0 << 4) | (0x4 << 1));
            if (frameNo < 0x80) f.write((int) frameNo);
            else { f.write(0xC0 | (int) (frameNo >> 6)); f.write(0x80 | (int) (frameNo & 0x3F)); }
            if (bsCode == 0x7) { f.write((n - 1) >> 8); f.write((n - 1) & 0xFF); }
            byte[] h = f.toByteArray();
            f.write(crc8(h, 0, h.length));
            f.write(0x02);
            for (int i = 0; i < n; i++) {
                double t = (s + i) / (double) rate;
                int smp = (int) Math.round(Math.sin(2 * Math.PI * 440 * t) * 0.45 * 32767);
                f.write(smp >> 8); f.write(smp & 0xFF);
            }
            byte[] fr = f.toByteArray();
            int c = crc16(fr, 0, fr.length);
            o.write(fr); o.write(c >> 8); o.write(c & 0xFF);
            frameNo++;
        }
        try (FileOutputStream fo = new FileOutputStream(path)) { fo.write(o.toByteArray()); }
    }

    static byte[] le32(int v) { return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array(); }

    // ---------------------------------------------------------------- DSF (DSD64, stereo, 10 s, 1 kHz)
    static void dsf(String path) throws Exception {
        int rate = 2822400, ch = 2, blockSize = 4096;
        long bitsPerCh = rate * 10L;
        long bytesPerCh = bitsPerCh / 8;
        long blocks = (bytesPerCh + blockSize - 1) / blockSize;
        byte[][] data = new byte[ch][(int) (blocks * blockSize)];
        for (int c = 0; c < ch; c++) {
            double integ = 0, y = 0, integ2 = 0;
            for (long i = 0; i < bitsPerCh; i++) {
                double x = 0.5 * Math.sin(2 * Math.PI * (c == 0 ? 1000 : 1500) * i / rate);
                integ += x - y; integ2 += integ - y; // 2nd-order modulator
                y = integ2 > 0 ? 1 : -1;
                if (y > 0) data[c][(int) (i >> 3)] |= (byte) (1 << (i & 7)); // LSB first
            }
        }
        long dataLen = blocks * blockSize * ch;
        ByteBuffer h = ByteBuffer.allocate(92).order(ByteOrder.LITTLE_ENDIAN);
        h.put("DSD ".getBytes()).putLong(28).putLong(92 + dataLen).putLong(0);
        h.put("fmt ".getBytes()).putLong(52).putInt(1).putInt(0).putInt(2).putInt(ch).putInt(rate).putInt(1)
                .putLong(bitsPerCh).putInt(blockSize).putInt(0);
        h.put("data".getBytes()).putLong(12 + dataLen);
        try (FileOutputStream fo = new FileOutputStream(path)) {
            fo.write(h.array());
            for (long b = 0; b < blocks; b++)
                for (int c = 0; c < ch; c++) fo.write(data[c], (int) (b * blockSize), blockSize);
        }
    }

    // ---------------------------------------------------------------- WAV (44.1 kHz stereo 16-bit, 20 s)
    static void wav(String path) throws Exception {
        int rate = 44100, n = rate * 20;
        ByteBuffer b = ByteBuffer.allocate(44 + n * 4).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + n * 4).put("WAVE".getBytes());
        b.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 2).putInt(rate).putInt(rate * 4)
                .putShort((short) 4).putShort((short) 16);
        b.put("data".getBytes()).putInt(n * 4);
        for (int i = 0; i < n; i++) {
            short s = (short) Math.round(Math.sin(2 * Math.PI * 523.25 * i / rate) * 0.4 * 32767);
            b.putShort(s).putShort(s);
        }
        try (FileOutputStream fo = new FileOutputStream(path)) { fo.write(b.array()); }
    }
}
