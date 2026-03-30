package com.franco.dev.service.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.*;
import com.franco.dev.domain.operaciones.enums.DevolucionEstado;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.operaciones.DevolucionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class DevolucionService extends CrudService<Devolucion, DevolucionRepository, Long> {
    
    private final DevolucionRepository repository;
    
    @Autowired
    private DevolucionItemService devolucionItemService;
    
    @Autowired
    private MovimientoStockService movimientoStockService;

    @Override
    public DevolucionRepository getRepository() {
        return repository;
    }

    /**
     * Crea una nueva devolución
     */
    @Transactional
    public Devolucion crearDevolucion(Proveedor proveedor, Sucursal sucursalOrigen, 
                                     String motivo, Usuario usuario) {
        Devolucion devolucion = new Devolucion();
        devolucion.setProveedor(proveedor);
        devolucion.setSucursalOrigen(sucursalOrigen);
        devolucion.setFecha(LocalDateTime.now());
        devolucion.setMotivo(motivo);
        devolucion.setEstado(DevolucionEstado.EN_PROCESO);
        devolucion.setUsuario(usuario);
        
        return save(devolucion);
    }

    /**
     * Confirma una devolución - FUNCIÓN CRÍTICA
     * Genera movimientos de stock de salida y puede generar créditos
     */
    @Transactional
    public Devolucion confirmarDevolucion(Long devolucionId) {
        Devolucion devolucion = findById(devolucionId).orElseThrow(
            () -> new IllegalArgumentException("Devolución no encontrada: " + devolucionId)
        );
        
        if (devolucion.getEstado() == DevolucionEstado.FINALIZADA) {
            throw new IllegalStateException("La devolución ya está finalizada");
        }
        
        // Obtener todos los ítems de la devolución
        List<DevolucionItem> items = devolucionItemService.findByDevolucionId(devolucionId);
        
        if (items.isEmpty()) {
            throw new IllegalStateException("No se pueden confirmar devoluciones sin ítems");
        }
        
        // Procesar cada ítem: generar movimiento de stock de salida
        for (DevolucionItem item : items) {
            procesarItemDevolucion(item, devolucion);
        }
        
        // Cambiar estado de la devolución
        devolucion.setEstado(DevolucionEstado.FINALIZADA);
        
        return save(devolucion);
    }

    /**
     * Procesa un ítem individual de devolución
     */
    @Transactional
    private void procesarItemDevolucion(DevolucionItem item, Devolucion devolucion) {
        // Verificar que hay stock suficiente
        Double stockActual = movimientoStockService.stockByProductoIdAndSucursalId(
            item.getProducto().getId(), devolucion.getSucursalOrigen().getId());
        
        if (stockActual < item.getCantidad()) {
            throw new IllegalStateException("Stock insuficiente para devolver el producto: " + 
                                          item.getProducto().getDescripcion() + ". Stock actual: " + 
                                          stockActual + ", cantidad a devolver: " + item.getCantidad());
        }
        
        // Generar movimiento de stock de salida (negativo)
        generarMovimientoStockDevolucion(item, devolucion);
        
        // TODO: Aquí se podría implementar lógica para generar notas de crédito
        // o ajustar cuentas por pagar al proveedor
    }

    /**
     * Genera el movimiento de stock de salida para un ítem devuelto
     */
    private void generarMovimientoStockDevolucion(DevolucionItem item, Devolucion devolucion) {
        MovimientoStock movimiento = new MovimientoStock();
        movimiento.setProducto(item.getProducto());
        movimiento.setSucursalId(devolucion.getSucursalOrigen().getId());
        movimiento.setCantidad(-item.getCantidad()); // Negativo = salida
        movimiento.setTipoMovimiento(TipoMovimiento.DEVOLUCION);
        movimiento.setReferencia(item.getId()); // Referencia al ítem de devolución
        movimiento.setCreadoEn(LocalDateTime.now());
        movimiento.setEstado(true); // Activo
        
        movimientoStockService.save(movimiento);
    }

    /**
     * Cancela una devolución en proceso
     */
    @Transactional
    public Devolucion cancelarDevolucion(Long devolucionId, String motivoCancelacion) {
        Devolucion devolucion = findById(devolucionId).orElseThrow(
            () -> new IllegalArgumentException("Devolución no encontrada: " + devolucionId)
        );
        
        if (devolucion.getEstado() == DevolucionEstado.FINALIZADA) {
            throw new IllegalStateException("No se puede cancelar una devolución finalizada");
        }
        
        devolucion.setEstado(DevolucionEstado.CANCELADA);
        devolucion.setMotivo(devolucion.getMotivo() + " - CANCELADA: " + motivoCancelacion);
        
        return save(devolucion);
    }

    /**
     * Busca devoluciones con filtros
     */
    public Page<Devolucion> findByFilters(Long proveedorId, Long sucursalId, 
                                         DevolucionEstado estado,
                                         LocalDateTime fechaInicio, LocalDateTime fechaFin,
                                         Pageable pageable) {
        return repository.findByFilters(proveedorId, sucursalId, estado, fechaInicio, fechaFin, pageable);
    }

    /**
     * Obtiene devoluciones por proveedor
     */
    public List<Devolucion> findByProveedorId(Long proveedorId) {
        return repository.findByProveedorId(proveedorId);
    }

    /**
     * Obtiene devoluciones por estado
     */
    public List<Devolucion> findByEstado(DevolucionEstado estado) {
        return repository.findByEstado(estado);
    }
} 