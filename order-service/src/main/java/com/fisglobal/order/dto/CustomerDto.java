package com.fisglobal.order.dto;

import java.time.LocalDateTime;

/**
 * Boundary DTO representing a customer record received from the Customer Service.
 * The Order Service never imports Customer entity classes — all cross-domain
 * data is exchanged through these DTOs.
 */
public record CustomerDto(
    Long id,
    String firstName,
    String lastName,
    String email,
    String phone,
    String address,
    String city,
    String state,
    String zipCode,
    String country,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
