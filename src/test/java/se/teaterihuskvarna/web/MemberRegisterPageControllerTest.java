package se.teaterihuskvarna.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.ui.ExtendedModelMap;
import se.teaterihuskvarna.Swedish;
import se.teaterihuskvarna.member.FeeFilter;
import se.teaterihuskvarna.member.FeeService;
import se.teaterihuskvarna.member.HouseholdService;
import se.teaterihuskvarna.member.InvitationService;
import se.teaterihuskvarna.member.MemberList;
import se.teaterihuskvarna.member.MemberService;

/// The register's fee filters name the year the fee ledger counts: the year
/// in Sweden by the application's clock.
class MemberRegisterPageControllerTest {

    /// 00:30 on 1 January 2027 in Sweden, while the UTC date is still 31 December 2026.
    private static final Instant AFTER_MIDNIGHT_IN_SWEDEN = Instant.parse("2026-12-31T23:30:00Z");

    @Test
    void theFeeFiltersNameTheNewYearOnceItHasTurnedInSweden() {
        MemberService members = mock(MemberService.class);
        when(members.search(null, FeeFilter.ALL)).thenReturn(new MemberList(List.of(), false));
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("register.filter.all", Swedish.LOCALE, "Alla");
        messages.addMessage("register.filter.unpaid", Swedish.LOCALE, "Har inte betalat {0}");
        messages.addMessage("register.filter.paid", Swedish.LOCALE, "Har betalat {0}");
        MemberRegisterPageController controller = new MemberRegisterPageController(members,
                mock(HouseholdService.class), mock(FeeService.class), mock(InvitationService.class),
                new Copy(messages), Clock.fixed(AFTER_MIDNIGHT_IN_SWEDEN, ZoneOffset.UTC));
        ExtendedModelMap model = new ExtendedModelMap();

        controller.list(null, null, model);

        assertThat(model.get("filters")).asInstanceOf(list(RegisterFilter.class))
                .extracting(RegisterFilter::label)
                .containsExactly("Alla", "Har inte betalat 2027", "Har betalat 2027");
    }
}
