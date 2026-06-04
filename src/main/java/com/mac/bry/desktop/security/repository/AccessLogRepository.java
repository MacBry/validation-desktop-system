package com.mac.bry.desktop.security.repository;

import com.mac.bry.desktop.security.model.AccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccessLogRepository extends JpaRepository<AccessLog, Long> {
    List<AccessLog> findByUsernameOrderByTimestampDesc(String username);
    List<AccessLog> findTop100ByOrderByTimestampDesc();
    List<AccessLog> findByActionOrderByTimestampDesc(String action);
    List<AccessLog> findByActionInOrderByTimestampDesc(List<String> actions);
}
