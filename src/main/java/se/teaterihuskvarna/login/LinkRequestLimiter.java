package se.teaterihuskvarna.login;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/// Limits how often mail can be asked for, per address and per client IP
/// address, counted in PostgreSQL so a restart does not reset the count.
///
/// Every request is recorded, refused ones too, so a client that keeps asking
/// stays over the limit instead of getting a new allowance each window. The
/// rows go when the window has passed: [LoginCleanup].
///
/// Refusal is silent. The caller shows the same page as for a sent link, since
/// an error only for known addresses would reveal which ones exist, and an
/// error for all of them would tell a script when to slow down.
@Component
public class LinkRequestLimiter {

    /// The widths of `link_request.email` and `link_request.client_address`.
    /// Longer input is cut, not refused: an address that long has no account,
    /// and an oversized value must not become a 500.
    private static final int EMAIL_WIDTH = 254;
    private static final int CLIENT_WIDTH = 45;

    private final JdbcClient jdbc;
    private final LoginSettings settings;

    LinkRequestLimiter(JdbcClient jdbc, LoginSettings settings) {
        this.jdbc = jdbc;
        this.settings = settings;
    }

    /// Records one request for mail and says whether to send it.
    ///
    /// @param email         the address as somebody typed it
    /// @param clientAddress the client's IP address
    /// @return true if both the address and the client are within their limits
    @Transactional
    public boolean tryAcquire(String email, String clientAddress) {
        String address = cut(Addresses.normalise(email), EMAIL_WIDTH);
        String client = cut(clientAddress, CLIENT_WIDTH);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime since = now.minus(settings.requestWindow());
        // Without the locks, requests sent at once all count before any of them
        // inserts, so all of them pass. With them, each waits for the one
        // before to commit, and then counts its row. The address is always
        // locked before the client, so two requests cannot each hold the lock
        // the other waits for.
        lock("link_request email " + address);
        lock("link_request client " + client);
        long byAddress = jdbc.sql("SELECT COUNT(*) FROM link_request WHERE email = ? AND requested_at > ?")
                .param(address)
                .param(since)
                .query(Long.class)
                .single();
        long byClient = jdbc.sql("SELECT COUNT(*) FROM link_request WHERE client_address = ? AND requested_at > ?")
                .param(client)
                .param(since)
                .query(Long.class)
                .single();
        jdbc.sql("INSERT INTO link_request (email, client_address, requested_at) VALUES (?, ?, ?)")
                .param(address)
                .param(client)
                .param(now)
                .update();
        return byAddress < settings.requestsPerAddress() && byClient < settings.requestsPerClient();
    }

    /// Held until the transaction ends. Two keys whose hashes collide only make
    /// requests wait for each other, which costs time and nothing else.
    private void lock(String key) {
        jdbc.sql("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(?, 0))) AS locked")
                .param(key)
                .query(Integer.class)
                .single();
    }

    private static String cut(String value, int width) {
        return value.length() > width ? value.substring(0, width) : value;
    }
}
