package org.letsemploy.ojobpub_publisher.ojobpub.v1.service;

import java.util.Map;
import org.letsemploy.ojobpub_publisher.job.ExperienceLevel;
import org.letsemploy.ojobpub_publisher.job.JobType;
import org.letsemploy.ojobpub_publisher.job.SalaryInterval;
import org.letsemploy.ojobpub_publisher.job.WorkType;

/**
 * Explicit mapping tables for every published enum (spec 6.5).
 *
 * <p>Deriving these from {@code name().toLowerCase()} is forbidden: it produces
 * {@code on_site} where the schema requires {@code on-site}, and it silently
 * breaks the contract whenever a constant is renamed.
 */
public final class OjobpubEnums {

    private static final Map<JobType, String> JOB_TYPE = Map.of(
            JobType.PERMANENT, "permanent",
            JobType.CONTRACT, "contract",
            JobType.TEMPORARY, "temporary",
            JobType.FREELANCE, "freelance",
            JobType.VOLUNTEER, "volunteer",
            JobType.APPRENTICESHIP, "apprenticeship",
            JobType.INTERNSHIP, "internship");

    private static final Map<WorkType, String> WORK_TYPE = Map.of(
            WorkType.ON_SITE, "on-site",
            WorkType.REMOTE, "remote",
            WorkType.HYBRID, "hybrid");

    private static final Map<ExperienceLevel, String> EXPERIENCE = Map.of(
            ExperienceLevel.JUNIOR, "junior",
            ExperienceLevel.MID, "mid",
            ExperienceLevel.SENIOR, "senior",
            ExperienceLevel.LEAD, "lead",
            ExperienceLevel.MANAGER, "manager",
            ExperienceLevel.DIRECTOR, "director",
            ExperienceLevel.EXECUTIVE, "executive");

    private static final Map<SalaryInterval, String> INTERVAL = Map.of(
            SalaryInterval.HOURLY, "hourly",
            SalaryInterval.DAILY, "daily",
            SalaryInterval.WEEKLY, "weekly",
            SalaryInterval.MONTHLY, "monthly",
            SalaryInterval.YEARLY, "yearly");

    private OjobpubEnums() {
    }

    public static String jobType(JobType v) {
        return v == null ? null : require(JOB_TYPE.get(v), v);
    }

    public static String workType(WorkType v) {
        return v == null ? null : require(WORK_TYPE.get(v), v);
    }

    public static String experienceLevel(ExperienceLevel v) {
        return v == null ? null : require(EXPERIENCE.get(v), v);
    }

    public static String salaryInterval(SalaryInterval v) {
        return v == null ? null : require(INTERVAL.get(v), v);
    }

    /** A constant added without a mapping must fail loudly, not emit something invalid. */
    private static String require(String mapped, Enum<?> source) {
        if (mapped == null) {
            throw new IllegalStateException(
                    "No ojobpub mapping for " + source.getDeclaringClass().getSimpleName() + "." + source.name());
        }
        return mapped;
    }
}
