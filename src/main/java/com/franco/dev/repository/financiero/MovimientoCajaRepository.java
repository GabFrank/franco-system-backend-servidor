package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MovimientoCajaRepository extends HelperRepository<MovimientoCaja, EmbebedPrimaryKey> {

    default Class<MovimientoCaja> getEntityClass() {
        return MovimientoCaja.class;
    }

//    @Query("select m from MovimientoCaja m " +
//            "where UPPER(CAST(id as text)) like %?1% or UPPER(nombre) like %?1% or UPPER(codigo) like %?1%")
//    public List<MovimientoCaja> findByAll(String texto);

//    Moneda findByPaisId(Long id);

    public List<MovimientoCaja> findByTipoMovimientoAndReferenciaAndSucursalId(PdvCajaTipoMovimiento tipoMovimiento, Long referencia, Long sucId);

    public List<MovimientoCaja> findByPdvCajaIdAndActivoAndSucursalId(Long id, Boolean activo, Long sucId);

    @Query(value = "select sum(mc.cantidad) as total from financiero.movimiento_caja mc " +
            "where mc.caja_id = ?1 and mc.tipo_movimiento = 'VENTA' and mc.moneda_id = ?2 and mc.id = ?3", nativeQuery = true)
    public Double findTotalVentaByCajaIdAndMonedaId(Long id, Long monedaId, Long sucId);

    @Query(value="select sum(mc.cantidad) from financiero.movimiento_caja mc " +
            "where mc.caja_id = ?1 and mc.moneda_id = ?2 and mc.sucursal_id = ?3", nativeQuery = true)
    public Double totalEnCajaByCajaIdandMonedaId(Long id, Long monedaId, Long sucId);

}