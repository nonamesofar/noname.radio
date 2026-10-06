package noname.fm.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs the real JLayer decoder on hand-built MPEG-1 Layer III files (128 kbps, 44.1 kHz, stereo). */
class WaveformServiceDecodeTest {

    private static final int FRAME_SIZE = 417; // 144 * 128000 / 44100, no padding

    @TempDir
    Path tmp;

    private static byte[] header() {
        return new byte[] {(byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0x00};
    }

    /** A valid frame whose side info and main data are all zero: decodes to silence. */
    private static byte[] silentFrame() {
        byte[] frame = new byte[FRAME_SIZE];
        System.arraycopy(header(), 0, frame, 0, 4);
        return frame;
    }

    /** A valid header followed by random bytes: usually decodes to noise or fails inside the decoder. */
    private static byte[] noiseFrame(Random random) {
        byte[] frame = new byte[FRAME_SIZE];
        random.nextBytes(frame);
        System.arraycopy(header(), 0, frame, 0, 4);
        return frame;
    }

    private File write(byte[]... frames) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] f : frames) {
            out.write(f);
        }
        Path file = tmp.resolve("t" + System.nanoTime() + ".mp3");
        Files.write(file, out.toByteArray());
        return file.toFile();
    }

    private WaveformService service() {
        WaveformService service = new WaveformService();
        service.setCacheDir(tmp.resolve("cache").toString());
        return service;
    }

    @Test
    void decodesOneLevelPerFrame() throws Exception {
        File f = write(silentFrame(), silentFrame(), silentFrame(), silentFrame());
        WaveformService.FrameLevels levels = service().decodeFrameLevels(f);
        assertEquals(4, levels.count());
        for (int i = 0; i < levels.count(); i++) {
            assertEquals(0f, levels.values()[i]);
        }
    }

    @Test
    void silentTrackGivesThousandZeros() throws Exception {
        File f = write(silentFrame(), silentFrame(), silentFrame());
        String json = new String(service().getWaveformJson(f));
        assertTrue(json.startsWith("{\"bins\":[0,0,"));
        assertEquals(1000, json.split(",").length);
        assertTrue(!json.matches(".*[1-9].*"));
    }

    @Test
    void corruptFrameIsSkippedAndDecodingContinues() throws Exception {
        // seed 0 gives a noise frame that makes JLayer fail inside the decoder (it is skipped, not fatal);
        // the frames around it must still be decoded
        File f = write(silentFrame(), silentFrame(), noiseFrame(new Random(0)), silentFrame(), silentFrame());
        assertEquals(4, service().decodeFrameLevels(f).count());
    }

    @Test
    void truncatedFileKeepsFramesReadSoFar() throws Exception {
        byte[] last = silentFrame();
        byte[] half = java.util.Arrays.copyOf(last, FRAME_SIZE / 2);
        File f = write(silentFrame(), silentFrame(), half);
        assertTrue(service().decodeFrameLevels(f).count() >= 2);
    }

    @Test
    void nonMp3FileThrowsWaveformException() throws Exception {
        Path text = tmp.resolve("notes.mp3");
        Files.writeString(text, "this is definitely not an mp3 file, just some text".repeat(50));
        assertThrows(WaveformException.class, () -> service().decodeFrameLevels(text.toFile()));
    }

    @Test
    void emptyFileThrowsWaveformException() throws Exception {
        Path empty = Files.createFile(tmp.resolve("empty.mp3"));
        assertThrows(WaveformException.class, () -> service().getWaveformJson(empty.toFile()));
    }

    @Test
    void missingFileThrowsWaveformException() {
        File missing = tmp.resolve("missing.mp3").toFile();
        assertThrows(WaveformException.class, () -> service().getWaveformJson(missing));
    }

    @Test
    void corruptCacheEntryIsRewritten() throws Exception {
        File f = write(silentFrame(), silentFrame());
        WaveformService service = service();
        byte[] good = service.getWaveformJson(f);
        try (var files = Files.list(tmp.resolve("cache"))) {
            Path entry = files.filter(p -> p.toString().endsWith(".json")).findFirst().orElseThrow();
            Files.write(entry, new byte[0]); // simulate a zero-byte entry left by a crash
            assertEquals(new String(good), new String(service.getWaveformJson(f)));
            assertEquals(new String(good), Files.readString(entry));
        }
    }
}
