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
import se.teaterihuskvarna.offer.NoSuchOffer;
import se.teaterihuskvarna.offer.OfferFull;
import se.teaterihuskvarna.offer.OfferService;
import se.teaterihuskvarna.offer.RegistrationClosed;
import se.teaterihuskvarna.offer.RegistrationOutcome;

/// Offers for members (R014): the open offers, one offer's page, and the
/// buttons that register and cancel. Spring Security lets only a member's
/// account reach it.
///
/// Every button redirects back to the offer's page with its outcome as a
/// flash message, so reloading the page never sends the form again. An offer
/// that is not published answers 404, the same as one that does not exist.
@Controller
public class MemberOfferPageController {

    private final OfferService offers;
    private final MemberService members;
    private final Copy copy;

    MemberOfferPageController(OfferService offers, MemberService members, Copy copy) {
        this.offers = offers;
        this.members = members;
        this.copy = copy;
    }

    /// @param signedIn the logged-in account
    /// @param model    receives the open offers
    /// @return the list of offers
    @GetMapping("/medlem/erbjudanden")
    public String list(@AuthenticationPrincipal SignedIn signedIn, Model model) {
        model.addAttribute("offers", offers.openOffers(memberId(signedIn)));
        return "member/offers";
    }

    /// @param signedIn the logged-in account
    /// @param id       the offer
    /// @param model    receives the offer
    /// @return the offer's page
    @GetMapping("/medlem/erbjudanden/{id}")
    public String offer(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long id, Model model) {
        model.addAttribute("offer", offers.offer(id, memberId(signedIn)));
        return "member/offer";
    }

    /// @param signedIn   the logged-in account
    /// @param id         the offer
    /// @param redirected receives the outcome, shown after the redirect
    /// @return a redirect to the offer's page
    @PostMapping("/medlem/erbjudanden/{id}/anmal")
    public String register(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long id,
            RedirectAttributes redirected) {
        long memberId = memberId(signedIn);
        try {
            RegistrationOutcome outcome = offers.register(id, memberId);
            String key = outcome == RegistrationOutcome.REGISTERED
                    ? "offers.notice.registered" : "offers.notice.alreadyRegistered";
            redirected.addFlashAttribute("notice", copy.text(key));
        } catch (OfferFull e) {
            redirected.addFlashAttribute("error", copy.text("offers.error.full"));
        } catch (RegistrationClosed e) {
            redirected.addFlashAttribute("error", copy.text("offers.error.closed"));
        }
        return redirect(id);
    }

    /// @param signedIn   the logged-in account
    /// @param id         the offer
    /// @param redirected receives the outcome, shown after the redirect
    /// @return a redirect to the offer's page
    @PostMapping("/medlem/erbjudanden/{id}/avanmal")
    public String cancel(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long id,
            RedirectAttributes redirected) {
        long memberId = memberId(signedIn);
        try {
            boolean cancelled = offers.cancel(id, memberId);
            redirected.addFlashAttribute("notice",
                    copy.text(cancelled ? "offers.notice.cancelled" : "offers.notice.notRegistered"));
        } catch (RegistrationClosed e) {
            redirected.addFlashAttribute("error", copy.text("offers.error.closed"));
        }
        return redirect(id);
    }

    /// Sends the error page with 404, as for any path that does not exist.
    ///
    /// @param response the response to send the error on
    /// @throws IOException if the response cannot be written
    @ExceptionHandler(NoSuchOffer.class)
    public void notFound(HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.NOT_FOUND.value());
    }

    private long memberId(SignedIn signedIn) {
        return members.findByAccount(signedIn.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND))
                .id();
    }

    private static String redirect(long id) {
        return "redirect:/medlem/erbjudanden/" + id;
    }
}
