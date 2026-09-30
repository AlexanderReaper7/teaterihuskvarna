package se.teaterihuskvarna.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import se.teaterihuskvarna.login.SignedIn;
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

    MemberShiftPageController(ShiftService shifts, MemberService members, Copy copy) {
        this.shifts = shifts;
        this.members = members;
        this.copy = copy;
    }

    /// @param signedIn the logged-in account
    /// @param model    receives the upcoming shifts
    /// @return the list of shifts
    @GetMapping(LIST)
    public String list(@AuthenticationPrincipal SignedIn signedIn, Model model) {
        model.addAttribute("shifts", shifts.upcoming(memberId(signedIn)));
        return "member/shifts";
    }

    /// @param signedIn   the logged-in account
    /// @param id         the shift
    /// @param redirected receives the outcome, shown after the redirect
    /// @return a redirect to the list
    @PostMapping(LIST + "/{id}/boka")
    public String book(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long id,
            RedirectAttributes redirected) {
        try {
            BookingOutcome outcome = shifts.book(id, memberId(signedIn));
            redirected.addFlashAttribute("notice", copy.text(outcome == BookingOutcome.BOOKED
                    ? "shifts.notice.booked" : "shifts.notice.alreadyBooked"));
        } catch (ShiftFull e) {
            redirected.addFlashAttribute("error", copy.text("shifts.error.full"));
        } catch (ShiftStarted e) {
            redirected.addFlashAttribute("error", copy.text("shifts.error.started"));
        }
        return "redirect:" + LIST;
    }

    /// @param signedIn   the logged-in account
    /// @param id         the shift
    /// @param redirected receives the outcome, shown after the redirect
    /// @return a redirect to the list
    @PostMapping(LIST + "/{id}/avboka")
    public String cancel(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long id,
            RedirectAttributes redirected) {
        try {
            boolean cancelled = shifts.cancel(id, memberId(signedIn));
            redirected.addFlashAttribute("notice",
                    copy.text(cancelled ? "shifts.notice.cancelled" : "shifts.notice.notBooked"));
        } catch (ShiftStarted e) {
            redirected.addFlashAttribute("error", copy.text("shifts.error.started"));
        }
        return "redirect:" + LIST;
    }

    /// Sends the error page with 404, as for any path that does not exist.
    ///
    /// @param response the response to send the error on
    /// @throws IOException if the response cannot be written
    @ExceptionHandler(NoSuchShift.class)
    public void notFound(HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.NOT_FOUND.value());
    }

    private long memberId(SignedIn signedIn) {
        return members.findByAccount(signedIn.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND))
                .id();
    }
}
