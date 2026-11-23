package com.franco.dev.graphql.financiero;

import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.TerminalPos;
import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.VentaTarjetaInput;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.TerminalPosService;
import com.franco.dev.service.financiero.VentaTarjetaService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.utilitarios.DateUtils;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class VentaTarjetaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private TesoreriaSecurityService seg;

    @Autowired
    private VentaTarjetaService service;

    @Autowired
    private VentaService ventaService;


    @Autowired
    private PdvCajaService pdvCajaService;

    @Autowired
    private TerminalPosService terminalPosService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private ImpresionService impresionService;

    static final String VENTA_TARJETA_COMPLETAR = "VENTA TARJETA COMPLETAR";

    /** Leen o tocan el cobro de una caja ajena: quien concilia cupones y la lectura de caja (#574). */
    static final String[] COBRO_AJENO = java.util.stream.Stream.concat(
            java.util.stream.Stream.of(VENTA_TARJETA_COMPLETAR),
            java.util.Arrays.stream(PdvCajaGraphQL.LECTURA_CAJA)).toArray(String[]::new);

    /** El dueño es el de la caja del cobro, que resuelve el servidor; `usuario` lo manda el cliente (#574). */
    private void exigirCobroPropioORol(VentaTarjeta vt) {
        if (vt != null) seg.requireUsuarioORol(PdvCajaGraphQL.duenio(vt.getCaja()), COBRO_AJENO);
    }

    public VentaTarjeta ventaTarjetaPorId(Long id, Long sucId) {
        VentaTarjeta vt = service.findByIdAndSucursalId(id, sucId);
        exigirCobroPropioORol(vt);
        return vt;
    }

    public VentaTarjeta ventaTarjetaPorVentaId(Long ventaId, Long sucId) {
        VentaTarjeta vt = service.findByVentaIdAndSucursalId(ventaId, sucId);
        exigirCobroPropioORol(vt);
        return vt;
    }

    public List<VentaTarjeta> ventasTarjetaPorCaja(Long cajaId, Long sucId) {
        PdvCajaGraphQL.exigirLecturaDeCaja(seg, pdvCajaService, cajaId, sucId);
        return service.findByCajaIdAndSucursalId(cajaId, sucId);
    }

    public Long countVentasTarjetaSinRegistrar(Long cajaId, Long sucId) {
        PdvCajaGraphQL.exigirLecturaDeCaja(seg, pdvCajaService, cajaId, sucId);
        Long count = service.countVentasTarjetaSinRegistrar(cajaId, sucId);
        return count != null ? count : 0L;
    }

    private static final int VENTA_LOOKUP_MAX_INTENTOS = 5;
    private static final long VENTA_LOOKUP_ESPERA_MS = 300;

    public VentaTarjeta saveVentaTarjeta(VentaTarjetaInput input) {
        seg.requireAnyRole("VENTA TOUCH", VENTA_TARJETA_COMPLETAR);
        // La PWA y el mobile toman un rechazo de la lectura como «no hay registro» y guardan otro: un cobro ya
        // registrado solo lo vuelve a guardar su dueño o el rol, en vez de duplicarlo (#574)
        if (input.getSucursalId() == null || input.getCajaId() == null) {
            // Sin sucursal o sin caja no se sabe de quién es el cobro (la PWA y el mobile mandan las dos)
            seg.requireAnyRole(COBRO_AJENO);
        } else {
            // La caja destino la elige el cliente: un cobro en la caja de otro, solo con el rol
            PdvCaja destino = pdvCajaService.findById(input.getCajaId(), input.getSucursalId());
            if (destino != null) seg.requireUsuarioORol(PdvCajaGraphQL.duenio(destino), COBRO_AJENO);
        }
        if (input.getId() != null && input.getSucursalId() != null) {
            exigirCobroPropioORol(service.findByIdAndSucursalId(input.getId(), input.getSucursalId()));
        }
        if (input.getVentaId() != null && input.getSucursalId() != null) {
            List<VentaTarjeta> previos = service.findAllByVentaIdAndSucursalId(input.getVentaId(), input.getSucursalId());
            if (previos != null) previos.forEach(this::exigirCobroPropioORol);
        }
        ModelMapper m = new ModelMapper();
        m.getConfiguration().setAmbiguityIgnored(true);
        VentaTarjeta entity = m.map(input, VentaTarjeta.class);

        if (input.getVentaId() != null && input.getSucursalId() != null) {
            Venta venta = null;
            for (int intento = 1; intento <= VENTA_LOOKUP_MAX_INTENTOS && venta == null; intento++) {
                venta = ventaService.findByIdAndSucursalId(input.getVentaId(), input.getSucursalId());
                if (venta == null && intento < VENTA_LOOKUP_MAX_INTENTOS) {
                    try {
                        Thread.sleep(VENTA_LOOKUP_ESPERA_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            if (venta == null) {
                throw new GraphQLException(
                        "La venta aún no se sincronizó con el central, intente nuevamente en unos segundos.");
            }
            entity.setVenta(venta);
        }

        if (input.getCajaId() != null && input.getSucursalId() != null) {
            PdvCaja caja = pdvCajaService.findById(input.getCajaId(), input.getSucursalId());
            entity.setCaja(caja);
        }

        if (input.getTerminalPosId() != null) {
            TerminalPos terminal = terminalPosService.findById(input.getTerminalPosId()).orElse(null);
            entity.setTerminalPos(terminal);
        }

        if (input.getUsuarioId() != null) {
            Usuario usuario = usuarioService.findById(input.getUsuarioId()).orElse(null);
            entity.setUsuario(usuario);
        }

        return service.save(entity);
    }

    @Autowired(required = false)
    private com.franco.dev.eventos.EventoEmisor eventoEmisor;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager txManager;

    /**
     * La app (frc-mobile) completa la tarjeta por aca, en el central. El guardado y su aviso
     * venta_tarjeta.completada van en una sola TX (srv #587, C1): si el guardado no queda, no hay aviso.
     */
    public VentaTarjeta updateVentaTarjeta(VentaTarjetaInput input) {
        seg.requireAnyRole("VENTA TOUCH", VENTA_TARJETA_COMPLETAR);
        return new org.springframework.transaction.support.TransactionTemplate(txManager).execute(s -> actualizar(input));
    }

    private VentaTarjeta actualizar(VentaTarjetaInput input) {
        VentaTarjeta entity = service.findByIdAndSucursalId(input.getId(), input.getSucursalId());
        if (entity == null) return null;
        exigirCobroPropioORol(entity); // hoy cualquier cajero reescribía monto, autorización e imagen ajenos (#574)

        if (input.getCodigoAutorizacion() != null) entity.setCodigoAutorizacion(input.getCodigoAutorizacion());
        if (input.getNumeroBoleta() != null) entity.setNumeroBoleta(input.getNumeroBoleta());
        if (input.getMontoEscaneado() != null) entity.setMontoEscaneado(input.getMontoEscaneado());
        if (input.getImagenUrl() != null) entity.setImagenUrl(input.getImagenUrl());
        String estadoAnterior = entity.getEstado();
        if (input.getEstado() != null) entity.setEstado(input.getEstado());
        if (input.getTerminalPosId() != null) {
            entity.setTerminalPos(terminalPosService.findById(input.getTerminalPosId()).orElse(null));
        }
        // La moneda del COBRO, no la de la terminal: es el cobro el que se esta pagando, y sin
        // ella monto/monto_escaneado quedan sin unidad.
        if (input.getMonedaId() != null) {
            entity.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        }
        if (input.getUsuarioId() != null) {
            entity.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }

        VentaTarjeta guardada = service.save(entity);
        if (eventoEmisor != null && eventoEmisor.encendido() && "COMPLETADO".equals(guardada.getEstado())
                && !"COMPLETADO".equals(estadoAnterior)) {
            final VentaTarjeta vt = guardada;
            eventoEmisor.emitirProyeccionDe(vt.getSucursalId(), "venta_tarjeta.completada", "baja",
                    vt.getUsuario() != null ? vt.getUsuario().getId() : null,
                    "venta_tarjeta/" + vt.getSucursalId() + "/" + vt.getId(),
                    () -> com.franco.dev.eventos.EventosTarjeta.tarjetaConLineas(vt, vt.getVenta(), lineasCobro(vt)),
                    () -> com.franco.dev.eventos.EventosTarjeta.atributos(vt));
        }
        return guardada;
    }

    @Autowired(required = false)
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    static final String SQL_LINEAS_COBRO = "SELECT cd.id, cd.valor, cd.cambio, cd.pago, cd.vuelto, cd.descuento, cd.aumento,"
            + " cd.identificador_transaccion, m.id AS moneda_id, m.denominacion, fp.id AS forma_pago_id, fp.descripcion"
            + " FROM operaciones.cobro_detalle cd"
            + " LEFT JOIN financiero.moneda m ON m.id = cd.moneda_id"
            + " LEFT JOIN financiero.forma_pago fp ON fp.id = cd.forma_pago_id"
            + " WHERE cd.cobro_id = ? AND cd.sucursal_id = ? ORDER BY cd.id";

    /**
     * Las lineas de cobro de la venta, con la forma de EventosVenta.lineaCobro (pedido de Oku, srv #587).
     * Por JDBC sobre la conexion de la TX y dentro de un savepoint: si la lectura falla, se vuelve al
     * savepoint y el aviso sale sin lineas. No pasa por un repositorio de Spring Data ni por Hibernate,
     * que ante una excepcion dejarian la TX de la tarjeta marcada para rollback (auditoria de C1).
     */
    java.util.List<java.util.Map<String, Object>> lineasCobro(VentaTarjeta vt) {
        if (vt.getVenta() == null || vt.getVenta().getCobro() == null || jdbcTemplate == null) return null;
        Long cobroId = vt.getVenta().getCobro().getId();
        // La sucursal de la venta (clave compuesta del cobro), no la de la tarjeta.
        Long suc = vt.getVenta().getSucursalId() != null ? vt.getVenta().getSucursalId() : vt.getSucursalId();
        try {
            return jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<java.util.List<java.util.Map<String, Object>>>) con -> {
                java.sql.Savepoint sp = con.setSavepoint();
                try (java.sql.PreparedStatement ps = con.prepareStatement(SQL_LINEAS_COBRO)) {
                    ps.setQueryTimeout(2);
                    ps.setLong(1, cobroId);
                    ps.setLong(2, suc);
                    java.util.List<java.util.Map<String, Object>> l = new java.util.ArrayList<>();
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) l.add(lineaCobro(rs));
                    }
                    con.releaseSavepoint(sp);
                    return l;
                } catch (java.sql.SQLException e) {
                    con.rollback(sp);
                    throw e;
                }
            });
        } catch (Exception e) {
            // El aviso sale sin lineas y la tarjeta se guarda igual; queda en el log para que se note en el piloto.
            org.slf4j.LoggerFactory.getLogger(VentaTarjetaGraphQL.class).warn(
                    "[eventos] venta_tarjeta.completada {}/{} sin lineas de cobro: {}", vt.getSucursalId(), vt.getId(), e.toString());
            return null;
        }
    }

    // numeric de Postgres: el driver no lo convierte a Double con getObject(..., Double.class).
    private static Double aDouble(java.math.BigDecimal b) {
        return b == null ? null : b.doubleValue();
    }

    static java.util.Map<String, Object> lineaCobro(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.util.Map<String, Object> l = new java.util.LinkedHashMap<>();
        l.put("id", rs.getLong("id"));
        l.put("valor", aDouble(rs.getBigDecimal("valor")));
        l.put("cambio", aDouble(rs.getBigDecimal("cambio")));
        l.put("pago", (Boolean) rs.getObject("pago", Boolean.class));
        l.put("vuelto", (Boolean) rs.getObject("vuelto", Boolean.class));
        l.put("descuento", (Boolean) rs.getObject("descuento", Boolean.class));
        l.put("aumento", (Boolean) rs.getObject("aumento", Boolean.class));
        l.put("identificadorTransaccion", rs.getString("identificador_transaccion"));
        Long monedaId = (Long) rs.getObject("moneda_id", Long.class);
        if (monedaId != null) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", monedaId);
            m.put("denominacion", rs.getString("denominacion"));
            l.put("moneda", m);
        }
        Long fpId = (Long) rs.getObject("forma_pago_id", Long.class);
        if (fpId != null) {
            java.util.Map<String, Object> f = new java.util.LinkedHashMap<>();
            f.put("id", fpId);
            f.put("descripcion", rs.getString("descripcion"));
            l.put("formaPago", f);
        }
        return l;
    }

    public Boolean deleteVentaTarjeta(Long id, Long sucId) {
        seg.requireSuperusuario();
        VentaTarjeta entity = service.findByIdAndSucursalId(id, sucId);
        if (entity == null) return false;
        return service.delete(entity);
    }

    public Page<VentaTarjeta> filtrarVentasTarjeta(Long id, Long ventaId, Long sucursalId,
                                                    String terminalDescripcion, String terminalCodigo,
                                                    String estado, String fechaDesde, String fechaHasta,
                                                    Integer page, Integer size) {
        seg.requireAnyRole(VENTA_TARJETA_COMPLETAR); // el rol de «Conciliación de cupones» (#574)
        LocalDateTime desde = (fechaDesde != null && !fechaDesde.isEmpty()) ? LocalDateTime.parse(fechaDesde) : null;
        LocalDateTime hasta = (fechaHasta != null && !fechaHasta.isEmpty()) ? LocalDateTime.parse(fechaHasta) : null;
        return service.filter(id, ventaId, sucursalId, terminalDescripcion, terminalCodigo, estado, desde, hasta,
                page != null ? page : 0, size != null ? size : 15);
    }

    public Boolean cancelarVentaTarjetaPorVentaId(Long ventaId, Long sucId) {
        seg.requireCancelarVenta();
        List<VentaTarjeta> registros = service.findAllByVentaIdAndSucursalId(ventaId, sucId);
        if (registros == null || registros.isEmpty()) return false;
        registros.forEach(vt -> {
            vt.setEstado("CANCELADO");
            service.save(vt);
        });
        return true;
    }

    public String imprimirReporteVentaTarjeta(Long id, Long ventaId, Long sucursalId, String terminalDescripcion,
                                               String terminalCodigo, String estado,
                                               String fechaDesde, String fechaHasta,
                                               Long usuarioResponsableId) {
        seg.requireAnyRole(VENTA_TARJETA_COMPLETAR);
        LocalDateTime desde = (fechaDesde != null && !fechaDesde.isEmpty()) ? LocalDateTime.parse(fechaDesde) : null;
        LocalDateTime hasta = (fechaHasta != null && !fechaHasta.isEmpty()) ? LocalDateTime.parse(fechaHasta) : null;

        List<VentaTarjeta> ventaTarjetaList = service.filterList(id, ventaId, sucursalId, terminalDescripcion, terminalCodigo, estado, desde, hasta);

        String sucursalFiltro = null;
        if (sucursalId != null) {
            Sucursal sucursal = sucursalService.findById(sucursalId).orElse(null);
            sucursalFiltro = sucursal != null ? sucursal.getNombre() : null;
        }

        String terminalFiltro = null;
        if (terminalDescripcion != null && terminalCodigo != null) {
            terminalFiltro = terminalDescripcion + " - " + terminalCodigo;
        } else if (terminalDescripcion != null) {
            terminalFiltro = terminalDescripcion;
        } else if (terminalCodigo != null) {
            terminalFiltro = terminalCodigo;
        }

        Usuario usuarioReporte = null;
        if (usuarioResponsableId != null) {
            usuarioReporte = usuarioService.findById(usuarioResponsableId).orElse(null);
        }

        String fechaDesdeFormateada = desde != null ? DateUtils.toString(desde) : null;
        String fechaHastaFormateada = hasta != null ? DateUtils.toString(hasta) : null;

        return impresionService.imprimirReporteVentaTarjeta(ventaTarjetaList, sucursalFiltro, terminalFiltro,
                estado, fechaDesdeFormateada, fechaHastaFormateada, usuarioReporte);
    }
}
