package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Devolucion;
import com.franco.dev.domain.operaciones.dto.RetiroBloqueResultadoDto;
import com.franco.dev.domain.operaciones.dto.RetiroProveedorConsolidadoDto;
import com.franco.dev.domain.operaciones.enums.DevolucionEstado;
import com.franco.dev.graphql.operaciones.input.DevolucionInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.impresion.RemitoRetiroService;
import com.franco.dev.service.impresion.TicketRetiroService;
import com.franco.dev.service.operaciones.DevolucionService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class DevolucionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private DevolucionService service;

    @Autowired
    private ProveedorService proveedorService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private DevolucionItemGraphQL devolucionItemGraphQL;

    @Autowired
    private RemitoRetiroService remitoRetiroService;

    @Autowired
    private TicketRetiroService ticketRetiroService;

    /**
     * Obtiene una devolución por ID
     */
    public Devolucion devolucion(Long id) {
        if (id == null) {
            throw new GraphQLException("ID es requerido");
        }
        return service.findById(id).orElse(null);
    }

    /**
     * Busca devoluciones con filtros avanzados
     */
    public Page<Devolucion> devolucionConFiltros(
            Long proveedorId,
            Long sucursalId,
            DevolucionEstado estado,
            String fechaInicio,
            String fechaFin,
            Integer page,
            Integer size) {

        if (page == null) page = 0;
        if (size == null) size = 20;

        Pageable pageable = PageRequest.of(page, size);
        LocalDateTime fechaInicioDate = fechaInicio != null ? stringToDate(fechaInicio) : null;
        LocalDateTime fechaFinDate = fechaFin != null ? stringToDate(fechaFin) : null;

        return service.findByFilters(proveedorId, sucursalId, estado, fechaInicioDate, fechaFinDate, pageable);
    }

    /**
     * Obtiene devoluciones por proveedor
     */
    public List<Devolucion> devolucionesPorProveedor(Long proveedorId) {
        if (proveedorId == null) {
            throw new GraphQLException("ID del proveedor es requerido");
        }
        return service.findByProveedorId(proveedorId);
    }

    /**
     * Obtiene devoluciones por estado
     */
    public List<Devolucion> devolucionesPorEstado(DevolucionEstado estado) {
        if (estado == null) {
            throw new GraphQLException("Estado es requerido");
        }
        return service.findByEstado(estado);
    }

    /**
     * Guarda una nueva devolución
     */
    @Transactional
    public Devolucion saveDevolucion(DevolucionInput input) {
        ModelMapper mapper = new ModelMapper();
        Devolucion entity = mapper.map(input, Devolucion.class);

        // Mapear relaciones
        if (input.getProveedorId() != null) {
            entity.setProveedor(proveedorService.findById(input.getProveedorId()).orElse(null));
        }

        if (input.getSucursalOrigenId() != null) {
            entity.setSucursalOrigen(sucursalService.findById(input.getSucursalOrigenId()).orElse(null));
        }

        if (input.getUsuarioId() != null) {
            entity.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }

        if (input.getFecha() != null) {
            entity.setFecha(stringToDate(input.getFecha()));
        }

        // Inferir tipo si no vino: con proveedor => CON_PROVEEDOR, sin proveedor => SIN_PROVEEDOR
        if (entity.getTipo() == null) {
            entity.setTipo(input.getProveedorId() != null
                    ? com.franco.dev.domain.operaciones.enums.TipoDevolucion.CON_PROVEEDOR
                    : com.franco.dev.domain.operaciones.enums.TipoDevolucion.SIN_PROVEEDOR);
        }

        Devolucion saved = service.save(entity);

        // Persistir items incluidos en el input (si los hay)
        if (input.getItems() != null) {
            for (com.franco.dev.graphql.operaciones.input.DevolucionItemInput itemInput : input.getItems()) {
                itemInput.setDevolucionId(saved.getId());
                devolucionItemGraphQL.saveDevolucionItem(itemInput);
            }
        }

        return saved;
    }

    /**
     * Crea una nueva devolución con datos básicos
     */
    @Transactional
    public Devolucion crearDevolucion(
            Long proveedorId,
            Long sucursalOrigenId,
            String motivo,
            Long usuarioId) {

        if (proveedorId == null || sucursalOrigenId == null || usuarioId == null) {
            throw new GraphQLException("Proveedor, sucursal de origen y usuario son requeridos");
        }

        try {
            com.franco.dev.domain.personas.Proveedor proveedor = proveedorService.findById(proveedorId)
                .orElseThrow(() -> new GraphQLException("Proveedor no encontrado: " + proveedorId));

            com.franco.dev.domain.empresarial.Sucursal sucursalOrigen = sucursalService.findById(sucursalOrigenId)
                .orElseThrow(() -> new GraphQLException("Sucursal no encontrada: " + sucursalOrigenId));

            com.franco.dev.domain.personas.Usuario usuario = usuarioService.findById(usuarioId)
                .orElseThrow(() -> new GraphQLException("Usuario no encontrado: " + usuarioId));

            return service.crearDevolucion(proveedor, sucursalOrigen, motivo, usuario);

        } catch (Exception e) {
            throw new GraphQLException("Error al crear devolución: " + e.getMessage());
        }
    }

    /**
     * FUNCIÓN CRÍTICA: Confirma una devolución
     * Genera movimientos de stock de salida y validaciones
     */
    @Transactional
    public Devolucion confirmarDevolucion(Long devolucionId) {
        if (devolucionId == null) {
            throw new GraphQLException("ID de la devolución es requerido");
        }

        try {
            System.out.println("=== CONFIRMANDO DEVOLUCIÓN ===");
            System.out.println("Devolución ID: " + devolucionId);

            Devolucion devolucion = service.confirmarDevolucion(devolucionId);

            System.out.println("=== DEVOLUCIÓN CONFIRMADA EXITOSAMENTE ===");
            System.out.println("Estado final: " + devolucion.getEstado());

            return devolucion;

        } catch (Exception e) {
            System.err.println("Error al confirmar devolución " + devolucionId + ": " + e.getMessage());
            e.printStackTrace();
            throw new GraphQLException("Error al confirmar devolución: " + e.getMessage());
        }
    }

    /**
     * Cancela una devolución en proceso
     */
    @Transactional
    public Devolucion cancelarDevolucion(Long devolucionId, String motivoCancelacion) {
        if (devolucionId == null) {
            throw new GraphQLException("ID de la devolución es requerido");
        }

        try {
            return service.cancelarDevolucion(devolucionId, motivoCancelacion);
        } catch (Exception e) {
            throw new GraphQLException("Error al cancelar devolución: " + e.getMessage());
        }
    }

    /**
     * Avanza la devolución a un nuevo estado (máquina de estados).
     * Ejecuta los efectos: baja/reingreso de stock, generación de gasto de merma.
     */
    @Transactional
    public Devolucion avanzarEstadoDevolucion(Long devolucionId, DevolucionEstado estado, Long usuarioId) {
        if (devolucionId == null || estado == null) {
            throw new GraphQLException("devolucionId y estado son requeridos");
        }
        com.franco.dev.domain.personas.Usuario usuario = usuarioId != null
                ? usuarioService.findById(usuarioId).orElse(null) : null;
        try {
            return service.avanzarEstado(devolucionId, estado, usuario);
        } catch (Exception e) {
            throw new GraphQLException(e.getMessage());
        }
    }

    /**
     * Registra la nota de crédito del proveedor y finaliza la devolución (ACREDITADO).
     */
    @Transactional
    public Devolucion acreditarDevolucion(Long devolucionId, String nroNotaCredito,
                                          Double montoAcreditado, Long usuarioId) {
        if (devolucionId == null) {
            throw new GraphQLException("devolucionId es requerido");
        }
        com.franco.dev.domain.personas.Usuario usuario = usuarioId != null
                ? usuarioService.findById(usuarioId).orElse(null) : null;
        try {
            return service.acreditar(devolucionId, nroNotaCredito, montoAcreditado, usuario);
        } catch (Exception e) {
            throw new GraphQLException(e.getMessage());
        }
    }

    /**
     * Devoluciones pendientes (no finalizadas) de un proveedor.
     * Usado por Compras para alertar al comprar a ese proveedor.
     */
    public List<Devolucion> devolucionesPendientesPorProveedor(Long proveedorId) {
        if (proveedorId == null) {
            throw new GraphQLException("ID del proveedor es requerido");
        }
        return service.devolucionesPendientesPorProveedor(proveedorId);
    }

    // ------------------------------------------------------------------
    // Retiro consolidado por proveedor + remito PDF (FASE A)
    // ------------------------------------------------------------------

    /**
     * Vista consolidada de las devoluciones SEPARADAS de un proveedor, agrupada por
     * sucursal, lista para el retiro fisico. sucursalId es opcional.
     */
    public RetiroProveedorConsolidadoDto retiroProveedorConsolidado(Long proveedorId, Long sucursalId) {
        if (proveedorId == null) {
            throw new GraphQLException("proveedorId es requerido");
        }
        try {
            return service.getRetiroConsolidado(proveedorId, sucursalId);
        } catch (Exception e) {
            throw new GraphQLException(e.getMessage());
        }
    }

    /**
     * Genera el remito PDF (Base64) para el conjunto de devoluciones indicado.
     */
    public String remitoRetiroProveedor(List<Long> devolucionIds) {
        if (devolucionIds == null || devolucionIds.isEmpty()) {
            throw new GraphQLException("devolucionIds es requerido");
        }
        try {
            return remitoRetiroService.generarRemitoRetiroProveedor(devolucionIds);
        } catch (Exception e) {
            throw new GraphQLException(e.getMessage());
        }
    }

    // ===================== Dashboard =====================

    public com.franco.dev.domain.operaciones.dto.ResumenDevolucionesDto resumenDevoluciones(
            String fechaInicio, String fechaFin, Long sucursalId) {
        return service.getResumen(stringToDate(fechaInicio), stringToDate(fechaFin), sucursalId);
    }

    public List<com.franco.dev.domain.operaciones.dto.DevolucionPorEstadoDto> devolucionesPorEstadoResumen(
            String fechaInicio, String fechaFin, Long sucursalId) {
        return service.getConteoPorEstado(stringToDate(fechaInicio), stringToDate(fechaFin), sucursalId);
    }

    public List<com.franco.dev.domain.operaciones.dto.TopProductoDevueltoDto> topProductosDevueltos(
            String fechaInicio, String fechaFin, Long sucursalId, Integer limite) {
        return service.getTopProductos(stringToDate(fechaInicio), stringToDate(fechaFin), sucursalId,
                limite != null ? limite : 10);
    }

    public List<com.franco.dev.domain.operaciones.dto.TopMotivoDevolucionDto> topMotivosDevolucion(
            String fechaInicio, String fechaFin, Long sucursalId, Integer limite) {
        return service.getTopMotivos(stringToDate(fechaInicio), stringToDate(fechaFin), sucursalId,
                limite != null ? limite : 10);
    }

    public List<com.franco.dev.domain.operaciones.dto.DevolucionSeriePuntoDto> devolucionesSeriePorDia(
            String fechaInicio, String fechaFin, Long sucursalId) {
        return service.getSeriePorDia(stringToDate(fechaInicio), stringToDate(fechaFin), sucursalId);
    }

    /**
     * Imprime el comprobante de retiro en impresora termica (58mm / 80mm).
     * El ancho llega por parametro: cuando exista el modulo de impresoras, el
     * desktop dejara de mandar el default y usara el valor registrado.
     */
    public Boolean imprimirTicketRetiroProveedor(List<Long> devolucionIds, String printerName, Integer anchoMm) {
        if (devolucionIds == null || devolucionIds.isEmpty()) {
            throw new GraphQLException("devolucionIds es requerido");
        }
        try {
            return ticketRetiroService.imprimirTicketRetiro(devolucionIds, printerName, anchoMm);
        } catch (Exception e) {
            throw new GraphQLException(e.getMessage());
        }
    }

    /**
     * Retira en bloque varias devoluciones (avanza cada una a RETIRADO, reusando
     * avanzarEstado). El fallo de una no aborta el resto; se reporta por id.
     *
     * NOTA: intencionalmente SIN @Transactional para que cada avanzarEstado abra su
     * propia transaccion. Si el metodo fuera transaccional, el rollback-only de una
     * devolucion fallida arrastraria a las exitosas (UnexpectedRollbackException).
     */
    public RetiroBloqueResultadoDto retirarDevolucionesEnBloque(List<Long> devolucionIds, Long usuarioId) {
        if (devolucionIds == null || devolucionIds.isEmpty()) {
            throw new GraphQLException("devolucionIds es requerido");
        }
        com.franco.dev.domain.personas.Usuario usuario = usuarioId != null
                ? usuarioService.findById(usuarioId).orElse(null) : null;
        return service.retirarEnBloque(devolucionIds, usuario);
    }
} 