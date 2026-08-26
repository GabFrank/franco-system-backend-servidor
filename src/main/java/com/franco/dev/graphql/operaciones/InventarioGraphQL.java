package com.franco.dev.graphql.operaciones;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.operaciones.Inventario;
import com.franco.dev.domain.operaciones.InventarioProducto;
import com.franco.dev.domain.operaciones.InventarioProductoItem;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.enums.InventarioEstado;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.operaciones.input.InventarioInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.InventarioLoteService;
import com.franco.dev.service.operaciones.InventarioProductoItemService;
import com.franco.dev.service.operaciones.PlanConteoLote;
import com.franco.dev.service.operaciones.InventarioProductoService;
import com.franco.dev.service.operaciones.InventarioService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.service.reports.TicketReportService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import lombok.extern.java.Log;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Log
@Component
public class InventarioGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private InventarioService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private TicketReportService ticketReportService;


    @Autowired
    private MovimientoStockService movimientoStockService;

    @Autowired
    private InventarioProductoService inventarioProductoService;

    @Autowired
    private InventarioProductoItemService inventarioProductoItemService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private InventarioLoteService inventarioLoteService;

    @Autowired
    private MultiTenantService multiTenantService;

    @Autowired
    private com.franco.dev.fmc.service.NotificationTemplateService notificationTemplateService;

    @Autowired
    private com.franco.dev.fmc.service.PushNotificationService pushNotificationService;

    @Autowired
    private com.franco.dev.fmc.service.NotificationRoleService notificationRoleService;

    public Optional<Inventario> inventario(Long id) {
        return service.findById(id);
    }

    public List<Inventario> inventarioList(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    // public List<Inventario> inventarioSearch(String texto){
    // return service.findByAll(texto);
    // }

    public List<Inventario> inventarioPorUsuario(Long id) {
        return service.findByUsuario(id);
    }

    public Page<Inventario> getInventariosPorUsuarioPaginado(Long usuarioId, int page, int size, String sortOrder) {
        return service.findPageByUsuarioId(usuarioId, page, size, sortOrder);
    }

    @Autowired
    private org.springframework.context.ApplicationEventPublisher publisher;

    public Inventario saveInventario(InventarioInput input) {
        ModelMapper m = new ModelMapper();
        Inventario e = m.map(input, Inventario.class);
        boolean esNuevo = input.getId() == null;
        if (input.getFechaInicio() != null)
            e.setFechaInicio(stringToDate(input.getFechaInicio()));
        if (input.getFechaFin() != null)
            e.setFechaFin(stringToDate(input.getFechaFin()));
        if (input.getUsuarioId() != null)
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getSucursalId() != null)
            e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        e = service.save(e);
        if (esNuevo && e.getId() != null) {
            // publisher.publishEvent(new
            // com.franco.dev.fmc.event.InventarioIniciadoEvent(this, e));
            sendInventarioIniciadoNotification(e);
        }

        return e;
    }

    public Boolean deleteInventario(Long id) {
        Boolean ok = false;
        Inventario i = service.findById(id).orElse(null);
        if (i != null) {
            ok = service.deleteById(id);
        }
        return ok;
    }

    public Inventario finalizarInventario(Long id) throws GraphQLException {
        Inventario inventario = service.findById(id).orElse(null);
        if (inventario.getId() != null && inventario.getEstado() != InventarioEstado.CONCLUIDO) {
            inventario = finalizarInventarioEnSucursal(id);
            if (inventario != null) {
                return service.save(inventario);
            }
        }
        return null;
    }

    public Boolean cancelarInventario(Long id) {
        try {
            Inventario inventario = service.findById(id).orElse(null);
            if (inventario.getId() != null) {
                inventario.setEstado(InventarioEstado.CANCELADO);
                List<MovimientoStock> movimientoStockList = movimientoStockService
                        .findByTipoMovimientoAndReferenciaAndSucursalId(TipoMovimiento.AJUSTE, inventario.getId(),
                                inventario.getSucursal().getId());
                for (MovimientoStock ms : movimientoStockList) {
                    if (ms != null) {
                        ms.setEstado(false);
                    }
                    movimientoStockService.save(ms);

                }
                service.save(inventario);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            throw new GraphQLException("No se pudo reabrir el inventario. Comunicarse con RRHH");
        }
    }

    public Boolean reabrirInventario(Long id) {
        try {
            Inventario inventario = service.findById(id).orElse(null);
            if (inventario.getId() != null) {
                inventario.setEstado(InventarioEstado.ABIERTO);
                inventario.setAbierto(true);
                List<MovimientoStock> movimientoStockList = movimientoStockService
                        .findByTipoMovimientoAndReferenciaAndSucursalId(TipoMovimiento.AJUSTE, inventario.getId(),
                                inventario.getSucursal().getId());
                for (MovimientoStock ms : movimientoStockList) {
                    if (ms != null) {
                        ms.setEstado(true);
                    }
                    movimientoStockService.save(ms);

                }
                inventario = service.save(inventario);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            throw new GraphQLException("No se pudo reabrir el inventario. Comunicarse con RRHH");
        }
    }

    public Long countInventario() {
        return service.count();
    }

    public List<Inventario> inventarioPorFecha(String inicio, String fin) {
        return service.findByDate(inicio, fin);
    }

    public List<Inventario> inventarioAbiertoPorSucursal(Long sucId) {
        return service.findInventarioAbiertoPorSucursal(sucId);
    }

    /**
     * Finaliza la toma y ajusta el stock con lo contado.
     *
     * Usado en:
     * - Desktop: Si (modulo de inventario, boton de finalizar)
     * - Mobile: Si (detalle de la toma)
     */
    public Inventario finalizarInventarioEnSucursal(Long id) throws GraphQLException {
        Inventario inventario = service.findById(id).orElse(null);
        if (inventario == null) {
            // Antes seguia y reventaba con un NullPointerException en la linea
            // siguiente, que no dice cual es el problema.
            throw new GraphQLException("No existe el inventario " + id);
        }
        Map<Long, Double> cantidadesPorProducto = new HashMap<>();
        Producto selectedProducto = null;
        try {
            if (inventario.getId() != null) {
                inventario.setEstado(InventarioEstado.CONCLUIDO);
                inventario.setAbierto(false);
                inventario.setFechaFin(LocalDateTime.now());
                List<InventarioProducto> inventarioProductoList = inventarioProductoService.findByInventarioId(id);
                List<MovimientoStock> movimientoStockList = new ArrayList<>();
                for (InventarioProducto ip : inventarioProductoList) {
                    List<InventarioProductoItem> inventarioProductoItemList = inventarioProductoItemService
                            .findByInventarioProductoId(ip.getId());
                    for (InventarioProductoItem ipi : inventarioProductoItemList) {
                        /*
                         * Un item SIN contar no es un item contado en cero.
                         *
                         * `cantidad` es lo contado y es nullable: un item que se
                         * sumo a la toma y que nadie fue a contar la tiene en
                         * null. Multiplicarla reventaba con un NullPointerException
                         * al desempaquetar el Double, asi que ninguna toma con un
                         * item sin contar se podia finalizar.
                         *
                         * Se saltea, no se toma como cero. Si se tomara como cero y
                         * un producto tuviera todos sus items sin contar, el ajuste
                         * le llevaria el stock A CERO sin que nadie hubiera contado
                         * nada — una perdida de stock muda. Salteandolo, ese
                         * producto simplemente no entra en el ajuste.
                         */
                        if (ipi.getCantidad() == null) {
                            continue;
                        }
                        selectedProducto = ipi.getPresentacion().getProducto();
                        cantidadesPorProducto.merge(
                                ipi.getPresentacion().getProducto().getId(),
                                ipi.getCantidad() * ipi.getPresentacion().getCantidad(),
                                Double::sum);
                    }

                }

                /*
                 * El desglose por lote de lo contado, por producto. Una sola pasada por toda la
                 * toma: el mismo lote puede estar en gondola y en deposito, y los dos conteos
                 * suman contra el mismo saldo.
                 */
                Map<Long, Map<Long, Double>> contadoPorLote =
                        inventarioLoteService.contadoPorProductoYLote(id);

                /*
                 * ESTE BUCLE VA AFUERA DEL DE ZONAS, y no es un detalle de estilo.
                 *
                 * Estaba anidado adentro y `cantidadesPorProducto` nunca se limpia, asi que con N
                 * zonas cada movimiento agregado se escribia N veces con acumulados parciales.
                 * Terminaba bien de casualidad —la ultima pasada pisaba con el total correcto—,
                 * pero el desglose por lote son filas NUEVAS con PK propia: escribirlas N veces
                 * multiplica el ledger y ahi no hay pisada que lo salve.
                 */
                for (Map.Entry<Long, Double> entry : cantidadesPorProducto.entrySet()) {
                    Long productoId = entry.getKey();
                    Producto foundProducto = productoService.findById(productoId).orElse(null);
                    Double cantidadTotal = entry.getValue();
                    Double stockSistema = 0.0;
                    MovimientoStock movimientoStockEncontrado = movimientoStockService
                            .findByTipoMovimientoAndReferenciaAndSucursalIdAndProductoId(TipoMovimiento.AJUSTE,
                                    inventario.getId(), inventario.getSucursal().getId(), productoId);
                    if (movimientoStockEncontrado != null) {
                        stockSistema = movimientoStockService.stockByProductoIdExecptMovStockId(
                                movimientoStockEncontrado.getProducto().getId(), movimientoStockEncontrado.getId(),
                                inventario.getSucursal().getId());
                    } else {
                        stockSistema = movimientoStockService.stockByProductoIdAndSucursalId(productoId,
                                inventario.getSucursal().getId());
                    }

                    /*
                     * Todo lo nuevo detras de este if: para los ~8.700 productos sin control de
                     * lote el camino es exactamente el de antes.
                     */
                    boolean conLote = foundProducto != null && Boolean.TRUE.equals(foundProducto.getLote());
                    PlanConteoLote plan = null;
                    if (conLote) {
                        plan = InventarioLoteService.planificar(
                                stockSistema,
                                cantidadTotal,
                                inventarioLoteService.saldosPorLote(productoId,
                                        inventario.getSucursal().getId(), movimientoStockEncontrado),
                                contadoPorLote.getOrDefault(productoId, Collections.emptyMap()));

                        if (plan.isOmitido()) {
                            /*
                             * Hay un lote con saldo que ningun renglon conto. El producto queda
                             * ENTERO afuera: ni movimiento agregado ni filas del ledger.
                             *
                             * Es la misma regla que ya rige para el item con cantidad nula. La
                             * pantalla lo avisa antes con lotesSinContar(), pero la regla se
                             * aplica igual, mire alguien esa pantalla o no.
                             */
                            log.warning("Inventario " + inventario.getId() + ": el producto "
                                    + productoId + " queda fuera del ajuste porque los lotes "
                                    + plan.getLotesSinContar() + " tienen saldo y nadie los conto.");
                            continue;
                        }
                    }

                    if (movimientoStockEncontrado == null) {
                        movimientoStockEncontrado = new MovimientoStock();
                        movimientoStockEncontrado.setTipoMovimiento(TipoMovimiento.AJUSTE);
                        movimientoStockEncontrado.setSucursalId(inventario.getSucursal().getId());
                        movimientoStockEncontrado.setReferencia(inventario.getId());
                        movimientoStockEncontrado.setProducto(foundProducto);
                        movimientoStockEncontrado.setUsuario(inventario.getUsuario());
                        movimientoStockEncontrado.setEstado(true);
                    }
                    Double diferencia = cantidadTotal - stockSistema; // 9 - 10 = -1, 11 - 10 = 1
                    movimientoStockEncontrado.setCantidad(diferencia);
                    movimientoStockEncontrado = movimientoStockService.save(movimientoStockEncontrado);

                    if (conLote) {
                        inventarioLoteService.escribirDesglose(movimientoStockEncontrado, foundProducto,
                                plan, inventario.getUsuario());
                    }
                }
            }
            return inventario;
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }

    }

    private void sendInventarioIniciadoNotification(Inventario inventario) {
        try {
            Long inventarioId = inventario.getId();

            // Initialize Lazy Objects or use what we have
            String sucursalNombre = "Sucursal no especificada";
            if (inventario.getSucursal() != null) {
                // If it's a proxy, we might need to fetch it, but usually if it came from
                // save(e) it might be attached or detached but with ID
                // Let's try to get name safely
                if (inventario.getSucursal().getNombre() != null) {
                    sucursalNombre = inventario.getSucursal().getNombre();
                } else {
                    com.franco.dev.domain.empresarial.Sucursal s = sucursalService
                            .findById(inventario.getSucursal().getId()).orElse(null);
                    if (s != null)
                        sucursalNombre = s.getNombre();
                }
            }

            String usuarioNombre = "Usuario";
            if (inventario.getUsuario() != null) {
                try {
                    // Try to get persona name if possible, or fallback to nickname
                    if (inventario.getUsuario().getPersona() != null
                            && inventario.getUsuario().getPersona().getNombre() != null) {
                        usuarioNombre = inventario.getUsuario().getPersona().getNombre();
                    } else {
                        usuarioNombre = inventario.getUsuario().getNickname();
                    }
                } catch (Exception e) {
                    usuarioNombre = inventario.getUsuario().getNickname();
                }
            }

            String tipoInventario = inventario.getTipo() != null ? inventario.getTipo().name() : "";

            List<String> roles = Arrays.asList(
                    "ADMIN",
                    "SOPORTE",
                    "CREAR INVENTARIO",
                    "VER INVENTARIO",
                    "PARTICIPAR DEL INVENTARIO");
            List<Long> usuarioIds = notificationRoleService.getUserIdsByRoles(roles);

            if (!usuarioIds.isEmpty()) {
                com.franco.dev.fmc.model.PushNotificationRequest request = notificationTemplateService
                        .inventarioIniciado(
                                tipoInventario,
                                sucursalNombre,
                                usuarioNombre,
                                inventario.getId());
                request.setUsuarioIds(usuarioIds);

                pushNotificationService.sendPushNotificationToToken(request);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
