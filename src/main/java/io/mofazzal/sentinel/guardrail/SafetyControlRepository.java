package io.mofazzal.sentinel.guardrail;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface SafetyControlRepository extends JpaRepository<SafetyControl, Short> {

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select control from SafetyControl control where control.id = 1")
    Optional<SafetyControl> findForUpdate();
}
