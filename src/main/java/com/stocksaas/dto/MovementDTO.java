package com.stocksaas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO pour représenter un mouvement de stock
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovementDTO {
    private Long id;
    private Long productId;
    private String productName;
    private String productSku;
    private String typeCode;
    private String typeLabel;
    private BigDecimal quantity;
    private LocalDate date;
    private Long warehouseId;
    private String warehouseName;
    private Long destinationWarehouseId;
    private String destinationWarehouseName;
    private Long userId;
    private String userName;
    private String justification;
    private LocalDateTime createdAt;
}
