package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.NotaRecepcionItem;
import com.franco.dev.domain.operaciones.NotaRecepcionItemDistribucion;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.enums.NotaRecepcionItemEstado;
import com.franco.dev.domain.operaciones.enums.NotaRecepcionEstado;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaTipo;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.ProductoProveedor;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaItem;
import com.franco.dev.graphql.operaciones.dto.AsignacionError;
import com.franco.dev.graphql.operaciones.dto.AsignacionResult;
import com.franco.dev.repository.operaciones.NotaRecepcionRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.productos.ProductoProveedorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NotaRecepcionService extends CrudService<NotaRecepcion, NotaRecepcionRepository, Long> {

    private static final Logger logger = LoggerFactory.getLogger(NotaRecepcionService.class);

    private final NotaRecepcionRepository repository;
    private final NotaRecepcionItemService notaRecepcionItemService;
    private final NotaRecepcionItemDistribucionService notaRecepcionItemDistribucionService;
    private final PedidoItemService pedidoItemService;
    private final ProcesoEtapaService procesoEtapaService;
    private final ProductoProveedorService productoProveedorService;
    
    // Usar @Lazy para romper dependencia circular con RecepcionMercaderiaItemService
    @Autowired
    @Lazy
    private RecepcionMercaderiaItemService recepcionMercaderiaItemService;

    public NotaRecepcionService(
            NotaRecepcionRepository repository,
            NotaRecepcionItemService notaRecepcionItemService,
            NotaRecepcionItemDistribucionService notaRecepcionItemDistribucionService,
            PedidoItemService pedidoItemService,
            ProcesoEtapaService procesoEtapaService,
            ProductoProveedorService productoProveedorService) {
        this.repository = repository;
        this.notaRecepcionItemService = notaRecepcionItemService;
        this.notaRecepcionItemDistribucionService = notaRecepcionItemDistribucionService;
        this.pedidoItemService = pedidoItemService;
        this.procesoEtapaService = procesoEtapaService;
        this.productoProveedorService = productoProveedorService;
    }

    @Override
    public NotaRecepcionRepository getRepository() {
        return repository;
    }

    public List<NotaRecepcion> findByPedidoId(Long id){
        return  repository.findByPedidoId(id);
    }

    public Page<NotaRecepcion> findByPedidoId(Long id, Pageable page){
        return repository.findByPedidoId(id, page);
    }

    public Page<NotaRecepcion> findByPedidoIdAndNumero(Long id, String texto, Pageable page){
        return repository.findByPedidoIdAndNumero(id, texto, page);
    }

    // public List<NotaRecepcion> findByNumero(Integer numero){
    //     return repository.findByNumeroAndPedidoEstadoNot(numero, com.franco.dev.domain.operaciones.enums.PedidoEstado.CONCLUIDO);
    // }

    /**
     * Find NotaRecepcion available for reception with complex filtering criteria
     * Used specifically for the reception process with business logic filtering
     * @param numero Optional nota number (can be null if proveedor is provided)
     * @param proveedorId Optional proveedor ID (can be null if numero is provided) 
     * @param sucursalId Optional sucursal ID for filtering by PedidoItemDistribucion
     * @return List of available NotaRecepcion for reception
     */
    public List<NotaRecepcion> findNotasDisponiblesParaRecepcion(Integer numero, Long proveedorId, Long sucursalId) {
        return repository.findNotasDisponiblesParaRecepcion(numero, proveedorId, sucursalId);
    }

    /**
     * Find NotaRecepcion available for reception with complex filtering criteria and pagination
     * Used specifically for the reception process with business logic filtering
     * @param numero Optional nota number (can be null if proveedor is provided)
     * @param proveedorId Optional proveedor ID (can be null if numero is provided) 
     * @param sucursalId Optional sucursal ID for filtering by PedidoItemDistribucion
     * @param pageable Pagination parameters
     * @return Page of available NotaRecepcion for reception
     */
    public Page<NotaRecepcion> findNotasDisponiblesParaRecepcionPage(Integer numero, Long proveedorId, Long sucursalId, Pageable pageable) {
        return repository.findNotasDisponiblesParaRecepcionPage(numero, proveedorId, sucursalId, pageable);
    }

    @Override
    @Transactional
    public NotaRecepcion save(NotaRecepcion entity) {
        // Verificar si es la primera nota de recepción del pedido
        boolean esPrimeraNota = false;
        if (entity.getPedido() != null && entity.getId() == null) {
            // Es una nueva nota (ID null) y tiene pedido asociado
            List<NotaRecepcion> notasExistentes = findByPedidoId(entity.getPedido().getId());
            esPrimeraNota = notasExistentes.isEmpty();
        }
        
        // Guardar la nota
        NotaRecepcion e = super.save(entity);
        
        // Si es la primera nota, actualizar el estado de la etapa RECEPCION_NOTA
        if (esPrimeraNota && entity.getPedido() != null) {
            try {
                procesoEtapaService.actualizarEtapaAEnProceso(entity.getPedido().getId(), ProcesoEtapaTipo.RECEPCION_NOTA);
            } catch (Exception ex) {
                // Log del error pero no fallar la operación principal
                System.err.println("Error al actualizar etapa RECEPCION_NOTA: " + ex.getMessage());
            }
        }
        
        return e;
    }

    /**
     * Asigna ítems de pedido a una nota de recepción
     * @param notaRecepcionId ID de la nota de recepción
     * @param pedidoItemIds Lista de IDs de ítems de pedido a asignar
     * @return Resultado de la asignación con ítems creados y errores
     */
    @Transactional
    public AsignacionResult asignarItemsANota(Long notaRecepcionId, List<Long> pedidoItemIds) {
        AsignacionResult result = new AsignacionResult();
        result.setSuccess(true);
        result.setNotaRecepcionItems(new ArrayList<>());
        result.setErrores(new ArrayList<>());

        // Obtener la nota de recepción
        Optional<NotaRecepcion> notaRecepcionOpt = findById(notaRecepcionId);
        if (!notaRecepcionOpt.isPresent()) {
            result.setSuccess(false);
            result.setMessage("Nota de recepción no encontrada");
            return result;
        }

        NotaRecepcion notaRecepcion = notaRecepcionOpt.get();

        // Procesar cada ítem de pedido
        for (Long pedidoItemId : pedidoItemIds) {
            try {
                // Obtener el ítem de pedido
                Optional<PedidoItem> pedidoItemOpt = pedidoItemService.findById(pedidoItemId);
                if (!pedidoItemOpt.isPresent()) {
                    AsignacionError error = new AsignacionError();
                    error.setPedidoItemId(pedidoItemId);
                    error.setError("Ítem de pedido no encontrado");
                    result.getErrores().add(error);
                    continue;
                }

                PedidoItem pedidoItem = pedidoItemOpt.get();

                // Verificar que el ítem pertenece al mismo pedido que la nota
                if (!pedidoItem.getPedido().getId().equals(notaRecepcion.getPedido().getId())) {
                    AsignacionError error = new AsignacionError();
                    error.setPedidoItemId(pedidoItemId);
                    error.setError("El ítem no pertenece al mismo pedido que la nota");
                    result.getErrores().add(error);
                    continue;
                }

                // Verificar que el ítem tenga cantidad pendiente para asignar
                Double cantidadPendiente = pedidoItemService.getCantidadPendiente(pedidoItemId);
                if (cantidadPendiente <= 0) {
                    AsignacionError error = new AsignacionError();
                    error.setPedidoItemId(pedidoItemId);
                    error.setError("El ítem no tiene cantidad pendiente para asignar");
                    result.getErrores().add(error);
                    continue;
                }

                // Crear el NotaRecepcionItem con la cantidad pendiente
                NotaRecepcionItem notaRecepcionItem = new NotaRecepcionItem();
                notaRecepcionItem.setNotaRecepcion(notaRecepcion);
                notaRecepcionItem.setPedidoItem(pedidoItem);
                notaRecepcionItem.setProducto(pedidoItem.getProducto());
                notaRecepcionItem.setPresentacionEnNota(pedidoItem.getPresentacionCreacion());
                notaRecepcionItem.setCantidadEnNota(cantidadPendiente); // Usar cantidad pendiente en lugar de cantidad solicitada
                notaRecepcionItem.setPrecioUnitarioEnNota(pedidoItem.getPrecioUnitarioSolicitado());
                notaRecepcionItem.setEsBonificacion(false);
                notaRecepcionItem.setEstado(NotaRecepcionItemEstado.PENDIENTE_CONCILIACION);
                notaRecepcionItem.setCreadoEn(LocalDateTime.now());

                // Guardar el NotaRecepcionItem asegurando mapeo correcto de presentación
                NotaRecepcionItem savedItem = notaRecepcionItemService.saveWithPresentacionMapping(notaRecepcionItem);

                // Crear las distribuciones basadas en PedidoItemDistribucion
                List<NotaRecepcionItemDistribucion> distribuciones = new ArrayList<>();
                // Buscar las distribuciones del pedidoItem desde el repositorio
                List<com.franco.dev.domain.operaciones.PedidoItemDistribucion> pedidoDistribuciones = 
                    pedidoItemService.getRepository().findByPedidoItemId(pedidoItemId);
                
                if (pedidoDistribuciones != null && !pedidoDistribuciones.isEmpty()) {
                    for (com.franco.dev.domain.operaciones.PedidoItemDistribucion pedidoDist : pedidoDistribuciones) {
                        NotaRecepcionItemDistribucion distribucion = new NotaRecepcionItemDistribucion();
                        distribucion.setNotaRecepcionItem(savedItem);
                        distribucion.setSucursalInfluencia(pedidoDist.getSucursalInfluencia());
                        distribucion.setSucursalEntrega(pedidoDist.getSucursalEntrega());
                        distribucion.setCantidad(pedidoDist.getCantidadAsignada());
                        distribucion.setCreadoEn(LocalDateTime.now());
                        distribuciones.add(distribucion);
                    }
                }

                // Guardar las distribuciones si existen
                if (!distribuciones.isEmpty()) {
                    notaRecepcionItemDistribucionService.saveDistribuciones(distribuciones);
                }

                // No necesitamos actualizar el pedidoItem ya que la relación es a través de NotaRecepcionItem

                result.getNotaRecepcionItems().add(savedItem);

            } catch (Exception e) {
                AsignacionError error = new AsignacionError();
                error.setPedidoItemId(pedidoItemId);
                error.setError("Error al procesar el ítem: " + e.getMessage());
                result.getErrores().add(error);
            }
        }

        if (result.getErrores().isEmpty()) {
            result.setMessage("Todos los ítems fueron asignados exitosamente");
        } else {
            result.setMessage("Algunos ítems no pudieron ser asignados");
        }

        // Actualizar estado de los ítems y de la nota después de crear distribuciones automáticamente
        // IMPORTANTE: Esto se hace DESPUÉS de que todos los items hayan sido asignados
        // para asegurar que cuando se creen los vínculos ProductoProveedor, todos los items ya estén en la BD
        actualizarEstadosDespuesDeDistribucion(notaRecepcionId);

        return result;
    }

    /**
     * Asigna automáticamente todos los ítems pendientes del pedido a la nota de recepción
     * @param notaRecepcionId ID de la nota de recepción
     * @param pedidoId ID del pedido
     */
    @Transactional
    public void asignarTodosLosItemsPendientes(Long notaRecepcionId, Long pedidoId) {
        // Obtener todos los ítems del pedido
        List<PedidoItem> todosLosItems = pedidoItemService.findByPedidoId(pedidoId);
        
        // Filtrar solo los ítems que tienen cantidad pendiente > 0
        List<Long> pedidoItemIds = new ArrayList<>();
        for (PedidoItem item : todosLosItems) {
            Double cantidadPendiente = pedidoItemService.getCantidadPendiente(item.getId());
            if (cantidadPendiente != null && cantidadPendiente > 0) {
                pedidoItemIds.add(item.getId());
            }
        }
        
        // Si hay items pendientes, asignarlos a la nota
        if (!pedidoItemIds.isEmpty()) {
            asignarItemsANota(notaRecepcionId, pedidoItemIds);
        }
    }

    /**
     * Actualiza el estado de una nota de recepción basándose en los estados de sus ítems
     * Reglas:
     * - Si todos los ítems están CONCILIADOS → Nota = CONCILIADA
     * - Si hay algún ítem PENDIENTE_CONCILIACION → Nota = PENDIENTE_CONCILIACION
     * - Si hay algún ítem RECHAZADO o DISCREPANCIA → Nota permanece en su estado actual (no cambia automáticamente)
     * 
     * @param notaRecepcionId ID de la nota de recepción
     */
    @Transactional
    public void actualizarEstadoNota(Long notaRecepcionId) {
        if (notaRecepcionId == null) {
            return;
        }

        Optional<NotaRecepcion> notaOpt = findById(notaRecepcionId);
        if (!notaOpt.isPresent()) {
            return;
        }

        NotaRecepcion nota = notaOpt.get();
        
        // Si la nota ya está en un estado final (CERRADA, RECEPCION_COMPLETA), no actualizar
        if (nota.getEstado() == NotaRecepcionEstado.CERRADA || 
            nota.getEstado() == NotaRecepcionEstado.RECEPCION_COMPLETA) {
            return;
        }

        // Obtener todos los ítems de la nota
        List<NotaRecepcionItem> items = notaRecepcionItemService.findByNotaRecepcionId(notaRecepcionId);
        
        if (items.isEmpty()) {
            // Si no hay ítems, mantener el estado actual
            return;
        }

        // Verificar si todos los ítems están conciliados
        boolean todosConciliados = items.stream()
            .allMatch(item -> {
                // Verificar que el ítem esté conciliado Y que su distribución esté concluida
                boolean estadoConciliado = item.getEstado() == NotaRecepcionItemEstado.CONCILIADO;
                boolean distribucionConcluida = notaRecepcionItemDistribucionService.isDistribucionConcluida(
                    item.getId(), 
                    item.getCantidadEnNota()
                );
                return estadoConciliado && distribucionConcluida;
            });

        // Verificar si hay algún ítem pendiente
        boolean hayPendientes = items.stream()
            .anyMatch(item -> item.getEstado() == NotaRecepcionItemEstado.PENDIENTE_CONCILIACION);

        // Actualizar estado de la nota
        if (todosConciliados && !hayPendientes) {
            // Todos los ítems están conciliados → Nota = CONCILIADA
            if (nota.getEstado() != NotaRecepcionEstado.CONCILIADA) {
                nota.setEstado(NotaRecepcionEstado.CONCILIADA);
                save(nota);
                
                // Crear vínculos ProductoProveedor cuando la nota se concilia
                crearVinculosProductoProveedor(nota);
            } else {
                // La nota ya estaba CONCILIADA, verificar si hay nuevos items que requieren vínculos
                crearVinculosProductoProveedor(nota);
            }
        } else if (hayPendientes) {
            // Hay ítems pendientes → Nota = PENDIENTE_CONCILIACION
            if (nota.getEstado() != NotaRecepcionEstado.PENDIENTE_CONCILIACION) {
                nota.setEstado(NotaRecepcionEstado.PENDIENTE_CONCILIACION);
                save(nota);
            }
        }
    }

    /**
     * Crea vínculos ProductoProveedor para todos los productos de la nota de recepción
     * cuando esta se concilia. Solo crea vínculos que no existan previamente.
     * 
     * @param nota Nota de recepción conciliada
     */
    @Transactional
    private void crearVinculosProductoProveedor(NotaRecepcion nota) {
        // Verificar que la nota tenga un pedido asociado
        if (nota.getPedido() == null || nota.getPedido().getProveedor() == null) {
            logger.warn("La nota {} no tiene pedido o el pedido no tiene proveedor. Abortando creación de vínculos.", nota.getId());
            return;
        }

        // Obtener el proveedor del pedido
        Proveedor proveedor = nota.getPedido().getProveedor();
        
        // Obtener el usuario (de la nota o del pedido como fallback)
        Usuario usuario = nota.getUsuario();
        if (usuario == null && nota.getPedido() != null) {
            usuario = nota.getPedido().getUsuario();
        }

        // Obtener todos los ítems de la nota
        List<NotaRecepcionItem> items = notaRecepcionItemService.findByNotaRecepcionId(nota.getId());
        
        if (items.isEmpty()) {
            logger.warn("No se encontraron items en la nota {}. Abortando creación de vínculos.", nota.getId());
            return;
        }

        // Usar un Set para evitar duplicados de productos
        Set<Long> productosProcesados = new HashSet<>();
        int vinculosCreados = 0;
        int vinculosReactivos = 0;
        int errores = 0;
        
        // Crear vínculos para cada producto único
        for (NotaRecepcionItem item : items) {
            try {
                Producto producto = item.getProducto();
                
                if (producto == null) {
                    continue;
                }
                
                Long productoId = producto.getId();
                
                if (productoId == null) {
                    continue;
                }

                // Evitar procesar el mismo producto múltiples veces
                if (productosProcesados.contains(productoId)) {
                    continue;
                }
                productosProcesados.add(productoId);

                // Verificar si ya existe un vínculo para este producto y proveedor
                ProductoProveedor vinculoExistente = productoProveedorService.getRepository()
                    .findByProductoIdAndProveedorId(productoId, proveedor.getId());

                if (vinculoExistente == null) {
                    // Crear nuevo vínculo ProductoProveedor
                    ProductoProveedor nuevoVinculo = new ProductoProveedor();
                    nuevoVinculo.setProducto(producto);
                    nuevoVinculo.setProveedor(proveedor);
                    nuevoVinculo.setPedido(nota.getPedido());
                    nuevoVinculo.setCreadoEn(LocalDateTime.now());
                    nuevoVinculo.setUsuario(usuario);
                    nuevoVinculo.setActivo(true);
                    nuevoVinculo.setMotivoDesvinculacion(null);
                    
                    productoProveedorService.save(nuevoVinculo);
                    vinculosCreados++;
                } else {
                    // Si el vínculo existe pero estaba desactivado, reactivarlo y limpiar motivo
                    if (vinculoExistente.getActivo() == null || !vinculoExistente.getActivo()) {
                        vinculoExistente.setActivo(true);
                        vinculoExistente.setMotivoDesvinculacion(null);
                        productoProveedorService.save(vinculoExistente);
                        vinculosReactivos++;
                    }
                }
            } catch (Exception e) {
                logger.error("Error procesando item {} para crear vínculo ProductoProveedor: {}", 
                    item.getId(), e.getMessage(), e);
                errores++;
            }
        }
        
        if (errores > 0) {
            logger.warn("Se encontraron {} errores al crear vínculos ProductoProveedor para la nota {}", 
                errores, nota.getId());
        }
    }

    /**
     * Actualiza los estados de ítems y nota después de guardar distribuciones
     * Este método se llama después de crear o actualizar distribuciones
     * 
     * @param notaRecepcionId ID de la nota de recepción
     */
    @Transactional
    public void actualizarEstadosDespuesDeDistribucion(Long notaRecepcionId) {
        if (notaRecepcionId == null) {
            return;
        }

        // IMPORTANTE: Forzar flush del repositorio para asegurar que todos los items
        // guardados en asignarItemsANota estén visibles en la siguiente query
        // Esto es necesario porque estamos dentro de una transacción y los items pueden
        // no estar aún "flushed" a la base de datos
        try {
            notaRecepcionItemService.getRepository().flush();
        } catch (Exception e) {
            logger.warn("No se pudo hacer flush explícito, continuando de todas formas: {}", e.getMessage());
        }

        // Obtener todos los ítems de la nota
        List<NotaRecepcionItem> items = notaRecepcionItemService.findByNotaRecepcionId(notaRecepcionId);
        
        // Actualizar estado de cada ítem si su distribución está concluida
        for (NotaRecepcionItem item : items) {
            if (item.getCantidadEnNota() != null && item.getCantidadEnNota() > 0) {
                boolean distribucionConcluida = notaRecepcionItemDistribucionService.isDistribucionConcluida(
                    item.getId(), 
                    item.getCantidadEnNota()
                );
                
                // Si la distribución está concluida y el ítem no está conciliado, actualizarlo
                if (distribucionConcluida && item.getEstado() == NotaRecepcionItemEstado.PENDIENTE_CONCILIACION) {
                    item.setEstado(NotaRecepcionItemEstado.CONCILIADO);
                    notaRecepcionItemService.save(item);
                }
            }
        }

        // Actualizar estado de la nota después de que todos los items hayan sido procesados
        // Esto asegura que cuando se creen los vínculos ProductoProveedor, todos los items ya estén asignados
        actualizarEstadoNota(notaRecepcionId);
    }

    /**
     * Verifica si una nota de recepción tiene recepción pendiente
     * @param notaId ID de la nota
     * @param sucursalId ID de la sucursal (opcional, si es null verifica todas)
     * @return true si tiene recepción pendiente, false si está completamente recibida
     */
    public boolean tieneRecepcionPendiente(Long notaId, Long sucursalId) {
        if (notaId == null) {
            return false;
        }

        // 1. Verificar estado de la nota
        Optional<NotaRecepcion> notaOpt = findById(notaId);
        if (!notaOpt.isPresent()) {
            return false;
        }

        NotaRecepcion nota = notaOpt.get();

        // Si está en estado final (CERRADA o RECEPCION_COMPLETA), verificar si realmente está completa
        // verificando items pendientes
        if (nota.getEstado() == NotaRecepcionEstado.RECEPCION_COMPLETA || 
            nota.getEstado() == NotaRecepcionEstado.CERRADA) {
            // Aunque esté en estado completo, verificar items por si hay recepciones parciales
            return verificarItemsPendientes(notaId, sucursalId);
        }

        // Si está en otros estados (PENDIENTE_CONCILIACION, CONCILIADA, EN_RECEPCION, RECEPCION_PARCIAL),
        // verificar items pendientes
        return verificarItemsPendientes(notaId, sucursalId);
    }

    /**
     * Verifica si hay items con cantidad pendiente de recibir
     * @param notaId ID de la nota
     * @param sucursalId ID de la sucursal (opcional, si es null verifica todas)
     * @return true si hay items pendientes, false si todo está recibido
     */
    private boolean verificarItemsPendientes(Long notaId, Long sucursalId) {
        // Obtener todos los items de la nota
        List<NotaRecepcionItem> items = notaRecepcionItemService.findByNotaRecepcionId(notaId);

        if (items.isEmpty()) {
            // Si no hay items, considerar como pendiente (nota nueva sin items)
            return true;
        }

        // Tolerancia para comparaciones de punto flotante
        final double TOLERANCIA = 0.001;

        for (NotaRecepcionItem item : items) {
            // Obtener distribuciones del item
            List<NotaRecepcionItemDistribucion> distribuciones = 
                notaRecepcionItemDistribucionService.findByNotaRecepcionItemId(item.getId());

            if (distribuciones.isEmpty()) {
                // Si no hay distribuciones, el item está pendiente
                return true;
            }

            for (NotaRecepcionItemDistribucion dist : distribuciones) {
                // Si se especifica sucursal, solo verificar esa
                if (sucursalId != null && 
                    (dist.getSucursalEntrega() == null || !dist.getSucursalEntrega().getId().equals(sucursalId))) {
                    continue;
                }

                // Obtener cantidad esperada de la distribución
                Double cantidadEsperada = dist.getCantidad() != null ? dist.getCantidad() : 0.0;

                // Obtener cantidad recibida y rechazada desde RecepcionMercaderiaItem
                List<RecepcionMercaderiaItem> itemsRecepcion = 
                    recepcionMercaderiaItemService.getRepository().findByNotaRecepcionItemDistribucionId(dist.getId());

                Double cantidadRecibida = itemsRecepcion.stream()
                    .mapToDouble(rmItem -> rmItem.getCantidadRecibida() != null ? rmItem.getCantidadRecibida() : 0.0)
                    .sum();

                Double cantidadRechazada = itemsRecepcion.stream()
                    .mapToDouble(rmItem -> rmItem.getCantidadRechazada() != null ? rmItem.getCantidadRechazada() : 0.0)
                    .sum();

                // Calcular cantidad pendiente
                Double cantidadPendiente = cantidadEsperada - cantidadRecibida - cantidadRechazada;

                // Si hay cantidad pendiente significativa, la nota tiene recepción pendiente
                if (cantidadPendiente > TOLERANCIA) {
                    return true;
                }
            }
        }

        // Todas las distribuciones están completamente recibidas
        return false;
    }

    /**
     * Busca notas disponibles para recepción filtrando automáticamente las completamente recibidas
     * Si hay múltiples notas con el mismo número, retorna solo las que tienen recepción pendiente
     * 
     * @param numero Número de nota (opcional)
     * @param proveedorId ID del proveedor (opcional)
     * @param sucursalId ID de la sucursal (opcional, si es null verifica todas)
     * @return Lista de notas con recepción pendiente
     */
    public List<NotaRecepcion> findNotasDisponiblesParaRecepcionFiltradas(
            Integer numero, Long proveedorId, Long sucursalId) {
        // Obtener todas las notas que coinciden
        List<NotaRecepcion> todasLasNotas = findNotasDisponiblesParaRecepcion(numero, proveedorId, sucursalId);

        // Filtrar solo las que tienen recepción pendiente
        return todasLasNotas.stream()
            .filter(nota -> tieneRecepcionPendiente(nota.getId(), sucursalId))
            .collect(Collectors.toList());
    }
}