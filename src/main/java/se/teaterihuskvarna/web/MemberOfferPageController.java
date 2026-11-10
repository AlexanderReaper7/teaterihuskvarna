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
    private final Templates templates;

    MemberOfferPageController(OfferService offers, MemberService members, Copy copy, Templates templates) {
        this.offers = offers;
        this.members = members;
        this.copy = copy;
        this.templates = templates;
    }

    /// @param signedIn the logged-in account
    /// @return the list of open offers
    @GetMapping("/medlem/erbjudanden")
    public View list(@AuthenticationPrincipal SignedIn signedIn) {
        return HtmlPage.of(templates.memberOffers(copy, offers.openOffers(memberId(signedIn))));
    }

    /// @param signedIn the logged-in account
    /// @param id       the offer
    /// @param csrf     the hidden field of the page's forms
    /// @param feedback what the request before left for this page
    /// @return the offer's page
    @GetMapping("/medlem/erbjudanden/{id}")
    public View offer(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long id, CsrfField csrf,
            Feedback feedback) {
        return HtmlPage.of(templates.memberOffer(copy, csrf, offers.offer(id, memberId(signedIn)), feedback));
    }

    /// @param signedIn the logged-in account
    /// @param id       the offer
    /// @return a redirect to the offer's page, with the outcome
    @PostMapping("/medlem/erbjudanden/{id}/anmal")
    public View register(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long id) {
        MemberId memberId = memberId(signedIn);
        try {
            RegistrationOutcome outcome = offers.register(id, memberId);
            String key = outcome == RegistrationOutcome.REGISTERED
                    ? "offers.notice.registered" : "offers.notice.alreadyRegistered";
            return redirect(id).notice(copy.text(key));
        } catch (OfferFull e) {
            return redirect(id).error(copy.text("offers.error.full"));
        } catch (RegistrationClosed e) {
            return redirect(id).error(copy.text("offers.error.closed"));
        }
    }

    /// @param signedIn the logged-in account
    /// @param id       the offer
    /// @return a redirect to the offer's page, with the outcome
    @PostMapping("/medlem/erbjudanden/{id}/avanmal")
    public View cancel(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long id) {
        MemberId memberId = memberId(signedIn);
        try {
            boolean cancelled = offers.cancel(id, memberId);
            return redirect(id)
                    .notice(copy.text(cancelled ? "offers.notice.cancelled" : "offers.notice.notRegistered"));
        } catch (RegistrationClosed e) {
            return redirect(id).error(copy.text("offers.error.closed"));
        }
    }

    /// Sends the error page with 404, as for any path that does not exist.
    ///
    /// @param response the response to send the error on
    /// @throws IOException if the response cannot be written
    @ExceptionHandler(NoSuchOffer.class)
    public void notFound(HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.NOT_FOUND.value());
    }

    private MemberId memberId(SignedIn signedIn) {
        return members.findByAccount(signedIn.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND))
                .id();
    }

    private static Redirect redirect(long id) {
        return Redirect.to("/medlem/erbjudanden/" + id);
    }
}
