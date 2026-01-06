package org.letsemploy.ojobpub_publisher.web.view;

import java.util.List;
import lombok.Value;

@Value
public class JobFormView {
    String id;
    String title;
    String description;
    String url;
    String language;
    String referenceId;
    String category;
    String jobType;
    String workType;
    String experienceLevel;
    String workLoadPercentMin;
    String workLoadPercentMax;
    String salaryMin;
    String salaryMax;
    String salaryCurrency;
    String salaryInterval;
    String startDate;
    String endDate;
    String applyBefore;
    List<Ref> tags;
    List<Ref> locations;

    /** Currency and interval are required as soon as an amount is present (spec 3.3). */
    public boolean isSalaryPresent() {
        return notBlank(salaryMin) || notBlank(salaryMax);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
