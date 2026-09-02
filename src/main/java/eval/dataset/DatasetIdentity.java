package eval.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import eval.generation.GoldenCase;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Stable dataset identity: unique non-blank case ids, order-independent SHA-256.
 */
public final class DatasetIdentity {

    private static final ObjectMapper CANON = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private DatasetIdentity() {
    }

    public static void validate(List<GoldenCase> rows) {
        if (rows == null) {
            throw new IllegalStateException("dataset is null");
        }
        Set<String> seen = new HashSet<>();
        int index = 0;
        for (GoldenCase row : rows) {
            if (row == null || row.id() == null || row.id().isBlank()) {
                throw new IllegalStateException("missing golden case id at row " + index);
            }
            if (!seen.add(row.id())) {
                throw new IllegalStateException("duplicate golden case id: " + row.id());
            }
            index++;
        }
    }

    /**
     * SHA-256 of canonical JSON of cases sorted by {@code id}. File order does not matter.
     */
    public static String hash(List<GoldenCase> rows) {
        validate(rows);
        List<GoldenCase> ordered = new ArrayList<>(rows);
        ordered.sort(Comparator.comparing(GoldenCase::id));
        try {
            byte[] canonical = CANON.writeValueAsBytes(ordered);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(canonical));
        } catch (Exception e) {
            throw new IllegalStateException("dataset hash", e);
        }
    }

    public static String sha256Utf8(String text) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("sha256", e);
        }
    }
}
