package com.eventpilot.infra;

import com.eventpilot.application.VenueCatalog;
import com.eventpilot.domain.Room;
import com.eventpilot.domain.Venue;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Adapter pattern - the "Adapter". Converts the dataset records read by
 * {@link VenueDatasetReader} into {@link Venue} objects and implements the
 * {@link VenueCatalog} search that tools and the facade use.
 */
public class JsonVenueCatalogAdapter implements VenueCatalog {

    private final VenueDatasetReader reader;
    private List<Venue> cache;

    public JsonVenueCatalogAdapter(VenueDatasetReader reader) {
        this.reader = reader;
    }

    @Override
    public synchronized List<Venue> all() {
        if (cache == null) {
            List<Venue> venues = new ArrayList<>();
            for (JsonNode record : reader.readRecords()) {
                venues.add(toVenue(record));
            }
            cache = List.copyOf(venues);
        }
        return cache;
    }

    static Venue toVenue(JsonNode r) {
        JsonNode loc = r.path("location");
        List<String> features = Arrays.stream(r.path("amenities").asText("").split(";"))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        List<Room> rooms = new ArrayList<>();
        r.path("spaces").forEach(s -> rooms.add(new Room(s.path("label").asText(), s.path("seats").asInt())));
        return new Venue(
                r.path("ref").asText(),
                r.path("venue_name").asText(),
                loc.path("city").asText(),
                loc.path("neighbourhood").asText(),
                loc.path("street").asText(),
                r.path("max_guests").asInt(),
                r.path("daily_rate_cad").asDouble(),
                r.path("wheelchair_access").asText("no").equalsIgnoreCase("yes"),
                r.path("review_score").asDouble() / 2.0, // dataset scores out of 10, we use 5 stars
                features,
                rooms);
    }

    @Override
    public List<Venue> search(Criteria c) {
        return all().stream()
                .filter(v -> c.city() == null || c.city().isBlank() || v.city().equalsIgnoreCase(c.city().trim()))
                .filter(v -> c.minCapacity() == null || v.capacity() >= c.minCapacity())
                .filter(v -> c.maxPricePerDay() == null || v.pricePerDay() <= c.maxPricePerDay())
                .filter(v -> c.accessible() == null || !c.accessible() || v.accessible())
                .filter(v -> c.district() == null || c.district().isBlank()
                        || v.district().toLowerCase(Locale.ROOT).contains(c.district().toLowerCase(Locale.ROOT)))
                .filter(v -> c.requiredFeatures().stream().allMatch(f -> v.features().stream()
                        .anyMatch(vf -> vf.toLowerCase(Locale.ROOT).contains(f.toLowerCase(Locale.ROOT)))))
                .sorted(Comparator.comparing(Venue::rating).reversed())
                .toList();
    }

    @Override
    public Optional<Venue> findById(String id) {
        return all().stream().filter(v -> v.id().equalsIgnoreCase(id)).findFirst();
    }
}
