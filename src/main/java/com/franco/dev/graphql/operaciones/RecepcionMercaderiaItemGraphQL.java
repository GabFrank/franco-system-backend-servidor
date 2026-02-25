package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.RecepcionMercaderiaItem;
import com.franco.dev.domain.operaciones.NotaRecepcionItemDistribucion;
import com.franco.dev.graphql.operaciones.input.RecepcionMercaderiaItemInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.NotaRecepcionItemService;
import com.franco.dev.service.operaciones.NotaRecepcionItemDistribucionService;
import com.franco.dev.service.operaciones.RecepcionMercaderiaItemService;
import com.franco.dev.service.operaciones.RecepcionMercaderiaService;
import com.franco.dev.service.operaciones.RecepcionMercaderiaNotaService;
import com.franco.dev.service.operaciones.NotaRecepcionService;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.operaciones.ProcesoEtapaService;
import com.franco.dev.service.operaciones.RecepcionMercaderiaItemVariacionService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.domain.operaciones.RecepcionMercaderia;
import com.franco.dev.domain.operaciones.NotaRecepcionItem;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.ProcesoEtapa;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaNota;
import com.franco.dev.domain.operaciones.enums.NotaRecepcionEstado;
import com.franco.dev.domain.operaciones.enums.RecepcionMercaderiaEstado;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaTipo;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaEstado;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.operaciones.enums.MotivoRechazoFisico;
import com.franco.dev.dto.operaciones.ValidacionFinalizacionRecepcion;
import com.franco.dev.dto.operaciones.ItemPendiente;
import com.franco.dev.graphql.operaciones.dto.RecepcionSumarioDTO;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;
import com.franco.dev.domain.operaciones.EstadoVerificacion;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaItemVariacion;
import com.franco.dev.graphql.operaciones.input.RecepcionMercaderiaItemVariacionInput;

@Component
public class RecepcionMercaderiaItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private RecepcionMercaderiaItemService service;

    @Autowired
    private RecepcionMercaderiaItemVariacionService recepcionMercaderiaItemVariacionService;

    @Autowired
    private RecepcionMercaderiaService recepcionMercaderiaService;

    @Autowired
    private NotaRecepcionItemService notaRecepcionItemService;

    @Autowired
    private NotaRecepcionItemDistribucionService notaRecepcionItemDistribucionService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private PresentacionService presentacionService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private RecepcionMercaderiaNotaService recepcionMercaderiaNotaService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private NotaRecepcionService notaRecepcionService;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private ProcesoEtapaService procesoEtapaService;


    /**
     * Obtiene el ID de la recepción de mercadería
     */
    public Integer getRecepcionMercaderiaId(RecepcionMercaderiaItem item) {
        if (item.getRecepcionMercaderia() != null) {
            return item.getRecepcionMercaderia().getId().intValue();
        }
        return null;
    }

    /**
     * Obtiene un ítem de recepción por ID
     */
    public RecepcionMercaderiaItem recepcionMercaderiaItem(Long id) {
        if (id == null) {
            throw new GraphQLException("ID es requerido");
        }
        return service.findById(id).orElse(null);
    }

    /**
     * Obtiene ítems por ID de recepción de mercadería
     */
    public List<RecepcionMercaderiaItem> recepcionMercaderiaItemsPorRecepcion(Long recepcionId) {
        if (recepcionId == null) {
            throw new GraphQLException("ID de recepción es requerido");
        }
        return service.findByRecepcionMercaderiaId(recepcionId);
    }

    /**
     * Obtiene ítems por ID de recepción de mercadería con paginación y filtros
     */
    public Page<RecepcionMercaderiaItem> recepcionMercaderiaItemsPorRecepcionPaginados(
            Long recepcionId,
            Integer page,
            Integer size,
            String filtroTexto,
            List<EstadoVerificacion> estados) {

        if (recepcionId == null) {
            throw new GraphQLException("ID de recepción es requerido");
        }

        // Crear pageable para paginación
        Pageable pageable = PageRequest.of(
                page != null ? page : 0,
                size != null ? size : 20);

        // Obtener ítems paginados desde el servicio
        return service.findByRecepcionMercaderiaIdPaginados(recepcionId, filtroTexto, estados, pageable);
    }

    /**
     * Busca un item pendiente de recepción por producto en una recepción específica
     */
    public RecepcionMercaderiaItem findPendienteRecepcionItemPorProducto(Long recepcionId, Long productoId) {
        if (recepcionId == null || productoId == null) {
            throw new GraphQLException("ID de recepción y producto son requeridos");
        }

        return service.findPendienteRecepcionItemPorProducto(recepcionId, productoId);
    }

    /**
     * Obtiene ítems por producto y sucursal
     */
    public List<RecepcionMercaderiaItem> recepcionMercaderiaItemsPorProductoYSucursal(Long productoId,
            Long sucursalId) {
        if (productoId == null || sucursalId == null) {
            throw new GraphQLException("ID de producto y sucursal son requeridos");
        }
        return service.findByProductoIdAndSucursalEntregaId(productoId, sucursalId);
    }

    /**
     * Guarda un ítem de recepción de mercadería
     * Soporta tanto creación (ID == null) como actualización (ID != null)
     */
    @Transactional
    public RecepcionMercaderiaItem saveRecepcionMercaderiaItem(RecepcionMercaderiaItemInput input) {
        if (input == null) {
            throw new GraphQLException("Input es requerido");
        }

        try {
            RecepcionMercaderiaItem saved = input.getId() == null
                ? crearRecepcionMercaderiaItem(input)
                : actualizarRecepcionMercaderiaItem(input);
            // Si la nota está EN_RECEPCION y ya se guardó al menos un ítem (recibido/rechazado), pasar a RECEPCION_PARCIAL
            if (saved != null && saved.getNotaRecepcionItem() != null
                    && saved.getNotaRecepcionItem().getNotaRecepcion() != null
                    && saved.getNotaRecepcionItem().getNotaRecepcion().getId() != null) {
                notaRecepcionService.findById(saved.getNotaRecepcionItem().getNotaRecepcion().getId()).ifPresent(nota -> {
                    if (nota.getEstado() == NotaRecepcionEstado.EN_RECEPCION) {
                        nota.setEstado(NotaRecepcionEstado.RECEPCION_PARCIAL);
                        notaRecepcionService.save(nota);
                    }
                });
            }
            return saved;
        } catch (Exception e) {
            e.printStackTrace();
            throw new GraphQLException("Error al guardar ítem de recepción: " + e.getMessage());
        }
    }

    /**
     * Crea un nuevo ítem de recepción de mercadería
     */
    @Transactional
    private RecepcionMercaderiaItem crearRecepcionMercaderiaItem(RecepcionMercaderiaItemInput input) {
        // 1. Validar campos obligatorios
        if (input.getNotaRecepcionItemId() == null) {
            throw new GraphQLException("NotaRecepcionItemId es requerido para crear un ítem de recepción");
        }
        if (input.getSucursalEntregaId() == null) {
            throw new GraphQLException("SucursalEntregaId es requerido para crear un ítem de recepción");
        }
        if (input.getUsuarioId() == null) {
            throw new GraphQLException("UsuarioId es requerido para crear un ítem de recepción");
        }

        // 2. Verificar si ya existe un ítem de recepción para esta sucursal/distribución
        // Si existe y el estado es PARCIAL, actualizar automáticamente en lugar de crear uno nuevo
        List<RecepcionMercaderiaItem> itemsExistentes;
        if (input.getNotaRecepcionItemDistribucionId() != null) {
            itemsExistentes = service.getRepository().findByNotaRecepcionItemDistribucionId(
                input.getNotaRecepcionItemDistribucionId());
        } else {
            itemsExistentes = service.getRepository().findByNotaRecepcionItemIdAndSucursalId(
                input.getNotaRecepcionItemId(), 
                input.getSucursalEntregaId());
        }

        // FILTRAR: Solo considerar items de la recepción actual para evitar actualizar items de otras recepciones
        // Esto es crítico cuando hay múltiples recepciones para la misma nota en diferentes sucursales
        if (input.getRecepcionMercaderiaId() != null) {
            itemsExistentes = itemsExistentes.stream()
                    .filter(item -> item.getRecepcionMercaderia() != null && 
                                   item.getRecepcionMercaderia().getId().equals(input.getRecepcionMercaderiaId()))
                    .collect(Collectors.toList());
        }

        // Filtrar solo ítems que no estén rechazados (excluir rechazos)
        List<RecepcionMercaderiaItem> itemsVerificados = itemsExistentes.stream()
                .filter(item -> item.getCantidadRechazada() == null || item.getCantidadRechazada() == 0)
                .collect(Collectors.toList());

        // Si ya existe un item verificado para esta sucursal/distribución, verificar su estado
        if (!itemsVerificados.isEmpty()) {
            String estadoRecepcion;
            if (input.getNotaRecepcionItemDistribucionId() != null) {
                estadoRecepcion = service.getEstadoRecepcionPorDistribucion(input.getNotaRecepcionItemDistribucionId());
            } else {
                estadoRecepcion = service.getEstadoRecepcion(input.getNotaRecepcionItemId(),
                        Collections.singletonList(input.getSucursalEntregaId()));
            }

            // Si el estado es PARCIAL o PENDIENTE, actualizar el item existente sumando la cantidad recibida
            if ("PARCIAL".equals(estadoRecepcion) || "PENDIENTE".equals(estadoRecepcion)) {
                RecepcionMercaderiaItem itemExistente = itemsVerificados.get(0);

                // Calcular nueva cantidad recibida (sumar la cantidad existente con la nueva)
                Double cantidadRecibidaExistente = itemExistente.getCantidadRecibida() != null
                        ? itemExistente.getCantidadRecibida()
                        : 0.0;
                Double cantidadRecibidaNueva = input.getCantidadRecibida() != null ? input.getCantidadRecibida() : 0.0;
                Double cantidadRecibidaTotal = cantidadRecibidaExistente + cantidadRecibidaNueva;

                // Actualizar el input con el ID del item existente y la cantidad total
                input.setId(itemExistente.getId());
                input.setCantidadRecibida(cantidadRecibidaTotal);

                // Usar el método de actualización
                return actualizarRecepcionMercaderiaItem(input);
            } else {
                // Si el estado es VERIFICADO o RECHAZADO, lanzar error
                throw new GraphQLException(
                        "El ítem de nota ya está verificado completamente para esta " + 
                        (input.getNotaRecepcionItemDistribucionId() != null ? "distribución" : "sucursal") + 
                        ". Use actualización para modificar.");
            }
        }

        // 3. Obtener el NotaRecepcionItem
        NotaRecepcionItem notaItem = notaRecepcionItemService.findById(input.getNotaRecepcionItemId())
                .orElseThrow(() -> new GraphQLException(
                        "NotaRecepcionItem no encontrado con ID: " + input.getNotaRecepcionItemId()));

        // 4. Obtener o crear RecepcionMercaderia automáticamente
        RecepcionMercaderia recepcion = crearRecepcionAutomatica(input);

        // 4.5. Validación de trazabilidad: Si hay múltiples NotaRecepcionItem del mismo producto en las notas asociadas,
        // verificar que el notaRecepcionItemId sea explícito y correcto
        if (recepcion != null && recepcion.getId() != null && notaItem.getProducto() != null) {
            // Obtener todas las notas asociadas a esta recepción
            List<com.franco.dev.domain.operaciones.RecepcionMercaderiaNota> asociaciones = 
                recepcionMercaderiaNotaService.findByRecepcionMercaderiaId(recepcion.getId());
            
            if (asociaciones != null && !asociaciones.isEmpty()) {
                // Buscar todos los NotaRecepcionItem del mismo producto en las notas asociadas
                List<NotaRecepcionItem> itemsMismoProducto = new ArrayList<>();
                for (com.franco.dev.domain.operaciones.RecepcionMercaderiaNota asociacion : asociaciones) {
                    if (asociacion.getNotaRecepcion() != null && asociacion.getNotaRecepcion().getId() != null) {
                        List<NotaRecepcionItem> itemsNota = notaRecepcionItemService.findByNotaRecepcionId(
                            asociacion.getNotaRecepcion().getId());
                        for (NotaRecepcionItem item : itemsNota) {
                            if (item.getProducto() != null && 
                                item.getProducto().getId() != null && 
                                item.getProducto().getId().equals(notaItem.getProducto().getId())) {
                                itemsMismoProducto.add(item);
                            }
                        }
                    }
                }
                
                // Si hay múltiples items del mismo producto, el notaRecepcionItemId debe ser explícito
                if (itemsMismoProducto.size() > 1) {
                    // Verificar que el item especificado esté en la lista
                    boolean itemValido = itemsMismoProducto.stream()
                        .anyMatch(item -> item.getId().equals(input.getNotaRecepcionItemId()));
                    
                    if (!itemValido) {
                        throw new GraphQLException(
                            "Hay " + itemsMismoProducto.size() + " items del mismo producto (ID: " + 
                            notaItem.getProducto().getId() + ") en las notas asociadas a esta recepción. " +
                            "El notaRecepcionItemId especificado (" + input.getNotaRecepcionItemId() + 
                            ") no corresponde a ninguno de estos items. Por favor, especifique el ID correcto.");
                    }
                }
            }
        }

        // 5. Obtener entidades relacionadas
        Producto producto = null;
        if (input.getProductoId() != null) {
            producto = productoService.findById(input.getProductoId())
                    .orElseThrow(() -> new GraphQLException("Producto no encontrado con ID: " + input.getProductoId()));
        } else {
            // Si no se proporciona productoId, obtener del NotaRecepcionItem
            producto = notaItem.getProducto();
            if (producto == null) {
                throw new GraphQLException("Producto no encontrado en NotaRecepcionItem");
            }
        }

        Sucursal sucursalEntrega = sucursalService.findById(input.getSucursalEntregaId())
                .orElseThrow(
                        () -> new GraphQLException("Sucursal no encontrada con ID: " + input.getSucursalEntregaId()));

        Usuario usuario = usuarioService.findById(input.getUsuarioId())
                .orElseThrow(() -> new GraphQLException("Usuario no encontrado con ID: " + input.getUsuarioId()));

        Presentacion presentacionRecibida = null;
        if (input.getPresentacionRecibidaId() != null) {
            presentacionRecibida = presentacionService.findById(input.getPresentacionRecibidaId())
                    .orElse(null);
        }

        // 6. Vincular NotaRecepcionItemDistribucion si se proporciona
        NotaRecepcionItemDistribucion distribucion = null;
        if (input.getNotaRecepcionItemDistribucionId() != null) {
            distribucion = notaRecepcionItemDistribucionService.findById(input.getNotaRecepcionItemDistribucionId())
                    .orElse(null);
        } else {
            // Si no se proporciona, buscar por sucursal y nota item
            List<NotaRecepcionItemDistribucion> distribuciones = notaRecepcionItemDistribucionService
                    .findByNotaRecepcionItemId(notaItem.getId());
            distribucion = distribuciones.stream()
                    .filter(dist -> dist.getSucursalEntrega().getId().equals(input.getSucursalEntregaId()))
                    .findFirst()
                    .orElse(null);
        }

        // Validar que se encontró una distribución cuando hay múltiples distribuciones
        List<NotaRecepcionItemDistribucion> todasLasDistribuciones = notaRecepcionItemDistribucionService
                .findByNotaRecepcionItemId(notaItem.getId());
        if (todasLasDistribuciones.size() > 1 && distribucion == null) {
            throw new GraphQLException("No se encontró una distribución para vincular el item de recepción. " +
                    "Hay " + todasLasDistribuciones.size() + " distribuciones disponibles para este item.");
        }

        // 7. Crear nuevo RecepcionMercaderiaItem
        RecepcionMercaderiaItem item = new RecepcionMercaderiaItem();
        item.setRecepcionMercaderia(recepcion);
        item.setNotaRecepcionItem(notaItem);
        item.setNotaRecepcionItemDistribucion(distribucion);
        item.setProducto(producto);
        item.setPresentacionRecibida(presentacionRecibida);
        item.setSucursalEntrega(sucursalEntrega);
        item.setUsuario(usuario);
        item.setEsBonificacion(input.getEsBonificacion() != null ? input.getEsBonificacion() : false);
        item.setMetodoVerificacion(input.getMetodoVerificacion());
        item.setMotivoVerificacionManual(input.getMotivoVerificacionManual());
        item.setObservaciones(input.getObservaciones());
        item.setMotivoRechazo(input.getMotivoRechazo());

        // 8. Procesar variaciones si existen
        Double cantidadRecibidaTotal = input.getCantidadRecibida() != null ? input.getCantidadRecibida() : 0.0;
        Double cantidadRechazadaTotal = input.getCantidadRechazada() != null ? input.getCantidadRechazada() : 0.0;

        if (input.getVariaciones() != null && !input.getVariaciones().isEmpty()) {
            // Si hay variaciones, recalcular totales desde las variaciones
            cantidadRecibidaTotal = 0.0;
            cantidadRechazadaTotal = 0.0;

            for (RecepcionMercaderiaItemVariacionInput varInput : input.getVariaciones()) {
                RecepcionMercaderiaItemVariacion variacion = new RecepcionMercaderiaItemVariacion();
                variacion.setRecepcionMercaderiaItem(item);

                if (varInput.getPresentacionId() != null) {
                    variacion.setPresentacion(presentacionService.findById(varInput.getPresentacionId()).orElse(null));
                }
                if (varInput.getVencimiento() != null && !varInput.getVencimiento().isEmpty()) {
                    variacion.setVencimiento(stringToDate(varInput.getVencimiento()));
                }

                variacion.setCantidad(varInput.getCantidad());
                variacion.setLote(varInput.getLote());
                variacion.setRechazado(varInput.getRechazado() != null && varInput.getRechazado());
                variacion.setMotivoRechazo(varInput.getMotivoRechazo());

                recepcionMercaderiaItemVariacionService.save(variacion);

                // Sumarizar totales
                if (variacion.getRechazado()) {
                    cantidadRechazadaTotal += variacion.getCantidad() != null ? variacion.getCantidad() : 0.0;
                } else {
                    cantidadRecibidaTotal += variacion.getCantidad() != null ? variacion.getCantidad() : 0.0;
                }
            }
        }

        // 9. Establecer cantidades
        item.setCantidadRecibida(cantidadRecibidaTotal);
        item.setCantidadRechazada(cantidadRechazadaTotal);

        // 10. Calcular y establecer el estado de verificación
        actualizarEstadoVerificacion(item);

        // 11. Guardar el nuevo ítem
        return service.save(item);
    }

    /**
     * Actualiza un ítem de recepción de mercadería existente
     */
    @Transactional
    private RecepcionMercaderiaItem actualizarRecepcionMercaderiaItem(RecepcionMercaderiaItemInput input) {
        // 1. Obtener el RecepcionMercaderiaItem existente
        RecepcionMercaderiaItem item = service.findById(input.getId())
                .orElseThrow(
                        () -> new GraphQLException("RecepcionMercaderiaItem no encontrado con ID: " + input.getId()));

        // 2. Mapear campos opcionales del item principal
        if (input.getUsuarioId() != null) {
            item.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        item.setObservaciones(input.getObservaciones());
        item.setMetodoVerificacion(input.getMetodoVerificacion());
        item.setMotivoVerificacionManual(input.getMotivoVerificacionManual());

        // 2.1. Actualizar la distribución asociada si es necesario
        // Esto corrige casos donde el item fue pre-creado con una distribución incorrecta
        if (input.getNotaRecepcionItemDistribucionId() != null) {
            // Si se proporciona explícitamente, usarla
            NotaRecepcionItemDistribucion distribucion = notaRecepcionItemDistribucionService
                .findById(input.getNotaRecepcionItemDistribucionId())
                .orElse(null);
            if (distribucion != null) {
                item.setNotaRecepcionItemDistribucion(distribucion);
            }
        } else if (input.getSucursalEntregaId() != null) {
            // Si no se proporciona pero sí la sucursal, buscar la distribución correcta
            NotaRecepcionItem notaItem = item.getNotaRecepcionItem();
            if (notaItem != null) {
                List<NotaRecepcionItemDistribucion> distribuciones = notaRecepcionItemDistribucionService
                    .findByNotaRecepcionItemId(notaItem.getId());
                NotaRecepcionItemDistribucion distribucionCorrecta = distribuciones.stream()
                    .filter(dist -> dist.getSucursalEntrega() != null && 
                                   dist.getSucursalEntrega().getId().equals(input.getSucursalEntregaId()))
                    .findFirst()
                    .orElse(null);
                
                // Solo actualizar si se encontró una distribución diferente a la actual
                if (distribucionCorrecta != null && 
                    (item.getNotaRecepcionItemDistribucion() == null || 
                     !item.getNotaRecepcionItemDistribucion().getId().equals(distribucionCorrecta.getId()))) {
                    item.setNotaRecepcionItemDistribucion(distribucionCorrecta);
                }
            }
        }

        // 3. Eliminar variaciones anteriores
        recepcionMercaderiaItemVariacionService.deleteByRecepcionMercaderiaItemId(item.getId());

        Double cantidadRecibidaTotal = 0.0;
        Double cantidadRechazadaTotal = 0.0;

        // 4. Procesar y crear las nuevas variaciones
        if (input.getVariaciones() != null && !input.getVariaciones().isEmpty()) {
            for (RecepcionMercaderiaItemVariacionInput varInput : input.getVariaciones()) {
                RecepcionMercaderiaItemVariacion variacion = new RecepcionMercaderiaItemVariacion();
                variacion.setRecepcionMercaderiaItem(item);

                if (varInput.getPresentacionId() != null) {
                    variacion.setPresentacion(presentacionService.findById(varInput.getPresentacionId()).orElse(null));
                }
                if (varInput.getVencimiento() != null && !varInput.getVencimiento().isEmpty()) {
                    variacion.setVencimiento(stringToDate(varInput.getVencimiento()));
                }

                variacion.setCantidad(varInput.getCantidad());
                variacion.setLote(varInput.getLote());
                variacion.setRechazado(varInput.getRechazado() != null && varInput.getRechazado());
                variacion.setMotivoRechazo(varInput.getMotivoRechazo());

                recepcionMercaderiaItemVariacionService.save(variacion);

                // 5. Sumarizar totales
                if (variacion.getRechazado()) {
                    cantidadRechazadaTotal += variacion.getCantidad() != null ? variacion.getCantidad() : 0.0;
                } else {
                    cantidadRecibidaTotal += variacion.getCantidad() != null ? variacion.getCantidad() : 0.0;
                }
            }
        } else {
            // Si no hay variaciones, usar las cantidades directas del input
            cantidadRecibidaTotal = input.getCantidadRecibida() != null ? input.getCantidadRecibida()
                    : item.getCantidadRecibida();
            cantidadRechazadaTotal = input.getCantidadRechazada() != null ? input.getCantidadRechazada()
                    : item.getCantidadRechazada();
        }

        // 6. Actualizar el item principal con los totales sumados
        item.setCantidadRecibida(cantidadRecibidaTotal);
        item.setCantidadRechazada(cantidadRechazadaTotal);

        // 7. Calcular y actualizar el estado de verificación
        actualizarEstadoVerificacion(item);

        // 8. Guardar el ítem principal actualizado
        return service.save(item);
    }

    /**
     * Cancela la verificación de un ítem de recepción
     */
    public Boolean cancelarVerificacion(Long notaRecepcionItemId, Long sucursalId) {
        System.out.println("=== CANCELAR VERIFICACIÓN ===");
        System.out.println("NotaRecepcionItemId: " + notaRecepcionItemId);
        System.out.println("SucursalId: " + sucursalId);

        if (notaRecepcionItemId == null || sucursalId == null) {
            throw new GraphQLException("NotaRecepcionItemId y SucursalId son requeridos");
        }

        try {
            Boolean resultado = service.cancelarVerificacion(notaRecepcionItemId, sucursalId);
            System.out.println("Resultado de cancelación: " + resultado);
            return resultado;
        } catch (Exception e) {
            System.err.println("Error al cancelar verificación: " + e.getMessage());
            e.printStackTrace();
            throw new GraphQLException("Error al cancelar verificación: " + e.getMessage());
        }
    }

    /**
     * Resetea la verificación de un ítem de recepción (elimina variaciones y
     * resetea estado)
     */
    public Boolean resetearVerificacion(Long recepcionMercaderiaItemId) {
        System.out.println("=== RESETEAR VERIFICACIÓN ===");
        System.out.println("RecepcionMercaderiaItemId: " + recepcionMercaderiaItemId);

        if (recepcionMercaderiaItemId == null) {
            throw new GraphQLException("RecepcionMercaderiaItemId es requerido");
        }

        try {
            Boolean resultado = service.resetearVerificacion(recepcionMercaderiaItemId);
            System.out.println("Resultado de reseteo: " + resultado);
            return resultado;
        } catch (IllegalStateException e) {
            // Re-lanzar excepciones de validación (como la de 24 horas) sin envolver
            throw new GraphQLException(e.getMessage());
        } catch (Exception e) {
            System.err.println("Error al resetear verificación: " + e.getMessage());
            e.printStackTrace();
            throw new GraphQLException("Error al resetear verificación: " + e.getMessage());
        }
    }

    /**
     * Deshace la verificación de todos los items de un producto en una recepción
     * Útil para items que provienen de múltiples notas (distribución automática)
     * 
     * Usado en:
     * - Desktop: No
     * - Mobile: Sí (deshacer verificación de productos con múltiples notas)
     */
    public Boolean deshacerVerificacionPorProducto(Long recepcionMercaderiaId, Long productoId) {
        System.out.println("=== DESHACER VERIFICACIÓN POR PRODUCTO ===");
        System.out.println("RecepcionMercaderiaId: " + recepcionMercaderiaId);
        System.out.println("ProductoId: " + productoId);

        if (recepcionMercaderiaId == null || productoId == null) {
            throw new GraphQLException("RecepcionMercaderiaId y ProductoId son requeridos");
        }

        try {
            Boolean resultado = service.deshacerVerificacionPorProducto(recepcionMercaderiaId, productoId);
            System.out.println("Resultado de deshacer verificación por producto: " + resultado);
            return resultado;
        } catch (IllegalStateException e) {
            // Re-lanzar excepciones de validación (como la de 24 horas) sin envolver
            throw new GraphQLException(e.getMessage());
        } catch (Exception e) {
            System.err.println("Error al deshacer verificación por producto: " + e.getMessage());
            e.printStackTrace();
            throw new GraphQLException("Error al deshacer verificación por producto: " + e.getMessage());
        }
    }

    /**
     * Verificación de producto para mobile.
     * Distribuye cantidades entre distribuciones automáticamente.
     *
     * Usado en:
     * - Desktop: No
     * - Mobile: Sí (verificación de producto en recepción)
     */
    @Transactional
    public Boolean verificarProductoMobile(
            Long recepcionMercaderiaId,
            Long productoId,
            Double cantidadRecibida,
            Double cantidadRechazada,
            Long notaRecepcionItemIdParaRechazo,
            String motivoRechazo,
            String metodoVerificacion,
            Long usuarioId
    ) {
        if (recepcionMercaderiaId == null || productoId == null || usuarioId == null) {
            throw new GraphQLException("recepcionMercaderiaId, productoId y usuarioId son requeridos");
        }
        Usuario usuario = usuarioService.findById(usuarioId)
                .orElseThrow(() -> new GraphQLException("Usuario no encontrado: " + usuarioId));
        return service.verificarProductoMobile(
                recepcionMercaderiaId,
                productoId,
                cantidadRecibida,
                cantidadRechazada,
                notaRecepcionItemIdParaRechazo,
                motivoRechazo,
                metodoVerificacion,
                usuario
        );
    }

    /**
     * Cancela el rechazo de un ítem de recepción
     */
    public Boolean cancelarRechazo(Long notaRecepcionItemId, Long sucursalId) {
        System.out.println("=== CANCELAR RECHAZO ===");
        System.out.println("NotaRecepcionItemId: " + notaRecepcionItemId);
        System.out.println("SucursalId: " + sucursalId);

        if (notaRecepcionItemId == null || sucursalId == null) {
            throw new GraphQLException("NotaRecepcionItemId y SucursalId son requeridos");
        }

        try {
            Boolean resultado = service.cancelarRechazo(notaRecepcionItemId, sucursalId);
            System.out.println("Resultado de cancelación de rechazo: " + resultado);
            return resultado;
        } catch (Exception e) {
            System.err.println("Error al cancelar rechazo: " + e.getMessage());
            e.printStackTrace();
            throw new GraphQLException("Error al cancelar rechazo: " + e.getMessage());
        }
    }

    /**
     * Rechaza un ítem de recepción con múltiples distribuciones
     */
    @Transactional
    public Boolean rechazarItem(Long notaRecepcionItemId, Long presentacionId, List<RechazoInput> rechazos,
            Long usuarioId) {
        System.out.println("=== RECHAZAR ÍTEM ===");
        System.out.println("NotaRecepcionItemId: " + notaRecepcionItemId);
        System.out.println("PresentacionId: " + presentacionId);
        System.out.println("UsuarioId: " + usuarioId);
        System.out.println("Cantidad de rechazos: " + (rechazos != null ? rechazos.size() : 0));

        if (notaRecepcionItemId == null) {
            throw new GraphQLException("NotaRecepcionItemId es requerido");
        }

        if (rechazos == null || rechazos.isEmpty()) {
            throw new GraphQLException("Lista de rechazos es requerida");
        }

        if (usuarioId == null) {
            throw new GraphQLException("UsuarioId es requerido");
        }

        try {
            // 1. Obtener el NotaRecepcionItem
            NotaRecepcionItem notaItem = notaRecepcionItemService.findById(notaRecepcionItemId)
                    .orElseThrow(() -> new RuntimeException("NotaRecepcionItem no encontrado: " + notaRecepcionItemId));

            // 2. Obtener la presentación si se proporciona
            Presentacion presentacion = null;
            if (presentacionId != null) {
                presentacion = presentacionService.findById(presentacionId)
                        .orElseThrow(() -> new RuntimeException("Presentación no encontrada: " + presentacionId));
            }

            // 3. Obtener el usuario
            Usuario usuario = usuarioService.findById(usuarioId)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + usuarioId));

            // 4. Procesar cada rechazo
            for (RechazoInput rechazo : rechazos) {
                System.out.println("Procesando rechazo para sucursal " + rechazo.getSucursalId() +
                        ": cantidad=" + rechazo.getCantidadRechazada() + ", motivo=" + rechazo.getMotivoRechazo() +
                        ", distribucionId=" + rechazo.getNotaRecepcionItemDistribucionId());

                // Crear RecepcionMercaderiaItem para el rechazo
                RecepcionMercaderiaItem itemRechazo = new RecepcionMercaderiaItem();

                // Obtener o crear RecepcionMercaderia
                RecepcionMercaderia recepcion = recepcionMercaderiaService.obtenerOcrearRecepcion(
                        notaItem.getNotaRecepcion().getPedido().getProveedor().getId(),
                        rechazo.getSucursalId(),
                        notaItem.getNotaRecepcion().getPedido().getMoneda().getId(),
                        1.0, // cotización por defecto
                        usuarioId);

                // Vincular NotaRecepcionItemDistribucion si se proporciona
                NotaRecepcionItemDistribucion distribucion = null;
                if (rechazo.getNotaRecepcionItemDistribucionId() != null) {
                    distribucion = notaRecepcionItemDistribucionService
                            .findById(rechazo.getNotaRecepcionItemDistribucionId())
                            .orElse(null);
                    if (distribucion != null) {
                        System.out.println("Distribución vinculada: " + distribucion.getId() +
                                ", cantidad: " + distribucion.getCantidad());
                    } else {
                        System.out.println("Advertencia: No se encontró distribución con ID: " +
                                rechazo.getNotaRecepcionItemDistribucionId());
                    }
                } else {
                    // Si no se proporciona, buscar por sucursal y nota item
                    List<NotaRecepcionItemDistribucion> distribuciones = notaRecepcionItemDistribucionService
                            .findByNotaRecepcionItemId(notaItem.getId());
                    distribucion = distribuciones.stream()
                            .filter(dist -> dist.getSucursalEntrega().getId().equals(rechazo.getSucursalId()))
                            .findFirst()
                            .orElse(null);
                    if (distribucion != null) {
                        System.out.println("Distribución encontrada automáticamente: " + distribucion.getId());
                    }
                }

                itemRechazo.setRecepcionMercaderia(recepcion);
                itemRechazo.setNotaRecepcionItem(notaItem);
                itemRechazo.setNotaRecepcionItemDistribucion(distribucion);
                itemRechazo.setProducto(notaItem.getProducto());
                itemRechazo.setPresentacionRecibida(presentacion);
                itemRechazo.setSucursalEntrega(sucursalService.findById(rechazo.getSucursalId())
                        .orElseThrow(() -> new RuntimeException("Sucursal no encontrada: " + rechazo.getSucursalId())));
                itemRechazo.setUsuario(usuario);
                itemRechazo.setCantidadRecibida(0.0); // No se recibe nada
                itemRechazo.setCantidadRechazada(rechazo.getCantidadRechazada());
                itemRechazo.setMotivoRechazo(rechazo.getMotivoRechazo());
                itemRechazo.setObservaciones(rechazo.getObservaciones());
                itemRechazo.setEsBonificacion(notaItem.getEsBonificacion());

                // Calcular y establecer el estado de verificación
                actualizarEstadoVerificacion(itemRechazo);

                // Guardar el ítem de rechazo
                RecepcionMercaderiaItem itemGuardado = service.save(itemRechazo);
                System.out.println("Rechazo guardado con ID: " + itemGuardado.getId());
            }

            System.out.println("=== Rechazo de ítem completado exitosamente ===");
            return true;

        } catch (Exception e) {
            System.err.println("Error al rechazar ítem: " + e.getMessage());
            e.printStackTrace();
            throw new GraphQLException("Error al rechazar ítem: " + e.getMessage());
        }
    }

    /**
     * Crea una recepción automática basada en el input
     */
    private RecepcionMercaderia crearRecepcionAutomatica(RecepcionMercaderiaItemInput input) {
        // Obtener datos del ítem de nota para crear la recepción
        NotaRecepcionItem notaItem = notaRecepcionItemService.findById(input.getNotaRecepcionItemId())
                .orElseThrow(() -> new GraphQLException("Ítem de nota no encontrado para crear recepción"));

        NotaRecepcion nota = notaItem.getNotaRecepcion();
        Pedido pedido = nota.getPedido();

        // Usar sucursal de entrega como sucursal de recepción
        Long sucursalRecepcionId = input.getSucursalEntregaId();

        // Obtener o crear recepción según las reglas de negocio
        RecepcionMercaderia recepcion = recepcionMercaderiaService.obtenerOcrearRecepcion(
                pedido.getProveedor().getId(),
                sucursalRecepcionId,
                pedido.getMoneda().getId(),
                1.0, // cotizacion por defecto
                input.getUsuarioId());

        // Asociar la nota a la recepción si no está ya asociada
        if (!recepcionMercaderiaNotaService.existeAsociacion(recepcion.getId(), nota.getId())) {
            recepcionMercaderiaNotaService.asociarNotaARecepcion(recepcion, nota.getId());
        }

        // Iniciar la etapa RECEPCION_MERCADERIA si está en PENDIENTE
        // Esto ocurre cuando se crea el primer ítem de recepción automáticamente
        if (pedido != null && pedido.getId() != null) {
            try {
                procesoEtapaService.actualizarEtapaAEnProceso(
                        pedido.getId(),
                        ProcesoEtapaTipo.RECEPCION_MERCADERIA);
            } catch (Exception e) {
                // Si la etapa no existe o ya está en proceso, no es un error crítico
                // Solo loguear para debugging
                System.out.println("No se pudo iniciar etapa RECEPCION_MERCADERIA: " + e.getMessage());
            }
        }

        return recepcion;
    }

    /**
     * Clase para representar un rechazo individual en GraphQL
     */
    public static class RechazoInput {
        private Long sucursalId;
        private Long notaRecepcionItemDistribucionId;
        private Double cantidadRechazada;
        private MotivoRechazoFisico motivoRechazo;
        private String observaciones;

        // Constructores
        public RechazoInput() {
        }

        public RechazoInput(Long sucursalId, Double cantidadRechazada, MotivoRechazoFisico motivoRechazo,
                String observaciones) {
            this.sucursalId = sucursalId;
            this.cantidadRechazada = cantidadRechazada;
            this.motivoRechazo = motivoRechazo;
            this.observaciones = observaciones;
        }

        // Getters y Setters
        public Long getSucursalId() {
            return sucursalId;
        }

        public void setSucursalId(Long sucursalId) {
            this.sucursalId = sucursalId;
        }

        public Long getNotaRecepcionItemDistribucionId() {
            return notaRecepcionItemDistribucionId;
        }

        public void setNotaRecepcionItemDistribucionId(Long notaRecepcionItemDistribucionId) {
            this.notaRecepcionItemDistribucionId = notaRecepcionItemDistribucionId;
        }

        public Double getCantidadRechazada() {
            return cantidadRechazada;
        }

        public void setCantidadRechazada(Double cantidadRechazada) {
            this.cantidadRechazada = cantidadRechazada;
        }

        public MotivoRechazoFisico getMotivoRechazo() {
            return motivoRechazo;
        }

        public void setMotivoRechazo(MotivoRechazoFisico motivoRechazo) {
            this.motivoRechazo = motivoRechazo;
        }

        public String getObservaciones() {
            return observaciones;
        }

        public void setObservaciones(String observaciones) {
            this.observaciones = observaciones;
        }
    }

    /**
     * Valida si se puede finalizar la recepción física por pedido
     */
    @Transactional
    public Boolean recepcionarTodoPorNota(Long notaId, List<Long> sucursalesIds, Long usuarioId, List<Long> itemIds) {
        System.out.println("=== RECEPCIONAR TODO POR NOTA ===");
        System.out.println("NotaId: " + notaId);
        System.out.println("SucursalesIds: " + sucursalesIds);
        System.out.println("UsuarioId: " + usuarioId);
        System.out.println("ItemIds: " + (itemIds != null ? itemIds.size() : "TODOS"));

        if (notaId == null || sucursalesIds == null || sucursalesIds.isEmpty() || usuarioId == null) {
            throw new GraphQLException("notaId, sucursalesIds y usuarioId son requeridos");
        }

        try {
            // 1. Obtener la nota
            notaRecepcionService.findById(notaId)
                    .orElseThrow(() -> new GraphQLException("Nota de recepción no encontrada: " + notaId));

            // 2. Obtener todas las distribuciones de la nota para las sucursales dadas
            List<NotaRecepcionItemDistribucion> distribuciones = notaRecepcionItemDistribucionService
                    .findByNotaRecepcionId(notaId);
            List<NotaRecepcionItemDistribucion> distribucionesFiltradas = distribuciones.stream()
                    .filter(dist -> sucursalesIds.contains(dist.getSucursalEntrega().getId()))
                    .filter(dist -> itemIds == null || itemIds.contains(dist.getNotaRecepcionItem().getId()))
                    .collect(Collectors.toList());

            if (distribucionesFiltradas.isEmpty()) {
                System.out.println("No hay distribuciones para procesar en la nota " + notaId);
                return true; // Nada que recepcionar, pero no es un error
            }

            int itemsProcesados = 0;

            // 3. Procesar cada distribución
            for (NotaRecepcionItemDistribucion dist : distribucionesFiltradas) {
                // Obtener cuánto se ha recibido/rechazado de esta distribución específica
                List<RecepcionMercaderiaItem> itemsExistentes = service.getRepository()
                        .findByNotaRecepcionItemDistribucionId(dist.getId());

                double totalRecibido = itemsExistentes.stream()
                        .mapToDouble(item -> item.getCantidadRecibida() != null ? item.getCantidadRecibida() : 0.0)
                        .sum();
                double totalRechazado = itemsExistentes.stream()
                        .mapToDouble(item -> item.getCantidadRechazada() != null ? item.getCantidadRechazada() : 0.0)
                        .sum();

                double cantidadEsperada = dist.getCantidad() != null ? dist.getCantidad() : 0.0;
                double cantidadPendiente = cantidadEsperada - totalRecibido - totalRechazado;

                if (cantidadPendiente > 0.001) {
                    System.out.println(
                            "Recepcionando ítem: " + dist.getNotaRecepcionItem().getProducto().getDescripcion() +
                                    " - Cantidad: " + cantidadPendiente + " en sucursal: "
                                    + dist.getSucursalEntrega().getNombre());

                    // Crear RecepcionMercaderiaItemInput para reutilizar la lógica de guardado
                    RecepcionMercaderiaItemInput input = new RecepcionMercaderiaItemInput();
                    input.setNotaRecepcionItemId(dist.getNotaRecepcionItem().getId());
                    input.setNotaRecepcionItemDistribucionId(dist.getId());
                    input.setProductoId(dist.getNotaRecepcionItem().getProducto().getId());
                    input.setSucursalEntregaId(dist.getSucursalEntrega().getId());
                    input.setUsuarioId(usuarioId);
                    input.setCantidadRecibida(cantidadPendiente);
                    input.setCantidadRechazada(0.0);
                    input.setEsBonificacion(dist.getNotaRecepcionItem().getEsBonificacion() != null
                            ? dist.getNotaRecepcionItem().getEsBonificacion()
                            : false);

                    // Reutilizar la lógica de creación existente (esto maneja el header de
                    // recepción, etc.)
                    saveRecepcionMercaderiaItem(input);
                    itemsProcesados++;
                }
            }

            System.out.println("Recepción masiva completada. Ítems creados/actualizados: " + itemsProcesados);
            return true;
        } catch (Exception e) {
            System.err.println("Error al recepcionar todo por nota: " + e.getMessage());
            e.printStackTrace();
            throw new GraphQLException("Error al recepcionar todo por nota: " + e.getMessage());
        }
    }

    /**
     * Deshace (cancela) la verificación física de TODOS los ítems de una nota para las sucursales dadas.
     * Implementación masiva (backend-driven) similar a recepcionarTodoPorNota.
     *
     * - Elimina RecepcionMercaderiaItem (y por cascade sus variaciones)
     * - Si el cabezal RecepcionMercaderia queda vacío, elimina también sus asociaciones (RecepcionMercaderiaNota) y el cabezal
     *
     * @param notaId ID de la nota de recepción
     * @param sucursalesIds IDs de sucursales de entrega a procesar
     * @param usuarioId ID del usuario (auditoría / validación)
     * @param itemIds opcional: IDs de NotaRecepcionItem a restringir (para selección)
     */
    @Transactional
    public Boolean deshacerVerificacionTodoPorNota(Long notaId, List<Long> sucursalesIds, Long usuarioId, List<Long> itemIds) {
        System.out.println("=== DESHACER VERIFICACIÓN TODO POR NOTA ===");
        System.out.println("NotaId: " + notaId);
        System.out.println("SucursalesIds: " + sucursalesIds);
        System.out.println("UsuarioId: " + usuarioId);
        System.out.println("ItemIds: " + (itemIds != null ? itemIds.size() : "TODOS"));

        if (notaId == null || sucursalesIds == null || sucursalesIds.isEmpty() || usuarioId == null) {
            throw new GraphQLException("notaId, sucursalesIds y usuarioId son requeridos");
        }

        try {
            // Validar que exista la nota (y mantener consistencia con recepcionarTodoPorNota)
            notaRecepcionService.findById(notaId)
                    .orElseThrow(() -> new GraphQLException("Nota de recepción no encontrada: " + notaId));

            // Obtener distribuciones de la nota para las sucursales dadas (y filtrar itemIds si corresponde)
            List<NotaRecepcionItemDistribucion> distribuciones = notaRecepcionItemDistribucionService
                    .findByNotaRecepcionId(notaId);
            List<NotaRecepcionItemDistribucion> distribucionesFiltradas = distribuciones.stream()
                    .filter(dist -> sucursalesIds.contains(dist.getSucursalEntrega().getId()))
                    .filter(dist -> itemIds == null || itemIds.contains(dist.getNotaRecepcionItem().getId()))
                    .collect(Collectors.toList());

            if (distribucionesFiltradas.isEmpty()) {
                System.out.println("No hay distribuciones para procesar en la nota " + notaId);
                return true;
            }

            // Eliminar items de recepción asociados a cada distribución
            int itemsEliminados = 0;
            for (NotaRecepcionItemDistribucion dist : distribucionesFiltradas) {
                List<RecepcionMercaderiaItem> itemsExistentes = service.getRepository()
                        .findByNotaRecepcionItemDistribucionId(dist.getId());

                if (itemsExistentes == null || itemsExistentes.isEmpty()) {
                    continue;
                }

                // Eliminar cada item usando repository (cascade se encarga de variaciones)
                for (RecepcionMercaderiaItem item : itemsExistentes) {
                    service.getRepository().delete(item);
                    itemsEliminados++;
                }
            }

            // Flush para que el cleanup de cabezales sea consistente
            service.getRepository().flush();

            // Cleanup: eliminar cabezales vacíos que estén asociados a esta nota
            // (si hay asociaciones duplicadas, esto igual los limpia)
            List<RecepcionMercaderiaNota> asociaciones = recepcionMercaderiaNotaService.findByNotaRecepcionId(notaId);
            if (asociaciones != null && !asociaciones.isEmpty()) {
                for (RecepcionMercaderiaNota asoc : asociaciones) {
                    Long recepcionId = asoc.getRecepcionMercaderia() != null ? asoc.getRecepcionMercaderia().getId() : null;
                    if (recepcionId == null) continue;

                    Long count = service.getRepository().countByRecepcionMercaderiaId(recepcionId);
                    if (count != null && count == 0) {
                        // Eliminar asociaciones por recepción y luego el cabezal (misma lógica que en service)
                        recepcionMercaderiaNotaService.deleteByRecepcionId(recepcionId);
                        RecepcionMercaderia managedRecepcion = entityManager.find(RecepcionMercaderia.class, recepcionId);
                        if (managedRecepcion != null) {
                            entityManager.remove(managedRecepcion);
                            entityManager.flush();
                        }
                    }
                }
            }

            System.out.println("Deshacer verificación masivo completado. Items eliminados: " + itemsEliminados);
            return true;
        } catch (Exception e) {
            System.err.println("Error al deshacer verificación todo por nota: " + e.getMessage());
            e.printStackTrace();
            throw new GraphQLException("Error al deshacer verificación todo por nota: " + e.getMessage());
        }
    }

    public ValidacionFinalizacionRecepcion validarFinalizacionRecepcionPorPedido(Long pedidoId,
            List<Long> sucursalesIds) {
        System.out.println("=== VALIDACIÓN FINALIZACIÓN RECEPCIÓN POR PEDIDO ===");
        System.out.println("PedidoId: " + pedidoId);
        System.out.println("SucursalesIds: " + sucursalesIds);

        if (pedidoId == null) {
            throw new GraphQLException("PedidoId es requerido");
        }

        if (sucursalesIds == null || sucursalesIds.isEmpty()) {
            throw new GraphQLException("Lista de sucursales es requerida");
        }

        try {
            // 1. Obtener todas las notas de recepción del pedido
            List<NotaRecepcion> notasPedido = notaRecepcionService.findByPedidoId(pedidoId);
            System.out.println("Notas de recepción encontradas: " + notasPedido.size());

            List<ItemPendiente> itemsPendientes = new ArrayList<>();

            for (NotaRecepcion nota : notasPedido) {
                System.out.println("Procesando nota: " + nota.getNumero());

                // 2. Obtener todos los ítems de la nota
                List<NotaRecepcionItem> itemsNota = notaRecepcionItemService.findByNotaRecepcionId(nota.getId());
                System.out.println("Ítems de nota encontrados: " + itemsNota.size());

                for (NotaRecepcionItem item : itemsNota) {
                    // 3. Verificar si el ítem tiene distribuciones en las sucursales seleccionadas
                    List<NotaRecepcionItemDistribucion> distribuciones = notaRecepcionItemDistribucionService
                            .findByNotaRecepcionItemId(item.getId());

                    // Filtrar solo distribuciones de las sucursales seleccionadas
                    List<NotaRecepcionItemDistribucion> distribucionesFiltradas = distribuciones.stream()
                            .filter(dist -> sucursalesIds.contains(dist.getSucursalEntrega().getId()))
                            .collect(Collectors.toList());

                    if (distribucionesFiltradas.isEmpty()) {
                        System.out.println(
                                "No hay distribuciones para las sucursales seleccionadas en ítem: " + item.getId());
                        continue; // No hay distribuciones para las sucursales seleccionadas
                    }

                    // 4. Calcular cantidad esperada total para las sucursales seleccionadas
                    double cantidadEsperadaTotal = distribucionesFiltradas.stream()
                            .mapToDouble(NotaRecepcionItemDistribucion::getCantidad)
                            .sum();

                    System.out.println(
                            "Cantidad esperada total para ítem " + item.getId() + ": " + cantidadEsperadaTotal);

                    // 5. Buscar todas las recepciones de mercadería asociadas a esta nota
                    List<RecepcionMercaderiaNota> recepcionesNota = recepcionMercaderiaNotaService
                            .findByNotaRecepcionId(nota.getId());
                    System.out.println("Recepciones de mercadería encontradas: " + recepcionesNota.size());

                    double totalRecibido = 0.0;
                    double totalRechazado = 0.0;

                    for (RecepcionMercaderiaNota recepcionNota : recepcionesNota) {
                        // 6. Obtener ítems de recepción para las sucursales seleccionadas
                        List<RecepcionMercaderiaItem> itemsRecepcion = service.findByRecepcionMercaderiaIdAndSucursales(
                                recepcionNota.getRecepcionMercaderia().getId(),
                                sucursalesIds);

                        // Sumar cantidades recibidas y rechazadas
                        for (RecepcionMercaderiaItem itemRecepcion : itemsRecepcion) {
                            if (itemRecepcion.getNotaRecepcionItem().getId().equals(item.getId())) {
                                totalRecibido += (itemRecepcion.getCantidadRecibida() != null
                                        ? itemRecepcion.getCantidadRecibida()
                                        : 0.0);
                                totalRechazado += (itemRecepcion.getCantidadRechazada() != null
                                        ? itemRecepcion.getCantidadRechazada()
                                        : 0.0);
                            }
                        }
                    }

                    System.out.println("Total recibido: " + totalRecibido + ", Total rechazado: " + totalRechazado);

                    // 7. Verificar si está completo
                    if (totalRecibido + totalRechazado < cantidadEsperadaTotal) {
                        ItemPendiente itemPendiente = new ItemPendiente(
                                item.getId(),
                                item.getProducto().getDescripcion(),
                                nota.getNumero().toString(),
                                "Cantidad incompleta",
                                cantidadEsperadaTotal,
                                totalRecibido,
                                totalRechazado);
                        itemsPendientes.add(itemPendiente);
                        System.out.println("Ítem pendiente agregado: " + item.getProducto().getDescripcion());
                    }
                }
            }

            boolean puedeFinalizar = itemsPendientes.isEmpty();
            String mensaje = puedeFinalizar ? "Se puede finalizar la recepción física"
                    : "No se puede finalizar. Hay " + itemsPendientes.size() + " ítem(s) pendiente(s)";

            System.out.println("=== RESULTADO VALIDACIÓN ===");
            System.out.println("Puede finalizar: " + puedeFinalizar);
            System.out.println("Ítems pendientes: " + itemsPendientes.size());
            System.out.println("Mensaje: " + mensaje);

            return new ValidacionFinalizacionRecepcion(
                    puedeFinalizar,
                    itemsPendientes,
                    itemsPendientes.size(),
                    mensaje);

        } catch (Exception e) {
            System.err.println("Error en validación de finalización: " + e.getMessage());
            e.printStackTrace();
            throw new GraphQLException("Error en validación de finalización: " + e.getMessage());
        }
    }

    /**
     * Finaliza la recepción física por pedido
     * Refactorizado para usar finalizarRecepcion() del service y manejar
     * correctamente múltiples sucursales
     */
    @Transactional
    public Boolean finalizarRecepcionFisicaPorPedido(Long pedidoId, List<Long> sucursalesIds) {
        System.out.println("=== FINALIZAR RECEPCIÓN FÍSICA POR PEDIDO ===");
        System.out.println("PedidoId: " + pedidoId);
        System.out.println("SucursalesIds: " + sucursalesIds);

        if (pedidoId == null) {
            throw new GraphQLException("PedidoId es requerido");
        }

        if (sucursalesIds == null || sucursalesIds.isEmpty()) {
            throw new GraphQLException("Lista de sucursales es requerida");
        }

        try {
            // 1. Validar estado de etapa
            ProcesoEtapa etapa = procesoEtapaService.getEtapaByPedidoAndTipo(
                    pedidoId,
                    ProcesoEtapaTipo.RECEPCION_MERCADERIA)
                    .orElseThrow(() -> new GraphQLException(
                            "Etapa de recepción de mercadería no encontrada para el pedido: " + pedidoId));

            if (etapa.getEstadoEtapa() != ProcesoEtapaEstado.EN_PROCESO) {
                throw new GraphQLException(
                        "La etapa de recepción de mercadería debe estar en proceso para finalizar. Estado actual: "
                                + etapa.getEstadoEtapa());
            }

            // 2. Validar que se puede finalizar (verificar items pendientes)
            ValidacionFinalizacionRecepcion validacion = validarFinalizacionRecepcionPorPedido(pedidoId, sucursalesIds);

            if (!validacion.getPuedeFinalizar()) {
                throw new GraphQLException("No se puede finalizar la recepción física: " + validacion.getMensaje());
            }

            // 3. Obtener el pedido para verificar que existe
            Pedido pedido = pedidoService.findById(pedidoId)
                    .orElseThrow(() -> new GraphQLException("Pedido no encontrado: " + pedidoId));

            // 4. Obtener todas las recepciones del pedido
            List<RecepcionMercaderia> recepciones = recepcionMercaderiaService.findByPedidoId(pedidoId);

            if (recepciones.isEmpty()) {
                throw new GraphQLException("No se encontraron recepciones para el pedido: " + pedidoId);
            }

            System.out.println("Recepciones encontradas: " + recepciones.size());

            // 5. Identificar recepciones que tienen items en las sucursales seleccionadas
            // Una recepción puede tener items para múltiples sucursales, así que
            // necesitamos
            // identificar qué recepciones tienen items en las sucursales seleccionadas
            List<RecepcionMercaderia> recepcionesAProcesar = new ArrayList<>();

            for (RecepcionMercaderia recepcion : recepciones) {
                // Si la recepción ya está finalizada, no procesarla
                if (recepcion.getEstado() == RecepcionMercaderiaEstado.FINALIZADA) {
                    System.out.println("Recepción " + recepcion.getId() + " ya está finalizada, omitiendo");
                    continue;
                }

                // Verificar si esta recepción tiene items en las sucursales seleccionadas
                List<RecepcionMercaderiaItem> itemsRecepcion = service.findByRecepcionMercaderiaId(recepcion.getId());
                boolean tieneItemsEnSucursalesSeleccionadas = itemsRecepcion.stream()
                        .anyMatch(item -> sucursalesIds.contains(item.getSucursalEntrega().getId()));

                if (tieneItemsEnSucursalesSeleccionadas) {
                    recepcionesAProcesar.add(recepcion);
                    System.out.println("Recepción " + recepcion.getId() + " tiene items en sucursales seleccionadas");
                }
            }

            if (recepcionesAProcesar.isEmpty()) {
                throw new GraphQLException("No se encontraron recepciones con items en las sucursales seleccionadas");
            }

            System.out.println("Recepciones a procesar: " + recepcionesAProcesar.size());

            // 6. Finalizar cada recepción usando el método del service
            // Este método genera movimientos de stock, actualiza costos con prorrateo,
            // y cambia el estado a FINALIZADA, pero NO actualiza etapas del proceso
            // TODO: FALTA IMPLEMENTAR GENERACIÓN DE CONSTANCIA DE RECEPCIÓN EN APP DESKTOP
            //       Actualmente solo se genera en el flujo móvil (finalizarRecepcionMercaderia).
            //       Según RF-25 del manual, debería generarse automáticamente al finalizar.
            //       Ver: ConstanciaDeRecepcionService.generarConstancia() y
            //       RecepcionMercaderiaGraphQL.finalizarRecepcionMercaderia() para referencia.
            for (RecepcionMercaderia recepcion : recepcionesAProcesar) {
                System.out.println("Finalizando recepción ID: " + recepcion.getId());
                recepcionMercaderiaService.finalizarRecepcion(recepcion.getId());
                System.out.println("Recepción " + recepcion.getId() + " finalizada exitosamente");
            }

            // 7. Verificar si TODAS las recepciones del pedido están finalizadas
            // Solo si todas están finalizadas, entonces finalizar la etapa del pedido
            List<RecepcionMercaderia> todasRecepciones = recepcionMercaderiaService.findByPedidoId(pedidoId);
            boolean todasFinalizadas = todasRecepciones.stream()
                    .allMatch(rm -> rm.getEstado() == RecepcionMercaderiaEstado.FINALIZADA);

            System.out.println("Todas las recepciones finalizadas: " + todasFinalizadas);
            System.out.println("Total recepciones: " + todasRecepciones.size());

            if (todasFinalizadas) {
                // Finalizar etapa RECEPCION_MERCADERIA del pedido
                procesoEtapaService.finalizarEtapa(pedidoId, ProcesoEtapaTipo.RECEPCION_MERCADERIA);
                System.out.println("Etapa RECEPCION_MERCADERIA finalizada para el pedido " + pedidoId);

                // Crear etapa SOLICITUD_PAGO (crearEtapaSiguiente verifica si ya existe)
                procesoEtapaService.crearEtapaSiguiente(pedido, ProcesoEtapaTipo.RECEPCION_MERCADERIA);
                System.out.println("Etapa SOLICITUD_PAGO creada/verificada para el pedido " + pedidoId);
            } else {
                System.out.println("Aún hay recepciones pendientes. La etapa se mantiene en EN_PROCESO");
            }

            System.out.println("=== RECEPCIÓN FÍSICA FINALIZADA EXITOSAMENTE ===");
            System.out.println("Pedido ID: " + pedidoId);
            System.out.println("Recepciones finalizadas: " + recepcionesAProcesar.size());
            System.out.println("Todas las recepciones finalizadas: " + todasFinalizadas);

            return true;

        } catch (Exception e) {
            System.err.println("Error al finalizar recepción física: " + e.getMessage());
            e.printStackTrace();
            throw new GraphQLException("Error al finalizar recepción física: " + e.getMessage());
        }
    }

    /**
     * Actualiza el estado de verificación de un ítem basado en las cantidades
     * Implementa lógica inteligente para casos de recepción parcial + rechazo
     * parcial
     */
    private void actualizarEstadoVerificacion(RecepcionMercaderiaItem item) {
        if (item == null)
            return;

        try {
            // Obtener la cantidad esperada de la distribución
            Double cantidadEsperada = 0.0;
            if (item.getNotaRecepcionItemDistribucion() != null) {
                cantidadEsperada = item.getNotaRecepcionItemDistribucion().getCantidad();
            }

            Double cantidadRecibida = item.getCantidadRecibida() != null ? item.getCantidadRecibida() : 0.0;
            Double cantidadRechazada = item.getCantidadRechazada() != null ? item.getCantidadRechazada() : 0.0;

            // Calcular el estado con lógica inteligente
            EstadoVerificacion nuevoEstado;

            if (cantidadRecibida == 0 && cantidadRechazada == 0) {
                // No se ha procesado nada
                nuevoEstado = EstadoVerificacion.PENDIENTE;
            } else if (cantidadRecibida > 0 && cantidadRechazada > 0) {
                // Caso especial: recepción parcial + rechazo parcial
                if (cantidadRecibida >= cantidadRechazada) {
                    // Se recibió más o igual que lo rechazado -> VERIFICADO_CON_DIFERENCIA
                    nuevoEstado = EstadoVerificacion.VERIFICADO_CON_DIFERENCIA;
                } else {
                    // Se rechazó más que lo recibido -> RECHAZADO
                    nuevoEstado = EstadoVerificacion.RECHAZADO;
                }
            } else if (cantidadRecibida > 0) {
                // Solo hay recepción
                if (Math.abs(cantidadRecibida - cantidadEsperada) < 0.001) {
                    // Cantidad exacta recibida
                    nuevoEstado = EstadoVerificacion.VERIFICADO;
                } else {
                    // Cantidad diferente a la esperada
                    nuevoEstado = EstadoVerificacion.VERIFICADO_CON_DIFERENCIA;
                }
            } else if (cantidadRechazada > 0) {
                // Solo hay rechazo
                nuevoEstado = EstadoVerificacion.RECHAZADO;
            } else {
                // Caso edge (no debería ocurrir)
                nuevoEstado = EstadoVerificacion.PENDIENTE;
            }

            // Log del estado calculado para debugging
            System.out.println("=== ACTUALIZACIÓN DE ESTADO DE VERIFICACIÓN ===");
            System.out.println("Item ID: " + item.getId());
            System.out.println("Producto: "
                    + (item.getNotaRecepcionItem() != null ? item.getNotaRecepcionItem().getProducto().getDescripcion()
                            : "N/A"));
            System.out.println("Cantidad Esperada: " + cantidadEsperada);
            System.out.println("Cantidad Recibida: " + cantidadRecibida);
            System.out.println("Cantidad Rechazada: " + cantidadRechazada);
            System.out.println("Estado Anterior: " + item.getEstadoVerificacion());
            System.out.println("Estado Nuevo: " + nuevoEstado);
            System.out.println(
                    "Razón: " + obtenerRazonEstado(nuevoEstado, cantidadRecibida, cantidadRechazada, cantidadEsperada));

            // Actualizar el estado si ha cambiado
            if (!nuevoEstado.equals(item.getEstadoVerificacion())) {
                item.setEstadoVerificacion(nuevoEstado);
                service.save(item); // Guardar el cambio de estado
                System.out.println("✅ Estado actualizado exitosamente");
            } else {
                System.out.println("ℹ️ Estado sin cambios");
            }

        } catch (Exception e) {
            // Log del error pero no fallar la operación principal
            System.err.println("Error al actualizar estado de verificación: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Obtiene la razón del estado calculado para debugging
     */
    private String obtenerRazonEstado(EstadoVerificacion estado, Double cantidadRecibida, Double cantidadRechazada,
            Double cantidadEsperada) {
        switch (estado) {
            case PENDIENTE:
                return "No se ha procesado nada (recibido: " + cantidadRecibida + ", rechazado: " + cantidadRechazada
                        + ")";
            case VERIFICADO:
                return "Cantidad exacta recibida (" + cantidadRecibida + " = " + cantidadEsperada + ")";
            case VERIFICADO_CON_DIFERENCIA:
                if (cantidadRecibida > 0 && cantidadRechazada > 0) {
                    return "Recepción parcial + rechazo parcial (recibido: " + cantidadRecibida + ", rechazado: "
                            + cantidadRechazada + ")";
                } else {
                    return "Cantidad diferente a la esperada (recibido: " + cantidadRecibida + ", esperado: "
                            + cantidadEsperada + ")";
                }
            case RECHAZADO:
                if (cantidadRecibida > 0) {
                    return "Más rechazado que recibido (recibido: " + cantidadRecibida + ", rechazado: "
                            + cantidadRechazada + ")";
                } else {
                    return "Todo rechazado (rechazado: " + cantidadRechazada + ")";
                }
            default:
                return "Estado desconocido";
        }
    }

    /**
     * Obtiene el sumario de una recepción de mercadería
     */
    public RecepcionSumarioDTO obtenerSumarioRecepcion(Long recepcionId) {
        if (recepcionId == null) {
            throw new IllegalArgumentException("ID de recepción es requerido");
        }

        return service.obtenerSumarioRecepcion(recepcionId);
    }
}