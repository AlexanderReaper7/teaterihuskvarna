package se.teaterihuskvarna.api;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import se.teaterihuskvarna.development.DevelopmentService;
import se.teaterihuskvarna.development.LoginAccount;
import se.teaterihuskvarna.development.Route;
import se.teaterihuskvarna.development.RunningEnvironment;

/// What the development index on `/` shows, under the `dev` profile only.
@RestController
@Profile("dev")
public class DevelopmentController {

    private final DevelopmentService development;

    DevelopmentController(DevelopmentService development) {
        this.development = development;
    }

    /// @return every route the application answers
    @GetMapping("/api/development/routes")
    public List<Route> routes() {
        return development.routes();
    }

    /// @return every address that can log in right now
    @GetMapping("/api/development/login-accounts")
    public List<LoginAccount> loginAccounts() {
        return development.loginAccounts();
    }

    /// @return the profiles, the schema version, and the build and commit
    @GetMapping("/api/development/environment")
    public RunningEnvironment environment() {
        return development.environment();
    }
}
