# Szczegółowy Plan Refaktoryzacji: UserService

## Przegląd

Przekształcenie monolitycznego `UserService` (316 linii) w system 4 specjalistycznych serwisów.

## Architektura Po Refaktoryzacji

```
UserService (DEPRECATED - facade dla backward compatibility)
├── UserManagementService
│   ├── getAllUsers()
│   ├── getAllUsersByDepartment()
│   ├── updateUserProfile()
│   └── updateUserLocation()
│
├── UserPasswordService
│   ├── changeUserPassword()
│   ├── resetPassword()
│   ├── changePasswordWithOld()
│   └── isPasswordInHistory()
│
├── UserAuthenticationService
│   ├── incrementFailedLoginAttempts()
│   ├── resetFailedLoginAttempts()
│   ├── registerSession()
│   ├── updateLastLogin()
│   ├── clearSession()
│   ├── updateActivity()
│   ├── isAlreadyLoggedIn()
│   └── checkAccountExpiration()
│
└── UserAccountService
    ├── activateUser()
    ├── deactivateUser()
    ├── lockUser()
    ├── unlockUser()
    ├── setMustChangePassword()
    ├── updateUserRoles()
    ├── hasRole()
    └── getAllRoles()
```

---

## Krok 1: UserManagementService

```java
package com.mac.bry.desktop.security.service;

import com.mac.bry.desktop.security.model.Department;
import com.mac.bry.desktop.security.model.Laboratory;
import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserManagementService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<User> getAllUsersByDepartment(Long departmentId) {
        return userRepository.findByDepartmentId(departmentId);
    }

    @Transactional
    public void updateUserProfile(Long userId, String firstName, String lastName, String email, String phone) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEmail(email);
            user.setPhone(phone);
            log.info("Zaktualizowano profil użytkownika ID: {}", userId);
        });
    }

    @Transactional
    public void updateUserLocation(Long userId, Department dept, Laboratory lab) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setDepartment(dept);
            user.setLaboratory(lab);
            log.info("Zaktualizowano lokalizację użytkownika ID: {} (Dział: {}, Pracownia: {})", 
                    userId, (dept != null ? dept.getName() : "brak"), (lab != null ? lab.getName() : "brak"));
        });
    }
}
```

---

## Krok 2: UserPasswordService

```java
package com.mac.bry.desktop.security.service;

import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.security.model.UserPasswordHistory;
import com.mac.bry.desktop.security.repository.UserPasswordHistoryRepository;
import com.mac.bry.desktop.security.repository.UserRepository;
import com.mac.bry.desktop.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserPasswordService {

    private final UserRepository userRepository;
    private final UserPasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Transactional
    public void changeUserPassword(Long userId, String rawPassword) {
        userRepository.findById(userId).ifPresent(user -> {
            if (isPasswordInHistory(userId, rawPassword)) {
                throw new RuntimeException("Hasło zostało już użyte w przeszłości (wymagana historia 5 haseł).");
            }
            
            updatePasswordWithAging(user, rawPassword);
            log.info("Zmieniono hasło dla użytkownika {} (ID: {}) po wymuszeniu", user.getUsername(), userId);
        });
    }

    @Transactional
    public boolean changePasswordWithOld(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId).orElseThrow();
        
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            log.warn("Nieudana próba zmiany hasła dla użytkownika ID: {} (błędne stare hasło)", userId);
            return false;
        }
        
        if (isPasswordInHistory(userId, newPassword)) {
            throw new RuntimeException("Nowe hasło zostało już użyte w przeszłości (wymagana historia 5 haseł).");
        }
        
        updatePasswordWithAging(user, newPassword);
        log.info("Użytkownik ID: {} poprawnie zmienił swoje hasło.", userId);
        return true;
    }

    @Transactional
    public boolean resetPassword(String email) {
        return userRepository.findByEmail(email).map(user -> {
            String tempPassword = UUID.randomUUID().toString().substring(0, 8);
            
            user.setPassword(passwordEncoder.encode(tempPassword));
            user.setMustChangePassword(true);
            
            LocalDateTime now = LocalDateTime.now();
            user.setPasswordChangedAt(now);
            user.setPasswordExpiresAt(now.plusDays(user.getPasswordExpiryDays() != null ? user.getPasswordExpiryDays() : 90));

            passwordHistoryRepository.save(new UserPasswordHistory(user, user.getPassword()));
            emailService.sendPasswordResetEmail(user.getEmail(), tempPassword);
            
            log.info("Resetowano hasło dla użytkownika: {}", user.getUsername());
            return true;
        }).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isPasswordInHistory(Long userId, String rawPassword) {
        List<UserPasswordHistory> history = passwordHistoryRepository.findTop5ByUserIdOrderByCreatedDateDesc(userId);
        return history.stream()
                .anyMatch(h -> passwordEncoder.matches(rawPassword, h.getPasswordHash()));
    }

    // Helper method
    private void updatePasswordWithAging(User user, String rawPassword) {
        String encoded = passwordEncoder.encode(rawPassword);
        user.setPassword(encoded);
        user.setMustChangePassword(false);
        
        LocalDateTime now = LocalDateTime.now();
        user.setPasswordChangedAt(now);
        user.setPasswordExpiresAt(now.plusDays(user.getPasswordExpiryDays() != null ? user.getPasswordExpiryDays() : 90));

        passwordHistoryRepository.save(new UserPasswordHistory(user, encoded));
    }
}
```

---

## Krok 3: UserAuthenticationService

```java
package com.mac.bry.desktop.security.service;

import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAuthenticationService {

    private final UserRepository userRepository;

    @Value("${app.security.inactivity-timeout-minutes:15}")
    private int inactivityTimeoutMinutes;

    @Value("${app.security.account-expiration-days:90}")
    private int accountExpirationDays;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;
    private static final int SESSION_TIMEOUT_MINUTES = 30;

    @Transactional
    public void incrementFailedLoginAttempts(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            int newAttempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(newAttempts);
            log.warn("Nieudane logowanie dla {}: próba {} z {}", username, newAttempts, MAX_FAILED_ATTEMPTS);
            
            if (newAttempts >= MAX_FAILED_ATTEMPTS) {
                user.setLocked(true);
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
                log.warn("KONTO ZABLOKOWANE: {} na {} minut po {} nieudanych próbach", 
                         username, LOCK_DURATION_MINUTES, MAX_FAILED_ATTEMPTS);
            }
        });
    }

    @Transactional
    public void resetFailedLoginAttempts(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            if (user.getFailedLoginAttempts() > 0) {
                user.setFailedLoginAttempts(0);
                user.setLocked(false);
                user.setLockedUntil(null);
            }
        });
    }

    @Transactional
    public String registerSession(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        String token = UUID.randomUUID().toString();
        user.setSessionToken(token);
        user.setLastActivity(LocalDateTime.now());
        user.setLastLogin(LocalDateTime.now());
        return token;
    }

    @Transactional
    public void updateLastLogin(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastLogin(LocalDateTime.now());
        });
    }

    @Transactional
    public void clearSession(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setSessionToken(null);
            user.setLastActivity(null);
        });
    }

    @Transactional
    public void updateActivity(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastActivity(LocalDateTime.now());
        });
    }

    @Transactional(readOnly = true)
    public boolean isAlreadyLoggedIn(String username) {
        // Tymczasowo wyłączone dla celów deweloperskich
        return false;
    }

    @Transactional
    public void checkAccountExpiration(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            if (user.getLastLogin() != null) {
                LocalDateTime expirationThreshold = LocalDateTime.now().minusDays(accountExpirationDays);
                if (user.getLastLogin().isBefore(expirationThreshold)) {
                    log.warn("KONTO WYGASŁO: {} nie logował się od {}. Blokada konta.", username, user.getLastLogin());
                    user.setEnabled(false);
                    user.setLocked(true);
                }
            }
        });
    }
}
```

---

## Krok 4: UserAccountService

```java
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

    public boolean hasRole(User user, String roleName) {
        return user.getRoles().stream()
                .anyMatch(r -> r.getName().equals(roleName));
    }

    @Transactional(readOnly = true)
    public List<String> getSuperAdminEmails() {
        return userRepository.findByRoleName("ROLE_SUPER_ADMIN").stream()
                .map(User::getEmail)
                .collect(java.util.stream.Collectors.toList());
    }
}
```

---

## Krok 5: Refactored UserService (Facade Pattern)

```java
package com.mac.bry.desktop.security.service;

import com.mac.bry.desktop.security.model.Department;
import com.mac.bry.desktop.security.model.Laboratory;
import com.mac.bry.desktop.security.model.Role;
import com.mac.bry.desktop.security.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Facade service for backward compatibility.
 * Delegates to specific service implementations.
 * 
 * Consider using specific services directly in new code:
 * - UserManagementService for user CRUD and profile
 * - UserPasswordService for password operations
 * - UserAuthenticationService for login/session management
 * - UserAccountService for account operations and roles
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserManagementService userManagementService;
    private final UserPasswordService userPasswordService;
    private final UserAuthenticationService userAuthenticationService;
    private final UserAccountService userAccountService;

    // Delegations to UserManagementService
    public List<User> getAllUsers() {
        return userManagementService.getAllUsers();
    }

    public List<User> getAllUsersByDepartment(Long departmentId) {
        return userManagementService.getAllUsersByDepartment(departmentId);
    }

    public void updateUserProfile(Long userId, String firstName, String lastName, String email, String phone) {
        userManagementService.updateUserProfile(userId, firstName, lastName, email, phone);
    }

    public void updateUserLocation(Long userId, Department dept, Laboratory lab) {
        userManagementService.updateUserLocation(userId, dept, lab);
    }

    // Delegations to UserPasswordService
    public void changeUserPassword(Long userId, String rawPassword) {
        userPasswordService.changeUserPassword(userId, rawPassword);
    }

    public void changePasswordWithOld(Long userId, String oldPassword, String newPassword) {
        userPasswordService.changePasswordWithOld(userId, oldPassword, newPassword);
    }

    public boolean resetPassword(String email) {
        return userPasswordService.resetPassword(email);
    }

    public boolean isPasswordInHistory(Long userId, String rawPassword) {
        return userPasswordService.isPasswordInHistory(userId, rawPassword);
    }

    // Delegations to UserAuthenticationService
    public void incrementFailedLoginAttempts(String username) {
        userAuthenticationService.incrementFailedLoginAttempts(username);
    }

    public void resetFailedLoginAttempts(Long userId) {
        userAuthenticationService.resetFailedLoginAttempts(userId);
    }

    public String registerSession(Long userId) {
        return userAuthenticationService.registerSession(userId);
    }

    public void updateLastLogin(Long userId) {
        userAuthenticationService.updateLastLogin(userId);
    }

    public void clearSession(Long userId) {
        userAuthenticationService.clearSession(userId);
    }

    public void updateActivity(Long userId) {
        userAuthenticationService.updateActivity(userId);
    }

    public boolean isAlreadyLoggedIn(String username) {
        return userAuthenticationService.isAlreadyLoggedIn(username);
    }

    public void checkAccountExpiration(String username) {
        userAuthenticationService.checkAccountExpiration(username);
    }

    // Delegations to UserAccountService
    public List<Role> getAllRoles() {
        return userAccountService.getAllRoles();
    }

    public void activateUser(Long userId) {
        userAccountService.activateUser(userId);
    }

    public void deactivateUser(Long userId) {
        userAccountService.deactivateUser(userId);
    }

    public void lockUser(Long userId) {
        userAccountService.lockUser(userId);
    }

    public void unlockUser(Long userId) {
        userAccountService.unlockUser(userId);
    }

    public void setMustChangePassword(Long userId, boolean mustChange) {
        userAccountService.setMustChangePassword(userId, mustChange);
    }

    public void updateUserRoles(Long userId, Set<Role> newRoles) {
        userAccountService.updateUserRoles(userId, newRoles);
    }

    public boolean hasRole(User user, String roleName) {
        return userAccountService.hasRole(user, roleName);
    }

    public List<String> getSuperAdminEmails() {
        return userAccountService.getSuperAdminEmails();
    }
}
```

---

## Testy Jednostkowe

Utworzysz nowe testy dla każdego serwisu:

```java
@ExtendWith(MockitoExtension.class)
class UserPasswordServiceTest {

    @Mock
    private UserRepository userRepository;
    
    @Mock
    private UserPasswordHistoryRepository passwordHistoryRepository;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserPasswordService userPasswordService;

    @Test
    void shouldChangePassword_WhenValid() {
        // Given
        User user = new User();
        user.setId(1L);
        user.setUsername("test");
        user.setPassword("oldEnc");
        
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordHistoryRepository.findTop5ByUserIdOrderByCreatedDateDesc(1L)).thenReturn(new ArrayList<>());
        when(passwordEncoder.encode("newPass")).thenReturn("newEnc");

        // When
        userPasswordService.changeUserPassword(1L, "newPass");

        // Then
        assertEquals("newEnc", user.getPassword());
        verify(passwordHistoryRepository).save(any(UserPasswordHistory.class));
    }
}
```

---

## Checklistę Implementacji

- [ ] Stworzyć UserManagementService
- [ ] Stworzyć UserPasswordService
- [ ] Stworzyć UserAuthenticationService
- [ ] Stworzyć UserAccountService
- [ ] Refactoryzować istniejący UserService na Facade
- [ ] Zaktualizować dependency injection w użytkowniach UserService
- [ ] Stworzyć unit testy dla każdego nowego serwisu
- [ ] Uruchomić testy integracyjne
- [ ] Zweryfikować, że stare kody działają poprzez facade
- [ ] Dokumentacja dla przyszłych kodów - zachęcić używanie specific services

