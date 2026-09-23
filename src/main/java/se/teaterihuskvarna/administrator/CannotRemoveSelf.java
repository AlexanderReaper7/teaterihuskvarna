package se.teaterihuskvarna.administrator;

import java.io.Serial;

/// A removal refused because the administrator asked to remove themselves.
/// Administrators remove other administrators, never themselves: `GLOSSARY.md`,
/// "Administrator". The adapters answer 409.
public class CannotRemoveSelf extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    CannotRemoveSelf() {
        super("removal refused: an administrator cannot remove themselves");
    }
}
