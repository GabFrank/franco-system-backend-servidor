package com.franco.dev.graphql.operaciones;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.operaciones.InventarioProductoItem;
import com.franco.dev.domain.operaciones.dto.ReporteInventarioDto;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.operaciones.input.InventarioProductoItemInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.operaciones.InventarioProductoItemService;
import com.franco.dev.service.operaciones.InventarioProductoService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.utilitarios.DateUtils;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.time.LocalDateTime;
import java.util.*;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class InventarioProductoItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private InventarioProductoItemService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PresentacionService presentacionService;

    @Autowired
    private InventarioProductoService inventarioProductoService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private ImageService imageService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<InventarioProductoItem> inventarioProductoItem(Long id) {
        return service.findById(id);
    }

    public List<InventarioProductoItem> inventarioProductosItem(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<InventarioProductoItem> inventarioProductosItemPorInventarioProducto(Long id, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findByInventarioProductoId(id, pageable);
    }

    public InventarioProductoItem saveInventarioProductoItem(InventarioProductoItemInput input) {
        ModelMapper m = new ModelMapper();
        m.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STRICT);
        m.getConfiguration().setAmbiguityIgnored(true);
        InventarioProductoItem e = m.map(input, InventarioProductoItem.class);
        if (input.getVencimiento() != null) e.setVencimiento(stringToDate(input.getVencimiento()));
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getPresentacionId() != null)
            e.setPresentacion(presentacionService.findById(input.getPresentacionId()).orElse(null));
        if (input.getInventarioProductoId() != null)
            e.setInventarioProducto(inventarioProductoService.findById(input.getInventarioProductoId()).orElse(null));
        e = service.save(e);
//        propagacionService.propagarEntidad(e, TipoEntidad.INVENTARIO_PRODUCTO_ITEM, e.getInventarioProducto().getInventario().getSucursal().getId());
        multiTenantService.compartir("filial"+e.getInventarioProducto().getInventario().getSucursal().getId()+"_bkp", (InventarioProductoItem s) -> service.save(s), e);
        return e;
    }

    public Boolean deleteInventarioProductoItem(Long id) {
        Boolean ok = false;
        InventarioProductoItem i = service.findById(id).orElse(null);
        if (i != null) {
            ok = service.deleteById(id);
//            propagacionService.eliminarEntidad(i, TipoEntidad.INVENTARIO_PRODUCTO_ITEM, i.getInventarioProducto().getInventario().getSucursal().getId());
            multiTenantService.compartir("filial"+i.getInventarioProducto().getInventario().getSucursal().getId()+"_bkp", (Long s) -> service.deleteById(s), id);
        }
        return ok;
    }

    public Long countInventarioProductoItem() {
        return service.count();
    }

    public Page<InventarioProductoItem> inventarioProductoItemWithFilter(
            String startDate,
            String endDate,
            List<Long> sucursalIdList,
            List<Long> usuarioIdList,
            List<Long> productoIdList,
            Integer page, Integer size, String orderBy, String tipoOrder) {
        Pageable pageable = PageRequest.of(page, size, tipoOrder != null ? Sort.Direction.valueOf(tipoOrder) : Sort.Direction.valueOf("ASC"), orderBy != null ? orderBy : "id");
        return service.findAllWithFilters(sucursalIdList,
                stringToDate(startDate),
                stringToDate(endDate),
                usuarioIdList,
                productoIdList,
                pageable);
    }

    public String reporteInventario(
            String startDate,
            String endDate,
            List<Long> sucursalIdList,
            List<Long> usuarioIdList,
            List<Long> productoIdList,
            Integer page,
            Integer size,
            String orderBy,
            String tipoOrder,
            String nickname
    ) {
        File file = null;
        Page<InventarioProductoItem> inventarioProductoItemPage = service.findAllWithFilters(sucursalIdList,
                stringToDate(startDate),
                stringToDate(endDate),
                usuarioIdList,
                productoIdList,
                null);
        if (inventarioProductoItemPage.getContent() != null) {
            List<InventarioProductoItem> inventarioProductoItemList = inventarioProductoItemPage.getContent();
            try {
                List<ReporteInventarioDto> reporteInventarioDtoList = new ArrayList<>();
                for (InventarioProductoItem item : inventarioProductoItemList) {
                    ReporteInventarioDto reporteInventarioDto = new ReporteInventarioDto();
                    reporteInventarioDto.setProductoId(item.getPresentacion().getProducto().getId());
                    reporteInventarioDto.setDescripcion(item.getPresentacion().getProducto().getDescripcion());
                    reporteInventarioDto.setCantidadEncontrada(item.getCantidadFisica());
                    reporteInventarioDto.setCantidadSistema(item.getCantidad());
                    reporteInventarioDto.setSaldo(item.getCantidad() - item.getCantidadFisica());
                    if (reporteInventarioDto.getSaldo() < 0) {
                        reporteInventarioDto.setEstado("FALTA");
                    } else if (reporteInventarioDto.getSaldo() == 0) {
                        reporteInventarioDto.setEstado("OK");
                    } else if (reporteInventarioDto.getSaldo() > 0) {
                        reporteInventarioDto.setEstado("SOBRA");
                    }
                    reporteInventarioDto.setFecha(DateUtils.toString(item.getCreadoEn()));
                    reporteInventarioDto.setResponsable(item.getUsuario().getNickname());
                    reporteInventarioDtoList.add(reporteInventarioDto);
                }

                file = ResourceUtils.getFile(imageService.getResourcesPath() + File.separator + "reporte-inventario.jrxml");
                JasperReport jasperReport = JasperCompileManager.compileReport(file.getAbsolutePath());
                JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(reporteInventarioDtoList);
                Map<String, Object> parameters = new HashMap<>();
                parameters.put("filtroFechaInicio", startDate);
                parameters.put("filtroFechaFin", endDate);
                parameters.put("codigoBarra", "");
                if(sucursalIdList != null){
                    parameters.put("filtroSucursales", sucursalIdList.toString());
                } else {
                    parameters.put("filtroSucursales", "Todos");
                }
                if(productoIdList != null){
                    parameters.put("filtroProductos", productoIdList.toString());
                } else {
                    parameters.put("filtroProductos", "Todos");
                }
                parameters.put("fechaReporte", DateUtils.toString(LocalDateTime.now()));
                parameters.put("usuario", nickname);
                parameters.put("logo", imageService.getImagePath() + File.separator + "logo.png");
                JasperPrint jasperPrint1 = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
                byte[] pdfBytes = JasperExportManager.exportReportToPdf(jasperPrint1);
                String base64String = Base64.getEncoder().encodeToString(pdfBytes);
                return base64String;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return null;
            } catch (JRException e) {
                e.printStackTrace();
                return null;
            }
        } else {
            return null;
        }
    }
}
