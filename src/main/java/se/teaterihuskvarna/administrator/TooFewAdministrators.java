package se.teaterihuskvarna.administrator;

import java.io.Serial;

/// A removal refused because two or fewer administrators remain. The plan
/// requires at least two board members with administrator access, and this is
/// where the application enforces it. The adapters answer 409.
public class TooFewAdministrators extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    TooFewAdministrators() {
        super("removal refused: two or fewer administrators remain");
    }
}
