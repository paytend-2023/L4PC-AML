package com.paytend.l4pc.aml.config;

import jakarta.servlet.http.HttpSession;
import org.springframework.util.StringUtils;

public final class AuthenticationRedirectSupport {

    public static final String SESSION_REDIRECT_PATH = "AUTH_REDIRECT_PATH";

    private AuthenticationRedirectSupport() {
    }

    public static void rememberRedirectPath(HttpSession session, String redirectPath, String defaultPath) {
        session.setAttribute(SESSION_REDIRECT_PATH, normalizeRedirectPath(redirectPath, defaultPath));
    }

    public static String consumeRedirectPath(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object redirectPath = session.getAttribute(SESSION_REDIRECT_PATH);
        session.removeAttribute(SESSION_REDIRECT_PATH);
        return redirectPath instanceof String value ? value : null;
    }

    public static String normalizeRedirectPath(String redirectPath, String defaultPath) {
        if (!StringUtils.hasText(redirectPath)) {
            return defaultPath;
        }
        String candidate = redirectPath.trim();
        if (!candidate.startsWith("/") || candidate.startsWith("//")) {
            return defaultPath;
        }
        return candidate;
    }
}
