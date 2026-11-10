package se.teaterihuskvarna.login;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/// What [LinkRequestLimiter] counts as one client. [LinkRequestLimiterIT]
/// proves that the limits follow it.
class LinkRequestLimiterTest {

    @Test
    void anIpv4AddressIsAClientOfItsOwn() {
        assertThat(LinkRequestLimiter.client("192.0.2.10")).isEqualTo("192.0.2.10");
        assertThat(LinkRequestLimiter.client("192.0.2.11")).isEqualTo("192.0.2.11");
    }

    @Test
    void everyIpv6AddressInOneNetworkIsTheSameClient() {
        assertThat(LinkRequestLimiter.client("2001:db8:1:2::1")).isEqualTo("2001:db8:1:2::/64");
        assertThat(LinkRequestLimiter.client("2001:0db8:0001:0002:aaaa:bbbb:cccc:dddd")).isEqualTo("2001:db8:1:2::/64");
        assertThat(LinkRequestLimiter.client("2001:db8:1:3::1")).isEqualTo("2001:db8:1:3::/64");
        assertThat(LinkRequestLimiter.client("::1")).isEqualTo("0:0:0:0::/64");
    }

    /// The servlet container gives an address, but nothing here depends on it:
    /// a value that is not one counts as a client by its text.
    @Test
    void whatIsNotAnAddressIsKeptAndCutToTheColumn() {
        assertThat(LinkRequestLimiter.client("unknown")).isEqualTo("unknown");
        assertThat(LinkRequestLimiter.client("x".repeat(60))).isEqualTo("x".repeat(45));
    }
}
