package se.teaterihuskvarna.login;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import se.teaterihuskvarna.IntegrationTestSupport;

/// Spring Session's JDBC store, as [SecurityConfiguration] adjusts it for
/// PostgreSQL. Each test drives the repository directly, so none depends on
/// timing.
class SessionStoreIT extends IntegrationTestSupport {

    /// Enough sessions, each about as large as a real login, that PostgreSQL
    /// joins the two session tables by hash rather than by index.
    private static final int SESSIONS = 120;
    private static final String PERSON = "member:1";
    private static final String LARGE = "x".repeat(1200);

    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessions;

    /// Two requests in one session run side by side, each with its own copy of
    /// the session. When both add the same attribute, such as the flash message
    /// a form pressed twice stores each time, both saves insert it. Spring
    /// Session's default is a plain `INSERT`, and the second failed on the
    /// primary key with a 500 before [SecurityConfiguration] registered the
    /// PostgreSQL upsert.
    ///
    /// Two copies loaded one after the other stand in for the two requests.
    @Test
    void twoCopiesOfASessionCanBothAddTheSameAttribute() {
        assertThat(saveTwoCopies(sessions)).isEqualTo("second");
    }

    /// The list of logged-in devices reads every session of one person at
    /// once. An attribute added after the login, as a later request adds one,
    /// lies apart from the session's other rows, and with Spring Session's own
    /// query a session then came back split, without its device name or its
    /// end.
    @Test
    void listingAPersonsSessionsReturnsEveryAttributeOfEach() {
        Map<String, Set<String>> listed = saveAndList(sessions);

        assertThat(listed).hasSize(SESSIONS);
        assertThat(listed.values())
                .allSatisfy(names -> assertThat(names).contains("first", "second", "third"));
        assertThat(listed.values()).filteredOn(names -> names.contains("later")).hasSize(SESSIONS / 3);
    }

    private static <S extends Session> String saveTwoCopies(FindByIndexNameSessionRepository<S> repository) {
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

    private <S extends Session> Map<String, Set<String>> saveAndList(FindByIndexNameSessionRepository<S> repository) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < SESSIONS; i++) {
            S session = repository.createSession();
            session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, PERSON);
            session.setAttribute("first", LARGE);
            session.setAttribute("second", LARGE);
            session.setAttribute("third", LARGE);
            repository.save(session);
            ids.add(session.getId());
        }
        for (int i = 0; i < SESSIONS; i += 3) {
            S session = repository.findById(ids.get(i));
            session.setAttribute("later", "x");
            repository.save(session);
        }
        jdbc.sql("ANALYZE spring_session, spring_session_attributes").update();

        return repository.findByPrincipalName(PERSON).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getAttributeNames()));
    }
}
