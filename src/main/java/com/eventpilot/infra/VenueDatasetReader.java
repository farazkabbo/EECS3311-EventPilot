package com.eventpilot.infra;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter pattern - the "Adaptee". Reads the raw venue dataset, whose field
 * names and units (e.g. "max_guests", "daily_rate_cad", review score out of 10,
 * "amenities" as a ';'-separated string) do not match our {@code Venue} class.
 */
public class VenueDatasetReader {

    private final Path externalFile;

    /** Reads the bundled sample dataset. */
    public VenueDatasetReader() {
        this(null);
    }

    /** Reads an external dataset file in the same format (e.g. a real venue export). */
    public VenueDatasetReader(Path externalFile) {
        this.externalFile = externalFile;
    }

    public List<JsonNode> readRecords() {
        try (InputStream in = open()) {
            JsonNode root = Json.MAPPER.readTree(in);
            List<JsonNode> records = new ArrayList<>();
            root.path("records").forEach(records::add);
            return records;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read venue dataset: " + e.getMessage(), e);
        }
    }

    private InputStream open() throws IOException {
        if (externalFile != null) {
            return Files.newInputStream(externalFile);
        }
        InputStream in = VenueDatasetReader.class.getResourceAsStream("/data/venues.json");
        if (in == null) {
            throw new IOException("bundled /data/venues.json not found");
        }
        return in;
    }
}
