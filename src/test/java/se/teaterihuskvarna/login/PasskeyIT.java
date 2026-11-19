package se.teaterihuskvarna.login;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.webauthn4j.data.AuthenticatorAssertionResponse;
import com.webauthn4j.data.AuthenticatorAttestationResponse;
import com.webauthn4j.data.AuthenticatorSelectionCriteria;
import com.webauthn4j.data.PublicKeyCredential;
import com.webauthn4j.data.PublicKeyCredentialCreationOptions;
import com.webauthn4j.data.PublicKeyCredentialParameters;
import com.webauthn4j.data.PublicKeyCredentialRequestOptions;
import com.webauthn4j.data.PublicKeyCredentialRpEntity;
import com.webauthn4j.data.PublicKeyCredentialType;
import com.webauthn4j.data.PublicKeyCredentialUserEntity;
import com.webauthn4j.data.ResidentKeyRequirement;
import com.webauthn4j.data.UserVerificationRequirement;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.test.authenticator.webauthn.PackedAuthenticator;
import com.webauthn4j.test.authenticator.webauthn.WebAuthnAuthenticatorAdaptor;
import com.webauthn4j.test.client.ClientPlatform;
import jakarta.servlet.http.Cookie;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import se.teaterihuskvarna.IntegrationTestSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// Proves passkeys end to end, with webauthn4j's software authenticator in the
/// place of a browser and a phone. Every ceremony is real: the server hands out
/// a challenge, the authenticator signs it, and the server checks the signature
/// against what it stored. The request bodies are the ones `/js/passkey.js`
/// sends, so a change to what the server reads fails here.
///
/// Covered:
///
/// - a member and an administrator each add a passkey after a link login, and
///   log in with it on their own login page, with a new session id;
/// - a member's passkey does not log anyone in on the administrator page;
/// - the password manager sees the address, not the principal name;
/// - nobody adds a passkey without a login of the right kind;
/// - a label longer than the database holds is refused with 400;
/// - both adapters list and remove passkeys, and only one's own;
/// - removing an administrator removes their passkeys;
/// - the page offers a passkey once after a link login, and never after a
///   passkey login.
class PasskeyIT extends IntegrationTestSupport {

    private static final JsonMapper JSON = JsonMapper.shared();
    private static final Base64.Encoder ENCODE = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODE = Base64.getUrlDecoder();

    private static final String KARIN = "karin@example.test";
    private static final String BO = "bo@example.test";
    private static final String OFFER = "data-passkey-offer";

    @Test
    void aMemberAddsAPasskeyAndLogsInWithIt() throws Exception {
        long account = insertAccount("Karin Karlsson", KARIN);
        ClientPlatform phone = authenticator();
        register(LoginKind.MEMBER, phone, sessionOf(logInByLink(LoginKind.MEMBER, KARIN)));

        MvcResult options = loginOptions(LoginKind.MEMBER);
        MvcResult login = logIn(LoginKind.MEMBER, phone, options);

        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = JSON.readTree(login.getResponse().getContentAsString());
        assertThat(body.get("authenticated").asBoolean()).isTrue();
        assertThat(body.get("redirectUrl").asString()).isEqualTo("/medlem");
        assertThat(sessionCookie(login)).as("passkey login gives the session a new id")
                .isNotNull()
                .isNotEqualTo(sessionCookie(options));
        MvcResult page = mockMvc.perform(get("/medlem").with(sessionOf(login))).andReturn();
        assertThat(page.getResponse().getStatus()).isEqualTo(200);
        assertThat(page.getResponse().getContentAsString())
                .contains("Karin Karlsson")
                .doesNotContain(OFFER);
        assertThat(jdbc.sql("SELECT name FROM user_entities").query(String.class).single())
                .isEqualTo(LoginKind.MEMBER.principalName(account));
    }

    @Test
    void anAdministratorAddsAPasskeyAndLogsInWithIt() throws Exception {
        ClientPlatform laptop = authenticator();
        register(LoginKind.ADMINISTRATOR, laptop, sessionOf(logInByLink(LoginKind.ADMINISTRATOR,
                firstAdministratorEmail)));

        MvcResult login = logIn(LoginKind.ADMINISTRATOR, laptop, loginOptions(LoginKind.ADMINISTRATOR));

        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        assertThat(JSON.readTree(login.getResponse().getContentAsString()).get("redirectUrl").asString())
                .isEqualTo("/admin");
        assertThat(mockMvc.perform(get("/admin").with(sessionOf(login))).andReturn().getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    void aMembersPasskeyDoesNotLogInOnTheAdministratorPage() throws Exception {
        long account = insertAccount("Karin Karlsson", KARIN);
        // An administrator with the account's id, so that only the kind in the
        // passkey owner's name tells the two apart.
        jdbc.sql("INSERT INTO administrator (id, email, full_name, created_at) OVERRIDING SYSTEM VALUE"
                        + " VALUES (?, ?, ?, now()) ON CONFLICT (id) DO NOTHING")
                .param(account)
                .param(BO)
                .param("Bo Berg")
                .update();
        ClientPlatform phone = authenticator();
        register(LoginKind.MEMBER, phone, sessionOf(logInByLink(LoginKind.MEMBER, KARIN)));

        MvcResult options = loginOptions(LoginKind.ADMINISTRATOR);
        MvcResult login = logIn(LoginKind.ADMINISTRATOR, phone, options);

        assertThat(login.getResponse().getStatus()).isEqualTo(401);
        assertRedirect(mockMvc.perform(get("/admin").with(sessionOf(options))).andReturn(), "/admin/logga-in");
    }

    @Test
    void thePasswordManagerSeesTheAddressAndName() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        RequestPostProcessor member = sessionOf(logInByLink(LoginKind.MEMBER, KARIN));
        RequestPostProcessor administrator = sessionOf(logInByLink(LoginKind.ADMINISTRATOR, firstAdministratorEmail));

        JsonNode memberUser = registerOptions(LoginKind.MEMBER, member).get("user");
        JsonNode administratorUser = registerOptions(LoginKind.ADMINISTRATOR, administrator).get("user");

        assertThat(memberUser.get("name").asString()).isEqualTo(KARIN);
        assertThat(memberUser.get("displayName").asString()).isEqualTo("Karin Karlsson");
        assertThat(administratorUser.get("name").asString()).isEqualTo(firstAdministratorEmail + " (administratör)");
    }

    @Test
    void nobodyAddsAPasskeyWithoutTheRightLogin() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        RequestPostProcessor member = sessionOf(logInByLink(LoginKind.MEMBER, KARIN));

        MvcResult anonymous = mockMvc.perform(post(PasskeyUrls.of(LoginKind.MEMBER).registerOptions())
                .with(csrf())).andReturn();
        MvcResult memberOnAdministratorPath = mockMvc.perform(
                post(PasskeyUrls.of(LoginKind.ADMINISTRATOR).registerOptions()).with(member).with(csrf())).andReturn();

        assertRedirect(anonymous, "/logga-in");
        assertRedirect(memberOnAdministratorPath, "/admin/logga-in");
        assertThat(rowsIn("user_entities")).as("no owner row stored for a refused request").isZero();
    }

    /// The label column holds 1000 characters as PostgreSQL counts them. 🎭 is
    /// one character there and two UTF-16 units in Java, so a check on
    /// `String.length()` would refuse the 1000 that fit.
    @Test
    void aLabelLongerThanTheColumnIsRefused() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        RequestPostProcessor karin = sessionOf(logInByLink(LoginKind.MEMBER, KARIN));
        String mask = "\uD83C\uDFAD";
        String tooLong = mask.repeat(PasskeyRegistrationConverter.MAX_LABEL + 1);
        String longest = mask.repeat(PasskeyRegistrationConverter.MAX_LABEL);

        int refused = sendRegistration(LoginKind.MEMBER, authenticator(), karin, tooLong).getResponse().getStatus();
        long storedAfterRefusal = rowsIn("user_credentials");
        int stored = sendRegistration(LoginKind.MEMBER, authenticator(), karin, longest).getResponse().getStatus();

        assertThat(refused).isEqualTo(400);
        assertThat(storedAfterRefusal).isZero();
        assertThat(stored).isEqualTo(200);
        assertThat(jdbc.sql("SELECT label FROM user_credentials").query(String.class).single()).isEqualTo(longest);
    }

    @Test
    void theApiListsAndRemovesOnlyOnesOwnPasskeys() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        insertAccount("Bo Berg", BO);
        RequestPostProcessor karin = sessionOf(logInByLink(LoginKind.MEMBER, KARIN));
        RequestPostProcessor bo = sessionOf(logInByLink(LoginKind.MEMBER, BO));
        register(LoginKind.MEMBER, authenticator(), karin);
        register(LoginKind.MEMBER, authenticator(), bo);

        JsonNode list = JSON.readTree(mockMvc.perform(get("/api/member/passkeys").with(karin))
                .andReturn().getResponse().getContentAsString());
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).get("label").asString()).isEqualTo("JUnit på Testcontainers");
        String karinsPasskey = list.get(0).get("id").asString();

        int boRemovingKarins = mockMvc.perform(delete("/api/member/passkeys/" + karinsPasskey).with(bo).with(csrf()))
                .andReturn().getResponse().getStatus();
        int karinRemoving = mockMvc.perform(delete("/api/member/passkeys/" + karinsPasskey).with(karin).with(csrf()))
                .andReturn().getResponse().getStatus();
        int karinRemovingAgain = mockMvc.perform(
                delete("/api/member/passkeys/" + karinsPasskey).with(karin).with(csrf()))
                .andReturn().getResponse().getStatus();

        assertThat(boRemovingKarins).as("someone else's passkey").isEqualTo(404);
        assertThat(karinRemoving).isEqualTo(204);
        assertThat(karinRemovingAgain).isEqualTo(404);
        assertThat(rowsIn("user_credentials")).as("Bo's passkey is left").isEqualTo(1);
    }

    @Test
    void thePageRemovesAPasskeyAndItNoLongerLogsIn() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        RequestPostProcessor karin = sessionOf(logInByLink(LoginKind.MEMBER, KARIN));
        ClientPlatform phone = authenticator();
        register(LoginKind.MEMBER, phone, karin);
        String id = jdbc.sql("SELECT credential_id FROM user_credentials").query(String.class).single();

        MvcResult removed = mockMvc.perform(post(PasskeyUrls.of(LoginKind.MEMBER).remove(id))
                .with(karin).with(csrf())).andReturn();

        assertRedirect(removed, "/medlem");
        assertThat(rowsIn("user_credentials")).isZero();
        assertThat(logIn(LoginKind.MEMBER, phone, loginOptions(LoginKind.MEMBER)).getResponse().getStatus())
                .isEqualTo(401);
    }

    @Test
    void removingAnAdministratorRemovesTheirPasskeys() throws Exception {
        long bo = insertAdministrator(BO, "Bo Berg");
        insertAdministrator("cilla@example.test", "Cilla Carlsson");
        ClientPlatform laptop = authenticator();
        register(LoginKind.ADMINISTRATOR, laptop, sessionOf(logInByLink(LoginKind.ADMINISTRATOR, BO)));
        RequestPostProcessor first = sessionOf(logInByLink(LoginKind.ADMINISTRATOR, firstAdministratorEmail));

        int removed = mockMvc.perform(delete("/api/admin/administrators/" + bo).with(first).with(csrf()))
                .andReturn().getResponse().getStatus();

        assertThat(removed).isEqualTo(204);
        assertThat(rowsIn("user_credentials")).isZero();
        assertThat(rowsIn("user_entities")).isZero();
        assertThat(logIn(LoginKind.ADMINISTRATOR, laptop, loginOptions(LoginKind.ADMINISTRATOR))
                .getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void thePageOffersAPasskeyOnceAfterALinkLogin() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        RequestPostProcessor karin = sessionOf(logInByLink(LoginKind.MEMBER, KARIN));

        String first = mockMvc.perform(get("/medlem").with(karin)).andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(get("/medlem").with(karin)).andReturn().getResponse().getContentAsString();

        assertThat(first).contains(OFFER);
        assertThat(second).doesNotContain(OFFER);
    }

    /// The browser keeps the cookie and sends it back only under its path;
    /// MockMvc does neither, so the test checks the attributes and sends the
    /// cookie itself.
    @Test
    void fragaInteIgenStopsTheOfferOnThatBrowserOnly() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        MvcResult declined = mockMvc.perform(post(PasskeyUrls.of(LoginKind.MEMBER).decline())
                .with(sessionOf(logInByLink(LoginKind.MEMBER, KARIN))).with(csrf())).andReturn();
        assertRedirect(declined, "/medlem");
        String setCookie = declined.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).startsWith("p=1;").contains("Path=/medlem;", "Max-Age=34560000;", "HttpOnly",
                "SameSite=Lax");
        Cookie cookie = new Cookie("p", "1");

        String sameBrowser = mockMvc.perform(get("/medlem")
                .with(sessionOf(logInByLink(LoginKind.MEMBER, KARIN))).cookie(cookie))
                .andReturn().getResponse().getContentAsString();
        String otherBrowser = mockMvc.perform(get("/medlem")
                .with(sessionOf(logInByLink(LoginKind.MEMBER, KARIN))))
                .andReturn().getResponse().getContentAsString();

        assertThat(sameBrowser).doesNotContain(OFFER);
        assertThat(otherBrowser).contains(OFFER);
    }

    /// A software authenticator for one person's device, answering for the
    /// configured site address.
    private ClientPlatform authenticator() {
        return new ClientPlatform(new Origin(siteUrl), new WebAuthnAuthenticatorAdaptor(new PackedAuthenticator()));
    }

    private JsonNode registerOptions(LoginKind kind, RequestPostProcessor session) throws Exception {
        MvcResult options = mockMvc.perform(post(PasskeyUrls.of(kind).registerOptions()).with(session).with(csrf()))
                .andReturn();
        assertThat(options.getResponse().getStatus()).as("registration options").isEqualTo(200);
        return JSON.readTree(options.getResponse().getContentAsString());
    }

    /// Adds a passkey the way `/js/passkey.js` does, and fails unless the server
    /// stores it.
    private void register(LoginKind kind, ClientPlatform device, RequestPostProcessor session) throws Exception {
        MvcResult stored = sendRegistration(kind, device, session, "JUnit på Testcontainers");
        assertThat(stored.getResponse().getStatus()).as("registration: %s", stored.getResponse().getContentAsString())
                .isEqualTo(200);
    }

    /// Asks for a challenge, has `device` sign it, and sends the result with
    /// `label` in the body `/js/passkey.js` sends.
    private MvcResult sendRegistration(LoginKind kind, ClientPlatform device, RequestPostProcessor session,
            String label) throws Exception {
        JsonNode options = registerOptions(kind, session);
        JsonNode rp = options.get("rp");
        JsonNode user = options.get("user");
        PublicKeyCredential<AuthenticatorAttestationResponse, ?> credential = device.create(
                new PublicKeyCredentialCreationOptions(
                        new PublicKeyCredentialRpEntity(rp.get("id").asString(), rp.get("name").asString()),
                        new PublicKeyCredentialUserEntity(DECODE.decode(user.get("id").asString()),
                                user.get("name").asString(), user.get("displayName").asString()),
                        new DefaultChallenge(DECODE.decode(options.get("challenge").asString())),
                        List.of(new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY,
                                COSEAlgorithmIdentifier.ES256)),
                        null,
                        List.of(),
                        new AuthenticatorSelectionCriteria(null, ResidentKeyRequirement.REQUIRED,
                                UserVerificationRequirement.PREFERRED),
                        null,
                        null));
        AuthenticatorAttestationResponse response = requireNonNull(credential.getResponse());
        Map<String, Object> body = Map.of("publicKey", Map.of(
                "label", label,
                "credential", Map.of(
                        "id", credential.getId(),
                        "rawId", ENCODE.encodeToString(credential.getRawId()),
                        "response", Map.of(
                                "attestationObject", ENCODE.encodeToString(response.getAttestationObject()),
                                "clientDataJSON", ENCODE.encodeToString(response.getClientDataJSON()),
                                "transports", List.of()),
                        "type", credential.getType(),
                        "clientExtensionResults", Map.of())));
        return mockMvc.perform(post(PasskeyUrls.of(kind).register()).with(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body))).andReturn();
    }

    /// Asks for a login challenge from a browser with no session yet, as on
    /// opening the login page.
    private MvcResult loginOptions(LoginKind kind) throws Exception {
        MvcResult options = mockMvc.perform(post(PasskeyUrls.of(kind).loginOptions()).with(csrf())).andReturn();
        assertThat(options.getResponse().getStatus()).as("login options").isEqualTo(200);
        return options;
    }

    /// Signs the challenge in `options` the way `/js/passkey.js` does, in the
    /// session that asked for it.
    private MvcResult logIn(LoginKind kind, ClientPlatform device, MvcResult options) throws Exception {
        JsonNode json = JSON.readTree(options.getResponse().getContentAsString());
        PublicKeyCredential<AuthenticatorAssertionResponse, ?> credential = device.get(
                new PublicKeyCredentialRequestOptions(
                        new DefaultChallenge(DECODE.decode(json.get("challenge").asString())),
                        null,
                        json.get("rpId").asString(),
                        List.of(),
                        UserVerificationRequirement.PREFERRED,
                        null));
        AuthenticatorAssertionResponse response = requireNonNull(credential.getResponse());
        Map<String, Object> signed = new LinkedHashMap<>();
        signed.put("authenticatorData", ENCODE.encodeToString(response.getAuthenticatorData()));
        signed.put("clientDataJSON", ENCODE.encodeToString(response.getClientDataJSON()));
        signed.put("signature", ENCODE.encodeToString(response.getSignature()));
        signed.put("userHandle", ENCODE.encodeToString(response.getUserHandle()));
        Map<String, Object> body = Map.of(
                "id", credential.getId(),
                "rawId", ENCODE.encodeToString(credential.getRawId()),
                "response", signed,
                "type", credential.getType(),
                "clientExtensionResults", Map.of());
        return mockMvc.perform(post(PasskeyUrls.of(kind).login()).with(sessionOf(options)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body))).andReturn();
    }

    private static @Nullable String sessionCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("SESSION");
        return cookie == null ? null : cookie.getValue();
    }
}
