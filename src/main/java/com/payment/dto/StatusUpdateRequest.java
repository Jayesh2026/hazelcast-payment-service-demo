package com.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StatusUpdateRequest {

    @NotBlank(message = "status is required")
    private String status; // PENDING, AUTHORIZED, SETTLED, FAILED, REFUNDED

    @NotBlank(message = "updatedBy is required")
    private String updatedBy;
}
