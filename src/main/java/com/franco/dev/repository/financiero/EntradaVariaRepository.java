package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.EntradaVaria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.Optional;

public interface EntradaVariaRepository extends JpaRepository<EntradaVaria, Long> {
    Page<EntradaVaria> findByCajaVirtualIdOrderByCreadoEnDesc(Long cajaVirtualId, Pageable pageable);

    /** Toma la entrada con lock pesimista: serializa dos anulaciones simultaneas de la misma. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EntradaVaria e where e.id = :id")
    Optional<EntradaVaria> lockById(@Param("id") Long id);

    /**
     * Solo si esta anulada, leido de la base: {@link #lockById} devuelve la instancia que ya estuviera
     * cargada, sin refrescar. Se llama <b>despues</b> del lock.
     */
    @Query("select coalesce(e.anulado, false) from EntradaVaria e where e.id = :id")
    Optional<Boolean> findAnuladoById(@Param("id") Long id);

    /**
     * ¿Otro documento no anulado ya tiene este comprobante? {@code numero} llega normalizado (sin espacios,
     * en mayúsculas); la columna se compara igual porque los datos anteriores no lo están. Ignora los
     * comprobantes nulos o vacíos, y un {@code anulado} nulo cuenta como no anulado.
     */
    @Query("select count(x) > 0 from EntradaVaria x where upper(trim(x.numeroComprobante)) = :numero "
            + "and coalesce(x.anulado, false) = false")
    boolean existeComprobante(@Param("numero") String numero);
}
