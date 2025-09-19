/**
 * HtmlToText: thin CLI/API entrypoint that delegates to EmailPipeline.
 * Provides unified Options and basic I/O (args parsing and file writing).
 */

package com.composerai.api.service;

import jakarta.mail.MessagingException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class HtmlToText {

    public enum OutputFormat {
        PLAIN,
        MARKDOWN
    }

    public enum UrlPolicy {
        KEEP,
        STRIP_ALL,
        CLEAN_ONLY
    }

    /**
     * Unified options for both CLI and programmatic invocation
     */
    public static class Options {
        public String inputFile;
        public String inputType; // eml|html (optional)
        public OutputFormat format = OutputFormat.MARKDOWN;
        public String outputFile; // optional
        public java.nio.file.Path outputDir; // optional
        public Charset charset; // for raw HTML files
        public UrlPolicy urlsPolicy = UrlPolicy.KEEP;
        public boolean includeMetadata = true;
        public boolean jsonOutput = false;

        public boolean isValid() {
            return inputFile != null && format != null;
        }

        public static Options parseFromArgs(String[] args) {
            Options c = new Options();
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                switch (a) {
                    case "--input-file" -> c.inputFile = nextArg(args, ++i, "--input-file requires a value");
                    case "--input-type" -> c.inputType = nextArg(args, ++i, "--input-type requires eml|html");
                    case "--format" -> {
                        String v = nextArg(args, ++i, "--format requires plain|markdown");
                        if ("plain".equalsIgnoreCase(v)) c.format = OutputFormat.PLAIN;
                        else if ("markdown".equalsIgnoreCase(v) || "md".equalsIgnoreCase(v)) c.format = OutputFormat.MARKDOWN;
                        else throw new IllegalArgumentException("Unsupported --format: " + v);
                    }
                    case "--output-file" -> c.outputFile = nextArg(args, ++i, "--output-file requires a value");
                    case "--output-dir" -> c.outputDir = java.nio.file.Path.of(nextArg(args, ++i, "--output-dir requires a value"));
                    case "--charset" -> {
                        String v = nextArg(args, ++i, "--charset requires a value");
                        c.charset = Charset.forName(v);
                    }
                    case "--urls" -> {
                        String v = nextArg(args, ++i, "--urls requires keep|stripAll|cleanOnly");
                        if ("keep".equalsIgnoreCase(v)) c.urlsPolicy = UrlPolicy.KEEP;
                        else if ("stripAll".equalsIgnoreCase(v)) c.urlsPolicy = UrlPolicy.STRIP_ALL;
                        else if ("cleanOnly".equalsIgnoreCase(v)) c.urlsPolicy = UrlPolicy.CLEAN_ONLY;
                        else throw new IllegalArgumentException("Unsupported --urls: " + v);
                    }
                    case "--metadata" -> {
                        String v = nextArg(args, ++i, "--metadata requires true|false");
                        c.includeMetadata = Boolean.parseBoolean(v);
                    }
                    case "--json" -> {
                        String v = nextArg(args, ++i, "--json requires true|false");
                        c.jsonOutput = Boolean.parseBoolean(v);
                    }
                    case "-h", "--help" -> {
                        System.out.println(usage());
                        System.exit(0);
                    }
                    default -> throw new IllegalArgumentException("Unknown argument: " + a);
                }
            }
            return c;
        }
    }

    public static void main(String[] args) {
        Options options = Options.parseFromArgs(args);
        if (!options.isValid()) {
            System.err.println(usage());
            System.exit(2);
        }

        try {
            String result = convert(options);
            Path out = resolveOutputPath(options);
            if (out != null) {
                Files.createDirectories(out.getParent());
                Files.writeString(out, result, StandardCharsets.UTF_8);
            } else {
                System.out.println(result);
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    public static String convert(Options options) throws IOException, MessagingException {
        try {
            return com.composerai.api.service.email.EmailPipeline.process(options);
        } catch (MessagingException e) {
            throw e;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String nextArg(String[] args, int idx, String err) {
        if (idx >= args.length) throw new IllegalArgumentException(err);
        return args[idx];
    }

    private static Path resolveOutputPath(Options options) {
        if (options.outputFile != null && !options.outputFile.isBlank()) {
            return Path.of(options.outputFile);
        }
        if (options.outputDir != null) {
            String base = normalizeBaseName(options.inputFile);
            String ext = options.jsonOutput ? ".json" : (options.format == OutputFormat.MARKDOWN ? ".md" : ".txt");
            return options.outputDir.resolve(base + ext);
        }
        return null;
    }

    public static String normalizeBaseName(String inputFile) {
        String fileName = Path.of(inputFile).getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String lower = base.toLowerCase(Locale.ROOT);
        String dashed = lower.replaceAll("[^a-z0-9]+", "-");
        dashed = dashed.replaceAll("-+", "-");
        dashed = dashed.replaceAll("^-|-$", "");
        return dashed.isEmpty() ? "output" : dashed;
    }

    private static String usage() {
        return String.join("\n",
            "Usage:",
            "  java -cp <jar> com.composerai.api.service.HtmlToText --input-file <path> [--input-type eml|html] --format plain|markdown [--output-file <path>] [--output-dir <dir>] [--charset UTF-8] [--urls keep|stripAll|cleanOnly] [--metadata true|false] [--json true|false]",
            "",
            "Examples:",
            "  --input-file /path/to/email.eml --format markdown --urls cleanOnly --metadata true --json false",
            "  --input-file /path/to/email.html --input-type html --format plain --output-dir ./data --urls stripAll --metadata false --json true"
        );
    }
}
