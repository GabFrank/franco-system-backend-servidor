package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.Conteo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ConteoRepository extends HelperRepository<Conteo, EmbebedPrimaryKey> {

    default Class<Conteo> getEntityClass() {
        return Conteo.class;
    }

//    @Query("select m from Conteo m " +
//            "where UPPER(CAST(id as text)) like %?1% or UPPER(nombre) like %?1% or UPPER(codigo) like %?1%")
//    public List<Conteo> findByAll(String texto);

    @Query(value = "SELECT sum(cm.cantidad * mb.valor) FROM financiero.conteo c " +
            "join financiero.conteo_moneda cm on c.id = cm.conteo_id " +
            "join financiero.moneda_billetes mb on mb.id = cm.moneda_billetes_id " +
            "where c.id = ?1 and mb.moneda_id = ?2 and c.sucursal_id = ?3", nativeQuery = true)
    public Double getTotalPorMoneda(Long conteoId, Long monedaId, Long sucId);

    Conteo findByIdAndSucursalId(Long id, Long sucId);


}