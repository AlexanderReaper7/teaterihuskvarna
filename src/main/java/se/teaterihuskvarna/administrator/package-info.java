/// Administrator accounts: who can log in on the administrator login page, and
/// how administrators add and remove each other.
///
/// Separate from the member package on purpose. An administrator account is not
/// an account in the member sense; an administrator need not be a member, and
/// one address may belong to both. See the glossary, "Administrator".
///
/// The first administrator comes from configuration on a fresh database
/// ([FirstAdministrator]). After that, [AdministratorService] refuses any
/// removal that would leave fewer than two: `docs/projektplan.md`.
package se.teaterihuskvarna.administrator;
