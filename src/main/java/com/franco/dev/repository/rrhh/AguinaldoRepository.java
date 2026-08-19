package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.Aguinaldo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AguinaldoRepository extends HelperRepository<Aguinaldo, Long> {

    default Class<Aguinaldo> getEntityClass() {
        return Aguinaldo.class;
    }

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    @Query("select a from Aguinaldo a where " +
            "(:anio is null or a.anio = :anio) and " +
            "(:funcionarioId is null or a.funcionario.id = :funcionarioId) " +
            "order by a.anio desc, a.id desc")
    Page<Aguinaldo> findPage(@Param("anio") Integer anio,
                             @Param("funcionarioId") Long funcionarioId,
                             Pageable pageable);

    List<Aguinaldo> findByAnioOrderByIdAsc(Integer anio);

    List<Aguinaldo> findByFuncionarioIdOrderByAnioDesc(Long funcionarioId);

    Optional<Aguinaldo> findByFuncionarioIdAndAnio(Long funcionarioId, Integer anio);

    /** Documento dueno de una obligacion de pago (puente tesoreria, V199.5). */
    Aguinaldo findBySolicitudPagoId(Long solicitudPagoId);

    /** Aguinaldos en un estado dado (pagables = APROBADO). */
    List<Aguinaldo> findByEstadoOrderByAnioDescIdDesc(com.franco.dev.domain.rrhh.enums.AguinaldoEstado estado);
}
