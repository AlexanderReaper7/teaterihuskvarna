package se.teaterihuskvarna.web;

import gg.jte.generated.precompiled.Templates;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.View;
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
    private final Copy copy;
    private final Templates templates;

    DevelopmentIndexController(DevelopmentService development, Copy copy, Templates templates) {
        this.development = development;
        this.copy = copy;
        this.templates = templates;
    }

    /// Both login chains keep the security context in the same session
    /// attribute, so whoever is logged in, member or administrator, shows here.
    ///
    /// @param signedIn    who is logged in, or null
    /// @param loginFailed present after an immediate login failed
    /// @param csrf        the hidden field of the page's forms, which post to the login pages
    /// @return the development index: the routes, the accounts, the environment and the login
    @GetMapping("/dev")
    public View index(@AuthenticationPrincipal @Nullable SignedIn signedIn,
            @RequestParam(name = "login-failed", required = false) @Nullable String loginFailed, CsrfField csrf) {
        return HtmlPage.of(templates.developmentIndex(copy, csrf, development.routes(), development.loginAccounts(),
                development.environment(), signedIn, loginFailed != null));
    }
}
