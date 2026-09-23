package se.teaterihuskvarna.login;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.session.autoconfigure.DefaultCookieSerializerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.ott.GenerateOneTimeTokenFilter;
import org.springframework.security.web.authentication.ott.GenerateOneTimeTokenRequestResolver;
import org.springframework.security.web.authentication.ott.RedirectOneTimeTokenGenerationSuccessHandler;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.session.jdbc.PostgreSqlJdbcIndexedSessionRepositoryCustomizer;
import org.springframework.session.security.web.authentication.SpringSessionRememberMeServices;
import org.springframework.util.StringUtils;

/// The access rules and the two kinds of login, one filter chain each. Each
/// kind logs in by link or by passkey ([PasskeyLogin]).
///
/// Every path is denied unless a rule below grants it, as `docs/projektplan.md`
/// requires. A path no rule mentions answers 404 ([UnknownPaths]). The
/// administrator chain comes first and owns `/admin/**` and `/api/admin/**`;
/// the member chain takes every other request. Each chain has
/// its own login page, token store and lookup, because one address may belong
/// to both an account and an administrator account, and each page must look
/// only among its own kind.
///
/// Paths are Spring Security 7's default `PathPatternRequestMatcher` patterns
/// throughout, the same kind `requestMatchers(String)` builds, so `/admin/**`
/// also matches `/admin`.
///
/// CSRF protection stays on in both chains. A page sends the token as the
/// `_csrf` form field; a REST client reads it from `GET /api/csrf` and sends it
/// in the `X-CSRF-TOKEN` header. An unauthenticated request under `/api/`
/// gets 401 rather than a redirect to a page a client cannot use.
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfiguration {

    private final LoginLinks links;
    private final LinkRequestLimiter limiter;
    private final JdbcClient jdbc;
    private final LoginSettings settings;
    private final List<LoginDirectory> directories;
    private final WebAuthnRelyingPartyOperations relyingParty;
    private final UserCredentialRepository passkeys;

    SecurityConfiguration(LoginLinks links, LinkRequestLimiter limiter, JdbcClient jdbc, LoginSettings settings,
            List<LoginDirectory> directories, WebAuthnRelyingPartyOperations relyingParty,
            UserCredentialRepository passkeys) {
        this.links = links;
        this.limiter = limiter;
        this.jdbc = jdbc;
        this.settings = settings;
        this.directories = List.copyOf(directories);
        this.relyingParty = relyingParty;
        this.passkeys = passkeys;
    }

    /// @param http Spring's builder for this chain
    /// @return the chain for `/admin/**` and `/api/admin/**`
    @Bean
    @Order(1)
    SecurityFilterChain administratorChain(HttpSecurity http) {
        LoginUrls urls = LoginUrls.of(LoginKind.ADMINISTRATOR);
        http.securityMatcher("/admin/**", "/api/admin/**");
        http.authorizeHttpRequests(requests -> requests
                .requestMatchers(urls.page(), urls.page() + "/**").permitAll()
                .anyRequest().hasRole("ADMINISTRATOR"));
        login(http, LoginKind.ADMINISTRATOR, settings.administratorSession());
        // Only this chain: a member cannot be removed, and an administrator
        // can read the whole register.
        http.addFilterBefore(new ActiveLoginFilter(Directories.of(LoginKind.ADMINISTRATOR, directories)),
                AuthorizationFilter.class);
        return http.build();
    }

    /// @param http Spring's builder for this chain
    /// @return the chain for every request the administrator chain does not take
    @Bean
    @Order
    SecurityFilterChain memberChain(HttpSecurity http) {
        http.authorizeHttpRequests(requests -> requests
                .requestMatchers(
                        "/",
                        "/logga-in",
                        "/logga-in/**",
                        "/bli-medlem",
                        "/bli-medlem/**",
                        "/api/membership-applications",
                        "/api/membership-applications/**",
                        "/api/csrf",
                        // Only the dev profile maps anything here; elsewhere
                        // these paths answer 404.
                        "/api/development/**",
                        "/error").permitAll()
                // The stylesheet, fonts, logo, favicon and scripts under
                // static/, which the pages link to. Named here rather than with Boot's
                // PathRequest.toStaticResources().atCommonLocations(), which
                // would also open /images/**, /webjars/**, /favicon.* and
                // /icons/icon-*, none of which this application serves.
                .requestMatchers(HttpMethod.GET, "/css/**", "/fonts/**", "/img/**", "/js/**").permitAll()
                .requestMatchers("/medlem", "/medlem/**", "/api/member", "/api/member/**").hasRole("MEMBER")
                .anyRequest().access(UnknownPaths.denied()));
        login(http, LoginKind.MEMBER, settings.memberSession());
        return http.build();
    }

    /// Sets the session cookie to outlive the browser once someone logs in; see
    /// [LoginSuccessHandler] for why that is safe. A customizer rather than a
    /// `CookieSerializer` bean, because a bean of that type replaces Spring
    /// Boot's own serializer and with it every `server.servlet.session.cookie.*`
    /// property. Boot 4.1 already sets the same attribute when Spring Security is
    /// present, and `Lax` is `DefaultCookieSerializer`'s default; both are set
    /// here so neither silently depends on a default.
    ///
    /// The cookie's `Secure` flag is left to the serializer, which sets it when
    /// the request is secure. Behind Traefik that relies on
    /// `server.forward-headers-strategy`, set in `application.yaml`.
    ///
    /// @return the customizer Boot applies to its `DefaultCookieSerializer`
    @Bean
    DefaultCookieSerializerCustomizer sessionCookie() {
        return serializer -> {
            serializer.setRememberMeRequestAttribute(SpringSessionRememberMeServices.REMEMBER_ME_LOGIN_ATTR);
            serializer.setSameSite("Lax");
        };
    }

    /// Stores a session attribute with `INSERT ... ON CONFLICT DO UPDATE`.
    /// Spring Session's default is a plain `INSERT`, so two requests in one
    /// session that both add the same attribute, such as a form pressed twice
    /// that stores a flash message each time, make the second fail on the
    /// primary key with a 500.
    ///
    /// @return the customizer Boot applies to the session repository
    @Bean
    PostgreSqlJdbcIndexedSessionRepositoryCustomizer sessionAttributeUpsert() {
        return new PostgreSqlJdbcIndexedSessionRepositoryCustomizer();
    }

    /// The one-time-token login, passkeys, the rate limit, logout, the entry
    /// point, the refusals and the session at login, all of which differ between
    /// the two chains only by kind.
    private void login(HttpSecurity http, LoginKind kind, Duration sessionLifetime) {
        LoginUrls urls = LoginUrls.of(kind);
        HashedTokenService tokens = new HashedTokenService(kind, links, jdbc);
        LoginDirectory directory = Directories.of(kind, directories);
        DirectoryUserDetails users = new DirectoryUserDetails(directory);
        http.oneTimeTokenLogin(login -> login
                .tokenService(tokens)
                // Without an explicit provider the configurer looks up a single
                // UserDetailsService bean, and there is one per kind, not one.
                .authenticationProvider(new OneTimeTokenAuthenticationProvider(tokens, users))
                .tokenGeneratingUrl(urls.page())
                .loginProcessingUrl(urls.link())
                .loginPage(urls.page())
                .showDefaultSubmitPage(false)
                .generateRequestResolver(emailField(settings.linkLifetime()))
                .tokenGenerationSuccessHandler(new RedirectOneTimeTokenGenerationSuccessHandler(urls.sent()))
                .authenticationConverter(BoundLogin::from)
                .authenticationSuccessHandler(LoginSuccessHandler.byLink(sessionLifetime, urls.success()))
                .authenticationFailureHandler(failure(urls)));
        http.with(new PasskeyLogin(kind, relyingParty, passkeys, directory, sessionLifetime),
                Customizer.withDefaults());
        http.addFilterBefore(new LinkRequestLimitFilter(limiter, urls), GenerateOneTimeTokenFilter.class);
        http.addFilterBefore(new LoginBrowserFilter(urls, settings.linkLifetime()), LinkRequestLimitFilter.class);
        http.logout(logout -> logout
                .logoutUrl(urls.logout())
                .logoutSuccessUrl(urls.loggedOut()));
        http.exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(entryPoint(urls))
                .accessDeniedHandler(new RefusedRequests(urls)));
        // A new session at login, not the same row under a new id, which is
        // Spring's default. Spring Session JDBC saves a session by its row and
        // writes the id with it, so a request that loaded the session before the
        // login and saves after it, such as a font the link page is still
        // loading, would put the old id back and lose the login. With a new row
        // that save finds nothing to update. PasskeyLogin reads the same
        // strategy, so this covers both ways in.
        http.sessionManagement(sessions -> sessions
                .sessionFixation(SessionManagementConfigurer.SessionFixationConfigurer::migrateSession));
        // Spring saves a refused anonymous request in the session, to go back
        // to after login. LoginSuccessHandler always goes to the chain's own
        // page and never reads it, so the save only wrote a session row for
        // every refused request, a bot's 404s included.
        http.requestCache(cache -> cache.disable());
    }

    /// Reads the address from the form field `email`, not Spring's `username`,
    /// and asks for the configured lifetime. Spring's default request says 5
    /// minutes, which would disagree with the token [LoginLinks] stores. The
    /// browser's value comes from [LoginBrowserFilter], which ran first.
    ///
    /// A blank field gives null, which makes Spring's filter pass the request
    /// on. [LinkRequestLimitFilter] has already sent a blank form back, so that
    /// does not happen in practice.
    private static GenerateOneTimeTokenRequestResolver emailField(Duration linkLifetime) {
        return request -> {
            String email = request.getParameter("email");
            String browser = LoginBrowser.read(request);
            if (!StringUtils.hasText(email) || browser == null) {
                return null;
            }
            return new BoundLinkRequest(email, linkLifetime, browser);
        };
    }

    /// A wrong code goes back to the page it was typed on, which says so. A
    /// link that fails goes to the login form, as before codes existed.
    private static AuthenticationFailureHandler failure(LoginUrls urls) {
        AuthenticationFailureHandler link = new SimpleUrlAuthenticationFailureHandler(urls.failure());
        AuthenticationFailureHandler code = new SimpleUrlAuthenticationFailureHandler(urls.sent() + "?fel");
        return (request, response, exception) -> {
            boolean byCode = !StringUtils.hasText(request.getParameter("token"));
            (byCode ? code : link).onAuthenticationFailure(request, response, exception);
        };
    }

    /// 404 for a path no rule mentions, then 401 under `/api/`, and a redirect
    /// to the login page everywhere else.
    ///
    /// Built whole and set as the chain's only entry point, rather than added
    /// with `defaultAuthenticationEntryPointFor`. That method falls back to the
    /// first entry point registered when no matcher applies, which would be
    /// the 401, so a request for a page that did not ask for HTML, such as one
    /// from curl, would get 401 instead of the login page.
    private static AuthenticationEntryPoint entryPoint(LoginUrls urls) {
        return DelegatingAuthenticationEntryPoint.builder()
                .addEntryPointFor(
                        (request, response, refused) -> UnknownPaths.notFound(request, response),
                        UnknownPaths.MARKED)
                .addEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), RefusedRequests.API)
                .defaultEntryPoint(new LoginUrlAuthenticationEntryPoint(urls.page()))
                .build();
    }
}
