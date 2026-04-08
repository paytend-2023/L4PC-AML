package com.paytend.l4pc.aml.api;

public record AuthSessionResponse(boolean authenticated, AuthenticatedUser user) {

    public record AuthenticatedUser(String email, String name, String picture) {
    }
}
