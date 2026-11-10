package se.teaterihuskvarna.member;

import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/// Deletes invitations nobody accepted in time. An expired link already fails
/// in [InvitationService#accept]; this job removes the address it was sent to,
/// and lets the member page offer a new invitation.
///
/// Hourly, so a row outlives its expiry by an hour at most.
@Component
class ExpiredInvitations {

    private final InvitationRepository invitations;
    private final Clock clock;

    ExpiredInvitations(InvitationRepository invitations, Clock clock) {
        this.invitations = invitations;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void delete() {
        invitations.deleteExpired(clock.instant());
    }
}
