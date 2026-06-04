package com.mac.bry.desktop.security.service;

import com.mac.bry.desktop.security.model.Role;
import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.security.repository.RoleRepository;
import com.mac.bry.desktop.security.repository.UserRepository;
import com.mac.bry.desktop.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAccountService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    @Transactional
    public void activateUser(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setEnabled(true);
            log.info("Zaktualizowano status użytkownika {} (ID: {}) na Aktywny", user.getUsername(), userId);
            emailService.sendAccountActivatedEmail(user.getEmail(), user.getFullName());
        });
    }

    @Transactional
    public void deactivateUser(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setEnabled(false);
            log.info("Zaktualizowano status użytkownika {} (ID: {}) na Nieaktywny", user.getUsername(), userId);
            emailService.sendAccountDeactivatedEmail(user.getEmail(), user.getFullName());
        });
    }

    @Transactional
    public void lockUser(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLocked(true);
            user.setLockedUntil(null);
            log.info("Administrator zablokował konto użytkownika {} (ID: {})", user.getUsername(), userId);
        });
    }

    @Transactional
    public void unlockUser(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLocked(false);
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            log.info("Odblokowano użytkownika: {}", user.getUsername());
        });
    }

    @Transactional
    public void setMustChangePassword(Long userId, boolean mustChange) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setMustChangePassword(mustChange);
            log.info("Wymuszono zmianę hasła dla użytkownika {} (ID: {})", user.getUsername(), userId);
        });
    }

    @Transactional
    public void updateUserRoles(Long userId, Set<Role> newRoles) {
        userRepository.findById(userId).ifPresent(user -> {
            user.getRoles().clear();
            user.getRoles().addAll(newRoles);
            user.setUpdatedDate(LocalDateTime.now());
            log.info("Zaktualizowano role dla użytkownika {} (ID: {})", user.getUsername(), userId);
        });
    }

    @Transactional(readOnly = true)
    public boolean hasRole(User user, String roleName) {
        return user.getRoles().stream()
                .anyMatch(r -> r.getName().equals(roleName));
    }

    @Transactional(readOnly = true)
    public List<String> getSuperAdminEmails() {
        return userRepository.findByRoleName("ROLE_SUPER_ADMIN").stream()
                .map(User::getEmail)
                .collect(Collectors.toList());
    }
}
