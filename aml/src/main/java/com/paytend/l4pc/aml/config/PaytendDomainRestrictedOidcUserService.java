package com.paytend.l4pc.aml.config;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.util.StringUtils;

import java.util.Locale;

public class PaytendDomainRestrictedOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final String allowedDomain;
    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;

    public PaytendDomainRestrictedOidcUserService(String allowedDomain) {
        this(allowedDomain, new OidcUserService());
    }

    public PaytendDomainRestrictedOidcUserService(String allowedDomain,
                                                  OAuth2UserService<OidcUserRequest, OidcUser> delegate) {
        this.allowedDomain = normalizeDomain(allowedDomain);
        this.delegate = delegate;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser user = delegate.loadUser(userRequest);
        String email = normalizeEmail(user.getEmail());
        if (!StringUtils.hasText(email)) {
            throw authenticationException("oauth_email_required", "google account email is required");
        }
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw authenticationException("oauth_email_unverified", "google account email must be verified");
        }
        String actualDomain = email.substring(email.lastIndexOf('@') + 1);
        if (!allowedDomain.equals(actualDomain)) {
            throw authenticationException("unauthorized_domain",
                    "only @" + allowedDomain + " accounts are allowed");
        }
        return user;
    }

    private static OAuth2AuthenticationException authenticationException(String code, String message) {
        return new OAuth2AuthenticationException(new OAuth2Error(code), message);
    }

    private static String normalizeDomain(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
