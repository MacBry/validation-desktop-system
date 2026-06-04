package com.mac.bry.desktop.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DashboardSecurityServiceTest {

    private final DashboardSecurityService securityService = new DashboardSecurityService();
    private SecurityContext originalContext;

    @BeforeEach
    void setUp() {
        originalContext = SecurityContextHolder.getContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.setContext(originalContext);
    }

    @Test
    @DisplayName("should return false when authentication is null")
    void shouldReturnFalseWhenAuthIsNull() {
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(context);

        assertThat(securityService.isUserAdmin()).isFalse();
    }

    @Test
    @DisplayName("should return true when user is SUPER_ADMIN")
    void shouldReturnTrueWhenSuperAdmin() {
        SecurityContext context = mock(SecurityContext.class);
        Authentication auth = mock(Authentication.class);
        
        Collection<? extends GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        doReturn(authorities).when(auth).getAuthorities();
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        assertThat(securityService.isUserAdmin()).isTrue();
    }

    @Test
    @DisplayName("should return true when user is DEPT_ADMIN")
    void shouldReturnTrueWhenDeptAdmin() {
        SecurityContext context = mock(SecurityContext.class);
        Authentication auth = mock(Authentication.class);
        
        Collection<? extends GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_DEPT_ADMIN"));
        doReturn(authorities).when(auth).getAuthorities();
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        assertThat(securityService.isUserAdmin()).isTrue();
    }

    @Test
    @DisplayName("should return false when user is standard USER")
    void shouldReturnFalseWhenStandardUser() {
        SecurityContext context = mock(SecurityContext.class);
        Authentication auth = mock(Authentication.class);
        
        Collection<? extends GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        doReturn(authorities).when(auth).getAuthorities();
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        assertThat(securityService.isUserAdmin()).isFalse();
    }
}
