package com.stocksaas.security;

import com.stocksaas.model.User;
import com.stocksaas.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;

/**
 * Service pour charger les détails de l'utilisateur pour Spring Security
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    
    private final UserRepository userRepository;
    
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailAndNotDeleted(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé avec l'email: " + email));
        
        // Si l'utilisateur n'a pas de mot de passe (compte non validé), utiliser un mot de passe temporaire
        // qui ne pourra jamais être utilisé pour se connecter
        String password = user.getPassword() != null ? user.getPassword() : "{noop}NO_PASSWORD_SET";
        
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(password)
                .authorities(getAuthorities(user))
                .accountExpired(false)
                .accountLocked(!user.getStatus().equals("Actif"))
                .credentialsExpired(false)
                .disabled(user.getStatus().equals("Inactif") || user.getIsDeleted() || user.getPassword() == null)
                .build();
    }
    
    /**
     * Récupère les autorités de l'utilisateur basées sur son rôle
     */
    private Collection<? extends GrantedAuthority> getAuthorities(User user) {
        if (user.getRole() == null || user.getRole().getCode() == null) {
            return Collections.emptyList();
        }
        
        String roleCode = user.getRole().getCode();
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + roleCode));
    }
}
