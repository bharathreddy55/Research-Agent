package com.researchagent.llm;

/** Provider failures must remain failures rather than becoming synthetic research. */
public class LlmException extends RuntimeException {
    public enum Code { CONFIGURATION, INITIALIZATION, GENERATION, INVALID_RESPONSE }

    private final Code code;
    private final String provider;

    public LlmException(Code code, String provider, String message) {
        this(code, provider, message, null);
    }

    public LlmException(Code code, String provider, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.provider = provider;
    }

    public Code getCode() { return code; }
    public String getProvider() { return provider; }
}
