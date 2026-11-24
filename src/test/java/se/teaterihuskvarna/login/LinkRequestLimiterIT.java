package se.teaterihuskvarna.login;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import se.teaterihuskvarna.IntegrationTestSupport;

/// Proves what [LinkRequestLimiter] counts, by calling it the way the login
/// filter and the membership application do:
///
/// - one client's allowance for an address is its own, so a client that uses
///   it up does not stop another client from asking for the same address;
/// - all clients together stop at the address's total, so many clients cannot
///   send one address mail without end;
/// - an IPv6 client is counted by its /64 network, since whoever has one
///   address there has all of them;
/// - a request that was refused does not count against its address, so a
///   client over its own limit cannot fill another person's address, and a
///   person who keeps trying while over the limit is let through again once
///   the requests that were let through have left the window;
/// - a refused request still counts against its client, so a client that
///   keeps asking stays over its limit;
/// - each refusal is logged with the limit that refused it, and with neither
///   the address nor the client's IP address.
///
/// [LoginIT] proves the limits through HTTP, and that requests sent at once
/// still stop at the limit.
@ExtendWith(OutputCaptureExtension.class)
class LinkRequestLimiterIT extends IntegrationTestSupport {

    private static final String KARIN = "karin@example.test";
    private static final String KARINS_CLIENT = "192.0.2.10";
    private static final String OTHER_CLIENT = "192.0.2.66";

    @Autowired
    private LoginSettings settings;

    @Autowired
    private LinkRequestLimiter limiter;

    /// Finding C4 of the review of 2026-11-10: with one count per address,
    /// whoever asked five times for somebody else's address locked its owner
    /// out for the hour.
    @Test
    void aClientAtItsAllowanceForAnAddressDoesNotStopAnotherClient() {
        assertThat(settings.requestsPerAddressAndClient()).isLessThan(settings.requestsPerAddress());
        for (int request = 0; request < settings.requestsPerAddressAndClient(); request++) {
            assertThat(limiter.tryAcquire(KARIN, OTHER_CLIENT)).isTrue();
        }
        assertThat(limiter.tryAcquire(KARIN, OTHER_CLIENT)).isFalse();

        assertThat(limiter.tryAcquire(KARIN, KARINS_CLIENT)).isTrue();
    }

    @Test
    void everyClientIsRefusedOnceTheAddressIsAtItsTotal() {
        for (int request = 0; request < settings.requestsPerAddress(); request++) {
            assertThat(limiter.tryAcquire(KARIN, "198.51.100." + request)).isTrue();
        }

        assertThat(limiter.tryAcquire(KARIN, KARINS_CLIENT)).isFalse();
    }

    /// Three addresses in one /64, then one in the /64 beside it.
    @Test
    void anIpv6ClientIsCountedByItsNetwork() {
        for (int request = 0; request < settings.requestsPerAddressAndClient(); request++) {
            assertThat(limiter.tryAcquire(KARIN, "2001:db8:1:2::" + Integer.toHexString(request + 1))).isTrue();
        }

        assertThat(limiter.tryAcquire(KARIN, "2001:db8:1:2:aaaa:bbbb:cccc:dddd")).isFalse();
        assertThat(limiter.tryAcquire(KARIN, "2001:db8:1:3::1")).isTrue();
        assertThat(jdbc.sql("SELECT DISTINCT client_address FROM link_request ORDER BY client_address")
                .query(String.class)
                .list()).containsExactly("2001:db8:1:2::/64", "2001:db8:1:3::/64");
    }

    @Test
    void aClientOverItsOwnLimitCannotFillAnAddress() {
        for (int request = 0; request < settings.requestsPerClient(); request++) {
            assertThat(limiter.tryAcquire("nobody" + request + "@example.test", OTHER_CLIENT)).isTrue();
        }
        for (int request = 0; request < settings.requestsPerAddress() + 1; request++) {
            assertThat(limiter.tryAcquire(KARIN, OTHER_CLIENT)).isFalse();
        }

        assertThat(limiter.tryAcquire(KARIN, KARINS_CLIENT)).isTrue();
    }

    /// The requests let through are moved to the window's last minute, then
    /// out of it, with the refused ones still inside.
    @Test
    void tryingAgainWhileOverTheAllowanceDoesNotProlongIt() {
        for (int request = 0; request < settings.requestsPerAddressAndClient(); request++) {
            assertThat(limiter.tryAcquire(KARIN, KARINS_CLIENT)).isTrue();
        }
        makeOlder(settings.requestWindow().minusMinutes(1));
        for (int request = 0; request < settings.requestsPerAddressAndClient(); request++) {
            assertThat(limiter.tryAcquire(KARIN, KARINS_CLIENT)).isFalse();
        }
        makeOlder(Duration.ofMinutes(2));

        assertThat(limiter.tryAcquire(KARIN, KARINS_CLIENT)).isTrue();
    }

    /// The same moves as above, for the client: here the refused requests
    /// must keep counting.
    @Test
    void aClientThatKeepsAskingStaysOverItsLimit() {
        for (int request = 0; request < settings.requestsPerClient(); request++) {
            assertThat(limiter.tryAcquire("first" + request + "@example.test", OTHER_CLIENT)).isTrue();
        }
        makeOlder(settings.requestWindow().minusMinutes(1));
        for (int request = 0; request < settings.requestsPerClient(); request++) {
            assertThat(limiter.tryAcquire("second" + request + "@example.test", OTHER_CLIENT)).isFalse();
        }
        makeOlder(Duration.ofMinutes(2));

        assertThat(limiter.tryAcquire("third@example.test", OTHER_CLIENT)).isFalse();
    }

    @Test
    void aRefusalByAClientsAllowanceIsLoggedWithoutTheAddress(CapturedOutput output) {
        for (int request = 0; request < settings.requestsPerAddressAndClient(); request++) {
            limiter.tryAcquire(KARIN, KARINS_CLIENT);
        }
        assertThat(output).doesNotContain("Refused a request for mail");

        assertThat(limiter.tryAcquire(KARIN, KARINS_CLIENT)).isFalse();

        assertThat(output)
                .contains("Refused a request for mail: over the limit of " + settings.requestsPerAddressAndClient()
                        + " per address from one client in " + settings.requestWindow())
                .doesNotContain("per client")
                .doesNotContain(KARIN)
                .doesNotContain(KARINS_CLIENT);
    }

    @Test
    void aRefusalByTheAddressTotalIsLoggedWithoutTheAddress(CapturedOutput output) {
        for (int request = 0; request < settings.requestsPerAddress(); request++) {
            limiter.tryAcquire(KARIN, "198.51.100." + request);
        }
        assertThat(output).doesNotContain("Refused a request for mail");

        assertThat(limiter.tryAcquire(KARIN, KARINS_CLIENT)).isFalse();

        assertThat(output)
                .contains("Refused a request for mail: over the limit of " + settings.requestsPerAddress()
                        + " per address in " + settings.requestWindow())
                .doesNotContain("per client")
                .doesNotContain("from one client")
                .doesNotContain(KARIN)
                .doesNotContain(KARINS_CLIENT);
    }

    @Test
    void aRefusalByTheClientLimitIsLoggedWithoutTheClient(CapturedOutput output) {
        for (int request = 0; request < settings.requestsPerClient(); request++) {
            limiter.tryAcquire("nobody" + request + "@example.test", OTHER_CLIENT);
        }
        assertThat(output).doesNotContain("Refused a request for mail");

        assertThat(limiter.tryAcquire(KARIN, OTHER_CLIENT)).isFalse();

        assertThat(output)
                .contains("Refused a request for mail: over the limit of " + settings.requestsPerClient()
                        + " per client in " + settings.requestWindow())
                .doesNotContain("per address")
                .doesNotContain(KARIN)
                .doesNotContain(OTHER_CLIENT);
    }

    private void makeOlder(Duration by) {
        jdbc.sql("UPDATE link_request SET requested_at = requested_at - make_interval(secs => ?)")
                .param(by.toSeconds())
                .update();
    }
}
