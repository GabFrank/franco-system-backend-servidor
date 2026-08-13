package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.OperacionFinanciera;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperacionFinancieraRepository extends JpaRepository<OperacionFinanciera, Long> {
    Page<OperacionFinanciera> findAllByOrderByCreadoEnDesc(Pageable pageable);
}
