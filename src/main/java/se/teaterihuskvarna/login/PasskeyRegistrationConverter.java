package se.teaterihuskvarna.login;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.web.webauthn.jackson.WebauthnJacksonModule;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// Reads a new passkey for Spring's registration filter, as the filter's own
/// converter does, and refuses a label longer than the column holds. The filter
/// answers 400 to any body its converter cannot read, but checks nothing about
/// the label, so a longer one reached the insert and failed there with a 500.
///
/// The label comes from `/js/passkey.js`, built from the browser's user agent,
/// so only a client that writes its own request can send one this long.
final class PasskeyRegistrationConverter implements HttpMessageConverter<Object> {

    /// `label varchar(1000)` in `V4__passkeys.sql`. PostgreSQL counts
    /// characters, so the check counts code points rather than Java's UTF-16
    /// units, which count an emoji twice.
    static final int MAX_LABEL = 1000;

    private final JsonMapper json = JsonMapper.builder().addModule(new WebauthnJacksonModule()).build();
    private final HttpMessageConverter<Object> spring = new JacksonJsonHttpMessageConverter(json);

    @Override
    public boolean canRead(Class<?> type, @Nullable MediaType mediaType) {
        return spring.canRead(type, mediaType);
    }

    @Override
    public boolean canWrite(Class<?> type, @Nullable MediaType mediaType) {
        return spring.canWrite(type, mediaType);
    }

    @Override
    public List<MediaType> getSupportedMediaTypes() {
        return spring.getSupportedMediaTypes();
    }

    /// @param type  the type Spring's filter reads the body as
    /// @param input the request body and headers
    /// @return what Spring's own converter reads from the same body
    /// @throws IOException                    if the body cannot be read
    /// @throws HttpMessageNotReadableException if the label is too long
    @Override
    public Object read(Class<?> type, HttpInputMessage input) throws IOException {
        byte[] body = input.getBody().readAllBytes();
        JsonNode node = json.readTree(body).path("publicKey").path("label");
        String label = node.isString() ? node.asString() : "";
        if (label.codePointCount(0, label.length()) > MAX_LABEL) {
            throw new HttpMessageNotReadableException("A passkey label is over " + MAX_LABEL + " characters", input);
        }
        return spring.read(type, new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(body);
            }

            @Override
            public HttpHeaders getHeaders() {
                return input.getHeaders();
            }
        });
    }

    @Override
    public void write(Object value, @Nullable MediaType contentType, HttpOutputMessage output) throws IOException {
        spring.write(value, contentType, output);
    }
}
