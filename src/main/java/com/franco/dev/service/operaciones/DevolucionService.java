package com.franco.dev.service.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.TipoGasto;
import com.franco.dev.domain.operaciones.*;
import com.franco.dev.domain.operaciones.DevolucionConfiguracion;
import com.franco.dev.domain.operaciones.dto.DevolucionEstancadaDto;
import com.franco.dev.domain.operaciones.dto.DevolucionPorEstadoDto;
import com.franco.dev.domain.operaciones.dto.DevolucionSeriePuntoDto;
import com.franco.dev.domain.operaciones.dto.ResumenDevolucionesDto;
import com.franco.dev.domain.operaciones.dto.TopMotivoDevolucionDto;
import com.franco.dev.domain.operaciones.dto.TopProductoDevueltoDto;
import com.franco.dev.domain.operaciones.dto.TopProveedorDevolucionDto;
import com.franco.dev.domain.operaciones.dto.RetiroBloqueResultadoDto;
import com.franco.dev.domain.operaciones.dto.RetiroCajaDto;
import com.franco.dev.domain.operaciones.dto.RetiroDevolucionResultadoDto;
import com.franco.dev.domain.operaciones.dto.RetiroLineaConsolidadaDto;
import com.franco.dev.domain.operaciones.dto.RetiroProveedorConsolidadoDto;
import com.franco.dev.domain.operaciones.dto.RetiroSucursalGrupoDto;
import com.franco.dev.domain.operaciones.enums.DevolucionEstado;
import com.franco.dev.domain.operaciones.enums.TipoDevolucion;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.operaciones.enums.TipoOrigenVencimiento;
import com.franco.dev.domain.operaciones.enums.TipoResolucionDevolucion;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.service.productos.CodigoService;
import com.franco.dev.utilitarios.PresentacionUtils;
import com.franco.dev.repository.financiero.GastoRepository;
import com.franco.dev.repository.financiero.TipoGastoRepository;
import com.franco.dev.repository.operaciones.DevolucionRepository;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private com.franco.dev.repository.operaciones.DevolucionItemRepository devolucionItemRepository;

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

    @Autowired
    private CodigoService codigoService;

    @Autowired
    private ApplicationContext applicationContext;

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
        // Una devolucion nunca puede vincularse al servidor (sucursal id 0) ni
        // quedar sin sucursal. Se valida solo al crear (id null) para no afectar
        // los save internos de las transiciones de estado.
        if (entity.getId() == null) {
            Sucursal origen = entity.getSucursalOrigen();
            if (origen == null || origen.getId() == null || origen.getId() == 0L) {
                throw new GraphQLException("La devolucion debe tener una sucursal de origen valida (no el servidor)");
            }
        }
        if (entity.getFecha() == null) entity.setFecha(LocalDateTime.now());
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getEstado() == null) entity.setEstado(DevolucionEstado.PENDIENTE);
        if (entity.getFinalizado() == null) entity.setFinalizado(false);
        // Ubicacion por defecto = origen (para cualquier via de creacion).
        if (entity.getSucursalUbicacion() == null) entity.setSucursalUbicacion(entity.getSucursalOrigen());
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
        // Ubicacion inicial = origen; la colecta interna la cambia luego.
        devolucion.setSucursalUbicacion(sucursalOrigen);
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
                // La mercaderia se aparta y sale del stock disponible en este momento
                // (antes se bajaba al RETIRAR). Asi el stock refleja lo realmente
                // vendible/reponible apenas se separa. Tipo segun el destino: devolucion
                // al proveedor o descarte (merma).
                bajarStock(d, tipoBajaSeparado(d), ejecutor);
                break;
            case RETIRADO:
                // El proveedor retira: la mercaderia ya salio del stock al separarse.
                // Solo cambia el estado, sin movimiento de stock.
                break;
            case DESCARTADO:
                // El stock ya bajo al separar; aca solo se confirma la perdida (gasto).
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
                // Si estaba SEPARADO, el stock se habia bajado -> reingresarlo.
                if (d.getEstado() == DevolucionEstado.SEPARADO) {
                    reingresarSeparado(d, ejecutor);
                }
                break;
            default:
                throw new GraphQLException("Estado no soportado: " + nuevoEstado);
        }

        d.setEstado(nuevoEstado);
        return save(d);
    }

    /**
     * Revierte la devolucion un estado hacia atras, solo para transiciones
     * seguras (sin efectos financieros): RETIRADO -> (COLECTADO|SEPARADO segun
     * si fue colectada), COLECTADO -> SEPARADO (resetea ubicacion), SEPARADO ->
     * PENDIENTE (reingresa el stock bajado al separar). No revierte
     * CANJEADO/ACREDITADO/DESCARTADO (nota de credito / merma / reingreso de
     * canje) ni PENDIENTE/CANCELADA.
     */
    @Transactional
    public Devolucion revertirEstado(Long devolucionId, Usuario usuario) {
        Devolucion d = findById(devolucionId).orElseThrow(
                () -> new GraphQLException("Devolucion no encontrada: " + devolucionId));
        Usuario ejecutor = usuario != null ? usuario : d.getUsuario();
        DevolucionEstado actual = d.getEstado();
        RetiroDevolucion retiroPrevio = d.getRetiro();
        ColectaDevolucion colectaPrevio = d.getColecta();
        switch (actual) {
            case RETIRADO: {
                // El retiro no movio stock: solo vuelve al estado fisico previo.
                boolean fueColectada = d.getColectadoEn() != null
                        && d.getSucursalUbicacion() != null
                        && d.getSucursalOrigen() != null
                        && !d.getSucursalUbicacion().getId().equals(d.getSucursalOrigen().getId());
                d.setEstado(fueColectada ? DevolucionEstado.COLECTADO : DevolucionEstado.SEPARADO);
                d.setFinalizado(false);
                d.setRetiro(null); // sale de la operacion de retiro
                break;
            }
            case COLECTADO:
                // Vuelve a SEPARADO en la sucursal de origen; no movio stock.
                d.setEstado(DevolucionEstado.SEPARADO);
                d.setSucursalUbicacion(d.getSucursalOrigen());
                d.setColectadoEn(null);
                d.setColecta(null); // sale de la operacion de colecta
                break;
            case SEPARADO:
                // Deshace la baja de stock hecha al separar.
                reingresarSeparado(d, ejecutor);
                d.setEstado(DevolucionEstado.PENDIENTE);
                break;
            default:
                throw new GraphQLException("No se puede revertir una devolucion en estado " + actual
                        + ". Solo se permite revertir RETIRADO, COLECTADO o SEPARADO.");
        }
        Devolucion saved = save(d);
        // Si la cabecera de la operacion quedo sin lineas, marcarla REVERTIDA.
        if (actual == DevolucionEstado.RETIRADO && retiroPrevio != null
                && repository.countByRetiroId(retiroPrevio.getId()) == 0) {
            RetiroDevolucionService rs = applicationContext.getBean(RetiroDevolucionService.class);
            rs.findById(retiroPrevio.getId()).ifPresent(h -> {
                h.setEstado(RetiroDevolucion.ESTADO_REVERTIDO);
                rs.save(h);
            });
        }
        if (actual == DevolucionEstado.COLECTADO && colectaPrevio != null
                && repository.countByColectaId(colectaPrevio.getId()) == 0) {
            ColectaDevolucionService cs = applicationContext.getBean(ColectaDevolucionService.class);
            cs.findById(colectaPrevio.getId()).ifPresent(h -> {
                h.setEstado(ColectaDevolucion.ESTADO_REVERTIDO);
                cs.save(h);
            });
        }
        return saved;
    }

    /**
     * Revierte una operacion de retiro completa: cada linea vuelve a su estado
     * previo (RETIRADO -> COLECTADO/SEPARADO) y la cabecera queda REVERTIDA.
     */
    @Transactional
    public RetiroDevolucion revertirRetiro(Long retiroId, Usuario usuario) {
        RetiroDevolucionService rs = applicationContext.getBean(RetiroDevolucionService.class);
        RetiroDevolucion header = rs.findById(retiroId).orElseThrow(
                () -> new GraphQLException("Operacion de retiro no encontrada: " + retiroId));
        List<Devolucion> lineas = repository.findByRetiroId(retiroId);
        // Solo se revierte un retiro si sus lineas siguen en RETIRADO. Si alguna
        // ya fue canjeada/acreditada, no es reversible (efectos financieros).
        long finalizadas = lineas.stream()
                .filter(d -> d.getEstado() != DevolucionEstado.RETIRADO).count();
        if (finalizadas > 0) {
            throw new GraphQLException("No se puede revertir el retiro: " + finalizadas
                    + " devolucion(es) ya fueron canjeadas o acreditadas.");
        }
        DevolucionService self = applicationContext.getBean(DevolucionService.class);
        for (Devolucion d : lineas) {
            self.revertirEstado(d.getId(), usuario);
        }
        header.setEstado(RetiroDevolucion.ESTADO_REVERTIDO);
        return rs.save(header);
    }

    /**
     * Revierte una operacion de colecta completa: cada linea vuelve a SEPARADO
     * en su origen y la cabecera queda REVERTIDA.
     */
    @Transactional
    public ColectaDevolucion revertirColecta(Long colectaId, Usuario usuario) {
        ColectaDevolucionService cs = applicationContext.getBean(ColectaDevolucionService.class);
        ColectaDevolucion header = cs.findById(colectaId).orElseThrow(
                () -> new GraphQLException("Operacion de colecta no encontrada: " + colectaId));
        List<Devolucion> lineas = repository.findByColectaId(colectaId);
        // No se puede revertir una colecta si alguna de sus devoluciones ya fue
        // retirada por el proveedor: hay que revertir el retiro primero. Sin este
        // bloqueo, revertir la colecta cascadearia y desharia el retiro.
        long retiradas = lineas.stream()
                .filter(d -> d.getEstado() != DevolucionEstado.COLECTADO).count();
        if (retiradas > 0) {
            throw new GraphQLException("No se puede revertir la colecta: " + retiradas
                    + " devolucion(es) ya fueron retiradas por el proveedor. Reverti el retiro primero.");
        }
        DevolucionService self = applicationContext.getBean(DevolucionService.class);
        for (Devolucion d : lineas) {
            self.revertirEstado(d.getId(), usuario);
        }
        header.setEstado(ColectaDevolucion.ESTADO_REVERTIDO);
        return cs.save(header);
    }

    private TipoMovimiento tipoBajaSeparado(Devolucion d) {
        return d.getTipo() == TipoDevolucion.CON_PROVEEDOR
                ? TipoMovimiento.DEVOLUCION : TipoMovimiento.DESCARTE;
    }

    /**
     * Colecta interna: la mercaderia SEPARADA se envia a un deposito. Solo cambia
     * la sucursal de ubicacion fisica; el stock ya bajo al separar, no se mueve.
     */
    @Transactional
    public Devolucion colectar(Long devolucionId, Long sucursalDestinoId, Usuario usuario) {
        Devolucion d = findById(devolucionId).orElseThrow(
                () -> new GraphQLException("Devolucion no encontrada: " + devolucionId));
        validarTransicion(d, DevolucionEstado.COLECTADO);
        if (sucursalDestinoId == null) {
            throw new GraphQLException("La sucursal destino es requerida para colectar");
        }
        Sucursal destino = applicationContext.getBean(com.franco.dev.service.empresarial.SucursalService.class)
                .findById(sucursalDestinoId)
                .orElseThrow(() -> new GraphQLException("Sucursal destino no encontrada: " + sucursalDestinoId));
        d.setSucursalUbicacion(destino);
        d.setColectadoEn(LocalDateTime.now());
        d.setEstado(DevolucionEstado.COLECTADO);
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

    /**
     * Baja de stock por cada item (movimiento negativo).
     * Valida saldo suficiente salvo que la configuracion permita stock negativo
     * (permitirStockNegativo, default true).
     */
    private void bajarStock(Devolucion d, TipoMovimiento tipoMovimiento, Usuario usuario) {
        List<DevolucionItem> items = devolucionItemService.findByDevolucionId(d.getId());
        if (items.isEmpty()) {
            throw new GraphQLException("No se puede procesar una devolucion sin items");
        }
        boolean permiteNegativo = Boolean.TRUE.equals(applicationContext
                .getBean(DevolucionConfiguracionService.class).getConfiguracion().getPermitirStockNegativo());
        Long sucId = d.getSucursalOrigen().getId();
        for (DevolucionItem item : items) {
            double cantidadBase = cantidadEnUnidadBase(item);
            Double stockActual = movimientoStockService.stockByProductoIdAndSucursalId(item.getProducto().getId(), sucId);
            if (stockActual == null) stockActual = 0.0;
            if (!permiteNegativo && stockActual < cantidadBase) {
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

    /**
     * Reingreso del stock bajado al SEPARAR, cuando se cancela desde SEPARADO.
     * Reingresa en la ubicacion actual (== origen: cancelar solo se permite antes
     * de colectar).
     */
    private void reingresarSeparado(Devolucion d, Usuario usuario) {
        List<DevolucionItem> items = devolucionItemService.findByDevolucionId(d.getId());
        Long sucId = sucursalUbicacion(d).getId();
        for (DevolucionItem item : items) {
            double cantidadBase = cantidadEnUnidadBase(item);
            MovimientoStock m = new MovimientoStock();
            m.setProducto(item.getProducto());
            m.setSucursalId(sucId);
            m.setCantidad(cantidadBase);
            m.setTipoMovimiento(TipoMovimiento.ENTRADA);
            m.setReferencia(item.getId());
            m.setEstado(true);
            m.setUsuario(usuario);
            movimientoStockService.save(m);
        }
    }

    /** Ubicacion fisica actual: la de ubicacion si esta seteada, si no la de origen. */
    private Sucursal sucursalUbicacion(Devolucion d) {
        return d.getSucursalUbicacion() != null ? d.getSucursalUbicacion() : d.getSucursalOrigen();
    }

    /** Reingreso del producto canjeado por el proveedor, con nuevo vencimiento. */
    private void reingresarCanje(Devolucion d, Usuario usuario) {
        List<DevolucionItem> items = devolucionItemService.findByDevolucionId(d.getId());
        Sucursal sucUbicacion = sucursalUbicacion(d);
        Long sucId = sucUbicacion.getId();
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
            pv.setSucursal(sucUbicacion);
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
        DevolucionConfiguracion config = applicationContext
                .getBean(DevolucionConfiguracionService.class).getConfiguracion();
        boolean respetaMotivo = Boolean.TRUE.equals(config.getMermaRespetaMotivo());

        List<DevolucionItem> items = devolucionItemService.findByDevolucionId(d.getId());
        double total = 0.0;
        for (DevolucionItem item : items) {
            // Con respetaMotivo, solo suman los items cuyo motivo genera gasto.
            if (respetaMotivo && !(item.getMotivoAveria() != null
                    && Boolean.TRUE.equals(item.getMotivoAveria().getGeneraGasto()))) {
                continue;
            }
            double costo = item.getCostoUnitario() != null ? item.getCostoUnitario() : 0.0;
            total += costo * (item.getCantidad() != null ? item.getCantidad() : 0.0);
        }

        // Si ningun item genera gasto (respetaMotivo y todos exentos), no se crea gasto.
        if (respetaMotivo && total == 0.0) {
            return;
        }

        String tipoGastoDesc = config.getTipoGastoMerma() != null
                ? config.getTipoGastoMerma() : TIPO_GASTO_MERMA;

        Long sucId = d.getSucursalOrigen().getId();
        Long maxId = gastoRepository.findMaxId(sucId);
        Long nuevoId = (maxId == null ? 0L : maxId) + 1;

        Gasto gasto = new Gasto();
        gasto.setId(nuevoId);
        gasto.setSucursalId(sucId);
        gasto.setTipoGasto(tipoGastoRepository.findFirstByDescripcion(tipoGastoDesc));
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
                                          Long usuarioId, Pageable pageable) {
        return repository.findByFilters(proveedorId, sucursalId, estado, fechaInicio, fechaFin, usuarioId, pageable);
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
                Arrays.asList(DevolucionEstado.PENDIENTE, DevolucionEstado.SEPARADO,
                        DevolucionEstado.COLECTADO, DevolucionEstado.RETIRADO));
    }

    // ------------------------------------------------------------------
    // Retiro consolidado por proveedor (FASE A)
    // ------------------------------------------------------------------

    /**
     * Vista consolidada de las devoluciones en estado SEPARADO de un proveedor, lista
     * para el retiro fisico. Agrupa por sucursal de origen; dentro de cada grupo
     * consolida las lineas por producto+presentacion (sumando la cantidad en unidades
     * de presentacion) y ademas expone el desglose por caja (una entrada por item con
     * el identificador de su devolucion).
     *
     * @param proveedorId proveedor a retirar (no-null)
     * @param sucursalId  opcional; si viene, filtra el retiro a una sola sucursal
     *
     * Usado en:
     * - Desktop: Si (pantalla de retiro consolidado de devoluciones)
     * - Mobile: No
     */
    @Transactional(readOnly = true)
    public RetiroProveedorConsolidadoDto getRetiroConsolidado(Long proveedorId, Long sucursalId) {
        if (proveedorId == null) {
            throw new GraphQLException("proveedorId es requerido");
        }
        // Listo para retiro = SEPARADO o COLECTADO (ya en el deposito).
        List<Devolucion> devoluciones = repository.findByProveedorIdAndEstados(proveedorId,
                Arrays.asList(DevolucionEstado.SEPARADO, DevolucionEstado.COLECTADO));
        // El filtro y la agrupacion son por UBICACION fisica actual, no por origen:
        // lo colectado a un deposito se retira desde ese deposito.
        if (sucursalId != null) {
            devoluciones.removeIf(d -> !sucursalId.equals(sucursalUbicacion(d).getId()));
        }

        // Nombre del proveedor (via persona, puede faltar)
        String proveedorNombre = null;
        for (Devolucion d : devoluciones) {
            if (d.getProveedor() != null && d.getProveedor().getPersona() != null) {
                proveedorNombre = d.getProveedor().getPersona().getNombre();
                break;
            }
        }

        // Agrupar por sucursal de ubicacion preservando el orden
        Map<Long, List<Devolucion>> porSucursal = new LinkedHashMap<>();
        for (Devolucion d : devoluciones) {
            porSucursal.computeIfAbsent(sucursalUbicacion(d).getId(), k -> new ArrayList<>()).add(d);
        }

        List<RetiroSucursalGrupoDto> grupos = new ArrayList<>();
        for (Map.Entry<Long, List<Devolucion>> entry : porSucursal.entrySet()) {
            List<Devolucion> devsSucursal = entry.getValue();
            Sucursal sucursal = sucursalUbicacion(devsSucursal.get(0));
            Long sucId = sucursal != null ? sucursal.getId() : null;
            String sucNombre = sucursal != null ? sucursal.getNombre() : null;

            // Consolidacion por producto+presentacion
            Map<String, RetiroLineaConsolidadaDto> lineasMap = new LinkedHashMap<>();
            List<RetiroCajaDto> cajas = new ArrayList<>();
            List<Long> devolucionIds = new ArrayList<>();

            for (Devolucion d : devsSucursal) {
                devolucionIds.add(d.getId());
                List<DevolucionItem> items = devolucionItemService.findByDevolucionId(d.getId());
                for (DevolucionItem item : items) {
                    Long productoId = item.getProducto() != null ? item.getProducto().getId() : null;
                    String descripcion = item.getProducto() != null ? item.getProducto().getDescripcion() : null;
                    Presentacion pres = item.getPresentacion();
                    Long presId = pres != null ? pres.getId() : null;
                    // Factor ("x1", "x12"): al retirar importa cuantas unidades base
                    // entran en el bulto, no el nombre de la presentacion.
                    String presDesc = PresentacionUtils.formatearFactor(pres);
                    Double cantidad = item.getCantidad() != null ? item.getCantidad() : 0.0;

                    String key = productoId + "|" + presId;
                    RetiroLineaConsolidadaDto linea = lineasMap.get(key);
                    if (linea == null) {
                        String codigo = null;
                        if (presId != null) {
                            Codigo cod = codigoService.findPrincipalByPresentacionId(presId);
                            if (cod != null) codigo = cod.getCodigo();
                        }
                        linea = new RetiroLineaConsolidadaDto(productoId, codigo, descripcion, presDesc, 0.0);
                        lineasMap.put(key, linea);
                    }
                    linea.setCantidadTotal(linea.getCantidadTotal() + cantidad);

                    cajas.add(new RetiroCajaDto(
                            d.getIdentificador(),
                            d.getId(),
                            productoId,
                            descripcion,
                            cantidad,
                            item.getLote(),
                            item.getVencimiento()));
                }
            }

            grupos.add(new RetiroSucursalGrupoDto(sucId, sucNombre,
                    new ArrayList<>(lineasMap.values()), cajas, devolucionIds));
        }

        return new RetiroProveedorConsolidadoDto(proveedorId, proveedorNombre, LocalDateTime.now(), grupos);
    }

    /**
     * Retira en bloque varias devoluciones: intenta avanzar cada una a RETIRADO
     * (reusa avanzarEstado, que valida stock y genera el MovimientoStock). El fallo
     * de una devolucion (ej. stock insuficiente) NO aborta el resto; se reporta por id.
     *
     * Usado en:
     * - Desktop: Si (retiro consolidado de devoluciones)
     * - Mobile: No
     */
    public RetiroBloqueResultadoDto retirarEnBloque(List<Long> devolucionIds, Usuario usuario) {
        List<RetiroDevolucionResultadoDto> resultados = new ArrayList<>();
        if (devolucionIds == null || devolucionIds.isEmpty()) {
            return new RetiroBloqueResultadoDto(resultados);
        }
        // Una operacion de retiro = un unico proveedor. Se crea la cabecera y cada
        // linea se procesa en su propia transaccion (via proxy) sin arrastrar fallos.
        Proveedor proveedor = validarMismoProveedor(devolucionIds);
        RetiroDevolucionService rs = applicationContext.getBean(RetiroDevolucionService.class);
        RetiroDevolucion header = rs.crear(proveedor, usuario);
        DevolucionService self = applicationContext.getBean(DevolucionService.class);
        int ok = 0;
        for (Long id : devolucionIds) {
            try {
                self.retirarLinea(id, header.getId(), usuario);
                resultados.add(new RetiroDevolucionResultadoDto(id, true, "RETIRADO"));
                ok++;
            } catch (Exception e) {
                resultados.add(new RetiroDevolucionResultadoDto(id, false, e.getMessage()));
            }
        }
        if (ok == 0) {
            rs.delete(header.getId());
        }
        return new RetiroBloqueResultadoDto(resultados);
    }

    /** Todas las devoluciones deben ser del mismo proveedor; devuelve ese proveedor. */
    private Proveedor validarMismoProveedor(List<Long> devolucionIds) {
        Proveedor proveedor = null;
        for (Long id : devolucionIds) {
            Devolucion d = findById(id).orElseThrow(
                    () -> new GraphQLException("Devolucion no encontrada: " + id));
            if (d.getProveedor() == null) {
                throw new GraphQLException("La devolucion " + id + " no tiene proveedor; no se puede retirar");
            }
            if (proveedor == null) {
                proveedor = d.getProveedor();
            } else if (!proveedor.getId().equals(d.getProveedor().getId())) {
                throw new GraphQLException("El retiro debe ser de un unico proveedor");
            }
        }
        return proveedor;
    }

    @Transactional(readOnly = true)
    public List<Devolucion> findByRetiroId(Long retiroId) {
        return repository.findByRetiroId(retiroId);
    }

    @Transactional(readOnly = true)
    public List<Devolucion> findByColectaId(Long colectaId) {
        return repository.findByColectaId(colectaId);
    }

    /** Una linea del retiro: pasa a RETIRADO (sin stock) y la vincula a la cabecera. */
    @Transactional
    public Devolucion retirarLinea(Long devolucionId, Long retiroId, Usuario usuario) {
        Devolucion d = findById(devolucionId).orElseThrow(
                () -> new GraphQLException("Devolucion no encontrada: " + devolucionId));
        validarTransicion(d, DevolucionEstado.RETIRADO);
        d.setEstado(DevolucionEstado.RETIRADO); // el retiro no mueve stock
        RetiroDevolucion retiro = applicationContext.getBean(RetiroDevolucionService.class)
                .findById(retiroId).orElse(null);
        d.setRetiro(retiro);
        return save(d);
    }

    /**
     * Colecta interna en bloque: agrupa por sucursal de origen y crea una cabecera
     * de colecta por cada origen (una colecta = un viaje origen -> destino). El
     * fallo de una linea no aborta el resto; cada linea corre en su propia tx.
     */
    public RetiroBloqueResultadoDto colectarEnBloque(List<Long> devolucionIds, Long sucursalDestinoId,
                                                     Usuario usuario) {
        List<RetiroDevolucionResultadoDto> resultados = new ArrayList<>();
        if (devolucionIds == null || devolucionIds.isEmpty()) {
            return new RetiroBloqueResultadoDto(resultados);
        }
        if (sucursalDestinoId == null) {
            throw new GraphQLException("La sucursal destino es requerida para colectar");
        }
        Sucursal destino = applicationContext.getBean(com.franco.dev.service.empresarial.SucursalService.class)
                .findById(sucursalDestinoId)
                .orElseThrow(() -> new GraphQLException("Sucursal destino no encontrada: " + sucursalDestinoId));
        ColectaDevolucionService cs = applicationContext.getBean(ColectaDevolucionService.class);
        DevolucionService self = applicationContext.getBean(DevolucionService.class);
        Map<Long, Long> colectaIdPorOrigen = new HashMap<>();
        Map<Long, Integer> okPorColecta = new HashMap<>();
        for (Long id : devolucionIds) {
            try {
                Devolucion d = findById(id).orElseThrow(
                        () -> new GraphQLException("Devolucion no encontrada: " + id));
                Long origenId = d.getSucursalOrigen().getId();
                Long colectaId = colectaIdPorOrigen.get(origenId);
                if (colectaId == null) {
                    colectaId = cs.crear(d.getSucursalOrigen(), destino, usuario).getId();
                    colectaIdPorOrigen.put(origenId, colectaId);
                    okPorColecta.put(colectaId, 0);
                }
                self.colectarLinea(id, sucursalDestinoId, colectaId, usuario);
                okPorColecta.merge(colectaId, 1, Integer::sum);
                resultados.add(new RetiroDevolucionResultadoDto(id, true, "COLECTADO"));
            } catch (Exception e) {
                resultados.add(new RetiroDevolucionResultadoDto(id, false, e.getMessage()));
            }
        }
        // Limpiar cabeceras que no recibieron ninguna linea.
        for (Long colectaId : colectaIdPorOrigen.values()) {
            if (okPorColecta.getOrDefault(colectaId, 0) == 0) {
                cs.delete(colectaId);
            }
        }
        return new RetiroBloqueResultadoDto(resultados);
    }

    /** Una linea de la colecta: pasa a COLECTADO en el destino y la vincula a la cabecera. */
    @Transactional
    public Devolucion colectarLinea(Long devolucionId, Long sucursalDestinoId, Long colectaId, Usuario usuario) {
        Devolucion d = findById(devolucionId).orElseThrow(
                () -> new GraphQLException("Devolucion no encontrada: " + devolucionId));
        validarTransicion(d, DevolucionEstado.COLECTADO);
        Sucursal destino = applicationContext.getBean(com.franco.dev.service.empresarial.SucursalService.class)
                .findById(sucursalDestinoId)
                .orElseThrow(() -> new GraphQLException("Sucursal destino no encontrada: " + sucursalDestinoId));
        d.setSucursalUbicacion(destino);
        d.setColectadoEn(LocalDateTime.now());
        d.setEstado(DevolucionEstado.COLECTADO);
        ColectaDevolucion colecta = applicationContext.getBean(ColectaDevolucionService.class)
                .findById(colectaId).orElse(null);
        d.setColecta(colecta);
        return save(d);
    }

    // ===================== Dashboard =====================

    /**
     * Resumen agregado para el dashboard. Compone los conteos (nivel Devolucion)
     * con los valores costoUnitario x cantidad (nivel item, query aparte).
     */
    @Transactional(readOnly = true)
    public ResumenDevolucionesDto getResumen(LocalDateTime fechaInicio, LocalDateTime fechaFin, Long sucursalId) {
        ResumenDevolucionesDto resumen = repository.resumenConteos(fechaInicio, fechaFin, sucursalId);
        if (resumen == null) {
            resumen = new ResumenDevolucionesDto(0L, 0L, 0L, 0L);
        }
        Double valorTotal = devolucionItemRepository.valorTotal(fechaInicio, fechaFin, sucursalId);
        Double valorMerma = devolucionItemRepository.valorMerma(fechaInicio, fechaFin, sucursalId);
        resumen.setValorTotal(valorTotal != null ? valorTotal : 0.0);
        resumen.setValorMerma(valorMerma != null ? valorMerma : 0.0);
        return resumen;
    }

    @Transactional(readOnly = true)
    public List<DevolucionPorEstadoDto> getConteoPorEstado(LocalDateTime fechaInicio, LocalDateTime fechaFin,
                                                           Long sucursalId) {
        return repository.conteoPorEstado(fechaInicio, fechaFin, sucursalId);
    }

    @Transactional(readOnly = true)
    public List<TopProductoDevueltoDto> getTopProductos(LocalDateTime fechaInicio, LocalDateTime fechaFin,
                                                        Long sucursalId, int limite) {
        return devolucionItemRepository.topProductos(fechaInicio, fechaFin, sucursalId,
                org.springframework.data.domain.PageRequest.of(0, limite));
    }

    @Transactional(readOnly = true)
    public List<TopMotivoDevolucionDto> getTopMotivos(LocalDateTime fechaInicio, LocalDateTime fechaFin,
                                                      Long sucursalId, int limite) {
        return devolucionItemRepository.topMotivos(fechaInicio, fechaFin, sucursalId,
                org.springframework.data.domain.PageRequest.of(0, limite));
    }

    @Transactional(readOnly = true)
    public List<TopProveedorDevolucionDto> getTopProveedores(LocalDateTime fechaInicio, LocalDateTime fechaFin,
                                                             Long sucursalId, int limite) {
        return devolucionItemRepository.topProveedores(fechaInicio, fechaFin, sucursalId,
                org.springframework.data.domain.PageRequest.of(0, limite));
    }

    @Transactional(readOnly = true)
    public List<DevolucionSeriePuntoDto> getSeriePorDia(LocalDateTime fechaInicio, LocalDateTime fechaFin,
                                                        Long sucursalId) {
        return mapSerie(repository.seriePorDiaRaw(fechaInicio, fechaFin, sucursalId));
    }

    @Transactional(readOnly = true)
    public List<DevolucionSeriePuntoDto> getSeriePorMes(LocalDateTime fechaInicio, LocalDateTime fechaFin,
                                                        Long sucursalId) {
        return mapSerie(repository.seriePorMesRaw(fechaInicio, fechaFin, sucursalId));
    }

    private List<DevolucionSeriePuntoDto> mapSerie(List<Object[]> raw) {
        List<DevolucionSeriePuntoDto> serie = new ArrayList<>();
        if (raw != null) {
            for (Object[] fila : raw) {
                serie.add(new DevolucionSeriePuntoDto(
                        fila[0] != null ? fila[0].toString() : null,
                        aLong(fila[1]),
                        aDouble(fila[2])));
            }
        }
        return serie;
    }

    /**
     * Devoluciones estancadas (PENDIENTE/SEPARADO con mas de diasMinimos de
     * antiguedad). diasMinimos por defecto 30 si viene null.
     */
    @Transactional(readOnly = true)
    public List<DevolucionEstancadaDto> getEstancadas(Integer diasMinimos, Long sucursalId, int limite) {
        Integer defaultDias = applicationContext.getBean(DevolucionConfiguracionService.class)
                .getConfiguracion().getDiasEstancada();
        int dias = diasMinimos != null ? diasMinimos : (defaultDias != null ? defaultDias : 30);
        LocalDateTime limiteFecha = LocalDateTime.now().minusDays(dias);
        List<Object[]> raw = repository.estancadasRaw(limiteFecha, sucursalId, limite);
        List<DevolucionEstancadaDto> lista = new ArrayList<>();
        if (raw != null) {
            for (Object[] f : raw) {
                lista.add(new DevolucionEstancadaDto(
                        aLong(f[0]),
                        f[1] != null ? f[1].toString() : null,
                        f[2] != null ? f[2].toString() : null,
                        f[3] != null ? f[3].toString() : null,
                        f[4] != null ? f[4].toString() : null,
                        f[5] != null ? f[5].toString() : null,
                        aLong(f[6])));
            }
        }
        return lista;
    }

    private Double aDouble(Object o) {
        return o instanceof Number ? ((Number) o).doubleValue() : 0.0;
    }

    private Long aLong(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : 0L;
    }
}
