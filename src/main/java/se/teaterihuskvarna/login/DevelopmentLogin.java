package se.teaterihuskvarna.login;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.stereotype.Component;

/// Immediate login in development, through a CSRF-protected POST from `/dev`
/// or its API. Spring handles session fixation and persists the security
/// context, as for link and passkey login. No mail or token is created.
@Component
@Profile("dev")
final class DevelopmentLogin extends AbstractHttpConfigurer<DevelopmentLogin, HttpSecurity> {

    private static final String PAGE_LOGIN = "/dev/login";
    private static final String API_LOGIN = "/api/development/login";

    private final DevelopmentLoginService logins;
    private final LoginSettings settings;
    private final DeviceNames devices;

    DevelopmentLogin(DevelopmentLoginService logins, LoginSettings settings, MessageSource messages) {
        this.logins = logins;
        this.settings = settings;
        this.devices = new DeviceNames(messages);
    }

    @Override
    public void configure(HttpSecurity http) {
        DevelopmentLoginFilter login = new DevelopmentLoginFilter(authentication -> {
            Credentials submitted = (Credentials) Objects.requireNonNull(authentication.getPrincipal());
            SignedIn signedIn = logins.find(submitted.kind(), submitted.email())
                    .orElseThrow(() -> new BadCredentialsException("No such development login"));
            return UsernamePasswordAuthenticationToken.authenticated(signedIn, null, signedIn.getAuthorities());
        });
        login.setSessionAuthenticationStrategy(Objects.requireNonNull(
                http.getSharedObject(SessionAuthenticationStrategy.class), "No login session strategy"));
        login.setSecurityContextRepository(Objects.requireNonNull(
                http.getSharedObject(SecurityContextRepository.class), "No security context repository"));
        login.setAuthenticationSuccessHandler((request, response, authentication) -> {
            SignedIn signedIn = (SignedIn) Objects.requireNonNull(authentication.getPrincipal());
            LoginSuccessHandler.inDevelopment(settings.session(signedIn.kind()), devices,
                    LoginUrls.of(signedIn.kind()).success(), RefusedRequests.API.matches(request))
                    .onAuthenticationSuccess(request, response, authentication);
        });
        SimpleUrlAuthenticationFailureHandler failure = new SimpleUrlAuthenticationFailureHandler("/dev?login-failed");
        login.setAuthenticationFailureHandler((request, response, exception) -> {
            if (RefusedRequests.API.matches(request)) {
                response.sendError(HttpStatus.UNAUTHORIZED.value());
            } else {
                failure.onAuthenticationFailure(request, response, exception);
            }
        });
        http.addFilterBefore(login, BasicAuthenticationFilter.class);
    }

    /// The same authentication filter serves both adapters and uses the
    /// filter chain's normal session strategy and context repository.
    private static final class DevelopmentLoginFilter extends AbstractAuthenticationProcessingFilter {

        private DevelopmentLoginFilter(AuthenticationManager manager) {
            super(new OrRequestMatcher(
                    PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, PAGE_LOGIN),
                    PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, API_LOGIN)), manager);
        }

        @Override
        public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
            LoginKind kind = switch (request.getParameter("kind")) {
                case "MEMBER" -> LoginKind.MEMBER;
                case "ADMINISTRATOR" -> LoginKind.ADMINISTRATOR;
                case null, default -> throw new BadCredentialsException("Invalid development login kind");
            };
            Credentials submitted = new Credentials(kind,
                    Objects.requireNonNullElse(request.getParameter("email"), ""));
            return getAuthenticationManager().authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(submitted, null));
        }
    }

    /// @param kind the account kind selected on the index
    /// @param email the submitted email address
    private record Credentials(LoginKind kind, String email) {
    }
}
