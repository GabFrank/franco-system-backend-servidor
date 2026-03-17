package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CajaVirtualRepository extends JpaRepository<CajaVirtual, Long> {

    List<CajaVirtual> findByTipo(CajaVirtualTipo tipo);

    List<CajaVirtual> findBySucursalId(Long sucursalId);

    List<CajaVirtual> findByActivoTrue();

    Page<CajaVirtual> findAll(Pageable pageable);
}
