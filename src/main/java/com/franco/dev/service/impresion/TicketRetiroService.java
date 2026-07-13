package com.franco.dev.service.impresion;

import com.franco.dev.domain.operaciones.Devolucion;
import com.franco.dev.domain.operaciones.DevolucionItem;
import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.service.operaciones.DevolucionItemService;
import com.franco.dev.service.operaciones.DevolucionService;
import com.franco.dev.service.productos.CodigoService;
import com.franco.dev.service.utils.PrintingService;
import com.franco.dev.utilitarios.PresentacionUtils;
import com.franco.dev.utilitarios.print.escpos.EscPos;
import com.franco.dev.utilitarios.print.output.PrinterOutputStream;
import graphql.GraphQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.print.PrintService;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.franco.dev.service.impresion.TicketRetiroLayout.centrar;
import static com.franco.dev.service.impresion.TicketRetiroLayout.columnaDoble;
import static com.franco.dev.service.impresion.TicketRetiroLayout.columnasPara;
import static com.franco.dev.service.impresion.TicketRetiroLayout.formatearCantidad;
import static com.franco.dev.service.impresion.TicketRetiroLayout.lineasDeItem;
import static com.franco.dev.service.impresion.TicketRetiroLayout.recortar;
import static com.franco.dev.service.impresion.TicketRetiroLayout.repetir;

/**
 * Imprime el comprobante de retiro de devoluciones en impresora termica.
 * Mismos datos que el remito PDF, agrupados por sucursal (en 58mm no entra una
 * columna Sucursal por item).
 *
 * El ancho de papel llega por parametro (58mm / 80mm) porque todavia no existe
 * el modulo de registro de impresoras: cuando exista, solo cambia el origen del
 * valor, no este servicio. Todo el layout se deriva de `columnas`.
 *
 * Usado en:
 * - Desktop: Si (pantalla de retiro consolidado)
 * - Mobile: No
 */
@Service
public class TicketRetiroService {

    /** Impresora usada cuando el cliente no manda printerName (mismo default que ImpresionService). */
    private static final String IMPRESORA_DEFAULT = "TICKET58";

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired
    private DevolucionService devolucionService;

    @Autowired
    private DevolucionItemService devolucionItemService;

    @Autowired
    private CodigoService codigoService;

    @Autowired
    private PrintingService printingService;

    @Autowired
    private com.franco.dev.service.operaciones.DevolucionConfiguracionService configuracionService;

    /**
     * @param anchoMm ancho del papel en milimetros (58 u 80). Null usa la config.
     */
    @Transactional(readOnly = true)
    public Boolean imprimirTicketRetiro(List<Long> devolucionIds, String printerName, Integer anchoMm) {
        com.franco.dev.domain.operaciones.DevolucionConfiguracion config = configuracionService.getConfiguracion();
        int ancho = anchoMm != null ? anchoMm
                : (config.getTicketAnchoMm() != null ? config.getTicketAnchoMm() : 58);
        String titulo = config.getComprobanteTitulo() != null ? config.getComprobanteTitulo() : "COMPROBANTE DE RETIRO";
        String firma = config.getComprobanteTextoFirma() != null ? config.getComprobanteTextoFirma() : "Firma del proveedor";
        String impresoraDefault = config.getImpresoraTicket() != null ? config.getImpresoraTicket() : IMPRESORA_DEFAULT;

        List<String> lineas = construirTicket(devolucionIds, columnasPara(ancho), titulo, firma);

        PrintService printService = printerName != null ? printingService.getPrintService(printerName) : null;
        if (printService == null) {
            printService = PrinterOutputStream.getPrintServiceByName(impresoraDefault);
        }
        if (printService == null) {
            throw new GraphQLException("No se encontro la impresora "
                    + (printerName != null ? printerName : impresoraDefault));
        }

        try (PrinterOutputStream printerOutputStream = new PrinterOutputStream(printService)) {
            EscPos escpos = new EscPos(printerOutputStream);
            for (String linea : lineas) {
                escpos.writeLF(linea);
            }
            escpos.feed(4);
            escpos.close();
        } catch (IOException e) {
            throw new GraphQLException("Error al imprimir el ticket de retiro: " + e.getMessage());
        }
        return true;
    }

    /**
     * Arma el ticket completo como lineas de texto ya alineadas al ancho dado.
     * Separado de la impresion para poder testear el layout sin impresora.
     */
    List<String> construirTicket(List<Long> devolucionIds, int columnas, String titulo, String firma) {
        if (devolucionIds == null || devolucionIds.isEmpty()) {
            throw new GraphQLException("Se requiere al menos una devolucion para el ticket");
        }

        Map<String, List<DevolucionItem>> porSucursal = new LinkedHashMap<>();
        String proveedorNombre = null;
        for (Long id : devolucionIds) {
            Devolucion d = devolucionService.findById(id).orElse(null);
            if (d == null) continue;
            if (proveedorNombre == null && d.getProveedor() != null && d.getProveedor().getPersona() != null) {
                proveedorNombre = d.getProveedor().getPersona().getNombre();
            }
            String sucNombre = d.getSucursalOrigen() != null ? d.getSucursalOrigen().getNombre() : "SIN SUCURSAL";
            porSucursal.computeIfAbsent(sucNombre, k -> new ArrayList<>())
                    .addAll(devolucionItemService.findByDevolucionId(id));
        }
        if (porSucursal.isEmpty()) {
            throw new GraphQLException("Las devoluciones indicadas no existen");
        }

        String separador = repetir("-", columnas);
        List<String> lineas = new ArrayList<>();
        lineas.add(centrar(titulo != null ? titulo : "COMPROBANTE DE RETIRO", columnas));
        if (proveedorNombre != null) lineas.add(centrar(proveedorNombre, columnas));
        lineas.add(centrar(LocalDateTime.now().format(FECHA_HORA), columnas));
        lineas.add(separador);

        int totalItems = 0;
        for (Map.Entry<String, List<DevolucionItem>> grupo : porSucursal.entrySet()) {
            lineas.add(centrar(grupo.getKey(), columnas));
            for (DevolucionItem item : grupo.getValue()) {
                totalItems++;
                lineas.addAll(lineasDeItem(
                        item.getProducto() != null ? item.getProducto().getDescripcion() : "",
                        codigoDe(item.getPresentacion()),
                        PresentacionUtils.formatearFactor(item.getPresentacion()),
                        formatearCantidad(item.getCantidad()),
                        item.getLote(),
                        item.getVencimiento() != null ? item.getVencimiento().format(FECHA) : null,
                        columnas));
            }
            lineas.add(separador);
        }

        lineas.add(columnaDoble("Items:", String.valueOf(totalItems), columnas));
        lineas.add("");
        lineas.add(centrar(repetir("_", Math.min(columnas - 4, 28)), columnas));
        lineas.add(centrar(firma != null ? firma : "Firma del proveedor", columnas));
        return lineas;
    }

    private String codigoDe(Presentacion pres) {
        if (pres == null || pres.getId() == null) return "";
        Codigo cod = codigoService.findPrincipalByPresentacionId(pres.getId());
        return cod != null ? recortar(cod.getCodigo(), 20) : "";
    }
}
