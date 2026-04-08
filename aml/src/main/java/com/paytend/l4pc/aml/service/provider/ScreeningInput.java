package com.paytend.l4pc.aml.service.provider;

/**
 * Normalized screening input passed to all providers.
 */
public record ScreeningInput(
        String firstName,
        String surname,
        String fullName,
        String dateOfBirth,
        String nationality,
        String idNumber,
        String address
) {
}
