package org.letsemploy.ojobpub_publisher.summary;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.letsemploy.ojobpub_publisher.click.JobClickService;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerService;
import org.letsemploy.ojobpub_publisher.job.JobService;
import org.letsemploy.ojobpub_publisher.mail.Mail;
import org.letsemploy.ojobpub_publisher.mail.Mailer;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.membership.MembershipService;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.letsemploy.ojobpub_publisher.token.ServiceTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The weekly summary mail (spec 7.28): one mail per person who asked for it,
 * covering every employer they are an active member of.
 *
 * <p>Each person is claimed before anything is read: a conditional update stamps
 * {@code summary_sent_at} unless this week's summary was already claimed, so of
 * several instances running the same schedule only one sends. The claim stands
 * even when the week had nothing to report, which is what stops a later run from
 * looking again.
 *
 * <p>The summary reads as the person would, through the same services, with an
 * actor built from their active memberships and never as an admin: a platform
 * admin's mail covers the employers they belong to, not every employer there is.
 */
@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    /**
     * How long after one claim the next is allowed. Less than a week, so a run that
     * starts a little earlier than last week's still finds everyone due.
     */
    static final Duration INTERVAL = Duration.ofDays(6);

    private final UserRepo userRepo;
    private final MembershipService membershipService;
    private final EmployerService employerService;
    private final JobService jobService;
    private final JobClickService clickService;
    private final ServiceTokenService tokenService;
    private final Mailer mailer;
    private final TransactionTemplate transaction;
    private final String baseUrl;

    public SummaryService(UserRepo userRepo,
                          MembershipService membershipService,
                          EmployerService employerService,
                          JobService jobService,
                          JobClickService clickService,
                          ServiceTokenService tokenService,
                          Mailer mailer,
                          PlatformTransactionManager transactions,
                          @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.userRepo = userRepo;
        this.membershipService = membershipService;
        this.employerService = employerService;
        this.jobService = jobService;
        this.clickService = clickService;
        this.tokenService = tokenService;
        this.mailer = mailer;
        this.transaction = new TransactionTemplate(transactions);
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * Sends this week's summary to everyone due. Without a mail server it does
     * nothing at all, and claims nobody, so the first run after one is configured
     * still reaches everyone.
     *
     * @return how many mails were sent
     */
    public int sendDue(Instant now) {
        if (!mailer.configured()) {
            return 0;
        }
        List<UUID> subscribed = userRepo.findByMailSummaryIsTrueAndSuspendedAtIsNullAndEmailIsNotNull().stream()
                .map(UserEntity::getId)
                .toList();
        int sent = 0;
        for (UUID userId : subscribed) {
            // One transaction each: a failure for one person neither stops the
            // others nor undoes their claims. The mail goes out after its commit.
            try {
                if (Boolean.TRUE.equals(transaction.execute(status -> sendTo(userId, now)))) {
                    sent++;
                }
            } catch (RuntimeException e) {
                log.error("Weekly summary failed for user {}", userId, e);
            }
        }
        log.info("Weekly summary: {} mail(s) sent, {} subscribed", sent, subscribed.size());
        return sent;
    }

    private boolean sendTo(UUID userId, Instant now) {
        if (userRepo.claimSummary(userId, now, now.minus(INTERVAL)) == 0) {
            return false;
        }
        UserEntity user = userRepo.findById(userId).orElse(null);
        if (user == null || user.isSuspended() || user.getEmail() == null || !user.isMailSummary()) {
            return false;
        }
        Map<UUID, MembershipRole> roles = membershipService.activeRolesOf(userId);
        if (roles.isEmpty()) {
            return false;
        }
        Actor actor = Actor.user(user.getId(), user.getLabel(), user.getEmail(), false, roles);
        ZoneId zone = user.getTimeZone() != null ? ZoneId.of(user.getTimeZone()) : ZoneId.systemDefault();
        LocalDate today = LocalDate.ofInstant(now, ZoneId.systemDefault());
        Instant weekAgo = now.minus(Duration.ofDays(WeeklySummary.DAYS));

        List<WeeklySummary> summaries = new ArrayList<>();
        for (Employer employer : employerService.visibleTo(actor)) {
            UUID id = employer.getId();
            WeeklySummary summary = WeeklySummary.of(id, employer.getName(),
                    jobService.jobsOf(id, actor),
                    jobService.deactivatedSince(id, weekAgo, actor),
                    clickService.statistics(List.of(id), LocalDate.ofInstant(now, ZoneOffset.UTC),
                            WeeklySummary.DAYS, WeeklySummary.TOP_JOBS),
                    actor.isOwnerOf(id) ? tokenService.forEmployer(id, actor) : List.of(),
                    tokenService.getExpiryWarningDays(), today, now, zone);
            if (!summary.isEmpty()) {
                summaries.add(summary);
            }
        }
        if (summaries.isEmpty()) {
            return false;
        }
        mailer.send(new Mail(user.getEmail(), "summary", "mail.summary.subject",
                Map.of("name", user.getLabel(), "summaries", summaries, "base", baseUrl),
                user.getLanguage() != null ? Locale.forLanguageTag(user.getLanguage()) : Locale.ENGLISH));
        return true;
    }
}
