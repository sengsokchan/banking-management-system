package com.portfolio.banking.security;

import com.portfolio.banking.domain.Permission;
import com.portfolio.banking.domain.Role;
import com.portfolio.banking.domain.StaffUser;
import com.portfolio.banking.repo.StaffUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class StaffUserDetailsService implements UserDetailsService {

    private final StaffUserRepository staffUserRepository;

    public StaffUserDetailsService(StaffUserRepository staffUserRepository) {
        this.staffUserRepository = staffUserRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        StaffUser user = staffUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User was not found."));
        return toPrincipal(user);
    }

    @Transactional(readOnly = true)
    public StaffPrincipal loadActiveById(UUID id) {
        StaffUser user = staffUserRepository.findWithAccessById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User was not found."));
        if (!user.isActive()) {
            throw new UsernameNotFoundException("User is inactive.");
        }
        return toPrincipal(user);
    }

    private StaffPrincipal toPrincipal(StaffUser user) {
        Set<SimpleGrantedAuthority> authorities = new HashSet<>();
        for (Role role : user.getRoles()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
            for (Permission permission : role.getPermissions()) {
                authorities.add(new SimpleGrantedAuthority(permission.getCode()));
            }
        }
        return new StaffPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getFullName(),
                user.getEmail(),
                user.isActive(),
                authorities
        );
    }
}
