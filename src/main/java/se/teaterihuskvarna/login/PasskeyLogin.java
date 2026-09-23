package se.teaterihuskvarna.login;

import java.time.Duration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.webauthn.authentication.HttpSessionPublicKeyCredentialRequestOptionsRepository;
import org.springframework.security.web.webauthn.authentication.PublicKeyCredentialRequestOptionsFilter;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.registration.HttpSessionPublicKeyCredentialCreationOptionsRepository;
import org.springframework.security.web.webauthn.registration.PublicKeyCredentialCreationOptionsFilter;
import org.springframework.security.web.webauthn.registration.WebAuthnRegistrationFilter;

/// Spring Security's four passkey filters, on one kind of login's own paths
/// ([PasskeyUrls]). Spring's `http.webAuthn()` would put both chains on the
/// same `/webauthn/**` and `/login/webauthn` paths, with one lookup, one
/// success page and one session attribute for both kinds; this configurer
/// builds the same filters with each of those per kind.
///
/// A configurer rather than filters added straight from [SecurityConfiguration],
/// because the session fixation defence only exists once Spring has
/// initialised the chain. `configure` runs after that, and hands the login
/// filter the same `SessionAuthenticationStrategy` the link login gets, which
/// gives the session a new id at login. Spring's own passkey configurer
/// leaves that out (7.1.1).
///
/// Two departures from Spring's placement. The filter that hands out
/// registration challenges runs after the access rules rather than before, so
/// only a logged-in member reaches `/medlem/passkeys/alternativ`; before them,
/// anyone could make Spring store an owner row. And the registration filter's
/// DELETE never matches: [PasskeyService] removes passkeys, so both adapters
/// can. The registration filter also reads with [PasskeyRegistrationConverter],
/// which refuses a label the database cannot hold.
final class PasskeyLogin extends AbstractHttpConfigurer<PasskeyLogin, HttpSecurity> {

    private final LoginKind kind;
    private final WebAuthnRelyingPartyOperations relyingParty;
    private final UserCredentialRepository passkeys;
    private final LoginDirectory directory;
    private final Duration sessionLifetime;
    private final DeviceNames devices;

    /// @param kind            the login these filters serve
    /// @param relyingParty    Spring's passkey logic, shared by both kinds
    /// @param passkeys        where registration stores a new passkey
    /// @param directory       the lookup for this kind of login
    /// @param sessionLifetime how long a login lasts
    /// @param devices         names the device a login happens on
    PasskeyLogin(LoginKind kind, WebAuthnRelyingPartyOperations relyingParty, UserCredentialRepository passkeys,
            LoginDirectory directory, Duration sessionLifetime, DeviceNames devices) {
        this.kind = kind;
        this.relyingParty = relyingParty;
        this.passkeys = passkeys;
        this.directory = directory;
        this.sessionLifetime = sessionLifetime;
        this.devices = devices;
    }

    @Override
    public void configure(HttpSecurity http) {
        PasskeyUrls urls = PasskeyUrls.of(kind);
        String attribute = PasskeyLogin.class.getName() + "." + kind.code();

        HttpSessionPublicKeyCredentialRequestOptionsRepository loginChallenges =
                new HttpSessionPublicKeyCredentialRequestOptionsRepository();
        loginChallenges.setAttrName(attribute + ".login");
        PublicKeyCredentialRequestOptionsFilter loginOptions =
                new PublicKeyCredentialRequestOptionsFilter(relyingParty);
        loginOptions.setRequestMatcher(post(urls.loginOptions()));
        loginOptions.setRequestOptionsRepository(loginChallenges);

        WebAuthnAuthenticationFilter login = new WebAuthnAuthenticationFilter();
        login.setRequiresAuthenticationRequestMatcher(post(urls.login()));
        login.setRequestOptionsRepository(loginChallenges);
        login.setAuthenticationManager(
                new ProviderManager(new PasskeyAuthenticationProvider(kind, relyingParty, directory)));
        login.setAuthenticationSuccessHandler(
                LoginSuccessHandler.byPasskey(sessionLifetime, devices, LoginUrls.of(kind).success()));
        SessionAuthenticationStrategy sessions = http.getSharedObject(SessionAuthenticationStrategy.class);
        if (sessions == null) {
            throw new IllegalStateException(
                    "No SessionAuthenticationStrategy, so passkey login would keep the session id");
        }
        login.setSessionAuthenticationStrategy(sessions);
        SecurityContextRepository contexts = http.getSharedObject(SecurityContextRepository.class);
        if (contexts != null) {
            login.setSecurityContextRepository(contexts);
        }

        HttpSessionPublicKeyCredentialCreationOptionsRepository registerChallenges =
                new HttpSessionPublicKeyCredentialCreationOptionsRepository();
        registerChallenges.setAttrName(attribute + ".register");
        PublicKeyCredentialCreationOptionsFilter registerOptions =
                new PublicKeyCredentialCreationOptionsFilter(relyingParty);
        registerOptions.setRequestMatcher(post(urls.registerOptions()));
        registerOptions.setCreationOptionsRepository(registerChallenges);

        WebAuthnRegistrationFilter register = new WebAuthnRegistrationFilter(passkeys, relyingParty);
        register.setRegisterCredentialMatcher(post(urls.register()));
        register.setRemoveCredentialMatcher(request -> false);
        register.setCreationOptionsRepository(registerChallenges);
        register.setConverter(new PasskeyRegistrationConverter());

        http.addFilterBefore(loginOptions, AuthorizationFilter.class);
        http.addFilterBefore(login, BasicAuthenticationFilter.class);
        http.addFilterAfter(registerOptions, AuthorizationFilter.class);
        http.addFilterAfter(register, AuthorizationFilter.class);
    }

    private static RequestMatcher post(String path) {
        return PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, path);
    }
}
