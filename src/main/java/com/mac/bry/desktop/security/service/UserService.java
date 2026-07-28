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
 * Delegates to specific service implementations:
 * - UserManagementService for user CRUD and profile operations
 * - UserPasswordService for password operations
 * - UserAuthenticationService for login/session management
 * - UserAccountService for account operations and roles
 *
 * For new code, consider using specific services directly.
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

    public boolean changePasswordWithOld(Long userId, String oldPassword, String newPassword) {
        return userPasswordService.changePasswordWithOld(userId, oldPassword, newPassword);
    }

    public boolean initiatePasswordReset(String email) {
        return userPasswordService.initiatePasswordReset(email);
    }

    public UserPasswordService.PasswordResetResult resetPasswordWithToken(String email, String rawToken, String newPassword) {
        return userPasswordService.resetPasswordWithToken(email, rawToken, newPassword);
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

    public boolean isSessionValid(Long userId, String token) {
        return userAuthenticationService.isSessionValid(userId, token);
    }

    public boolean forceLogout(Long userId) {
        return userAuthenticationService.forceLogout(userId);
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
