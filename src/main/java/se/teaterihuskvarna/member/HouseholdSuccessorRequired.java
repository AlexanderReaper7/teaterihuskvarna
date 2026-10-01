package se.teaterihuskvarna.member;

import java.io.Serial;

/// An owner leaving other account holders must choose a successor.
public class HouseholdSuccessorRequired extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    HouseholdSuccessorRequired() {
        super("choose another household member with an account before leaving");
    }
}
