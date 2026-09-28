package se.teaterihuskvarna.offer;

import jakarta.validation.Valid;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import se.teaterihuskvarna.Swedish;
import se.teaterihuskvarna.export.Csv;
import se.teaterihuskvarna.export.CsvFile;
import se.teaterihuskvarna.member.Recipient;

/// Offers and registrations, R014 and R020.
///
/// A member sees only published offers, and an unpublished one answers as if
/// it did not exist ([NoSuchOffer]). A member registers and cancels until
/// registration closes ([Offer#closesAt]). Registration locks the offer row and
/// counts in the same transaction, so the capacity holds however many members
/// press the button at once.
///
/// The member-side methods take the member's id, not the account's. The
/// adapters get it from `MemberService.findByAccount`.
@Service
@Validated
@Transactional(readOnly = true)
public class OfferService {

    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(OfferForm.SWEDEN);

    private final OfferRepository offers;
    private final OfferRegistrationRepository registrations;
    private final MessageSource messages;

    OfferService(OfferRepository offers, OfferRegistrationRepository registrations, MessageSource messages) {
        this.offers = offers;
        this.registrations = registrations;
        this.messages = messages;
    }

    /// @param memberId the member asking
    /// @return the published offers still open for registration, soonest first
    public List<MemberOffer> openOffers(long memberId) {
        Instant now = Instant.now();
        List<Offer> open = offers.findOpen(now);
        Map<Long, Long> counts = counts(open);
        Set<Long> mine = new HashSet<>(registrations.findOfferIdsByMemberId(memberId));
        return open.stream()
                .map(offer -> memberOffer(offer, counts.getOrDefault(offer.getId(), 0L),
                        mine.contains(offer.getId()), now))
                .toList();
    }

    /// Also answers for a published offer whose registration has closed, so a
    /// registered member can still read what they signed up for.
    ///
    /// @param offerId  the offer
    /// @param memberId the member asking
    /// @return the offer as this member sees it
    /// @throws NoSuchOffer if no published offer has the id
    public MemberOffer offer(long offerId, long memberId) {
        Offer offer = offers.findById(offerId).filter(Offer::isPublished).orElseThrow(NoSuchOffer::new);
        return memberOffer(offer, registrations.countByOfferId(offerId),
                registrations.existsByOfferIdAndMemberId(offerId, memberId), Instant.now());
    }

    /// Takes a place. The offer row stays locked from the read until the
    /// commit, so a second registration for the same offer waits here and then
    /// counts the first one. Without the lock, two members could both count
    /// one place left and both take it.
    ///
    /// A member already registered gets [RegistrationOutcome#ALREADY_REGISTERED]
    /// even when the offer is full or has closed, since nothing is asked of it.
    ///
    /// @param offerId  the offer
    /// @param memberId the member registering
    /// @return whether a place was taken or the member already had one
    /// @throws NoSuchOffer if no published offer has the id
    /// @throws RegistrationClosed if registration has closed
    /// @throws OfferFull if every place is taken
    @Transactional
    public RegistrationOutcome register(long offerId, long memberId) {
        Offer offer = offers.lockById(offerId).filter(Offer::isPublished).orElseThrow(NoSuchOffer::new);
        if (registrations.existsByOfferIdAndMemberId(offerId, memberId)) {
            return RegistrationOutcome.ALREADY_REGISTERED;
        }
        if (!offer.isOpenAt(Instant.now())) {
            throw new RegistrationClosed();
        }
        Integer left = offer.placesLeft(registrations.countByOfferId(offerId));
        if (left != null && left == 0) {
            throw new OfferFull();
        }
        registrations.save(new OfferRegistration(offerId, memberId));
        return RegistrationOutcome.REGISTERED;
    }

    /// Gives a place back, which is allowed until registration closes.
    ///
    /// @param offerId  the offer
    /// @param memberId the member cancelling
    /// @return whether the member had a place to give back
    /// @throws NoSuchOffer if no published offer has the id
    /// @throws RegistrationClosed if registration has closed
    @Transactional
    public boolean cancel(long offerId, long memberId) {
        Offer offer = offers.findById(offerId).filter(Offer::isPublished).orElseThrow(NoSuchOffer::new);
        if (!offer.isOpenAt(Instant.now())) {
            throw new RegistrationClosed();
        }
        return registrations.deleteRegistration(offerId, memberId) > 0;
    }

    /// @return every offer, published or not, newest first
    public List<OfferDetails> list() {
        Instant now = Instant.now();
        List<Offer> all = offers.findAllNewestFirst();
        Map<Long, Long> counts = counts(all);
        return all.stream()
                .map(offer -> OfferDetails.of(offer, counts.getOrDefault(offer.getId(), 0L), now))
                .toList();
    }

    /// @param id the offer
    /// @return the offer, published or not
    /// @throws NoSuchOffer if no offer has the id
    public OfferDetails details(long id) {
        Offer offer = offers.findById(id).orElseThrow(NoSuchOffer::new);
        return OfferDetails.of(offer, registrations.countByOfferId(id), Instant.now());
    }

    /// Creates an offer, unpublished.
    ///
    /// @param form the title, description, times and capacity
    /// @return the offer as stored
    /// @throws jakarta.validation.ConstraintViolationException if the form breaks a constraint
    @Transactional
    public OfferDetails create(@Valid OfferForm form) {
        Offer offer = offers.save(new Offer(form.values()));
        return OfferDetails.of(offer, 0, Instant.now());
    }

    /// Changes an offer. Lowering the capacity below the number registered
    /// keeps every registration; the places left show 0.
    ///
    /// @param id   the offer
    /// @param form the new title, description, times and capacity
    /// @return the offer as stored
    /// @throws NoSuchOffer if no offer has the id
    /// @throws jakarta.validation.ConstraintViolationException if the form breaks a constraint
    @Transactional
    public OfferDetails update(long id, @Valid OfferForm form) {
        Offer offer = offers.findById(id).orElseThrow(NoSuchOffer::new);
        offer.change(form.values());
        return OfferDetails.of(offer, registrations.countByOfferId(id), Instant.now());
    }

    /// @param id        the offer
    /// @param published whether members can see it
    /// @throws NoSuchOffer if no offer has the id
    @Transactional
    public void setPublished(long id, boolean published) {
        offers.findById(id).orElseThrow(NoSuchOffer::new).setPublished(published);
    }

    /// Deletes an offer and, through the foreign key, every registration for it.
    ///
    /// @param id the offer
    /// @throws NoSuchOffer if no offer has the id
    @Transactional
    public void delete(long id) {
        offers.delete(offers.findById(id).orElseThrow(NoSuchOffer::new));
    }

    /// @param id the offer
    /// @return who registered, in the order they did
    /// @throws NoSuchOffer if no offer has the id
    public List<Registrant> registrations(long id) {
        if (!offers.existsById(id)) {
            throw new NoSuchOffer();
        }
        return registrations.findRegistrants(id);
    }

    /// The same rows as [#registrations], as a CSV file with Swedish column
    /// names and times in Swedish time.
    ///
    /// @param id the offer
    /// @return the file and a name for it
    /// @throws NoSuchOffer if no offer has the id
    public CsvFile registrationsCsv(long id) {
        List<List<String>> rows = registrations(id).stream()
                .map(registrant -> List.of(
                        registrant.fullName(),
                        registrant.email() == null ? "" : registrant.email(),
                        registrant.phone() == null ? "" : registrant.phone(),
                        CSV_TIME.format(registrant.registeredAt())))
                .toList();
        List<String> header = List.of(text("offer.csv.name"), text("offer.csv.email"), text("offer.csv.phone"),
                text("offer.csv.registeredAt"));
        return new CsvFile("anmalningar-erbjudande-" + id + ".csv", Csv.write(header, rows));
    }

    /// Who a mailing to an offer's registrants reaches: the registered members
    /// with an account. A registered member without one is left out, as a
    /// mailing has no address for them.
    ///
    /// @param id the offer
    /// @return the members, by name
    /// @throws NoSuchOffer if no offer has the id
    public List<Recipient> recipients(long id) {
        if (!offers.existsById(id)) {
            throw new NoSuchOffer();
        }
        return registrations.findRecipients(id);
    }

    private Map<Long, Long> counts(List<Offer> list) {
        if (list.isEmpty()) {
            return Map.of();
        }
        return registrations.countByOfferIds(list.stream().map(Offer::getId).toList()).stream()
                .collect(Collectors.toMap(RegistrationCount::offerId, RegistrationCount::registered));
    }

    private static MemberOffer memberOffer(Offer offer, long registered, boolean mine, Instant now) {
        return new MemberOffer(
                offer.getId(),
                offer.getTitle(),
                offer.getDescription(),
                offer.getStartsAt(),
                offer.closesAt(),
                offer.getCapacity(),
                offer.placesLeft(registered),
                mine,
                offer.isOpenAt(now));
    }

    private String text(String key) {
        return messages.getMessage(key, null, Swedish.LOCALE);
    }
}
