package com.franco.dev.service.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.TipoGasto;
import com.franco.dev.domain.operaciones.*;
import com.franco.dev.domain.operaciones.enums.DevolucionEstado;
import com.franco.dev.domain.operaciones.enums.TipoDevolucion;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.operaciones.enums.TipoOrigenVencimiento;
import com.franco.dev.domain.operaciones.enums.TipoResolucionDevolucion;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.GastoRepository;
import com.franco.dev.repository.financiero.TipoGastoRepository;
import com.franco.dev.repository.operaciones.DevolucionRepository;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Logica del modulo de devoluciones/salidas de mercaderia.
 *
 * Tipos:
 * - SIN_PROVEEDOR: averia/vencido sin devolucion a proveedor. PENDIENTE -> SEPARADO -> DESCARTADO.
 *   Al DESCARTAR: baja de stock (DESCARTE) + genera un Gasto de merma (perdida contable).
 * - CON_PROVEEDOR: devolucion a proveedor. PENDIENTE -> SEPARADO -> RETIRADO -> (CANJEADO | ACREDITADO).
 *   Al RETIRAR: baja de stock (DEVOLUCION). Al CANJEAR: reingreso de stock con nuevo vencimiento.
 *   Al ACREDITAR: registra la nota de credito del proveedor.
 *
 * Usado en:
 * - Desktop: Si (modulo de devoluciones)
 * - Mobile: Si (carga de devolucion por escaneo)
 */
@Service
@AllArgsConstructor
public class DevolucionService extends CrudService<Devolucion, DevolucionRepository, Long> {

    /** Descripcion del TipoGasto semilla (V147.0) usado para la merma. */
    public static final String TIPO_GASTO_MERMA = "MERMA/AVERIA DE PRODUCTO";

    private final DevolucionRepository repository;

    @Autowired
    private DevolucionItemService devolucionItemService;

    @Autowired
    private MovimientoStockService movimientoStockService;

    @Autowired
    private ProductoVencimientoService productoVencimientoService;

    @Autowired
    private com.franco.dev.service.financiero.GastoService gastoService;

    @Autowired
    private GastoRepository gastoRepository;

    @Autowired
    private TipoGastoRepository tipoGastoRepository;

    @Override
    public DevolucionRepository getRepository() {
        return repository;
    }

    // ------------------------------------------------------------------
    // Persistencia base
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public Devolucion save(Devolucion entity) {
        if (entity.getFecha() == null) entity.setFecha(LocalDateTime.now());
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getEstado() == null) entity.setEstado(DevolucionEstado.PENDIENTE);
        if (entity.getFinalizado() == null) entity.setFinalizado(false);
        Devolucion saved = super.save(entity);
        // Genera identificador fisico imprimible una vez que existe el id.
        if (saved.getIdentificador() == null) {
            Long sucId = saved.getSucursalOrigen() != null ? saved.getSucursalOrigen().getId() : 0L;
            saved.setIdentificador("DEV-" + sucId + "-" + saved.getId());
            saved = super.save(saved);
        }
        return saved;
    }

    /**
     * Crea una devolucion (compatibilidad). Por defecto tipo CON_PROVEEDOR.
     */
    @Transactional
    public Devolucion crearDevolucion(Proveedor proveedor, Sucursal sucursalOrigen,
                                      String motivo, Usuario usuario) {
        Devolucion devolucion = new Devolucion();
        devolucion.setTipo(proveedor != null ? TipoDevolucion.CON_PROVEEDOR : TipoDevolucion.SIN_PROVEEDOR);
        devolucion.setProveedor(proveedor);
        devolucion.setSucursalOrigen(sucursalOrigen);
        devolucion.setFecha(LocalDateTime.now());
        devolucion.setMotivo(motivo);
        devolucion.setEstado(DevolucionEstado.PENDIENTE);
        devolucion.setUsuario(usuario);
        return save(devolucion);
    }

    // ------------------------------------------------------------------
    // Maquina de estados
    // ------------------------------------------------------------------

    /**
     * Avanza la devolucion a un nuevo estado, validando la transicion segun el tipo
     * y ejecutando los efectos (baja/reingreso de stock, generacion de gasto).
     */
    @Transactional
    public Devolucion avanzarEstado(Long devolucionId, DevolucionEstado nuevoEstado, Usuario usuario) {
        Devolucion d = findById(devolucionId).orElseThrow(
                () -> new GraphQLException("Devolucion no encontrada: " + devolucionId));

        validarTransicion(d, nuevoEstado);
        Usuario ejecutor = usuario != null ? usuario : d.getUsuario();

        switch (nuevoEstado) {
            case SEPARADO:
                // La mercaderia se aparta fisicamente; sin movimiento de stock aun.
                break;
            case RETIRADO:
                // Solo CON_PROVEEDOR: el proveedor retira la mercaderia -> sale del stock.
                bajarStock(d, TipoMovimiento.DEVOLUCION, ejecutor);
                break;
            case DESCARTADO:
                // Solo SIN_PROVEEDOR: baja de stock + gasto de merma.
                bajarStock(d, TipoMovimiento.DESCARTE, ejecutor);
                generarGastoMerma(d, ejecutor);
                d.setFinalizado(true);
                break;
            case CANJEADO:
                // Reingreso del producto de reemplazo con nuevo vencimiento.
                d.setResolucion(TipoResolucionDevolucion.CANJE);
                reingresarCanje(d, ejecutor);
                d.setFinalizado(true);
                break;
            case ACREDITADO:
                d.setResolucion(TipoResolucionDevolucion.NOTA_CREDITO);
                d.setFinalizado(true);
                break;
            case CANCELADA:
                break;
            default:
                throw new GraphQLException("Estado no soportado: " + nuevoEstado);
        }

        d.setEstado(nuevoEstado);
        return save(d);
    }

    /**
     * Registra la acreditacion (nota de credito del proveedor) y finaliza la devolucion.
     */
    @Transactional
    public Devolucion acreditar(Long devolucionId, String nroNotaCredito, Double montoAcreditado, Usuario usuario) {
        Devolucion d = findById(devolucionId).orElseThrow(
                () -> new GraphQLException("Devolucion no encontrada: " + devolucionId));
        d.setNroNotaCredito(nroNotaCredito);
        d.setMontoAcreditado(montoAcreditado);
        save(d); // persistir antes: avanzarEstado re-consulta la entidad
        return avanzarEstado(devolucionId, DevolucionEstado.ACREDITADO, usuario);
    }

    private void validarTransicion(Devolucion d, DevolucionEstado nuevo) {
        boolean conProveedor = d.getTipo() == TipoDevolucion.CON_PROVEEDOR;
        String error = DevolucionTransicionValidator.validar(d.getEstado(), nuevo, conProveedor);
        if (error != null) {
            throw new GraphQLException(error);
        }
    }

    // ------------------------------------------------------------------
    // Efectos: stock, gasto, vencimiento
    // ------------------------------------------------------------------

    /** Baja de stock por cada item (movimiento negativo). Valida saldo suficiente. */
    private void bajarStock(Devolucion d, TipoMovimiento tipoMovimiento, Usuario usuario) {
        List<DevolucionItem> items = devolucionItemService.findByDevolucionId(d.getId());
        if (items.isEmpty()) {
            throw new GraphQLException("No se puede procesar una devolucion sin items");
        }
        Long sucId = d.getSucursalOrigen().getId();
        for (DevolucionItem item : items) {
            double cantidadBase = cantidadEnUnidadBase(item);
            Double stockActual = movimientoStockService.stockByProductoIdAndSucursalId(item.getProducto().getId(), sucId);
            if (stockActual == null) stockActual = 0.0;
            if (stockActual < cantidadBase) {
                throw new GraphQLException("Stock insuficiente para " + item.getProducto().getDescripcion()
                        + ". Actual: " + stockActual + ", requerido: " + cantidadBase);
            }
            MovimientoStock m = new MovimientoStock();
            m.setProducto(item.getProducto());
            m.setSucursalId(sucId);
            m.setCantidad(-cantidadBase);
            m.setTipoMovimiento(tipoMovimiento);
            m.setReferencia(item.getId());
            m.setEstado(true);
            m.setUsuario(usuario);
            movimientoStockService.save(m);
        }
    }

    /** Reingreso del producto canjeado por el proveedor, con nuevo vencimiento. */
    private void reingresarCanje(Devolucion d, Usuario usuario) {
        List<DevolucionItem> items = devolucionItemService.findByDevolucionId(d.getId());
        Long sucId = d.getSucursalOrigen().getId();
        for (DevolucionItem item : items) {
            Double reingresada = item.getCantidadReingresada();
            if (reingresada == null || reingresada <= 0) continue;
            double factor = presentacionFactor(item);
            double cantidadBase = reingresada * factor;

            MovimientoStock m = new MovimientoStock();
            m.setProducto(item.getProducto());
            m.setSucursalId(sucId);
            m.setCantidad(cantidadBase);
            m.setTipoMovimiento(TipoMovimiento.ENTRADA);
            m.setReferencia(item.getId());
            m.setEstado(true);
            m.setUsuario(usuario);
            movimientoStockService.save(m);

            ProductoVencimiento pv = new ProductoVencimiento();
            pv.setProducto(item.getProducto());
            pv.setPresentacion(item.getPresentacion());
            pv.setSucursal(d.getSucursalOrigen());
            pv.setFechaVencimiento(item.getVencimientoReingreso());
            pv.setCantidad(cantidadBase);
            pv.setTipoOrigen(TipoOrigenVencimiento.DEVOLUCION_CANJE);
            pv.setOrigenId(item.getId());
            pv.setUsuario(usuario);
            productoVencimientoService.save(pv);
        }
    }

    /**
     * Genera el Gasto de merma (perdida contable) para una devolucion SIN_PROVEEDOR.
     * No mueve efectivo por defecto (gasto sin caja). El acople opcional a caja virtual
     * (d.cajaVirtualId -> EGRESO en MovimientoCajaVirtual) se conectara cuando la rama
     * fd-93 (modulo caja mayor) este mergeada en develop.
     */
    private void generarGastoMerma(Devolucion d, Usuario usuario) {
        List<DevolucionItem> items = devolucionItemService.findByDevolucionId(d.getId());
        double total = 0.0;
        for (DevolucionItem item : items) {
            double costo = item.getCostoUnitario() != null ? item.getCostoUnitario() : 0.0;
            total += costo * (item.getCantidad() != null ? item.getCantidad() : 0.0);
        }

        Long sucId = d.getSucursalOrigen().getId();
        Long maxId = gastoRepository.findMaxId(sucId);
        Long nuevoId = (maxId == null ? 0L : maxId) + 1;

        Gasto gasto = new Gasto();
        gasto.setId(nuevoId);
        gasto.setSucursalId(sucId);
        gasto.setTipoGasto(tipoGastoRepository.findFirstByDescripcion(TIPO_GASTO_MERMA));
        gasto.setObservacion("Merma por devolucion " + (d.getIdentificador() != null ? d.getIdentificador() : d.getId()));
        gasto.setUsuario(usuario);
        gasto.setActivo(true);
        gasto.setFinalizado(true);
        gasto.setRetiroGs(total);
        gasto.setCreadoEn(LocalDateTime.now());
        Gasto guardado = gastoService.save(gasto);

        d.setGastoId(guardado.getId());
        d.setGastoSucursalId(guardado.getSucursalId());

        // TODO(fd-93): si d.getCajaVirtualId() != null, registrar EGRESO en la caja virtual
        //   via MovimientoCajaVirtualService (referenciaId = d.getId()). Se activa cuando
        //   financiero.caja_virtual exista en develop.
    }

    private double cantidadEnUnidadBase(DevolucionItem item) {
        double cantidad = item.getCantidad() != null ? item.getCantidad() : 0.0;
        return cantidad * presentacionFactor(item);
    }

    private double presentacionFactor(DevolucionItem item) {
        if (item.getPresentacion() != null && item.getPresentacion().getCantidad() != null) {
            return item.getPresentacion().getCantidad();
        }
        return 1.0;
    }

    // ------------------------------------------------------------------
    // Compatibilidad con la API previa
    // ------------------------------------------------------------------

    @Transactional
    public Devolucion confirmarDevolucion(Long devolucionId) {
        Devolucion d = findById(devolucionId).orElseThrow(
                () -> new GraphQLException("Devolucion no encontrada: " + devolucionId));
        // Ruta legacy: separar (si hace falta) y retirar.
        if (d.getEstado() == DevolucionEstado.PENDIENTE) {
            avanzarEstado(devolucionId, DevolucionEstado.SEPARADO, d.getUsuario());
        }
        return avanzarEstado(devolucionId, DevolucionEstado.RETIRADO, d.getUsuario());
    }

    @Transactional
    public Devolucion cancelarDevolucion(Long devolucionId, String motivoCancelacion) {
        Devolucion d = findById(devolucionId).orElseThrow(
                () -> new GraphQLException("Devolucion no encontrada: " + devolucionId));
        if (motivoCancelacion != null) {
            d.setMotivo((d.getMotivo() != null ? d.getMotivo() : "") + " - CANCELADA: " + motivoCancelacion);
            save(d);
        }
        return avanzarEstado(devolucionId, DevolucionEstado.CANCELADA, d.getUsuario());
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    public Page<Devolucion> findByFilters(Long proveedorId, Long sucursalId,
                                          DevolucionEstado estado,
                                          LocalDateTime fechaInicio, LocalDateTime fechaFin,
                                          Pageable pageable) {
        return repository.findByFilters(proveedorId, sucursalId, estado, fechaInicio, fechaFin, pageable);
    }

    public List<Devolucion> findByProveedorId(Long proveedorId) {
        return repository.findByProveedorId(proveedorId);
    }

    public List<Devolucion> findByEstado(DevolucionEstado estado) {
        return repository.findByEstado(estado);
    }

    /**
     * Devoluciones pendientes de resolucion para un proveedor (no finalizadas).
     * Usado por Compras para alertar al comprar a ese proveedor.
     */
    public List<Devolucion> devolucionesPendientesPorProveedor(Long proveedorId) {
        return repository.findByProveedorIdAndEstados(proveedorId,
                Arrays.asList(DevolucionEstado.PENDIENTE, DevolucionEstado.SEPARADO, DevolucionEstado.RETIRADO));
    }
}
