package com.strangequark.trasck.identity;

import java.util.UUID;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class TrasckUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public TrasckUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String identifier) {
        String normalized = identifier == null ? "" : identifier.trim();
        User user = userRepository.findByEmailIgnoreCase(normalized)
                .or(() -> userRepository.findByUsernameIgnoreCase(normalized))
                .orElseThrow(() -> new UsernameNotFoundException(normalized));
        return toPrincipal(user);
    }

    public TrasckPrincipal loadUserById(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UsernameNotFoundException(userId.toString()));
        return toPrincipal(user);
    }

    private TrasckPrincipal toPrincipal(User user) {
        TrasckPrincipal principal = new TrasckPrincipal(user);
        if (!principal.isEnabled()) {
            throw new UsernameNotFoundException(user.getUsername());
        }
        return principal;
    }
}
