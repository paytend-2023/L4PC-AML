package com.paytend.l4pc.aml.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytend.l4pc.aml.api.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableConfigurationProperties(GoogleSsoProperties.class)
public class SecurityConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.security.google-sso", name = "enabled", havingValue = "true")
    SecurityFilterChain googleSsoSecurityFilterChain(HttpSecurity http,
                                                     ObjectMapper objectMapper,
                                                     GoogleSsoProperties properties,
                                                     OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/pc/auth/login", "/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers("/pc/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
                        .successHandler((request, response, authentication) ->
                                response.sendRedirect(buildPortalRedirect(properties,
                                        AuthenticationRedirectSupport.consumeRedirectPath(request.getSession(false)))))
                        .failureHandler((request, response, exception) ->
                                response.sendRedirect(buildLoginRedirect(properties,
                                        AuthenticationRedirectSupport.consumeRedirectPath(request.getSession(false)),
                                        resolveFailureCode(exception)))))
                .logout(logout -> logout
                        .logoutUrl("/pc/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .defaultAuthenticationEntryPointFor(
                                (request, response, exception) ->
                                        writeErrorResponse(response, objectMapper, HttpServletResponse.SC_UNAUTHORIZED,
                                                "AUTHENTICATION_REQUIRED", "authentication is required"),
                                new AntPathRequestMatcher("/pc/**"))
                        .defaultAccessDeniedHandlerFor(
                                (request, response, exception) ->
                                        writeErrorResponse(response, objectMapper, HttpServletResponse.SC_FORBIDDEN,
                                                "ACCESS_DENIED", "access denied"),
                                new AntPathRequestMatcher("/pc/**")));
        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.security.google-sso", name = "enabled", havingValue = "true")
    OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService(GoogleSsoProperties properties) {
        return new PaytendDomainRestrictedOidcUserService(properties.getAllowedDomain());
    }

    private static void writeErrorResponse(HttpServletResponse response,
                                           ObjectMapper objectMapper,
                                           int status,
                                           String code,
                                           String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(code, message));
    }

    private static String resolveFailureCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauthException) {
            return oauthException.getError().getErrorCode();
        }
        return "oauth_failed";
    }

    private static String buildPortalRedirect(GoogleSsoProperties properties, String redirectPath) {
        String baseUri = trimTrailingSlash(properties.getPortalBaseUri());
        String path = AuthenticationRedirectSupport.normalizeRedirectPath(redirectPath, properties.getDefaultSuccessPath());
        return baseUri + path;
    }

    private static String buildLoginRedirect(GoogleSsoProperties properties, String redirectPath, String errorCode) {
        String baseUri = trimTrailingSlash(properties.getPortalBaseUri());
        String nextPath = AuthenticationRedirectSupport.normalizeRedirectPath(redirectPath, properties.getDefaultSuccessPath());
        return baseUri + "/login?error=" + urlEncode(errorCode) + "&next=" + urlEncode(nextPath);
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
