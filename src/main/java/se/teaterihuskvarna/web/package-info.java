/// The JTE adapter: controllers that answer with a page or a redirect to one.
///
/// Calls the application services in process, as ordinary method calls, rather
/// than over HTTP through `se.teaterihuskvarna.api`. The two adapters are peers
/// over one service layer, so neither may depend on the other:
/// `docs/decisions/0014-one-service-layer-two-adapters.md`.
///
/// A handler answers with [HtmlPage], built from the method the build
/// generates for the page's template on
/// `gg.jte.generated.precompiled.Templates`, or with [Redirect]. What the
/// page shows is the method's arguments, so the compiler checks that the
/// page exists, that nothing is left out and that each value has the type
/// the template declares. No handler here returns a view's name or fills a
/// model, and `PageRulesTest` fails the build on one that does.
///
/// Templates read their Swedish through [Copy]. A handler declares what
/// comes from the request as a parameter, which [PageArguments] fills in:
/// the [CsrfField] of a page with a form, and the [Feedback] of a page that
/// says how the request before it went.
@NullMarked
package se.teaterihuskvarna.web;

import org.jspecify.annotations.NullMarked;
