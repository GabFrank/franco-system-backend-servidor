package com.franco.dev.service.productos;

import com.franco.dev.domain.dto.ProductoIdAndCantidadDto;
import com.franco.dev.domain.dto.ProductoReportDto;
import com.franco.dev.domain.operaciones.dto.LucroPorProductosDto;
import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.domain.productos.Familia;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.Subfamilia;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.graphql.productos.input.ProductoInput;
import com.franco.dev.repository.productos.ProductoRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.productos.search.ProductoSearchService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.GraphQLException;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Service
@RequiredArgsConstructor
public class ProductoService extends CrudService<Producto, ProductoRepository, Long> {

    private static final Logger log = Logger.getLogger(String.valueOf(ProductoService.class));
    @Autowired
    private final ProductoRepository repository;
    @Autowired
    private final UsuarioService usuarioService;
    @Autowired
    private final SubFamiliaService subFamiliaService;
    @Autowired
    private final FamiliaService familiaService;
    @Autowired
    private final MovimientoStockService movimientoStockService;
    @Autowired
    private final PresentacionService presentacionService;
    @Autowired
    private final PrecioPorSucursalService precioPorSucursalService;
    @Autowired
    private final ImageService imageService;
    @Autowired
    private final CodigoService codigoService;
    @Autowired
    private final CostosPorProductoService costosPorProductoService;
    @Autowired
    private final SucursalService sucursalService;
    @Autowired
    private final com.franco.dev.service.configuraciones.ModificacionService modificacionService;
    @Autowired
    private final ProductoSearchService productoSearchService;

    @Value("${app.search.producto.enabled:true}")
    private boolean productoSearchEnabled;

    @Override
    public ProductoRepository getRepository() {
        return repository;
    }

    public List<Producto> findByAll(String texto, Integer offset, Long sucursalId, Boolean conStock, Boolean isEnvase,
            Boolean activo) {
        if (offset == null) {
            offset = 0;
        }

        // Convertimos el Long a String para evitar el error 'bytea'
        String sucursalIdStr = (sucursalId != null) ? String.valueOf(sucursalId) : "0";
        String conStockStr = (conStock != null) ? String.valueOf(conStock) : "false";

        if (texto == null || texto.trim().isEmpty()) {
            if (isEnvase != null && isEnvase == true) {
                return repository.findEnvases("", offset, true);
            } else {
                return repository.findbyAll("%", offset, sucursalIdStr, conStockStr);
            }
        }

        if (isEnvase != null && isEnvase == true) {
            String textoEnvase = texto.replace(' ', '%').toUpperCase();
            return repository.findEnvases(textoEnvase, offset, true);
        }

        if (productoSearchEnabled && productoSearchService.textoBusquedaValido(texto)) {
            Boolean activoFiltro = activo != null ? activo : true;
            return buscarPorTextoLucene(texto, offset, sucursalIdStr, conStockStr, activoFiltro);
        }

        String textoSql = texto.replace(' ', '%').toUpperCase();
        return repository.findbyAll(textoSql, offset, sucursalIdStr, conStockStr);
    }

    private List<Producto> buscarPorTextoLucene(
            String texto,
            int offset,
            String sucursalIdStr,
            String conStockStr,
            Boolean activo) {
        int fetchSize = Math.min(Math.max(offset + 10, 50), 200);
        List<Long> ids = productoSearchService.buscarIdsPorTexto(texto, fetchSize, activo, false, null, null);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }

        boolean conStock = "true".equalsIgnoreCase(conStockStr);
        Long sucursalId = null;
        if (sucursalIdStr != null && !"0".equals(sucursalIdStr)) {
            try {
                sucursalId = Long.parseLong(sucursalIdStr);
            } catch (NumberFormatException ignored) {
                sucursalId = null;
            }
        }

        List<Producto> encontrados;
        if (conStock && sucursalId != null) {
            encontrados = repository.searchWithFiltersByIds(
                    ids, activo, null, null, null, null, null, null, null, sucursalId);
        } else {
            encontrados = new ArrayList<>(repository.findAllById(ids));
        }

        Map<Long, Producto> porId = encontrados.stream()
                .collect(Collectors.toMap(Producto::getId, p -> p, (a, b) -> a, LinkedHashMap::new));

        List<Producto> ordenados = ids.stream()
                .map(porId::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        int from = Math.min(offset, ordenados.size());
        int to = Math.min(from + 10, ordenados.size());
        if (from >= to) {
            return Collections.emptyList();
        }
        return ordenados.subList(from, to);
    }

    public boolean existsByDescripcion(String descripcion) {
        if (descripcion == null) {
            return false;
        }
        Producto existing = repository.findByDescripcion(descripcion.toUpperCase());
        return existing != null;
    }

    /**
     * Match de producto por descripcion normalizada (UPPER+TRIM), aceptando
     * tanto p.descripcion como p.descripcionFactura. Util para resolver iva de
     * items de factura legal que llegan sin productoId ni iva (huerfanos).
     * Retorna lista porque pueden haber duplicados en catalogo con distinto iva,
     * en cuyo caso el caller debe decidir como manejar la ambiguedad.
     */
    public List<Producto> findByDescripcionNormalized(String descripcion) {
        if (descripcion == null || descripcion.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return repository.findByDescripcionNormalized(descripcion);
    }

    public Page<Producto> findWithFilters(String texto, Boolean activo, Boolean stock, Boolean balanza,
            Long subfamiliaId, Long familiaId, Boolean vencimiento, Boolean costoCero, String stockFiltro, Long sucursalId,
            Pageable page) {
        if (productoSearchEnabled && productoSearchService.textoBusquedaValido(texto)) {
            return buscarConFiltrosLucene(texto, activo, stock, balanza, subfamiliaId, familiaId, vencimiento,
                    costoCero, stockFiltro, sucursalId, page);
        }
        String textoSql = texto != null ? texto.replace(" ", "%").toUpperCase() : "";
        return repository.searchWithFilters(textoSql, activo, stock, balanza, subfamiliaId, familiaId, vencimiento,
                costoCero, stockFiltro, sucursalId, page);
    }

    private Page<Producto> buscarConFiltrosLucene(String texto, Boolean activo, Boolean stock, Boolean balanza,
            Long subfamiliaId, Long familiaId, Boolean vencimiento, Boolean costoCero, String stockFiltro,
            Long sucursalId, Pageable page) {
        final int overFetch;
        if (page.isUnpaged()) {
            // En exportación se usa Pageable.unpaged(); evitar getPageNumber/getPageSize
            // porque Unpaged lanza UnsupportedOperationException.
            overFetch = 50000;
        } else {
            overFetch = Math.max(Math.min(2000, (page.getPageNumber() + 1) * page.getPageSize() * 8), 100);
        }

        List<Long> ids = productoSearchService.buscarIdsPorTexto(
                texto, overFetch, activo, null, familiaId, subfamiliaId);
        if (ids.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), page, 0);
        }

        List<Producto> filtrados = repository.searchWithFiltersByIds(
                ids, activo, stock, balanza, subfamiliaId, familiaId, vencimiento, costoCero, stockFiltro, sucursalId);

        Map<Long, Producto> porId = filtrados.stream()
                .collect(Collectors.toMap(Producto::getId, p -> p, (a, b) -> a, LinkedHashMap::new));

        List<Producto> ordenados = ids.stream()
                .map(porId::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        int total = ordenados.size();
        if (page.isUnpaged()) {
            return new PageImpl<>(ordenados, Pageable.unpaged(), total);
        }

        int from = (int) page.getOffset();
        int to = Math.min(from + page.getPageSize(), total);
        List<Producto> content = from >= total ? Collections.emptyList() : ordenados.subList(from, to);
        return new PageImpl<>(content, page, total);
    }

    public Producto save(ProductoInput entity) throws GraphQLException {
        Producto p = null;
        ModelMapper m = new ModelMapper();
        m.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
        Producto e = m.map(entity, Producto.class);
        if (entity.getUsuarioId() != null)
            e.setUsuario(usuarioService.findById(entity.getUsuarioId()).orElse(null));
        if (entity.getSubfamiliaId() != null)
            e.setSubfamilia(subFamiliaService.findById(entity.getSubfamiliaId()).orElse(null));
        if (e.getDescripcionFactura() != null)
            e.setDescripcionFactura(e.getDescripcionFactura().toUpperCase());
        if (e.getImagenes() == null)
            e.setImagenes("/productos");
        if (entity.getEnvaseId() != null)
            e.setEnvase(findById(entity.getEnvaseId()).orElse(null));
        e.setDescripcion(e.getDescripcion().toUpperCase());

        // Obtener entidad anterior para comparar cambios (si es actualización)
        // IMPORTANTE: Obtener ANTES de guardar para tener los valores anteriores
        Producto entidadAnterior = null;
        boolean esNuevo = (e.getId() == null);
        if (!esNuevo) {
            Optional<Producto> productoOpt = repository.findById(e.getId());
            if (productoOpt != null && productoOpt.isPresent()) {
                entidadAnterior = productoOpt.get();
            }
        }

        p = repository.save(e);
        repository.flush(); // Asegurar que se guarde antes de registrar la modificación

        // Registrar modificación sin afectar la lógica existente
        try {
            if (esNuevo) {
                // Es una inserción
                modificacionService.registrarInsercion(p, "PRODUCTO", "productos", "producto");
            } else if (entidadAnterior != null) {
                // Es una actualización
                modificacionService.registrarActualizacion(entidadAnterior, p, "PRODUCTO", "productos", "producto");
            }
        } catch (Exception ex) {
        }

        return p;
    }

    @Override
    @javax.transaction.Transactional
    public Boolean deleteById(Long id) {
        try {
            // Obtener entidad antes de eliminar para registrar la modificación
            Producto entidad = repository.findById(id).orElse(null);
            if (entidad != null) {
                Boolean resultado = super.deleteById(id);
                // Registrar eliminación sin afectar la lógica existente
                try {
                    modificacionService.registrarEliminacion(entidad, "PRODUCTO", "productos", "producto");
                } catch (Exception ex) {
                }
                return resultado;
            }
            return super.deleteById(id);
        } catch (Exception e) {
            return false;
        }
    }

    public List<Producto> findByProveedorId(Long id, String text) {
        return repository.findByProveedorId(id, text);
    }

    public List<Producto> findBySubFamiliaId(Long id) {
        return repository.findBySubfamiliaId(id);
    }

    public Producto findByCodigo(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        for (String codigo : com.franco.dev.utilitarios.BarcodeSearchUtils.codigosParaBuscar(texto)) {
            Producto producto = repository.findByCodigo(codigo);
            if (producto != null) {
                return producto;
            }
        }
        return null;
    }

    public List<Producto> findAllForPdv() {
        return repository.findAllForPdv();
    }

    public String exportarReporte(String texto) throws FileNotFoundException {
        // Usar método específico para reportes sin límite
        List<Producto> productoList;
        String filtroTexto = texto;
        if (texto == null || texto.trim().isEmpty()) {
            productoList = repository.findForReport("%");
            filtroTexto = "Todos los productos";
        } else {
            texto = texto.replace(' ', '%').toUpperCase();
            productoList = repository.findForReport(texto);
            filtroTexto = "Productos que contienen: " + filtroTexto;
        }

        List<ProductoReportDto> productosDtoList = new ArrayList<>();

        for (Producto p : productoList) {
            // Obtener stock real
            Double stock = movimientoStockService.stockByProductoId(p.getId());

            // Obtener precios y código principal
            Presentacion presentacion = presentacionService.findByPrincipalAndProductoId(true, p.getId());
            Double precioVenta = 0.0;
            Double precioCosto = 0.0;
            String codigoPrincipal = "";

            if (presentacion != null) {
                // Obtener precio de venta
                PrecioPorSucursal precioVentaObj = precioPorSucursalService
                        .findPrincipalByPrecionacionId(presentacion.getId());
                if (precioVentaObj != null) {
                    precioVenta = precioVentaObj.getPrecio();
                }

                // Obtener código principal
                Codigo codigoObj = codigoService.findPrincipalByPresentacionId(presentacion.getId());
                if (codigoObj != null) {
                    codigoPrincipal = codigoObj.getCodigo();
                }
            }

            // Obtener precio costo real desde CostoPorProducto
            CostoPorProducto costoPorProducto = costosPorProductoService.findLastByProductoId(p.getId());
            if (costoPorProducto != null) {
                // Priorizar costo medio, si no hay, usar último precio compra
                if (costoPorProducto.getCostoMedio() != null) {
                    precioCosto = costoPorProducto.getCostoMedio();
                } else if (costoPorProducto.getUltimoPrecioCompra() != null) {
                    precioCosto = costoPorProducto.getUltimoPrecioCompra();
                }
            }

            ProductoReportDto dto = new ProductoReportDto();
            dto.setId(p.getId());
            dto.setDescripcion(p.getDescripcion());
            dto.setCantidad(stock != null ? stock : 0.0);
            dto.setPrecioCosto(precioCosto);
            dto.setPrecioVenta(precioVenta);
            dto.setCodigoPrincipal(codigoPrincipal);
            productosDtoList.add(dto);
        }

        try {
            ClassPathResource resource = new ClassPathResource("reports/productos.jrxml");
            InputStream inputStream = resource.getInputStream();
            JasperReport jasperReport = JasperCompileManager.compileReport(inputStream);
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(productosDtoList);

            // Preparar parámetros del reporte
            Map<String, Object> parameters = new HashMap<>();

            // Logo de la empresa (ruta al archivo de logo)
            String logoPath = imageService.getImagePath() + "logo.png";
            File logoFile = new File(logoPath);
            if (logoFile.exists()) {
                parameters.put("logo", logoPath);
            } else {
                // Logo por defecto o vacío
                parameters.put("logo", "");
            }

            // Fecha actual formateada
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                    .ofPattern("dd/MM/yyyy HH:mm:ss");
            parameters.put("fechaReporte", now.format(formatter));

            // Usuario actual (por ahora genérico, después se puede obtener del contexto de
            // seguridad)
            parameters.put("usuario", "Usuario del Sistema");

            // Filtro aplicado
            parameters.put("filtroAplicado", filtroTexto);

            // Total de productos encontrados
            parameters.put("totalProductos", productosDtoList.size());

            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
            byte[] pdfBytes = JasperExportManager.exportReportToPdf(jasperPrint);
            String b64 = Base64.getEncoder().encodeToString(pdfBytes);
            return b64;

        } catch (Exception e) {
            log.severe("Error al generar el reporte: " + e.getMessage());
            throw new RuntimeException("Error al generar el reporte de productos: " + e.getMessage(), e);
        }
    }

    public String exportarReporteConFiltros(
            String texto,
            Boolean codigo,
            Boolean activo,
            Boolean stock,
            Boolean balanza,
            Boolean vencimiento,
            Boolean costoCero,
            Long subfamiliaId,
            Long familiaId,
            String stockFiltro,
            Long sucursalId,
            Long usuarioId,
            String usuario) throws FileNotFoundException {

        Page<Producto> productoPage = findWithFilters(
                texto,
                activo,
                stock,
                balanza,
                subfamiliaId,
                familiaId,
                vencimiento,
                costoCero,
                stockFiltro,
                sucursalId,
                Pageable.unpaged());

        List<Producto> productoList = productoPage.getContent();

        // Construir filtros: Búsqueda arriba + 4 columnas abajo

        // Filtro de búsqueda (línea superior completa)
        StringBuilder filtroBusqueda = new StringBuilder();
        if (texto != null && !texto.trim().isEmpty()) {
            String textoBusqueda = texto.length() > 60 ? texto.substring(0, 57) + "..." : texto;
            filtroBusqueda.append("BÚSQUEDA: '").append(textoBusqueda.toUpperCase()).append("'");
        } else {
            filtroBusqueda.append("BÚSQUEDA: TODOS");
        }

        // Nuevos parámetros individuales
        String filtroEstado = "ESTADO: " + (activo != null ? (activo ? "ACTIVOS" : "INACTIVOS") : "TODOS");
        String filtroPesable = "PESABLE: " + (balanza != null ? (balanza ? "SÍ" : "NO") : "TODOS");
        String filtroMovStock = "MOV. STOCK: " + (stock != null ? (stock ? "SÍ" : "NO") : "TODOS");
        String filtroCostoCero = "COSTO CERO: " + (costoCero != null ? (costoCero ? "SÍ" : "NO") : "TODOS");
        String filtroVencimiento = "VENCIMIENTO: " + (vencimiento != null ? (vencimiento ? "SÍ" : "NO") : "TODOS");

        String filtroSubfamiliaStr = "SUBFAMILIA: TODOS";
        if (subfamiliaId != null) {
            try {
                Subfamilia subfamilia = subFamiliaService.findById(subfamiliaId).orElse(null);
                if (subfamilia != null) {
                    String nombreCorto = subfamilia.getNombre().length() > 35
                            ? subfamilia.getNombre().substring(0, 32) + "..."
                            : subfamilia.getNombre();
                    filtroSubfamiliaStr = "SUBFAMILIA: " + nombreCorto.toUpperCase();
                } else {
                    filtroSubfamiliaStr = "SUBFAMILIA ID: " + subfamiliaId;
                }
            } catch (Exception e) {
                filtroSubfamiliaStr = "SUBFAMILIA ID: " + subfamiliaId;
            }
        }

        String filtroFamiliaStr = "FAMILIA: TODOS";
        if (familiaId != null) {
            try {
                Familia familia = familiaService.findById(familiaId).orElse(null);
                if (familia != null) {
                    String nombreCorto = familia.getNombre().length() > 35
                            ? familia.getNombre().substring(0, 32) + "..."
                            : familia.getNombre();
                    filtroFamiliaStr = "FAMILIA: " + nombreCorto.toUpperCase();
                } else {
                    filtroFamiliaStr = "FAMILIA ID: " + familiaId;
                }
            } catch (Exception e) {
                filtroFamiliaStr = "FAMILIA ID: " + familiaId;
            }
        }

        // Filtro de Stock (columna 4 del template - separado)
        StringBuilder filtroStock = new StringBuilder();
        if (stockFiltro != null && !stockFiltro.equals("todos")) {
            filtroStock.append("STOCK: ").append(stockFiltro.toUpperCase());
            if (sucursalId != null) {
                try {
                    Sucursal sucursal = sucursalService.findById(sucursalId).orElse(null);
                    if (sucursal != null) {
                        // Mostrar nombre completo de sucursal, truncar solo si es muy largo
                        String nombreSucursal = sucursal.getNombre().length() > 35
                                ? sucursal.getNombre().substring(0, 32) + "..."
                                : sucursal.getNombre();
                        filtroStock.append(" (").append(nombreSucursal.toUpperCase()).append(")");
                    } else {
                        filtroStock.append(" (ID: ").append(sucursalId).append(")");
                    }
                } catch (Exception e) {
                    filtroStock.append(" (ID: ").append(sucursalId).append(")");
                }
            }
        } else {
            filtroStock.append("STOCK: TODOS");
        }

        // Crear la lista de DTOs para el reporte
        List<ProductoReportDto> productosDtoList = new ArrayList<>();

        for (Producto p : productoList) {
            // Obtener stock real - usar stock por sucursal si se filtró por sucursal
            // específica
            Double stockCantidad;
            if (sucursalId != null && (stockFiltro != null && !stockFiltro.equals("todos"))) {
                // Si hay filtro de sucursal y stock específico, usar stock de esa sucursal
                stockCantidad = movimientoStockService.stockByProductoIdAndSucursalId(p.getId(), sucursalId);
            } else {
                // Usar stock total (todas las sucursales)
                stockCantidad = movimientoStockService.stockByProductoId(p.getId());
            }

            // Obtener precios y código principal
            Presentacion presentacion = presentacionService.findByPrincipalAndProductoId(true, p.getId());
            Double precioVenta = 0.0;
            Double precioCosto = 0.0;
            String codigoPrincipal = "";

            if (presentacion != null) {
                // Obtener precio de venta
                PrecioPorSucursal precioVentaObj = precioPorSucursalService
                        .findPrincipalByPrecionacionId(presentacion.getId());
                if (precioVentaObj != null) {
                    precioVenta = precioVentaObj.getPrecio();
                }

                // Obtener código principal
                Codigo codigoData = codigoService.findPrincipalByPresentacionId(presentacion.getId());
                if (codigoData != null) {
                    codigoPrincipal = codigoData.getCodigo();
                }
            }

            // Obtener precio costo real desde CostoPorProducto
            CostoPorProducto costoPorProducto = costosPorProductoService.findLastByProductoId(p.getId());
            if (costoPorProducto != null) {
                precioCosto = costoPorProducto.getCostoMedio();
            }

            ProductoReportDto dto = new ProductoReportDto();
            dto.setId(p.getId());
            dto.setDescripcion(p.getDescripcion());
            dto.setCantidad(stockCantidad != null ? stockCantidad : 0.0);
            dto.setPrecioCosto(precioCosto != null ? precioCosto : 0.0);
            dto.setPrecioVenta(precioVenta != null ? precioVenta : 0.0);
            dto.setCodigoPrincipal(codigoPrincipal);

            productosDtoList.add(dto);
        }

        try {
            ClassPathResource resource = new ClassPathResource("reports/productos.jrxml");
            InputStream inputStream = resource.getInputStream();
            JasperReport jasperReport = JasperCompileManager.compileReport(inputStream);
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(productosDtoList);

            // Preparar parámetros del reporte
            Map<String, Object> parameters = new HashMap<>();

            // Logo de la empresa (ruta al archivo de logo)
            String logoPath = imageService.getImagePath() + "logo.png";
            File logoFile = new File(logoPath);
            if (logoFile.exists()) {
                parameters.put("logo", logoPath);
            } else {
                // Logo por defecto o vacío
                parameters.put("logo", "");
            }

            // Fecha actual formateada
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            parameters.put("fechaReporte", now.format(formatter));

            // Usuario actual - usar el parámetro recibido
            parameters.put("usuario", usuario != null ? usuario : "Usuario del Sistema");

            // Filtro de búsqueda y filtros individuales
            parameters.put("filtroBusqueda", filtroBusqueda.toString());
            parameters.put("filtroEstado", filtroEstado);
            parameters.put("filtroPesable", filtroPesable);
            parameters.put("filtroMovStock", filtroMovStock);
            parameters.put("filtroCostoCero", filtroCostoCero);
            parameters.put("filtroVencimiento", filtroVencimiento);
            parameters.put("filtroSubfamilia", filtroSubfamiliaStr);
            parameters.put("filtroFamilia", filtroFamiliaStr);
            parameters.put("filtroStock", filtroStock.toString());

            // Total de productos encontrados
            parameters.put("totalProductos", productosDtoList.size());

            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
            byte[] pdfBytes = JasperExportManager.exportReportToPdf(jasperPrint);
            String b64 = Base64.getEncoder().encodeToString(pdfBytes);
            return b64;

        } catch (Exception e) {
            log.severe("Error al generar el reporte: " + e.getMessage());
            throw new RuntimeException("Error al generar el reporte de productos: " + e.getMessage(), e);
        }
    }

    public List<Producto> findByGrupoProductoId(Long id) {
        return repository.findByPdvGrupoProductoId(id);
    }

    public List<ProductoIdAndCantidadDto> findProductosAndCantidadVendidaPorPeriodoAndSucursal(String inicio,
            String fin, Long sucId) {
        List<ProductoIdAndCantidadDto> productoIdAndCantidadDtoList = repository
                .findProductosAndCantidadVendidaPorPeriodoAndSucursal(sucId, stringToDate(inicio), stringToDate(fin));
        return productoIdAndCantidadDtoList;
    }

    public List<LucroPorProductosDto> findLucroPorProductos(String inicio, String fin, List<Long> sucIdList,
            List<Long> usuarioIdList, List<Long> productoIdList, Long subfamiliaId, Long familiaId) {
        List<LucroPorProductosDto> aggregatedResult = new ArrayList<>();
        Map<Long, Double> totalVentaPacksMap = new HashMap<>();

        // Obtener datos de todas las sucursales
        for (Long sucId : sucIdList) {
            boolean filtrarUsuario = usuarioIdList != null && !usuarioIdList.isEmpty();
            boolean filtrarProducto = productoIdList != null && !productoIdList.isEmpty();

            List<Long> finalUsuarioIdList = filtrarUsuario ? usuarioIdList : Arrays.asList(-1L);
            List<Long> finalProductoIdList = filtrarProducto ? productoIdList : Arrays.asList(-1L);

            List<LucroPorProductosDto> lucroPorProductosDtoList = repository.findLucroPorProducto(sucId,
                    stringToDate(inicio), stringToDate(fin), finalUsuarioIdList, finalProductoIdList, subfamiliaId, familiaId, filtrarUsuario,
                    filtrarProducto);
            aggregatedResult.addAll(lucroPorProductosDtoList);

            // Obtener totalVentaPacks (SUM(vi.precio * vi.cantidad)) para calcular
            // ventaMedia correctamente
            List<Object[]> totalVentaPacksList = repository.findTotalVentaPacksPorProducto(sucId, stringToDate(inicio),
                    stringToDate(fin), finalUsuarioIdList, finalProductoIdList, subfamiliaId, familiaId, filtrarUsuario, filtrarProducto);
            for (Object[] row : totalVentaPacksList) {
                Long productoId = ((Number) row[0]).longValue();
                Double totalVentaPacks = ((Number) row[1]).doubleValue();
                totalVentaPacksMap.merge(productoId, totalVentaPacks, Double::sum);
            }
        }

        Map<Long, LucroPorProductosDto> combinedResults = new HashMap<>();
        for (LucroPorProductosDto dto : aggregatedResult) {
            combinedResults.merge(dto.getProductoId(), dto, (oldDto, newDto) -> {
                oldDto.aggregate(newDto);
                return oldDto;
            });
        }
        List<LucroPorProductosDto> result = new ArrayList<>(combinedResults.values());

        // Calcular todos los campos derivados después de la agregación
        for (LucroPorProductosDto dto : result) {

            if (dto.getCantidad() != null && dto.getCantidad() > 0) {
                // Calcular costo unitario
                dto.setCostoUnitario(dto.getCostoTotal() / dto.getCantidad());

                Double totalVentaPacks = totalVentaPacksMap.get(dto.getProductoId());

                if (totalVentaPacks != null) {
                    dto.setTotalVenta(totalVentaPacks);
                }

                if (totalVentaPacks != null && totalVentaPacks > 0 && dto.getCantidad() > 0) {
                    dto.setVentaMedia(totalVentaPacks / dto.getCantidad());
                } else {
                    dto.setVentaMedia(0.0);
                }

                if (dto.getTotalDescuento() != null) {
                    dto.setTotalDescuento((double) Math.round(dto.getTotalDescuento()));
                }
                if (dto.getTotalAumento() != null) {
                    dto.setTotalAumento((double) Math.round(dto.getTotalAumento()));
                }

                // Calcular lucro (total venta - total costo - descuento + aumento)
                dto.setLucro((double) Math.round(dto.getTotalVenta() - dto.getCostoTotal()
                        - (dto.getTotalDescuento() != null ? dto.getTotalDescuento() : 0.0)
                        + (dto.getTotalAumento() != null ? dto.getTotalAumento() : 0.0)));

                // Calcular margen (lucro / costo total * 100) - porcentaje sobre costo
                if (dto.getCostoTotal() > 0) {
                    dto.setMargen((dto.getLucro() / dto.getCostoTotal()) * 100);
                } else {
                    dto.setMargen(0.0);
                }

                // Calcular porcentaje (lucro / total venta * 100) - porcentaje sobre venta
                if (dto.getTotalVenta() > 0) {
                    dto.setPercent((dto.getLucro() / dto.getTotalVenta()) * 100);
                } else {
                    dto.setPercent(0.0);
                }

            } else {
                // Si no hay cantidad, todos los valores son 0
                dto.setCostoUnitario(0.0);
                dto.setVentaMedia(0.0);
                dto.setLucro(0.0);
                dto.setMargen(0.0);
                dto.setPercent(0.0);
            }
        }

        result.sort((dto1, dto2) -> dto2.getTotalVenta().compareTo(dto1.getTotalVenta()));
        return result;
    }

    public Double findPrecioVentaPorProducto(Long productoId, Long sucursalId) {
        Presentacion presentacion = presentacionService.findByPrincipalAndProductoId(true, productoId);
        if (presentacion != null) {
            PrecioPorSucursal precioPorSucursal = precioPorSucursalService.findBySucursalIdAndPresentacionId(sucursalId,
                    presentacion.getId());
            if (precioPorSucursal != null) {
                return precioPorSucursal.getPrecio();
            }
        }
        return null;
    }
}
