package com.eventpilot.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventpilot.application.MessageGateway;
import com.eventpilot.application.VenueCatalog;
import com.eventpilot.domain.DomainException;
import com.eventpilot.domain.Invitation;
import com.eventpilot.domain.Venue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdaptersTest {

    @TempDir
    Path tmp;

    @Test
    void adapterConvertsDatasetFieldsToVenue() {
        VenueCatalog catalog = new JsonVenueCatalogAdapter(new VenueDatasetReader());
        Venue v = catalog.findById("TO-002").orElseThrow();
        assertEquals("Spadina Innovation Loft", v.name());
        assertEquals(150, v.capacity());
        assertEquals(4.3, v.rating(), 0.001);
        assertTrue(v.features().contains("whiteboards"));
        assertEquals(3, v.rooms().size());
    }

    @Test
    void searchFiltersByCityCapacityPriceAndAccessibility() {
        VenueCatalog catalog = new JsonVenueCatalogAdapter(new VenueDatasetReader());
        List<Venue> result = catalog.search(new VenueCatalog.Criteria("Toronto", 120, 2500.0, true, null, List.of()));
        assertFalse(result.isEmpty());
        assertTrue(result.stream().allMatch(v -> v.capacity() >= 120 && v.pricePerDay() <= 2500 && v.accessible()));
        assertTrue(catalog.search(new VenueCatalog.Criteria("Toronto", 10000, null, null, null, List.of())).isEmpty());
    }

    @Test
    void csvImportReportsBadRowsAndDuplicates() {
        AttendeeCsvImporter.ImportReport report = new AttendeeCsvImporter()
                .parse(Path.of("samples/attendees.csv"), Set.of("grace.lee@example.com"));
        assertEquals(11, report.imported().size());
        assertEquals(2, report.rejected().size());
        assertEquals(2, report.duplicates().size());
        assertTrue(report.rejected().stream().anyMatch(r -> r.line() == 14));
    }

    @Test
    void csvImportRejectsWrongHeader() {
        DomainException ex = assertThrows(DomainException.class,
                () -> new AttendeeCsvImporter().parse(List.of("first,last"), Set.of()));
        assertTrue(ex.getMessage().contains("Expected"));
    }

    @Test
    void outboxWritesFilesAndRejectsInvalidDomain() throws Exception {
        OutboxFileGateway gateway = new OutboxFileGateway(tmp.resolve("outbox"));
        gateway.send(new Invitation("p1", "a@example.com", "GUEST", "Hi", "Body"));
        assertTrue(Files.exists(tmp.resolve("outbox/a_example.com.eml")));
        assertThrows(MessageGateway.DeliveryException.class,
                () -> gateway.send(new Invitation("p2", "b@example.invalid", "GUEST", "Hi", "Body")));
    }
}
