package se.teaterihuskvarna.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import se.teaterihuskvarna.login.SignedIn;
import se.teaterihuskvarna.member.MemberService;
import se.teaterihuskvarna.offer.MemberOffer;
import se.teaterihuskvarna.offer.OfferService;
import se.teaterihuskvarna.offer.RegistrationOutcome;

/// Offers for the logged-in member (R014), as `/medlem/erbjudanden` shows them.
/// Errors become statuses in [ProblemResponses]: an unpublished offer is 404,
/// a full or closed one 409.
@RestController
public class MemberOffersController {

    private final OfferService offers;
    private final MemberService members;

    MemberOffersController(OfferService offers, MemberService members) {
        this.offers = offers;
        this.members = members;
    }

    /// @param signedIn the logged-in account
    /// @return the published offers still open, with places left and whether this member is registered
    @GetMapping("/api/member/offers")
    public List<MemberOffer> list(@AuthenticationPrincipal SignedIn signedIn) {
        return offers.openOffers(memberId(signedIn));
    }

    /// @param signedIn the logged-in account
    /// @param id       the offer
    /// @return the offer, which may have closed
    @GetMapping("/api/member/offers/{id}")
    public MemberOffer offer(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long id) {
        return offers.offer(id, memberId(signedIn));
    }

    /// Registering twice is not an error: the second answer is 200 rather than 201.
    ///
    /// @param signedIn the logged-in account
    /// @param id       the offer
    /// @return 201 when a place was taken, 200 when the member already had one, with the offer either way
    @PostMapping("/api/member/offers/{id}/registration")
    public ResponseEntity<MemberOffer> register(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long id) {
        long memberId = memberId(signedIn);
        RegistrationOutcome outcome = offers.register(id, memberId);
        HttpStatus status = outcome == RegistrationOutcome.REGISTERED ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(offers.offer(id, memberId));
    }

    /// 204 whether or not the member had a place, so a repeated request is harmless.
    ///
    /// @param signedIn the logged-in account
    /// @param id       the offer
    @DeleteMapping("/api/member/offers/{id}/registration")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long id) {
        offers.cancel(id, memberId(signedIn));
    }

    /// An account always belongs to a member, so none means the member was
    /// removed while the session lived on.
    private long memberId(SignedIn signedIn) {
        return members.findByAccount(signedIn.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND))
                .id();
    }
}
