package org.letsemploy.ojobpub_publisher.location;

import com.neovisionaries.i18n.CountryCode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepo locationRepo;

    public List<Location> findAll() {
        return locationRepo.findAllByOrderByCityAsc();
    }

    public Location findById(UUID id) {
        return locationRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Location not found: " + id));
    }

    public List<Location> search(String query) {
        if (query == null || query.isBlank()) {
            return locationRepo.findAllByOrderByCityAsc().stream().limit(20).toList();
        }
        return locationRepo.findTop20ByCityContainingIgnoreCaseOrderByCityAsc(query.trim());
    }

    public long usageCount(UUID id) {
        return locationRepo.countUsages(id);
    }

    @Transactional
    public Location save(UUID id, String city, String countryCode) {
        if (city == null || city.isBlank()) {
            throw new ValidationFailure("city", "A city is required.");
        }
        CountryCode country = CountryCode.getByCodeIgnoreCase(countryCode);
        if (country == null || country == CountryCode.UNDEFINED) {
            throw new ValidationFailure("country", "Select a country.");
        }
        Location location = id == null ? new Location() : findById(id);
        location.setCity(city.trim());
        location.setCountry(country);
        return locationRepo.save(location);
    }

    /** A location still referenced cannot be deleted; the UI names what uses it (spec 7.14). */
    @Transactional
    public void delete(UUID id) {
        Location location = findById(id);
        if (usageCount(id) > 0) {
            throw new ValidationFailure("id", "This location is still in use.");
        }
        locationRepo.delete(location);
    }
}
