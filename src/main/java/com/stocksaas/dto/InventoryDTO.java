package com.stocksaas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryDTO {

    private Long id;
    private Long warehouseId;
    private String warehouseName;
    private LocalDate inventoryDate;
    private String status;
    private String statusLabel;
    private Long createdById;
    private String createdByName;
    private LocalDateTime closedAt;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<InventoryLineDTO> lines;
}
