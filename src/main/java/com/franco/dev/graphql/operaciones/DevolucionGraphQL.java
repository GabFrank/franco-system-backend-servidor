package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Devolucion;
import com.franco.dev.domain.operaciones.enums.DevolucionEstado;
import com.franco.dev.graphql.operaciones.input.DevolucionInput;
import com.franco.dev.service.empresarial.SucursalService;
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

        // TODO: Handle items if included in input
        // TODO: Handle creadoEn when field is added to entity

        return service.save(entity);
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
} 