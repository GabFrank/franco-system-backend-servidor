package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.Aguinaldo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import javax.persistence.LockModeType;
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

    /**
     * Toma el documento con lock pesimista antes de resolver su obligacion de pago.
     *
     * <p>Sin esto, dos pedidos de pago concurrentes sobre el mismo documento (doble click,
     * reintento de red) leen los dos {@code solicitud_pago_id == null}, crean una solicitud
     * cada uno y terminan pagando dos veces el mismo sueldo. El indice unico no lo impide:
     * evita que dos documentos compartan una solicitud, no que un documento reciba dos.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Aguinaldo e where e.id = :id")
    java.util.Optional<Aguinaldo> lockById(@org.springframework.data.repository.query.Param("id") Long id);
}
