package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.RecepcionMercaderia;
import com.franco.dev.domain.operaciones.enums.RecepcionMercaderiaEstado;
import com.franco.dev.graphql.operaciones.input.RecepcionMercaderiaInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.RecepcionMercaderiaService;
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
public class RecepcionMercaderiaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private RecepcionMercaderiaService service;

    @Autowired
    private ProveedorService proveedorService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private UsuarioService usuarioService;

    /**
     * Obtiene una recepción de mercadería por ID
     */
    public RecepcionMercaderia recepcionMercaderia(Long id) {
        if (id == null) {
            throw new GraphQLException("ID es requerido");
        }
        return service.findById(id).orElse(null);
    }

    /**
     * Busca recepciones de mercadería con filtros avanzados
     */
    public Page<RecepcionMercaderia> recepcionMercaderiaConFiltros(
            Long proveedorId,
            Long sucursalId,
            RecepcionMercaderiaEstado estado,
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
     * Obtiene recepciones por proveedor
     */
    public List<RecepcionMercaderia> recepcionesPorProveedor(Long proveedorId) {
        if (proveedorId == null) {
            throw new GraphQLException("ID del proveedor es requerido");
        }
        return service.findByProveedorId(proveedorId);
    }

    /**
     * Obtiene recepciones por estado
     */
    public List<RecepcionMercaderia> recepcionesPorEstado(RecepcionMercaderiaEstado estado) {
        if (estado == null) {
            throw new GraphQLException("Estado es requerido");
        }
        return service.findByEstado(estado);
    }

    /**
     * Guarda una nueva recepción de mercadería
     */
    @Transactional
    public RecepcionMercaderia saveRecepcionMercaderia(RecepcionMercaderiaInput input) {
        ModelMapper mapper = new ModelMapper();
        RecepcionMercaderia entity = mapper.map(input, RecepcionMercaderia.class);

        // Mapear relaciones
        if (input.getProveedorId() != null) {
            entity.setProveedor(proveedorService.findById(input.getProveedorId()).orElse(null));
        }

        if (input.getSucursalRecepcionId() != null) {
            entity.setSucursalRecepcion(sucursalService.findById(input.getSucursalRecepcionId()).orElse(null));
        }

        if (input.getMonedaId() != null) {
            entity.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        }

        if (input.getUsuarioId() != null) {
            entity.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }

        if (input.getFecha() != null) {
            entity.setFecha(stringToDate(input.getFecha()));
        }

        // TODO: Uncomment when creadoEn is added to RecepcionMercaderia entity
        // if (input.getCreadoEn() != null) {
        //     entity.setCreadoEn(stringToDate(input.getCreadoEn()));
        // }

        RecepcionMercaderia recepcionGuardada = service.save(entity);

        // Asociar notas de recepción si están especificadas
        if (input.getNotaRecepcionIds() != null && !input.getNotaRecepcionIds().isEmpty()) {
            service.asociarNotasRecepcion(recepcionGuardada.getId(), input.getNotaRecepcionIds());
        }

        return recepcionGuardada;
    }

    /**
     * FUNCIÓN CRÍTICA: Finaliza una recepción de mercadería
     * Genera movimientos de stock y actualiza costos
     */
    @Transactional
    public RecepcionMercaderia finalizarRecepcionMercaderia(Long recepcionId) {
        if (recepcionId == null) {
            throw new GraphQLException("ID de la recepción es requerido");
        }

        try {
            System.out.println("=== FINALIZANDO RECEPCIÓN DE MERCADERÍA ===");
            System.out.println("Recepción ID: " + recepcionId);

            RecepcionMercaderia recepcion = service.finalizarRecepcion(recepcionId);

            System.out.println("=== RECEPCIÓN FINALIZADA EXITOSAMENTE ===");
            System.out.println("Estado final: " + recepcion.getEstado());

            return recepcion;

        } catch (Exception e) {
            System.err.println("Error al finalizar recepción " + recepcionId + ": " + e.getMessage());
            e.printStackTrace();
            throw new GraphQLException("Error al finalizar recepción: " + e.getMessage());
        }
    }

    /**
     * Crea una nueva recepción de mercadería con datos básicos
     */
    @Transactional
    public RecepcionMercaderia crearRecepcionMercaderia(
            Long proveedorId,
            Long sucursalId,
            Long monedaId,
            Double cotizacion,
            Long usuarioId) {

        if (proveedorId == null || sucursalId == null || usuarioId == null) {
            throw new GraphQLException("Proveedor, sucursal y usuario son requeridos");
        }

        try {
            com.franco.dev.domain.personas.Proveedor proveedor = proveedorService.findById(proveedorId)
                .orElseThrow(() -> new GraphQLException("Proveedor no encontrado: " + proveedorId));

            com.franco.dev.domain.empresarial.Sucursal sucursal = sucursalService.findById(sucursalId)
                .orElseThrow(() -> new GraphQLException("Sucursal no encontrada: " + sucursalId));

            com.franco.dev.domain.financiero.Moneda moneda = monedaService.findById(monedaId)
                .orElseThrow(() -> new GraphQLException("Moneda no encontrada: " + monedaId));

            com.franco.dev.domain.personas.Usuario usuario = usuarioService.findById(usuarioId)
                .orElseThrow(() -> new GraphQLException("Usuario no encontrado: " + usuarioId));

            return service.crearRecepcion(proveedor, sucursal, moneda, 
                                        cotizacion != null ? cotizacion : 1.0, usuario);

        } catch (Exception e) {
            throw new GraphQLException("Error al crear recepción: " + e.getMessage());
        }
    }

    /**
     * Asocia notas de recepción a una recepción de mercadería
     */
    @Transactional
    public Boolean asociarNotasARecepcion(Long recepcionId, List<Long> notaRecepcionIds) {
        if (recepcionId == null || notaRecepcionIds == null || notaRecepcionIds.isEmpty()) {
            throw new GraphQLException("ID de recepción y lista de notas son requeridos");
        }

        try {
            service.asociarNotasRecepcion(recepcionId, notaRecepcionIds);
            return true;
        } catch (Exception e) {
            throw new GraphQLException("Error al asociar notas: " + e.getMessage());
        }
    }
} 