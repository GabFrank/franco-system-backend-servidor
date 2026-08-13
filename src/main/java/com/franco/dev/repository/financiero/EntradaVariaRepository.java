package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.EntradaVaria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntradaVariaRepository extends JpaRepository<EntradaVaria, Long> {
    Page<EntradaVaria> findByCajaVirtualIdOrderByCreadoEnDesc(Long cajaVirtualId, Pageable pageable);
}
