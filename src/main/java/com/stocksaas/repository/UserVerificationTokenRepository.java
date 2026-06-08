package com.stocksaas.repository;

import com.stocksaas.model.UserVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository pour les tokens de validation
 */
@Repository
public interface UserVerificationTokenRepository extends JpaRepository<UserVerificationToken, Long> {
    
    /**
     * Trouve un token par sa valeur
     */
    Optional<UserVerificationToken> findByToken(String token);
    
    /**
     * Trouve un token valide (non utilisé et non expiré) par sa valeur
     */
    @Query("SELECT t FROM UserVerificationToken t WHERE t.token = :token AND t.used = false AND t.expiresAt > :now")
    Optional<UserVerificationToken> findValidToken(@Param("token") String token, @Param("now") LocalDateTime now);

    /**
     * Trouve un token valide avec user, role et company (évite N+1 dans verifyAccount).
     */
    @Query("SELECT t FROM UserVerificationToken t LEFT JOIN FETCH t.user u LEFT JOIN FETCH u.role LEFT JOIN FETCH u.company WHERE t.token = :token AND t.used = false AND t.expiresAt > :now")
    Optional<UserVerificationToken> findValidTokenWithUser(@Param("token") String token, @Param("now") LocalDateTime now);
    
    /**
     * Marque un token comme utilisé
     */
    @Modifying
    @Query("UPDATE UserVerificationToken t SET t.used = true WHERE t.token = :token")
    void markAsUsed(@Param("token") String token);
}
