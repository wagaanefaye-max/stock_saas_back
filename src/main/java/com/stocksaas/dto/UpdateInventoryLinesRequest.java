package com.stocksaas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateInventoryLinesRequest {

    @Valid
    private List<LineUpdate> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineUpdate {
        @NotNull(message = "Le produit est obligatoire")
        private Long productId;
        @NotNull(message = "La quantité comptée est obligatoire")
        private BigDecimal countedQuantity;
    }
}
