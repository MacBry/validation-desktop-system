package com.mac.bry.desktop.integration;

import com.mac.bry.desktop.security.model.AccessLog;
import com.mac.bry.desktop.security.repository.AccessLogRepository;
import com.mac.bry.desktop.security.service.AuditService;
import com.mac.bry.desktop.security.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AccessLoggingIntegrationTest {

    @Autowired
    private AuditService auditService;

    @Autowired
    private AccessLogRepository accessLogRepository;

    @Test
    void shouldLogAccessEvent() {
        // given
        String username = "testuser";
        String action = "LOGIN_SUCCESS";
        String details = "Test login";

        // when
        auditService.logAccessEvent(username, action, details);

        // then
        List<AccessLog> logs = accessLogRepository.findByUsernameOrderByTimestampDesc(username);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo(action);
        assertThat(logs.get(0).getDetails()).isEqualTo(details);
    }
}
