package se.teaterihuskvarna.development;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import se.teaterihuskvarna.login.LoginKind;
import se.teaterihuskvarna.login.LoginUrls;
import se.teaterihuskvarna.login.PasskeyUrls;
import se.teaterihuskvarna.login.MailSettings;

/// What the development index shows. Read from the running application rather
/// than written down, so the index cannot fall behind the code: a new
/// controller method appears on the next start.
@Service
@Profile("dev")
public class DevelopmentService {

    private static final String OWN_PACKAGE = "se.teaterihuskvarna";
    private static final String API_PACKAGE = "se.teaterihuskvarna.api";
    private static final String FILTERS = "Spring Security";

    private final RequestMappingHandlerMapping mappings;
    private final JdbcClient jdbc;
    private final Flyway flyway;
    private final Environment environment;
    private final MailSettings mail;
    private final ObjectProvider<GitProperties> git;
    private final ObjectProvider<BuildProperties> build;
    private final String mailpitUrl;

    DevelopmentService(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings,
            JdbcClient jdbc,
            Flyway flyway,
            Environment environment,
            MailSettings mail,
            ObjectProvider<GitProperties> git,
            ObjectProvider<BuildProperties> build,
            @Value("${teaterihuskvarna.development.mailpit-url}") String mailpitUrl) {
        this.mappings = mappings;
        this.jdbc = jdbc;
        this.flyway = flyway;
        this.environment = environment;
        this.mail = mail;
        this.git = git;
        this.build = build;
        this.mailpitUrl = mailpitUrl;
    }

    /// Every controller mapping in this application, and the POSTs the login
    /// filters answer, which no mapping shows. Spring's own mappings, such as
    /// `/error`, are left out.
    ///
    /// @return the routes, pages first, then endpoints, then filters, each by path
    public List<Route> routes() {
        List<Route> routes = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> mapping : mappings.getHandlerMethods().entrySet()) {
            Class<?> controller = mapping.getValue().getBeanType();
            if (!controller.getPackageName().startsWith(OWN_PACKAGE)) {
                continue;
            }
            RouteKind kind = controller.getPackageName().startsWith(API_PACKAGE) ? RouteKind.ENDPOINT : RouteKind.PAGE;
            String methods = methods(mapping.getKey().getMethodsCondition().getMethods());
            String handledBy = controller.getSimpleName() + "." + mapping.getValue().getMethod().getName();
            for (String path : mapping.getKey().getPatternValues()) {
                routes.add(new Route(kind, methods, path, handledBy));
            }
        }
        for (LoginKind kind : LoginKind.values()) {
            LoginUrls urls = LoginUrls.of(kind);
            routes.add(new Route(RouteKind.LOGIN_FILTER, "POST", urls.page(), FILTERS + ": ask for a link"));
            routes.add(new Route(RouteKind.LOGIN_FILTER, "POST", urls.link(), FILTERS + ": log in with a token"));
            routes.add(new Route(RouteKind.LOGIN_FILTER, "POST", urls.logout(), FILTERS + ": log out"));
            PasskeyUrls passkeys = PasskeyUrls.of(kind);
            routes.add(new Route(RouteKind.LOGIN_FILTER, "POST", passkeys.loginOptions(),
                    FILTERS + ": passkey login challenge"));
            routes.add(new Route(RouteKind.LOGIN_FILTER, "POST", passkeys.login(),
                    FILTERS + ": log in with a passkey"));
            routes.add(new Route(RouteKind.LOGIN_FILTER, "POST", passkeys.registerOptions(),
                    FILTERS + ": new passkey challenge"));
            routes.add(new Route(RouteKind.LOGIN_FILTER, "POST", passkeys.register(),
                    FILTERS + ": store a new passkey"));
        }
        routes.add(new Route(RouteKind.LOGIN_FILTER, "POST", "/dev/login", FILTERS + ": immediate development login"));
        routes.add(new Route(RouteKind.LOGIN_FILTER, "POST", "/api/development/login",
                FILTERS + ": immediate development login"));
        routes.sort(Comparator.comparing(Route::kind).thenComparing(Route::path).thenComparing(Route::methods));
        return routes;
    }

    /// Every account and every active administrator account, whatever put it
    /// there: `DevSeed`, a confirmed membership application, or an administrator.
    ///
    /// @return accounts by name, then administrator accounts by name
    @Transactional(readOnly = true)
    public List<LoginAccount> loginAccounts() {
        List<LoginAccount> accounts = new ArrayList<>(jdbc.sql("""
                        SELECT a.email, m.full_name FROM account a JOIN member m ON m.id = a.member_id
                        ORDER BY m.full_name""")
                .query((row, n) -> new LoginAccount(LoginKind.MEMBER, row.getString(1), row.getString(2)))
                .list());
        accounts.addAll(jdbc.sql("""
                        SELECT email, full_name FROM administrator WHERE removed_at IS NULL
                        ORDER BY full_name""")
                .query((row, n) -> new LoginAccount(LoginKind.ADMINISTRATOR, row.getString(1), row.getString(2)))
                .list());
        return accounts;
    }

    /// @return the profiles, the schema version, and the build and commit, where
    ///         the build recorded them
    public RunningEnvironment environment() {
        MigrationInfo current = flyway.info().current();
        GitProperties commit = git.getIfAvailable();
        BuildProperties jar = build.getIfAvailable();
        return new RunningEnvironment(
                List.of(environment.getActiveProfiles()),
                mail.siteUrl().toString(),
                current == null ? null : current.getVersion().getVersion(),
                commit == null ? null : commit(commit),
                jar == null ? null : jar.getTime(),
                mailpitUrl);
    }

    /// A `git.properties` without `git.dirty` counts as uncommitted changes,
    /// since nothing then says the worktree was clean.
    private static RunningEnvironment.Commit commit(GitProperties git) {
        return new RunningEnvironment.Commit(
                Objects.requireNonNullElse(git.getShortCommitId(), "unknown"),
                git.getCommitTime(),
                !"false".equals(git.get("dirty")));
    }

    /// An empty set means the mapping takes any method.
    private static String methods(Set<RequestMethod> methods) {
        if (methods.isEmpty()) {
            return "any";
        }
        return methods.stream().map(RequestMethod::name).sorted().collect(Collectors.joining(", "));
    }
}
