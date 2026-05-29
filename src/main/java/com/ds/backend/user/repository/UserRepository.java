package com.ds.backend.user.repository;

import com.ds.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {
    
    @Query("SELECT u FROM User u WHERE u.version > :since ORDER BY u.version ASC")
    List<User> findByVersionGreaterThanOrderByVersionAsc(@Param("since") Long since);

    @Query("SELECT MAX(u.version) FROM User u")
    Optional<Long> findMaxVersion();
}
