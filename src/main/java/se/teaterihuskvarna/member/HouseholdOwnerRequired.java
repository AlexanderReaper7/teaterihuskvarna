package se.teaterihuskvarna.member;

import java.io.Serial;

/// Only the household owner can edit it. Other members can leave.
public class HouseholdOwnerRequired extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    HouseholdOwnerRequired() {
        super("only the household owner can edit it");
    }
}
