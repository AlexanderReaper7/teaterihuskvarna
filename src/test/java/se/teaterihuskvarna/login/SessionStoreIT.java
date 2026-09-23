package se.teaterihuskvarna.login;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import se.teaterihuskvarna.IntegrationTestSupport;

/// Two requests in one session run side by side, each with its own copy of the
/// session. When both add the same attribute, such as the flash message a form
/// pressed twice stores each time, both saves insert it. Spring Session's
/// default is a plain `INSERT`, and the second failed on the primary key with a
/// 500 before [SecurityConfiguration] registered the PostgreSQL upsert.
///
/// Two copies loaded one after the other stand in for the two requests, so the
/// test does not depend on timing.
class SessionStoreIT extends IntegrationTestSupport {

    @Autowired
    private SessionRepository<? extends Session> sessions;

    @Test
    void twoCopiesOfASessionCanBothAddTheSameAttribute() {
        assertThat(saveTwoCopies(sessions)).isEqualTo("second");
    }

    private static <S extends Session> String saveTwoCopies(SessionRepository<S> repository) {
        S created = repository.createSession();
        repository.save(created);
        S first = repository.findById(created.getId());
        S second = repository.findById(created.getId());

        first.setAttribute("flash", "first");
        second.setAttribute("flash", "second");
        repository.save(first);
        repository.save(second);

        return repository.findById(created.getId()).getAttribute("flash");
    }
}
