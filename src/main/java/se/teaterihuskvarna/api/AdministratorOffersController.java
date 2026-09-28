package se.teaterihuskvarna.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import se.teaterihuskvarna.offer.OfferDetails;
import se.teaterihuskvarna.offer.OfferForm;
import se.teaterihuskvarna.offer.OfferService;
import se.teaterihuskvarna.offer.Recipient;
import se.teaterihuskvarna.offer.Registrant;
import se.teaterihuskvarna.offer.RegistrationExport;

/// Offer administration over HTTP (R014, R020): what `/admin/erbjudanden`
/// offers. Times in a request body are Swedish wall-clock times such as
/// `2026-10-05T19:00`, as [OfferForm] explains; times in a response are
/// instants in UTC.
@RestController
public class AdministratorOffersController {

    private final OfferService offers;

    AdministratorOffersController(OfferService offers) {
        this.offers = offers;
    }

    /// @return every offer, published or not, newest first
    @GetMapping("/api/admin/offers")
    public List<OfferDetails> list() {
        return offers.list();
    }

    /// @param form the title, description, times and capacity
    /// @return the offer as stored, unpublished
    @PostMapping("/api/admin/offers")
    @ResponseStatus(HttpStatus.CREATED)
    public OfferDetails create(@RequestBody OfferForm form) {
        return offers.create(form);
    }

    /// @param id the offer
    /// @return the offer
    @GetMapping("/api/admin/offers/{id}")
    public OfferDetails details(@PathVariable long id) {
        return offers.details(id);
    }

    /// @param id   the offer
    /// @param form every field, as a replacement
    /// @return the offer as stored
    @PutMapping("/api/admin/offers/{id}")
    public OfferDetails update(@PathVariable long id, @RequestBody OfferForm form) {
        return offers.update(id, form);
    }

    /// @param id the offer to show to members
    /// @return the offer as stored
    @PostMapping("/api/admin/offers/{id}/publish")
    public OfferDetails publish(@PathVariable long id) {
        offers.setPublished(id, true);
        return offers.details(id);
    }

    /// @param id the offer to hide from members
    /// @return the offer as stored
    @PostMapping("/api/admin/offers/{id}/unpublish")
    public OfferDetails unpublish(@PathVariable long id) {
        offers.setPublished(id, false);
        return offers.details(id);
    }

    /// Deletes the offer and every registration for it.
    ///
    /// @param id the offer
    @DeleteMapping("/api/admin/offers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        offers.delete(id);
    }

    /// @param id the offer
    /// @return who registered, in the order they did
    @GetMapping("/api/admin/offers/{id}/registrations")
    public List<Registrant> registrations(@PathVariable long id) {
        return offers.registrations(id);
    }

    /// The same file as `/admin/erbjudanden/{id}/anmalningar.csv` (R020).
    ///
    /// @param id the offer
    /// @return the registrations as CSV, as an attachment
    @GetMapping("/api/admin/offers/{id}/registrations.csv")
    public ResponseEntity<String> registrationsCsv(@PathVariable long id) {
        RegistrationExport export = offers.registrationsCsv(id);
        return ResponseEntity.ok().headers(export.headers()).body(export.text());
    }

    /// Who a mailing to this offer's registrants would reach: the registered
    /// members with an account.
    ///
    /// @param id the offer
    /// @return the members, by name
    @GetMapping("/api/admin/offers/{id}/recipients")
    public List<Recipient> recipients(@PathVariable long id) {
        return offers.recipients(id);
    }
}
