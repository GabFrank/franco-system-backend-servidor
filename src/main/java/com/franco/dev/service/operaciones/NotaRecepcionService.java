package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.NotaPedido;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.NotaRecepcionItem;
import com.franco.dev.domain.operaciones.NotaRecepcionItemDistribucion;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.enums.NotaRecepcionItemEstado;
import com.franco.dev.graphql.operaciones.dto.AsignacionError;
import com.franco.dev.graphql.operaciones.dto.AsignacionResult;
import com.franco.dev.repository.operaciones.NotaPedidoRepository;
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
     * @param numero Optional nota number (can be null if proveedor is provided)
     * @param proveedorId Optional proveedor ID (can be null if numero is provided) 
     * @param sucursalId Optional sucursal ID for filtering by PedidoItemDistribucion
     * @return List of available NotaRecepcion for reception
     */
    public List<NotaRecepcion> findNotasDisponiblesParaRecepcion(Integer numero, Long proveedorId, Long sucursalId) {
        return repository.findNotasDisponiblesParaRecepcion(numero, proveedorId, sucursalId);
    }

    @Override
    public NotaRecepcion save(NotaRecepcion entity) {
        NotaRecepcion e = super.save(entity);
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

                // Verificar que el ítem no esté ya asignado a otra nota
                // Buscar si ya existe un NotaRecepcionItem para este PedidoItem
                List<NotaRecepcionItem> existingItems = notaRecepcionItemService.findByPedidoItemId(pedidoItemId);
                if (!existingItems.isEmpty()) {
                    AsignacionError error = new AsignacionError();
                    error.setPedidoItemId(pedidoItemId);
                    error.setError("El ítem ya está asignado a otra nota de recepción");
                    result.getErrores().add(error);
                    continue;
                }

                // Crear el NotaRecepcionItem
                NotaRecepcionItem notaRecepcionItem = new NotaRecepcionItem();
                notaRecepcionItem.setNotaRecepcion(notaRecepcion);
                notaRecepcionItem.setPedidoItem(pedidoItem);
                notaRecepcionItem.setProducto(pedidoItem.getProducto());
                notaRecepcionItem.setPresentacionEnNota(pedidoItem.getPresentacionCreacion());
                notaRecepcionItem.setCantidadEnNota(pedidoItem.getCantidadSolicitada());
                notaRecepcionItem.setPrecioUnitarioEnNota(pedidoItem.getPrecioUnitarioSolicitado());
                notaRecepcionItem.setEsBonificacion(false);
                notaRecepcionItem.setEstado(NotaRecepcionItemEstado.PENDIENTE_CONCILIACION);
                notaRecepcionItem.setCreadoEn(LocalDateTime.now());

                // Guardar el NotaRecepcionItem
                NotaRecepcionItem savedItem = notaRecepcionItemService.save(notaRecepcionItem);

                // Crear las distribuciones basadas en PedidoItemDistribucion
                List<NotaRecepcionItemDistribucion> distribuciones = new ArrayList<>();
                // Buscar las distribuciones del pedidoItem desde el repositorio
                List<com.franco.dev.domain.operaciones.PedidoItemDistribucion> pedidoDistribuciones = 
                    pedidoItemService.getRepository().findByPedidoItemId(pedidoItemId);
                
                if (pedidoDistribuciones != null && !pedidoDistribuciones.isEmpty()) {
                    for (com.franco.dev.domain.operaciones.PedidoItemDistribucion pedidoDist : pedidoDistribuciones) {
                        NotaRecepcionItemDistribucion distribucion = new NotaRecepcionItemDistribucion();
                        distribucion.setNotaRecepcionItem(savedItem);
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

        return result;
    }
}