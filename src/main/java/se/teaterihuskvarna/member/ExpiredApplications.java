package se.teaterihuskvarna.member;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/// Deletes membership applications nobody confirmed in time, as
/// `docs/projektplan.md` promises: "An application nobody confirms is deleted
/// after 24 hours." An expired link already fails in
/// [MembershipApplicationService#confirm]; this job is what removes the
/// applicant's name and address from the database.
///
/// Hourly, so a row outlives its expiry by an hour at most.
@Component
class ExpiredApplications {

    private final MembershipApplicationRepository applications;

    ExpiredApplications(MembershipApplicationRepository applications) {
        this.applications = applications;
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void delete() {
        applications.deleteExpired(Instant.now());
    }
}
