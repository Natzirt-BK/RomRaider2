/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/** Bounded UTF-8 properties, strict schema and atomic setup-only file replacement. */
public final class FuelAnalysisSetupStore {
    public static final int MAX_BYTES = 32768;
    public static final String EXTENSION = ".rr2analysis";

    public FuelAnalysisSetup read(Path path) throws IOException {
        requireSetupPath(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Choose a regular setup file");
        byte[] bytes;
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) throw new IOException("Analysis setup exceeds 32 KiB");
        try {
            String content = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            Properties values = new Properties() {
                @Override public synchronized Object put(Object key, Object value) {
                    if (containsKey(key)) throw new IllegalArgumentException("Duplicate setup field: " + key);
                    return super.put(key, value);
                }
            };
            values.load(new StringReader(content));
            Set<String> used = new HashSet<>();
            String version = required(values, used, "format.version");
            if (!Set.of("1", "2").contains(version)) throw new IllegalArgumentException("Unsupported analysis setup version");
            FuelAnalysisSetup.Kind kind = FuelAnalysisSetup.Kind.valueOf(required(values, used, "kind"));
            var x = channel(values, used, "x"); var y = channel(values, used, "y");
            var correction = kind == FuelAnalysisSetup.Kind.MAF ? channel(values, used, "correction") : null;
            double width = number(values, used, "bin.width");
            double afr = number(values, used, "stoich.afr"), density = number(values, used, "fuel.density");
            int count = Integer.parseInt(required(values, used, "filter.count"));
            if (count < 0 || count > 3) throw new IllegalArgumentException("Invalid analysis filter count");
            var filters = new ArrayList<FuelAnalysisSetup.Filter>();
            for (int i = 0; i < count; i++) {
                String prefix = "filter." + i;
                filters.add(new FuelAnalysisSetup.Filter(channel(values, used, prefix),
                        number(values, used, prefix + ".minimum"), number(values, used, prefix + ".maximum")));
            }
            FuelAnalysisSetup.Rate rate = null;
            if (version.equals("2")) rate = new FuelAnalysisSetup.Rate(channel(values, used, "rate.signal"), channel(values, used, "rate.time"),
                    number(values, used, "rate.seconds.per.time.unit"), number(values, used, "rate.maximum"), number(values, used, "rate.maximum.gap.seconds"));
            if (!used.equals(values.stringPropertyNames())) throw new IllegalArgumentException("Unknown analysis setup fields");
            return new FuelAnalysisSetup(kind, x, y, correction, width, afr, density, filters, rate);
        } catch (IllegalArgumentException failure) {
            throw new IOException("Invalid analysis setup: " + failure.getMessage(), failure);
        }
    }

    public void write(Path path, FuelAnalysisSetup setup) throws IOException {
        requireSetupPath(path);
        if (setup == null) throw new IllegalArgumentException("Analysis setup is required");
        Path target = path.toAbsolutePath();
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Cannot replace a directory or linked setup file");
        }
        Properties values = new Properties();
        values.setProperty("format.version", setup.rate() == null ? "1" : "2"); values.setProperty("kind", setup.kind().name());
        putChannel(values, "x", setup.x()); putChannel(values, "y", setup.y());
        if (setup.correction() != null) putChannel(values, "correction", setup.correction());
        values.setProperty("bin.width", Double.toString(setup.binWidth()));
        values.setProperty("stoich.afr", Double.toString(setup.stoichAfr()));
        values.setProperty("fuel.density", Double.toString(setup.fuelDensity()));
        values.setProperty("filter.count", Integer.toString(setup.filters().size()));
        if (setup.rate() != null) {
            var rate = setup.rate(); putChannel(values, "rate.signal", rate.signal()); putChannel(values, "rate.time", rate.time());
            values.setProperty("rate.seconds.per.time.unit", Double.toString(rate.secondsPerTimeUnit()));
            values.setProperty("rate.maximum", Double.toString(rate.maximumRate()));
            values.setProperty("rate.maximum.gap.seconds", Double.toString(rate.maximumGapSeconds()));
        }
        for (int i = 0; i < setup.filters().size(); i++) {
            var filter = setup.filters().get(i); String prefix = "filter." + i;
            putChannel(values, prefix, filter.channel());
            values.setProperty(prefix + ".minimum", Double.toString(filter.minimum()));
            values.setProperty(prefix + ".maximum", Double.toString(filter.maximum()));
        }
        StringWriter output = new StringWriter();
        values.store(output, "RomRaider2 analysis setup; review units, filters and sample range after import");
        byte[] bytes = output.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Analysis setup exceeds 32 KiB");
        Path temporary = Files.createTempFile(target.getParent(), ".rr2-analysis-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            // No non-atomic fallback: a failed save must preserve the original.
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void requireSetupPath(Path path) throws IOException {
        if (path == null || path.getFileName() == null
                || !path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(EXTENSION)) {
            throw new IOException("Choose an " + EXTENSION + " file; CSV and ROM files cannot be overwritten");
        }
    }
    private static String required(Properties values, Set<String> used, String key) {
        String value = values.getProperty(key); used.add(key);
        if (value == null) throw new IllegalArgumentException("Missing setup field: " + key);
        return value;
    }
    private static double number(Properties values, Set<String> used, String key) {
        return Double.parseDouble(required(values, used, key));
    }
    private static FuelAnalysisSetup.Channel channel(Properties values, Set<String> used, String key) {
        return new FuelAnalysisSetup.Channel(required(values, used, key + ".label"), required(values, used, key + ".units"));
    }
    private static void putChannel(Properties values, String key, FuelAnalysisSetup.Channel channel) {
        values.setProperty(key + ".label", channel.label()); values.setProperty(key + ".units", channel.units());
    }
}
