package com.rmrx.agenticshop.eval.transcript;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the transcripts of one eval run to {@code <output-dir>/<yyyyMMdd-HHmmss>/}: one
 * {@code <scenario id with / as __>__r<n>.json} per scenario run, and {@code manifest.json} listing them (rewritten
 * after each, so an interrupted run still has a valid manifest).
 */
public final class TranscriptWriter {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final JsonMapper json = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
    private final Path dir;
    private final Instant startedAt = Instant.now();
    private final Map<String, Object> settings;
    private final List<Map<String, Object>> entries = new ArrayList<>();

    public TranscriptWriter(Path outputDir, Map<String, Object> settings) {
        this.dir = outputDir.resolve(STAMP.format(startedAt));
        this.settings = settings;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path dir() {
        return dir;
    }

    public synchronized Path write(Transcript transcript) {
        String file = transcript.scenario().id().replace("/", "__") + "__r" + transcript.repetition() + ".json";
        Path path = dir.resolve(file);
        json.writeValue(path.toFile(), transcript);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("file", file);
        entry.put("scenario", transcript.scenario().id());
        entry.put("repetition", transcript.repetition());
        entry.put("harnessErrors", transcript.harnessErrors().size());
        entries.add(entry);
        writeManifest();
        return path;
    }

    private void writeManifest() {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", Transcript.VERSION);
        manifest.put("startedAt", startedAt);
        manifest.put("settings", settings);
        manifest.put("transcripts", entries);
        json.writeValue(dir.resolve("manifest.json").toFile(), manifest);
    }
}
