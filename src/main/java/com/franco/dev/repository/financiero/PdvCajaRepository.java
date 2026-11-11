package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaEstado;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.repository.HelperRepository;
import com.franco.dev.repository.HelperRepositoryEmbeddedId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PdvCajaRepository extends HelperRepository<PdvCaja, Long>, JpaSpecificationExecutor<PdvCaja> {

    default Class<PdvCaja> getEntityClass() {
        return PdvCaja.class;
    }

//    @Query(value = "select * from financiero.pdv_caja ms " +
//            "where ms.sucursal_id = ?1 order by ms.id desc", nativeQuery = true)
    public List<PdvCaja> findBySucursalIdAndCreadoEnBetweenOrderByIdDesc(Long id, LocalDateTime inicio, LocalDateTime fin);

    public List<PdvCaja> findByCreadoEnBetween(LocalDateTime inicio, LocalDateTime fin);

//    @Query(value = "select * from financiero.pdv_caja pc \n" +
//            "where \n" +
//            "(:maletin_id is null or pc.maletin_id = :maletin_id) and \n" +
//            "(:cajero_id is null or pc.usuario_id = :cajero_id) and \n" +
//            "((:fecha_inicio is null and :fecha_fin is null) or pc.creado_en between cast(:fecha_inicio as timestamp) and cast(:fecha_fin as timestamp))\n")
//    public List<PdvCaja> findByAll();

    public List<PdvCaja> findByUsuarioIdAndActivo(Long id, Boolean activo);

    public PdvCaja findByUsuarioIdAndActivoAndSucursalId(Long id, Boolean activo, Long sucId);

    Optional<PdvCaja> findFirstByMaletinIdOrderByCreadoEnDesc(Long id);

    List<PdvCaja> findByUsuarioIdOrderByIdDesc(Long id, Pageable pageable);

    Boolean deleteByIdAndSucursalId(Long id, Long sucId);

    PdvCaja findByIdAndSucursalId(Long id, Long sucId);

    @Query(value = "select c from PdvCaja c " +
            "join c.maletin m " +
            "join c.usuario u " +
            "where c.sucursalId = :sucId and " +
            "(:cajaId is null or c.id = :cajaId) and " +
            "(:maletinId is null or m.id = :maletinId) and " +
            "(:cajeroId is null or u.id = :cajeroId) and " +
            "(:verificado is null or c.verificado = :verificado) and " +
            "(:activo is null or c.activo = :activo) and " +
            "((:cajaId is not null) or (cast(:fechaInicio as timestamp) is null or cast(:fechaFin as timestamp) is null) or c.creadoEn between :fechaInicio and :fechaFin) and " +
            // Turno: minuto del día en la zona del negocio (no la de la sesión), rotado para que el turno sea un rango (CajaTurnos.rango).
            // Sin turno (0..1440) no se evalúa: una caja con creado_en nulo sigue saliendo como antes.
            "((:desde = 0 and :hasta = 1440) or (" +
            "mod(cast(hour(function('timezone', :zona, c.creadoEn)) as integer) * 60 + cast(minute(function('timezone', :zona, c.creadoEn)) as integer) + :offset, 1440) >= :desde and " +
            "mod(cast(hour(function('timezone', :zona, c.creadoEn)) as integer) * 60 + cast(minute(function('timezone', :zona, c.creadoEn)) as integer) + :offset, 1440) < :hasta)) and " +
            "(c.estado = :estado or cast(:estado as com.franco.dev.domain.financiero.enums.PdvCajaEstado) is null) order by c.creadoEn desc")
    public Page<PdvCaja> findAllWithFilters(Long cajaId, PdvCajaEstado estado, Long maletinId, Long cajeroId, LocalDateTime fechaInicio, LocalDateTime fechaFin, Long sucId, Boolean verificado, Boolean activo, String zona, int offset, int desde, int hasta, Pageable pageable);

    List<PdvCaja> findBySucursalIdAndActivo(Long sucursalId, Boolean activo);

    /**
     * Los cajeros que hoy estan en caja en una sucursal.
     *
     * No alcanza con activo = true: esa bandera solo dice que la caja nunca se cerro, y en la base
     * hay cajas de 2022-2024 que quedaron asi. Una caja realmente abierta es la ultima de su
     * maletin -- si aparecio otra caja despues en el mismo maletin, la anterior se abandono.
     * Tampoco sirve el campo estado, que esta nulo en toda la tabla.
     *
     * Se excluyen las cajas sin fecha de apertura, que son filas que nunca llegaron a abrirse.
     */
    @Query("select distinct c.usuario from PdvCaja c " +
            "where c.sucursalId = ?1 " +
            "and c.activo = true " +
            "and c.fechaApertura is not null " +
            "and c.usuario is not null " +
            "and not exists (select 1 from PdvCaja c2 " +
            "                where c2.sucursalId = c.sucursalId " +
            "                and c2.maletin = c.maletin " +
            "                and c2.fechaApertura > c.fechaApertura)")
    List<Usuario> findCajerosConCajaAbiertaBySucursalId(Long sucursalId);

    @Query("SELECT DISTINCT v.caja FROM VentaObservacion vo JOIN vo.venta v WHERE v.caja IS NOT NULL")
    List<PdvCaja> findCajasWithVentaObservaciones();

    @Query("SELECT c FROM PdvCaja c WHERE c.maletin.id = :maletinId AND c.fechaCierre < :fechaApertura AND c.conteoCierre IS NOT NULL ORDER BY c.fechaCierre DESC, c.id DESC, c.sucursalId DESC")
    List<PdvCaja> findPreviousCajaByMaletinAndFecha(Long maletinId, LocalDateTime fechaApertura, Pageable pageable);

    /**
     * Analisis de diferencias de maletin (#609): estado de cada caja contra la anterior del mismo
     * maletin, filtrado y paginado en la base, con el total en cada fila. Misma regla que
     * PdvCajaService.enriquecerConCajaAnterior + estadoPorMoneda: anterior = misma maletin_id, cerrada
     * antes de la apertura de esta y con conteo de cierre, la mas reciente; moneda por moneda con
     * medio centavo de tolerancia. Los filtros llegan como texto: un nulo de Hibernate puede venir como
     * bytea, y CAST(bytea AS text) existe; CAST(bytea AS bigint) no.
     * Fila: id, sucursal_id, total.
     */
    @Query(value = "WITH base AS ( " +
            "  SELECT c.id, c.sucursal_id, c.maletin_id, c.creado_en, c.fecha_apertura, c.conteo_apertura_id " +
            "  FROM financiero.pdv_caja c LEFT JOIN financiero.maletin m ON m.id = c.maletin_id " +
            "  WHERE (CAST(:sucId AS text) IS NULL OR c.sucursal_id = CAST(CAST(:sucId AS text) AS bigint)) " +
            "    AND (CAST(:cajaId AS text) IS NULL OR c.id = CAST(CAST(:cajaId AS text) AS bigint)) " +
            "    AND (CAST(:estado AS text) IS NULL OR CAST(c.estado AS text) = CAST(:estado AS text)) " +
            "    AND (CAST(:maletinId AS text) IS NULL OR c.maletin_id = CAST(CAST(:maletinId AS text) AS bigint)) " +
            "    AND (CAST(:maletinDescripcion AS text) IS NULL OR lower(m.descripcion) LIKE CAST(:maletinDescripcion AS text)) " +
            "    AND (CAST(:cajeroId AS text) IS NULL OR c.usuario_id = CAST(CAST(:cajeroId AS text) AS bigint)) " +
            "    AND (CAST(:verificado AS text) IS NULL OR c.verificado = CAST(CAST(:verificado AS text) AS boolean)) " +
            "    AND (CAST(:inicio AS text) IS NULL OR CAST(:fin AS text) IS NULL " +
            "         OR c.creado_en BETWEEN CAST(CAST(:inicio AS text) AS timestamp) AND CAST(CAST(:fin AS text) AS timestamp)) " +
            "), est AS ( " +
            "  SELECT b.*, ant.id AS ant_id, " +
            "    CASE WHEN ant.id IS NULL THEN 'SIN_DATOS' " +
            "      WHEN EXISTS ( " +
            "        SELECT 1 FROM ( " +
            "          SELECT mo.denominacion AS d, cm.cantidad * mb.valor AS t " +
            "          FROM financiero.conteo_moneda cm " +
            "          JOIN financiero.moneda_billetes mb ON mb.id = cm.moneda_billetes_id " +
            "          JOIN financiero.moneda mo ON mo.id = mb.moneda_id " +
            "          WHERE cm.conteo_id = ant.conteo_cierre_id AND cm.sucursal_id = ant.sucursal_id " +
            "          UNION ALL " +
            "          SELECT mo.denominacion, -(cm.cantidad * mb.valor) " +
            "          FROM financiero.conteo_moneda cm " +
            "          JOIN financiero.moneda_billetes mb ON mb.id = cm.moneda_billetes_id " +
            "          JOIN financiero.moneda mo ON mo.id = mb.moneda_id " +
            "          WHERE cm.conteo_id = b.conteo_apertura_id AND cm.sucursal_id = b.sucursal_id " +
            "        ) x WHERE x.d IS NOT NULL AND x.t IS NOT NULL GROUP BY x.d HAVING abs(sum(x.t)) > 0.005 " +
            "      ) THEN 'CON_DIFERENCIA' ELSE 'SIN_DIFERENCIA' END AS estado_dif " +
            "  FROM base b " +
            "  LEFT JOIN LATERAL ( " +
            "    SELECT a.id, a.sucursal_id, a.conteo_cierre_id FROM financiero.pdv_caja a " +
            "    WHERE b.conteo_apertura_id IS NOT NULL AND b.fecha_apertura IS NOT NULL " +
            "      AND a.maletin_id = b.maletin_id AND a.fecha_cierre < b.fecha_apertura AND a.conteo_cierre_id IS NOT NULL " +
            "    ORDER BY a.fecha_cierre DESC, a.id DESC, a.sucursal_id DESC LIMIT 1 " +
            "  ) ant ON TRUE " +
            ") " +
            "SELECT e.id, e.sucursal_id, count(*) OVER () AS total FROM est e " +
            "WHERE (CAST(:cajaAnteriorId AS text) IS NULL OR e.ant_id = CAST(CAST(:cajaAnteriorId AS text) AS bigint)) " +
            "  AND (CAST(:difEstado AS text) IS NULL OR e.estado_dif = CAST(:difEstado AS text)) " +
            "ORDER BY e.maletin_id DESC, e.creado_en DESC, e.id DESC, e.sucursal_id DESC " +
            "LIMIT :limite OFFSET :desde",
            nativeQuery = true)
    List<Object[]> findAnalisisDiferencias(@Param("sucId") String sucId, @Param("cajaId") String cajaId,
                                           @Param("estado") String estado, @Param("maletinId") String maletinId,
                                           @Param("maletinDescripcion") String maletinDescripcion,
                                           @Param("cajeroId") String cajeroId, @Param("verificado") String verificado,
                                           @Param("inicio") String inicio, @Param("fin") String fin,
                                           @Param("cajaAnteriorId") String cajaAnteriorId,
                                           @Param("difEstado") String difEstado,
                                           @Param("limite") int limite, @Param("desde") long desde);

}