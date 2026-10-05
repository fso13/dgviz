package io.github.dgviz.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserInviteRepository extends JpaRepository<UserInvite, Long> {

    @Query("""
            SELECT i FROM UserInvite i
            JOIN FETCH i.user
            WHERE i.token = :token
            """)
    Optional<UserInvite> findByToken(@Param("token") String token);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE UserInvite i
            SET i.usedAt = CURRENT_TIMESTAMP
            WHERE i.user.id = :userId AND i.usedAt IS NULL
            """)
    int invalidateOpenInvites(@Param("userId") Long userId);
}
