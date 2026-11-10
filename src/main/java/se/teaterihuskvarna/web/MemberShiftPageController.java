package se.teaterihuskvarna.web;

import gg.jte.generated.precompiled.Templates;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.View;
import se.teaterihuskvarna.login.SignedIn;
import se.teaterihuskvarna.member.MemberId;
import se.teaterihuskvarna.member.MemberService;
import se.teaterihuskvarna.volunteer.BookingOutcome;
import se.teaterihuskvarna.volunteer.NoSuchShift;
import se.teaterihuskvarna.volunteer.ShiftFull;
import se.teaterihuskvarna.volunteer.ShiftService;
import se.teaterihuskvarna.volunteer.ShiftStarted;

/// Volunteer shifts for members (R016): the upcoming shifts, and the buttons
/// that book and cancel. Spring Security lets only a member's account reach it.
///
/// Every button redirects back to the list with its outcome as a flash
/// message, so reloading never sends the form again.
@Controller
public class MemberShiftPageController {

    private static final String LIST = "/medlem/volontar";

    private final ShiftService shifts;
    private final MemberService members;
    private final Copy copy;
    private final Templates templates;

    MemberShiftPageController(ShiftService shifts, MemberService members, Copy copy, Templates templates) {
        this.shifts = shifts;
        this.members = members;
        this.copy = copy;
        this.templates = templates;
    }

    /// @param signedIn the logged-in account
    /// @param csrf     the hidden field of the page's forms
    /// @param feedback what the request before left for this page
    /// @return the list of upcoming shifts
    @GetMapping(LIST)
    public View list(@AuthenticationPrincipal SignedIn signedIn, CsrfField csrf, Feedback feedback) {
        return HtmlPage.of(templates.memberShifts(copy, csrf, shifts.upcoming(memberId(signedIn)), feedback));
    }

    /// @param signedIn the logged-in account
    /// @param id       the shift
    /// @return a redirect to the list, with the outcome
    @PostMapping(LIST + "/{id}/boka")
    public View book(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long id) {
        try {
            BookingOutcome outcome = shifts.book(id, memberId(signedIn));
            return Redirect.to(LIST).notice(copy.text(outcome == BookingOutcome.BOOKED
                    ? "shifts.notice.booked" : "shifts.notice.alreadyBooked"));
        } catch (ShiftFull e) {
            return Redirect.to(LIST).error(copy.text("shifts.error.full"));
        } catch (ShiftStarted e) {
            return Redirect.to(LIST).error(copy.text("shifts.error.started"));
        }
    }

    /// @param signedIn the logged-in account
    /// @param id       the shift
    /// @return a redirect to the list, with the outcome
    @PostMapping(LIST + "/{id}/avboka")
    public View cancel(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long id) {
        try {
            boolean cancelled = shifts.cancel(id, memberId(signedIn));
            return Redirect.to(LIST)
                    .notice(copy.text(cancelled ? "shifts.notice.cancelled" : "shifts.notice.notBooked"));
        } catch (ShiftStarted e) {
            return Redirect.to(LIST).error(copy.text("shifts.error.started"));
        }
    }

    /// Sends the error page with 404, as for any path that does not exist.
    ///
    /// @param response the response to send the error on
    /// @throws IOException if the response cannot be written
    @ExceptionHandler(NoSuchShift.class)
    public void notFound(HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.NOT_FOUND.value());
    }

    private MemberId memberId(SignedIn signedIn) {
        return members.findByAccount(signedIn.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND))
                .id();
    }
}
