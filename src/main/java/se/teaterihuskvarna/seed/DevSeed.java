package se.teaterihuskvarna.seed;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/// Fills an empty development database with invented members, accounts and
/// administrators, so every page has something to show and every login path
/// has an address to try.
///
/// Invented data only. `docs/projektplan.md` says development and test
/// environments use invented data, so no name, address or phone number here
/// belongs to a real person. Every address is under `.test`, a top-level
/// domain RFC 2606 reserves for testing, so none of them can reach anyone.
///
/// Profile `dev` only. Runs after `FirstAdministrator` in the administrator
/// package has created the first administrator (order 0), so the others can
/// name that one as the administrator who added them. Does nothing once the
/// member table has a row, so restarting keeps whatever was changed by hand.
///
/// What the data covers:
///
/// - two households, one with a child member who has no account;
/// - members with and without an account, and one member with no household;
/// - Karin Holmberg, who has both an account and an administrator account on
///   the same address, which is the case the two login pages exist for;
/// - three administrators in total, so removing one works and removing a second
///   is refused;
/// - three offers: a published one with two places, one of them taken by Erik
///   Lindqvist; a published one without a limit; and an unpublished draft,
///   which members do not see;
/// - one annual meeting document, a one-page PDF built here;
/// - this year's fees: Erik Lindqvist paid for his household, which covers
///   Maria and Olle; Karin Holmberg paid for herself; the Bergströms, Anders
///   and Ingrid have not paid. Johan Bergström paid last year, so his page
///   has a year of history.
///
/// Plain SQL through `JdbcClient` rather than the entities, because this package
/// is not the member package and the repositories are package private there.
@Component
@Profile("dev")
@Order(1)
class DevSeed implements ApplicationRunner {

    /// A one-page PDF with one line of text. The text is ASCII, without å and
    /// ö, because the page names Helvetica without an encoding.
    private static final byte[] PDF = pdf("Kallelse till arsmotet");

    private final JdbcClient jdbc;

    DevSeed(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long existing = jdbc.sql("SELECT count(*) FROM member").query(Long.class).single();
        if (existing > 0) {
            return;
        }

        long lindqvist = household("Familjen Lindqvist");
        long bergstrom = household("Familjen Bergström");

        long erik = member(lindqvist, "Erik Lindqvist", "070-174 06 01", "Exempelvägen 4", "561 31");
        long maria = member(lindqvist, "Maria Lindqvist", "070-174 06 02", "Exempelvägen 4", "561 31");
        // A child added to the household: a member without an account.
        member(lindqvist, "Olle Lindqvist", null, "Exempelvägen 4", "561 31");

        long johan = member(bergstrom, "Johan Bergström", "070-174 06 03", "Provgatan 12", "561 32");
        long sara = member(bergstrom, "Sara Bergström", null, "Provgatan 12", "561 32");

        long karin = member(null, "Karin Holmberg", "070-174 06 04", "Påhittade gränd 7", "561 33");
        long anders = member(null, "Anders Sjöberg", "070-174 06 05", "Låtsasvägen 2", "561 34");
        // An adult without an account.
        member(null, "Ingrid Nyström", "070-174 06 06", "Exempelvägen 9", "561 31");

        account(erik, "erik.lindqvist@example.test");
        account(maria, "maria.lindqvist@example.test");
        account(johan, "johan.bergstrom@example.test");
        account(sara, "sara.bergstrom@example.test");
        account(karin, "karin.holmberg@example.test");
        account(anders, "anders.sjoberg@example.test");

        Long first = jdbc.sql("SELECT id FROM administrator ORDER BY id LIMIT 1").query(Long.class).optional()
                .orElse(null);
        administrator("karin.holmberg@example.test", "Karin Holmberg", first);
        administrator("gunnar.wik@example.test", "Gunnar Wik", first);

        fee(erik, 0, "HOUSEHOLD", 10_000, first);
        fee(karin, 0, "INDIVIDUAL", 5_000, first);
        fee(johan, 1, "INDIVIDUAL", 5_000, first);

        long workshop = offer("Improvisationsverkstad", """
                En kväll med övningar i improvisation för alla medlemmar.
                Ta med bekväma kläder och vattenflaska.""", "30 days", "25 days", 2, true);
        offer("Rabatt på Skräckkabinettet", "Medlemmar får 50 kronor rabatt på biljetten.", "60 days", null, null,
                true);
        offer("Sommarläger", "Utkast, inte publicerat.", "200 days", null, 20, false);
        jdbc.sql("INSERT INTO offer_registration (offer_id, member_id, created_at) VALUES (:offer, :member, now())")
                .param("offer", workshop)
                .param("member", erik)
                .update();

        jdbc.sql("""
                INSERT INTO member_document
                    (title, kind, filename, content, size_bytes, uploaded_at, uploaded_by, published_on)
                VALUES (:title, 'ANNUAL_MEETING', 'kallelse-arsmote.pdf', :content, :size, now(), :by,
                    DATE '2026-03-14')
                """)
                .param("title", "Kallelse till årsmötet 2026")
                .param("content", PDF)
                .param("size", PDF.length)
                .param("by", first)
                .update();
    }

    /// `startsIn` and `closesIn` are PostgreSQL intervals from now, so the
    /// offers stay in the future however long ago the database was seeded.
    private long offer(String title, String description, String startsIn, @Nullable String closesIn,
            @Nullable Integer capacity, boolean published) {
        return jdbc.sql("""
                INSERT INTO offer (title, description, starts_at, registration_closes_at, capacity, published,
                    created_at, updated_at)
                VALUES (:title, :description, now() + CAST(:startsIn AS interval),
                    now() + CAST(:closesIn AS interval), :capacity, :published, now(), now())
                RETURNING id
                """)
                .param("title", title)
                .param("description", description)
                .param("startsIn", startsIn)
                .param("closesIn", closesIn)
                .param("capacity", capacity)
                .param("published", published)
                .query(Long.class)
                .single();
    }

    /// The year is taken in Sweden, as the fee pages take it.
    ///
    /// @param yearsAgo 0 for this year, 1 for last year
    private void fee(long member, int yearsAgo, String kind, int amountOre, @Nullable Long markedBy) {
        jdbc.sql("""
                INSERT INTO fee (member_id, year, kind, amount_ore, paid_at, marked_by, household_id)
                SELECT id, EXTRACT(YEAR FROM now() AT TIME ZONE 'Europe/Stockholm')::int - :yearsAgo,
                        :kind, :amount, now() - make_interval(years => :yearsAgo), :markedBy,
                        CASE WHEN :kind = 'HOUSEHOLD' THEN household_id END
                FROM member WHERE id = :member
                """)
                .param("member", member)
                .param("yearsAgo", yearsAgo)
                .param("kind", kind)
                .param("amount", amountOre)
                .param("markedBy", markedBy)
                .update();
    }

    private long household(String name) {
        return jdbc.sql("INSERT INTO household (name, created_at) VALUES (:name, now()) RETURNING id")
                .param("name", name)
                .query(Long.class)
                .single();
    }

    private long member(@Nullable Long household, String fullName, @Nullable String phone, String address,
            String postalCode) {
        return jdbc.sql("""
                INSERT INTO member (household_id, full_name, phone, address, postal_code, city, created_at)
                VALUES (:household, :fullName, :phone, :address, :postalCode, 'Huskvarna', now())
                RETURNING id
                """)
                .param("household", household)
                .param("fullName", fullName)
                .param("phone", phone)
                .param("address", address)
                .param("postalCode", postalCode)
                .query(Long.class)
                .single();
    }

    private void account(long member, String email) {
        jdbc.sql("INSERT INTO account (member_id, email, created_at) VALUES (:member, :email, now())")
                .param("member", member)
                .param("email", email)
                .update();
    }

    /// `ON CONFLICT DO NOTHING` because the first administrator comes from local
    /// configuration, and may already have one of these addresses.
    private void administrator(String email, String fullName, @Nullable Long createdBy) {
        jdbc.sql("""
                INSERT INTO administrator (email, full_name, created_at, created_by)
                VALUES (:email, :fullName, now(), :createdBy)
                ON CONFLICT DO NOTHING
                """)
                .param("email", email)
                .param("fullName", fullName)
                .param("createdBy", createdBy)
                .update();
    }

    /// The smallest PDF a reader opens without complaint: a catalog, a page
    /// tree, one page, its text and a font, and the byte offset of each object
    /// in the cross-reference table.
    private static byte[] pdf(String text) {
        String stream = "BT /F1 24 Tf 72 720 Td (" + text + ") Tj ET";
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R"
                        + " /Resources << /Font << /F1 5 0 R >> >> >>",
                "<< /Length " + stream.length() + " >>\nstream\n" + stream + "\nendstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.length());
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = pdf.length();
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        for (int offset : offsets) {
            pdf.append(String.format(Locale.ROOT, "%010d", offset)).append(" 00000 n \n");
        }
        pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
