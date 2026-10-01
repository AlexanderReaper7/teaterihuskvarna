package se.teaterihuskvarna.member;

import jakarta.validation.constraints.Positive;
import org.jspecify.annotations.Nullable;

/// @param memberId a household member with an account, or null for administrator management
public record HouseholdOwnerForm(@Nullable @Positive Long memberId) {
}
