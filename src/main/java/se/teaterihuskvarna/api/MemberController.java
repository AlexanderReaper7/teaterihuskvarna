package se.teaterihuskvarna.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import se.teaterihuskvarna.login.SignedIn;
import se.teaterihuskvarna.member.MemberDetails;
import se.teaterihuskvarna.member.MemberService;

/// The logged-in member's own details, as `/medlem` shows them.
@RestController
public class MemberController {

    private final MemberService members;

    MemberController(MemberService members) {
        this.members = members;
    }

    /// @param signedIn the logged-in account
    /// @return 200 with the member, or 404 if the member was removed while the session lived on
    @GetMapping("/api/member")
    public ResponseEntity<MemberDetails> member(@AuthenticationPrincipal SignedIn signedIn) {
        return ResponseEntity.of(members.findByAccount(signedIn.id()));
    }
}
