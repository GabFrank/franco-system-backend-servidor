package com.franco.dev.service.productos;

import com.franco.dev.domain.dto.ProductoIdAndCantidadDto;
import com.franco.dev.domain.dto.ProductoReportDto;
import com.franco.dev.domain.operaciones.dto.LucroPorProductosDto;
import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.Subfamilia;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.graphql.productos.input.ProductoInput;
import com.franco.dev.repository.productos.ProductoRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Service
@AllArgsConstructor
public class ProductoService extends CrudService<Producto, ProductoRepository, Long> {

    private static final Logger log = Logger.getLogger(String.valueOf(ProductoService.class));
    @Autowired
    private final ProductoRepository repository;
    @Autowired
    private final UsuarioService usuarioService;
    @Autowired
    private final SubFamiliaService subFamiliaService;
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
                // Pasamos el String convertido
                return repository.findbyAll("%", offset, sucursalIdStr, conStockStr);
            }
        }

        texto = texto.replace(' ', '%').toUpperCase();
        if (isEnvase != null && isEnvase == true) {
            return repository.findEnvases(texto, offset, true);
        } else {
            // Pasamos el String convertido
            return repository.findbyAll(texto, offset, sucursalIdStr, conStockStr);
        }
    }

    public boolean existsByDescripcion(String descripcion) {
        if (descripcion == null) {
            return false;
        }
        Producto existing = repository.findByDescripcion(descripcion.toUpperCase());
        return existing != null;
    }

    public Page<Producto> findWithFilters(String texto, Boolean activo, Boolean stock, Boolean balanza,
            Long subfamiliaId, Boolean vencimiento, Boolean costoCero, String stockFiltro, Long sucursalId,
            Pageable page) {
        return repository.searchWithFilters(texto, activo, stock, balanza, subfamiliaId, vencimiento, costoCero,
                stockFiltro, sucursalId, page);
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
            // No interrumpir el flujo si falla el registro de modificación
            System.err.println("Error registrando modificación de producto: " + ex.getMessage());
            ex.printStackTrace();
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
                    // No interrumpir el flujo si falla el registro de modificación
                    System.err.println("Error registrando eliminación de producto: " + ex.getMessage());
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
        return repository.findByCodigo(texto);
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
            File file = ResourceUtils.getFile("classpath:reports/productos.jrxml");
            JasperReport jasperReport = JasperCompileManager.compileReport(file.getAbsolutePath());
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
            JasperExportManager.exportReportToPdfFile(jasperPrint,
                    imageService.getStorageDirectoryPathReports() + "productos.pdf");

            // Leer el PDF generado y convertir a Base64
            File pdf = new File(imageService.getStorageDirectoryPathReports() + "productos.pdf");
            byte[] bytes = Files.readAllBytes(pdf.toPath());
            String b64 = Base64.getEncoder().encodeToString(bytes);
            return b64;

        } catch (Exception e) {
            log.severe("Error generando reporte de productos: " + e.getMessage());
            e.printStackTrace();
            return "Error al generar el reporte: " + e.getMessage();
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
            String stockFiltro,
            Long sucursalId,
            Long usuarioId,
            String usuario) throws FileNotFoundException {

        // Usar el método de búsqueda con filtros existente
        Page<Producto> productoPage = repository.searchWithFilters(
                texto != null ? texto.replace(' ', '%').toUpperCase() : "%",
                activo,
                stock,
                balanza,
                subfamiliaId,
                vencimiento,
                costoCero,
                stockFiltro,
                sucursalId,
                null // No paginar para el reporte
        );

        List<Producto> productoList = productoPage.getContent();

        // Construir filtros: Búsqueda arriba + 4 columnas abajo

        // Filtro de búsqueda (línea superior completa)
        StringBuilder filtroBusqueda = new StringBuilder();
        if (texto != null && !texto.trim().isEmpty()) {
            String textoBusqueda = texto.length() > 60 ? texto.substring(0, 57) + "..." : texto;
            filtroBusqueda.append("Búsqueda: '").append(textoBusqueda).append("'");
        } else {
            filtroBusqueda.append("Búsqueda: todos");
        }

        // Columna 1: Estado y Pesable
        StringBuilder filtroColumna1 = new StringBuilder();
        if (activo != null) {
            filtroColumna1.append("Estado: ").append(activo ? "activos" : "inactivos");
        } else {
            filtroColumna1.append("Estado: todos");
        }
        filtroColumna1.append("\n");
        if (balanza != null) {
            filtroColumna1.append("Pesable: ").append(balanza ? "sí" : "no");
        } else {
            filtroColumna1.append("Pesable: todos");
        }

        // Columna 2: Movimiento stock y Costo cero
        StringBuilder filtroColumna2 = new StringBuilder();
        if (stock != null) {
            filtroColumna2.append("Mov. stock: ").append(stock ? "sí" : "no");
        } else {
            filtroColumna2.append("Mov. stock: todos");
        }
        filtroColumna2.append("\n");
        if (costoCero != null) {
            filtroColumna2.append("Costo cero: ").append(costoCero ? "sí" : "no");
        } else {
            filtroColumna2.append("Costo cero: todos");
        }

        // Columna 3: Solo Vencimiento
        StringBuilder filtroColumna3 = new StringBuilder();
        if (vencimiento != null) {
            filtroColumna3.append("Vencimiento: ").append(vencimiento ? "sí" : "no");
        } else {
            filtroColumna3.append("Vencimiento: todos");
        }

        // Columna 4: Subfamilia (debajo de vencimiento en la misma columna 3 del
        // template)
        StringBuilder filtroColumna4 = new StringBuilder();
        if (subfamiliaId != null) {
            try {
                Subfamilia subfamilia = subFamiliaService.findById(subfamiliaId).orElse(null);
                if (subfamilia != null) {
                    String nombreCorto = subfamilia.getNombre().length() > 35
                            ? subfamilia.getNombre().substring(0, 32) + "..."
                            : subfamilia.getNombre();
                    filtroColumna4.append("Subfamilia: ").append(nombreCorto);
                } else {
                    filtroColumna4.append("Subfamilia ID: ").append(subfamiliaId);
                }
            } catch (Exception e) {
                filtroColumna4.append("Subfamilia ID: ").append(subfamiliaId);
            }
        } else {
            filtroColumna4.append("Subfamilia: todos");
        }

        // Filtro de Stock (columna 4 del template - separado)
        StringBuilder filtroStock = new StringBuilder();
        if (stockFiltro != null && !stockFiltro.equals("todos")) {
            filtroStock.append("Stock: ").append(stockFiltro);
            if (sucursalId != null) {
                try {
                    Sucursal sucursal = sucursalService.findById(sucursalId).orElse(null);
                    if (sucursal != null) {
                        // Mostrar nombre completo de sucursal, truncar solo si es muy largo
                        String nombreSucursal = sucursal.getNombre().length() > 35
                                ? sucursal.getNombre().substring(0, 32) + "..."
                                : sucursal.getNombre();
                        filtroStock.append(" (").append(nombreSucursal).append(")");
                    } else {
                        filtroStock.append(" (ID: ").append(sucursalId).append(")");
                    }
                } catch (Exception e) {
                    filtroStock.append(" (ID: ").append(sucursalId).append(")");
                }
            }
        } else {
            filtroStock.append("Stock: todos");
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
            File file = ResourceUtils.getFile("classpath:reports/productos.jrxml");
            JasperReport jasperReport = JasperCompileManager.compileReport(file.getAbsolutePath());
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

            // Usuario actual - usar el parámetro recibido
            parameters.put("usuario", usuario != null ? usuario : "Usuario del Sistema");

            // Filtro de búsqueda y filtros en columnas
            parameters.put("filtroBusqueda", filtroBusqueda.toString());
            parameters.put("filtroColumna1", filtroColumna1.toString());
            parameters.put("filtroColumna2", filtroColumna2.toString());
            parameters.put("filtroColumna3", filtroColumna3.toString());
            parameters.put("filtroColumna4", filtroColumna4.toString());
            parameters.put("filtroStock", filtroStock.toString());

            // Total de productos encontrados
            parameters.put("totalProductos", productosDtoList.size());

            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
            JasperExportManager.exportReportToPdfFile(jasperPrint,
                    imageService.getStorageDirectoryPathReports() + "productos.pdf");

            // Leer el PDF generado y convertir a Base64
            File pdf = new File(imageService.getStorageDirectoryPathReports() + "productos.pdf");
            byte[] bytes = Files.readAllBytes(pdf.toPath());
            String b64 = Base64.getEncoder().encodeToString(bytes);
            return b64;

        } catch (Exception e) {
            log.severe("Error generando reporte de productos con filtros: " + e.getMessage());
            e.printStackTrace();
            return "Error al generar el reporte: " + e.getMessage();
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
            List<Long> usuarioIdList, List<Long> productoIdList, Long subfamiliaId) {
        List<LucroPorProductosDto> aggregatedResult = new ArrayList<>();
        Map<Long, Double> totalVentaPacksMap = new HashMap<>();

        // Obtener datos de todas las sucursales
        for (Long sucId : sucIdList) {
            boolean filtrarUsuario = usuarioIdList != null && !usuarioIdList.isEmpty();
            boolean filtrarProducto = productoIdList != null && !productoIdList.isEmpty();

            List<Long> finalUsuarioIdList = filtrarUsuario ? usuarioIdList : Arrays.asList(-1L);
            List<Long> finalProductoIdList = filtrarProducto ? productoIdList : Arrays.asList(-1L);

            List<LucroPorProductosDto> lucroPorProductosDtoList = repository.findLucroPorProducto(sucId,
                    stringToDate(inicio), stringToDate(fin), finalUsuarioIdList, finalProductoIdList, subfamiliaId, filtrarUsuario,
                    filtrarProducto);
            aggregatedResult.addAll(lucroPorProductosDtoList);

            // Obtener totalVentaPacks (SUM(vi.precio * vi.cantidad)) para calcular
            // ventaMedia correctamente
            List<Object[]> totalVentaPacksList = repository.findTotalVentaPacksPorProducto(sucId, stringToDate(inicio),
                    stringToDate(fin), finalUsuarioIdList, finalProductoIdList, subfamiliaId, filtrarUsuario, filtrarProducto);
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
            // Log para debug
            log.info("DEBUG - Producto ID: " + dto.getProductoId() +
                    ", Cantidad: " + dto.getCantidad() +
                    ", CostoTotal: " + dto.getCostoTotal() +
                    ", TotalVenta: " + dto.getTotalVenta());

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

                // Log para debug de cálculos
                log.info("DEBUG - Cálculos para ID " + dto.getProductoId() +
                        ": CostoUnitario=" + dto.getCostoUnitario() +
                        ", VentaMedia=" + dto.getVentaMedia() +
                        ", Lucro=" + dto.getLucro() +
                        ", Margen=" + dto.getMargen() +
                        ", Percent=" + dto.getPercent() +
                        ", Descuento=" + dto.getTotalDescuento() +
                        ", Aumento=" + dto.getTotalAumento());
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
