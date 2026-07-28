package com.mac.bry.desktop.security.service;

import com.mac.bry.desktop.security.model.Role;
import com.mac.bry.desktop.security.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserManagementService userManagementService;
    @Mock private UserPasswordService userPasswordService;
    @Mock private UserAuthenticationService userAuthenticationService;
    @Mock private UserAccountService userAccountService;

    @InjectMocks private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword("hashedPassword");
        testUser.setEnabled(false);
        testUser.setLocked(false);
        testUser.setFirstName("Jan");
        testUser.setLastName("Kowalski");
        testUser.setFailedLoginAttempts(0);
    }

    // ===== 1.1 Zarządzanie kontami =====

    @Nested
    @DisplayName("UT-01..06: Zarządzanie Kontami")
    class AccountManagementTests {

        @Test
        @DisplayName("UT-01: getAllUsers() deleguje do UserManagementService")
        void shouldDelegateGetAllUsersToManagementService() {
            User user2 = new User(); user2.setId(2L);
            User user3 = new User(); user3.setId(3L);
            when(userManagementService.getAllUsers()).thenReturn(List.of(testUser, user2, user3));

            List<User> result = userService.getAllUsers();

            assertThat(result).hasSize(3);
            verify(userManagementService).getAllUsers();
        }

        @Test
        @DisplayName("UT-02: activateUser() deleguje do UserAccountService")
        void shouldDelegateActivateUserToAccountService() {
            userService.activateUser(1L);
            verify(userAccountService).activateUser(1L);
        }

        @Test
        @DisplayName("UT-03: deactivateUser() deleguje do UserAccountService")
        void shouldDelegateDeactivateUserToAccountService() {
            userService.deactivateUser(1L);
            verify(userAccountService).deactivateUser(1L);
        }

        @Test
        @DisplayName("UT-04: updateUserRoles() deleguje do UserAccountService")
        void shouldDelegateUpdateUserRolesToAccountService() {
            Role roleUser = new Role();
            roleUser.setId(2L);
            roleUser.setName("ROLE_USER");

            userService.updateUserRoles(1L, Set.of(roleUser));

            verify(userAccountService).updateUserRoles(1L, Set.of(roleUser));
        }

        @Test
        @DisplayName("UT-05: setMustChangePassword(true) deleguje do UserAccountService")
        void shouldDelegateSetMustChangePasswordTrueToAccountService() {
            userService.setMustChangePassword(1L, true);
            verify(userAccountService).setMustChangePassword(1L, true);
        }

        @Test
        @DisplayName("UT-06: setMustChangePassword(false) deleguje do UserAccountService")
        void shouldDelegateSetMustChangePasswordFalseToAccountService() {
            userService.setMustChangePassword(1L, false);
            verify(userAccountService).setMustChangePassword(1L, false);
        }
    }

    // ===== 1.2 Hasła i Bezpieczeństwo =====

    @Nested
    @DisplayName("UT-07..11: Hasła i Bezpieczeństwo")
    class PasswordSecurityTests {

        @Test
        @DisplayName("UT-07: changeUserPassword() deleguje do UserPasswordService")
        void shouldDelegateChangeUserPasswordToPasswordService() {
            userService.changeUserPassword(1L, "NewPassword1!");
            verify(userPasswordService).changeUserPassword(1L, "NewPassword1!");
        }

        @Test
        @DisplayName("UT-08: initiatePasswordReset() deleguje do UserPasswordService")
        void shouldDelegateInitiatePasswordResetToPasswordService() {
            when(userPasswordService.initiatePasswordReset("test@example.com")).thenReturn(true);

            boolean result = userService.initiatePasswordReset("test@example.com");

            assertThat(result).isTrue();
            verify(userPasswordService).initiatePasswordReset("test@example.com");
        }

        @Test
        @DisplayName("UT-09: initiatePasswordReset() zwraca false gdy delegacja zwraca false")
        void shouldReturnFalseWhenPasswordServiceReturnsFalse() {
            when(userPasswordService.initiatePasswordReset("unknown@example.com")).thenReturn(false);

            boolean result = userService.initiatePasswordReset("unknown@example.com");

            assertThat(result).isFalse();
            verify(userPasswordService).initiatePasswordReset("unknown@example.com");
        }

        @Test
        @DisplayName("UT-10: changePasswordWithOld() deleguje do UserPasswordService")
        void shouldDelegateChangePasswordWithOldToPasswordService() {
            when(userPasswordService.changePasswordWithOld(1L, "oldPass", "NewPass1!")).thenReturn(true);

            boolean result = userService.changePasswordWithOld(1L, "oldPass", "NewPass1!");

            assertThat(result).isTrue();
            verify(userPasswordService).changePasswordWithOld(1L, "oldPass", "NewPass1!");
        }

        @Test
        @DisplayName("UT-11: isPasswordInHistory() deleguje do UserPasswordService")
        void shouldDelegateIsPasswordInHistoryToPasswordService() {
            when(userPasswordService.isPasswordInHistory(1L, "oldPass")).thenReturn(true);

            boolean result = userService.isPasswordInHistory(1L, "oldPass");

            assertThat(result).isTrue();
            verify(userPasswordService).isPasswordInHistory(1L, "oldPass");
        }
    }

    // ===== 1.3 Blokada po nieudanych logowaniach =====

    @Nested
    @DisplayName("UT-12..16: Blokada Po Nieudanych Logowaniach")
    class AccountLockingTests {

        @Test
        @DisplayName("UT-12: incrementFailedLoginAttempts() deleguje do UserAuthenticationService")
        void shouldDelegateIncrementFailedLoginAttemptsToAuthService() {
            userService.incrementFailedLoginAttempts("testuser");
            verify(userAuthenticationService).incrementFailedLoginAttempts("testuser");
        }

        @Test
        @DisplayName("UT-13: resetFailedLoginAttempts() deleguje do UserAuthenticationService")
        void shouldDelegateResetFailedLoginAttemptsToAuthService() {
            userService.resetFailedLoginAttempts(1L);
            verify(userAuthenticationService).resetFailedLoginAttempts(1L);
        }

        @Test
        @DisplayName("UT-14: lockUser() deleguje do UserAccountService")
        void shouldDelegateLockUserToAccountService() {
            userService.lockUser(1L);
            verify(userAccountService).lockUser(1L);
        }

        @Test
        @DisplayName("UT-15: unlockUser() deleguje do UserAccountService")
        void shouldDelegateUnlockUserToAccountService() {
            userService.unlockUser(1L);
            verify(userAccountService).unlockUser(1L);
        }

        @Test
        @DisplayName("UT-16: registerSession() deleguje do UserAuthenticationService")
        void shouldDelegateRegisterSessionToAuthService() {
            when(userAuthenticationService.registerSession(1L)).thenReturn("token123");

            String result = userService.registerSession(1L);

            assertThat(result).isEqualTo("token123");
            verify(userAuthenticationService).registerSession(1L);
        }
    }

    // ===== 1.4 Profil i Session =====

    @Nested
    @DisplayName("UT-17..19: Profil i Session")
    class ProfileAndSessionTests {

        @Test
        @DisplayName("UT-17: updateUserProfile() deleguje do UserManagementService")
        void shouldDelegateUpdateUserProfileToManagementService() {
            userService.updateUserProfile(1L, "Anna", "Nowak", "anna@test.com", "123456789");
            verify(userManagementService).updateUserProfile(1L, "Anna", "Nowak", "anna@test.com", "123456789");
        }

        @Test
        @DisplayName("UT-18: updateLastLogin() deleguje do UserAuthenticationService")
        void shouldDelegateUpdateLastLoginToAuthService() {
            userService.updateLastLogin(1L);
            verify(userAuthenticationService).updateLastLogin(1L);
        }

        @Test
        @DisplayName("UT-19: getSuperAdminEmails() deleguje do UserAccountService")
        void shouldDelegateGetSuperAdminEmailsToAccountService() {
            List<String> emails = List.of("admin1@test.com", "admin2@test.com");
            when(userAccountService.getSuperAdminEmails()).thenReturn(emails);

            List<String> result = userService.getSuperAdminEmails();

            assertThat(result).containsExactlyInAnyOrder("admin1@test.com", "admin2@test.com");
            verify(userAccountService).getSuperAdminEmails();
        }

        @Test
        @DisplayName("UT-20: updateUserLocation() deleguje do UserManagementService")
        void shouldDelegateUpdateUserLocationToManagementService() {
            com.mac.bry.desktop.security.model.Department dept = new com.mac.bry.desktop.security.model.Department();
            com.mac.bry.desktop.security.model.Laboratory lab = new com.mac.bry.desktop.security.model.Laboratory();

            userService.updateUserLocation(1L, dept, lab);

            verify(userManagementService).updateUserLocation(1L, dept, lab);
        }

        @Test
        @DisplayName("UT-21: clearSession() deleguje do UserAuthenticationService")
        void shouldDelegateClearSessionToAuthService() {
            userService.clearSession(1L);
            verify(userAuthenticationService).clearSession(1L);
        }

        @Test
        @DisplayName("UT-22: updateActivity() deleguje do UserAuthenticationService")
        void shouldDelegateUpdateActivityToAuthService() {
            userService.updateActivity(1L);
            verify(userAuthenticationService).updateActivity(1L);
        }

        @Test
        @DisplayName("UT-23: getAllRoles() deleguje do UserAccountService")
        void shouldDelegateGetAllRolesToAccountService() {
            Role role1 = new Role();
            role1.setId(1L);
            role1.setName("ROLE_USER");
            when(userAccountService.getAllRoles()).thenReturn(List.of(role1));

            List<Role> result = userService.getAllRoles();

            assertThat(result).hasSize(1);
            verify(userAccountService).getAllRoles();
        }

        @Test
        @DisplayName("UT-24: hasRole() deleguje do UserAccountService")
        void shouldDelegateHasRoleToAccountService() {
            when(userAccountService.hasRole(testUser, "ROLE_ADMIN")).thenReturn(true);

            boolean result = userService.hasRole(testUser, "ROLE_ADMIN");

            assertThat(result).isTrue();
            verify(userAccountService).hasRole(testUser, "ROLE_ADMIN");
        }

        @Test
        @DisplayName("UT-25: checkAccountExpiration() deleguje do UserAuthenticationService")
        void shouldDelegateCheckAccountExpirationToAuthService() {
            userService.checkAccountExpiration("testuser");
            verify(userAuthenticationService).checkAccountExpiration("testuser");
        }

        @Test
        @DisplayName("UT-26: isAlreadyLoggedIn() deleguje do UserAuthenticationService")
        void shouldDelegateIsAlreadyLoggedInToAuthService() {
            when(userAuthenticationService.isAlreadyLoggedIn("testuser")).thenReturn(false);

            boolean result = userService.isAlreadyLoggedIn("testuser");

            assertThat(result).isFalse();
            verify(userAuthenticationService).isAlreadyLoggedIn("testuser");
        }

        @Test
        @DisplayName("UT-27: getAllUsersByDepartment() deleguje do UserManagementService")
        void shouldDelegateGetAllUsersByDepartmentToManagementService() {
            when(userManagementService.getAllUsersByDepartment(1L)).thenReturn(List.of(testUser));

            List<User> result = userService.getAllUsersByDepartment(1L);

            assertThat(result).hasSize(1);
            verify(userManagementService).getAllUsersByDepartment(1L);
        }
    }
}
