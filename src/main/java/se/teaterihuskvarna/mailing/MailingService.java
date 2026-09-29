package se.teaterihuskvarna.mailing;

import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import se.teaterihuskvarna.Swedish;
import se.teaterihuskvarna.content.ContentService;
import se.teaterihuskvarna.content.Event;
import se.teaterihuskvarna.content.Image;
import se.teaterihuskvarna.content.NewsItem;
import se.teaterihuskvarna.content.Perspective;
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
/// only after its campaign exists, so a failure never leaves a row pointing at
/// nothing.
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

    private final Brevo brevo;
    private final MailingRepository mailings;
    private final ContentService content;
    private final MailSettings site;
    private final TemplateEngine templates;
    private final MessageSource messages;
    private final Clock clock;

    MailingService(Brevo brevo, MailingRepository mailings, ContentService content, MailSettings site,
            TemplateEngine templates, MessageSource messages, Clock clock) {
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
        return brevo.reach(segmentId(audience));
    }

    /// R022: makes the draft campaign in Brevo, and logs the mailing. A
    /// segment is checked first: one holding a contact that is not on the
    /// members' list is refused, so an administrator cannot send a member
    /// mailing to others by building a segment the wrong way. Brevo can
    /// change between the check and the send; the check is at preparing.
    ///
    /// @param form            the subject, words, audience and content
    /// @param administratorId who prepares it
    /// @return the logged mailing, in status `draft`
    /// @throws EmptyMailing if the mail would say nothing
    /// @throws UnknownContent if an event or news item is not published
    /// @throws UnknownAudience if the audience is not one [#audiences] offers
    /// @throws AudienceHasNonMembers if the segment holds a contact that is not a member
    /// @throws BrevoUnavailable if Brevo refuses or cannot be reached
    /// @throws jakarta.validation.ConstraintViolationException if the form breaks a constraint
    public MailingDetails prepare(@Valid MailingForm form, long administratorId) {
        String html = html(form);
        String audienceName = audienceName(form.audience());
        Long segment = segmentId(form.audience());
        if (segment != null) {
            long nonMembers = brevo.reach(segment).nonMembers();
            if (nonMembers > 0) {
                throw new AudienceHasNonMembers(nonMembers);
            }
        }
        Instant now = clock.instant();
        String name = text("mailing.brevoName", LIST_DATE.format(now), form.subject().strip());
        long campaignId = brevo.createDraft(new Brevo.Campaign(name, form.subject().strip(), html, segment));
        Mailing mailing = mailings.save(new Mailing(form.subject().strip(), form.audience(), audienceName,
                campaignId, administratorId, now));
        return MailingDetails.of(mailing);
    }

    /// R023: sends the draft to one address, the administrator's own.
    ///
    /// @param id    the mailing
    /// @param email where the test goes
    /// @throws NoSuchMailing if no mailing has the id
    /// @throws BrevoUnavailable if Brevo refuses or cannot be reached
    public void sendTest(long id, String email) {
        Mailing mailing = mailings.findById(id).orElseThrow(() -> new NoSuchMailing("no mailing " + id));
        brevo.sendTest(mailing.getBrevoCampaignId(), email);
    }

    /// R025: every mailing, with what Brevo last said. Asks Brevo again about
    /// each mailing that may still change and was not asked in the last
    /// minute. If Brevo cannot be reached, the log shows the last answer.
    ///
    /// @return the mailings, newest first
    public List<MailingDetails> log() {
        Instant now = clock.instant();
        List<MailingDetails> log = new ArrayList<>();
        for (Mailing mailing : mailings.findAllByOrderByCreatedAtDescIdDesc()) {
            log.add(MailingDetails.of(refresh(mailing, now)));
        }
        return log;
    }

    /// @param id the mailing
    /// @return the mailing, asking Brevo again as [#log] does
    /// @throws NoSuchMailing if no mailing has the id
    public MailingDetails details(long id) {
        Mailing mailing = mailings.findById(id).orElseThrow(() -> new NoSuchMailing("no mailing " + id));
        return MailingDetails.of(refresh(mailing, clock.instant()));
    }

    private Mailing refresh(Mailing mailing, Instant now) {
        if (!mailing.worthChecking(now)) {
            return mailing;
        }
        try {
            mailing.record(brevo.report(mailing.getBrevoCampaignId()), now);
            return mailings.save(mailing);
        } catch (BrevoUnavailable e) {
            LOG.warn("Could not ask Brevo about mailing {}", mailing.getId(), e);
            return mailing;
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
        StringOutput output = new StringOutput();
        templates.render("mail/mailing.jte",
                Map.of("mail", new Mail(form.subject().strip(), intro, events, news, labels)), output);
        return output.toString();
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
