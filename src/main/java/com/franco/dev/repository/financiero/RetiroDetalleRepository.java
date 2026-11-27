package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.RetiroDetalle;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface RetiroDetalleRepository extends HelperRepository<RetiroDetalle, EmbebedPrimaryKey> {

    default Class<RetiroDetalle> getEntityClass() {
        return RetiroDetalle.class;
    }

//    @Query("select m from RetiroDetalle m " +
//            "where UPPER(CAST(id as text)) like %?1% or UPPER(nombre) like %?1% or UPPER(codigo) like %?1%")
//    public List<RetiroDetalle> findByAll(String texto);

    public List<RetiroDetalle> findByRetiroIdAndSucursalId(Long id, Long sucId);

    /**
     * Lo declarado por el PDV, por retiro y moneda: {@code [retiroId, sucursalId, monedaId, suma]}. Una sola lectura
     * para toda la lista (la tabla no tiene índice por retiro_id). El id del retiro no es global: quien llama
     * filtra por la clave (retiro, sucursal).
     */
    @org.springframework.data.jpa.repository.Query("select d.retiro.id, d.sucursalId, d.moneda.id, sum(d.cantidad) "
            + "from RetiroDetalle d where d.retiro.id in :retiroIds group by d.retiro.id, d.sucursalId, d.moneda.id")
    List<Object[]> sumarPorMoneda(@org.springframework.data.repository.query.Param("retiroIds")
                                  java.util.Collection<Long> retiroIds);

    public List<RetiroDetalle> findByRetiroIdAndMonedaIdAndSucursalId(Long id, Long monedaId, Long sucId);

    public List<RetiroDetalle> findByRetiroCajaSalidaIdAndSucursalId(Long id, Long sucId);

//    Moneda findByPaisId(Long id);

}