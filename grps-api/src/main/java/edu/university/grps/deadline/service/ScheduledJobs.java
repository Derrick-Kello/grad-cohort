package edu.university.grps.deadline.service;

import edu.university.grps.attendance.service.AttendanceService;
import edu.university.grps.evidence.service.EvidenceService;
import edu.university.grps.milestone.domain.PresentationAttempt;
import edu.university.grps.milestone.repository.MilestoneRepositories.PresentationAttemptRepository;
import edu.university.grps.notification.domain.NotificationCode;
import edu.university.grps.notification.service.NotificationService;
import edu.university.grps.settings.service.SettingsService;
import edu.university.grps.common.Times;
import edu.university.grps.student.service.StudentAccess;
import edu.university.grps.thesis.service.ThesisPipeline;
import edu.university.grps.user.service.AuthService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Background jobs (PRD section 24). Times are Africa/Accra. ShedLock makes each run happen once even with
 * several API instances (NFR-04).
 */
@Component
public class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);
    private static final String ZONE = "Africa/Accra";

    private final DeadlineService deadlines;
    private final AttendanceService attendance;
    private final ThesisPipeline thesisPipeline;
    private final NotificationService notifications;
    private final AuthService auth;
    private final EvidenceService evidence;
    private final PresentationAttemptRepository attempts;
    private final StudentAccess access;
    private final SettingsService settings;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ScheduledJobs(DeadlineService deadlines, AttendanceService attendance, ThesisPipeline thesisPipeline,
                         NotificationService notifications, AuthService auth, EvidenceService evidence,
                         PresentationAttemptRepository attempts, StudentAccess access, SettingsService settings,
                         TransactionTemplate tx, Clock clock) {
        this.deadlines = deadlines;
        this.attendance = attendance;
        this.thesisPipeline = thesisPipeline;
        this.notifications = notifications;
        this.auth = auth;
        this.evidence = evidence;
        this.attempts = attempts;
        this.access = access;
        this.settings = settings;
        this.tx = tx;
        this.clock = clock;
    }

    /** Job names usable from the local dev tools. */
    public Map<String, Supplier<Object>> registry() {
        Map<String, Supplier<Object>> jobs = new LinkedHashMap<>();
        jobs.put("deadline-evaluation", deadlines::evaluateAll);
        jobs.put("milestone-unlock", deadlines::unlockFirstMilestones);
        jobs.put("presentation-reminders", this::sendReminders);
        jobs.put("eligibility-recalc", () -> tx.execute(s -> attendance.recalculateAll()));
        jobs.put("screening-poll", thesisPipeline::pollAll);
        jobs.put("email-retry", notifications::retryFailedEmails);
        jobs.put("email-digest", notifications::sendDigests);
        jobs.put("token-cleanup", auth::cleanupTokens);
        jobs.put("pending-scan", () -> tx.execute(s -> evidence.rescanPending()));
        return jobs;
    }

    @Scheduled(cron = "0 0 1 * * *", zone = ZONE)
    @SchedulerLock(name = "DeadlineEvaluationJob", lockAtMostFor = "PT2H")
    public void deadlineEvaluationJob() {
        run("DeadlineEvaluationJob", deadlines::evaluateAll);
    }

    @Scheduled(cron = "0 30 0 * * *", zone = ZONE)
    @SchedulerLock(name = "MilestoneUnlockJob", lockAtMostFor = "PT1H")
    public void milestoneUnlockJob() {
        run("MilestoneUnlockJob", deadlines::unlockFirstMilestones);
    }

    @Scheduled(cron = "0 0 * * * *", zone = ZONE)
    @SchedulerLock(name = "PresentationReminderJob", lockAtMostFor = "PT30M")
    public void presentationReminderJob() {
        run("PresentationReminderJob", this::sendReminders);
    }

    @Scheduled(cron = "0 0 2 * * *", zone = ZONE)
    @SchedulerLock(name = "EligibilityRecalcJob", lockAtMostFor = "PT1H")
    public void eligibilityRecalcJob() {
        run("EligibilityRecalcJob", () -> tx.execute(s -> attendance.recalculateAll()));
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    @SchedulerLock(name = "ScreeningPollJob", lockAtMostFor = "PT4M")
    public void screeningPollJob() {
        run("ScreeningPollJob", thesisPipeline::pollAll);
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT2M")
    @SchedulerLock(name = "EmailRetryJob", lockAtMostFor = "PT4M")
    public void emailRetryJob() {
        run("EmailRetryJob", notifications::retryFailedEmails);
    }

    @Scheduled(cron = "0 0 7 * * *", zone = ZONE)
    @SchedulerLock(name = "EmailDigestJob", lockAtMostFor = "PT1H")
    public void emailDigestJob() {
        run("EmailDigestJob", notifications::sendDigests);
    }

    @Scheduled(cron = "0 0 3 * * *", zone = ZONE)
    @SchedulerLock(name = "TokenCleanupJob", lockAtMostFor = "PT30M")
    public void tokenCleanupJob() {
        run("TokenCleanupJob", auth::cleanupTokens);
    }

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT3M")
    @SchedulerLock(name = "PendingScanJob", lockAtMostFor = "PT9M")
    public void pendingScanJob() {
        run("PendingScanJob", () -> tx.execute(s -> evidence.rescanPending()));
    }

    /** N-04: reminders 48 h and 24 h before a presentation, once each. */
    public int sendReminders() {
        List<Integer> hours = settings.integers(SettingsService.REMINDER_HOURS);
        int first = hours.isEmpty() ? 48 : hours.get(0);
        int second = hours.size() > 1 ? hours.get(1) : 24;
        Integer sent = tx.execute(s -> {
            Instant now = clock.instant();
            int count = 0;
            for (PresentationAttempt a : attempts.upcomingOpen(now, now.plus(Duration.ofHours(first)))) {
                long hoursLeft = Duration.between(now, a.getScheduledStart()).toHours();
                boolean sendSecond = hoursLeft < second && !a.isReminder24Sent();
                boolean sendFirst = !sendSecond && !a.isReminder48Sent();
                if (!sendFirst && !sendSecond) continue;
                if (sendSecond) a.setReminder24Sent(true);
                a.setReminder48Sent(true);
                var m = a.getMilestone();
                var st = m.getStudent();
                String where = a.getVenue() != null ? a.getVenue().getName() : "online: " + a.getOnlineLink();
                notifications.notify(access.studentAndSupervisors(st), NotificationCode.N04,
                        "Reminder: " + m.label() + " " + (sendSecond ? "tomorrow" : "in two days"),
                        st.getUser().getFullName() + "'s " + m.label() + " (" + m.getTitle() + ") is on "
                                + Times.formatDateTime(a.getScheduledStart()) + " at " + where + ".", "/student/presentations");
                count++;
            }
            return count;
        });
        return sent == null ? 0 : sent;
    }

    private void run(String name, Supplier<Object> job) {
        long start = System.currentTimeMillis();
        try {
            Object result = job.get();
            log.info("{} finished in {} ms: {}", name, System.currentTimeMillis() - start, result);
        } catch (RuntimeException e) {
            log.error("{} failed", name, e);
        }
    }
}
