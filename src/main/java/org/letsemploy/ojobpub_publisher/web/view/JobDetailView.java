package org.letsemploy.ojobpub_publisher.web.view;

import java.util.List;
import lombok.Value;

@Value
public class JobDetailView {
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
    String workLoad;
    String salary;
    String publishedAt;
    String startDate;
    String endDate;
    String applyBefore;
    PublicationStatus status;
    List<String> locations;
    List<String> tags;
    List<ReadinessCheck> readiness;
    List<FeedMembership> feeds;
    List<TransitionEvent> history;
    List<String> availableTransitions;

    public boolean isPublishable() {
        return readiness.stream().allMatch(ReadinessCheck::isSatisfied);
    }

    public long getUnmetCount() {
        return readiness.stream().filter(c -> !c.isSatisfied()).count();
    }
}
