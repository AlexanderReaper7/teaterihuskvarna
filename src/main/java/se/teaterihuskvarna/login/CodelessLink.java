package se.teaterihuskvarna.login;

import java.time.Instant;

/// A login link without a code, from [LoginLinks#createWithoutCode].
///
/// @param token   the token, for the link's `?token=`
/// @param expires when the link stops working, and a mail carrying it is worth nothing
public record CodelessLink(String token, Instant expires) {
}
