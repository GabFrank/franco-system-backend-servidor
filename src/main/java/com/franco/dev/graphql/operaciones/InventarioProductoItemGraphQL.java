package com.franco.dev.graphql.operaciones;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.InventarioProducto;
import com.franco.dev.domain.operaciones.InventarioProductoItem;
import com.franco.dev.domain.operaciones.dto.ProductoSaldoDto;
import com.franco.dev.domain.operaciones.dto.ReporteInventarioDto;
import com.franco.dev.graphql.operaciones.input.InventarioProductoItemInput;
import com.franco.dev.domain.operaciones.enums.FuenteVerdadVencimiento;
import com.franco.dev.graphql.operaciones.dto.ProductoVencidoViewDTO;
import com.franco.dev.service.operaciones.InventarioProductoItemService;
import com.franco.dev.service.operaciones.InventarioProductoService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.operaciones.ProductosVencidosService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
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
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private ImageService imageService;

    @Autowired
    private MultiTenantService multiTenantService;

    @Autowired
    private MovimientoStockService movimientoStockService;

    @Autowired
    private ProductosVencidosService productosVencidosService;

    @Autowired
    private SucursalService sucursalService;

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final String DEFAULT_SORT_FIELD = "vencimiento";

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

    public List<InventarioProductoItem> inventarioItemsPorInvProYPresentacion(Long invProId, Long presentacionId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findByInventarioProductoIdAndPresentacionId(invProId, presentacionId, pageable);
    }

    public List<InventarioProductoItem> inventarioItemsDeInventariosAnteriores(Long invProId, Long presentacionId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        InventarioProducto invPro = inventarioProductoService.findById(invProId).orElse(null);
        if (invPro == null || invPro.getInventario() == null || invPro.getZona() == null || invPro.getZona().getSector() == null || invPro.getInventario().getSucursal() == null) {
            return Collections.emptyList();
        }
        LocalDateTime fechaInicioActual = invPro.getInventario().getFechaInicio();
        if (fechaInicioActual == null) {
            return Collections.emptyList();
        }
        Long sucursalId = invPro.getInventario().getSucursal().getId();
        Long sectorId = invPro.getZona().getSector().getId();
        Long zonaId = invPro.getZona().getId();
        List<InventarioProductoItem> result = service.findItemsDeInventariosAnteriores(presentacionId, sucursalId, sectorId, zonaId, fechaInicioActual, pageable);
        return result;
    }

    public Page<InventarioProductoItem> getInventarioItemsParaRevisar(Long inventarioId, String filtro, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findItemsParaRevisar(inventarioId, filtro, pageable);
    }

    public InventarioProductoItem saveInventarioProductoItem(InventarioProductoItemInput input) {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STRICT)
                .setAmbiguityIgnored(true);

        InventarioProductoItem entity = modelMapper.map(input, InventarioProductoItem.class);

        if (input.getVencimiento() != null) {
            entity.setVencimiento(stringToDate(input.getVencimiento()));
        }
        if (input.getUsuarioId() != null) {
            entity.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getPresentacionId() != null) {
            entity.setPresentacion(presentacionService.findById(input.getPresentacionId()).orElse(null));
        }
        if (input.getInventarioProductoId() != null) {
            entity.setInventarioProducto(inventarioProductoService.findById(input.getInventarioProductoId()).orElse(null));
        }

        return service.save(entity);
    }

    public Boolean deleteInventarioProductoItem(Long id) {
        return service.findById(id)
                .map(item -> service.deleteById(id))
                .orElse(false);
    }

    public Long countInventarioProductoItem() {
        return service.count();
    }

    public Page<InventarioProductoItem> inventarioProductoItemWithFilter(
            @Nullable String startDate,
            @Nullable String endDate,
            @Nullable List<Long> sucursalIdList,
            @Nullable List<Long> sectorIdList,
            @Nullable List<Long> zonaIdList,
            @Nullable List<Long> usuarioIdList,
            @Nullable List<Long> productoIdList,
            Integer page, Integer size, @Nullable String orderBy, @Nullable String tipoOrder,
            @Nullable String estado) {

        Pageable pageable = createPageable(page, size, orderBy, tipoOrder);
        return service.findAllWithFilters(
                sucursalIdList,
                sectorIdList,
                zonaIdList,
                stringToDate(startDate),
                stringToDate(endDate),
                usuarioIdList,
                productoIdList,
                estado,
                pageable);
    }

    public String reporteInventario(
            @Nullable String startDate,
            @Nullable String endDate,
            @Nullable List<Long> sucursalIdList,
            @Nullable List<Long> sectorIdList,
            @Nullable List<Long> zonaIdList,
            @Nullable List<Long> usuarioIdList,
            @Nullable List<Long> productoIdList,
            Integer page,
            Integer size,
            @Nullable String orderBy,
            @Nullable String tipoOrder,
            String nickname) {

        try {
            Pageable pageable = PageRequest.of(page != null ? page : 0, size != null ? size : DEFAULT_PAGE_SIZE);
            Page<InventarioProductoItem> inventarioProductoItemPage = service.findAllWithFilters(
                    sucursalIdList, sectorIdList, zonaIdList, stringToDate(startDate), stringToDate(endDate),
                    usuarioIdList, productoIdList, null, pageable);

            List<InventarioProductoItem> inventarioProductoItemList = inventarioProductoItemPage.getContent();
            if (inventarioProductoItemList.isEmpty()) {
                return null;
            }

            List<ReporteInventarioDto> reporteInventarioDtoList = new ArrayList<>();
            for (InventarioProductoItem item : inventarioProductoItemList) {
                ReporteInventarioDto dto = new ReporteInventarioDto();
                dto.setProductoId(item.getPresentacion().getProducto().getId());
                dto.setDescripcion(item.getPresentacion().getProducto().getDescripcion());
                dto.setCantidadEncontrada(item.getCantidadFisica());
                dto.setCantidadSistema(item.getCantidad());
                Double cantidad = item.getCantidad() != null ? item.getCantidad() : 0.0;
                Double cantidadFisica = item.getCantidadFisica() != null ? item.getCantidadFisica() : 0.0;
                Double saldo = cantidad - cantidadFisica;
                dto.setSaldo(saldo);

                if (saldo < 0) {
                    dto.setEstado("FALTA");
                } else if (saldo == 0) {
                    dto.setEstado("OK");
                } else {
                    dto.setEstado("SOBRA");
                }

                dto.setFecha(DateUtils.toString(item.getCreadoEn()));
                dto.setResponsable(item.getUsuario().getNickname());
                reporteInventarioDtoList.add(dto);
            }

            // Se lee como stream: dentro del JAR empaquetado el .jrxml no tiene ruta de filesystem
            JasperReport jasperReport;
            try (InputStream jrxmlStream = new ClassPathResource("reports/reporte-inventario.jrxml").getInputStream()) {
                jasperReport = JasperCompileManager.compileReport(jrxmlStream);
            }
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(reporteInventarioDtoList);

            Map<String, Object> parameters = createReportParameters(
                    startDate, endDate, sucursalIdList, sectorIdList, zonaIdList, productoIdList, nickname);

            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
            byte[] pdfBytes = JasperExportManager.exportReportToPdf(jasperPrint);
            return Base64.getEncoder().encodeToString(pdfBytes);

        } catch (IOException | JRException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Map<String, Object> createReportParameters(
            @Nullable String startDate,
            @Nullable String endDate,
            @Nullable List<Long> sucursalIdList,
            @Nullable List<Long> sectorIdList,
            @Nullable List<Long> zonaIdList,
            @Nullable List<Long> productoIdList,
            String nickname) {

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("filtroFechaInicio", startDate != null ? startDate : "Todos");
        parameters.put("filtroFechaFin", endDate != null ? endDate : "Todos");
        parameters.put("codigoBarra", "");
        parameters.put("filtroSucursales", nombresDeSucursales(sucursalIdList));
        parameters.put("filtroSectores", sectorIdList != null ? sectorIdList.toString() : "Todos");
        parameters.put("filtroZonas", zonaIdList != null ? zonaIdList.toString() : "Todos");
        parameters.put("filtroProductos", productoIdList != null ? productoIdList.toString() : "Todos");
        parameters.put("fechaReporte", DateUtils.toString(LocalDateTime.now()));
        parameters.put("usuario", nickname);
        parameters.put("logo", imageService.getImagePath() + File.separator + "logo.png");

        return parameters;
    }

    /**
     * Convierte los ids de sucursal del filtro en sus nombres, para que la cabecera
     * del reporte no muestre "[1, 2]". Si algun id no se encuentra, se deja el id.
     */
    private String nombresDeSucursales(@Nullable List<Long> sucursalIdList) {
        if (sucursalIdList == null || sucursalIdList.isEmpty()) {
            return "Todos";
        }
        return sucursalIdList.stream()
                .map(id -> sucursalService.findById(id)
                        .map(Sucursal::getNombre)
                        .orElse(String.valueOf(id)))
                .collect(Collectors.joining(", "));
    }

    public Page<ProductoSaldoDto> productosConCantidadPositiva(Long sucursalId, Long productoId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return movimientoStockService.findProductosConCantidadPositiva(sucursalId, productoId, pageable);
    }

    public Page<ProductoSaldoDto> productosConCantidadNegativa(Long sucursalId, Long productoId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return movimientoStockService.findProductosConCantidadNegativa(sucursalId, productoId, pageable);
    }

    public Page<ProductoSaldoDto> productosFaltantes(Long sucursalId, Long productoId, String fechaInicio, String fechaFin, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return movimientoStockService.findProductosFaltantes(sucursalId, productoId, stringToDate(fechaInicio), stringToDate(fechaFin), pageable);
    }

    public Page<ProductoVencidoViewDTO> productosVencidos(
            @Nullable String startDate,
            @Nullable String endDate,
            @Nullable List<Long> sucursalIdList,
            @Nullable List<Long> sectorIdList,
            @Nullable List<Long> zonaIdList,
            @Nullable List<Long> usuarioIdList,
            @Nullable List<Long> productoIdList,
            @Nullable List<FuenteVerdadVencimiento> fuenteVerdadList,
            @Nullable Boolean soloRealmenteVencidos,
            Integer page,
            Integer size) {

        Pageable pageable = createPageable(page, size, DEFAULT_SORT_FIELD, "ASC");

        List<String> fuenteVerdadNombres = fuenteVerdadList == null ? null
                : fuenteVerdadList.stream().map(FuenteVerdadVencimiento::name).collect(Collectors.toList());

        return productosVencidosService.buscarProductosVencidos(
                stringToDate(startDate),
                stringToDate(endDate),
                sucursalIdList,
                sectorIdList,
                zonaIdList,
                usuarioIdList,
                productoIdList,
                fuenteVerdadNombres,
                soloRealmenteVencidos,
                pageable);
    }

    public Page<InventarioProductoItem> productosVencidosPorSucursal(
            @Nullable Long sucursalId,
            Integer page,
            Integer size) {
        Pageable pageable = createPageable(page, size, DEFAULT_SORT_FIELD, "ASC");
        return service.findProductosVencidosPorSucursal(sucursalId, pageable);
    }

    public Page<InventarioProductoItem> productosVencidosPorSector(
            @Nullable Long sectorId,
            Integer page,
            Integer size) {
        Pageable pageable = createPageable(page, size, DEFAULT_SORT_FIELD, "ASC");
        return service.findProductosVencidosPorSector(sectorId, pageable);
    }

    public Page<InventarioProductoItem> productosVencidosPorZona(
            @Nullable Long zonaId,
            Integer page,
            Integer size) {
        Pageable pageable = createPageable(page, size, DEFAULT_SORT_FIELD, "ASC");
        return service.findProductosVencidosPorZona(zonaId, pageable);
    }

    public Page<InventarioProductoItem> productosVencidosPorSucursalYSector(
            @Nullable Long sucursalId,
            @Nullable Long sectorId,
            Integer page,
            Integer size) {
        Pageable pageable = createPageable(page, size, DEFAULT_SORT_FIELD, "ASC");
        return service.findProductosVencidosPorSucursalYSector(sucursalId, sectorId, pageable);
    }

    public Page<InventarioProductoItem> productosProximosAVencer(
            @Nullable List<Long> sucursalIdList,
            @Nullable List<Long> sectorIdList,
            @Nullable List<Long> zonaIdList,
            @Nullable List<Long> usuarioIdList,
            @Nullable List<Long> productoIdList,
            @Nullable Integer diasProximos,
            Integer page,
            Integer size) {

        Pageable pageable = createPageable(page, size, DEFAULT_SORT_FIELD, "ASC");
        LocalDateTime fechaProximoVencimiento = LocalDateTime.now().plusDays(diasProximos != null ? diasProximos : 30);

        return service.findProductosProximosAVencer(
                sucursalIdList, sectorIdList, zonaIdList, usuarioIdList, productoIdList, fechaProximoVencimiento, pageable);
    }

    public Integer countProductosVencidos(
            @Nullable List<Long> sucursalIdList,
            @Nullable List<Long> sectorIdList,
            @Nullable List<Long> zonaIdList,
            @Nullable List<Long> usuarioIdList,
            @Nullable List<Long> productoIdList) {

        Long count = service.countProductosVencidos(
                sucursalIdList, sectorIdList, zonaIdList, usuarioIdList, productoIdList);
        return count != null ? count.intValue() : 0;
    }

    public Page<InventarioProductoItem> productosVencidosCompleto(
            @Nullable List<Long> sucursalIdList,
            @Nullable List<Long> sectorIdList,
            @Nullable List<Long> zonaIdList,
            @Nullable String startDate,
            @Nullable String endDate,
            @Nullable List<Long> usuarioIdList,
            @Nullable List<Long> productoIdList,
            @Nullable Boolean incluirProximosVencer,
            @Nullable Integer diasProximosVencer,
            @Nullable Boolean soloRealmenteVencidos,
            Integer page,
            Integer size) {

        Pageable pageable = createPageable(page, size, DEFAULT_SORT_FIELD, "ASC");

        return service.findProductosVencidosCompleto(
                sucursalIdList,
                sectorIdList,
                zonaIdList,
                stringToDate(startDate),
                stringToDate(endDate),
                usuarioIdList,
                productoIdList,
                incluirProximosVencer,
                diasProximosVencer,
                soloRealmenteVencidos,
                pageable);
    }

    private Pageable createPageable(Integer page, Integer size, @Nullable String orderBy, @Nullable String tipoOrder) {
        int pageNumber = page != null ? page : 0;
        int pageSize = size != null ? size : DEFAULT_PAGE_SIZE;

        if (orderBy != null && tipoOrder != null) {
            Sort.Direction direction = "DESC".equalsIgnoreCase(tipoOrder) ? Sort.Direction.DESC : Sort.Direction.ASC;
            return PageRequest.of(pageNumber, pageSize, Sort.by(direction, orderBy));
        }

        return PageRequest.of(pageNumber, pageSize);
    }
}