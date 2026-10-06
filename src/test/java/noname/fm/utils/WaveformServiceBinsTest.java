package noname.fm.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class WaveformServiceBinsTest {

    private static float[] ramp(int n) {
        float[] levels = new float[n];
        for (int i = 0; i < n; i++) {
            levels[i] = i + 1;
        }
        return levels;
    }

    @Test
    void manyLevelsGiveExactlyThousandBins() {
        assertEquals(1000, WaveformService.computeBins(ramp(11500), 11500, 1000).length);
    }

    @Test
    void fewerLevelsThanBinsStillGiveThousandBins() {
        assertEquals(1000, WaveformService.computeBins(ramp(300), 300, 1000).length);
    }

    @Test
    void allZeroLevelsGiveAllZeros() {
        int[] bins = WaveformService.computeBins(new float[500], 500, 1000);
        assertArrayEquals(new int[1000], bins);
    }

    @Test
    void maximumIs255() {
        int[] bins = WaveformService.computeBins(ramp(11500), 11500, 1000);
        assertEquals(255, Arrays.stream(bins).max().getAsInt());
    }

    @Test
    void quietIntroIsLowerThanLoudSection() {
        float[] levels = new float[2000];
        Arrays.fill(levels, 0, 1000, 100f);
        Arrays.fill(levels, 1000, 2000, 1000f);
        int[] bins = WaveformService.computeBins(levels, levels.length, 1000);
        for (int i = 0; i < 500; i++) {
            assertTrue(bins[i] < bins[500 + i], "bin " + i);
        }
    }

    @Test
    void jsonHasThousandValues() throws Exception {
        int[] bins = WaveformService.computeBins(ramp(11500), 11500, 1000);
        byte[] json = WaveformService.toJson(bins);
        JsonNode node = new ObjectMapper().readTree(new String(json, StandardCharsets.UTF_8));
        assertEquals(1000, node.get("bins").size());
        assertEquals(bins[10], node.get("bins").get(10).asInt());
    }
}
