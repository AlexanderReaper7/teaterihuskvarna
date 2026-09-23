package se.teaterihuskvarna.login;

import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/// The devices a person is logged in on, and the end of the login on the device
/// asking. A device is one session in Spring Session's table, found by the
/// principal name it is indexed under, so the list needs no table of its own.
/// What it shows beyond Spring's own columns is the two attributes
/// [LoginSession] keeps.
///
/// Each method takes the asking request's session, because the list marks it,
/// ending it has to go through it, and extending changes it.
@Service
public class DeviceService {

    private static final Comparator<DeviceDetails> ORDER = Comparator
            .comparing(DeviceDetails::current).reversed()
            .thenComparing(DeviceDetails::lastUsed, Comparator.reverseOrder());

    private final FindByIndexNameSessionRepository<? extends Session> repository;
    private final LoginSettings settings;

    DeviceService(FindByIndexNameSessionRepository<? extends Session> repository, LoginSettings settings) {
        this.repository = repository;
        this.settings = settings;
    }

    /// @param signedIn who is logged in
    /// @param current  the asking request's session
    /// @return the devices whose login has not ended, the asking one first, then the most recently used
    public List<DeviceDetails> list(SignedIn signedIn, HttpSession current) {
        Instant now = Instant.now();
        List<DeviceDetails> devices = new ArrayList<>();
        for (Map.Entry<String, ? extends Session> entry : sessions(signedIn).entrySet()) {
            Session session = entry.getValue();
            Instant endsAt = LoginSession.endsAt(session);
            if (endsAt == null || !now.isBefore(endsAt) || session.isExpired()) {
                continue;
            }
            boolean isCurrent = entry.getKey().equals(current.getId());
            String device = LoginSession.device(session);
            devices.add(new DeviceDetails(
                    Tokens.hash(entry.getKey()),
                    device == null ? "" : device,
                    session.getCreationTime(),
                    isCurrent ? Instant.ofEpochMilli(current.getLastAccessedTime()) : session.getLastAccessedTime(),
                    isCurrent));
        }
        devices.sort(ORDER);
        return devices;
    }

    /// Ends the login on one device. The next request from there finds no
    /// login, and a page left open there says so when it next asks
    /// ([#status]). Ending the asking device logs it out.
    ///
    /// @param signedIn who is logged in
    /// @param id       the device's id, as [DeviceDetails#id] gives it
    /// @param current  the asking request's session
    /// @throws NoSuchDevice if no login of this person has that id
    public void end(SignedIn signedIn, String id, HttpSession current) {
        for (String sessionId : sessions(signedIn).keySet()) {
            if (Tokens.hash(sessionId).equals(id)) {
                end(sessionId, current);
                return;
            }
        }
        throw new NoSuchDevice();
    }

    /// "Logga ut överallt annars": ends the login on every device but the asking one.
    ///
    /// @param signedIn who is logged in
    /// @param current  the asking request's session, which stays logged in
    public void endOthers(SignedIn signedIn, HttpSession current) {
        for (String sessionId : sessions(signedIn).keySet()) {
            if (!sessionId.equals(current.getId())) {
                repository.deleteById(sessionId);
            }
        }
    }

    /// Starts the asking login's full lifetime again from now: 30 days for a
    /// member and 8 hours for an administrator, by default.
    ///
    /// @param signedIn who is logged in, whose kind sets the lifetime
    /// @param current  the asking request's session
    /// @return when the login now ends
    public LoginStatus extend(SignedIn signedIn, HttpSession current) {
        Duration lifetime = settings.session(signedIn.kind());
        LoginSession.extend(current, Instant.now().plus(lifetime));
        return status(current);
    }

    /// @param current the asking request's session
    /// @return when its login ends
    public LoginStatus status(HttpSession current) {
        Instant now = Instant.now();
        Instant endsAt = LoginSession.endsAt(current);
        if (endsAt == null) {
            // LoginExpiryFilter has already ended such a login.
            return new LoginStatus(now, 0);
        }
        return new LoginStatus(endsAt, Math.max(0, Duration.between(now, endsAt).toSeconds()));
    }

    private Map<String, ? extends Session> sessions(SignedIn signedIn) {
        return repository.findByPrincipalName(signedIn.getUsername());
    }

    /// The asking session ends through the request, so this request's own copy
    /// of it ends with the row. Deleting the row under it also logs the device
    /// out: swapping `deleteById` in on 2026-09-23 failed no test, and the save
    /// at the end of the request finds no row to write to, flash message or not.
    private void end(String sessionId, HttpSession current) {
        if (sessionId.equals(current.getId())) {
            current.invalidate();
        } else {
            repository.deleteById(sessionId);
        }
    }
}
