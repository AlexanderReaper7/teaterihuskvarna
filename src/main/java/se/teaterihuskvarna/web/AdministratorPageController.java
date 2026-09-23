package se.teaterihuskvarna.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import se.teaterihuskvarna.administrator.AdministratorAlreadyExists;
import se.teaterihuskvarna.administrator.AdministratorDetails;
import se.teaterihuskvarna.administrator.AdministratorService;
import se.teaterihuskvarna.administrator.CannotRemoveSelf;
import se.teaterihuskvarna.administrator.NewAdministrator;
import se.teaterihuskvarna.administrator.NoSuchAdministrator;
import se.teaterihuskvarna.administrator.TooFewAdministrators;
import se.teaterihuskvarna.login.SignedIn;

/// The administrators' start page: who the administrators are, a form to add one,
/// a button to remove each, and the logged-in administrator's own passkeys.
/// Spring Security lets only an administrator account reach it.
///
/// A failed add shows the page again with the typed values kept. A remove has no
/// typed values to keep, so it always redirects back, with its outcome as a flash
/// message. Flash attributes live in the session, which Spring Session serialises
/// to PostgreSQL, so they are plain strings.
///
/// The page offers no button to remove oneself, and the service refuses it for a
/// form posted anyway.
@Controller
public class AdministratorPageController {

    private static final String REDIRECT = "redirect:/admin";

    private final AdministratorService administrators;
    private final Copy copy;
    private final PasskeySection passkeys;

    AdministratorPageController(AdministratorService administrators, Copy copy, PasskeySection passkeys) {
        this.administrators = administrators;
        this.copy = copy;
        this.passkeys = passkeys;
    }

    /// @param signedIn the logged-in administrator
    /// @param session  holds whether a link login just happened, which offers a passkey
    /// @param request  may say the offer was declined on this browser
    /// @param model    receives the administrators, an empty form, passkeys, and any flash message
    /// @return the administrators page
    @GetMapping("/admin")
    public String overview(@AuthenticationPrincipal SignedIn signedIn, HttpSession session,
            HttpServletRequest request, Model model) {
        return page(signedIn, new NewAdministrator("", ""), FieldErrors.none(), null,
                passkeys.offer(session, request), model);
    }

    /// @param signedIn   the logged-in administrator, recorded as the one who added
    /// @param form       the submitted address and name
    /// @param model      receives the page again when the add fails
    /// @param redirected receives the confirmation shown after the redirect
    /// @return a redirect to the page, or the page again with what was wrong
    @PostMapping("/admin/administratorer")
    public String add(@AuthenticationPrincipal SignedIn signedIn, @ModelAttribute("form") NewAdministrator form,
            Model model, RedirectAttributes redirected) {
        AdministratorDetails added;
        try {
            added = administrators.add(form, signedIn.id());
        } catch (ConstraintViolationException e) {
            return page(signedIn, form, FieldErrors.of(e), null, false, model);
        } catch (AdministratorAlreadyExists e) {
            return page(signedIn, form, FieldErrors.none(), copy.text("admin.error.alreadyExists"), false, model);
        }
        redirected.addFlashAttribute("notice", copy.text("admin.added", added.fullName()));
        return REDIRECT;
    }

    /// @param signedIn   the logged-in administrator, recorded as the one who removed
    /// @param id         the administrator to remove
    /// @param redirected receives the outcome, shown after the redirect
    /// @return a redirect to the page
    @PostMapping("/admin/administratorer/{id}/ta-bort")
    public String remove(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long id,
            RedirectAttributes redirected) {
        try {
            administrators.remove(id, signedIn.id());
        } catch (CannotRemoveSelf e) {
            redirected.addFlashAttribute("error", copy.text("admin.error.self"));
            return REDIRECT;
        } catch (TooFewAdministrators e) {
            redirected.addFlashAttribute("error", copy.text("admin.error.tooFew"));
            return REDIRECT;
        } catch (NoSuchAdministrator e) {
            redirected.addFlashAttribute("error", copy.text("admin.error.noSuch"));
            return REDIRECT;
        }
        redirected.addFlashAttribute("notice", copy.text("admin.removed"));
        return REDIRECT;
    }

    /// @param signedIn   the logged-in administrator, whose passkey it must be
    /// @param id         the passkey to remove
    /// @param redirected receives the outcome, shown after the redirect
    /// @return a redirect to the page
    @PostMapping("/admin/passkeys/{id}/ta-bort")
    public String removePasskey(@AuthenticationPrincipal SignedIn signedIn, @PathVariable String id,
            RedirectAttributes redirected) {
        passkeys.remove(signedIn, id, redirected);
        return REDIRECT;
    }

    /// "Fråga inte igen" on the offer.
    ///
    /// @param signedIn the logged-in administrator
    /// @param request  whether the connection is HTTPS
    /// @param response receives the cookie that stops the offer on this browser
    /// @return a redirect to the page
    @PostMapping("/admin/passkeys/fraga-inte-igen")
    public String declinePasskey(@AuthenticationPrincipal SignedIn signedIn, HttpServletRequest request,
            HttpServletResponse response) {
        passkeys.decline(signedIn, request, response);
        return REDIRECT;
    }

    private String page(SignedIn signedIn, NewAdministrator form, FieldErrors errors, @Nullable String error,
            boolean offerPasskey, Model model) {
        model.addAttribute("signedIn", signedIn);
        passkeys.addTo(model, signedIn, offerPasskey);
        model.addAttribute("administrators", administrators.list());
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        if (error != null) {
            model.addAttribute("error", error);
        }
        return "admin/overview";
    }
}
