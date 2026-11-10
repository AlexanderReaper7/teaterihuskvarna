package se.teaterihuskvarna.web;

import gg.jte.generated.precompiled.Templates;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.View;
import se.teaterihuskvarna.export.CsvFile;
import se.teaterihuskvarna.offer.NoSuchOffer;
import se.teaterihuskvarna.offer.OfferDetails;
import se.teaterihuskvarna.offer.OfferForm;
import se.teaterihuskvarna.offer.OfferService;

/// Offer administration (R014, R020): the list of offers, a form to create
/// one, each offer's page with its form, publish button and registrations,
/// the CSV file of those registrations, and a confirmation page before a
/// delete. Spring Security lets only an administrator account reach it.
///
/// A failed save shows the form again with what was typed, also when a value
/// could not become an [OfferForm] at all. Every other button redirects, with
/// its outcome as a flash message.
@Controller
public class AdministratorOfferPageController {

    private static final String LIST = "/admin/erbjudanden";

    /// What the keys of [OfferForm]'s messages start with.
    private static final String KEYS = "offer";

    private final OfferService offers;
    private final Copy copy;
    private final Templates templates;

    AdministratorOfferPageController(OfferService offers, Copy copy, Templates templates) {
        this.offers = offers;
        this.copy = copy;
        this.templates = templates;
    }

    /// @param feedback what the request before left for this page
    /// @return the list of every offer
    @GetMapping(LIST)
    public View list(Feedback feedback) {
        return HtmlPage.of(templates.adminOffers(copy, offers.list(), feedback));
    }

    /// @param csrf the hidden field of the page's form
    /// @return the page that creates an offer, with an empty form
    @GetMapping(LIST + "/nytt")
    public View newOffer(CsrfField csrf) {
        return HtmlPage.of(templates.adminOfferNew(copy, csrf, OfferForm.empty(), FieldErrors.none()));
    }

    /// @param form    what was typed
    /// @param binding the values that could not be read, which are null in the form
    /// @param csrf    the hidden field of the page's form
    /// @return a redirect to the new offer's page, or the form again with what was wrong
    @PostMapping(LIST)
    public View create(@ModelAttribute("form") OfferForm form, BindingResult binding, CsrfField csrf) {
        if (binding.hasErrors()) {
            return HtmlPage.of(templates.adminOfferNew(copy, csrf, form, FieldErrors.of(binding, copy, KEYS)));
        }
        OfferDetails created;
        try {
            created = offers.create(form);
        } catch (ConstraintViolationException e) {
            return HtmlPage.of(templates.adminOfferNew(copy, csrf, form, FieldErrors.of(e)));
        }
        return redirect(created.id()).notice(copy.text("adminOffers.created"));
    }

    /// @param id       the offer
    /// @param csrf     the hidden field of the page's forms
    /// @param feedback what the request before left for this page
    /// @return the offer's page, with its form and its registrations
    @GetMapping(LIST + "/{id}")
    public View offer(@PathVariable long id, CsrfField csrf, Feedback feedback) {
        OfferDetails offer = offers.details(id);
        return page(offer, offer.toForm(), FieldErrors.none(), csrf, feedback);
    }

    /// @param id       the offer
    /// @param form     what was typed
    /// @param binding  the values that could not be read, which are null in the form
    /// @param csrf     the hidden field of the page's forms
    /// @param feedback what the request before left for this page
    /// @return a redirect to the offer's page, or the page again with what was wrong
    @PostMapping(LIST + "/{id}")
    public View update(@PathVariable long id, @ModelAttribute("form") OfferForm form, BindingResult binding,
            CsrfField csrf, Feedback feedback) {
        if (binding.hasErrors()) {
            return page(offers.details(id), form, FieldErrors.of(binding, copy, KEYS), csrf, feedback);
        }
        try {
            offers.update(id, form);
        } catch (ConstraintViolationException e) {
            return page(offers.details(id), form, FieldErrors.of(e), csrf, feedback);
        }
        return redirect(id).notice(copy.text("adminOffers.saved"));
    }

    /// @param id the offer to show to members
    /// @return a redirect to the offer's page, with the confirmation
    @PostMapping(LIST + "/{id}/publicera")
    public View publish(@PathVariable long id) {
        offers.setPublished(id, true);
        return redirect(id).notice(copy.text("adminOffers.published"));
    }

    /// @param id the offer to hide from members
    /// @return a redirect to the offer's page, with the confirmation
    @PostMapping(LIST + "/{id}/avpublicera")
    public View unpublish(@PathVariable long id) {
        offers.setPublished(id, false);
        return redirect(id).notice(copy.text("adminOffers.unpublished"));
    }

    /// @param id   the offer, whose registration count the page names
    /// @param csrf the hidden field of the page's form
    /// @return the page that asks before deleting
    @GetMapping(LIST + "/{id}/ta-bort")
    public View confirmDelete(@PathVariable long id, CsrfField csrf) {
        return HtmlPage.of(templates.adminOfferDelete(copy, csrf, offers.details(id)));
    }

    /// @param id the offer, which takes its registrations with it
    /// @return a redirect to the list, with the confirmation
    @PostMapping(LIST + "/{id}/ta-bort")
    public View delete(@PathVariable long id) {
        offers.delete(id);
        return Redirect.to(LIST).notice(copy.text("adminOffers.deleted"));
    }

    /// R020. The same file as the API's `registrations.csv`.
    ///
    /// @param id the offer
    /// @return the registrations as CSV, as an attachment
    @GetMapping(LIST + "/{id}/anmalningar.csv")
    public ResponseEntity<String> registrationsCsv(@PathVariable long id) {
        CsvFile export = offers.registrationsCsv(id);
        return ResponseEntity.ok().headers(export.headers()).body(export.text());
    }

    /// Sends the error page with 404, as for any path that does not exist.
    ///
    /// @param response the response to send the error on
    /// @throws IOException if the response cannot be written
    @ExceptionHandler(NoSuchOffer.class)
    public void notFound(HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.NOT_FOUND.value());
    }

    private View page(OfferDetails offer, OfferForm form, FieldErrors errors, CsrfField csrf, Feedback feedback) {
        return HtmlPage.of(templates.adminOffer(copy, csrf, offer, form, errors, offers.registrations(offer.id()),
                feedback));
    }

    private static Redirect redirect(long id) {
        return Redirect.to(LIST + "/" + id);
    }
}
