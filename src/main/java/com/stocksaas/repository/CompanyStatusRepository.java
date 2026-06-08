package com.stocksaas.repository;

import com.stocksaas.model.CompanyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour l'entité CompanyStatus (table de référence)
 */
@Repository
public interface CompanyStatusRepository extends JpaRepository<CompanyStatus, String> {
}
