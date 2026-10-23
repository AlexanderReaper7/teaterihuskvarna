/// The JTE adapter: controllers that put values in a model and name a template.
///
/// Calls the application services in process, as ordinary method calls, rather
/// than over HTTP through `se.teaterihuskvarna.api`. The two adapters are peers
/// over one service layer, so neither may depend on the other:
/// `docs/decisions/0014-one-service-layer-two-adapters.md`.
///
/// Templates read their Swedish through [Copy], which [PageModel] puts in every
/// page's model, and forms get their CSRF field from [FormModel].
@NullMarked
package se.teaterihuskvarna.web;

import org.jspecify.annotations.NullMarked;
