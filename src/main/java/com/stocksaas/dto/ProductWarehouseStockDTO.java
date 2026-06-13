package com.stocksaas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductWarehouseStockDTO {
    private Long warehouseId;
    private String warehouseName;
    private BigDecimal quantity;
    private BigDecimal minThreshold;
    private Boolean lowStock;
}
