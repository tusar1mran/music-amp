import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Runs the app's compiled TagWriter on the JVM (no device needed) and checks it:
 *   FLAC: only changed fields are rewritten, other comments and the audio bytes are preserved.
 *   MP3 ID3v2.3: version kept, UTF-16 text, unknown frames preserved byte-for-byte, audio preserved.
 *   Refusals: ID3 unsynchronisation, ID3v2.2, a damaged frame.
 *
 * Usage (Git Bash, after :app:compileDebugKotlin; the source launcher can't see android.jar, so compile first):
 *   CP="app/build/tmp/kotlin-classes/debug;<kotlin-stdlib.jar>;<sdk>/platforms/android-35/android.jar"
 *   javac -d /tmp/twc -encoding UTF-8 tools/testmedia/TagWriterCheck.java
 *   java -Dfile.encoding=UTF-8 -cp "/tmp/twc;$CP" TagWriterCheck
 */
public class TagWriterCheck {
    static Object writer; static Class<?> wc, fieldEnum, changesCls;
    static int failures = 0;

    public static void main(String[] a) throws Exception {
        wc = Class.forName("com.musicamp.poweramp.TagWriter");
        writer = wc.getField("INSTANCE").get(null);
        fieldEnum = Class.forName("com.musicamp.poweramp.TagField");
        changesCls = Class.forName("com.musicamp.poweramp.TagChanges");
        Path dir = Files.createTempDirectory("tw");
        flacTest(dir);
        mp3Test(dir);
        refusalTests(dir);
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    static void check(boolean ok, String what) { System.out.println((ok ? "PASS " : "FAIL ") + what); if (!ok) failures++; }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object changes(String... kv) throws Exception {
        Map m = new LinkedHashMap();
        for (int i = 0; i < kv.length; i += 2) m.put(Enum.valueOf((Class) fieldEnum, kv[i]), kv[i + 1]);
        return changesCls.getConstructor(Map.class).newInstance(m);
    }

    static Object call(String name, Object... args) throws Exception {
        for (Method m : wc.getDeclaredMethods()) if (m.getName().equals(name) && m.getParameterCount() == args.length) {
            m.setAccessible(true);
            try { return m.invoke(writer, args); } catch (InvocationTargetException e) { throw (Exception) e.getCause(); }
        }
        throw new NoSuchMethodException(name);
    }

    static byte[] le32(int v) { return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array(); }

    // ------------------------------------------------------------------ FLAC
    static void flacTest(Path dir) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write("fLaC".getBytes(StandardCharsets.ISO_8859_1));
        o.write(new byte[]{0, 0, 0, 34}); o.write(new byte[34]);                // STREAMINFO
        String[] tags = {"TITLE=Old Title", "ARTIST=Artist A", "DATE=2019-05-03", "TRACKNUMBER=3/12", "COMMENT=keep me"};
        ByteArrayOutputStream vc = new ByteArrayOutputStream();
        byte[] vendor = "ref".getBytes(); vc.write(le32(vendor.length)); vc.write(vendor); vc.write(le32(tags.length));
        for (String t : tags) { byte[] b = t.getBytes(StandardCharsets.UTF_8); vc.write(le32(b.length)); vc.write(b); }
        byte[] v = vc.toByteArray();
        o.write(new byte[]{4, 0, (byte) (v.length >> 8), (byte) v.length}); o.write(v);
        o.write(new byte[]{(byte) 0x81, 0, 1, 0}); o.write(new byte[256]);    // last: PADDING 256
        byte[] audio = new byte[5000]; new Random(1).nextBytes(audio); o.write(audio);
        File src = dir.resolve("in.flac").toFile(), dst = dir.resolve("out.flac").toFile();
        Files.write(src.toPath(), o.toByteArray());

        long audioBytes = (Long) call("rewriteFlac", src, dst, changes("TITLE", "New Title ✓"));
        String out = new String(Files.readAllBytes(dst.toPath()), StandardCharsets.UTF_8);
        check(audioBytes == 5000, "FLAC audio byte count reported");
        check(out.contains("TITLE=New Title ✓") && !out.contains("Old Title"), "FLAC title replaced");
        check(out.contains("ARTIST=Artist A") && out.contains("DATE=2019-05-03") && out.contains("TRACKNUMBER=3/12") && out.contains("COMMENT=keep me"),
                "FLAC untouched fields kept exactly (full date, 3/12, comment)");
        check(!out.contains("COMPOSER") && !out.contains("GENRE") && !out.contains("Unknown"), "FLAC no placeholder fields added");
        byte[] d = Files.readAllBytes(dst.toPath());
        check(Arrays.equals(Arrays.copyOfRange(d, d.length - 5000, d.length), audio), "FLAC audio bytes identical");
        check(d.length == o.size(), "FLAC fits in old comment+padding space (file size unchanged)");
        check(call("verify", dst, changes("TITLE", "New Title ✓"), 5000L) == null, "FLAC read-back verification passes");
        check(call("verify", dst, changes("TITLE", "Something else"), 5000L) != null, "FLAC verification catches a wrong value");
    }

    // ------------------------------------------------------------------ MP3 ID3v2.3
    static byte[] frame23(String id, byte[] body, int f0, int f1) {
        ByteBuffer b = ByteBuffer.allocate(10 + body.length);
        b.put(id.getBytes(StandardCharsets.ISO_8859_1)).putInt(body.length).put((byte) f0).put((byte) f1).put(body);
        return b.array();
    }
    static byte[] text23(String s) { byte[] t = s.getBytes(StandardCharsets.ISO_8859_1); byte[] b = new byte[t.length + 1]; System.arraycopy(t, 0, b, 1, t.length); return b; }
    static byte[] syncsafe(int v) { return new byte[]{(byte) ((v >> 21) & 0x7F), (byte) ((v >> 14) & 0x7F), (byte) ((v >> 7) & 0x7F), (byte) (v & 0x7F)}; }

    static byte[] mp3(int major, int flags, byte[]... frames) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (byte[] f : frames) body.write(f);
        body.write(new byte[64]); // padding
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write("ID3".getBytes()); o.write(major); o.write(0); o.write(flags); o.write(syncsafe(body.size())); o.write(body.toByteArray());
        byte[] audio = new byte[3000]; new Random(2).nextBytes(audio); audio[0] = (byte) 0xFF; audio[1] = (byte) 0xFB; o.write(audio);
        return o.toByteArray();
    }

    static void mp3Test(Path dir) throws Exception {
        byte[] apic = new byte[700]; new Random(3).nextBytes(apic);
        byte[] in = mp3(3, 0,
                frame23("TIT2", text23("Old"), 0, 0),
                frame23("TPE1", text23("Band"), 0, 0),
                frame23("TYER", text23("1999"), 0, 0),
                frame23("APIC", apic, 0, 0),
                frame23("TXXX", text23("custom"), 0x40, 0)); // v2.3 status flag (file alter preservation)
        File src = dir.resolve("in.mp3").toFile(), dst = dir.resolve("out.mp3").toFile();
        Files.write(src.toPath(), in);
        long audioBytes = (Long) call("rewriteMp3", src, dst, changes("TITLE", "Новое ✓", "YEAR", "2024"));
        byte[] d = Files.readAllBytes(dst.toPath());
        check(d[3] == 3, "MP3 keeps ID3v2.3");
        check(audioBytes == 3000 && Arrays.equals(Arrays.copyOfRange(d, d.length - 3000, d.length), Arrays.copyOfRange(in, in.length - 3000, in.length)), "MP3 audio bytes identical");
        String s = new String(d, StandardCharsets.ISO_8859_1);
        check(s.contains("TPE1") && s.contains("Band"), "MP3 untouched artist frame kept");
        check(indexOf(d, apic) >= 0, "MP3 cover art (APIC) preserved byte-for-byte");
        int tx = s.indexOf("TXXX");
        check(tx > 0 && d[tx + 8] == 0x40, "MP3 v2.3 frame flags preserved");
        check(!s.contains("1999") && s.contains("TYER") && !s.contains("TDRC"), "MP3 year written as v2.3 TYER, old year gone");
        check(call("verify", dst, changes("TITLE", "Новое ✓", "YEAR", "2024"), 3000L) == null, "MP3 read-back verification passes (UTF-16 title)");
    }

    static int indexOf(byte[] h, byte[] n) { outer: for (int i = 0; i <= h.length - n.length; i++) { for (int j = 0; j < n.length; j++) if (h[i + j] != n[j]) continue outer; return i; } return -1; }

    // ------------------------------------------------------------------ refusals
    static void refusalTests(Path dir) throws Exception {
        expectRefusal(dir, mp3(3, 0x80, frame23("TIT2", text23("x"), 0, 0)), "unsynchronisation");
        expectRefusal(dir, mp3(2, 0, frame23("TIT2", text23("x"), 0, 0)), "ID3v2.2");
        byte[] bad = frame23("TIT2", text23("x"), 0, 0); bad[7] = 120; // size runs past the tag
        expectRefusal(dir, mp3(3, 0, bad), "damaged frame size");
        expectRefusal(dir, mp3(3, 0, frame23("TIT2", text23("x"), 0, 0x80)), "compressed frame");
    }

    static void expectRefusal(Path dir, byte[] data, String what) throws Exception {
        File src = dir.resolve("r.mp3").toFile(), dst = dir.resolve("r_out.mp3").toFile();
        Files.write(src.toPath(), data);
        boolean refused = false;
        try { call("rewriteMp3", src, dst, changes("TITLE", "y")); } catch (Exception e) { refused = e.getClass().getSimpleName().equals("UnsupportedLayout"); }
        check(refused, "MP3 refused: " + what);
    }
}
