package se.teaterihuskvarna.offer;

import java.io.Serial;

/// Every place is taken. The adapters answer 409.
public class OfferFull extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    OfferFull() {
        super("the offer has no places left");
    }
}
