package eval.cli;

import eval.reporting.PagesSite;

import java.nio.file.Path;

public final class PagesMain {

    public static void main(String[] args) {
        System.exit(run(args));
    }

    public static int run(String[] args) {
        if (has(args, "--help") || has(args, "-h")) {
            System.out.println("""
                    java-ai-golden pages

                      --input=DIR    eval output (default build/eval)
                      --output=DIR   static site (default build/eval-pages)
                    """);
            return ExitCode.SUCCESS;
        }
        Path input = Path.of("build/eval");
        Path output = Path.of("build/eval-pages");
        if (args != null) {
            for (String arg : args) {
                if (arg.startsWith("--input=")) {
                    input = Path.of(arg.substring("--input=".length()));
                } else if (arg.startsWith("--output=")) {
                    output = Path.of(arg.substring("--output=".length()));
                } else if (!arg.isBlank()) {
                    System.err.println("USAGE: unknown argument " + arg);
                    return ExitCode.USAGE;
                }
            }
        }
        try {
            Path written = PagesSite.write(input, output);
            System.out.println("Wrote " + written.toAbsolutePath());
            return ExitCode.SUCCESS;
        } catch (IllegalArgumentException e) {
            System.err.println("USAGE: " + e.getMessage());
            return ExitCode.USAGE;
        } catch (RuntimeException e) {
            System.err.println("PAGES failed: " + e.getMessage());
            e.printStackTrace(System.err);
            return ExitCode.USAGE;
        }
    }

    private static boolean has(String[] args, String flag) {
        if (args == null) {
            return false;
        }
        for (String a : args) {
            if (flag.equals(a)) {
                return true;
            }
        }
        return false;
    }
}
