package se.teaterihuskvarna.mailing;

import java.io.Serial;

/// A segment chosen for a mailing holds contacts that are not on the members'
/// list, so the mailing would reach people who are not members. The adapters
/// answer 409.
public class AudienceHasNonMembers extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /// How many contacts in the segment are not members.
    private final long nonMembers;

    AudienceHasNonMembers(long nonMembers) {
        super(nonMembers + " contacts in the segment are not on the members' list");
        this.nonMembers = nonMembers;
    }

    /// @return how many contacts in the segment are not members
    public long nonMembers() {
        return nonMembers;
    }
}
