package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.configuracion.Local;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface FacturaLegalRepository extends HelperRepository<FacturaLegal, EmbebedPrimaryKey> {

    default Class<FacturaLegal> getEntityClass() {
        return FacturaLegal.class;
    }

//    @Query("select m from Moneda m " +
//            "where UPPER(CAST(id as text)) like %?1% or UPPER(denominacion) like %?1%")
//    public List<Moneda> findByAll(String texto);

//    Moneda findByPaisId(Long id);

    public List<FacturaLegal> findByCajaId(Long id);

    @Query(value =  "select fl from FacturaLegal fl where " +
            "(fl.creadoEn between :inicio and :fin) " +
            "and (fl.sucursalId in :sucId) " +
            "and (:nombre is null or fl.nombre like :nombre) " +
            "and (:ruc is null or fl.ruc like :ruc) " +
            "order by fl.creadoEn")
    public List<FacturaLegal> findByCreadoEnBetweenAndSucursalId(LocalDateTime inicio, LocalDateTime fin, List<Long> sucId, String nombre, String ruc);

}