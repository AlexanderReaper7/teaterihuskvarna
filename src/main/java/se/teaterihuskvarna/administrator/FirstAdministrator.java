package se.teaterihuskvarna.administrator;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import se.teaterihuskvarna.login.Email;

/// Creates the first administrator account on a database that has none, from
/// [FirstAdministratorSettings]. Without it nobody could log in to add anyone.
///
/// Checks for any row, removed ones included, not for an active one. The
/// configuration is how a fresh install gets its first administrator, not a
/// standing administrator that comes back whenever the others are gone.
///
/// Order 0, so the dev seed (order 1) can name this administrator as the one
/// who added the others.
@Component
@Order(0)
class FirstAdministrator implements ApplicationRunner {

    private final AdministratorRepository administrators;
    private final FirstAdministratorSettings settings;

    FirstAdministrator(AdministratorRepository administrators, FirstAdministratorSettings settings) {
        this.administrators = administrators;
        this.settings = settings;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (administrators.count() > 0) {
            return;
        }
        String email = settings.email();
        String fullName = settings.fullName();
        if (email == null || email.isBlank() || fullName == null || fullName.isBlank()) {
            throw new IllegalStateException("No administrator exists, so FIRST_ADMINISTRATOR_EMAIL and"
                    + " FIRST_ADMINISTRATOR_NAME must both be set to create the first one.");
        }
        administrators.save(new Administrator(new Email(email), fullName.strip(), null));
    }
}
