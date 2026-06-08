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
public class InventoryLineDTO {

    private Long id;
    private Long productId;
    private String productName;
    private String productSku;
    private BigDecimal theoreticalQuantity;
    private BigDecimal countedQuantity;
    private BigDecimal difference; // counted - theoretical (calculé)
}
