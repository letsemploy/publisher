package org.letsemploy.ojobpub_publisher.web.view;

import lombok.Value;

@Value
public class EmployerFormView {
    String id;
    String name;
    String slug;
    String url;
    String industry;
    /** One of the employer's own locations, by id - editing only. */
    String headquarters;
    /** Or a new location, which joins the employer's locations (spec 3.1). */
    String hqCity;
    String hqCountry;
}
