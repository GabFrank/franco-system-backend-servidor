package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CambioRepository extends HelperRepository<Cambio, Long> {

    default Class<Cambio> getEntityClass() {
        return Cambio.class;
    }

    @Query(value = "select * from financiero.cambio c " +
            "where c.moneda_id = ?1 order by creado_en desc limit 1", nativeQuery = true)
    public Cambio findLastByCambioId(Long id);

    /**
     * La cotizacion cargada por el comercio vigente en un instante: la ultima fila no desactivada con
     * creado_en hasta ese instante y valor_en_gs positivo. No mira las columnas de mercado.
     */
    @Query(value = "select * from financiero.cambio c " +
            "where c.moneda_id = ?1 and c.activo is not false and c.creado_en <= ?2 " +
            "and c.valor_en_gs > 0 order by c.creado_en desc, c.id desc limit 1", nativeQuery = true)
    Cambio findVigenteDelComercio(Long monedaId, java.time.LocalDateTime instante);

    @Query("select e from Cambio e " +
            " where cast(e.creadoEn as date) = cast(?1 as date) and ?2 = ?1" +
            "or cast(e.creadoEn as date) >= cast(?1 as date) AND cast(e.creadoEn as date) <= cast(?2 as date)")
    public List<Cambio> findByDate(String start, String end);

}