package com.researchagent.security;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Revoke existing stream subscriptions when the session expires or identity changes. */
public final class SessionStreams implements HttpSessionBindingListener {
    private static final String KEY = SessionStreams.class.getName();
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public static Runnable attach(HttpSession session, SseEmitter emitter) {
        try {
            synchronized (session) {
                SessionStreams streams = (SessionStreams) session.getAttribute(KEY);
                if (streams == null) {
                    streams = new SessionStreams();
                    session.setAttribute(KEY, streams);
                }
                streams.emitters.add(emitter);
                if (streams.closed) {
                    streams.emitters.remove(emitter);
                    throw new IllegalStateException("Session ended");
                }
                SessionStreams subscriptions = streams;
                return () -> subscriptions.emitters.remove(emitter);
            }
        } catch (IllegalStateException e) {
            emitter.complete();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session ended");
        }
    }

    public static void close(HttpSession session) {
        if (session != null) {
            synchronized (session) {
                session.removeAttribute(KEY);
            }
        }
    }

    @Override
    public void valueUnbound(HttpSessionBindingEvent event) {
        closed = true;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
                // A previously closed connection must not prevent revoking the others.
            }
        }
        emitters.clear();
    }
}
