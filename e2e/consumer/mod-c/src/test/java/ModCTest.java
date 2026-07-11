import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModCTest {
    private static final String MODULE = "mod-c";

    @Test
    void passes() throws Exception {
        sleepIfEnabled();
        assertTrue(true);
    }

    private static void sleepIfEnabled() throws Exception {
        if (!"1".equals(System.getenv("SHARDWISE_E2E_SLEEP_TESTS"))) return;
        int weight = parseWeight(System.getenv("SHARDWISE_E2E_WEIGHTS"));
        int unitMs = Integer.parseUnsignedInt(
            System.getenv().getOrDefault("SHARDWISE_E2E_SLEEP_UNIT_MS", "100"));
        Thread.sleep((long) weight * unitMs);
    }

    private static int parseWeight(String weights) {
        if (weights == null) return 10;
        for (String line : weights.split("\\n")) {
            String[] kv = line.split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals(MODULE)) {
                try { return Integer.parseUnsignedInt(kv[1].trim()); }
                catch (NumberFormatException e) { return 10; }
            }
        }
        return 10;
    }
}
