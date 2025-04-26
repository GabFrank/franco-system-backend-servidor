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

    public List<RetiroDetalle> findByRetiroIdAndMonedaIdAndSucursalId(Long id, Long monedaId, Long sucId);

    public List<RetiroDetalle> findByRetiroCajaSalidaIdAndSucursalId(Long id, Long sucId);

//    Moneda findByPaisId(Long id);

}