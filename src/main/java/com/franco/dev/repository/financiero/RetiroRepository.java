package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.enums.TipoCliente;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RetiroRepository extends HelperRepository<Retiro, EmbebedPrimaryKey> {

    default Class<Retiro> getEntityClass() {
        return Retiro.class;
    }

//    @Query("select m from Retiro m " +
//            "where UPPER(CAST(id as text)) like %?1% or UPPER(nombre) like %?1% or UPPER(codigo) like %?1%")
//    public List<Retiro> findByAll(String texto);

//    Moneda findByPaisId(Long id);

    public List<Retiro> findByCajaSalidaId(Long id);

    @Query("select r from Retiro r " +
            "left join r.cajaSalida ca " +
            "left join r.responsable res " +
            "left join r.usuario u " +
            "where " +
            "(r.id = :id or :id is null) and " +
            "(ca.id = :cajaId or :cajaId is null) and " +
            "(r.sucursalId = :sucId or :sucId is null) and " +
            "(res.id = :responsableId or :responsableId is null) and " +
            "(u.id = :cajeroId or :cajeroId is null) " +
            "order by r.id desc")
    List<Retiro> findByAll(Long id, Long cajaId, Long sucId, Long responsableId, Long cajeroId, Pageable pageable);

}