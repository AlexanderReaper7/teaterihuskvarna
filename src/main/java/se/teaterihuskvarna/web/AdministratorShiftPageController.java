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
import se.teaterihuskvarna.volunteer.NoSuchEvent;
import se.teaterihuskvarna.volunteer.NoSuchShift;
import se.teaterihuskvarna.volunteer.ShiftDetails;
import se.teaterihuskvarna.volunteer.ShiftForm;
import se.teaterihuskvarna.volunteer.ShiftService;

/// Volunteer shift administration (R016, R020): the upcoming shifts with a form
/// to add one, each shift's page with who booked it, the CSV file of those
/// bookings, and a confirmation page before a delete. Spring Security lets only
/// an administrator account reach it.
@Controller
public class AdministratorShiftPageController {

    private static final String LIST = "/admin/volontarpass";

    /// What the keys of [ShiftForm]'s messages start with.
    private static final String KEYS = "shift";

    private final ShiftService shifts;
    private final Copy copy;
    private final Templates templates;

    AdministratorShiftPageController(ShiftService shifts, Copy copy, Templates templates) {
        this.shifts = shifts;
        this.copy = copy;
        this.templates = templates;
    }

    /// @param csrf     the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return the list of shifts, with the events and an empty form
    @GetMapping(LIST)
    public View list(CsrfField csrf, Feedback feedback) {
        return page(ShiftForm.empty(), FieldErrors.none(), csrf, feedback);
    }

    /// @param form     what was typed
    /// @param binding  the values that could not be read, which are null in the form
    /// @param csrf     the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return a redirect to the new shift, or the list again with what was wrong
    @PostMapping(LIST)
    public View create(@ModelAttribute("form") ShiftForm form, BindingResult binding, CsrfField csrf,
            Feedback feedback) {
        if (binding.hasErrors()) {
            return page(form, FieldErrors.of(binding, copy, KEYS), csrf, feedback);
        }
        ShiftDetails created;
        try {
            created = shifts.create(form);
        } catch (ConstraintViolationException e) {
            return page(form, FieldErrors.of(e), csrf, feedback);
        } catch (NoSuchEvent e) {
            return page(form, FieldErrors.none(), csrf, feedback.withError(copy.text("adminShifts.error.event")));
        }
        return Redirect.to(LIST + "/" + created.id()).notice(copy.text("adminShifts.created"));
    }

    /// @param id       the shift
    /// @param feedback what the request before left for this page
    /// @return the shift's page, with who booked it
    @GetMapping(LIST + "/{id}")
    public View shift(@PathVariable long id, Feedback feedback) {
        return HtmlPage.of(templates.adminShift(copy, shifts.details(id), shifts.volunteers(id), feedback));
    }

    /// @param id       the shift, whose booking count the page names
    /// @param csrf     the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return the page that asks before deleting
    @GetMapping(LIST + "/{id}/ta-bort")
    public View confirmDelete(@PathVariable long id, CsrfField csrf, Feedback feedback) {
        return HtmlPage.of(templates.adminShiftDelete(copy, csrf, shifts.details(id), feedback));
    }

    /// @param id the shift, which takes its bookings with it
    /// @return a redirect to the list, with the confirmation
    @PostMapping(LIST + "/{id}/ta-bort")
    public View delete(@PathVariable long id) {
        shifts.delete(id);
        return Redirect.to(LIST).notice(copy.text("adminShifts.deleted"));
    }

    /// R020. The same file as the API's `volunteers.csv`.
    ///
    /// @param id the shift
    /// @return the bookings as CSV, as an attachment
    @GetMapping(LIST + "/{id}/bokningar.csv")
    public ResponseEntity<String> volunteersCsv(@PathVariable long id) {
        CsvFile file = shifts.volunteersCsv(id);
        return ResponseEntity.ok().headers(file.headers()).body(file.text());
    }

    /// Sends the error page with 404, as for any path that does not exist.
    ///
    /// @param response the response to send the error on
    /// @throws IOException if the response cannot be written
    @ExceptionHandler(NoSuchShift.class)
    public void notFound(HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.NOT_FOUND.value());
    }

    private View page(ShiftForm form, FieldErrors errors, CsrfField csrf, Feedback feedback) {
        return HtmlPage.of(templates.adminShifts(copy, csrf, shifts.list(), shifts.past(), shifts.events(), form,
                errors, feedback));
    }
}
