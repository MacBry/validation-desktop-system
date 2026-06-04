package com.mac.bry.desktop.security.service;

import com.mac.bry.desktop.security.model.Department;
import com.mac.bry.desktop.security.model.Laboratory;
import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
