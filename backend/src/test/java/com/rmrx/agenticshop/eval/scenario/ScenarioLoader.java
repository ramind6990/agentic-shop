package com.rmrx.agenticshop.eval.scenario;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds the scenario files on the test classpath ({@code eval/scenarios/**.yaml}) and reads one. Unknown keys fail the
 * read (typos in the world or the turns would otherwise be silently ignored); {@code expect} is free-form.
 */
public final class ScenarioLoader {

    private static final String ROOT = "eval/scenarios";

    private static final YAMLMapper YAML = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private ScenarioLoader() {
    }

    /**
     * Scenario ids (file paths below {@code eval/scenarios} without {@code .yaml}, with {@code /}) matching one of the
     * comma-separated globs ({@code *} within a folder, {@code **} across folders), sorted.
     */
    public static List<String> ids(String globs) {
        Path root = root();
        List<Pattern> patterns = Arrays.stream((globs == null || globs.isBlank() ? "**" : globs).split(","))
                .map(String::trim)
                .filter(g -> !g.isEmpty())
                .map(ScenarioLoader::globToRegex)
                .toList();
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.toString().endsWith(".yaml"))
                    .map(p -> toId(root, p))
                    .filter(id -> patterns.stream().anyMatch(p -> p.matcher(id).matches()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reads the scenario again (a fresh, mutable world each time). */
    public static Scenario load(String id) {
        Path file = root().resolve(id + ".yaml");
        try (InputStream in = Files.newInputStream(file)) {
            return YAML.readValue(in, Scenario.class).withId(id);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("scenario " + id + " cannot be read: " + e.getMessage(), e);
        }
    }

    private static Pattern globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*' && i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                regex.append(".*");
                i++;
            } else if (c == '*') {
                regex.append("[^/]*");
            } else if (c == '?') {
                regex.append("[^/]");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString());
    }

    private static String toId(Path root, Path file) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        return relative.substring(0, relative.length() - ".yaml".length());
    }

    private static Path root() {
        URL url = ScenarioLoader.class.getClassLoader().getResource(ROOT);
        if (url == null) throw new IllegalStateException("no " + ROOT + " on the test classpath");
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
