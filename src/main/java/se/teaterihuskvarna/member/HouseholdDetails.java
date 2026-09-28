package se.teaterihuskvarna.member;

import java.util.List;

/// A household and who is in it now.
///
/// @param id      the household's identifier
/// @param name    what the household is called
/// @param members its members by name
public record HouseholdDetails(Long id, String name, List<HouseholdMember> members) {

    /// @param id      the household's identifier
    /// @param name    what the household is called
    /// @param members its members, copied
    public HouseholdDetails {
        members = List.copyOf(members);
    }
}
