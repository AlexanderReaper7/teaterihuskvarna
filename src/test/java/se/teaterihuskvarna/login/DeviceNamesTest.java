package se.teaterihuskvarna.login;


import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DeviceNamesTest {

    private final ResourceBundleMessageSource messages = createMessages();
    private final DeviceNames deviceNames = new DeviceNames(messages);

    private static ResourceBundleMessageSource createMessages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }

    @Test
    void firefoxOnWindows() {
        assertEquals("Firefox på Windows",
                deviceNames.of("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0)"
                        + " Gecko/20100101 Firefox/131.0"));
    }

    @Test
    void edgeOnWindows() {
        assertEquals("Edge på Windows",
                deviceNames.of("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
                        + " Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0"));
    }

    @Test
    void chromeOnAndroid() {
        assertEquals("Chrome på Android",
                deviceNames.of("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko)"
                        + " Chrome/130.0.0.0 Mobile Safari/537.36"));
    }

    @Test
    void safariOnIos() {
        assertEquals("Safari på iOS",
                deviceNames.of("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15"
                        + " (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"));
    }

    @Test
    void curlIsUnknown() {
        assertEquals("okänd på okänd", deviceNames.of("curl/8.10.1"));
    }

    @Test
    void nullHeaderIsUnknown() {
        assertEquals("okänd på okänd", deviceNames.of(null));
    }

}
