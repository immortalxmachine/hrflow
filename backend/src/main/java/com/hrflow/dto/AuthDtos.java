package com.hrflow.dto;

import com.hrflow.domain.Country;
import com.hrflow.domain.Role;
import jakarta.validation.constraints.NotBlank;

/** Request/response payloads for authentication. */
public final class AuthDtos {

    private AuthDtos() { }

    public record LoginRequest(
            @NotBlank String employeeCode,
            @NotBlank String password) { }

    public record LoginResponse(
            String token,
            long expiresInMinutes,
            Long employeeId,
            String employeeCode,
            String fullName,
            Role role,
            Country country) { }
}
