package se.teaterihuskvarna.development;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/// Refuses to start the `dev` profile on a real site.
///
/// Under the profile `/dev/login` logs anyone in as any administrator, `/dev`
/// lists every address, and `DevSeed` fills an empty register with invented
/// members. `@Profile("dev")` is all that keeps those classes out of
/// production, and one line in `.env` turns the profile on: `COMPOSE_FILE`
/// naming `compose.dev.yaml`, which `.env.example` sets.
///
/// `SITE_URL` is what tells the two apart. No development stack sets it:
/// `application-dev.yaml` builds the address from `PROXY_HTTP_PORT`, and
/// `compose.share.yaml` passes a shared stack's public address as
/// `TEATERIHUSKVARNA_MAIL_SITE_URL`, from `SHARE_URL`. Production has to set
/// it, or `MailSettings` refuses to start. So the profile together with a
/// `SITE_URL` is a production `.env` that still loads `compose.dev.yaml`.
///
/// The check reads the `SITE_URL` variable itself and not
/// `teaterihuskvarna.mail.site-url`, which the profile always sets. It runs
/// while Spring creates its beans, so the application stops before it answers
/// a request and before `DevSeed` runs.
@Component
@Profile("dev")
final class RealSiteGuard {

    /// @param siteUrl the `SITE_URL` variable, empty when it is unset or set to nothing
    /// @throws IllegalStateException if `SITE_URL` has a value
    RealSiteGuard(@Value("${SITE_URL:}") String siteUrl) {
        if (!siteUrl.isBlank()) {
            throw new IllegalStateException("The dev profile is active and SITE_URL is set, to " + siteUrl.strip()
                    + ". SITE_URL names a real site, and under the dev profile /dev/login logs anyone in as any"
                    + " administrator, so the application refuses to start. On the production host, remove the"
                    + " lines COMPOSE_FILE, COMPOSE_PATH_SEPARATOR, PROXY_HTTP_PORT and POSTGRES_HOST_PORT from"
                    + " .env, which load compose.dev.yaml, and anything else that sets SPRING_PROFILES_ACTIVE=dev:"
                    + " .env.production.example has the lines production needs. In local development, leave"
                    + " SITE_URL empty.");
        }
    }
}
