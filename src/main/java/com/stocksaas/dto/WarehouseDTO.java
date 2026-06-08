package com.stocksaas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO pour représenter un entrepôt
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseDTO {
    private Long id;
    private String name;
    private String region;
    private String description;
    private String statusCode;
    private String statusLabel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
