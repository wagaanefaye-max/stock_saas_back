package com.stocksaas.repository;

import com.stocksaas.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour l'entité UserRole (table de référence)
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, String> {
}
