package com.stocksaas.service;

import com.stocksaas.exception.ForbiddenAccessException;
import com.stocksaas.dto.CreatePartnerRequest;
import com.stocksaas.dto.PartnerDTO;
import com.stocksaas.dto.UpdatePartnerRequest;
import com.stocksaas.model.Company;
import com.stocksaas.model.Partner;
import com.stocksaas.repository.CompanyRepository;
import com.stocksaas.repository.PartnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PartnerService {

    private final PartnerRepository partnerRepository;
    private final CompanyRepository companyRepository;

    private static String roleToLabel(String role) {
        if (role == null) return "";
        return "FOURNISSEUR".equals(role) ? "Fournisseur" : "Client";
    }

    @Transactional
    public PartnerDTO create(Long companyId, CreatePartnerRequest request) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Entreprise non trouvée"));
        if (company.getIsDeleted()) {
            throw new RuntimeException("L'entreprise a été supprimée");
        }
        String email = request.getEmail() != null ? request.getEmail().trim() : null;
        String phone = request.getPhone() != null ? request.getPhone().trim() : null;
        if (email != null && !email.isEmpty()) {
            partnerRepository.findByCompanyIdAndEmailIgnoreCaseAndNotDeleted(companyId, email, null)
                    .ifPresent(p -> { throw new RuntimeException("Un partenaire avec cet email existe déjà dans votre entreprise"); });
        }
        if (phone != null && !phone.isEmpty()) {
            partnerRepository.findByCompanyIdAndPhoneAndNotDeleted(companyId, phone, null)
                    .ifPresent(p -> { throw new RuntimeException("Un partenaire avec ce numéro de téléphone existe déjà dans votre entreprise"); });
        }
        Partner partner = new Partner();
        partner.setCompany(company);
        partner.setRole(request.getRole());
        partner.setName(request.getName().trim());
        partner.setEmail(email);
        partner.setPhone(phone);
        partner.setAddress(request.getAddress());
        partner.setDescription(request.getDescription());
        partner.setIsDeleted(false);
        partner = partnerRepository.save(partner);
        return mapToDTO(partner);
    }

    @Transactional(readOnly = true)
    public List<PartnerDTO> findAllByCompany(Long companyId, String roleFilter) {
        List<Partner> list;
        if (roleFilter != null && !roleFilter.isBlank() && ("CLIENT".equals(roleFilter) || "FOURNISSEUR".equals(roleFilter))) {
            list = partnerRepository.findByCompanyIdAndRoleAndNotDeleted(companyId, roleFilter);
        } else {
            list = partnerRepository.findByCompanyIdAndNotDeleted(companyId);
        }
        return list.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<PartnerDTO> findAllByCompanyPaged(Long companyId, String roleFilter, int page, int size, String search) {
        if (size <= 0) size = 10;
        if (page < 0) page = 0;
        String role = (roleFilter != null && ("CLIENT".equals(roleFilter) || "FOURNISSEUR".equals(roleFilter))) ? roleFilter : null;
        boolean hasSearch = search != null && !search.isBlank();
        Page<Partner> partnerPage;
        if (role != null) {
            Pageable pageable = PageRequest.of(page, size);
            if (hasSearch) {
                partnerPage = partnerRepository.findByCompanyIdAndRoleAndNotDeletedAndSearch(companyId, role, search.trim(), pageable);
            } else {
                partnerPage = partnerRepository.findByCompanyIdAndRoleAndNotDeleted(companyId, role, pageable);
            }
        } else {
            Pageable pageable = PageRequest.of(page, size);
            if (hasSearch) {
                partnerPage = partnerRepository.findByCompanyIdAndNotDeletedAndSearch(companyId, search.trim(), pageable);
            } else {
                partnerPage = partnerRepository.findByCompanyIdAndNotDeleted(companyId, pageable);
            }
        }
        return partnerPage.map(this::mapToDTO);
    }

    @Transactional(readOnly = true)
    public PartnerDTO getById(Long companyId, Long id) {
        Partner partner = partnerRepository.findByIdWithCompany(id)
                .orElseThrow(() -> new RuntimeException("Partenaire non trouvé"));
        if (partner.getIsDeleted()) {
            throw new RuntimeException("Le partenaire a été supprimé");
        }
        assertBelongsToCompany(partner, companyId);
        return mapToDTO(partner);
    }

    @Transactional
    public PartnerDTO update(Long companyId, Long id, UpdatePartnerRequest request) {
        Partner partner = partnerRepository.findByIdWithCompany(id)
                .orElseThrow(() -> new RuntimeException("Partenaire non trouvé"));
        if (partner.getIsDeleted()) {
            throw new RuntimeException("Le partenaire a été supprimé");
        }
        assertBelongsToCompany(partner, companyId);
        if (request.getEmail() != null) {
            String email = request.getEmail().trim().isEmpty() ? null : request.getEmail().trim();
            if (email != null && !email.isEmpty()) {
                partnerRepository.findByCompanyIdAndEmailIgnoreCaseAndNotDeleted(companyId, email, id)
                        .ifPresent(p -> { throw new RuntimeException("Un partenaire avec cet email existe déjà dans votre entreprise"); });
            }
            partner.setEmail(email);
        }
        if (request.getPhone() != null) {
            String phone = request.getPhone().trim().isEmpty() ? null : request.getPhone().trim();
            if (phone != null && !phone.isEmpty()) {
                partnerRepository.findByCompanyIdAndPhoneAndNotDeleted(companyId, phone, id)
                        .ifPresent(p -> { throw new RuntimeException("Un partenaire avec ce numéro de téléphone existe déjà dans votre entreprise"); });
            }
            partner.setPhone(phone);
        }
        if (request.getRole() != null) partner.setRole(request.getRole());
        if (request.getName() != null) partner.setName(request.getName().trim());
        if (request.getAddress() != null) partner.setAddress(request.getAddress());
        if (request.getDescription() != null) partner.setDescription(request.getDescription());
        partner = partnerRepository.save(partner);
        return mapToDTO(partner);
    }

    @Transactional
    public void delete(Long companyId, Long id) {
        Partner partner = partnerRepository.findByIdWithCompany(id)
                .orElseThrow(() -> new RuntimeException("Partenaire non trouvé"));
        if (partner.getIsDeleted()) {
            throw new RuntimeException("Le partenaire a été supprimé");
        }
        assertBelongsToCompany(partner, companyId);
        partner.setIsDeleted(true);
        partnerRepository.save(partner);
    }

    private void assertBelongsToCompany(Partner partner, Long companyId) {
        if (partner.getCompany() == null || !companyId.equals(partner.getCompany().getId())) {
            throw new ForbiddenAccessException("Accès non autorisé à ce partenaire");
        }
    }

    private PartnerDTO mapToDTO(Partner p) {
        return PartnerDTO.builder()
                .id(p.getId())
                .role(p.getRole())
                .roleLabel(roleToLabel(p.getRole()))
                .name(p.getName())
                .email(p.getEmail())
                .phone(p.getPhone())
                .address(p.getAddress())
                .description(p.getDescription())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
