package eval.execution;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import eval.domain.EvalMode;
import eval.domain.MetricWeights;
import eval.domain.Thresholds;
import eval.generation.GoldenReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EvalConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EvalMode mode = EvalMode.DETERMINISTIC;
    private String model = "qwen2.5-coder:7b";
    private String judgeModel;
    private boolean judgeEnabled = true;
    private int repetitions = 1;
    private Path outputDir = Path.of("build/eval");
    private Path baselinePath = Path.of("baselines/generation-v1.json");
    private Path saveBaselinePath;
    private List<String> benchmarkModels = List.of();
    private boolean includeRed = false;
    private boolean applyGate = false;
    private ArtifactMode artifactMode = ArtifactMode.FAILURE;
    private Thresholds thresholds = Thresholds.deterministicStrict();
    private MetricWeights weights = MetricWeights.equal();
    private boolean writeFixtures = false;
    private boolean live = false;
    private Path configPath;

    public EvalMode mode() {
        return mode;
    }

    public String model() {
        return model;
    }

    public String judgeModel() {
        return judgeModel == null || judgeModel.isBlank() ? model : judgeModel;
    }

    public boolean usesModel() {
        return mode == EvalMode.LIVE
                || mode == EvalMode.BENCHMARK
                || (mode == EvalMode.REGRESSION && live);
    }

    public boolean judgeEnabled() {
        return judgeEnabled && usesModel();
    }

    public int repetitions() {
        if (!usesModel()) {
            return 1;
        }
        return Math.max(1, repetitions);
    }

    public Path outputDir() {
        return outputDir;
    }

    public Path baselinePath() {
        return baselinePath;
    }

    public Path saveBaselinePath() {
        return saveBaselinePath;
    }

    public List<String> benchmarkModels() {
        return benchmarkModels;
    }

    public boolean includeRed() {
        return includeRed;
    }

    public boolean applyGate() {
        return applyGate;
    }

    public ArtifactMode artifactMode() {
        return artifactMode;
    }

    public Thresholds thresholds() {
        return thresholds;
    }

    public MetricWeights weights() {
        return weights;
    }

    public boolean writeFixtures() {
        return writeFixtures;
    }

    public Path datasetDir() {
        return GoldenReader.evalDir();
    }

    public String datasetVersion() {
        return GoldenReader.datasetVersion();
    }

    public static EvalConfig resolve(String[] args) {
        EvalConfig config = new EvalConfig();
        Path file = configFile(args);
        if (file != null && Files.isRegularFile(file)) {
            config.configPath = file;
            overlayJson(config, file);
        }
        overlayProperties(config);
        overlayArgs(config, args);
        return config;
    }

    public EvalConfig copyForModel(String modelName) {
        EvalConfig copy = new EvalConfig();
        copy.mode = this.mode;
        copy.model = modelName;
        copy.judgeModel = this.judgeModel;
        copy.judgeEnabled = this.judgeEnabled;
        copy.repetitions = this.repetitions;
        copy.outputDir = this.outputDir;
        copy.baselinePath = this.baselinePath;
        copy.saveBaselinePath = this.saveBaselinePath;
        copy.benchmarkModels = this.benchmarkModels;
        copy.includeRed = this.includeRed;
        copy.applyGate = this.applyGate;
        copy.artifactMode = this.artifactMode;
        copy.thresholds = this.thresholds;
        copy.weights = this.weights;
        copy.writeFixtures = this.writeFixtures;
        copy.live = this.live;
        return copy;
    }

    static Path configFile(String[] args) {
        if (args != null) {
            for (String arg : args) {
                if (arg.startsWith("--config=")) {
                    return Path.of(arg.substring("--config=".length()));
                }
            }
        }
        String prop = System.getProperty("evalConfig");
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop);
        }
        Path local = Path.of("eval.json");
        return Files.isRegularFile(local) ? local : null;
    }

    static void overlayJson(EvalConfig config, Path file) {
        try {
            FileDto dto = MAPPER.readValue(Files.readString(file), FileDto.class);
            if (dto.mode != null) {
                config.mode = parseMode(dto.mode);
            }
            if (dto.model != null) {
                config.model = dto.model;
            }
            if (dto.judgeModel != null) {
                config.judgeModel = dto.judgeModel;
            }
            if (dto.judge != null) {
                config.judgeEnabled = dto.judge;
            }
            if (dto.repetitions != null) {
                config.repetitions = dto.repetitions;
            }
            if (dto.outputDir != null) {
                config.outputDir = Path.of(dto.outputDir);
            }
            if (dto.baseline != null) {
                config.baselinePath = Path.of(dto.baseline);
            }
            if (dto.models != null && !dto.models.isEmpty()) {
                config.benchmarkModels = List.copyOf(dto.models);
            }
            if (dto.red != null) {
                config.includeRed = dto.red;
            }
            if (dto.gate != null) {
                config.applyGate = dto.gate;
            }
            if (dto.artifacts != null) {
                config.artifactMode = ArtifactMode.parse(dto.artifacts);
            }
            if (dto.thresholds != null) {
                config.thresholds = dto.thresholds;
            }
            if (dto.weights != null) {
                config.weights = dto.weights;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    static void overlayProperties(EvalConfig config) {
        String mode = System.getProperty("mode");
        if (mode != null) {
            config.mode = parseMode(mode);
        }
        String model = System.getProperty("model");
        if (model != null) {
            config.model = model;
        }
        String judgeModel = System.getProperty("judgeModel");
        if (judgeModel != null) {
            config.judgeModel = judgeModel;
        }
        String judge = System.getProperty("judge");
        if (judge != null) {
            config.judgeEnabled = !"false".equalsIgnoreCase(judge);
        }
        String repetitions = System.getProperty("repetitions");
        if (repetitions != null) {
            config.repetitions = Integer.parseInt(repetitions);
        }
        String output = System.getProperty("outputDir");
        if (output != null) {
            config.outputDir = Path.of(output);
        }
        String baseline = System.getProperty("baseline");
        if (baseline != null) {
            config.baselinePath = Path.of(baseline);
        }
        if ("true".equals(System.getProperty("red"))) {
            config.includeRed = true;
        }
        if ("true".equals(System.getProperty("gate"))) {
            config.applyGate = true;
        }
        String artifacts = System.getProperty("artifacts");
        if (artifacts != null) {
            config.artifactMode = ArtifactMode.parse(artifacts);
        }
        if ("true".equals(System.getProperty("writeFixtures"))) {
            config.writeFixtures = true;
        }
        String live = System.getProperty("live");
        if ("true".equals(live)) {
            config.live = true;
            if (config.mode == EvalMode.DETERMINISTIC) {
                config.mode = EvalMode.LIVE;
            }
        }
    }

    static void overlayArgs(EvalConfig config, String[] args) {
        if (args == null) {
            return;
        }
        for (String arg : args) {
            if (arg.startsWith("--mode=")) {
                config.mode = parseMode(arg.substring("--mode=".length()));
            } else if (arg.startsWith("--model=")) {
                config.model = arg.substring("--model=".length());
            } else if (arg.startsWith("--judgeModel=")) {
                config.judgeModel = arg.substring("--judgeModel=".length());
            } else if (arg.startsWith("--judge=")) {
                config.judgeEnabled = !"false".equalsIgnoreCase(arg.substring("--judge=".length()));
            } else if (arg.startsWith("--repetitions=")) {
                config.repetitions = Integer.parseInt(arg.substring("--repetitions=".length()));
            } else if (arg.startsWith("--output=")) {
                config.outputDir = Path.of(arg.substring("--output=".length()));
            } else if (arg.startsWith("--baseline=")) {
                config.baselinePath = Path.of(arg.substring("--baseline=".length()));
            } else if (arg.startsWith("--save-baseline=")) {
                config.saveBaselinePath = Path.of(arg.substring("--save-baseline=".length()));
            } else if (arg.startsWith("--models=")) {
                config.benchmarkModels = splitCsv(arg.substring("--models=".length()));
            } else if (arg.startsWith("--red=")) {
                config.includeRed = "true".equalsIgnoreCase(arg.substring("--red=".length()));
            } else if (arg.equals("--red")) {
                config.includeRed = true;
            } else if (arg.startsWith("--gate=")) {
                config.applyGate = "true".equalsIgnoreCase(arg.substring("--gate=".length()));
            } else if (arg.equals("--gate")) {
                config.applyGate = true;
            } else if (arg.startsWith("--artifacts=")) {
                config.artifactMode = ArtifactMode.parse(arg.substring("--artifacts=".length()));
            } else if (arg.equals("--live") || "--live=true".equals(arg)) {
                config.live = true;
            }
        }
        if (config.mode == EvalMode.LIVE || config.mode == EvalMode.BENCHMARK) {
            config.live = true;
        }
    }

    static EvalMode parseMode(String raw) {
        return EvalMode.valueOf(raw.strip().toUpperCase(Locale.ROOT));
    }

    static List<String> splitCsv(String raw) {
        List<String> out = new ArrayList<>();
        for (String p : raw.split(",")) {
            if (!p.isBlank()) {
                out.add(p.strip());
            }
        }
        return List.copyOf(out);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class FileDto {
        public String mode;
        public String model;
        public String judgeModel;
        public Boolean judge;
        public Integer repetitions;
        public String outputDir;
        public String baseline;
        public List<String> models;
        public Boolean red;
        public Boolean gate;
        public String artifacts;
        public Thresholds thresholds;
        public MetricWeights weights;
    }
}
