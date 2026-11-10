package se.teaterihuskvarna.mailing;

import gg.jte.generated.precompiled.Templates;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import se.teaterihuskvarna.Swedish;
import se.teaterihuskvarna.content.ContentService;
import se.teaterihuskvarna.content.Event;
import se.teaterihuskvarna.content.Image;
import se.teaterihuskvarna.content.NewsItem;
import se.teaterihuskvarna.content.Perspective;
import se.teaterihuskvarna.login.AdministratorId;
import se.teaterihuskvarna.login.MailSettings;

/// Mailings, R022 to R025, prepared here and sent from Brevo:
/// `docs/decisions/0005-brevo-campaign-drafts.md` and
/// `docs/decisions/0026-outbox-and-brevo-contacts.md`.
///
/// Every member with an account is already a contact on the members' list,
/// kept current by [BrevoContacts]. Preparing a mailing creates a draft
/// campaign to that list, or to a segment the association made in Brevo, with
/// HTML built from published Sanity content. The administrator can look at the
/// HTML here first and send a test to themselves. The real send happens in
/// Brevo, which also owns the unsubscribe list: the application never unblocks
/// a contact, so an unsubscribe outlives every later sync.
///
/// Nothing here holds a transaction while Brevo is called. A mailing is saved
/// as `creating` before its draft is asked for, so an answer that is lost
/// leaves a row that finds its campaign later: [#prepare]. Every failure to
/// reach Brevo is logged with its cause, because the administrator is only
/// told to try again.
@Service
@Validated
public class MailingService {

    private static final Logger LOG = LoggerFactory.getLogger(MailingService.class);

    private static final ZoneId SWEDEN = ZoneId.of("Europe/Stockholm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Swedish.LOCALE)
            .withZone(SWEDEN);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
            .ofPattern("d MMMM yyyy 'kl.' HH:mm", Swedish.LOCALE).withZone(SWEDEN);
    private static final DateTimeFormatter LIST_DATE = DateTimeFormatter.ISO_LOCAL_DATE.withZone(SWEDEN);

    /// The audience value for the whole members' list.
    static final String LIST = "LIST";

    /// How many news items the form offers: the newest.
    private static final int NEWS_OFFERED = 20;

    private static final String SEGMENT = "SEGMENT:";

    /// How long a mailing stays `creating` while Brevo neither answered nor
    /// shows its campaign. Brevo took 10 s to make a draft once on 2026-11-06,
    /// and under a second otherwise. After this the attempt is taken as never
    /// made in Brevo, and is deleted.
    static final Duration UNANSWERED = Duration.ofMinutes(5);

    private final Brevo brevo;
    private final MailingRepository mailings;
    private final ContentService content;
    private final MailSettings site;
    private final Templates templates;
    private final MessageSource messages;
    private final Clock clock;

    MailingService(Brevo brevo, MailingRepository mailings, ContentService content, MailSettings site,
            Templates templates, MessageSource messages, Clock clock) {
        this.brevo = brevo;
        this.mailings = mailings;
        this.content = content;
        this.site = site;
        this.templates = templates;
        this.messages = messages;
        this.clock = clock;
    }

    /// The whole members' list, and each segment made in Brevo. The user
    /// chose on 2026-09-28 to keep one list and let the association build its
    /// audiences in Brevo, such as those who paid this year, the volunteers,
    /// or those registered to an offer.
    ///
    /// @return the audiences; only the list if Brevo cannot be asked
    public MailingAudiences audiences() {
        List<AudienceChoice> choices = new ArrayList<>();
        choices.add(new AudienceChoice(LIST, text("mailing.audience.LIST")));
        try {
            for (Brevo.Segment segment : brevo.segments()) {
                choices.add(new AudienceChoice(SEGMENT + segment.id(), segment.name()));
            }
        } catch (BrevoUnavailable e) {
            LOG.warn("Could not ask Brevo for its segments", e);
            return new MailingAudiences(choices, true);
        }
        return new MailingAudiences(choices, false);
    }

    /// @return the upcoming events and the newest published news, for the form
    public MailingContent content() {
        List<NewsItem> news = content.news(Perspective.PUBLISHED);
        return new MailingContent(content.upcomingEvents(null, Perspective.PUBLISHED),
                news.subList(0, Math.min(NEWS_OFFERED, news.size())));
    }

    /// R023: the mail as it will look, without touching Brevo.
    ///
    /// @param form the subject, words, audience and content
    /// @return the whole mail as HTML, with Brevo's `{{ unsubscribe }}` still in it
    /// @throws EmptyMailing if the mail would say nothing
    /// @throws UnknownContent if an event or news item is not published
    /// @throws jakarta.validation.ConstraintViolationException if the form breaks a constraint
    public String preview(@Valid MailingForm form) {
        return html(form);
    }

    /// Counts in Brevo who a mailing to the audience would reach. A segment
    /// with contacts that are not on the members' list is refused by
    /// [#prepare], and this says so first.
    ///
    /// @param audience the audience's value from [#audiences]
    /// @return the members reached, and the contacts that are not members
    /// @throws UnknownAudience if the audience is not one [#audiences] offers
    /// @throws BrevoUnavailable if Brevo refuses or cannot be reached
    public MailingReach reach(String audience) {
        audienceName(audience);
        try {
            return brevo.reach(segmentId(audience));
        } catch (BrevoUnavailable e) {
            LOG.warn("Could not ask Brevo who audience {} reaches", audience, e);
            throw e;
        }
    }

    /// R022: makes the draft campaign in Brevo, and logs the mailing. The
    /// audience is counted in Brevo first. A segment holding a contact that is
    /// not on the members' list is refused, so an administrator cannot send a
    /// member mailing to others by building a segment the wrong way. An
    /// audience that reaches no member is refused too, because Brevo makes no
    /// draft to it. Brevo can change between the check and the send; the
    /// check is at preparing.
    ///
    /// The mailing is saved as `creating` before Brevo is asked for the
    /// draft, and its number is in the campaign's name. When Brevo's answer is
    /// lost, the campaign is looked for by that name right away, and again
    /// whenever the log is read or a mailing is prepared, for [#UNANSWERED].
    /// Until then no other mailing can be prepared, so a retry cannot make a
    /// second draft of the same mail. A mailing whose draft Brevo refused is
    /// deleted at once.
    ///
    /// @param form            the subject, words, audience and content
    /// @param administratorId who prepares it
    /// @return the logged mailing, in status `draft` or what Brevo reports
    /// @throws EmptyMailing if the mail would say nothing
    /// @throws UnknownContent if an event or news item is not published
    /// @throws UnknownAudience if the audience is not one [#audiences] offers
    /// @throws AudienceHasNonMembers if the segment holds a contact that is not a member
    /// @throws EmptyAudience if no member in the audience can receive the mailing
    /// @throws EarlierAttemptPending if an earlier mailing is still `creating`
    /// @throws DraftUnanswered if Brevo did not answer and its draft is not found yet
    /// @throws BrevoUnavailable if Brevo refuses or cannot be reached
    /// @throws jakarta.validation.ConstraintViolationException if the form breaks a constraint
    public MailingDetails prepare(@Valid MailingForm form, AdministratorId administratorId) {
        String html = html(form);
        String audienceName = audienceName(form.audience());
        Long segment = segmentId(form.audience());
        String subject = form.subject().strip();
        try {
            settleUnanswered(clock.instant());
            MailingReach reach = brevo.reach(segment);
            if (reach.nonMembers() > 0) {
                throw new AudienceHasNonMembers(reach.nonMembers());
            }
            if (reach.members() == 0) {
                throw new EmptyAudience("no member in audience " + form.audience() + " can receive a mailing");
            }
        } catch (BrevoUnavailable e) {
            LOG.warn("Could not prepare mailing \"{}\" to audience {}", subject, form.audience(), e);
            throw e;
        }
        Mailing attempt;
        try {
            attempt = mailings.saveAndFlush(new Mailing(subject, form.audience(), audienceName, administratorId,
                    clock.instant()));
        } catch (DataIntegrityViolationException e) {
            if (mailings.findByStatus(Mailing.CREATING).isEmpty()) {
                throw e;
            }
            throw new EarlierAttemptPending("an earlier mailing is still waiting for Brevo's draft", e);
        }
        long campaignId;
        try {
            campaignId = brevo.createDraft(new Brevo.Campaign(brevoName(attempt), subject, html, segment));
        } catch (BrevoRefused e) {
            mailings.delete(attempt);
            LOG.warn("Brevo refused the draft for mailing {}, so it is deleted", attempt.getId(), e);
            throw e;
        } catch (BrevoUnavailable e) {
            return MailingDetails.of(unanswered(attempt, e));
        }
        attempt.created(campaignId, clock.instant());
        return MailingDetails.of(mailings.save(attempt));
    }

    /// Looks for the draft at once, since Brevo may have made it and only
    /// the answer was lost.
    ///
    /// @return the mailing with its campaign
    /// @throws DraftUnanswered if the campaign is not found yet
    private Mailing unanswered(Mailing attempt, BrevoUnavailable failure) {
        Optional<Mailing> found;
        try {
            found = resolve(attempt, clock.instant());
        } catch (BrevoUnavailable again) {
            failure.addSuppressed(again);
            found = Optional.of(attempt);
        }
        if (found.isPresent() && !found.get().creating()) {
            LOG.warn("Brevo did not answer for mailing {}, but made campaign {}", attempt.getId(),
                    found.get().getBrevoCampaignId(), failure);
            return found.get();
        }
        LOG.warn("Brevo did not answer whether it made the draft for mailing {}, which waits as creating",
                attempt.getId(), failure);
        throw new DraftUnanswered("Brevo did not answer for mailing " + attempt.getId(), failure);
    }

    private void settleUnanswered(Instant now) {
        for (Mailing attempt : mailings.findByStatus(Mailing.CREATING)) {
            resolve(attempt, now);
        }
    }

    /// Asks Brevo for the campaign named after a `creating` mailing. A
    /// campaign found becomes the mailing's, with Brevo's report on it. A
    /// mailing still without one after [#UNANSWERED] is deleted, since Brevo
    /// made nothing.
    ///
    /// @return the mailing, or empty if it was deleted
    private Optional<Mailing> resolve(Mailing attempt, Instant now) {
        List<Long> named = brevo.campaignsNamed(brevoName(attempt));
        if (named.isEmpty()) {
            if (attempt.getCreatedAt().isBefore(now.minus(UNANSWERED))) {
                mailings.delete(attempt);
                LOG.warn("Brevo never made the draft for mailing {}, so it is deleted", attempt.getId());
                return Optional.empty();
            }
            return Optional.of(attempt);
        }
        long campaignId = named.getFirst();
        attempt.created(campaignId, now);
        try {
            attempt.record(brevo.report(campaignId), now);
        } catch (BrevoUnavailable e) {
            LOG.warn("Could not ask Brevo about mailing {}", attempt.getId(), e);
        }
        return Optional.of(mailings.save(attempt));
    }

    /// @return the campaign's name in Brevo, which holds the day, the subject
    ///         and the mailing's number, so no two mailings share one
    private String brevoName(Mailing mailing) {
        return text("mailing.brevoName", LIST_DATE.format(mailing.getCreatedAt()), mailing.getSubject(),
                String.valueOf(mailing.getId()));
    }

    /// R023: sends the draft to one address, the administrator's own.
    ///
    /// @param id    the mailing
    /// @param email where the test goes
    /// @throws NoSuchMailing if no mailing has the id
    /// @throws DraftUnanswered if the mailing is still `creating`
    /// @throws BrevoUnavailable if Brevo refuses or cannot be reached
    public void sendTest(long id, String email) {
        Mailing mailing = mailings.findById(id).orElseThrow(() -> new NoSuchMailing("no mailing " + id));
        Long campaignId = mailing.getBrevoCampaignId();
        if (campaignId == null) {
            throw new DraftUnanswered("mailing " + id + " has no draft in Brevo yet");
        }
        try {
            brevo.sendTest(campaignId, email);
        } catch (BrevoUnavailable e) {
            LOG.warn("Could not send a test of mailing {}", id, e);
            throw e;
        }
    }

    /// R025: every mailing, with what Brevo last said. Asks Brevo again about
    /// each mailing that may still change and was not asked in the last
    /// minute, and about every `creating` one. If Brevo cannot be reached, the
    /// log shows the last answer.
    ///
    /// @return the mailings, newest first
    public List<MailingDetails> log() {
        Instant now = clock.instant();
        List<MailingDetails> log = new ArrayList<>();
        for (Mailing mailing : mailings.findAllByOrderByCreatedAtDescIdDesc()) {
            refresh(mailing, now).map(MailingDetails::of).ifPresent(log::add);
        }
        return log;
    }

    /// @param id the mailing
    /// @return the mailing, asking Brevo again as [#log] does
    /// @throws NoSuchMailing if no mailing has the id, or it was an attempt Brevo never made
    public MailingDetails details(long id) {
        Mailing mailing = mailings.findById(id).orElseThrow(() -> new NoSuchMailing("no mailing " + id));
        return MailingDetails.of(refresh(mailing, clock.instant())
                .orElseThrow(() -> new NoSuchMailing("mailing " + id + " was never made in Brevo")));
    }

    private Optional<Mailing> refresh(Mailing mailing, Instant now) {
        if (mailing.creating()) {
            try {
                return resolve(mailing, now);
            } catch (BrevoUnavailable e) {
                LOG.warn("Could not ask Brevo for the draft of mailing {}", mailing.getId(), e);
                return Optional.of(mailing);
            }
        }
        Long campaignId = mailing.getBrevoCampaignId();
        if (campaignId == null || !mailing.worthChecking(now)) {
            return Optional.of(mailing);
        }
        try {
            mailing.record(brevo.report(campaignId), now);
            return Optional.of(mailings.save(mailing));
        } catch (BrevoUnavailable e) {
            LOG.warn("Could not ask Brevo about mailing {}", mailing.getId(), e);
            return Optional.of(mailing);
        }
    }

    private String audienceName(String audience) {
        return audiences().choices().stream()
                .filter(choice -> choice.value().equals(audience))
                .map(AudienceChoice::name)
                .findFirst()
                .orElseThrow(() -> new UnknownAudience("no audience " + audience));
    }

    private static @Nullable Long segmentId(String audience) {
        if (LIST.equals(audience)) {
            return null;
        }
        if (!audience.startsWith(SEGMENT)) {
            throw new UnknownAudience("no audience " + audience);
        }
        try {
            return Long.parseLong(audience.substring(SEGMENT.length()));
        } catch (NumberFormatException e) {
            throw new UnknownAudience("no audience " + audience);
        }
    }

    private String html(MailingForm form) {
        List<Mail.Item> events = new ArrayList<>();
        for (String slug : form.eventSlugs()) {
            Event event = content.event(slug, Perspective.PUBLISHED)
                    .orElseThrow(() -> new UnknownContent("no published event " + slug));
            events.add(new Mail.Item(event.title(), DATE_TIME.format(event.startsAt()), event.place(),
                    event.summary(), null, imageUrl(event.image()), imageAlt(event.image()),
                    site.link("/evenemang/" + event.slug(), null)));
        }
        List<Mail.Item> news = new ArrayList<>();
        for (String slug : form.newsSlugs()) {
            NewsItem item = content.newsItem(slug, Perspective.PUBLISHED)
                    .orElseThrow(() -> new UnknownContent("no published news item " + slug));
            news.add(new Mail.Item(item.title(), DATE.format(item.publishedAt()), null, item.summary(),
                    item.bodyHtml(), imageUrl(item.image()), imageAlt(item.image()),
                    site.link("/nyheter/" + item.slug(), null)));
        }
        String typed = form.intro();
        String intro = typed == null || typed.isBlank() ? null : typed.strip();
        if (intro == null && events.isEmpty() && news.isEmpty()) {
            throw new EmptyMailing("a mailing needs words of its own or content from Sanity");
        }
        Mail.Labels labels = new Mail.Labels(text("mailing.mail.events"), text("mailing.mail.news"),
                text("mailing.mail.readMore"), text("mailing.mail.site"), site.link("/", null),
                text("mailing.mail.unsubscribe"), text("mailing.mail.why"));
        return templates.mailMailing(new Mail(form.subject().strip(), intro, events, news, labels)).render();
    }

    private static @Nullable String imageUrl(@Nullable Image image) {
        return image == null ? null : image.url();
    }

    private static String imageAlt(@Nullable Image image) {
        return image == null ? "" : image.alt();
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, Swedish.LOCALE);
    }
}
