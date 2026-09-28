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
import se.teaterihuskvarna.export.CsvFile;
import se.teaterihuskvarna.volunteer.NoSuchEvent;
import se.teaterihuskvarna.volunteer.NoSuchShift;
import se.teaterihuskvarna.volunteer.ShiftDetails;
import se.teaterihuskvarna.volunteer.ShiftForm;
import se.teaterihuskvarna.volunteer.ShiftService;

/// Volunteer shift administration (R016, R020): the upcoming shifts with a form
/// to add one, each shift's page with who booked it, the CSV file of those
/// bookings, and the delete button. Spring Security lets only an administrator
/// account reach it.
@Controller
public class AdministratorShiftPageController {

    private static final String LIST = "/admin/volontarpass";

    private final ShiftService shifts;
    private final Copy copy;

    AdministratorShiftPageController(ShiftService shifts, Copy copy) {
        this.shifts = shifts;
        this.copy = copy;
    }

    /// @param model receives the shifts, the events and an empty form
    /// @return the list of shifts
    @GetMapping(LIST)
    public String list(Model model) {
        return page(ShiftForm.empty(), FieldErrors.none(), model);
    }

    /// @param form       what was typed
    /// @param model      receives the page again when the form has errors
    /// @param redirected receives the confirmation shown after the redirect
    /// @return a redirect to the new shift, or the list again with what was wrong
    @PostMapping(LIST)
    public String create(@ModelAttribute("form") ShiftForm form, Model model, RedirectAttributes redirected) {
        ShiftDetails created;
        try {
            created = shifts.create(form);
        } catch (ConstraintViolationException e) {
            return page(form, FieldErrors.of(e), model);
        } catch (NoSuchEvent e) {
            model.addAttribute("error", copy.text("adminShifts.error.event"));
            return page(form, FieldErrors.none(), model);
        }
        redirected.addFlashAttribute("notice", copy.text("adminShifts.created"));
        return "redirect:" + LIST + "/" + created.id();
    }

    /// @param id    the shift
    /// @param model receives the shift and who booked it
    /// @return the shift's page
    @GetMapping(LIST + "/{id}")
    public String shift(@PathVariable long id, Model model) {
        model.addAttribute("shift", shifts.details(id));
        model.addAttribute("volunteers", shifts.volunteers(id));
        return "admin/shift";
    }

    /// @param id         the shift, which takes its bookings with it
    /// @param redirected receives the confirmation shown after the redirect
    /// @return a redirect to the list
    @PostMapping(LIST + "/{id}/ta-bort")
    public String delete(@PathVariable long id, RedirectAttributes redirected) {
        shifts.delete(id);
        redirected.addFlashAttribute("notice", copy.text("adminShifts.deleted"));
        return "redirect:" + LIST;
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

    private String page(ShiftForm form, FieldErrors errors, Model model) {
        model.addAttribute("shifts", shifts.list());
        model.addAttribute("past", shifts.past());
        model.addAttribute("events", shifts.events());
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        return "admin/shifts";
    }
}
