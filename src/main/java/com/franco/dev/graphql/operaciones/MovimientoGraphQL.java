package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.dto.StockPorTipoMovimientoDto;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.fmc.service.NotificationTemplateService;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.fmc.service.NotificationRoleService;
import com.franco.dev.graphql.operaciones.input.MovimientoStockInput;
import com.franco.dev.repository.personas.UsuarioRepository;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.CodigoService;
import com.franco.dev.service.productos.ProductoService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class MovimientoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private MovimientoStockService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private CodigoService codigoService;

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private NotificationTemplateService notificationTemplateService;

    @Autowired
    private NotificationRoleService notificationRoleService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private static final DecimalFormat df = new DecimalFormat("#,###.##");

    public Page<MovimientoStock> findMovimientoStockByFilters(String inicio,
            String fin,
            List<Long> sucursalList,
            Long productoId,
            List<TipoMovimiento> tipoMovimientoList,
            Long usuarioId,
            Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        if (productoId == null || inicio == null || fin == null)
            return null;
        return service.findMovimientoStockWithFilters(stringToDate(inicio), stringToDate(fin), sucursalList, productoId,
                tipoMovimientoList, usuarioId, pageable);
    }

    public Optional<MovimientoStock> movimientoStock(Long id, Long sucId) {
        return service.findById(new EmbebedPrimaryKey(id, sucId));
    }

    public List<MovimientoStock> movimientosStock(int page, int size, Long sucId) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public MovimientoStock saveMovimientoStock(MovimientoStockInput input) {
        try {
            MovimientoStock e = new MovimientoStock();
            if (input.getId() != null && input.getId() != 0) {
                e.setId(input.getId());
            }
            e.setSucursalId(input.getSucursalId());
            e.setTipoMovimiento(input.getTipoMovimiento());
            e.setReferencia(input.getReferencia() != null ? input.getReferencia().longValue() : null);
            e.setCantidad(input.getCantidad() != null ? input.getCantidad().doubleValue() : null);
            e.setEstado(input.getEstado());

            if (input.getUsuarioId() != null) {
                e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
            }
            if (input.getProductoId() != null) {
                e.setProducto(productoService.findById(input.getProductoId()).orElse(null));
            }

            MovimientoStock saved = service.save(e);
            try {
                enviarNotificacionAjusteStock(saved);
            } catch (Throwable t) {
                // Silent notification error
            }

            return saved;
        } catch (Exception ex) {
            System.out.println("Error in saveMovimientoStock: " + ex.getMessage());
            ex.printStackTrace();
            throw ex;
        }
    }

    public Boolean deleteMovimientoStock(Long id, Long sucId) {
        return service.deleteById(new EmbebedPrimaryKey(id, sucId));
    }

    public Long countMovimientoStock() {
        return service.count();
    }

    public List<MovimientoStock> movimientoStockByFecha(String inicio, String fin, Long sucId) {
        return service.findByDate(inicio, fin);
    }

    public Double stockPorProducto(Long id, Long sucId) {
        if (sucId == null)
            return service.stockByProductoId(id);
        return service.stockByProductoIdAndSucursalId(id, sucId);
    }

    public Double stockByProductoIdExeptMovimientoId(Long productoId, Long movimientoId, Long sucursalId) {
        return service.stockByProductoIdExecptMovStockId(productoId, movimientoId, sucursalId);
    }

    public Double stockByProductoIdAntesDeFecha(Long productoId, Long sucursalId, String fecha) {
        LocalDateTime fechaDate = stringToDate(fecha);
        return service.stockByProductoIdAndSucursalIdAntesDeFecha(productoId, sucursalId, fechaDate);
    }

    public Double findStockWithFilters(String inicio,
            String fin,
            List<Long> sucursalList,
            Long productoId,
            List<TipoMovimiento> tipoMovimientoList,
            Long usuarioId) {
        if (productoId == null || inicio == null || fin == null)
            return null;
        return service.findStockWithFilters(stringToDate(inicio), stringToDate(fin), sucursalList, productoId,
                tipoMovimientoList, usuarioId);
    }

    public List<StockPorTipoMovimientoDto> findStockPorTipoMovimiento(String inicio,
            String fin,
            List<Long> sucursalList,
            Long productoId,
            List<TipoMovimiento> tipoMovimientoList,
            Long usuarioId) {
        if (productoId == null || inicio == null || fin == null)
            return null;
        return service.findStockPorTipoMovimiento(stringToDate(inicio), stringToDate(fin), sucursalList, productoId,
                tipoMovimientoList, usuarioId);
    }

    private void enviarNotificacionAjusteStock(MovimientoStock saved) {
        if (saved == null || saved.getSucursalId() == null) {
            return;
        }

        try {
            List<String> roles = notificationRoleService.getRolesForAjusteStock();
            List<Long> usuarioIds = notificationRoleService.getUserIdsByRoles(roles);

            if (usuarioIds.isEmpty()) {
                return;
            }
            Sucursal sucursal = null;
            if (sucursalService != null) {
                try {
                    Optional<Sucursal> optSuc = sucursalService.findById(saved.getSucursalId());
                    if (optSuc != null && optSuc.isPresent()) {
                        sucursal = optSuc.get();
                    }
                } catch (Exception e) {
                }
            }
            PushNotificationRequest request = notificationTemplateService.ajusteStock(saved, sucursal, df);
            if (request != null) {
                request.setUsuarioIds(usuarioIds);
                pushNotificationService.sendPushNotificationToToken(request);
            }
        } catch (Exception e) {
            // Silent notification error
        }
    }

}
