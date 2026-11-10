package se.teaterihuskvarna.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.WebUtils;
import se.teaterihuskvarna.content.Perspective;
import se.teaterihuskvarna.content.Previews;

/// Lets a handler declare a [Feedback], a [CsrfField] or a [Perspective] as
/// a parameter: what a page needs that comes from the request and not from a
/// service.
///
/// A page whose template declares `@param CsrfField csrf` cannot be shown
/// without one, and a parameter is the only way a handler gets one. So the
/// token is read by the handlers that can show a form and by no other. A
/// list of controllers kept by hand, `FormModel`, used to arrange that, for
/// every handler of a controller on it.
@Configuration(proxyBeanMethods = false)
class PageArguments implements WebMvcConfigurer {

    private final Previews previews;

    PageArguments(Previews previews) {
        this.previews = previews;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new FromRequest<>(Feedback.class, Feedback::of));
        resolvers.add(new FromRequest<>(CsrfField.class, CsrfField::of));
        resolvers.add(new FromRequest<>(Perspective.class, this::perspective));
    }

    /// Drafts for an editor in the Studio's preview, who carries its cookie,
    /// and published for everyone else.
    private Perspective perspective(HttpServletRequest request) {
        Cookie pass = WebUtils.getCookie(request, Previews.COOKIE);
        return previews.perspective(pass == null ? null : pass.getValue());
    }

    private record FromRequest<T>(Class<T> type, Function<HttpServletRequest, T> read)
            implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType() == type;
        }

        @Override
        public T resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer container,
                NativeWebRequest request, @Nullable WebDataBinderFactory binders) {
            return read.apply(Objects.requireNonNull(request.getNativeRequest(HttpServletRequest.class)));
        }
    }
}
