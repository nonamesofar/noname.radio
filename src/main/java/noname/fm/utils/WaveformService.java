package noname.fm.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Computes, caches and serializes the overview waveform of an MP3 file.
 */
@Service
public class WaveformService {

    public static final int BIN_COUNT = 1000;

    private static final Logger LOG = LoggerFactory.getLogger(WaveformService.class);
    private static final String CACHE_VERSION = "v1";

    /** One RMS level per decoded MP3 frame; only the first {@code count} values are valid. */
    protected record FrameLevels(float[] values, int count) {
    }

    @Value("${waveform.cache.dir:waveform-cache}")
    private String cacheDir = "waveform-cache";

    private final ConcurrentHashMap<String, CompletableFuture<byte[]>> inFlight = new ConcurrentHashMap<>();

    public void setCacheDir(String cacheDir) {
        this.cacheDir = cacheDir;
    }

    /**
     * Returns the response body ({@code {"bins":[...]}}) for the given file, from the disk cache when possible.
     */
    public byte[] getWaveformJson(File mp3) {
        String key = cacheKey(mp3);
        Path cacheFile = Paths.get(cacheDir).resolve(key + ".json");

        byte[] cached = readCache(cacheFile);
        if (cached != null) {
            return cached;
        }

        CompletableFuture<byte[]> mine = new CompletableFuture<>();
        CompletableFuture<byte[]> existing = inFlight.putIfAbsent(key, mine);
        if (existing != null) {
            try {
                return existing.join();
            } catch (CompletionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                if (cause instanceof WaveformException we) {
                    throw we;
                }
                throw new WaveformException("Waveform failed for " + mp3, cause);
            }
        }

        try {
            // another thread may have finished between our cache check and putIfAbsent
            byte[] json = readCache(cacheFile);
            if (json == null) {
                FrameLevels levels = decodeFrameLevels(mp3);
                json = toJson(computeBins(levels.values(), levels.count(), BIN_COUNT));
                writeCache(cacheFile, json);
            }
            mine.complete(json);
            return json;
        } catch (Throwable t) {
            // always complete the future, or waiters would block forever
            WaveformException failure = t instanceof WaveformException we ? we
                    : new WaveformException("Waveform failed for " + mp3, t);
            mine.completeExceptionally(failure);
            if (t instanceof Error err) {
                throw err;
            }
            throw failure;
        } finally {
            inFlight.remove(key);
        }
    }

    /**
     * Reduces per-frame levels to {@code binCount} values in 0..255, normalized so the loudest bin is 255.
     */
    public static int[] computeBins(float[] levels, int count, int binCount) {
        float[] bins = new float[binCount];
        float max = 0;
        for (int i = 0; i < binCount; i++) {
            int start = (int) ((long) i * count / binCount);
            int end = Math.max(start + 1, (int) ((long) (i + 1) * count / binCount));
            start = Math.min(start, count - 1);
            end = Math.min(end, count);
            float m = 0;
            for (int j = start; j < end; j++) {
                m = Math.max(m, levels[j]);
            }
            bins[i] = m;
            max = Math.max(max, m);
        }
        int[] out = new int[binCount];
        if (max > 0) {
            for (int i = 0; i < binCount; i++) {
                out[i] = Math.round(bins[i] / max * 255);
            }
        }
        return out;
    }

    public static byte[] toJson(int[] bins) {
        StringBuilder sb = new StringBuilder(bins.length * 4 + 16);
        sb.append("{\"bins\":[");
        for (int i = 0; i < bins.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(bins[i]);
        }
        sb.append("]}");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    protected FrameLevels decodeFrameLevels(File mp3) {
        long begin = System.currentTimeMillis();
        float[] levels = new float[8192];
        int count = 0;
        Decoder decoder = new Decoder();
        try (InputStream in = new BufferedInputStream(new FileInputStream(mp3))) {
            Bitstream bitstream = new Bitstream(in);
            try {
                while (true) {
                    Header header;
                    try {
                        header = bitstream.readFrame();
                    } catch (BitstreamException e) {
                        break;
                    }
                    if (header == null) {
                        break;
                    }
                    try {
                        SampleBuffer buffer = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                        short[] samples = buffer.getBuffer();
                        int n = buffer.getBufferLength();
                        double sum = 0;
                        for (int i = 0; i < n; i++) {
                            sum += (double) samples[i] * samples[i];
                        }
                        if (count == levels.length) {
                            levels = Arrays.copyOf(levels, count * 2);
                        }
                        levels[count++] = n > 0 ? (float) Math.sqrt(sum / n) : 0f;
                    } catch (DecoderException | RuntimeException e) {
                        // corrupt frame: skip it (JLayer often throws a raw ArrayIndexOutOfBoundsException here)
                    } finally {
                        bitstream.closeFrame();
                    }
                }
            } finally {
                try {
                    bitstream.close();
                } catch (BitstreamException e) {
                    // nothing to do
                }
            }
        } catch (IOException e) {
            throw new WaveformException("Cannot read " + mp3, e);
        }
        if (count == 0) {
            throw new WaveformException("No MP3 frames decoded from " + mp3);
        }
        LOG.info("Decoded waveform of {}: {} frames in {} ms", mp3.getName(), count,
                System.currentTimeMillis() - begin);
        return new FrameLevels(levels, count);
    }

    private static String cacheKey(File mp3) {
        String raw = mp3.getAbsolutePath() + "|" + mp3.lastModified() + "|" + CACHE_VERSION;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] readCache(Path cacheFile) {
        try {
            if (!Files.isRegularFile(cacheFile)) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(cacheFile);
            if (!isValidJson(bytes)) {
                // empty or truncated entry (e.g. after a crash): treat as a miss so it gets rewritten
                LOG.warn("Ignoring corrupt waveform cache entry {}", cacheFile);
                return null;
            }
            return bytes;
        } catch (IOException e) {
            LOG.warn("Cannot read waveform cache {}: {}", cacheFile, e.toString());
            return null;
        }
    }

    private static boolean isValidJson(byte[] bytes) {
        String s = new String(bytes, StandardCharsets.UTF_8);
        return s.startsWith("{\"bins\":[") && s.endsWith("]}");
    }

    private static void writeCache(Path cacheFile, byte[] json) {
        try {
            Files.createDirectories(cacheFile.getParent());
            Path tmp = cacheFile.resolveSibling(cacheFile.getFileName() + ".tmp");
            Files.write(tmp, json);
            try {
                Files.move(tmp, cacheFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, cacheFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.warn("Cannot write waveform cache {}: {}", cacheFile, e.toString());
        }
    }
}
