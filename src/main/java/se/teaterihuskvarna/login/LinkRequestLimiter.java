package se.teaterihuskvarna.login;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/// Limits how often mail can be asked for, counted in PostgreSQL so a restart
/// does not reset the count. `docs/decisions/0015-login-links-on-spring-security.md`
/// has the reasoning, under "The rate limit answers like a success".
///
/// Three limits, each over the same window:
///
/// - One client's allowance for one address counts the requests that were let
///   through for that address from that client. The user chose on 2026-11-10
///   to count by IP address, so that whoever uses up an allowance with
///   somebody else's address uses up their own and not the owner's.
/// - An address's total counts the requests let through for it from every
///   client, so many clients together cannot send one address mail without
///   end.
/// - A client counts every request it made, for any address, refused ones
///   too, so a client that keeps asking stays over the limit instead of
///   getting a new allowance each window.
///
/// A refused request is therefore recorded without its address. Recorded with
/// it, a client over its own limit could fill any address's count by posting
/// that address, and a person who kept trying while over an allowance would
/// stay over it for as long as they tried.
///
/// A client is an IPv4 address, or the /64 network of an IPv6 address: a
/// connection that has one IPv6 address has every address in its /64, and
/// would otherwise be a new client for each.
///
/// What the limits still allow: clients on enough different addresses can
/// together hold somebody else's address at its total for the window, and the
/// owner gets that many mails they cannot use.
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

    /// The 16-bit groups of an IPv6 address that name its /64 network.
    private static final int NETWORK_GROUPS = 4;

    /// What a refused request has in `link_request.email`. No address is
    /// empty: the login filter sends a form without one back, and the
    /// application form refuses it. So the row counts against its client and
    /// against no address.
    private static final String NO_ADDRESS = "";

    private static final Logger LOG = LoggerFactory.getLogger(LinkRequestLimiter.class);

    private final JdbcClient jdbc;
    private final LoginSettings settings;
    private final Clock clock;

    LinkRequestLimiter(JdbcClient jdbc, LoginSettings settings, Clock clock) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.clock = clock;
    }

    /// Records one request for mail and says whether to send it.
    ///
    /// @param email         the address as somebody typed it
    /// @param clientAddress the client's IP address
    /// @return true if the request is within all three limits
    @Transactional
    public boolean tryAcquire(String email, String clientAddress) {
        String address = cut(new Email(email).value(), EMAIL_WIDTH);
        String client = client(clientAddress);
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime since = now.minus(settings.requestWindow());
        // Without the locks, requests sent at once all count before any of them
        // inserts, so all of them pass. With them, each waits for the one
        // before to commit, and then counts its row. The address is always
        // locked before the client, so two requests cannot each hold the lock
        // the other waits for. The address's lock covers the client's
        // allowance for it too.
        lock("link_request email " + address);
        lock("link_request client " + client);
        long byAddressAndClient = jdbc.sql(
                "SELECT COUNT(*) FROM link_request WHERE email = ? AND client_address = ? AND requested_at > ?")
                .param(address)
                .param(client)
                .param(since)
                .query(Long.class)
                .single();
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
        List<String> over = new ArrayList<>();
        if (byAddressAndClient >= settings.requestsPerAddressAndClient()) {
            over.add(settings.requestsPerAddressAndClient() + " per address from one client");
        }
        if (byAddress >= settings.requestsPerAddress()) {
            over.add(settings.requestsPerAddress() + " per address");
        }
        if (byClient >= settings.requestsPerClient()) {
            over.add(settings.requestsPerClient() + " per client");
        }
        boolean allowed = over.isEmpty();
        jdbc.sql("INSERT INTO link_request (email, client_address, requested_at) VALUES (?, ?, ?)")
                .param(allowed ? address : NO_ADDRESS)
                .param(client)
                .param(now)
                .update();
        if (!allowed) {
            LOG.warn("Refused a request for mail: over the limit of {} in {}", String.join(" and of ", over),
                    settings.requestWindow());
        }
        return allowed;
    }

    /// What a request counts against as its client, and what
    /// `link_request.client_address` holds: an IPv4 address whole, and an IPv6
    /// address as its /64 network, such as `2001:db8:1:2::/64`. Anything that
    /// is not an IP address is kept as it came, cut to the column's width.
    ///
    /// @param clientAddress the client's IP address as the servlet container gives it
    /// @return the client
    static String client(String clientAddress) {
        InetAddress parsed;
        try {
            parsed = InetAddress.ofLiteral(clientAddress);
        } catch (IllegalArgumentException notAnAddress) {
            return cut(clientAddress, CLIENT_WIDTH);
        }
        if (!(parsed instanceof Inet6Address)) {
            return cut(clientAddress, CLIENT_WIDTH);
        }
        byte[] bytes = parsed.getAddress();
        StringBuilder network = new StringBuilder(CLIENT_WIDTH);
        for (int group = 0; group < NETWORK_GROUPS; group++) {
            int high = bytes[2 * group] & 0xff;
            int low = bytes[2 * group + 1] & 0xff;
            network.append(Integer.toHexString(high << 8 | low)).append(':');
        }
        return network.append(":/64").toString();
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
