package se.teaterihuskvarna;

import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import se.teaterihuskvarna.login.AccountId;
import se.teaterihuskvarna.login.AdministratorId;
import se.teaterihuskvarna.member.MemberId;

/// How a request's text becomes an id: an empty value is no id, anything
/// else is the number.
///
/// A form posts the choice "nobody" as an empty value. The owner of a
/// household is picked from a list whose first option is the administrators,
/// with the value `""`. Spring reads an empty value as null for a `Long`,
/// and for any other type it calls `valueOf`, which rejects it, so the first
/// version of the id types answered that choice with an error.
///
/// `IdConversionIT` finds every id type in the code and fails when one of
/// them is missing here.
@Configuration(proxyBeanMethods = false)
class IdConversion implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, MemberId.class, emptyIsNoId(MemberId::valueOf));
        registry.addConverter(String.class, AccountId.class, emptyIsNoId(AccountId::valueOf));
        registry.addConverter(String.class, AdministratorId.class, emptyIsNoId(AdministratorId::valueOf));
    }

    private static <T> Converter<String, @Nullable T> emptyIsNoId(Function<String, T> valueOf) {
        return text -> text.isBlank() ? null : valueOf.apply(text.strip());
    }
}
