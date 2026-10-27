package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Lote;
import com.franco.dev.domain.operaciones.dto.AjusteStockLoteResultadoDto;
import com.franco.dev.domain.operaciones.dto.InfoPendienteLoteDto;
import com.franco.dev.domain.operaciones.dto.LoteEncontradoDto;
import com.franco.dev.domain.operaciones.dto.PendienteLoteDto;
import com.franco.dev.domain.operaciones.dto.PendienteLotePageDto;
import com.franco.dev.domain.operaciones.dto.ResolucionLotePendienteDto;
import com.franco.dev.domain.operaciones.dto.ResultadoBusquedaLoteDto;
import com.franco.dev.domain.operaciones.dto.StockLoteDto;
import com.franco.dev.domain.operaciones.enums.ModoAjusteLote;
import com.franco.dev.graphql.operaciones.input.AjusteStockLoteInput;
import com.franco.dev.repository.operaciones.LoteRepository;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

/**
 * Recepciones pendientes de lote (#429, fase 2, entrega 3).
 *
 * Pendiente = fila SIN LOTE (lote_id nulo) de movimiento_stock_lote cuyo movimiento es una COMPRA
 * (la recepcion cargada con «Cargar lote despues») y cuya cantidad no esta cubierta por
 * resoluciones vigentes en operaciones.lote_pendiente_resuelto.
 *
 * Completar NO escribe stock por su cuenta: usa el ajuste ATRIBUIR de la fase 1
 * ({@link AjusteStockLoteService}), que ya garantiza la invariante (movimiento AJUSTE 0 y dos
 * filas que se compensan) y la auditoria. Reabrir usa LIBERAR. Esta clase solo decide cuanto y
 * deja anotada la resolucion.
 *
 * El SIN LOTE de una sucursal es un solo pozo: lo que la recepcion dejo sin lote pudo venderse o
 * atribuirse a mano despues. Por eso nunca se atribuye mas que el SIN LOTE disponible.
 */
@Log
@Service
public class LotePendienteService {

    private static final double EPSILON = 0.0001;
    public static final int MAX_PAGINA = 100;

    @Autowired private NamedParameterJdbcTemplate jdbc;
    @Autowired private AjusteStockLoteService ajusteService;
    @Autowired private MovimientoStockLoteService movimientoStockLoteService;
    @Autowired private LoteFefoService loteFefoService;
    @Autowired private LoteRepository loteRepository;
    @Autowired private BusquedaLoteService busquedaLoteService;

    /** Minutos sin sincronizar a partir de los cuales no se deja completar. 0 = no se controla. */
    @Value("${lotes.pendientes.max-desfase-min:15}")
    private int maxDesfaseMinutos = 15;

    private static final String RESUELTO =
            "COALESCE((SELECT SUM(r.cantidad_atribuida + r.cantidad_no_atribuida) " +
            "          FROM operaciones.lote_pendiente_resuelto r " +
            "          WHERE r.msl_id = msl.id AND r.sucursal_id = msl.sucursal_id AND r.anulada = false), 0)";

    private static final String BASE =
            "FROM operaciones.movimiento_stock_lote msl " +
            "JOIN operaciones.movimiento_stock ms " +
            "  ON ms.id = msl.movimiento_stock_id AND ms.sucursal_id = msl.sucursal_id " +
            " AND CAST(ms.tipo_movimiento AS text) = 'COMPRA' " +
            "LEFT JOIN operaciones.recepcion_mercaderia_item rmi ON rmi.id = ms.referencia " +
            "LEFT JOIN operaciones.recepcion_mercaderia rm ON rm.id = rmi.recepcion_mercaderia_id " +
            "LEFT JOIN personas.proveedor pv ON pv.id = rm.proveedor_id " +
            "LEFT JOIN personas.persona pe ON pe.id = pv.persona_id " +
            "LEFT JOIN productos.producto p ON p.id = msl.producto_id " +
            "LEFT JOIN empresarial.sucursal s ON s.id = msl.sucursal_id " +
            "WHERE msl.lote_id IS NULL AND msl.estado = true AND msl.cantidad > 0 " +
            "  AND (CAST(:sucursalId AS bigint) IS NULL OR msl.sucursal_id = :sucursalId) " +
            "  AND (CAST(:mslId AS bigint) IS NULL OR msl.id = :mslId) " +
            "  AND (CAST(:desde AS timestamp) IS NULL OR msl.creado_en >= :desde) " +
            "  AND (CAST(:hasta AS timestamp) IS NULL OR msl.creado_en < :hasta) " +
            "  AND msl.cantidad - " + RESUELTO + " > " + EPSILON + " ";

    private static final String SELECT =
            "SELECT msl.id, msl.sucursal_id, s.nombre, msl.producto_id, p.descripcion, " +
            "       rmi.recepcion_mercaderia_id, rmi.id, pe.nombre, msl.creado_en, " +
            "       CAST(msl.cantidad AS double precision), CAST(" + RESUELTO + " AS double precision) ";

    // ---------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public PendienteLotePageDto listar(Long sucursalId, String desde, String hasta, Integer page, Integer size) {
        int pg = page != null && page >= 0 ? page : 0;
        int sz = size != null && size > 0 ? Math.min(size, MAX_PAGINA) : 20;
        MapSqlParameterSource p = params(sucursalId, null, aInicio(desde), aFinExclusivo(hasta));
        p.addValue("limite", sz).addValue("offset", (long) pg * sz);
        List<PendienteLoteDto> filas = jdbc.query(SELECT + BASE +
                "ORDER BY msl.creado_en DESC, msl.id DESC LIMIT :limite OFFSET :offset", p, (rs, n) -> map(rs));
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + BASE, p, Long.class);
        return new PendienteLotePageDto(filas, total, pg, sz);
    }

    @Transactional(readOnly = true)
    public Long contar(Long sucursalId) {
        return jdbc.queryForObject("SELECT COUNT(*) " + BASE, params(sucursalId, null, null, null), Long.class);
    }

    @Transactional(readOnly = true)
    public InfoPendienteLoteDto info(Long mslId, Long sucursalId, String numeroLote) {
        PendienteLoteDto pend = buscar(mslId, sucursalId);
        InfoPendienteLoteDto info = new InfoPendienteLoteDto();
        info.setPendiente(pend);
        double sinLote = saldoSinLote(pend.getProductoId(), sucursalId);
        info.setSaldoSinLote(sinLote);
        info.setAtribuidoManualDespues(atribuidoManualDespues(pend));
        info.setCantidadSugerida(Math.max(0.0, Math.min(pend.getRemanente(), sinLote)));
        LocalDateTime sync = ultimaSincronizacion(sucursalId);
        info.setUltimaSincronizacion(sync);
        info.setMaxDesfaseMinutos(maxDesfaseMinutos);
        info.setSincronizacionVencida(sincronizacionVencida(sync, maxDesfaseMinutos, LocalDateTime.now()));
        if (numeroLote != null && !numeroLote.trim().isEmpty()) {
            ResultadoBusquedaLoteDto r = busquedaLoteService.buscarLotePorNumero(numeroLote, pend.getProductoId());
            if (r != null && r.getLotes() != null) {
                for (LoteEncontradoDto l : r.getLotes()) {
                    if (Objects.equals(l.getProductoId(), pend.getProductoId())) {
                        info.getLotesParecidos().add(l);
                    }
                }
            }
        }
        return info;
    }

    @Transactional(readOnly = true)
    public List<ResolucionLotePendienteDto> resoluciones(Long sucursalId, Integer page, Integer size) {
        int pg = page != null && page >= 0 ? page : 0;
        int sz = size != null && size > 0 ? Math.min(size, MAX_PAGINA) : 20;
        MapSqlParameterSource p = new MapSqlParameterSource("sucursalId", sucursalId)
                .addValue("limite", sz).addValue("offset", (long) pg * sz);
        return jdbc.query(
                "SELECT r.id, r.msl_id, r.sucursal_id, msl.producto_id, pr.descripcion, r.lote_id, l.numero_lote, " +
                "       CAST(r.cantidad_atribuida AS double precision), CAST(r.cantidad_no_atribuida AS double precision), " +
                "       r.accion, r.motivo, r.movimiento_stock_id, r.usuario_id, u.nickname, r.fecha, r.anulada " +
                "FROM operaciones.lote_pendiente_resuelto r " +
                "LEFT JOIN operaciones.movimiento_stock_lote msl ON msl.id = r.msl_id AND msl.sucursal_id = r.sucursal_id " +
                "LEFT JOIN productos.producto pr ON pr.id = msl.producto_id " +
                "LEFT JOIN operaciones.lote l ON l.id = r.lote_id " +
                "LEFT JOIN personas.usuario u ON u.id = r.usuario_id " +
                "WHERE (CAST(:sucursalId AS bigint) IS NULL OR r.sucursal_id = :sucursalId) " +
                "ORDER BY r.fecha DESC, r.id DESC LIMIT :limite OFFSET :offset", p, (rs, n) -> {
                    ResolucionLotePendienteDto d = new ResolucionLotePendienteDto();
                    d.setId(rs.getLong(1));
                    d.setMslId(rs.getLong(2));
                    d.setSucursalId(rs.getLong(3));
                    d.setProductoId((Long) rs.getObject(4));
                    d.setProductoDescripcion(rs.getString(5));
                    d.setLoteId((Long) rs.getObject(6));
                    d.setNumeroLote(rs.getString(7));
                    d.setCantidadAtribuida(rs.getDouble(8));
                    d.setCantidadNoAtribuida(rs.getDouble(9));
                    d.setAccion(rs.getString(10));
                    d.setMotivo(rs.getString(11));
                    d.setMovimientoStockId((Long) rs.getObject(12));
                    d.setUsuarioId((Long) rs.getObject(13));
                    d.setUsuarioNickname(rs.getString(14));
                    Timestamp f = rs.getTimestamp(15);
                    d.setFecha(f != null ? f.toLocalDateTime() : null);
                    d.setAnulada(rs.getBoolean(16));
                    return d;
                });
    }

    // ---------------------------------------------------------------- reglas puras

    /**
     * Valida cuanto se puede atribuir. Pura y estatica para probarla sin base.
     *
     * @param remanente      lo que falta resolver de esa recepcion.
     * @param saldoSinLote   SIN LOTE disponible ahora en la sucursal.
     * @param esperado       SIN LOTE que vio el operador al abrir el dialogo (concurrencia optimista).
     * @param cantidad       lo que el operador confirma atribuir.
     */
    public static void validarAtribucion(double remanente, double saldoSinLote, Double esperado, Double cantidad) {
        if (remanente <= EPSILON) {
            throw new IllegalArgumentException("Esta recepción ya no tiene unidades pendientes de lote.");
        }
        if (esperado == null) {
            throw new IllegalArgumentException("Falta el saldo SIN LOTE que se vio al abrir: recargá.");
        }
        if (Math.abs(esperado - saldoSinLote) > EPSILON) {
            throw new IllegalArgumentException("El saldo SIN LOTE cambió mientras completabas (ahora "
                    + redondear(saldoSinLote) + "): recargá.");
        }
        if (cantidad == null || cantidad <= EPSILON) {
            throw new IllegalArgumentException("La cantidad a atribuir tiene que ser mayor a cero.");
        }
        if (cantidad - remanente > EPSILON) {
            throw new IllegalArgumentException("No se puede atribuir más de lo pendiente de esta recepción ("
                    + redondear(remanente) + ").");
        }
        if (cantidad - saldoSinLote > EPSILON) {
            throw new IllegalArgumentException("No se puede atribuir más que el SIN LOTE disponible en la sucursal ("
                    + redondear(saldoSinLote) + ").");
        }
    }

    /** Sin dato de sincronizacion se bloquea: no se sabe si hay ventas de la filial sin replicar. */
    public static boolean sincronizacionVencida(LocalDateTime ultima, int maxMinutos, LocalDateTime ahora) {
        if (maxMinutos <= 0) {
            return false;
        }
        return ultima == null || ultima.isBefore(ahora.minusMinutes(maxMinutos));
    }

    // ---------------------------------------------------------------- mutaciones

    /**
     * Pasa unidades de la recepcion SIN LOTE a un lote (ajuste ATRIBUIR). Todo en una transaccion.
     *
     * @param cerrarRemanente si queda algo sin atribuir, true lo cierra con {@code motivoRemanente}
     *                        (obligatorio); false deja el pendiente abierto por lo que falta.
     * @param confirmarVencimiento el lote ya existe con otro vencimiento y el operador lo acepta.
     */
    @Transactional
    public ResolucionLotePendienteDto completar(Long mslId, Long sucursalId, String numeroLote, String fechaVencimiento,
                                                Double cantidad, Double saldoSinLoteEsperado,
                                                Boolean cerrarRemanente, String motivoRemanente,
                                                Boolean confirmarVencimiento, Long usuarioId) {
        if (numeroLote == null || LoteService.normalizarNumeroLote(numeroLote) == null) {
            throw new IllegalArgumentException("Falta el número de lote.");
        }
        LocalDate venc = aFecha(fechaVencimiento);
        if (sincronizacionVencida(ultimaSincronizacion(sucursalId), maxDesfaseMinutos, LocalDateTime.now())) {
            throw new IllegalArgumentException("La sucursal no sincroniza con el central hace más de "
                    + maxDesfaseMinutos + " minutos: puede haber ventas sin lote que todavía no llegaron. "
                    + "Reintentá cuando sincronice.");
        }
        loteFefoService.bloquearSaldo(sucursalId);
        bloquearFila(mslId, sucursalId);
        PendienteLoteDto pend = buscar(mslId, sucursalId);

        double sinLote = saldoSinLote(pend.getProductoId(), sucursalId);
        validarAtribucion(pend.getRemanente(), sinLote, saldoSinLoteEsperado, cantidad);

        double resto = pend.getRemanente() - cantidad;
        boolean cerrar = resto > EPSILON && Boolean.TRUE.equals(cerrarRemanente);
        if (cerrar && (motivoRemanente == null || motivoRemanente.trim().isEmpty())) {
            throw new IllegalArgumentException("Para cerrar las " + redondear(resto)
                    + " unidades que no se atribuyen hace falta el motivo.");
        }

        Lote existente = loteRepository.findByProductoIdAndNumeroLote(pend.getProductoId(),
                LoteService.normalizarNumeroLote(numeroLote)).orElse(null);
        if (existente != null && venc != null && existente.getFechaVencimiento() != null
                && !venc.equals(existente.getFechaVencimiento()) && !Boolean.TRUE.equals(confirmarVencimiento)) {
            throw new IllegalArgumentException("VENCIMIENTO_DISTINTO: el lote " + existente.getNumeroLote()
                    + " ya existe con vencimiento " + existente.getFechaVencimiento()
                    + ". Confirmá para usarlo con ese vencimiento.");
        }

        AjusteStockLoteInput in = new AjusteStockLoteInput();
        in.setProductoId(pend.getProductoId());
        in.setSucursalId(sucursalId);
        in.setModo(ModoAjusteLote.ATRIBUIR);
        in.setUsuarioId(usuarioId);
        in.setMotivo("Pendiente de lote: recepción " + pend.getRecepcionId() + " (msl " + mslId + ")");
        double saldoLote = 0.0;
        if (existente != null) {
            in.setLoteId(existente.getId());
            saldoLote = saldoDeLote(pend.getProductoId(), sucursalId, existente.getId());
        } else {
            in.setNumeroLote(numeroLote);
            in.setFechaVencimiento(venc != null ? venc.toString() : null);
        }
        in.setSaldoEsperado(saldoLote);
        in.setCantidadFinal(saldoLote + cantidad);
        AjusteStockLoteResultadoDto aj = ajusteService.ajustar(in);

        Long id = insertar(mslId, sucursalId, aj.getLoteId(), cantidad, cerrar ? resto : 0.0, "ATRIBUIDO",
                cerrar ? motivoRemanente.trim() : null, aj.getMovimientoStockId(), usuarioId);
        return resolucion(id);
    }

    /** Cierra el pendiente sin mover stock (lote ilegible, ya vendido, ya atribuido a mano). */
    @Transactional
    public ResolucionLotePendienteDto descartar(Long mslId, Long sucursalId, String motivo, Long usuarioId) {
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new IllegalArgumentException("Para descartar un pendiente el motivo es obligatorio.");
        }
        bloquearFila(mslId, sucursalId);
        PendienteLoteDto pend = buscar(mslId, sucursalId);
        Long id = insertar(mslId, sucursalId, null, 0.0, pend.getRemanente(), "DESCARTADO", motivo.trim(), null, usuarioId);
        return resolucion(id);
    }

    /**
     * Anula una resolucion y el pendiente vuelve a la lista. Si atribuyo unidades, las devuelve a
     * SIN LOTE con un LIBERAR, solo si el lote todavia tiene ese saldo en la sucursal.
     */
    @Transactional
    public ResolucionLotePendienteDto reabrir(Long resolucionId, String motivo, Long usuarioId) {
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new IllegalArgumentException("Para reabrir el motivo es obligatorio.");
        }
        List<Object[]> filas = jdbc.query(
                "SELECT r.sucursal_id, r.lote_id, CAST(r.cantidad_atribuida AS double precision), r.anulada, msl.producto_id " +
                "FROM operaciones.lote_pendiente_resuelto r " +
                "JOIN operaciones.movimiento_stock_lote msl ON msl.id = r.msl_id AND msl.sucursal_id = r.sucursal_id " +
                "WHERE r.id = :id FOR UPDATE OF r",
                new MapSqlParameterSource("id", resolucionId),
                (rs, n) -> new Object[]{rs.getLong(1), rs.getObject(2), rs.getDouble(3), rs.getBoolean(4), rs.getLong(5)});
        if (filas.isEmpty()) {
            throw new IllegalArgumentException("No existe la resolución " + resolucionId + ".");
        }
        Object[] f = filas.get(0);
        if (Boolean.TRUE.equals(f[3])) {
            throw new IllegalArgumentException("Esa resolución ya estaba anulada.");
        }
        Long sucursalId = (Long) f[0];
        Long loteId = f[1] != null ? ((Number) f[1]).longValue() : null;
        double atribuida = (Double) f[2];
        Long productoId = (Long) f[4];
        if (loteId != null && atribuida > EPSILON) {
            loteFefoService.bloquearSaldo(sucursalId);
            double saldo = saldoDeLote(productoId, sucursalId, loteId);
            if (saldo + EPSILON < atribuida) {
                throw new IllegalArgumentException("No se puede reabrir: el lote ya no tiene las " + redondear(atribuida)
                        + " unidades atribuidas en la sucursal (tiene " + redondear(saldo)
                        + "). Corregilo desde «Lotes del producto».");
            }
            AjusteStockLoteInput in = new AjusteStockLoteInput();
            in.setProductoId(productoId);
            in.setSucursalId(sucursalId);
            in.setModo(ModoAjusteLote.LIBERAR);
            in.setLoteId(loteId);
            in.setUsuarioId(usuarioId);
            in.setSaldoEsperado(saldo);
            in.setCantidadFinal(saldo - atribuida);
            in.setMotivo("Reabrir pendiente de lote (resolución " + resolucionId + "): " + motivo.trim());
            ajusteService.ajustar(in);
        }
        jdbc.update("UPDATE operaciones.lote_pendiente_resuelto SET anulada = true, anulada_por = :u, " +
                        "anulada_motivo = :m, anulada_en = now() WHERE id = :id",
                new MapSqlParameterSource("u", usuarioId).addValue("m", motivo.trim()).addValue("id", resolucionId));
        return resolucion(resolucionId);
    }

    // ---------------------------------------------------------------- auxiliares

    private void bloquearFila(Long mslId, Long sucursalId) {
        if (mslId == null || sucursalId == null) {
            throw new IllegalArgumentException("Faltan la recepción pendiente y la sucursal.");
        }
        List<Long> ids = jdbc.queryForList("SELECT id FROM operaciones.movimiento_stock_lote " +
                        "WHERE id = :id AND sucursal_id = :s FOR UPDATE",
                new MapSqlParameterSource("id", mslId).addValue("s", sucursalId), Long.class);
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("No existe el pendiente " + mslId + " en la sucursal " + sucursalId + ".");
        }
    }

    private PendienteLoteDto buscar(Long mslId, Long sucursalId) {
        if (mslId == null || sucursalId == null) {
            throw new IllegalArgumentException("Faltan la recepción pendiente y la sucursal.");
        }
        List<PendienteLoteDto> r = jdbc.query(SELECT + BASE, params(sucursalId, mslId, null, null), (rs, n) -> map(rs));
        if (r.isEmpty()) {
            throw new IllegalArgumentException("Esa recepción ya no está pendiente de lote (ya se resolvió o no existe).");
        }
        return r.get(0);
    }

    private double saldoSinLote(Long productoId, Long sucursalId) {
        Double s = ajusteService.resumen(productoId, sucursalId).getSinTrazar();
        return s != null ? Math.max(0.0, s) : 0.0;
    }

    private double saldoDeLote(Long productoId, Long sucursalId, Long loteId) {
        for (StockLoteDto l : movimientoStockLoteService.stockPorLote(productoId, sucursalId)) {
            if (loteId.equals(l.getLoteId())) {
                return l.getCantidadDisponible() != null ? l.getCantidadDisponible() : 0.0;
            }
        }
        return 0.0;
    }

    /** Ajustes ATRIBUIR (AJUSTE en 0 con fila a lote real) hechos a mano despues de la recepcion. */
    private double atribuidoManualDespues(PendienteLoteDto pend) {
        Double v = jdbc.queryForObject(
                "SELECT CAST(COALESCE(SUM(msl.cantidad), 0) AS double precision) " +
                "FROM operaciones.movimiento_stock_lote msl " +
                "JOIN operaciones.movimiento_stock ms ON ms.id = msl.movimiento_stock_id AND ms.sucursal_id = msl.sucursal_id " +
                "WHERE msl.producto_id = :p AND msl.sucursal_id = :s AND msl.lote_id IS NOT NULL AND msl.estado = true " +
                "  AND msl.cantidad > 0 AND CAST(ms.tipo_movimiento AS text) = 'AJUSTE' AND ms.cantidad = 0 " +
                "  AND msl.creado_en >= :f " +
                "  AND NOT EXISTS (SELECT 1 FROM operaciones.lote_pendiente_resuelto r " +
                "                  WHERE r.movimiento_stock_id = ms.id AND r.sucursal_id = ms.sucursal_id)",
                new MapSqlParameterSource("p", pend.getProductoId()).addValue("s", pend.getSucursalId())
                        .addValue("f", Timestamp.valueOf(pend.getFecha())), Double.class);
        return v != null ? v : 0.0;
    }

    /** Ultimo mensaje de replicacion recibido de la sucursal, si este central tiene esa suscripcion. */
    LocalDateTime ultimaSincronizacion(Long sucursalId) {
        try {
            Timestamp t = jdbc.queryForObject(
                    "SELECT MAX(last_msg_receipt_time)::timestamp FROM pg_stat_subscription " +
                    "WHERE subname LIKE :patron",
                    new MapSqlParameterSource("patron", "%\\_filial" + sucursalId + "\\_%"), Timestamp.class);
            return t != null ? t.toLocalDateTime() : null;
        } catch (Exception e) {
            log.warning("No se pudo leer la sincronizacion de la sucursal " + sucursalId + ": " + e.getMessage());
            return null;
        }
    }

    private Long insertar(Long mslId, Long sucursalId, Long loteId, double atribuida, double noAtribuida,
                          String accion, String motivo, Long movimientoId, Long usuarioId) {
        return jdbc.queryForObject(
                "INSERT INTO operaciones.lote_pendiente_resuelto (msl_id, sucursal_id, lote_id, cantidad_atribuida, " +
                "cantidad_no_atribuida, accion, motivo, movimiento_stock_id, usuario_id) " +
                "VALUES (:msl, :s, :l, :a, :na, :ac, :m, :mov, :u) RETURNING id",
                new MapSqlParameterSource("msl", mslId).addValue("s", sucursalId).addValue("l", loteId)
                        .addValue("a", atribuida).addValue("na", noAtribuida).addValue("ac", accion)
                        .addValue("m", motivo).addValue("mov", movimientoId).addValue("u", usuarioId), Long.class);
    }

    private ResolucionLotePendienteDto resolucion(Long id) {
        ResolucionLotePendienteDto d = new ResolucionLotePendienteDto();
        jdbc.query("SELECT r.id, r.msl_id, r.sucursal_id, r.lote_id, l.numero_lote, " +
                        "CAST(r.cantidad_atribuida AS double precision), CAST(r.cantidad_no_atribuida AS double precision), " +
                        "r.accion, r.motivo, r.movimiento_stock_id, r.usuario_id, r.fecha, r.anulada " +
                        "FROM operaciones.lote_pendiente_resuelto r LEFT JOIN operaciones.lote l ON l.id = r.lote_id " +
                        "WHERE r.id = :id", new MapSqlParameterSource("id", id), rs -> {
                    d.setId(rs.getLong(1));
                    d.setMslId(rs.getLong(2));
                    d.setSucursalId(rs.getLong(3));
                    d.setLoteId((Long) rs.getObject(4));
                    d.setNumeroLote(rs.getString(5));
                    d.setCantidadAtribuida(rs.getDouble(6));
                    d.setCantidadNoAtribuida(rs.getDouble(7));
                    d.setAccion(rs.getString(8));
                    d.setMotivo(rs.getString(9));
                    d.setMovimientoStockId((Long) rs.getObject(10));
                    d.setUsuarioId((Long) rs.getObject(11));
                    Timestamp f = rs.getTimestamp(12);
                    d.setFecha(f != null ? f.toLocalDateTime() : null);
                    d.setAnulada(rs.getBoolean(13));
                });
        return d;
    }

    private static MapSqlParameterSource params(Long sucursalId, Long mslId, Timestamp desde, Timestamp hasta) {
        return new MapSqlParameterSource("sucursalId", sucursalId).addValue("mslId", mslId)
                .addValue("desde", desde).addValue("hasta", hasta);
    }

    private static PendienteLoteDto map(ResultSet rs) throws SQLException {
        PendienteLoteDto d = new PendienteLoteDto();
        d.setMslId(rs.getLong(1));
        d.setSucursalId(rs.getLong(2));
        d.setSucursalNombre(rs.getString(3));
        d.setProductoId((Long) rs.getObject(4));
        d.setProductoDescripcion(rs.getString(5));
        d.setRecepcionId((Long) rs.getObject(6));
        d.setRecepcionItemId((Long) rs.getObject(7));
        d.setProveedorNombre(rs.getString(8));
        Timestamp f = rs.getTimestamp(9);
        d.setFecha(f != null ? f.toLocalDateTime() : null);
        d.setCantidad(rs.getDouble(10));
        d.setResuelto(rs.getDouble(11));
        d.setRemanente(d.getCantidad() - d.getResuelto());
        return d;
    }

    private static Timestamp aInicio(String fecha) {
        LocalDate d = aFecha(fecha);
        return d != null ? Timestamp.valueOf(d.atStartOfDay()) : null;
    }

    private static Timestamp aFinExclusivo(String fecha) {
        LocalDate d = aFecha(fecha);
        return d != null ? Timestamp.valueOf(d.plusDays(1).atStartOfDay()) : null;
    }

    private static LocalDate aFecha(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return null;
        }
        String v = valor.trim();
        try {
            return LocalDate.parse(v.length() > 10 ? v.substring(0, 10) : v);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("La fecha " + valor + " no tiene formato yyyy-MM-dd.");
        }
    }

    private static String redondear(double v) {
        return (Math.abs(v - Math.rint(v)) < EPSILON) ? String.valueOf((long) Math.rint(v)) : String.format("%.2f", v);
    }
}
