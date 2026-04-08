package com.paytend.l4pc.aml.web;

import com.paytend.l4pc.aml.api.AuthSessionResponse;
import com.paytend.l4pc.aml.config.AuthenticationRedirectSupport;
import com.paytend.l4pc.aml.config.GoogleSsoProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
@RequestMapping("/pc/auth")
public class AuthController {

    private final GoogleSsoProperties properties;

    public AuthController(GoogleSsoProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/login")
    @ResponseStatus(HttpStatus.FOUND)
    public void login(@RequestParam(name = "redirect", required = false) String redirect,
                      HttpServletRequest request,
                      HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(true);
        AuthenticationRedirectSupport.rememberRedirectPath(session, redirect, properties.getDefaultSuccessPath());
        response.sendRedirect("/oauth2/authorization/google");
    }

    @GetMapping("/session")
    public AuthSessionResponse session(@AuthenticationPrincipal OidcUser user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication is required");
        }
        return new AuthSessionResponse(true, new AuthSessionResponse.AuthenticatedUser(
                user.getEmail(),
                user.getClaimAsString("name"),
                user.getClaimAsString("picture")));
    }
}
