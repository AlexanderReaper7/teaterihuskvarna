package se.teaterihuskvarna.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/// A household name for creation or renaming (R019).
///
/// @param name what the household is called, such as "Familjen Lindqvist"
public record NewHousehold(
        @NotBlank(message = "{household.name.required}")
        @Size(max = 100, message = "{household.name.size}")
        String name) {
}
