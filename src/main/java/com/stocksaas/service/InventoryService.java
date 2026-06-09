package com.stocksaas.service;

import com.stocksaas.dto.*;
import com.stocksaas.model.*;
import com.stocksaas.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryLineRepository inventoryLineRepository;
    private final CompanyRepository companyRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final StockLevelRepository stockLevelRepository;
    private final MovementService movementService;

    @Transactional
    public InventoryDTO create(Long companyId, Long userId, CreateInventoryRequest request) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Entreprise non trouvée"));
        if (company.getIsDeleted()) {
            throw new RuntimeException("L'entreprise a été supprimée");
        }
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new RuntimeException("Entrepôt non trouvé"));
        if (!warehouse.getCompany().getId().equals(companyId)) {
            throw new RuntimeException("L'entrepôt n'appartient pas à cette entreprise");
        }
        if (warehouse.getIsDeleted()) {
            throw new RuntimeException("L'entrepôt a été supprimé");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        List<Inventory> open = inventoryRepository.findOpenByCompanyIdAndWarehouseId(companyId, request.getWarehouseId());
        if (!open.isEmpty()) {
            throw new RuntimeException("Un inventaire est déjà en cours pour cet entrepôt. Clôturez-le avant d'en créer un nouveau.");
        }

        Inventory inventory = new Inventory();
        inventory.setCompany(company);
        inventory.setWarehouse(warehouse);
        inventory.setInventoryDate(request.getInventoryDate());
        inventory.setStatus("IN_PROGRESS");
        inventory.setCreatedBy(user);
        inventory.setNotes(request.getNotes());
        inventory.setIsDeleted(false);
        inventory = inventoryRepository.save(inventory);

        List<StockLevel> levels = stockLevelRepository.findByWarehouseIdWithProduct(request.getWarehouseId());
        for (StockLevel sl : levels) {
            if (sl.getQuantity() == null || sl.getQuantity().compareTo(BigDecimal.ZERO) < 0) continue;
            InventoryLine line = new InventoryLine();
            line.setInventory(inventory);
            line.setProduct(sl.getProduct());
            line.setTheoreticalQuantity(sl.getQuantity());
            line.setCountedQuantity(sl.getQuantity());
            line.setIsDeleted(false);
            inventoryLineRepository.save(line);
        }

        return getById(companyId, inventory.getId());
    }

    @Transactional(readOnly = true)
    public InventoryDTO getById(Long companyId, Long id) {
        Inventory inventory = inventoryRepository.findByIdWithWarehouseAndCompany(id)
                .orElseThrow(() -> new RuntimeException("Inventaire non trouvé"));
        if (inventory.getIsDeleted() || !inventory.getCompany().getId().equals(companyId)) {
            throw new RuntimeException("Inventaire non trouvé");
        }
        List<InventoryLine> lines = inventoryLineRepository.findByInventoryIdWithProduct(id);
        return mapToDTO(inventory, lines);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryDTO> listByCompanyPaged(
            Long companyId,
            List<Long> warehouseIds,
            Long filterWarehouseId,
            String filterStatus,
            int page,
            int size) {
        boolean restrictWarehouses = warehouseIds != null && !warehouseIds.isEmpty();
        List<Long> scopedWarehouseIds = restrictWarehouses ? warehouseIds : List.of(-1L);
        String statusFilter = normalizeStatusFilter(filterStatus);

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "inventoryDate").and(Sort.by(Sort.Direction.DESC, "id")));

        Page<Inventory> inventoryPage = inventoryRepository.findPagedByCompany(
                companyId,
                filterWarehouseId,
                statusFilter,
                restrictWarehouses,
                scopedWarehouseIds,
                pageable);

        List<InventoryDTO> content = inventoryPage.getContent().stream()
                .map(this::mapToSummaryDTO)
                .collect(Collectors.toList());

        return PageResponse.<InventoryDTO>builder()
                .content(content)
                .page(inventoryPage.getNumber())
                .size(inventoryPage.getSize())
                .totalElements(inventoryPage.getTotalElements())
                .totalPages(inventoryPage.getTotalPages())
                .first(inventoryPage.isFirst())
                .last(inventoryPage.isLast())
                .build();
    }

    private static String normalizeStatusFilter(String filterStatus) {
        if (filterStatus == null || filterStatus.isBlank() || "ALL".equalsIgnoreCase(filterStatus)) {
            return null;
        }
        return filterStatus;
    }

    @Transactional
    public InventoryDTO updateLines(Long companyId, Long inventoryId, UpdateInventoryLinesRequest request) {
        Inventory inventory = inventoryRepository.findByIdWithWarehouseAndCompany(inventoryId)
                .orElseThrow(() -> new RuntimeException("Inventaire non trouvé"));
        if (inventory.getIsDeleted() || !inventory.getCompany().getId().equals(companyId)) {
            throw new RuntimeException("Inventaire non trouvé");
        }
        if ("CLOSED".equals(inventory.getStatus())) {
            throw new RuntimeException("Impossible de modifier un inventaire clôturé");
        }
        if (request.getLines() == null) return getById(companyId, inventoryId);
        for (UpdateInventoryLinesRequest.LineUpdate lu : request.getLines()) {
            inventoryLineRepository.findByInventoryIdAndProductId(inventoryId, lu.getProductId())
                    .ifPresent(line -> {
                        if (lu.getCountedQuantity() != null && lu.getCountedQuantity().compareTo(BigDecimal.ZERO) >= 0) {
                            line.setCountedQuantity(lu.getCountedQuantity());
                            inventoryLineRepository.save(line);
                        }
                    });
        }
        return getById(companyId, inventoryId);
    }

    @Transactional
    public InventoryDTO close(Long companyId, Long userId, Long inventoryId) {
        Inventory inventory = inventoryRepository.findByIdWithWarehouseAndCompany(inventoryId)
                .orElseThrow(() -> new RuntimeException("Inventaire non trouvé"));
        if (inventory.getIsDeleted() || !inventory.getCompany().getId().equals(companyId)) {
            throw new RuntimeException("Inventaire non trouvé");
        }
        if ("CLOSED".equals(inventory.getStatus())) {
            throw new RuntimeException("Cet inventaire est déjà clôturé");
        }

        List<InventoryLine> lines = inventoryLineRepository.findByInventoryIdWithProduct(inventoryId);
        for (InventoryLine line : lines) {
            BigDecimal counted = line.getCountedQuantity() != null ? line.getCountedQuantity() : line.getTheoreticalQuantity();
            BigDecimal theoretical = line.getTheoreticalQuantity() != null ? line.getTheoreticalQuantity() : BigDecimal.ZERO;
            BigDecimal diff = counted.subtract(theoretical);
            if (diff.compareTo(BigDecimal.ZERO) == 0) continue;

            CreateMovementRequest movReq = CreateMovementRequest.builder()
                    .typeCode("AJUSTEMENT")
                    .productId(line.getProduct().getId())
                    .warehouseId(inventory.getWarehouse().getId())
                    .quantity(diff)
                    .date(inventory.getInventoryDate())
                    .justification("Inventaire du " + inventory.getInventoryDate() + " (session #" + inventoryId + ")")
                    .build();
            movementService.createMovement(companyId, userId, movReq);
        }

        inventory.setStatus("CLOSED");
        inventory.setClosedAt(LocalDateTime.now());
        inventoryRepository.save(inventory);
        return getById(companyId, inventoryId);
    }

    private static String statusToLabel(String status) {
        if (status == null) return "";
        return switch (status) {
            case "DRAFT" -> "Brouillon";
            case "IN_PROGRESS" -> "En cours";
            case "CLOSED" -> "Clôturé";
            default -> status;
        };
    }

    private InventoryDTO mapToSummaryDTO(Inventory i) {
        return InventoryDTO.builder()
                .id(i.getId())
                .warehouseId(i.getWarehouse().getId())
                .warehouseName(i.getWarehouse().getName())
                .inventoryDate(i.getInventoryDate())
                .status(i.getStatus())
                .statusLabel(statusToLabel(i.getStatus()))
                .createdById(i.getCreatedBy() != null ? i.getCreatedBy().getId() : null)
                .createdByName(i.getCreatedBy() != null ? i.getCreatedBy().getName() : null)
                .closedAt(i.getClosedAt())
                .notes(i.getNotes())
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .lines(Collections.emptyList())
                .build();
    }

    private InventoryDTO mapToDTO(Inventory i, List<InventoryLine> lines) {
        List<InventoryLineDTO> lineDTOs = lines.stream()
                .map(this::mapLineToDTO)
                .collect(Collectors.toList());
        return InventoryDTO.builder()
                .id(i.getId())
                .warehouseId(i.getWarehouse().getId())
                .warehouseName(i.getWarehouse().getName())
                .inventoryDate(i.getInventoryDate())
                .status(i.getStatus())
                .statusLabel(statusToLabel(i.getStatus()))
                .createdById(i.getCreatedBy() != null ? i.getCreatedBy().getId() : null)
                .createdByName(i.getCreatedBy() != null ? i.getCreatedBy().getName() : null)
                .closedAt(i.getClosedAt())
                .notes(i.getNotes())
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .lines(lineDTOs)
                .build();
    }

    private InventoryLineDTO mapLineToDTO(InventoryLine l) {
        BigDecimal counted = l.getCountedQuantity() != null ? l.getCountedQuantity() : l.getTheoreticalQuantity();
        BigDecimal theoretical = l.getTheoreticalQuantity() != null ? l.getTheoreticalQuantity() : BigDecimal.ZERO;
        BigDecimal diff = counted != null ? counted.subtract(theoretical) : BigDecimal.ZERO;
        return InventoryLineDTO.builder()
                .id(l.getId())
                .productId(l.getProduct().getId())
                .productName(l.getProduct().getName())
                .productSku(l.getProduct().getSku())
                .theoreticalQuantity(theoretical)
                .countedQuantity(counted)
                .difference(diff)
                .build();
    }
}
