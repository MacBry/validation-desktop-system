package com.mac.bry.desktop.security.repository;

import com.mac.bry.desktop.security.model.UserPasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserPasswordHistoryRepository extends JpaRepository<UserPasswordHistory, Long> {
    
    List<UserPasswordHistory> findTop5ByUserIdOrderByCreatedDateDesc(Long userId);
}
