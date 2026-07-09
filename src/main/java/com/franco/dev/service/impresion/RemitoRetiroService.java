package com.franco.dev.service.impresion;

import com.franco.dev.domain.operaciones.Devolucion;
import com.franco.dev.domain.operaciones.DevolucionItem;
import com.franco.dev.domain.operaciones.dto.RemitoRetiroFilaDto;
import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.service.operaciones.DevolucionItemService;
import com.franco.dev.service.operaciones.DevolucionService;
import com.franco.dev.service.productos.CodigoService;
import graphql.GraphQLException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Genera el remito PDF de retiro de devoluciones por proveedor (FASE A).
 * Sigue el patron Jasper de ProductoService: fill -> exportReportToPdf -> Base64.
 *
 * Usado en:
 * - Desktop: Si (impresion del remito de retiro)
 * - Mobile: No
 */
@Service
public class RemitoRetiroService {

    @Autowired
    private DevolucionService devolucionService;

    @Autowired
    private DevolucionItemService devolucionItemService;

    @Autowired
    private CodigoService codigoService;

    /**
     * Arma las filas desde las devoluciones/items indicadas y devuelve el PDF en Base64.
     */
    @Transactional(readOnly = true)
    public String generarRemitoRetiroProveedor(List<Long> devolucionIds) {
        if (devolucionIds == null || devolucionIds.isEmpty()) {
            throw new GraphQLException("Se requiere al menos una devolucion para el remito");
        }

        List<RemitoRetiroFilaDto> filas = new ArrayList<>();
        String proveedorNombre = null;
        String sucursalNombre = null;
        boolean sucursalUnica = true;
        DateTimeFormatter vtoFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        for (Long id : devolucionIds) {
            Devolucion d = devolucionService.findById(id).orElse(null);
            if (d == null) continue;

            if (proveedorNombre == null && d.getProveedor() != null && d.getProveedor().getPersona() != null) {
                proveedorNombre = d.getProveedor().getPersona().getNombre();
            }
            String sucNombre = d.getSucursalOrigen() != null ? d.getSucursalOrigen().getNombre() : null;
            if (sucursalNombre == null) {
                sucursalNombre = sucNombre;
            } else if (sucNombre != null && !sucursalNombre.equals(sucNombre)) {
                sucursalUnica = false;
            }

            List<DevolucionItem> items = devolucionItemService.findByDevolucionId(id);
            for (DevolucionItem item : items) {
                Presentacion pres = item.getPresentacion();
                String codigo = null;
                if (pres != null && pres.getId() != null) {
                    Codigo cod = codigoService.findPrincipalByPresentacionId(pres.getId());
                    if (cod != null) codigo = cod.getCodigo();
                }
                RemitoRetiroFilaDto fila = new RemitoRetiroFilaDto();
                fila.setSucursal(sucNombre);
                fila.setCodigo(codigo);
                fila.setDescripcion(item.getProducto() != null ? item.getProducto().getDescripcion() : null);
                fila.setPresentacion(pres != null ? pres.getDescripcion() : null);
                fila.setCantidad(item.getCantidad());
                fila.setLote(item.getLote());
                fila.setVencimiento(item.getVencimiento() != null ? item.getVencimiento().format(vtoFmt) : null);
                fila.setIdentificador(d.getIdentificador());
                filas.add(fila);
            }
        }

        try {
            ClassPathResource resource = new ClassPathResource("reports/remito-retiro-devolucion.jrxml");
            InputStream inputStream = resource.getInputStream();
            JasperReport jasperReport = JasperCompileManager.compileReport(inputStream);
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(filas);

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("proveedor", proveedorNombre != null ? proveedorNombre : "");
            parameters.put("sucursal", sucursalUnica && sucursalNombre != null ? sucursalNombre : "VARIAS");
            parameters.put("fechaReporte",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));

            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
            byte[] pdfBytes = JasperExportManager.exportReportToPdf(jasperPrint);
            return Base64.getEncoder().encodeToString(pdfBytes);
        } catch (Exception e) {
            throw new GraphQLException("Error al generar el remito de retiro: " + e.getMessage());
        }
    }
}
