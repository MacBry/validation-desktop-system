package com.mac.bry.desktop.repository;

import com.mac.bry.desktop.model.UserVacation;
import com.mac.bry.desktop.security.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface UserVacationRepository extends JpaRepository<UserVacation, Long> {

    /**
     * Nieobecności operatora przecinające podany zakres dat (granice domknięte).
     * {@code user IS NULL} obejmuje nieobecności zdefiniowane globalnie.
     */
    @Query("select v from UserVacation v " +
           "where (:user is null or v.user = :user or v.user is null) " +
           "and v.startDate <= :rangeEnd and v.endDate >= :rangeStart " +
           "order by v.startDate asc")
    List<UserVacation> findOverlapping(@Param("user") User user,
                                       @Param("rangeStart") LocalDate rangeStart,
                                       @Param("rangeEnd") LocalDate rangeEnd);

    List<UserVacation> findByUnplannedL4TrueOrderByStartDateDesc();
}