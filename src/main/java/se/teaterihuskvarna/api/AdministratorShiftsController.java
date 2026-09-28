package se.teaterihuskvarna.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import se.teaterihuskvarna.content.Event;
import se.teaterihuskvarna.export.CsvFile;
import se.teaterihuskvarna.volunteer.ShiftDetails;
import se.teaterihuskvarna.volunteer.ShiftForm;
import se.teaterihuskvarna.volunteer.ShiftService;
import se.teaterihuskvarna.volunteer.Volunteer;

/// Volunteer shift administration (R016, R020), as `/admin/volontarpass` does it.
@RestController
public class AdministratorShiftsController {

    private final ShiftService shifts;

    AdministratorShiftsController(ShiftService shifts) {
        this.shifts = shifts;
    }

    /// @return every shift from today on
    @GetMapping("/api/admin/shifts")
    public List<ShiftDetails> list() {
        return shifts.list();
    }

    /// @return every shift that started before today, latest first
    @GetMapping("/api/admin/shifts/past")
    public List<ShiftDetails> past() {
        return shifts.past();
    }

    /// @return the upcoming events a shift can be added to
    @GetMapping("/api/admin/shifts/events")
    public List<Event> events() {
        return shifts.events();
    }

    /// @param form the event, the work, the times in Swedish wall-clock time, and the places
    /// @return the new shift
    @PostMapping("/api/admin/shifts")
    @ResponseStatus(HttpStatus.CREATED)
    public ShiftDetails create(@RequestBody ShiftForm form) {
        return shifts.create(form);
    }

    /// @param id the shift
    /// @return the shift
    @GetMapping("/api/admin/shifts/{id}")
    public ShiftDetails shift(@PathVariable long id) {
        return shifts.details(id);
    }

    /// @param id the shift, which takes its bookings with it
    @DeleteMapping("/api/admin/shifts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        shifts.delete(id);
    }

    /// @param id the shift
    /// @return who booked it, in the order they did
    @GetMapping("/api/admin/shifts/{id}/volunteers")
    public List<Volunteer> volunteers(@PathVariable long id) {
        return shifts.volunteers(id);
    }

    /// @param id the shift
    /// @return the bookings as CSV, as an attachment
    @GetMapping("/api/admin/shifts/{id}/volunteers.csv")
    public ResponseEntity<String> volunteersCsv(@PathVariable long id) {
        CsvFile file = shifts.volunteersCsv(id);
        return ResponseEntity.ok().headers(file.headers()).body(file.text());
    }
}
