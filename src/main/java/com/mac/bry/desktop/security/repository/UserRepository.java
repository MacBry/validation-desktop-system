package com.mac.bry.desktop.security.repository;

import com.mac.bry.desktop.security.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    List<User> findByDepartmentId(Long departmentId);
    
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = :roleName")
    java.util.List<User> findByRoleName(@org.springframework.data.repository.query.Param("roleName") String roleName);
}
