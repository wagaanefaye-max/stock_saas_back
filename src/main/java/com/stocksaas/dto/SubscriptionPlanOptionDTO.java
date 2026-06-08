package com.stocksaas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlanOptionDTO {

    private String code;
    private String label;
    private Double monthlyPrice;
    private Integer maxUsers;
    private Integer maxWarehouses;
}
