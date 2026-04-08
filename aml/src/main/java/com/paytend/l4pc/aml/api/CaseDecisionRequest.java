package com.paytend.l4pc.aml.api;

import jakarta.validation.constraints.NotBlank;

public record CaseDecisionRequest(

        @NotBlank
        String decision,

        @NotBlank
        String decision_reason,

        @NotBlank
        String decided_by,

        String approved_by,

        String attachments
) {
}
