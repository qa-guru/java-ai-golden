package eval.reporting;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import eval.domain.EvalRun;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ReportIo {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private ReportIo() {
    }

    public static EvalRun readRun(Path path) {
        try {
            EvalRun run = MAPPER.readValue(Files.readString(path), EvalRun.class);
            if (run == null) {
                throw new IllegalArgumentException("INVALID RUN " + path + ": empty document");
            }
            run.requireIntegrity();
            return run;
        } catch (IOException e) {
            throw new IllegalArgumentException("INVALID RUN " + path + ": " + e.getMessage(), e);
        }
    }

    public static void writeJson(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            MAPPER.writeValue(path.toFile(), value);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + path, e);
        }
    }
}
