package com.stocksaas.controller;

import com.stocksaas.dto.PageResponse;
import com.stocksaas.dto.UpdateUserRequest;
import com.stocksaas.dto.UpdateUserStatusRequest;
import com.stocksaas.dto.UserDTO;
import com.stocksaas.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller pour la gestion des utilisateurs
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_ENTREPRISE')")
@Tag(name = "Utilisateurs", description = "API pour la gestion des utilisateurs")
@CrossOrigin(
        origins = {
                "http://localhost:4200",
                "http://localhost:3000",
                "http://sen-stocksaas.com",
                "https://sen-stocksaas.com"
        },
        allowCredentials = "true"
)
public class UserController {
    
    private final UserService userService;
    
    @GetMapping
    @Operation(summary = "Liste des utilisateurs", description = "Super-admin : tous les utilisateurs ; admin entreprise : gestionnaires de son entreprise")
    public ResponseEntity<PageResponse<UserDTO>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {
        PageResponse<UserDTO> response = userService.listUsersForCurrentUser(page, size, search);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Détails d'un utilisateur", description = "Récupère les détails d'un utilisateur par son ID")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id) {
        try {
            UserDTO user = userService.getUserById(id);
            return ResponseEntity.ok(user);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Modifier un utilisateur", description = "Met à jour les informations d'un utilisateur")
    public ResponseEntity<?> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        try {
            UserDTO updatedUser = userService.updateUser(id, request);
            return ResponseEntity.ok(updatedUser);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Une erreur est survenue lors de la mise à jour"));
        }
    }
    
    @PatchMapping("/{id}/status")
    @Operation(summary = "Modifier le statut d'un utilisateur", description = "Active ou désactive un utilisateur")
    public ResponseEntity<?> updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        try {
            UserDTO updatedUser = userService.updateUserStatus(id, request);
            return ResponseEntity.ok(updatedUser);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Une erreur est survenue lors de la mise à jour du statut"));
        }
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un utilisateur", description = "Supprime un utilisateur (soft delete)")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            return ResponseEntity.ok(Map.of("message", "Utilisateur supprimé avec succès"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Une erreur est survenue lors de la suppression"));
        }
    }
}
