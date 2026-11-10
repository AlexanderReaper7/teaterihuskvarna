package se.teaterihuskvarna.login;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/// Limits how often mail can be asked for, per address and per client IP
/// address, counted in PostgreSQL so a restart does not reset the count.
///
/// The two limits count different things. An address counts the requests that
/// were let through, so only mail that could have gone out uses up its
/// allowance. A client counts every request it made, refused ones too, so a
/// client that keeps asking stays over the limit instead of getting a new
/// allowance each window.
///
/// A refused request is therefore recorded without its address. Recorded with
/// it, a client over its own limit could fill any address's count by posting
/// that address, and a person who kept trying while over the address limit
/// would stay over it for as long as they tried.
///
/// What the address limit still allows: whoever sends as many requests as it
/// lets through, with somebody else's address, holds that address at its limit
/// for the window, and the owner gets that many mails they cannot use.
/// `docs/decisions/0015-login-links-on-spring-security.md` says what is open.
///
/// The rows go when the window has passed: [LoginCleanup].
///
/// Refusal is silent towards the caller. The caller shows the same page as for
/// a sent link, since an error only for known addresses would reveal which ones
/// exist, and an error for all of them would tell a script when to slow down.
/// Each refusal is logged with the limit that refused it, so an address held at
/// its limit shows in the log. Neither the address nor the client's IP address
/// is in the line: the log is not the place to keep who asked for a login link.
@Component
public class LinkRequestLimiter {

    /// The widths of `link_request.email` and `link_request.client_address`.
    /// Longer input is cut, not refused: an address that long has no account,
    /// and an oversized value must not become a 500.
    private static final int EMAIL_WIDTH = 254;
    private static final int CLIENT_WIDTH = 45;

    /// What a refused request has in `link_request.email`. No address is
    /// empty: the login filter sends a form without one back, and the
    /// application form refuses it. So the row counts against its client and
    /// against no address.
    private static final String NO_ADDRESS = "";

    private static final Logger LOG = LoggerFactory.getLogger(LinkRequestLimiter.class);

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
        String address = cut(new Email(email).value(), EMAIL_WIDTH);
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
        boolean addressOver = byAddress >= settings.requestsPerAddress();
        boolean clientOver = byClient >= settings.requestsPerClient();
        boolean allowed = !addressOver && !clientOver;
        jdbc.sql("INSERT INTO link_request (email, client_address, requested_at) VALUES (?, ?, ?)")
                .param(allowed ? address : NO_ADDRESS)
                .param(client)
                .param(now)
                .update();
        if (!allowed) {
            LOG.warn("Refused a request for mail: over the limit of {} in {}", limits(addressOver, clientOver),
                    settings.requestWindow());
        }
        return allowed;
    }

    /// @return the limits a refused request was over, as the log line names them
    private String limits(boolean addressOver, boolean clientOver) {
        String perAddress = settings.requestsPerAddress() + " per address";
        String perClient = settings.requestsPerClient() + " per client";
        if (addressOver && clientOver) {
            return perAddress + " and of " + perClient;
        }
        return addressOver ? perAddress : perClient;
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
