package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.NotaRecepcionItem;
import com.franco.dev.domain.operaciones.NotaRecepcionItemDistribucion;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.enums.NotaRecepcionItemEstado;
import com.franco.dev.domain.operaciones.enums.NotaRecepcionEstado;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaTipo;
import com.franco.dev.graphql.operaciones.dto.AsignacionError;
import com.franco.dev.graphql.operaciones.dto.AsignacionResult;
import com.franco.dev.repository.operaciones.NotaRecepcionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class NotaRecepcionService extends CrudService<NotaRecepcion, NotaRecepcionRepository, Long> {

    private final NotaRecepcionRepository repository;
    private final NotaRecepcionItemService notaRecepcionItemService;
    private final NotaRecepcionItemDistribucionService notaRecepcionItemDistribucionService;
    private final PedidoItemService pedidoItemService;
    private final ProcesoEtapaService procesoEtapaService;

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
        actualizarEstadosDespuesDeDistribucion(notaRecepcionId);

        return result;
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

        // Actualizar estado de la nota
        actualizarEstadoNota(notaRecepcionId);
    }
}