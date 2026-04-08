package com.paytend.l4pc.aml.api;

import jakarta.validation.constraints.NotBlank;

public record ScreeningRequest(

        @NotBlank
        String customer_id,

        @NotBlank
        String name,

        String date_of_birth,

        String nationality,

        String id_number,

        String address,

        String screening_type,

        String trace_id,

        String actor,

        String source_system
) {
}
