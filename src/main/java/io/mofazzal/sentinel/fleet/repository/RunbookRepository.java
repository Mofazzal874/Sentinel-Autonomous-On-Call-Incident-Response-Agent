package io.mofazzal.sentinel.fleet.repository;

import io.mofazzal.sentinel.fleet.domain.Runbook;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RunbookRepository extends JpaRepository<Runbook, UUID> {

    @Query("""
            select runbook
            from Runbook runbook
            where lower(runbook.title) like lower(concat('%', :symptom, '%'))
               or lower(runbook.symptomDescription) like lower(concat('%', :symptom, '%'))
            order by runbook.title asc
            """)
    List<Runbook> searchLexical(@Param("symptom") String symptom, Pageable page);
}
