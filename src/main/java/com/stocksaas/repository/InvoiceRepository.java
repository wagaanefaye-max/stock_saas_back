package com.stocksaas.repository;

import com.stocksaas.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @Query("SELECT i FROM Invoice i WHERE i.company.id = :companyId AND i.isDeleted = false ORDER BY i.invoiceDate DESC, i.id DESC")
    List<Invoice> findByCompanyIdAndNotDeleted(@Param("companyId") Long companyId);

    /** Liste des factures avec client, créateur, lignes et produits (évite N+1 sur mapToDTO). */
    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.client LEFT JOIN FETCH i.createdBy LEFT JOIN FETCH i.lines l LEFT JOIN FETCH l.product WHERE i.company.id = :companyId AND i.isDeleted = false ORDER BY i.invoiceDate DESC, i.id DESC")
    List<Invoice> findByCompanyIdAndNotDeletedWithClientAndLines(@Param("companyId") Long companyId);

    /** Détail facture avec company, client, créateur, lignes et produits (évite N+1 sur getById, update, delete, PDF public). */
    @Query("SELECT i FROM Invoice i LEFT JOIN FETCH i.company LEFT JOIN FETCH i.client LEFT JOIN FETCH i.createdBy LEFT JOIN FETCH i.lines l LEFT JOIN FETCH l.product WHERE i.id = :id AND i.isDeleted = false")
    Optional<Invoice> findByIdWithLinesAndProduct(@Param("id") Long id);

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.company.id = :companyId AND i.isDeleted = false AND YEAR(i.invoiceDate) = :year")
    long countByCompanyIdAndYear(@Param("companyId") Long companyId, @Param("year") int year);

    /** Compte toutes les factures de l'année (toutes sociétés, y compris supprimées) pour générer un numéro unique. */
    @Query("SELECT COUNT(i) FROM Invoice i WHERE YEAR(i.invoiceDate) = :year")
    long countByYearIncludingDeleted(@Param("year") int year);

    @Query("SELECT i FROM Invoice i WHERE i.company.id = :companyId AND i.isDeleted = false AND i.invoiceNumber = :invoiceNumber")
    Optional<Invoice> findByCompanyIdAndInvoiceNumber(@Param("companyId") Long companyId, @Param("invoiceNumber") String invoiceNumber);

    @Query("""
            SELECT i.status, COUNT(i), COALESCE(SUM(i.total), 0)
            FROM Invoice i
            WHERE i.company.id = :companyId AND i.isDeleted = false
            GROUP BY i.status
            """)
    List<Object[]> summarizeByStatus(@Param("companyId") Long companyId);

    @Query("""
            SELECT i FROM Invoice i
            WHERE i.company.id = :companyId AND i.isDeleted = false
            AND i.status = 'PAID' AND i.invoiceDate >= :since
            """)
    List<Invoice> findPaidInvoicesSince(@Param("companyId") Long companyId, @Param("since") LocalDate since);

    @Query("""
            SELECT i FROM Invoice i LEFT JOIN FETCH i.client
            WHERE i.company.id = :companyId AND i.isDeleted = false
            AND i.status IN ('SENT', 'DRAFT')
            ORDER BY i.invoiceDate DESC, i.id DESC
            """)
    List<Invoice> findPendingForDashboard(@Param("companyId") Long companyId, org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT i FROM Invoice i LEFT JOIN FETCH i.client
            WHERE i.company.id = :companyId AND i.isDeleted = false
            ORDER BY i.invoiceDate DESC, i.id DESC
            """)
    List<Invoice> findRecentForDashboard(@Param("companyId") Long companyId, org.springframework.data.domain.Pageable pageable);
}
