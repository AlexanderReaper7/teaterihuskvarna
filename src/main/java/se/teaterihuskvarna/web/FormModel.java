package se.teaterihuskvarna.web;

import org.jspecify.annotations.Nullable;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/// Puts the [CsrfField] in the model as `csrf`, for the controllers whose pages
/// have a form.
///
/// Limited to those controllers on purpose. Reading the token stores it in the
/// session, and so creates one: a row in `spring_session`. Doing that for the start
/// page would write a row for every visitor who never fills in a form, which is
/// what Spring Security's deferred token exists to avoid. The development index
/// on `/` is the exception, under the `dev` profile only, because its buttons
/// post to the login pages.
@ControllerAdvice(assignableTypes = {
    DevelopmentIndexController.class,
    LoginPagesController.class,
    MemberPageController.class,
    AdministratorPageController.class,
    MembershipApplicationPageController.class,
    MemberOfferPageController.class,
    AdministratorOfferPageController.class,
    AdministratorDocumentPageController.class})
public class FormModel {

    /// @param token the request's token, which Spring Security resolves
    /// @return the hidden field, or null where Spring Security put no token
    @ModelAttribute("csrf")
    public @Nullable CsrfField csrf(@Nullable CsrfToken token) {
        return CsrfField.of(token);
    }
}
