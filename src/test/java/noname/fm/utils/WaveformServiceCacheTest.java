package noname.fm.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WaveformServiceCacheTest {

    /** Service with a fake decoder that counts calls. */
    private static class FakeService extends WaveformService {
        final AtomicInteger decodes = new AtomicInteger();
        volatile boolean fail;
        volatile CountDownLatch entered;
        volatile CountDownLatch release;

        FakeService(Path cacheDir) {
            setCacheDir(cacheDir.toString());
        }

        @Override
        protected FrameLevels decodeFrameLevels(File mp3) {
            decodes.incrementAndGet();
            if (entered != null) {
                entered.countDown();
            }
            if (release != null) {
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (fail) {
                throw new WaveformException("boom");
            }
            float[] levels = new float[2000];
            for (int i = 0; i < levels.length; i++) {
                levels[i] = i % 100;
            }
            return new FrameLevels(levels, levels.length);
        }
    }

    @TempDir
    Path tmp;

    Path cacheDir;
    File mp3;

    @BeforeEach
    void setUp() throws Exception {
        cacheDir = tmp.resolve("cache");
        mp3 = Files.createFile(tmp.resolve("track.mp3")).toFile();
    }

    @Test
    void secondCallIsServedFromCache() {
        FakeService service = new FakeService(cacheDir);
        byte[] first = service.getWaveformJson(mp3);
        byte[] second = service.getWaveformJson(mp3);
        assertEquals(1, service.decodes.get());
        assertArrayEquals(first, second);
    }

    @Test
    void newInstanceOnSameFolderDoesNotDecode() {
        FakeService first = new FakeService(cacheDir);
        byte[] a = first.getWaveformJson(mp3);
        FakeService restarted = new FakeService(cacheDir);
        byte[] b = restarted.getWaveformJson(mp3);
        assertEquals(0, restarted.decodes.get());
        assertArrayEquals(a, b);
    }

    @Test
    void modifiedFileIsDecodedAgain() {
        FakeService service = new FakeService(cacheDir);
        service.getWaveformJson(mp3);
        assertTrue(mp3.setLastModified(mp3.lastModified() + 10_000));
        service.getWaveformJson(mp3);
        assertEquals(2, service.decodes.get());
    }

    @Test
    void missingCacheFolderIsCreated() {
        assertTrue(!Files.exists(cacheDir));
        new FakeService(cacheDir).getWaveformJson(mp3);
        assertTrue(Files.isDirectory(cacheDir));
    }

    @Test
    void concurrentRequestsDecodeOnce() throws Exception {
        FakeService service = new FakeService(cacheDir);
        service.entered = new CountDownLatch(1);
        service.release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<byte[]> a = pool.submit(() -> service.getWaveformJson(mp3));
            assertTrue(service.entered.await(5, TimeUnit.SECONDS));
            Future<byte[]> b = pool.submit(() -> service.getWaveformJson(mp3));
            Thread.sleep(200); // let the second request reach the in-flight map
            service.release.countDown();
            assertArrayEquals(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS));
            assertEquals(1, service.decodes.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void failedDecodeIsNotCached() {
        FakeService service = new FakeService(cacheDir);
        service.fail = true;
        assertThrows(WaveformException.class, () -> service.getWaveformJson(mp3));
        service.fail = false;
        service.getWaveformJson(mp3);
        assertEquals(2, service.decodes.get());
    }
}
