/// The REST adapter. One HTTP endpoint per capability, each one a thin wrapper
/// around a method on an application service, holding no logic of its own.
///
/// Empty so far, and that is the honest state: no capability exists yet for it
/// to expose. `AdapterRulesTest` fails the moment a JTE page calls a service
/// method that has no endpoint here, which is what stops this package being
/// quietly left behind. See
/// `docs/decisions/0014-one-service-layer-two-adapters.md`.
package se.teaterihuskvarna.api;
