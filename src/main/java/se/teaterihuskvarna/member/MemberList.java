package se.teaterihuskvarna.member;

import java.util.List;

/// One page of a register search: the first [MemberService#SEARCH_LIMIT]
/// matches by name, and whether there were more.
///
/// @param members the matches, by name
/// @param more    true when more members matched than the list holds
public record MemberList(List<MemberDetails> members, boolean more) {

    /// @param members the matches, copied
    /// @param more    true when more members matched than the list holds
    public MemberList {
        members = List.copyOf(members);
    }
}
