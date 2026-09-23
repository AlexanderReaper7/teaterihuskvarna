package se.teaterihuskvarna.login;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/// Deletes login links nobody used and link requests older than the rate
/// limit's window. `docs/projektplan.md` says the client IP addresses in
/// `link_request` go along with expired tokens, so they are not kept longer
/// than the limit needs them.
///
/// Correctness does not depend on this job. An expired token redeems nothing
/// and an old request is outside the window either way; the job only keeps the
/// tables, and the personal data in them, from growing.
@Component
class LoginCleanup {

    private static final Logger LOG = LoggerFactory.getLogger(LoginCleanup.class);

    private final JdbcClient jdbc;
    private final LoginSettings settings;

    LoginCleanup(JdbcClient jdbc, LoginSettings settings) {
        this.jdbc = jdbc;
        this.settings = settings;
    }

    /// Runs every 15 minutes, counted from the end of the previous run.
    @Scheduled(fixedDelayString = "PT15M")
    void deleteExpired() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int tokens = jdbc.sql("DELETE FROM one_time_token WHERE expires_at <= ?")
                .param(now)
                .update();
        int requests = jdbc.sql("DELETE FROM link_request WHERE requested_at <= ?")
                .param(now.minus(settings.requestWindow()))
                .update();
        LOG.debug("Deleted {} expired login links and {} old link requests", tokens, requests);
    }
}
