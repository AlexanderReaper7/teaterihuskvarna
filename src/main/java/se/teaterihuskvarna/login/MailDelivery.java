package se.teaterihuskvarna.login;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import se.teaterihuskvarna.outbox.OutboxHandler;
import tools.jackson.databind.json.JsonMapper;

/// Sends the mails [Mailer] put in the outbox. A `MailException` from the relay
/// leaves the mail for a later attempt. The recipient stays out of the log,
/// which is not the place to keep who asked for a login link.
@Component
class MailDelivery implements OutboxHandler {

    static final String KIND = "mail";

    private static final JsonMapper JSON = JsonMapper.shared();

    private final JavaMailSender sender;
    private final MailSettings settings;

    MailDelivery(JavaMailSender sender, MailSettings settings) {
        this.sender = sender;
        this.settings = settings;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public void handle(String payload) {
        Payload mail = JSON.readValue(payload, Payload.class);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(settings.from());
        message.setTo(mail.to());
        message.setSubject(mail.subject());
        message.setText(mail.body());
        sender.send(message);
    }

    /// @param to      the recipient's address
    /// @param subject the subject line
    /// @param body    the plain text body
    record Payload(String to, String subject, String body) {
    }
}
