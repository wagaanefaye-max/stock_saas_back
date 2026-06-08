package com.stocksaas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionDurationDTO {

    private String code;
    private String label;
    private Integer months;
    private Double discountPercent;
    private Double totalPrice;
}
