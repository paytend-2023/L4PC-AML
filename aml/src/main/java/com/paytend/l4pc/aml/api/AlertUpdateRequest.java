package com.paytend.l4pc.aml.api;

public record AlertUpdateRequest(
        String status,
        String assigned_to
) {
}
