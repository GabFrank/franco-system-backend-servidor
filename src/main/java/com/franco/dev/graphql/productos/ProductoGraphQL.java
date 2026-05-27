package com.franco.dev.graphql.productos;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.dto.LucroPorProductosDto;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.Familia;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.Subfamilia;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.fmc.service.NotificationTemplateService;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.fmc.service.NotificationRoleService;
import com.franco.dev.graphql.productos.input.LucroPorProductoResponse;
import com.franco.dev.graphql.productos.input.LucroPorProductoSummary;
import com.franco.dev.graphql.productos.input.ProductoInput;
import com.franco.dev.repository.personas.UsuarioRepository;
import com.franco.dev.security.Unsecured;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.*;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.service.utils.PrintingService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Component
public class ProductoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final Logger log = Logger.getLogger(String.valueOf(ProductoService.class));

    @Autowired
    private ProductoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SubFamiliaService subFamiliaService;

    @Autowired
    private FamiliaService familiaService;

    @Autowired
    private IngredienteService ingredienteService;

    @Autowired
    private MovimientoStockService movimientoStockService;

    @Autowired
    private ImageService imageService;

    @Autowired
    private Environment env;

    @Autowired
    private PrintingService printingService;


    @Autowired
    private PresentacionService presentacionService;

    @Autowired
    private ImpresionService impresionService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private CodigoService codigoService;

    @Autowired
    private MultiTenantService multiTenantService;

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private NotificationTemplateService notificationTemplateService;

    @Autowired
    private NotificationRoleService notificationRoleService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Unsecured
    public Optional<Producto> producto(Long id) {
        return service.findById(id);
    }

    public List<Producto> productoSearch(String texto, int offset, Long sucursalId, Boolean conStock, Boolean isEnvase,
            Boolean activo) {
        return service.findByAll(texto, offset, sucursalId, conStock, isEnvase, activo);
    }

    public Page<Producto> searchProductoWithFilters(String texto, String codigo, Boolean activo, Boolean stock,
            Boolean balanza, Long familia, Long subfamilia, Boolean vencimiento, Boolean costoCero, String stockFiltro,
            Long sucursalId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        if (codigo != null && !codigo.trim().isEmpty()) {
            List<Codigo> foundCondigoList = codigoService.findByCodigo(codigo);
            if (foundCondigoList != null && foundCondigoList.size() > 0) {
                if (foundCondigoList.size() == 1) {
                    Producto foundProducto = foundCondigoList.get(0).getPresentacion().getProducto();
                    return new PageImpl<>(Arrays.asList(foundProducto), pageable, 1);
                } else {
                    List<Producto> foundProductoList = foundCondigoList.stream()
                            .map(c -> c.getPresentacion().getProducto()).collect(Collectors.toList());
                    return new PageImpl<>(foundProductoList, pageable, foundProductoList.size());
                }
            } else {
                return new PageImpl<>(new ArrayList<>(), pageable, 0);
            }
        }

        return service.findWithFilters(texto, activo, stock, balanza, subfamilia, familia, vencimiento, costoCero, stockFiltro,
                sucursalId, pageable);
    }

    public List<Producto> productos(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAllForPdv();
    }

    @Autowired
    private org.springframework.context.ApplicationEventPublisher publisher;

    public Producto saveProducto(ProductoInput input) {
        boolean isNewProduct = (input.getId() == null);

        Producto e = service.save(input);
        if (isNewProduct) {
            sendProductoCreadoNotification(e);
        }
        return e;
    }

    public Producto updateProducto(Long id, ProductoInput input) {
        ModelMapper m = new ModelMapper();
        Producto p = service.getOne(id);
        p = m.map(input, Producto.class);
        return service.save(p);
    }

    public Double productoPorSucursalStock(Long proId, Long sucId) {
        Double stock = movimientoStockService.stockByProductoIdAndSucursalId(proId, sucId);
        return stock != null ? stock : 0.0;
    }

    public Boolean deleteProducto(Long id) {
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countProducto() {
        return service.count();
    }

    public List<Producto> productoPorProveedorId(Long id, String texto) {
        return service.findByProveedorId(id, texto);
    }

    public Producto productoPorCodigo(String texto) {
        return service.findByCodigo(texto);
    }

    public Boolean saveImagenProducto(String image, String filename) throws IOException {
        return false;
    }

    public Boolean productoDescripcionExists(String descripcion) {
        if (descripcion == null) {
            return false;
        }
        return service.existsByDescripcion(descripcion);
    }

    public Producto printProducto(Long id) {
        return null;
    }

    public String exportarReporte(String texto) throws FileNotFoundException {
        return service.exportarReporte(texto);
    }

    public String exportarReporteConFiltros(
            String texto,
            Boolean codigo,
            Boolean activo,
            Boolean stock,
            Boolean balanza,
            Boolean vencimiento,
            Boolean costoCero,
            Long familiaId,
            Long subfamiliaId,
            String stockFiltro,
            Long sucursalId,
            Long usuarioId,
            String usuario) throws FileNotFoundException {
        return service.exportarReporteConFiltros(
                texto, codigo, activo, stock, balanza, vencimiento,
                costoCero, subfamiliaId, familiaId, stockFiltro, sucursalId, usuarioId, usuario);
    }

    public List<Producto> findByPdvGrupoProducto(Long id) {
        return service.findByGrupoProductoId(id);
    }

    public String lucroPorProducto(String fechaInicio, String fechaFin, List<Long> sucursalIdList, Long usuarioId,
            List<Long> usuarioIdList, List<Long> productoIdList, Long subfamiliaId, Long familiaId) {
        Usuario usuario = usuarioService.findById(usuarioId).orElse(null);
        StringBuilder filtro = new StringBuilder();
        if (usuarioIdList != null && !usuarioIdList.isEmpty()) {
            filtro.append("Cajero: ");
            for (int i = 0; i < usuarioIdList.size(); i++) {
                Usuario u = usuarioService.findById(usuarioIdList.get(i)).orElse(null);
                if (u != null) {
                    filtro.append(u.getNickname());
                    if (i < usuarioIdList.size() - 1) {
                        filtro.append(", ");
                    }
                }
            }
        }
        if (filtro.length() > 0 && sucursalIdList != null && sucursalIdList.size() > 0) {
            filtro.append("\n");
        }
        if (sucursalIdList != null && sucursalIdList.size() > 0) {
            if (sucursalIdList.size() > 1) {
                filtro.append("Sucursales: ");
            } else {
                filtro.append("Sucursal: ");
            }
        }
        for (Long sucId : sucursalIdList) {
            Sucursal suc = sucursalService.findById(sucId).orElse(null);
            if (suc != null)
                filtro.append(suc.getNombre() + ", ");
        }
        if (familiaId != null) {
            Familia familia = familiaService.findById(familiaId).orElse(null);
            if (familia != null && familia.getNombre() != null) {
                filtro.append("\nFamilia: " + familia.getNombre());
            }
        }
        if (subfamiliaId != null) {
            Subfamilia subfamilia = subFamiliaService.findById(subfamiliaId).orElse(null);
            if (subfamilia != null && subfamilia.getNombre() != null) {
                filtro.append("\nSubfamilia: " + subfamilia.getNombre());
            }
        }
        List<LucroPorProductosDto> lucroPorProductosDtoList = service.findLucroPorProductos(fechaInicio, fechaFin,
                sucursalIdList, usuarioIdList, productoIdList, subfamiliaId, familiaId);
        return impresionService.imprimirReporteLucroPorProducto(lucroPorProductosDtoList, fechaInicio, fechaFin, "",
                filtro.toString(), usuario);
    }

    public LucroPorProductoResponse lucroPorProductoList(
            String fechaInicio,
            String fechaFin,
            List<Long> sucursalIdList,
            List<Long> usuarioIdList,
            List<Long> productoIdList,
            Long subfamiliaId,
            Integer page,
            Integer size,
            Long familiaId) {

        List<LucroPorProductosDto> fullList = service.findLucroPorProductos(fechaInicio, fechaFin,
                sucursalIdList, usuarioIdList, productoIdList, subfamiliaId, familiaId);

        // 1. Calculate Global Summary
        LucroPorProductoSummary summary = new LucroPorProductoSummary();
        summary.setCantidad(0.0);
        summary.setCostoTotal(0.0);
        summary.setTotalVenta(0.0);
        summary.setLucro(0.0);
        summary.setTotalDescuento(0.0);
        summary.setTotalAumento(0.0);

        for (LucroPorProductosDto dto : fullList) {
            summary.setCantidad(summary.getCantidad() + (dto.getCantidad() != null ? dto.getCantidad() : 0));
            summary.setCostoTotal(summary.getCostoTotal() + (dto.getCostoTotal() != null ? dto.getCostoTotal() : 0));
            summary.setTotalVenta(summary.getTotalVenta() + (dto.getTotalVenta() != null ? dto.getTotalVenta() : 0));
            summary.setLucro(summary.getLucro() + (dto.getLucro() != null ? dto.getLucro() : 0));
            summary.setTotalDescuento(
                    summary.getTotalDescuento() + (dto.getTotalDescuento() != null ? dto.getTotalDescuento() : 0));
            summary.setTotalAumento(
                    summary.getTotalAumento() + (dto.getTotalAumento() != null ? dto.getTotalAumento() : 0));
        }

        // Calculate averages for summary
        if (summary.getTotalVenta() > 0) {
            summary.setMargen((summary.getLucro() / summary.getTotalVenta()) * 100);
        } else {
            summary.setMargen(0.0);
        }

        // 2. Pagination Logic
        int start = 0;
        int end = fullList.size();

        if (page != null && size != null) {
            start = page * size;
            end = Math.min(start + size, fullList.size());
        }

        List<LucroPorProductosDto> pagedContent;
        if (start >= fullList.size()) {
            pagedContent = new ArrayList<>();
        } else {
            pagedContent = fullList.subList(start, end);
        }

        // 3. Construct Response
        LucroPorProductoResponse response = new LucroPorProductoResponse();
        response.setContent(pagedContent);
        response.setTotalElements((long) fullList.size());
        response.setSummary(summary);

        return response;
    }

    public Boolean imprimirCodigoBarra(Long codigoId) {
        Codigo codigo = codigoService.findById(codigoId).orElse(null);
        if (codigo != null) {
            impresionService.imprimirCodigoDeBarra(codigo);
            return true;
        } else {
            return false;
        }
    }

    private void sendProductoCreadoNotification(Producto producto) {
        if (producto == null)
            return;

        try {
            if (producto.getSubfamilia() != null) {
                try {
                    producto.getSubfamilia().getNombre();
                } catch (Exception e) {
                }
            }

            List<String> roles = notificationRoleService.getRolesForProductoCreado();
            List<Long> usuarioIds = notificationRoleService.getUserIdsByRoles(roles);

            if (usuarioIds.isEmpty()) {
                return;
            }
            Sucursal sucursal = null;
            if (env != null && sucursalService != null) {
                try {
                    String sucursalIdStr = env.getProperty("sucursalId");
                    if (sucursalIdStr != null) {
                        Long sucursalId = Long.valueOf(sucursalIdStr);
                        Optional<Sucursal> optSuc = sucursalService.findById(sucursalId);
                        if (optSuc != null && optSuc.isPresent()) {
                            sucursal = optSuc.get();
                        }
                    }
                } catch (Exception e) {
                }
            }
            PushNotificationRequest request = notificationTemplateService.productoCreado(producto, sucursal);
            if (request != null) {
                request.setUsuarioIds(usuarioIds);
                pushNotificationService.sendPushNotificationToToken(request);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
