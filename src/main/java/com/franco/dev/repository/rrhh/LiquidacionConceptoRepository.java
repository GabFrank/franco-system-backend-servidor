package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionConcepto;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

public interface LiquidacionConceptoRepository extends HelperRepository<LiquidacionConcepto, Long> {

    default Class<LiquidacionConcepto> getEntityClass() {
        return LiquidacionConcepto.class;
    }

    Optional<LiquidacionConcepto> findByCodigo(String codigo);

    /**
     * Conceptos que se pueden elegir al cargar un item a mano: activos y no calculados
     * por el motor. Los auto (SALARIO_BASE, IPS_DESCUENTO, PENALIZACION...) existen en el
     * catalogo solo para que el recibo resuelva su etiqueta, no para ofrecerlos en el
     * select -- cargarlos a mano duplicaria lo que la generacion ya arma.
     */
    List<LiquidacionConcepto> findByActivoTrueAndEsCalculadoAutoFalse(Sort sort);

    /** Activos con ese número de operación (a lo sumo uno, lo garantiza el índice único parcial). */
    List<LiquidacionConcepto> findByNumeroAndActivoTrue(Integer numero);
}
