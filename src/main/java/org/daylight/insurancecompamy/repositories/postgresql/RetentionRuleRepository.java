package org.daylight.insurancecompamy.repositories.postgresql;

import org.daylight.insurancecompamy.entity.Dataset;
import org.daylight.insurancecompamy.entity.RetentionRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RetentionRuleRepository extends JpaRepository<RetentionRule, Long> {
    Optional<RetentionRule> findByCategory(String category);
}
