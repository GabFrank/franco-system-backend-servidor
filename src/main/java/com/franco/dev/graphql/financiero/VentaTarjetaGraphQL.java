package com.franco.dev.graphql.financiero;

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

    public VentaTarjeta ventaTarjetaPorId(Long id, Long sucId) {
        return service.findByIdAndSucursalId(id, sucId);
    }

    public VentaTarjeta ventaTarjetaPorVentaId(Long ventaId, Long sucId) {
        return service.findByVentaIdAndSucursalId(ventaId, sucId);
    }

    public List<VentaTarjeta> ventasTarjetaPorCaja(Long cajaId, Long sucId) {
        return service.findByCajaIdAndSucursalId(cajaId, sucId);
    }

    public Long countVentasTarjetaSinRegistrar(Long cajaId, Long sucId) {
        Long count = service.countVentasTarjetaSinRegistrar(cajaId, sucId);
        return count != null ? count : 0L;
    }

    private static final int VENTA_LOOKUP_MAX_INTENTOS = 5;
    private static final long VENTA_LOOKUP_ESPERA_MS = 300;

    public VentaTarjeta saveVentaTarjeta(VentaTarjetaInput input) {
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

    public VentaTarjeta updateVentaTarjeta(VentaTarjetaInput input) {
        VentaTarjeta entity = service.findByIdAndSucursalId(input.getId(), input.getSucursalId());
        if (entity == null) return null;

        if (input.getCodigoAutorizacion() != null) entity.setCodigoAutorizacion(input.getCodigoAutorizacion());
        if (input.getNumeroBoleta() != null) entity.setNumeroBoleta(input.getNumeroBoleta());
        if (input.getMontoEscaneado() != null) entity.setMontoEscaneado(input.getMontoEscaneado());
        if (input.getImagenUrl() != null) entity.setImagenUrl(input.getImagenUrl());
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

        return service.save(entity);
    }

    public Boolean deleteVentaTarjeta(Long id, Long sucId) {
        VentaTarjeta entity = service.findByIdAndSucursalId(id, sucId);
        if (entity == null) return false;
        return service.delete(entity);
    }

    public Page<VentaTarjeta> filtrarVentasTarjeta(Long id, Long ventaId, Long sucursalId,
                                                    String terminalDescripcion, String terminalCodigo,
                                                    String estado, String fechaDesde, String fechaHasta,
                                                    Integer page, Integer size) {
        LocalDateTime desde = (fechaDesde != null && !fechaDesde.isEmpty()) ? LocalDateTime.parse(fechaDesde) : null;
        LocalDateTime hasta = (fechaHasta != null && !fechaHasta.isEmpty()) ? LocalDateTime.parse(fechaHasta) : null;
        return service.filter(id, ventaId, sucursalId, terminalDescripcion, terminalCodigo, estado, desde, hasta,
                page != null ? page : 0, size != null ? size : 15);
    }

    public Boolean cancelarVentaTarjetaPorVentaId(Long ventaId, Long sucId) {
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
