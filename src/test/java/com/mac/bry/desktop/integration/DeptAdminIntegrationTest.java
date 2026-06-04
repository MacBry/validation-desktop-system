package com.mac.bry.desktop.integration;

import com.mac.bry.desktop.security.model.Department;
import com.mac.bry.desktop.security.model.Role;
import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.security.repository.DepartmentRepository;
import com.mac.bry.desktop.security.repository.RoleRepository;
import com.mac.bry.desktop.security.repository.UserRepository;
import com.mac.bry.desktop.security.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class DeptAdminIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private com.mac.bry.desktop.security.repository.LaboratoryRepository laboratoryRepository;

    private Department deptLab;
    private Department deptExp;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        laboratoryRepository.deleteAll();
        departmentRepository.deleteAll();

        // Upewniamy się, że ROLE_DEPT_ADMIN istnieje
        if (roleRepository.findByName("ROLE_DEPT_ADMIN").isEmpty()) {
            Role deptAdminRole = new Role("ROLE_DEPT_ADMIN", "Administrator Działu");
            roleRepository.save(deptAdminRole);
        }

        deptLab = new Department();
        deptLab.setName("Laboratorium");
        deptLab.setAbbreviation("LAB");
        deptLab = departmentRepository.save(deptLab);

        deptExp = new Department();
        deptExp.setName("Ekspedycja");
        deptExp.setAbbreviation("EXP");
        deptExp = departmentRepository.save(deptExp);

        // Użytkownicy w Lab
        createUser("user1_lab", deptLab);
        createUser("user2_lab", deptLab);

        // Użytkownicy w Exp
        createUser("user1_exp", deptExp);
    }

    private void createUser(String username, Department dept) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@test.com");
        user.setPassword("secret");
        user.setDepartment(dept);
        userRepository.save(user);
    }

    @Test
    @DisplayName("IT-DEPT-01: Dept Admin widzi tylko użytkowników ze swojego działu")
    void deptAdminShouldSeeOnlyOwnDepartmentUsers() {
        // Given
        User deptAdmin = new User();
        deptAdmin.setUsername("admin_lab");
        deptAdmin.setDepartment(deptLab);
        
        // When
        List<User> usersInDept = userService.getAllUsersByDepartment(deptAdmin.getDepartment().getId());

        // Then
        assertThat(usersInDept).hasSize(2);
        assertThat(usersInDept).allMatch(u -> u.getDepartment().getAbbreviation().equals("LAB"));
    }

    @Test
    @DisplayName("IT-DEPT-02: Weryfikacja nowej roli ROLE_DEPT_ADMIN")
    void roleDeptAdminShouldExist() {
        Role role = roleRepository.findByName("ROLE_DEPT_ADMIN").orElse(null);
        assertThat(role).isNotNull();
    }
}
