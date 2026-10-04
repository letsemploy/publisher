package org.letsemploy.ojobpub_publisher.summary;

import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The application's one scheduled task (spec 7.28, 12): the weekly summary, on
 * {@code app.summary.cron}. Everything else time-dependent - the date window, token
 * expiry - is still evaluated when read.
 *
 * <p>Off with {@code app.summary.enabled=false}, which also leaves scheduling off
 * altogether. Every instance may run it: {@link SummaryService} claims each person
 * in the database, so a summary goes out once however many there are.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.summary.enabled", havingValue = "true", matchIfMissing = true)
public class SummaryScheduler {

    private final SummaryService summaries;

    public SummaryScheduler(SummaryService summaries) {
        this.summaries = summaries;
    }

    @Scheduled(cron = "${app.summary.cron:0 0 7 * * MON}", zone = "${app.summary.zone:}")
    public void run() {
        summaries.sendDue(Instant.now());
    }
}
