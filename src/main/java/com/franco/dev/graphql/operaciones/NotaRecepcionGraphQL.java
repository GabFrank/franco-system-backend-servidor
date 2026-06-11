package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.operaciones.dto.AsignacionResult;
import com.franco.dev.graphql.operaciones.dto.ProductoAgrupadoDTO;
import com.franco.dev.domain.operaciones.NotaRecepcionItemDistribucion;
import com.franco.dev.service.operaciones.NotaRecepcionItemDistribucionService;
import com.franco.dev.graphql.operaciones.input.NotaRecepcionInput;
import com.franco.dev.service.financiero.DocumentoService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.*;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class NotaRecepcionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private NotaRecepcionService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private CompraService compraService;
    @Autowired
    private DocumentoService documentoService;
    @Autowired
    private PedidoService pedidoService;
    @Autowired
    private PedidoItemService pedidoItemService;
    @Autowired
    private MonedaService monedaService;

    @Autowired
    private NotaRecepcionItemDistribucionService notaRecepcionItemDistribucionService;

    public Optional<NotaRecepcion> notaRecepcion(Long id) {
        return service.findById(id);
    }

    public List<NotaRecepcion> notaRecepcions(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<NotaRecepcion> notaRecepcionPorPedidoId(Long id) {
        return service.findByPedidoId(id);
    }

    public Page<NotaRecepcion> notaRecepcionPorPedidoIdAndNumero(Long id, Integer numero, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        if (numero != null) {
            String texto = "%" + numero + "%";
            return service.findByPedidoIdAndNumero(id, texto, pageable);
        } else {
            return service.findByPedidoId(id, pageable);
        }
    }

    /**
     * Agrupa productos por notas
     */
    public List<ProductoAgrupadoDTO> productosAgrupadosPorNotas(List<Long> notaRecepcionIds) {
        if (notaRecepcionIds == null || notaRecepcionIds.isEmpty()) {
            throw new GraphQLException("Se requieren IDs de notas de recepción");
        }

        List<NotaRecepcionItemDistribucion> distribuciones = notaRecepcionItemDistribucionService
                .findByNotaRecepcionIds(notaRecepcionIds);

        // Agrupar por producto
        return distribuciones.stream()
                .collect(java.util.stream.Collectors.groupingBy(d -> d.getNotaRecepcionItem().getProducto()))
                .entrySet().stream()
                .map(entry -> {
                    ProductoAgrupadoDTO dto = new ProductoAgrupadoDTO();
                    dto.setProducto(entry.getKey());
                    double total = entry.getValue().stream()
                            .mapToDouble(d -> d.getCantidad() != null ? d.getCantidad() : 0.0)
                            .sum();
                    dto.setCantidadTotalEsperada(total);
                    // Heurística simple: usar presentacion del primer item
                    if (!entry.getValue().isEmpty()) {
                        dto.setPresentacionConsolidada(
                                entry.getValue().get(0).getNotaRecepcionItem().getPresentacionEnNota());
                    }
                    dto.setDistribuciones(entry.getValue());
                    return dto;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Agrupa productos por notas con paginación y filtros
     */
    public Page<ProductoAgrupadoDTO> productosAgrupadosPorNotasPaginados(
            List<Long> notaRecepcionIds,
            Integer page,
            Integer size,
            String filtroTexto) {

        if (notaRecepcionIds == null || notaRecepcionIds.isEmpty()) {
            throw new GraphQLException("Se requieren IDs de notas de recepción");
        }

        // Crear pageable para paginación
        Pageable pageable = PageRequest.of(
                page != null ? page : 0,
                size != null ? size : 20);

        // Obtener productos agrupados paginados desde el servicio
        return notaRecepcionItemDistribucionService
                .findProductosAgrupadosPaginados(notaRecepcionIds, filtroTexto, pageable);
    }

    public NotaRecepcion saveNotaRecepcion(NotaRecepcionInput input) {
        ModelMapper m = new ModelMapper();
        NotaRecepcion e = m.map(input, NotaRecepcion.class);
        if (input.getCompraId() != null)
            e.setCompra(compraService.findById(input.getCompraId()).orElse(null));
        if (input.getDocumentoId() != null)
            e.setDocumento(documentoService.findById(input.getDocumentoId()).orElse(null));
        if (input.getPedidoId() != null)
            e.setPedido(pedidoService.findById(input.getPedidoId()).orElse(null));
        if (input.getUsuarioId() != null)
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getFecha() != null)
            e.setFecha(stringToDate(input.getFecha()));
        if (input.getCreadoEn() != null)
            e.setCreadoEn(stringToDate(input.getCreadoEn()));
        if (input.getMonedaId() != null)
            e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));

        NotaRecepcion notaGuardada = service.save(e);

        // Si assignAllItems = true, asignar automáticamente todos los items pendientes
        // del pedido
        if (Boolean.TRUE.equals(input.getAssignAllItems()) && notaGuardada.getPedido() != null) {
            service.asignarTodosLosItemsPendientes(notaGuardada.getId(), notaGuardada.getPedido().getId(),
                    notaGuardada.getUsuario());
        }

        return notaGuardada;
    }

    public Boolean deleteNotaRecepcion(Long id) {
        try {
            // With the refactor, PedidoItem no longer has direct notaRecepcion reference
            // The relationship is now through NotaRecepcionItem
            // Simply delete the NotaRecepcion - related NotaRecepcionItem entities
            // will handle their own cascade behavior
            return service.deleteById(id);
        } catch (Exception e) {
            throw new GraphQLException("Error al eliminar nota de recepción: " + e.getMessage());
        }
    }

    public Long countNotaRecepcion() {
        return service.count();
    }

    public Integer countNotaRecepcionPorPedidoId(Long id) {
        return service.getRepository().countByPedidoId(id);
    }

    /**
     * Busca NotaRecepcion por proveedor y número.
     * Cuando sucursalId es provisto, filtra solo notas con distribución para esa
     * sucursal.
     * El frontend mobile valida en procesarNotaUnica si la nota ya fue recepcionada
     * en la sucursal y muestra el mensaje correspondiente.
     *
     * Usado en:
     * - Desktop: No
     * - Mobile: Sí (componente recepcion-notas, búsqueda de notas por número)
     */
    public List<NotaRecepcion> findByProveedorAndNumero(Long id, Integer numero, Long sucursalId) {
        return service.findNotasDisponiblesParaRecepcion(numero, id, sucursalId);
    }

    public List<NotaRecepcion> findByNumero(Integer numero) {
        // return service.findByNumero(numero);
        return new ArrayList<>();
    }

    /**
     * Find NotaRecepcion available for reception with complex filtering criteria
     * Used specifically for the reception process with business logic filtering
     * 
     * @param numero      Optional nota number (can be null if proveedor is
     *                    provided)
     * @param proveedorId Optional proveedor ID (can be null if numero is provided)
     * @param sucursalId  Optional sucursal ID for filtering by
     *                    PedidoItemDistribucion
     * @return List of available NotaRecepcion for reception
     */
    public List<NotaRecepcion> findNotasDisponiblesParaRecepcion(Integer numero, Long proveedorId, Long sucursalId) {
        return service.findNotasDisponiblesParaRecepcion(numero, proveedorId, sucursalId);
    }

    /**
     * Notas pendientes para recepción (alias claro para móvil)
     */
    public List<NotaRecepcion> notasPendientes(Long sucursalId, Long proveedorId) {
        // Reutiliza el método existente con numero = null
        return service.findNotasDisponiblesParaRecepcion(null, proveedorId, sucursalId);
    }

    /**
     * Notas pendientes para recepción con paginación
     * 
     * @param sucursalId  ID de la sucursal
     * @param proveedorId ID del proveedor (opcional)
     * @param page        Número de página (0-based)
     * @param size        Tamaño de la página
     * @return Página paginada de notas pendientes
     */
    public Page<NotaRecepcion> notasPendientesPage(Long sucursalId, Long proveedorId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findNotasDisponiblesParaRecepcionPage(null, proveedorId, sucursalId, pageable);
    }

    /**
     * Asigna ítems de pedido a una nota de recepción
     * 
     * @param notaRecepcionId ID de la nota de recepción
     * @param pedidoItemIds   Lista de IDs de ítems de pedido a asignar
     * @return Resultado de la asignación con ítems creados y errores
     */
    public AsignacionResult asignarItemsANota(Long notaRecepcionId, List<Long> pedidoItemIds, Long usuarioId) {
        try {
            Usuario usuario = usuarioId != null ? usuarioService.findById(usuarioId).orElse(null) : null;
            return service.asignarItemsANota(notaRecepcionId, pedidoItemIds, usuario);
        } catch (Exception e) {
            throw new GraphQLException("Error al asignar ítems a la nota: " + e.getMessage());
        }
    }
}
