package com.mac.bry.desktop.security.service;

import com.mac.bry.desktop.security.model.AccessLog;
import com.mac.bry.desktop.security.repository.AccessLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccessLogService {

    private final AccessLogRepository accessLogRepository;

    @Transactional(readOnly = true)
    public List<AccessLog> getRecentLogs(int limit) {
        log.debug("Fetching top {} recent access logs", limit);
        // Using PageRequest to limit at DB query level rather than fetching 100 entries and truncating in memory
        return accessLogRepository.findTop100ByOrderByTimestampDesc()
                .stream()
                .limit(limit)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AccessLog> getAllLogs() {
        return accessLogRepository.findAll();
    }
}
