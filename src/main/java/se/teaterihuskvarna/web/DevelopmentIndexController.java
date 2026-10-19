package se.teaterihuskvarna.web;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import se.teaterihuskvarna.development.DevelopmentService;
import se.teaterihuskvarna.login.SignedIn;

/// Developer tools at `/dev` under the `dev` profile: every route, every address
/// that can log in, immediate-login and email-link buttons, and the running build.
///
/// The page is English and written in its template, not Swedish copy from
/// `messages_sv.properties`. `docs/decisions/0001-language-policy.md` makes
/// Swedish what a visitor or a member reads, and only a developer reads this.
@Controller
@Profile("dev")
public class DevelopmentIndexController {

    private final DevelopmentService development;

    DevelopmentIndexController(DevelopmentService development) {
        this.development = development;
    }

    /// Both login chains keep the security context in the same session
    /// attribute, so whoever is logged in, member or administrator, shows here.
    ///
    /// @param signedIn who is logged in, or null
    /// @param loginFailed present after an immediate login failed
    /// @param model    receives the routes, the accounts, the environment and the login
    /// @return the development index
    @GetMapping("/dev")
    public String index(@AuthenticationPrincipal @Nullable SignedIn signedIn,
            @RequestParam(name = "login-failed", required = false) @Nullable String loginFailed, Model model) {
        model.addAttribute("routes", development.routes());
        model.addAttribute("accounts", development.loginAccounts());
        model.addAttribute("environment", development.environment());
        model.addAttribute("signedIn", signedIn);
        model.addAttribute("loginFailed", loginFailed != null);
        return "development/index";
    }
}
