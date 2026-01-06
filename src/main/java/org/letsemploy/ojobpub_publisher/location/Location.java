package org.letsemploy.ojobpub_publisher.location;

import com.neovisionaries.i18n.CountryCode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.letsemploy.ojobpub_publisher.common.Base;

@Entity
@Table(name = "locations")
@Getter
@Setter
@NoArgsConstructor
public class Location extends Base {

    @Column(nullable = false)
    private String city;

    /**
     * Persisted as a string, never as an ordinal (spec 3.2). The constant names of
     * {@link CountryCode} are the ISO 3166-1 alpha-2 codes, so the stored value is
     * the published value.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private CountryCode country;

    public Location(String city, CountryCode country) {
        this.city = city;
        this.country = country;
    }

    /** The alpha-2 code as the published document must carry it: upper case (spec 6.3). */
    public String getCountryCode() {
        return country.getAlpha2().toUpperCase();
    }

    public String getLabel() {
        return city + ", " + getCountryCode();
    }

    @Override
    public String toString() {
        return getLabel();
    }
}
