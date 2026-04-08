package com.paytend.l4pc.aml.api;

public record ErrorResponse(ApiError error) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new ApiError(code, message));
    }

    public record ApiError(String code, String message) {
    }
}
