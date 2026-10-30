package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MovimientoCajaVirtualRepository extends JpaRepository<MovimientoCajaVirtual, Long> {

    /**
     * Lee el movimiento bloqueándolo: dos anulaciones simultáneas del mismo movimiento se
     * serializan, y la segunda lo relee ya inactivo. Tiene que ser la primera carga de la fila
     * en la transacción.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MovimientoCajaVirtual m where m.id = :id")
    Optional<MovimientoCajaVirtual> lockById(@Param("id") Long id);

    /**
     * Solo si sigue activo, leido de la base: si el movimiento ya estaba en el contexto de persistencia,
     * {@link #lockById} espera el lock pero devuelve esa instancia sin refrescar (estado viejo). Se llama
     * <b>despues</b> del lock; antes, leeria lo mismo que la carrera que se quiere cortar.
     */
    @Query("select coalesce(m.activo, true) from MovimientoCajaVirtual m where m.id = :id")
    Optional<Boolean> findActivoById(@Param("id") Long id);

    /**
     * Ids de los ingresos <b>activos</b> del cierre de un maletín en una moneda: la marca de que ese cierre
     * ya entró a la caja mayor. El cierre es la caja de PDV (id + sucursal: su clave es compuesta). Los
     * ingresos de maletín hechos a mano no llevan sucursal de origen y no aparecen acá.
     */
    @Query("select m.id from MovimientoCajaVirtual m "
            + "where m.origenTipo = com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo.MALETIN "
            + "and m.tipoMovimiento = com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento.INGRESO "
            + "and m.origenId = :maletinId and m.referenciaId = :cajaId and m.origenSucursalId = :sucursalId "
            + "and m.moneda.id = :monedaId and coalesce(m.activo, true) = true order by m.id")
    java.util.List<Long> findIngresosDeCierreDeMaletin(@Param("maletinId") Long maletinId, @Param("cajaId") Long cajaId,
                                                       @Param("sucursalId") Long sucursalId, @Param("monedaId") Long monedaId);

    /** Los datos de vínculo de un movimiento, sin cargar la entidad (ver {@link MovimientoCajaVirtualVinculo}). */
    @Query("select new com.franco.dev.repository.financiero.MovimientoCajaVirtualVinculo("
            + "m.id, m.tipoMovimiento, m.origenTipo, cv.id, co.id, cd.id, mo.id, m.cantidad, m.referenciaId, m.creadoEn) "
            + "from MovimientoCajaVirtual m join m.cajaVirtual cv left join m.cajaOrigen co "
            + "left join m.cajaDestino cd left join m.moneda mo where m.id = :id")
    Optional<MovimientoCajaVirtualVinculo> findVinculoById(@Param("id") Long id);

    /** ¿Existe el contra-movimiento de este movimiento? Un inactivo sin contra es un dato roto, no una anulación. */
    boolean existsByOrigenTipoAndOrigenId(OrigenMovimientoTipo origenTipo, Long origenId);

    Page<MovimientoCajaVirtual> findByCajaVirtualIdOrderByCreadoEnDesc(Long cajaVirtualId, Pageable pageable);

    Page<MovimientoCajaVirtual> findByCajaVirtualIdAndCreadoEnBetweenOrderByCreadoEnDesc(
            Long cajaVirtualId, LocalDateTime inicio, LocalDateTime fin, Pageable pageable);

    List<MovimientoCajaVirtual> findByCajaVirtualIdAndActivoTrue(Long cajaVirtualId);

    /**
     * Patas de caja activas de una operación dueña (para revertir todas al anularla), por caja ascendente:
     * el mismo orden en que se postearon, para que dos anulaciones no tomen los saldos cruzados.
     */
    List<MovimientoCajaVirtual> findByOrigenTipoAndOrigenIdAndActivoTrueOrderByCajaVirtualIdAscIdAsc(
            OrigenMovimientoTipo origenTipo, Long origenId);

    /**
     * Igual que el anterior pero acotado a la sucursal del documento de origen.
     *
     * <p>Hace falta cuando el origen tiene PK compuesta: el id de un Retiro no es global —cada
     * filial numera desde 1— y una caja mayor recibe retiros de varias sucursales, así que
     * buscar solo por (origenTipo, origenId) devuelve movimientos de retiros ajenos que
     * casualmente comparten el número.</p>
     */
    List<MovimientoCajaVirtual> findByOrigenTipoAndOrigenIdAndOrigenSucursalIdAndActivoTrue(
            OrigenMovimientoTipo origenTipo, Long origenId, Long origenSucursalId);

    /**
     * Filtro combinado de movimientos (todos opcionales salvo la caja). soloActivos=true oculta anulados.
     * {@code tipo} llega como String (name del enum) y se compara contra la columna casteada a texto:
     * el enum es nativo de Postgres y un bind param nulo de enum rompe con 42P18. Castear a texto lo evita.
     */
    String FILTER_JPQL = "select m from MovimientoCajaVirtual m where m.cajaVirtual.id = :cajaId "
            + "and (cast(:desde as timestamp) is null or m.creadoEn >= :desde) "
            + "and (cast(:fin as timestamp) is null or m.creadoEn <= :fin) "
            + "and (:tipo is null or cast(m.tipoMovimiento as string) = :tipo) "
            + "and (:monedaId is null or m.moneda.id = :monedaId) "
            + "and (:soloActivos = false or m.activo = true) "
            + "order by m.creadoEn desc";

    @Query(FILTER_JPQL)
    Page<MovimientoCajaVirtual> filter(@Param("cajaId") Long cajaId,
                                       @Param("desde") LocalDateTime desde,
                                       @Param("fin") LocalDateTime fin,
                                       @Param("tipo") String tipo,
                                       @Param("monedaId") Long monedaId,
                                       @Param("soloActivos") boolean soloActivos,
                                       Pageable pageable);

    /** Mismo filtro sin paginar: el reporte imprime todo lo que la lista muestra en páginas. */
    @Query(FILTER_JPQL)
    List<MovimientoCajaVirtual> filterList(@Param("cajaId") Long cajaId,
                                           @Param("desde") LocalDateTime desde,
                                           @Param("fin") LocalDateTime fin,
                                           @Param("tipo") String tipo,
                                           @Param("monedaId") Long monedaId,
                                           @Param("soloActivos") boolean soloActivos);

    // ─────────────── Vueltos de egresos ───────────────

    /** Lo ya devuelto de un egreso, en su moneda: suma de sus vueltos activos (un activo nulo cuenta como activo). */
    @Query("select coalesce(sum(m.vueltoEquivalente), 0) from MovimientoCajaVirtual m "
            + "where m.vueltoDeId = :egresoId and (m.activo is null or m.activo = true)")
    java.math.BigDecimal sumVueltoActivo(@Param("egresoId") Long egresoId);

    @Query("select count(m) > 0 from MovimientoCajaVirtual m "
            + "where m.vueltoDeId = :egresoId and (m.activo is null or m.activo = true)")
    boolean existsVueltoActivo(@Param("egresoId") Long egresoId);

    /** Fila de la lista de egresos con vuelto pendiente: el egreso y lo ya devuelto (en su moneda). */
    interface EgresoParaVueltoFila {
        Long getId();
        java.math.BigDecimal getDevuelto();
    }

    String EGRESOS_PARA_VUELTO_FROM = " FROM financiero.movimiento_caja_virtual m "
            + "LEFT JOIN (SELECT vuelto_de_id, SUM(vuelto_equivalente) AS devuelto "
            + "           FROM financiero.movimiento_caja_virtual "
            + "           WHERE vuelto_de_id IS NOT NULL AND activo IS NOT FALSE GROUP BY vuelto_de_id) v "
            + "       ON v.vuelto_de_id = m.id "
            + "LEFT JOIN personas.funcionario f ON f.id = m.responsable_id "
            + "LEFT JOIN personas.persona p ON p.id = f.persona_id "
            + "WHERE m.caja_virtual_id = :cajaId AND m.activo IS NOT FALSE "
            // Los egresos que se rinden: efectivo (MANUAL o de antes de origen_tipo), vario y gasto. El maletín no:
            // la plata viaja adentro y vuelve con el ingreso del maletín, no como vuelto.
            + "  AND ((CAST(m.tipo_movimiento AS text) = 'EGRESO' "
            + "        AND (m.origen_tipo IS NULL OR m.origen_tipo IN ('MANUAL', 'ENTRADA_VARIA'))) "
            + "    OR (CAST(m.tipo_movimiento AS text) = 'PAGO_PROVEEDOR' AND m.origen_tipo = 'GASTO')) "
            + "  AND m.cantidad - COALESCE(v.devuelto, 0) > 0.005 "
            + "  AND (CAST(:texto AS text) IS NULL OR UPPER(m.descripcion) LIKE CAST(:texto AS text) "
            + "       OR UPPER(p.nombre) LIKE CAST(:texto AS text)) ";

    /** Egresos de la caja con algo pendiente de devolver, del más nuevo al más viejo. {@code texto}: patrón LIKE o null. */
    @Query(value = "SELECT m.id AS id, COALESCE(v.devuelto, 0) AS devuelto" + EGRESOS_PARA_VUELTO_FROM
            + "ORDER BY m.creado_en DESC, m.id DESC",
            countQuery = "SELECT COUNT(*)" + EGRESOS_PARA_VUELTO_FROM,
            nativeQuery = true)
    org.springframework.data.domain.Page<EgresoParaVueltoFila> findEgresosParaVuelto(
            @Param("cajaId") Long cajaId, @Param("texto") String texto,
            org.springframework.data.domain.Pageable pageable);
}
