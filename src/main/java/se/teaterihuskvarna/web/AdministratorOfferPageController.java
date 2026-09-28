package se.teaterihuskvarna.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import se.teaterihuskvarna.offer.NoSuchOffer;
import se.teaterihuskvarna.offer.OfferDetails;
import se.teaterihuskvarna.offer.OfferForm;
import se.teaterihuskvarna.offer.OfferService;
import se.teaterihuskvarna.offer.RegistrationExport;

/// Offer administration (R014, R020): the list of offers, a form to create
/// one, each offer's page with its form, publish button and registrations,
/// the CSV file of those registrations, and a confirmation page before a
/// delete. Spring Security lets only an administrator account reach it.
///
/// A failed save shows the form again with what was typed. Every other button
/// redirects, with its outcome as a flash message.
@Controller
public class AdministratorOfferPageController {

    private static final String LIST = "/admin/erbjudanden";

    private final OfferService offers;
    private final Copy copy;

    AdministratorOfferPageController(OfferService offers, Copy copy) {
        this.offers = offers;
        this.copy = copy;
    }

    /// @param model receives every offer
    /// @return the list of offers
    @GetMapping(LIST)
    public String list(Model model) {
        model.addAttribute("offers", offers.list());
        return "admin/offers";
    }

    /// @param model receives an empty form
    /// @return the page that creates an offer
    @GetMapping(LIST + "/nytt")
    public String newOffer(Model model) {
        model.addAttribute("form", OfferForm.empty());
        model.addAttribute("errors", FieldErrors.none());
        return "admin/offerNew";
    }

    /// @param form       what was typed
    /// @param model      receives the form again when it has errors
    /// @param redirected receives the confirmation shown after the redirect
    /// @return a redirect to the new offer's page, or the form again with what was wrong
    @PostMapping(LIST)
    public String create(@ModelAttribute("form") OfferForm form, Model model, RedirectAttributes redirected) {
        OfferDetails created;
        try {
            created = offers.create(form);
        } catch (ConstraintViolationException e) {
            model.addAttribute("errors", FieldErrors.of(e));
            return "admin/offerNew";
        }
        redirected.addFlashAttribute("notice", copy.text("adminOffers.created"));
        return redirect(created.id());
    }

    /// @param id    the offer
    /// @param model receives the offer, its form, and its registrations
    /// @return the offer's page
    @GetMapping(LIST + "/{id}")
    public String offer(@PathVariable long id, Model model) {
        OfferDetails offer = offers.details(id);
        return page(offer, offer.toForm(), FieldErrors.none(), model);
    }

    /// @param id         the offer
    /// @param form       what was typed
    /// @param model      receives the page again when the form has errors
    /// @param redirected receives the confirmation shown after the redirect
    /// @return a redirect to the offer's page, or the page again with what was wrong
    @PostMapping(LIST + "/{id}")
    public String update(@PathVariable long id, @ModelAttribute("form") OfferForm form, Model model,
            RedirectAttributes redirected) {
        try {
            offers.update(id, form);
        } catch (ConstraintViolationException e) {
            return page(offers.details(id), form, FieldErrors.of(e), model);
        }
        redirected.addFlashAttribute("notice", copy.text("adminOffers.saved"));
        return redirect(id);
    }

    /// @param id         the offer to show to members
    /// @param redirected receives the confirmation shown after the redirect
    /// @return a redirect to the offer's page
    @PostMapping(LIST + "/{id}/publicera")
    public String publish(@PathVariable long id, RedirectAttributes redirected) {
        offers.setPublished(id, true);
        redirected.addFlashAttribute("notice", copy.text("adminOffers.published"));
        return redirect(id);
    }

    /// @param id         the offer to hide from members
    /// @param redirected receives the confirmation shown after the redirect
    /// @return a redirect to the offer's page
    @PostMapping(LIST + "/{id}/avpublicera")
    public String unpublish(@PathVariable long id, RedirectAttributes redirected) {
        offers.setPublished(id, false);
        redirected.addFlashAttribute("notice", copy.text("adminOffers.unpublished"));
        return redirect(id);
    }

    /// @param id    the offer
    /// @param model receives the offer, whose registration count the page names
    /// @return the page that asks before deleting
    @GetMapping(LIST + "/{id}/ta-bort")
    public String confirmDelete(@PathVariable long id, Model model) {
        model.addAttribute("offer", offers.details(id));
        return "admin/offerDelete";
    }

    /// @param id         the offer, which takes its registrations with it
    /// @param redirected receives the confirmation shown after the redirect
    /// @return a redirect to the list
    @PostMapping(LIST + "/{id}/ta-bort")
    public String delete(@PathVariable long id, RedirectAttributes redirected) {
        offers.delete(id);
        redirected.addFlashAttribute("notice", copy.text("adminOffers.deleted"));
        return "redirect:" + LIST;
    }

    /// R020. The same file as the API's `registrations.csv`.
    ///
    /// @param id the offer
    /// @return the registrations as CSV, as an attachment
    @GetMapping(LIST + "/{id}/anmalningar.csv")
    public ResponseEntity<String> registrationsCsv(@PathVariable long id) {
        RegistrationExport export = offers.registrationsCsv(id);
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

    private String page(OfferDetails offer, OfferForm form, FieldErrors errors, Model model) {
        model.addAttribute("offer", offer);
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("registrations", offers.registrations(offer.id()));
        return "admin/offer";
    }

    private static String redirect(long id) {
        return "redirect:" + LIST + "/" + id;
    }
}
