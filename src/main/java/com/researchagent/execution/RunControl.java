package com.researchagent.execution;

import java.util.function.BooleanSupplier;

/** Operation-boundary checks for the synchronous pipeline. Nested scopes combine
 * the worker's persisted cancellation signal with the orchestrator's local signal. */
public final class RunControl {
    private static final ThreadLocal<BooleanSupplier> CANCELLATION = new ThreadLocal<>();
    private RunControl() {}

    public static Scope open(BooleanSupplier cancellation) {
        BooleanSupplier previous = CANCELLATION.get();
        CANCELLATION.set(() -> cancellation.getAsBoolean() || (previous != null && previous.getAsBoolean()));
        return () -> { if (previous == null) CANCELLATION.remove(); else CANCELLATION.set(previous); };
    }

    public static void checkActive() {
        BooleanSupplier cancellation = CANCELLATION.get();
        if (cancellation != null && cancellation.getAsBoolean()) throw new RunCancelledException();
    }

    public interface Scope extends AutoCloseable {
        @Override void close();
    }
}
