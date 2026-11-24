package se.teaterihuskvarna;

import java.io.Serial;
import java.util.IdentityHashMap;
import java.util.Map;

/// A copy of an exception for the log: the class names of the exception, of
/// its causes and of its suppressed exceptions, each with its stack frames,
/// and none of their messages.
///
/// A message can hold personal data. A mail relay's refusal names the
/// recipient, and a database error can quote the row it failed on. A class
/// name and a stack frame hold only names from the code, and they are what
/// tells a relay that is down from a recipient it refuses, and both from a
/// fault in this application.
///
/// It is logged in the exception's place, `LOG.warn("...", WithoutMessage.of(e))`,
/// and each exception then reads `se.teaterihuskvarna.WithoutMessage: ` and the
/// original's class name, with the frames under it as usual.
public final class WithoutMessage extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private WithoutMessage(String className) {
        super(className);
    }

    /// @param failure what was thrown
    /// @return a copy without the message of `failure` or of anything it holds
    public static WithoutMessage of(Throwable failure) {
        return copy(failure, new IdentityHashMap<>());
    }

    /// An exception met twice, as in a cause that loops, is copied once.
    private static WithoutMessage copy(Throwable original, Map<Throwable, WithoutMessage> copies) {
        WithoutMessage known = copies.get(original);
        if (known != null) {
            return known;
        }
        WithoutMessage copy = new WithoutMessage(original.getClass().getName());
        copies.put(original, copy);
        copy.setStackTrace(original.getStackTrace());
        Throwable cause = original.getCause();
        if (cause != null) {
            copy.initCause(copy(cause, copies));
        }
        for (Throwable suppressed : original.getSuppressed()) {
            copy.addSuppressed(copy(suppressed, copies));
        }
        return copy;
    }
}
