package com.mac.bry.desktop.integration;

import com.mac.bry.desktop.dto.UserAuditDto;
import com.mac.bry.desktop.security.model.Role;
import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.security.repository.RoleRepository;
import com.mac.bry.desktop.security.repository.UserRepository;
import com.mac.bry.desktop.security.service.AuditService;
import com.mac.bry.desktop.security.service.UserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class UserAuditIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private Long testUserId;

    @BeforeEach
    void setUp() {
        // Czyścimy i przygotowujemy dane
        userRepository.deleteAll();
        User user = new User();
        user.setUsername("audit_test_user");
        user.setEmail("audit@test.com");
        user.setPassword("EncodedPassword123!");
        user.setEnabled(false);
        user = userRepository.save(user);
        testUserId = user.getId();
    }

    @Test
    @WithMockUser(username = "admin_tester")
    void shouldTrackUserFieldChangesInAudit() {
        // 1. Zmiana pola
        User user = userRepository.findById(testUserId).orElseThrow();
        user.setEnabled(true);
        user.setEmail("new_audit@test.com");
        userRepository.saveAndFlush(user);
        entityManager.clear();

        // 2. Pobranie historii
        List<UserAuditDto> history = auditService.getUserHistory(testUserId);

        // 3. Weryfikacja
        assertFalse(history.isEmpty(), "Historia nie powinna być pusta");
        
        UserAuditDto emailChange = history.stream()
                .filter(h -> "E-mail".equals(h.getFieldName()))
                .findFirst()
                .orElseThrow();

        assertEquals("audit@test.com", emailChange.getOldValue());
        assertEquals("new_audit@test.com", emailChange.getNewValue());
        assertEquals("admin_tester", emailChange.getModifiedBy());
    }

    @Test
    @WithMockUser(username = "super_admin")
    void shouldTrackRoleChangesInAudit() {
        // 1. Przygotowanie ról
        Role roleUser = roleRepository.findByName("ROLE_USER").orElseGet(() -> roleRepository.save(new Role("ROLE_USER", "Desc")));
        Role roleQa = roleRepository.findByName("ROLE_QA").orElseGet(() -> roleRepository.save(new Role("ROLE_QA", "Desc")));

        // 2. Nadanie ról
        Set<Role> roles = new HashSet<>();
        roles.add(roleUser);
        roles.add(roleQa);
        userService.updateUserRoles(testUserId, roles);

        // 3. Pobranie historii
        List<UserAuditDto> history = auditService.getUserHistory(testUserId);

        // 4. Weryfikacja
        UserAuditDto rolesChange = history.stream()
                .filter(h -> "Uprawnienia".equals(h.getFieldName()))
                .findFirst()
                .orElseThrow();

        assertTrue(rolesChange.getNewValue().contains("ROLE_QA"));
        assertTrue(rolesChange.getNewValue().contains("ROLE_USER"));
        assertEquals("super_admin", rolesChange.getModifiedBy());
    }
}
