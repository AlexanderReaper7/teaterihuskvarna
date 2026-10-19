/// The REST adapter. One HTTP endpoint per capability, each one a thin wrapper
/// around a method on an application service, holding no logic of its own.
///
/// Every service method a page in `se.teaterihuskvarna.web` calls has an endpoint
/// here that calls the same method, and `AdapterRulesTest` fails the build the
/// moment one does not. Records from the services go to Jackson as they are, and
/// [ProblemResponses] turns the services' exceptions into statuses. Login has no
/// endpoint of its own: an API client posts to the same Spring Security URLs the
/// login pages do, after fetching a CSRF token from `/api/csrf`. See
/// `docs/decisions/0014-one-service-layer-two-adapters.md`.
@NullMarked
package se.teaterihuskvarna.api;

import org.jspecify.annotations.NullMarked;
