package com.eventpilot.application;

import com.eventpilot.domain.Venue;
import java.util.List;
import java.util.Optional;

/** Adapter pattern - the "Target" interface the rest of the system uses to find venues. */
public interface VenueCatalog {

    /** Search criteria; null fields are not checked. */
    record Criteria(String city, Integer minCapacity, Double maxPricePerDay, Boolean accessible,
                    String district, List<String> requiredFeatures) {
        public Criteria {
            requiredFeatures = requiredFeatures == null ? List.of() : List.copyOf(requiredFeatures);
        }
    }

    List<Venue> search(Criteria criteria);

    Optional<Venue> findById(String id);

    List<Venue> all();
}
