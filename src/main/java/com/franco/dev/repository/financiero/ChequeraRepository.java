package com.franco.dev.repository.financiero;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import javax.persistence.LockModeType;
import java.util.Optional;
import com.franco.dev.domain.financiero.Chequera;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ChequeraRepository extends HelperRepository<Chequera, Long> {
    default Class<Chequera> getEntityClass() {
        return Chequera.class;
    }

    @Query("select c from Chequera c " +
            "where UPPER(CAST(id as text)) like %?1% or UPPER(CAST(rangoDesde as text)) like %?1% or UPPER(CAST(rangoHasta as text)) like %?1%")
    public List<Chequera> findByAll(String texto);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Chequera e where e.id = :id")
    Optional<Chequera> lockById(@Param("id") Long id);

    /** La cuenta de una chequera, sin cargar la entidad (el lock que viene después tiene que ser su primera carga). */
    @Query("select c.cuentaBancaria.id from Chequera c where c.id = :id")
    Optional<Long> findCuentaIdById(@Param("id") Long id);

    /**
     * Chequeras no anuladas de la cuenta cuyo rango comparte algún número con {@code desde–hasta}.
     * {@code exceptoId}: la que se está editando, o nulo en un alta.
     */
    @Query("select c from Chequera c where c.cuentaBancaria.id = :cuentaId "
            + "and (c.estado is null or c.estado <> com.franco.dev.domain.financiero.enums.EstadoChequera.ANULADA) "
            + "and c.rangoDesde <= :hasta and c.rangoHasta >= :desde "
            + "and (:exceptoId is null or c.id <> :exceptoId) order by c.id")
    List<Chequera> findSuperpuestas(@Param("cuentaId") Long cuentaId, @Param("desde") Double desde,
                                    @Param("hasta") Double hasta, @Param("exceptoId") Long exceptoId);

    List<Chequera> findByEstadoOrderByIdDesc(com.franco.dev.domain.financiero.enums.EstadoChequera estado);

    List<Chequera> findByCuentaBancariaIdOrderByIdDesc(Long cuentaBancariaId);

    List<Chequera> findByCuentaBancariaIdAndEstadoOrderByIdDesc(Long cuentaBancariaId, com.franco.dev.domain.financiero.enums.EstadoChequera estado);

}
