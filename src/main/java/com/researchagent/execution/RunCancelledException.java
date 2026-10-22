package com.researchagent.execution;

/** Cooperative cancellation, distinct from a provider or infrastructure failure. */
public class RunCancelledException extends RuntimeException {
    public RunCancelledException() { super("Research run cancelled"); }
}
