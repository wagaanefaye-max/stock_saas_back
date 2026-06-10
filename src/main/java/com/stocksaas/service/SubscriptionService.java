package com.stocksaas.service;

import com.stocksaas.dto.SubscriptionDurationDTO;
import com.stocksaas.dto.SubscriptionPlanOptionDTO;
import com.stocksaas.dto.SubscriptionRecordDTO;
import com.stocksaas.dto.SubscriptionRequestsPageResponse;
import com.stocksaas.dto.SubscriptionStatusDTO;
import com.stocksaas.model.Company;
import com.stocksaas.model.CompanySubscriptionRecord;
import com.stocksaas.model.SubscriptionDuration;
import com.stocksaas.model.SubscriptionPlan;
import com.stocksaas.model.User;
import com.stocksaas.repository.CompanyRepository;
import com.stocksaas.repository.CompanySubscriptionRecordRepository;
import com.stocksaas.repository.SubscriptionDurationRepository;
import com.stocksaas.repository.SubscriptionPlanRepository;
import com.stocksaas.repository.UserRepository;
import com.stocksaas.subscription.PaymentProviderCode;
import com.stocksaas.subscription.SubscriptionPricing;
import com.stocksaas.subscription.SubscriptionRequestStatusCode;
import com.stocksaas.subscription.SubscriptionStatusCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gestion du cycle d'abonnement : essai gratuit 1 mois, upgrade, lecture seule.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    public static final int TRIAL_MONTHS = 1;
    /** Plan payant unique — la tarification dépend uniquement de la durée. */
    public static final String DEFAULT_PAID_PLAN_CODE = "Standard";

    private final CompanyRepository companyRepository;
    private final CompanySubscriptionRecordRepository subscriptionRecordRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionDurationRepository subscriptionDurationRepository;
    private final UserRepository userRepository;
    private final SubscriptionProofStorageService proofStorageService;
    private final PlatformSettingsService platformSettingsService;

    /**
     * Initialise l'essai gratuit d'un mois pour une nouvelle entreprise.
     */
    @Transactional
    public void initializeTrialForNewCompany(Company company) {
        LocalDateTime now = LocalDateTime.now();
        company.setSubscriptionStatus(SubscriptionStatusCode.TRIAL);
        company.setTrialEndsAt(now.plusMonths(TRIAL_MONTHS));
        company.setSubscriptionEndsAt(null);
        company.setDurationCode(null);
    }

    /**
     * Rétroactive l'essai pour les entreprises existantes sans dates (migration douce).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void backfillSubscriptionIfMissing(Long companyId) {
        Company company = companyRepository.findById(companyId).orElse(null);
        if (company == null || (company.getSubscriptionStatus() != null && company.getTrialEndsAt() != null)) {
            return;
        }
        LocalDateTime base = company.getCreatedAt() != null ? company.getCreatedAt() : LocalDateTime.now();
        company.setTrialEndsAt(base.plusMonths(TRIAL_MONTHS));
        company.setSubscriptionStatus(SubscriptionStatusCode.TRIAL);
        company.setSubscriptionEndsAt(null);
        companyRepository.save(company);
    }

    private void applyDefaultsInMemory(Company company) {
        if (company.getSubscriptionStatus() != null && company.getTrialEndsAt() != null) {
            return;
        }
        LocalDateTime base = company.getCreatedAt() != null ? company.getCreatedAt() : LocalDateTime.now();
        company.setTrialEndsAt(base.plusMonths(TRIAL_MONTHS));
        company.setSubscriptionStatus(SubscriptionStatusCode.TRIAL);
    }

    @Transactional(readOnly = true)
    public SubscriptionStatusDTO getStatusForCompany(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Entreprise non trouvée"));
        if (Boolean.TRUE.equals(company.getIsDeleted())) {
            throw new RuntimeException("Entreprise supprimée");
        }
        backfillSubscriptionIfMissing(companyId);
        company = syncSubscriptionStatus(companyId);
        return buildStatusDto(company);
    }

    @Transactional(readOnly = true)
    public SubscriptionStatusDTO getStatusForCurrentUser(String email) {
        User user = userRepository.findByEmailWithCompanyAndRole(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        if (user.getCompany() == null) {
            throw new RuntimeException("Aucune entreprise associée à cet utilisateur");
        }
        return getStatusForCompany(user.getCompany().getId());
    }

    @Transactional
    public SubscriptionRecordDTO submitSubscriptionRequest(
            Long companyId,
            String planCode,
            String durationCode,
            String paymentProvider,
            MultipartFile proofFile,
            String requesterEmail
    ) throws java.io.IOException {
        User requester = userRepository.findByEmailWithCompanyAndRole(requesterEmail)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        assertCompanyAccess(requester, companyId);

        if (subscriptionRecordRepository.existsByCompanyIdAndRequestStatusAndIsDeletedFalse(
                companyId, SubscriptionRequestStatusCode.PENDING)) {
            throw new RuntimeException("Une demande de souscription est déjà en attente de validation");
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Entreprise non trouvée"));
        backfillSubscriptionIfMissing(companyId);
        syncSubscriptionStatus(companyId);

        SubscriptionPlan plan = resolvePaidPlan(planCode != null ? planCode : DEFAULT_PAID_PLAN_CODE);
        SubscriptionDuration duration = resolveDuration(durationCode);

        if (!PaymentProviderCode.isValid(paymentProvider)) {
            throw new RuntimeException("Moyen de paiement invalide. Choisissez Wave ou Orange Money");
        }

        var storedProof = proofStorageService.storeProof(proofFile, companyId);
        double amountPaid = calculateAmount(duration);

        CompanySubscriptionRecord record = new CompanySubscriptionRecord();
        record.setCompany(company);
        record.setPlanCode(plan.getCode());
        record.setPlanLabel(plan.getLabel());
        record.setDurationCode(duration.getCode());
        record.setDurationLabel(duration.getLabel());
        record.setMonths(duration.getMonths());
        record.setAmountPaid(amountPaid);
        record.setRequestStatus(SubscriptionRequestStatusCode.PENDING);
        record.setPaymentProvider(paymentProvider);
        record.setProofFilePath(storedProof.filePath());
        record.setProofFileData(storedProof.data());
        record.setProofContentType(storedProof.contentType());
        record.setSubscribedByEmail(requesterEmail);
        record.setIsDeleted(false);
        record = subscriptionRecordRepository.save(record);

        return toRecordDto(record, requesterEmail);
    }

    @Transactional
    public SubscriptionRecordDTO approveRequest(Long recordId, String adminEmail) {
        requireSuperAdmin(adminEmail);
        CompanySubscriptionRecord record = getPendingRecord(recordId);

        Company company = record.getCompany();
        SubscriptionDuration duration = resolveDuration(record.getDurationCode());
        SubscriptionPlan plan = resolvePaidPlan(record.getPlanCode());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime periodStart = resolveCumulativePeriodStart(company, now);
        LocalDateTime periodEnd = periodStart.plusMonths(duration.getMonths());

        company.setPlan(plan);
        company.setDurationCode(duration.getCode());
        company.setSubscriptionStatus(SubscriptionStatusCode.ACTIVE);
        company.setSubscriptionEndsAt(periodEnd);
        companyRepository.save(company);

        record.setRequestStatus(SubscriptionRequestStatusCode.APPROVED);
        record.setPeriodStart(periodStart);
        record.setPeriodEnd(periodEnd);
        record.setValidatedByEmail(adminEmail);
        record.setValidatedAt(now);
        record.setRejectionReason(null);
        record = subscriptionRecordRepository.save(record);

        return toRecordDto(record, adminEmail);
    }

    @Transactional
    public SubscriptionRecordDTO rejectRequest(Long recordId, String adminEmail, String reason) {
        requireSuperAdmin(adminEmail);
        CompanySubscriptionRecord record = getPendingRecord(recordId);

        record.setRequestStatus(SubscriptionRequestStatusCode.REJECTED);
        record.setValidatedByEmail(adminEmail);
        record.setValidatedAt(LocalDateTime.now());
        record.setRejectionReason(reason != null && !reason.isBlank() ? reason.trim() : "Demande refusée");
        record = subscriptionRecordRepository.save(record);

        return toRecordDto(record, adminEmail);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionRecordDTO> listPendingRequests(String adminEmail) {
        return listAllRequestsForAdmin(adminEmail, SubscriptionRequestStatusCode.PENDING);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionRecordDTO> listAllRequestsForAdmin(String adminEmail, String statusFilter) {
        requireSuperAdmin(adminEmail);
        List<CompanySubscriptionRecord> records = subscriptionRecordRepository
                .findAllWithCompanyOrderByCreatedAtDesc();
        return records.stream()
                .filter(r -> statusFilter == null || statusFilter.isBlank()
                        || statusFilter.equalsIgnoreCase(r.getRequestStatus()))
                .map(r -> toRecordDto(r, adminEmail))
                .toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionRequestsPageResponse listAllRequestsForAdminPaged(
            String adminEmail, String statusFilter, int page, int size) {
        requireSuperAdmin(adminEmail);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<CompanySubscriptionRecord> recordPage;
        if (statusFilter == null || statusFilter.isBlank() || "ALL".equalsIgnoreCase(statusFilter)) {
            recordPage = subscriptionRecordRepository.findByIsDeletedFalseOrderByCreatedAtDesc(pageable);
        } else {
            recordPage = subscriptionRecordRepository
                    .findByRequestStatusAndIsDeletedFalseOrderByCreatedAtDesc(statusFilter.toUpperCase(), pageable);
        }

        List<SubscriptionRecordDTO> content = recordPage.getContent().stream()
                .map(r -> toRecordDto(r, adminEmail))
                .toList();

        return SubscriptionRequestsPageResponse.builder()
                .content(content)
                .page(recordPage.getNumber())
                .size(recordPage.getSize())
                .totalElements(recordPage.getTotalElements())
                .totalPages(recordPage.getTotalPages())
                .first(recordPage.isFirst())
                .last(recordPage.isLast())
                .totalAll(subscriptionRecordRepository.countByIsDeletedFalse())
                .totalPending(subscriptionRecordRepository.countByRequestStatusAndIsDeletedFalse(
                        SubscriptionRequestStatusCode.PENDING))
                .totalApproved(subscriptionRecordRepository.countByRequestStatusAndIsDeletedFalse(
                        SubscriptionRequestStatusCode.APPROVED))
                .totalRejected(subscriptionRecordRepository.countByRequestStatusAndIsDeletedFalse(
                        SubscriptionRequestStatusCode.REJECTED))
                .build();
    }

    @Transactional(readOnly = true)
    public com.stocksaas.dto.ProofResourceResult getProofResource(Long recordId, String userEmail) throws java.io.IOException {
        CompanySubscriptionRecord record = subscriptionRecordRepository.findByIdAndIsDeletedFalse(recordId)
                .orElseThrow(() -> new RuntimeException("Demande non trouvée"));
        User user = userRepository.findByEmailWithCompanyAndRole(userEmail)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        boolean superAdmin = user.getRole() != null && "SUPER_ADMIN".equals(user.getRole().getCode());
        if (!superAdmin) {
            if (user.getCompany() == null || !user.getCompany().getId().equals(record.getCompany().getId())) {
                throw new RuntimeException("Accès non autorisé au justificatif");
            }
        }
        if (record.getProofFilePath() == null && (record.getProofFileData() == null || record.getProofFileData().length == 0)) {
            throw new com.stocksaas.exception.ProofNotFoundException("Aucun justificatif n'est associé à cette demande.");
        }
        return proofStorageService.loadProof(record);
    }

    private CompanySubscriptionRecord getPendingRecord(Long recordId) {
        CompanySubscriptionRecord record = subscriptionRecordRepository.findByIdAndIsDeletedFalse(recordId)
                .orElseThrow(() -> new RuntimeException("Demande non trouvée"));
        if (!SubscriptionRequestStatusCode.PENDING.equals(record.getRequestStatus())) {
            throw new RuntimeException("Cette demande n'est plus en attente de validation");
        }
        return record;
    }

    private SubscriptionPlan resolvePaidPlan(String planCode) {
        String effectiveCode = (planCode == null || planCode.isBlank())
                ? DEFAULT_PAID_PLAN_CODE
                : planCode;
        if ("Free".equalsIgnoreCase(effectiveCode)) {
            throw new RuntimeException("Le plan gratuit ne nécessite pas de souscription.");
        }
        SubscriptionPlan plan = subscriptionPlanRepository.findById(normalizePlanCode(effectiveCode))
                .orElseThrow(() -> new RuntimeException("Plan d'abonnement non trouvé: " + planCode));
        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new RuntimeException("Ce plan n'est plus disponible");
        }
        return plan;
    }

    private SubscriptionDuration resolveDuration(String durationCode) {
        SubscriptionDuration duration = subscriptionDurationRepository.findById(durationCode)
                .orElseThrow(() -> new RuntimeException("Durée d'abonnement non trouvée: " + durationCode));
        if (!Boolean.TRUE.equals(duration.getIsActive())) {
            throw new RuntimeException("Cette durée n'est plus disponible");
        }
        return duration;
    }

    private void assertCompanyAccess(User requester, Long companyId) {
        if (requester.getCompany() == null || !companyId.equals(requester.getCompany().getId())) {
            if (requester.getRole() == null || !"SUPER_ADMIN".equals(requester.getRole().getCode())) {
                throw new RuntimeException("Vous ne pouvez souscrire que pour votre propre entreprise");
            }
        }
    }

    private void requireSuperAdmin(String email) {
        User user = userRepository.findByEmailWithCompanyAndRole(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        if (user.getRole() == null || !"SUPER_ADMIN".equals(user.getRole().getCode())) {
            throw new RuntimeException("Seul le super administrateur peut valider les souscriptions");
        }
    }

    private boolean hasPendingRequest(Long companyId) {
        return subscriptionRecordRepository.existsByCompanyIdAndRequestStatusAndIsDeletedFalse(
                companyId, SubscriptionRequestStatusCode.PENDING);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionRecordDTO> listHistoryForCurrentUser(String email) {
        User user = userRepository.findByEmailWithCompanyAndRole(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        if (user.getCompany() == null) {
            throw new RuntimeException("Aucune entreprise associée à cet utilisateur");
        }
        return listHistoryForCompany(user.getCompany().getId(), email);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionRecordDTO> listHistoryForCompany(Long companyId, String viewerEmail) {
        return subscriptionRecordRepository.findByCompanyIdAndIsDeletedFalseOrderByCreatedAtDesc(companyId)
                .stream()
                .map(r -> toRecordDto(r, viewerEmail))
                .toList();
    }

    private SubscriptionRecordDTO toRecordDto(CompanySubscriptionRecord record, String viewerEmail) {
        String status = record.getRequestStatus() != null
                ? record.getRequestStatus()
                : SubscriptionRequestStatusCode.APPROVED;
        return SubscriptionRecordDTO.builder()
                .id(record.getId())
                .companyId(record.getCompany() != null ? record.getCompany().getId() : null)
                .companyName(record.getCompany() != null ? record.getCompany().getName() : null)
                .planCode(record.getPlanCode())
                .planLabel(record.getPlanLabel())
                .durationCode(record.getDurationCode())
                .durationLabel(record.getDurationLabel())
                .months(record.getMonths())
                .amountPaid(record.getAmountPaid())
                .periodStart(record.getPeriodStart())
                .periodEnd(record.getPeriodEnd())
                .subscribedByEmail(record.getSubscribedByEmail())
                .createdAt(record.getCreatedAt())
                .requestStatus(status)
                .requestStatusLabel(requestStatusLabel(status))
                .paymentProvider(record.getPaymentProvider())
                .paymentProviderLabel(PaymentProviderCode.label(record.getPaymentProvider()))
                .proofUrl(hasProof(record)
                        ? "/api/subscriptions/requests/" + record.getId() + "/proof"
                        : null)
                .validatedByEmail(record.getValidatedByEmail())
                .validatedAt(record.getValidatedAt())
                .rejectionReason(record.getRejectionReason())
                .build();
    }

    private static boolean hasProof(CompanySubscriptionRecord record) {
        return (record.getProofFilePath() != null && !record.getProofFilePath().isBlank())
                || (record.getProofFileData() != null && record.getProofFileData().length > 0);
    }

    private static String requestStatusLabel(String status) {
        return switch (status) {
            case SubscriptionRequestStatusCode.PENDING -> "En attente";
            case SubscriptionRequestStatusCode.APPROVED -> "Validée";
            case SubscriptionRequestStatusCode.REJECTED -> "Refusée";
            default -> status;
        };
    }

    @Transactional(readOnly = true)
    public boolean canWrite(Long companyId) {
        if (companyId == null) {
            return true;
        }
        Company company = companyRepository.findById(companyId).orElse(null);
        if (company == null || Boolean.TRUE.equals(company.getIsDeleted())) {
            return false;
        }
        if (company.getSubscriptionStatus() == null || company.getTrialEndsAt() == null) {
            backfillSubscriptionIfMissing(companyId);
            company = companyRepository.findById(companyId).orElse(null);
            if (company == null) {
                return false;
            }
        }
        company = syncSubscriptionStatus(companyId);
        return isWriteAllowed(company);
    }

    @Transactional(readOnly = true)
    public boolean canWriteForUser(String email) {
        User user = userRepository.findByEmailWithCompanyAndRole(email).orElse(null);
        if (user == null) {
            return false;
        }
        if (user.getRole() != null && "SUPER_ADMIN".equals(user.getRole().getCode())) {
            return true;
        }
        if (user.getCompany() == null) {
            return true;
        }
        return canWrite(user.getCompany().getId());
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlanOptionDTO> listPaidPlans() {
        return subscriptionPlanRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .filter(p -> DEFAULT_PAID_PLAN_CODE.equalsIgnoreCase(p.getCode()))
                .map(p -> SubscriptionPlanOptionDTO.builder()
                        .code(p.getCode())
                        .label(p.getLabel())
                        .monthlyPrice(platformSettingsService.getMonthlyPriceFcfa())
                        .maxUsers(p.getMaxUsers())
                        .maxWarehouses(p.getMaxWarehouses())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDurationDTO> listDurations() {
        return subscriptionDurationRepository.findByIsActiveTrueOrderByMonthsAsc().stream()
                .map(this::toDurationDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildQuote(String planCode, String durationCode) {
        return buildQuote(planCode, durationCode, null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildQuote(String planCode, String durationCode, Long companyId) {
        String effectivePlanCode = (planCode == null || planCode.isBlank())
                ? DEFAULT_PAID_PLAN_CODE
                : planCode;
        resolvePaidPlan(effectivePlanCode);
        SubscriptionDuration duration = subscriptionDurationRepository.findById(durationCode)
                .orElseThrow(() -> new RuntimeException("Durée non trouvée"));
        double discount = duration.getDiscountPercent() != null ? duration.getDiscountPercent() : 0.0;
        double monthlyPrice = platformSettingsService.getMonthlyPriceFcfa();
        double gross = SubscriptionPricing.calculateGross(duration.getMonths(), monthlyPrice);
        double total = calculateAmount(duration);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime periodStart = now;
        boolean willStack = false;
        if (companyId != null) {
            Company company = companyRepository.findById(companyId).orElse(null);
            if (company != null) {
                applyDefaultsInMemory(company);
                periodStart = resolveCumulativePeriodStart(company, now);
                willStack = periodStart.isAfter(now);
            }
        }
        LocalDateTime periodEnd = periodStart.plusMonths(duration.getMonths());

        java.util.HashMap<String, Object> quote = new java.util.HashMap<>();
        quote.put("planCode", effectivePlanCode);
        quote.put("durationCode", duration.getCode());
        quote.put("months", duration.getMonths());
        quote.put("monthlyPrice", monthlyPrice);
        quote.put("discountPercent", discount);
        quote.put("grossTotal", gross);
        quote.put("totalPrice", total);
        quote.put("currency", "FCFA");
        quote.put("periodStart", periodStart);
        quote.put("periodEnd", periodEnd);
        quote.put("willStack", willStack);
        return quote;
    }

    /**
     * Début de la nouvelle période : cumulée après l'abonnement actif ou l'essai en cours, sinon maintenant.
     */
    public LocalDateTime resolveCumulativePeriodStart(Company company, LocalDateTime now) {
        if (SubscriptionStatusCode.ACTIVE.equals(company.getSubscriptionStatus())
                && company.getSubscriptionEndsAt() != null
                && company.getSubscriptionEndsAt().isAfter(now)) {
            return company.getSubscriptionEndsAt();
        }
        if (SubscriptionStatusCode.TRIAL.equals(company.getSubscriptionStatus())
                && company.getTrialEndsAt() != null
                && company.getTrialEndsAt().isAfter(now)) {
            return company.getTrialEndsAt();
        }
        return now;
    }

    public double calculateAmount(SubscriptionDuration duration) {
        double discount = duration.getDiscountPercent() != null ? duration.getDiscountPercent() : 0.0;
        return SubscriptionPricing.calculateTotal(
                duration.getMonths(),
                discount,
                platformSettingsService.getMonthlyPriceFcfa());
    }

    private SubscriptionDurationDTO toDurationDto(SubscriptionDuration d) {
        double discount = d.getDiscountPercent() != null ? d.getDiscountPercent() : 0.0;
        return SubscriptionDurationDTO.builder()
                .code(d.getCode())
                .label(d.getLabel())
                .months(d.getMonths())
                .discountPercent(discount)
                .totalPrice(calculateAmount(d))
                .build();
    }

    /**
     * Recalcule le statut selon les dates et persiste si nécessaire.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Company syncSubscriptionStatus(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Entreprise non trouvée"));
        applyDefaultsInMemory(company);
        LocalDateTime now = LocalDateTime.now();
        String previous = company.getSubscriptionStatus();

        if (SubscriptionStatusCode.ACTIVE.equals(company.getSubscriptionStatus())) {
            if (company.getSubscriptionEndsAt() != null && !company.getSubscriptionEndsAt().isAfter(now)) {
                company.setSubscriptionStatus(SubscriptionStatusCode.EXPIRED);
            }
        } else if (SubscriptionStatusCode.TRIAL.equals(company.getSubscriptionStatus())) {
            if (company.getTrialEndsAt() != null && !company.getTrialEndsAt().isAfter(now)) {
                company.setSubscriptionStatus(SubscriptionStatusCode.EXPIRED);
            }
        }

        if (!java.util.Objects.equals(previous, company.getSubscriptionStatus())) {
            company = companyRepository.save(company);
        }
        return company;
    }

    public boolean isWriteAllowed(Company company) {
        String status = company.getSubscriptionStatus();
        LocalDateTime now = LocalDateTime.now();

        if (SubscriptionStatusCode.TRIAL.equals(status)) {
            return company.getTrialEndsAt() != null && company.getTrialEndsAt().isAfter(now);
        }
        if (SubscriptionStatusCode.ACTIVE.equals(status)) {
            return company.getSubscriptionEndsAt() == null || company.getSubscriptionEndsAt().isAfter(now);
        }
        return false;
    }

    public boolean isReadOnly(Company company) {
        return !isWriteAllowed(company);
    }

    public SubscriptionStatusDTO buildStatusDto(Company company) {
        boolean writeAllowed = isWriteAllowed(company);
        String status = company.getSubscriptionStatus();
        LocalDateTime now = LocalDateTime.now();
        long daysRemaining = 0;

        if (SubscriptionStatusCode.TRIAL.equals(status) && company.getTrialEndsAt() != null) {
            daysRemaining = Math.max(0, ChronoUnit.DAYS.between(now, company.getTrialEndsAt()));
        } else if (SubscriptionStatusCode.ACTIVE.equals(status) && company.getSubscriptionEndsAt() != null) {
            daysRemaining = Math.max(0, ChronoUnit.DAYS.between(now, company.getSubscriptionEndsAt()));
        }

        String durationLabel = null;
        if (company.getDurationCode() != null) {
            durationLabel = subscriptionDurationRepository.findById(company.getDurationCode())
                    .map(SubscriptionDuration::getLabel)
                    .orElse(company.getDurationCode());
        }

        boolean pending = hasPendingRequest(company.getId());
        boolean canUpgrade = !pending && (SubscriptionStatusCode.TRIAL.equals(status)
                || SubscriptionStatusCode.EXPIRED.equals(status)
                || (SubscriptionStatusCode.ACTIVE.equals(status) && writeAllowed));

        LocalDateTime stackFrom = resolveCumulativePeriodStart(company, now);
        boolean willStack = stackFrom.isAfter(now);

        return SubscriptionStatusDTO.builder()
                .companyId(company.getId())
                .planCode(company.getPlan() != null ? company.getPlan().getCode() : "Free")
                .planLabel(company.getPlan() != null ? company.getPlan().getLabel() : "Gratuit")
                .planMonthlyPrice(platformSettingsService.getMonthlyPriceFcfa())
                .subscriptionStatus(status)
                .subscriptionStatusLabel(statusLabel(status))
                .trialEndsAt(company.getTrialEndsAt())
                .subscriptionEndsAt(company.getSubscriptionEndsAt())
                .durationCode(company.getDurationCode())
                .durationLabel(durationLabel)
                .readOnly(!writeAllowed)
                .canUpgrade(canUpgrade)
                .hasPendingRequest(pending)
                .daysRemaining(daysRemaining)
                .nextCumulativeStartAt(willStack ? stackFrom : null)
                .willStackSubscription(willStack)
                .build();
    }

    public void enrichAuthResponse(com.stocksaas.dto.AuthResponse response, User user) {
        if (user.getCompany() == null) {
            response.setReadOnly(false);
            response.setSubscriptionStatus(null);
            return;
        }
        Company company = user.getCompany();
        backfillSubscriptionIfMissing(company.getId());
        company = syncSubscriptionStatus(company.getId());
        SubscriptionStatusDTO status = buildStatusDto(company);
        response.setSubscriptionStatus(status.getSubscriptionStatus());
        response.setSubscriptionStatusLabel(status.getSubscriptionStatusLabel());
        response.setTrialEndsAt(status.getTrialEndsAt());
        response.setSubscriptionEndsAt(status.getSubscriptionEndsAt());
        response.setReadOnly(status.isReadOnly());
        response.setPlanCode(status.getPlanCode());
        response.setDaysRemaining(status.getDaysRemaining());
    }

    private static String statusLabel(String status) {
        if (status == null) {
            return "Inconnu";
        }
        return switch (status) {
            case SubscriptionStatusCode.TRIAL -> "Essai gratuit";
            case SubscriptionStatusCode.ACTIVE -> "Abonnement actif";
            case SubscriptionStatusCode.EXPIRED -> "Expiré (lecture seule)";
            default -> status;
        };
    }

    private static String normalizePlanCode(String code) {
        if (code == null || code.isBlank()) {
            return "Free";
        }
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "FREE" -> "Free";
            case "BASIQUE" -> "Basique";
            case "STANDARD" -> "Standard";
            case "PREMIUM" -> "Premium";
            default -> code;
        };
    }
}
