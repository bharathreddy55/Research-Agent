package com.researchagent.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;

/** Run from a terminal; passwords are never accepted as command arguments. */
public final class PasswordHashTool {
    public static void main(String[] args) {
        var console = System.console();
        if (console == null) throw new IllegalStateException("Run this tool in an interactive terminal");
        char[] password = console.readPassword("Password (at least 12 characters): ");
        if (password == null) return;
        try {
            if (password.length < 12 || new String(password).getBytes(StandardCharsets.UTF_8).length > 72) {
                throw new IllegalArgumentException("Use at least 12 characters and at most 72 UTF-8 bytes");
            }
            System.out.println(new BCryptPasswordEncoder(12).encode(new String(password)));
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
