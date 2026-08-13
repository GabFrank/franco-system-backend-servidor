package com.franco.dev.graphql.operaciones;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.operaciones.InventarioProducto;
import com.franco.dev.domain.operaciones.InventarioProductoItem;
import com.franco.dev.domain.operaciones.dto.ProductoSaldoDto;
import com.franco.dev.domain.operaciones.dto.ReporteInventarioDto;
import com.franco.dev.graphql.operaciones.input.InventarioProductoItemInput;
import com.franco.dev.domain.operaciones.enums.FuenteVerdadVencimiento;
import com.franco.dev.domain.operaciones.enums.InventarioProductoEstado;
import com.franco.dev.graphql.operaciones.dto.ProductoVencidoViewDTO;
import com.franco.dev.service.operaciones.InventarioProductoItemService;
import com.franco.dev.service.operaciones.InventarioProductoService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.operaciones.ProductosVencidosService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.utilitarios.DateUtils;
import com.franco.dev.utilitarios.IdUtils;
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
import java.util.function.Function;
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

    @Autowired
    private ProductoService productoService;

    @Autowired
    private ImpresionService impresionService;

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final String DEFAULT_SORT_FIELD = "vencimiento";

    /** Tope de filas del PDF de productos vencidos, para no traer todo a memoria. */
    private static final int LIMITE_REPORTE_VENCIDOS = 5000;

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
            @Nullable String estado,
            @Nullable String vencimientoFiltro,
            @Nullable Integer diasPorVencer,
            @Nullable String vencimientoDesde,
            @Nullable String vencimientoHasta) {

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
                vencimientoFiltro,
                diasPorVencer,
                stringToDate(vencimientoDesde),
                DateUtils.stringToDateEndOfDay(vencimientoHasta),
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
            String nickname,
            @Nullable String estado,
            @Nullable String vencimientoFiltro,
            @Nullable Integer diasPorVencer,
            @Nullable String vencimientoDesde,
            @Nullable String vencimientoHasta) {

        try {
            // El reporte lista todo lo que matchea los filtros: la paginacion de la
            // pantalla (page/size) no debe recortarlo. Se reciben por compatibilidad
            // con el schema, pero se ignoran a proposito.
            Pageable pageable = Pageable.unpaged();
            Page<InventarioProductoItem> inventarioProductoItemPage = service.findAllWithFilters(
                    sucursalIdList, sectorIdList, zonaIdList, stringToDate(startDate), stringToDate(endDate),
                    usuarioIdList, productoIdList, estado,
                    vencimientoFiltro, diasPorVencer,
                    stringToDate(vencimientoDesde), DateUtils.stringToDateEndOfDay(vencimientoHasta),
                    pageable);

            List<InventarioProductoItem> inventarioProductoItemList = inventarioProductoItemPage.getContent();
            if (inventarioProductoItemList.isEmpty()) {
                return null;
            }

            LocalDateTime ahora = LocalDateTime.now();
            List<ReporteInventarioDto> reporteInventarioDtoList = new ArrayList<>();
            for (InventarioProductoItem item : inventarioProductoItemList) {
                ReporteInventarioDto dto = new ReporteInventarioDto();
                dto.setProductoId(item.getPresentacion().getProducto().getId());
                dto.setDescripcion(item.getPresentacion().getProducto().getDescripcion());
                // cantidad = lo contado, cantidadFisica = lo que tenia el sistema.
                dto.setCantidadSistema(item.getCantidadFisica());
                dto.setCantidadEncontrada(item.getCantidad());
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
                dto.setSucursal(nombreDeSucursalDelItem(item));
                dto.setVencimiento(
                        item.getVencimiento() != null ? DateUtils.toStringOnlyDate(item.getVencimiento()) : null);
                dto.setVencido(esVencido(item, ahora));
                reporteInventarioDtoList.add(dto);
            }

            // Jasper agrupa por corte de valor: la lista tiene que venir ordenada por
            // sucursal. Dentro de cada sucursal, alfabetico por producto.
            reporteInventarioDtoList.sort(
                    Comparator.comparing(ReporteInventarioDto::getSucursal,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(ReporteInventarioDto::getDescripcion,
                                    Comparator.nullsLast(Comparator.naturalOrder())));

            // Se lee como stream: dentro del JAR empaquetado el .jrxml no tiene ruta de filesystem
            JasperReport jasperReport;
            try (InputStream jrxmlStream = new ClassPathResource("reports/reporte-inventario.jrxml").getInputStream()) {
                jasperReport = JasperCompileManager.compileReport(jrxmlStream);
            }
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(reporteInventarioDtoList);

            Map<String, Object> parameters = createReportParameters(
                    startDate, endDate, sucursalIdList, sectorIdList, zonaIdList, productoIdList, nickname,
                    estado, descripcionFiltroVencimiento(vencimientoFiltro, diasPorVencer, vencimientoDesde,
                            vencimientoHasta));

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
            String nickname,
            @Nullable String estado,
            String filtroVencimiento) {

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("filtroFechaInicio", startDate != null ? startDate : "Todos");
        parameters.put("filtroFechaFin", endDate != null ? endDate : "Todos");
        parameters.put("codigoBarra", "");
        parameters.put("filtroSucursales", nombresDeSucursales(sucursalIdList));
        parameters.put("filtroSectores", sectorIdList != null ? sectorIdList.toString() : "Todos");
        parameters.put("filtroZonas", zonaIdList != null ? zonaIdList.toString() : "Todos");
        parameters.put("filtroProductos", nombresDeProductos(productoIdList));
        parameters.put("filtroEstado", estado != null && !estado.trim().isEmpty() ? estado : "Todos");
        parameters.put("filtroVencimiento", filtroVencimiento);
        parameters.put("fechaReporte", DateUtils.toString(LocalDateTime.now()));
        parameters.put("usuario", nickname);
        parameters.put("logo", imageService.getImagePath() + File.separator + "logo.png");

        return parameters;
    }

    /**
     * Nombre de la sucursal del inventario al que pertenece el item; es el corte de
     * grupo del PDF, asi que nunca puede quedar null.
     */
    private String nombreDeSucursalDelItem(InventarioProductoItem item) {
        if (item.getInventarioProducto() == null
                || item.getInventarioProducto().getInventario() == null
                || item.getInventarioProducto().getInventario().getSucursal() == null
                || item.getInventarioProducto().getInventario().getSucursal().getNombre() == null) {
            return "Sin sucursal";
        }
        return item.getInventarioProducto().getInventario().getSucursal().getNombre();
    }

    /**
     * Mismo criterio que el filtro VENCIDOS de la lista: vencido contra hoy, o
     * marcado como VENCIDO al momento de contarlo.
     */
    private boolean esVencido(InventarioProductoItem item, LocalDateTime ahora) {
        if (InventarioProductoEstado.VENCIDO.equals(item.getEstado())) {
            return true;
        }
        return item.getVencimiento() != null && item.getVencimiento().isBefore(ahora);
    }

    /**
     * Texto del filtro de vencimiento para la cabecera del PDF, para que quede
     * asentado con que criterio se genero.
     */
    private String descripcionFiltroVencimiento(
            @Nullable String vencimientoFiltro,
            @Nullable Integer diasPorVencer,
            @Nullable String vencimientoDesde,
            @Nullable String vencimientoHasta) {

        String filtro = vencimientoFiltro != null ? vencimientoFiltro.trim().toUpperCase() : "";
        String texto;
        switch (filtro) {
            case "VENCIDOS":
                texto = "Vencidos";
                break;
            case "POR_VENCER":
                texto = "Por vencer (" + (diasPorVencer != null && diasPorVencer > 0 ? diasPorVencer : 30) + " dias)";
                break;
            case "VIGENTES":
                texto = "Vigentes";
                break;
            case "SIN_VENCIMIENTO":
                texto = "Sin vencimiento";
                break;
            default:
                texto = "Todos";
                break;
        }

        if (vencimientoDesde != null && !vencimientoDesde.trim().isEmpty()) {
            texto += " | desde " + vencimientoDesde;
        }
        if (vencimientoHasta != null && !vencimientoHasta.trim().isEmpty()) {
            texto += " | hasta " + vencimientoHasta;
        }
        return texto;
    }

    /**
     * Convierte los ids de sucursal del filtro en sus nombres, para que la cabecera
     * del reporte no muestre "[1, 2]". Si algun id no se encuentra, se deja el id.
     */
    private String nombresDeSucursales(@Nullable List<?> sucursalIdList) {
        return nombresDeFiltro(sucursalIdList,
                id -> sucursalService.findById(id).map(Sucursal::getNombre).orElse(null));
    }

    /**
     * Idem para el filtro de productos, usando la descripcion del producto.
     */
    private String nombresDeProductos(@Nullable List<?> productoIdList) {
        return nombresDeFiltro(productoIdList,
                id -> productoService.findById(id).map(Producto::getDescripcion).orElse(null));
    }

    /**
     * Resuelve los ids de un filtro a nombres legibles. Los ids llegan de GraphQL
     * como Integer o String, asi que se normalizan con IdUtils antes de buscarlos.
     * Si un id no resuelve a nombre, se muestra el id para no perder informacion.
     */
    private String nombresDeFiltro(@Nullable List<?> idList, Function<Long, String> resolverNombre) {
        List<Long> ids = IdUtils.toLongList(idList);
        if (ids == null) {
            return "Todos";
        }
        return ids.stream()
                .map(id -> {
                    String nombre = resolverNombre.apply(id);
                    return nombre != null && !nombre.trim().isEmpty() ? nombre : String.valueOf(id);
                })
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

    /**
     * PDF del listado de productos vencidos, con los mismos filtros que la
     * pantalla pero sin paginar: el reporte trae todo lo que matchea, no la
     * pagina visible. Devuelve el PDF en base64, o null si el filtro no dio
     * resultados (el front avisa al usuario en ese caso).
     */
    public String reporteProductosVencidos(
            @Nullable String startDate,
            @Nullable String endDate,
            @Nullable List<Long> sucursalIdList,
            @Nullable List<Long> sectorIdList,
            @Nullable List<Long> zonaIdList,
            @Nullable List<Long> usuarioIdList,
            @Nullable List<Long> productoIdList,
            @Nullable List<FuenteVerdadVencimiento> fuenteVerdadList,
            @Nullable Boolean soloRealmenteVencidos,
            @Nullable Long usuarioResponsableId) {

        List<String> fuenteVerdadNombres = fuenteVerdadList == null ? null
                : fuenteVerdadList.stream().map(FuenteVerdadVencimiento::name).collect(Collectors.toList());

        // Se pide una fila mas que el tope para saber si quedo truncado, sin pagar un
        // COUNT aparte sobre una consulta que ya es cara.
        List<ProductoVencidoViewDTO> vencidoList = productosVencidosService.listarProductosVencidosParaReporte(
                stringToDate(startDate),
                stringToDate(endDate),
                sucursalIdList,
                sectorIdList,
                zonaIdList,
                usuarioIdList,
                productoIdList,
                fuenteVerdadNombres,
                soloRealmenteVencidos,
                LIMITE_REPORTE_VENCIDOS + 1);

        if (vencidoList.isEmpty()) {
            return null;
        }

        String aviso = "";
        if (vencidoList.size() > LIMITE_REPORTE_VENCIDOS) {
            vencidoList = new ArrayList<>(vencidoList.subList(0, LIMITE_REPORTE_VENCIDOS));
            aviso = "Resultado truncado: se muestran los primeros " + LIMITE_REPORTE_VENCIDOS + " registros";
        }

        com.franco.dev.domain.personas.Usuario responsable = usuarioResponsableId != null
                ? usuarioService.findById(usuarioResponsableId).orElse(null)
                : null;

        return impresionService.imprimirProductosVencidos(
                vencidoList,
                fechaLegible(startDate),
                fechaLegible(endDate),
                fuenteVerdadNombres != null && !fuenteVerdadNombres.isEmpty()
                        ? String.join(", ", fuenteVerdadNombres)
                        : "TODAS",
                nombresDeSucursales(sucursalIdList),
                nombresDeProductos(productoIdList),
                nombresDeUsuarios(usuarioIdList),
                aviso,
                responsable);
    }

    /**
     * Las fechas llegan del front como "yyyy-MM-dd HH:mm"; para la cabecera del
     * reporte se muestran en el formato legible del sistema.
     */
    private String fechaLegible(@Nullable String fecha) {
        LocalDateTime parsed = stringToDate(fecha);
        return parsed != null ? DateUtils.toString(parsed) : "-";
    }

    /**
     * Idem {@link #nombresDeSucursales}, usando el nickname del usuario.
     */
    private String nombresDeUsuarios(@Nullable List<?> usuarioIdList) {
        return nombresDeFiltro(usuarioIdList,
                id -> usuarioService.findById(id).map(com.franco.dev.domain.personas.Usuario::getNickname)
                        .orElse(null));
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