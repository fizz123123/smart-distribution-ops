package com.smartdistribution.identity.repository;

import com.smartdistribution.identity.entity.PasswordToken;
import com.smartdistribution.identity.entity.PasswordTokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasswordTokenRepository extends JpaRepository<PasswordToken, Long> {

    Optional<PasswordToken> findByTokenHash(String tokenHash);

    //同一個人、同一個用途還沒用過的 token；發新的之前先把它們作廢
    List<PasswordToken> findAllByUserIdAndPurposeAndUsedAtIsNull(Long userId, PasswordTokenPurpose purpose);
}
