package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.enums.TipoCliente;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RetiroRepository extends HelperRepository<Retiro, EmbebedPrimaryKey> {

    default Class<Retiro> getEntityClass() {
        return Retiro.class;
    }

//    @Query("select m from Retiro m " +
//            "where UPPER(CAST(id as text)) like %?1% or UPPER(nombre) like %?1% or UPPER(codigo) like %?1%")
//    public List<Retiro> findByAll(String texto);

//    Moneda findByPaisId(Long id);

    public Retiro findByIdAndSucursalId(Long id, Long sucId);

    /**
     * Toma el retiro con lock pesimista para verificarlo.
     *
     * <p>Sin esto, dos tesoreros verificando el mismo retiro a la vez (o un doble click, o un
     * reintento de red) leen los dos que no hay verificación, cuentan cada uno lo suyo y
     * acreditan dos veces la misma plata en la caja mayor. El índice único sobre
     * {@code retiro_verificacion} es la red de abajo, pero falla con un error feo; el lock
     * serializa antes y el segundo ve la verificación que dejó el primero.</p>
     *
     * <p>Mismo patrón que {@code asegurarSolicitud} en RRHH, documentado ahí por la misma
     * razón: un doble click pagaba dos veces el mismo sueldo.</p>
     */
    @org.springframework.data.jpa.repository.Lock(javax.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from Retiro r where r.id = :id and r.sucursalId = :sucId")
    java.util.Optional<Retiro> lockByIdAndSucursalId(@org.springframework.data.repository.query.Param("id") Long id,
                                                     @org.springframework.data.repository.query.Param("sucId") Long sucId);

    public List<Retiro> findByCajaSalidaId(Long id);

    /** Retiros destinados a una caja mayor, aún no posteados y ya concluidos (poller de tesorería, F3). */
    @Query("select r from Retiro r where r.cajaVirtualId is not null and r.movimientoCajaVirtualId is null " +
            "and r.estado in (com.franco.dev.domain.financiero.enums.EstadoRetiro.CONCLUIDO, " +
            "com.franco.dev.domain.financiero.enums.EstadoRetiro.VERIFICADO_CONCLUIDO_SIN_PROBLEMA, " +
            "com.franco.dev.domain.financiero.enums.EstadoRetiro.VERIFICADO_CONCLUIDO_CON_PROBLEMA)")
    List<Retiro> findPendientesIngresoCajaMayor(Pageable pageable);

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
    Page<Retiro> findByAllPage(Long id, Long cajaId, Long sucId, Long responsableId, Long cajeroId, Pageable pageable);

    /**
     * Retiros "flotantes": replicados desde el PDV pero aún NO asignados a una caja mayor
     * ({@code cajaVirtualId IS NULL}) y sin postear ({@code movimientoCajaVirtualId IS NULL}).
     * Se excluyen los EN_PROCESO (retiro todavía abierto en el PDV). Filtrable por sucursal,
     * caja de salida (PdvCaja) y rango de fechas (creado_en).
     */
    @Query("select r from Retiro r " +
            "where r.cajaVirtualId is null and r.movimientoCajaVirtualId is null " +
            "and (r.estado is null or r.estado <> com.franco.dev.domain.financiero.enums.EstadoRetiro.EN_PROCESO) " +
            "and (:sucId is null or r.sucursalId = :sucId) " +
            "and (:cajaId is null or r.cajaSalidaId = :cajaId) " +
            "and (cast(:desde as timestamp) is null or r.creadoEn >= :desde) " +
            "and (cast(:hasta as timestamp) is null or r.creadoEn <= :hasta) " +
            "order by r.creadoEn desc")
    Page<Retiro> findFlotantes(@Param("sucId") Long sucId, @Param("cajaId") Long cajaId,
                               @Param("desde") java.time.LocalDateTime desde,
                               @Param("hasta") java.time.LocalDateTime hasta, Pageable pageable);

}