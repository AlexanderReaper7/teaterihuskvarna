package se.teaterihuskvarna.mailing;

import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import se.teaterihuskvarna.member.Audience;
import se.teaterihuskvarna.member.MemberService;
import se.teaterihuskvarna.member.Recipient;
import se.teaterihuskvarna.offer.OfferDetails;
import se.teaterihuskvarna.offer.OfferService;
import se.teaterihuskvarna.volunteer.ShiftService;

/// Mailings, R022 to R025, prepared here and sent from Brevo:
/// `docs/decisions/0005-brevo-campaign-drafts.md` and
/// `docs/decisions/0023-brevo-list-per-mailing.md`.
///
/// Preparing a mailing picks the audience from the register, makes a new
/// Brevo list with those addresses, and creates a draft campaign to that list
/// with HTML built from published Sanity content. The administrator can look
/// at the HTML here first and send a test to themselves. The real send happens
/// in Brevo, which also owns the unsubscribe list: the application never sets
/// a contact's blacklist, so an unsubscribe outlives every later sync.
///
/// Nothing here holds a transaction while Brevo is called. A mailing is saved
/// only after its campaign exists, so a failure half way leaves at most an
/// unused list in Brevo, never a row pointing at nothing.
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

    /// A volunteer, as a mailing audience, is a member with a shift in this many days before today.
    private static final int VOLUNTEER_DAYS = 365;

    /// How many news items the form offers: the newest.
    private static final int NEWS_OFFERED = 20;

    private static final String OFFER = "OFFER:";

    private final Brevo brevo;
    private final MailingRepository mailings;
    private final ContentService content;
    private final MemberService members;
    private final OfferService offers;
    private final ShiftService shifts;
    private final MailSettings site;
    private final TemplateEngine templates;
    private final MessageSource messages;
    private final Clock clock;

    MailingService(Brevo brevo, MailingRepository mailings, ContentService content, MemberService members,
            OfferService offers, ShiftService shifts, MailSettings site, TemplateEngine templates,
            MessageSource messages, Clock clock) {
        this.brevo = brevo;
        this.mailings = mailings;
        this.content = content;
        this.members = members;
        this.offers = offers;
        this.shifts = shifts;
        this.site = site;
        this.templates = templates;
        this.messages = messages;
        this.clock = clock;
    }

    /// The audiences the user chose on 2026-09-28: every member, members who
    /// have paid this year or have not, volunteers, and the members registered
    /// to each offer. Each reaches only its members with an account.
    ///
    /// @return the audiences, the fixed ones first and then one per offer, newest first
    public List<AudienceChoice> audiences() {
        List<AudienceChoice> choices = new ArrayList<>();
        for (Audience audience : Audience.values()) {
            choices.add(new AudienceChoice(audience.name(), text("mailing.audience." + audience.name())));
        }
        choices.add(new AudienceChoice("VOLUNTEERS", text("mailing.audience.VOLUNTEERS", VOLUNTEER_DAYS / 30)));
        for (OfferDetails offer : offers.list()) {
            choices.add(new AudienceChoice(OFFER + offer.id(), text("mailing.audience.OFFER", offer.title())));
        }
        return choices;
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

    /// R022: makes the Brevo list and the draft campaign, and logs the mailing.
    ///
    /// @param form            the subject, words, audience and content
    /// @param administratorId who prepares it
    /// @return the logged mailing, in status `draft`
    /// @throws EmptyMailing if the mail would say nothing
    /// @throws UnknownContent if an event or news item is not published
    /// @throws UnknownAudience if the audience is not one [#audiences] offers
    /// @throws NoRecipients if nobody in the audience has an account
    /// @throws BrevoUnavailable if Brevo refuses or cannot be reached
    /// @throws jakarta.validation.ConstraintViolationException if the form breaks a constraint
    public MailingDetails prepare(@Valid MailingForm form, long administratorId) {
        String html = html(form);
        String audienceName = audienceName(form.audience());
        List<String> addresses = addresses(form.audience());
        if (addresses.isEmpty()) {
            throw new NoRecipients("the audience " + form.audience() + " has nobody with an account");
        }
        Instant now = clock.instant();
        String name = text("mailing.brevoName", LIST_DATE.format(now), form.subject().strip());
        long listId = brevo.createList(name);
        for (String address : addresses) {
            brevo.addContact(address, listId);
        }
        long campaignId = brevo.createDraft(new Brevo.Campaign(name, form.subject().strip(), html, listId));
        Mailing mailing = mailings.save(new Mailing(form.subject().strip(), form.audience(), audienceName,
                addresses.size(), listId, campaignId, administratorId, now));
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

    private List<String> addresses(String audience) {
        List<Recipient> recipients;
        if ("VOLUNTEERS".equals(audience)) {
            recipients = shifts.recentVolunteers(clock.instant().minus(Duration.ofDays(VOLUNTEER_DAYS)));
        } else if (audience.startsWith(OFFER)) {
            recipients = offers.recipients(offerId(audience));
        } else {
            recipients = members.recipients(fixed(audience));
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (Recipient recipient : recipients) {
            unique.putIfAbsent(recipient.email().toLowerCase(Locale.ROOT), recipient.email());
        }
        return List.copyOf(unique.values());
    }

    private String audienceName(String audience) {
        return audiences().stream()
                .filter(choice -> choice.value().equals(audience))
                .map(AudienceChoice::name)
                .findFirst()
                .orElseThrow(() -> new UnknownAudience("no audience " + audience));
    }

    private static long offerId(String audience) {
        try {
            return Long.parseLong(audience.substring(OFFER.length()));
        } catch (NumberFormatException e) {
            throw new UnknownAudience("no audience " + audience);
        }
    }

    private static Audience fixed(String audience) {
        try {
            return Audience.valueOf(audience);
        } catch (IllegalArgumentException e) {
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
