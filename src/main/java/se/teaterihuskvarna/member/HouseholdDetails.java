package se.teaterihuskvarna.member;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// A household and who is in it now.
///
/// @param id      the household's identifier
/// @param name    what the household is called
/// @param ownerMemberId the member owner, or null for administrator management
/// @param members its members by name
public record HouseholdDetails(Long id, String name, @Nullable Long ownerMemberId, List<HouseholdMember> members) {

    /// @param id      the household's identifier
    /// @param name    what the household is called
    /// @param ownerMemberId the member owner, or null for administrator management
    /// @param members its members, copied
    public HouseholdDetails {
        members = List.copyOf(members);
    }

    /// @param memberId the signed-in member
    /// @return whether that member owns the household
    public boolean ownedBy(long memberId) {
        return ownerMemberId != null && ownerMemberId == memberId;
    }
}
