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
import se.teaterihuskvarna.volunteer.BookingOutcome;
import se.teaterihuskvarna.volunteer.MemberShift;
import se.teaterihuskvarna.volunteer.ShiftService;

/// Volunteer shifts for the logged-in member (R016), as `/medlem/volontar`
/// shows them. Errors become statuses in [ProblemResponses]: a full or started
/// shift is 409.
@RestController
public class MemberShiftsController {

    private final ShiftService shifts;
    private final MemberService members;

    MemberShiftsController(ShiftService shifts, MemberService members) {
        this.shifts = shifts;
        this.members = members;
    }

    /// @param signedIn the logged-in account
    /// @return the shifts that have not started, with places left and whether this member booked them
    @GetMapping("/api/member/shifts")
    public List<MemberShift> list(@AuthenticationPrincipal SignedIn signedIn) {
        return shifts.upcoming(memberId(signedIn));
    }

    /// @param signedIn the logged-in account
    /// @param id       the shift
    /// @return 201 when a place was taken, 200 when the member already had one
    @PostMapping("/api/member/shifts/{id}/booking")
    public ResponseEntity<Void> book(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long id) {
        BookingOutcome outcome = shifts.book(id, memberId(signedIn));
        return ResponseEntity.status(outcome == BookingOutcome.BOOKED ? HttpStatus.CREATED : HttpStatus.OK).build();
    }

    /// 204 whether or not the member had a place, so a repeated request is harmless.
    ///
    /// @param signedIn the logged-in account
    /// @param id       the shift
    @DeleteMapping("/api/member/shifts/{id}/booking")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long id) {
        shifts.cancel(id, memberId(signedIn));
    }

    private long memberId(SignedIn signedIn) {
        return members.findByAccount(signedIn.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND))
                .id();
    }
}
