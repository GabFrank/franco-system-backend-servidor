package com.franco.dev.service.impresion;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.PreGasto;
import com.franco.dev.domain.financiero.PreGastoDetalleFinanzas;
import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.dto.LucroPorFuncionarioDto;
import com.franco.dev.domain.operaciones.dto.LucroPorProductosDto;
import com.franco.dev.domain.operaciones.dto.ReporteVentaItemDto;
import com.franco.dev.domain.operaciones.dto.ReporteVentaDetalladoDto;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.graphql.financiero.input.PdvCajaBalanceDto;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.PreGastoDetalleFinanzasService;
import com.franco.dev.service.impresion.dto.GastoDto;
import com.franco.dev.service.impresion.dto.RetiroDto;
import com.franco.dev.service.productos.CodigoService;
import com.franco.dev.service.productos.PrecioPorSucursalService;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.service.utils.PrintingService;
import com.franco.dev.utilitarios.DateUtils;
import com.google.zxing.WriterException;
import com.franco.dev.utilitarios.print.escpos.EscPos;
import com.franco.dev.utilitarios.print.escpos.EscPosConst;
import com.franco.dev.utilitarios.print.escpos.Style;
import com.franco.dev.utilitarios.print.escpos.barcode.QRCode;
import com.franco.dev.utilitarios.print.escpos.image.*;
import com.franco.dev.utilitarios.print.output.PrinterOutputStream;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.JRPrintServiceExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimplePrintServiceExporterConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.print.PrintService;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.OrientationRequested;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

import static com.franco.dev.service.utils.PrintingService.resize;

@Service
public class ImpresionService {

    private static final Logger log = LoggerFactory.getLogger(ImpresionService.class);

    /**
     * Cache de reportes Jasper compilados para evitar recompilación y problemas de
     * classloader (web app stopped)
     */
    private static final Map<String, JasperReport> REPORT_CACHE = new ConcurrentHashMap<>();

    PrintService selectedPrintService = null;
    @Autowired
    private ImageService imageService;
    @Autowired
    private PrintingService printingService;
    private PrintService printService;
    private PrinterOutputStream printerOutputStream;
    @Autowired
    private CodigoService codigoService;
    @Autowired
    private PrecioPorSucursalService precioPorSucursalService;
    @Autowired
    private SucursalService sucursalService;
    @Autowired
    private com.franco.dev.service.personas.FuncionarioService funcionarioService;
    @Autowired
    private PreGastoDetalleFinanzasService preGastoDetalleFinanzasService;

    @Autowired
    private MultiTenantService multiTenantService;

    @Autowired
    private com.franco.dev.service.activos.VehiculoService vehiculoService;
    @Autowired
    private com.franco.dev.service.activos.InmuebleService inmuebleService;
    @Autowired
    private com.franco.dev.service.activos.MuebleService muebleService;
    @Autowired
    private com.franco.dev.service.equipos.EquipoService equipoService;

    public static DateTimeFormatter shortDate = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    public static DateTimeFormatter shortDateTime = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    /**
     * Compila un reporte JRXML desde los recursos del classpath (dentro del JAR).
     * Usa ClassPathResource y cache para evitar problemas de classloader (web app
     * stopped) y mejorar rendimiento.
     * 
     * @param resourcePath Ruta del archivo JRXML (ej:
     *                     "reports/solicitud-pago.jrxml")
     * @return JasperReport compilado
     * @throws JRException Si hay error compilando el reporte
     */
    private JasperReport compileReportFromClasspath(String resourcePath) throws JRException {
        return REPORT_CACHE.computeIfAbsent(resourcePath, path -> {
            try {
                ClassPathResource resource = new ClassPathResource(path);
                try (InputStream is = resource.getInputStream()) {
                    return JasperCompileManager.compileReport(is);
                }
            } catch (Exception e) {
                throw new RuntimeException("Error compilando reporte: " + path, e);
            }
        });
    }

    public void printReport(JasperPrint jasperPrint, String filename, String printerName, Boolean silent)
            throws GraphQLException {
        printReport(jasperPrint, filename, printerName, silent, MediaSizeName.ISO_A4);
    }

    /**
     * Overload que permite elegir el tamanho de hoja (A4, Carta, etc.) segun el perfil
     * de papel de la impresora, en vez de fijar siempre A4.
     * @see com.franco.dev.utilitarios.print.PerfilPapelHelper#mediaSize
     */
    public void printReport(JasperPrint jasperPrint, String filename, String printerName, Boolean silent,
                            MediaSizeName mediaSize)
            throws GraphQLException {
        if (silent == null)
            silent = false;
        PrintRequestAttributeSet printRequestAttributeSet = new HashPrintRequestAttributeSet();
        printRequestAttributeSet.add(mediaSize != null ? mediaSize : MediaSizeName.ISO_A4);
        if (jasperPrint.getOrientationValue() == net.sf.jasperreports.engine.type.OrientationEnum.LANDSCAPE) {
            printRequestAttributeSet.add(OrientationRequested.LANDSCAPE);
        } else {
            printRequestAttributeSet.add(OrientationRequested.PORTRAIT);
        }

        JRPrintServiceExporter exporter = new JRPrintServiceExporter();
        SimplePrintServiceExporterConfiguration configuration = new SimplePrintServiceExporterConfiguration();
        configuration.setPrintRequestAttributeSet(printRequestAttributeSet);
        configuration.setDisplayPageDialog(!silent);
        configuration.setDisplayPrintDialog(!silent);

        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        exporter.setConfiguration(configuration);

        printService = PrinterOutputStream.getPrintServiceByName(printerName);

        if (printService != null) {
            try {
                JasperExportManager.exportReportToPdfFile(jasperPrint,
                        imageService.getStorageDirectoryPathReports() + File.separator + filename);
                exporter.exportReport();
            } catch (JRException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("You did not set the printer!");
        }
    }

    public Boolean printBalance(PdvCajaBalanceDto balanceDto, String printerName, String local) {
        try {
            selectedPrintService = printingService.getPrintService(printerName);
            if (selectedPrintService != null) {
                printerOutputStream = new PrinterOutputStream(selectedPrintService);
                // creating the EscPosImage, need buffered image and algorithm.
                // Styles
                Style center = new Style().setJustification(EscPosConst.Justification.Center);

                QRCode qrCode = new QRCode();

                BufferedImage imageBufferedImage = ImageIO.read(new File(imageService.getImagePath() + "logo.png"));
                imageBufferedImage = resize(imageBufferedImage, 200, 100);
                RasterBitImageWrapper imageWrapper = new RasterBitImageWrapper();
                EscPos escpos = new EscPos(printerOutputStream);
                Bitonal algorithm = new BitonalThreshold();
                EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
                imageWrapper.setJustification(EscPosConst.Justification.Center);
                escpos.write(imageWrapper, escposImage);
                // escpos.writeLF(center.setBold(true), "SUC. CENTRO");
                // escpos.writeLF(center, "Salto del Guairá");
                if (balanceDto.getSucursal() != null) {
                    escpos.writeLF(center, "Suc: " + balanceDto.getSucursal().getNombre());
                }
                if (local != null) {
                    escpos.writeLF(center, "Local: " + local);
                }
                escpos.writeLF(center.setBold(true), "Caja: " + balanceDto.getIdCaja());
                if (balanceDto.getUsuario().getPersona().getNombre().length() > 23) {
                    escpos.writeLF("Cajero: " + balanceDto.getUsuario().getPersona().getNombre().substring(0, 23));
                } else {
                    escpos.writeLF("Cajero: " + balanceDto.getUsuario().getPersona().getNombre());
                }
                escpos.writeLF("Fecha Apertura: " + balanceDto.getFechaApertura().format(formatter));
                escpos.writeLF("Fecha Cierre: " + balanceDto.getFechaCierre().format(formatter));
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "VALORES DE APERTURA");
                escpos.write("Guaranies G$: ");
                String valorGsAper = NumberFormat.getNumberInstance(Locale.GERMAN)
                        .format(balanceDto.getTotalGsAper().intValue());
                for (int i = 18; i > valorGsAper.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorGsAper);
                escpos.write("Reales R$: ");
                String valorRsAper = String.format("%.2f", balanceDto.getTotalRsAper());
                for (int i = 21; i > valorRsAper.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorRsAper);
                escpos.write("Dolares D$: ");
                String valorDsAper = String.format("%.2f", balanceDto.getTotalDsAper());
                for (int i = 20; i > valorDsAper.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorDsAper);
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "VALORES DE CIERRE");
                escpos.write("Guaranies G$: ");
                String valorGsCierre = NumberFormat.getNumberInstance(Locale.GERMAN)
                        .format(balanceDto.getTotalGsCierre().intValue());
                for (int i = 18; i > valorGsCierre.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorGsCierre);
                escpos.write("Reales R$: ");
                String valorRsCierre = String.format("%.2f", balanceDto.getTotalRsCierre());
                for (int i = 21; i > valorRsCierre.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorRsCierre);
                escpos.write("Dolares D$: ");
                String valorDsCierre = String.format("%.2f", balanceDto.getTotalDsCierre());
                for (int i = 20; i > valorDsCierre.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorDsCierre);
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "VALORES DE TARJETA");
                escpos.write("Guaranies G$: ");
                String valorTarjeta = NumberFormat.getNumberInstance(Locale.GERMAN)
                        .format(balanceDto.getTotalTarjeta().intValue());
                for (int i = 18; i > valorTarjeta.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorTarjeta);
                escpos.write("Reales R$: ");
                String valorTarjetaRs = String.format("%.2f", balanceDto.getTotalTarjetaRs());
                for (int i = 21; i > valorTarjetaRs.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorTarjetaRs);
                escpos.write("Dolares D$: ");
                String valorTarjetaDs = String.format("%.2f", balanceDto.getTotalTarjetaDs());
                for (int i = 20; i > valorTarjetaDs.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorTarjetaDs);
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "VALORES DE TRANSFERENCIA");
                escpos.write("Guaranies G$: ");
                String valorTransferencia = NumberFormat.getNumberInstance(Locale.GERMAN)
                        .format(balanceDto.getTotalTransferencia().intValue());
                for (int i = 18; i > valorTransferencia.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorTransferencia);
                escpos.write("Reales R$: ");
                String valorTransferenciaRs = String.format("%.2f", balanceDto.getTotalTransferenciaRs());
                for (int i = 21; i > valorTransferenciaRs.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorTransferenciaRs);
                escpos.write("Dolares D$: ");
                String valorTransferenciaDs = String.format("%.2f", balanceDto.getTotalTransferenciaDs());
                for (int i = 20; i > valorTransferenciaDs.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorTransferenciaDs);
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "VALORES DE CREDITO");
                escpos.write("Guaranies G$: ");
                String valorCredito = NumberFormat.getNumberInstance(Locale.GERMAN)
                        .format(balanceDto.getTotalCredito().intValue());
                for (int i = 18; i > valorCredito.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorCredito);
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "VALORES DE RETIRO");
                String valorGsRetiro = NumberFormat.getNumberInstance(Locale.GERMAN)
                        .format(balanceDto.getTotalRetiroGs().intValue());
                escpos.write("Guaranies G$: ");
                for (int i = 18; i > valorGsRetiro.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorGsRetiro);
                String valorRsRetiro = String.format("%.2f", balanceDto.getTotalRetiroRs());
                escpos.write("Reales R$: ");
                for (int i = 21; i > valorRsRetiro.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorRsRetiro);
                String valorDsRetiro = String.format("%.2f", balanceDto.getTotalRetiroDs());
                escpos.write("Dolares D$: ");
                for (int i = 20; i > valorDsRetiro.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorDsRetiro);
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "VALORES DE GASTO");
                String valorGsGasto = NumberFormat.getNumberInstance(Locale.GERMAN)
                        .format(balanceDto.getTotalGastoGs().intValue());
                escpos.write("Guaranies G$: ");
                for (int i = 18; i > valorGsGasto.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorGsGasto);
                String valorRsGasto = String.format("%.2f", balanceDto.getTotalGastoRs());
                escpos.write("Reales R$: ");
                for (int i = 21; i > valorRsGasto.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorRsGasto);
                String valorDsGasto = String.format("%.2f", balanceDto.getTotalGastoDs());
                escpos.write("Dolares D$: ");
                for (int i = 20; i > valorDsGasto.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorDsGasto);
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "DIFERENCIA");
                String valorGsDiferencia = NumberFormat.getNumberInstance(Locale.GERMAN)
                        .format(balanceDto.getDiferenciaGs().intValue());
                escpos.write("Guaranies G$: ");
                for (int i = 18; i > valorGsDiferencia.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorGsDiferencia);
                String valorRsDiferencia = String.format("%.2f", balanceDto.getDiferenciaRs());
                escpos.write("Reales R$: ");
                for (int i = 21; i > valorRsDiferencia.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorRsDiferencia);
                String valorDsDiferencia = String.format("%.2f", balanceDto.getDiferenciaDs());
                escpos.write("Dolares D$: ");
                for (int i = 20; i > valorDsDiferencia.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorDsDiferencia);
                // escpos.writeLF("--------------------------------");
                // escpos.writeLF(center, "VENTA TOTAL");
                // String valorGsVenta =
                // NumberFormat.getNumberInstance(Locale.GERMAN).format(balanceDto.getTotalVentaGs().intValue());
                // escpos.write("Guaranies G$: ");
                // for (int i = 18; i > valorGsVenta.length(); i--) {
                // escpos.write(" ");
                // }
                // escpos.writeLF(valorGsVenta);
                // String valorRsVenta = String.format("%.2f", balanceDto.getTotalVentaRs());
                // escpos.write("Reales R$: ");
                // for (int i = 21; i > valorRsVenta.length(); i--) {
                // escpos.write(" ");
                // }
                // escpos.writeLF(valorRsVenta);
                // String valorDsVenta = String.format("%.2f", balanceDto.getTotalVentaDs());
                // escpos.write("Dolares D$: ");
                // for (int i = 20; i > valorDsVenta.length(); i--) {
                // escpos.write(" ");
                // }
                // escpos.writeLF(valorDsVenta);
                escpos.writeLF("--------------------------------");
                escpos.feed(4);
                escpos.writeLF(center, ".......................");
                escpos.writeLF(center, "FIRMA");
                if (balanceDto.getUsuario().getPersona().getNombre().length() > 23) {
                    escpos.writeLF(center, balanceDto.getUsuario().getPersona().getNombre().substring(0, 23));
                } else {
                    escpos.writeLF(center, balanceDto.getUsuario().getPersona().getNombre());
                }
                escpos.feed(5);
                escpos.close();
                printerOutputStream.close();
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }

    public void printGasto(GastoDto gastoDto, String printerName, String local) {
        try {
            selectedPrintService = printingService.getPrintService(printerName);
            if (selectedPrintService != null) {
                printerOutputStream = new PrinterOutputStream(selectedPrintService);
                // creating the EscPosImage, need buffered image and algorithm.
                // Styles
                Style center = new Style().setJustification(EscPosConst.Justification.Center);

                QRCode qrCode = new QRCode();

                BufferedImage imageBufferedImage = ImageIO.read(new File(imageService.getImagePath() + "logo.png"));
                imageBufferedImage = resize(imageBufferedImage, 200, 100);
                RasterBitImageWrapper imageWrapper = new RasterBitImageWrapper();
                EscPos escpos = new EscPos(printerOutputStream);
                Bitonal algorithm = new BitonalThreshold();
                EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
                imageWrapper.setJustification(EscPosConst.Justification.Center);
                escpos.write(imageWrapper, escposImage);
                if (local != null) {
                    escpos.writeLF(center, "Local: " + local);
                }
                escpos.writeLF(center.setBold(true), "Gasto: " + gastoDto.getId());
                escpos.writeLF(center.setBold(true), "Caja: " + gastoDto.getCajaId());
                if (gastoDto.getUsuario().getPersona().getNombre().length() > 23) {
                    escpos.writeLF("Cajero: " + gastoDto.getUsuario().getPersona().getNombre().substring(0, 23));
                } else {
                    escpos.writeLF("Cajero: " + gastoDto.getUsuario().getPersona().getNombre());
                }
                escpos.writeLF("Fecha " + gastoDto.getFecha().format(formatter));
                String tipoGastoTexto = "SIN TIPO DE GASTO";
                if (gastoDto.getTipoGasto() != null) {
                    String tipoId = gastoDto.getTipoGasto().getId() != null ? gastoDto.getTipoGasto().getId().toString() : "N/A";
                    String tipoDesc = gastoDto.getTipoGasto().getDescripcion() != null
                            ? gastoDto.getTipoGasto().getDescripcion().toUpperCase()
                            : "SIN DESCRIPCION";
                    tipoGastoTexto = "Tipo " + tipoId + " - " + tipoDesc;
                }
                escpos.writeLF(new Style().setBold(true), tipoGastoTexto);
                if (gastoDto.getObservacion() != null) {
                    escpos.writeLF("Obs: " + gastoDto.getObservacion().toUpperCase());
                }
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "VALORES DE GASTO");
                escpos.write("Guaranies G$: ");
                String valorGsAper = NumberFormat.getNumberInstance(Locale.GERMAN)
                        .format(gastoDto.getRetiroGs().intValue());
                for (int i = 18; i > valorGsAper.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorGsAper);
                escpos.write("Reales R$: ");
                String valorRsAper = String.format("%.2f", gastoDto.getRetiroRs());
                for (int i = 21; i > valorRsAper.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorRsAper);
                escpos.write("Dolares D$: ");
                String valorDsAper = String.format("%.2f", gastoDto.getRetiroDs());
                for (int i = 20; i > valorDsAper.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorDsAper);
                escpos.writeLF("--------------------------------");
                escpos.feed(4);
                escpos.writeLF(center, ".......................");
                escpos.writeLF(center, "FIRMA RESPONSABLE");
                if (gastoDto.getResponsable().getPersona().getNombre().length() > 23) {
                    escpos.writeLF(center, gastoDto.getResponsable().getPersona().getNombre().substring(0, 23));
                } else {
                    escpos.writeLF(center, gastoDto.getResponsable().getPersona().getNombre());
                }
                if (gastoDto.getAutorizadoPor() != null) {
                    escpos.writeLF("--------------------------------");
                    escpos.feed(4);
                    escpos.writeLF(center, ".......................");
                    escpos.writeLF(center, "AUTORIZACION");
                    if (gastoDto.getAutorizadoPor().getPersona().getNombre().length() > 23) {
                        escpos.writeLF(center, gastoDto.getAutorizadoPor().getPersona().getNombre().substring(0, 23));
                    } else {
                        escpos.writeLF(center, gastoDto.getAutorizadoPor().getPersona().getNombre());
                    }
                }
                escpos.feed(5);
                escpos.close();
                printerOutputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void printVueltoGasto(GastoDto gastoDto) {
        try {
            printService = PrinterOutputStream.getPrintServiceByName("TICKET58");
            if (printService != null) {
                printerOutputStream = new PrinterOutputStream(printService);
                // creating the EscPosImage, need buffered image and algorithm.
                // Styles
                Style center = new Style().setJustification(EscPosConst.Justification.Center);

                QRCode qrCode = new QRCode();

                BufferedImage imageBufferedImage = ImageIO.read(new File(imageService.getImagePath() + "logo.png"));
                imageBufferedImage = resize(imageBufferedImage, 200, 100);
                RasterBitImageWrapper imageWrapper = new RasterBitImageWrapper();
                EscPos escpos = new EscPos(printerOutputStream);
                Bitonal algorithm = new BitonalThreshold();
                EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
                imageWrapper.setJustification(EscPosConst.Justification.Center);
                escpos.write(imageWrapper, escposImage);
                escpos.writeLF(center.setBold(true), "SUC. CENTRO");
                escpos.writeLF(center, "Salto del Guairá");
                escpos.writeLF(center.setBold(true), "Gasto: " + gastoDto.getId());
                if (gastoDto.getUsuario().getPersona().getNombre().length() > 23) {
                    escpos.writeLF("Cajero: " + gastoDto.getUsuario().getPersona().getNombre().substring(0, 23));
                } else {
                    escpos.writeLF("Cajero: " + gastoDto.getUsuario().getPersona().getNombre());
                }
                escpos.writeLF("Fecha " + gastoDto.getFecha().format(formatter));
                String tipoGastoTexto = "SIN TIPO DE GASTO";
                if (gastoDto.getTipoGasto() != null) {
                    String tipoId = gastoDto.getTipoGasto().getId() != null ? gastoDto.getTipoGasto().getId().toString() : "N/A";
                    String tipoDesc = gastoDto.getTipoGasto().getDescripcion() != null
                            ? gastoDto.getTipoGasto().getDescripcion().toUpperCase()
                            : "SIN DESCRIPCION";
                    tipoGastoTexto = "Tipo " + tipoId + " - " + tipoDesc;
                }
                escpos.writeLF(new Style().setBold(true), tipoGastoTexto);
                if (gastoDto.getObservacion() != null) {
                    escpos.writeLF("Obs: " + gastoDto.getObservacion().toUpperCase());
                }
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "VALORES DE GASTO");
                escpos.write("Guaranies G$: ");
                String valorGsAper = NumberFormat.getNumberInstance(Locale.GERMAN)
                        .format(gastoDto.getRetiroGs().intValue());
                for (int i = 18; i > valorGsAper.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorGsAper);
                escpos.write("Reales R$: ");
                String valorRsAper = String.format("%.2f", gastoDto.getRetiroRs());
                for (int i = 21; i > valorRsAper.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorRsAper);
                escpos.write("Dolares D$: ");
                String valorDsAper = String.format("%.2f", gastoDto.getRetiroDs());
                for (int i = 20; i > valorDsAper.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorDsAper);
                escpos.writeLF("--------------------------------");
                escpos.feed(4);
                escpos.writeLF(center, ".......................");
                escpos.writeLF(center, "FIRMA RESPONSABLE");
                if (gastoDto.getResponsable().getPersona().getNombre().length() > 23) {
                    escpos.writeLF(center, gastoDto.getResponsable().getPersona().getNombre().substring(0, 23));
                } else {
                    escpos.writeLF(center, gastoDto.getResponsable().getPersona().getNombre());
                }
                if (gastoDto.getAutorizadoPor() != null) {
                    escpos.writeLF("--------------------------------");
                    escpos.feed(4);
                    escpos.writeLF(center, ".......................");
                    escpos.writeLF(center, "AUTORIZACION");
                    if (gastoDto.getAutorizadoPor().getPersona().getNombre().length() > 23) {
                        escpos.writeLF(center, gastoDto.getAutorizadoPor().getPersona().getNombre().substring(0, 23));
                    } else {
                        escpos.writeLF(center, gastoDto.getAutorizadoPor().getPersona().getNombre());
                    }
                }
                escpos.feed(5);
                escpos.close();
                printerOutputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Imprime solicitud de pago en ticket 58mm.
     * Estructura: Header (logo, proveedor, nro, hora reporte) | Lista notas | Lista
     * formas pago | Firma | QR
     */
    public void printSolicitudPagoTicket(SolicitudPago solicitudPago, String proveedorNombre,
            String usuario, String printerName,
            java.util.List<NotaTicketDto> notas,
            java.util.List<FormaPagoTicketDto> formasPago) {
        try {
            selectedPrintService = printerName != null ? printingService.getPrintService(printerName) : null;
            if (selectedPrintService == null) {
                selectedPrintService = PrinterOutputStream.getPrintServiceByName("TICKET58");
            }
            if (selectedPrintService != null) {
                printerOutputStream = new PrinterOutputStream(selectedPrintService);
                Style center = new Style().setJustification(EscPosConst.Justification.Center);
                QRCode qrCode = new QRCode();

                BufferedImage imageBufferedImage = ImageIO.read(new File(imageService.getImagePath() + "logo.png"));
                imageBufferedImage = resize(imageBufferedImage, 200, 100);
                BitImageWrapper imageWrapper = new BitImageWrapper();
                EscPos escpos = new EscPos(printerOutputStream);
                Bitonal algorithm = new BitonalThreshold();
                EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
                imageWrapper.setJustification(EscPosConst.Justification.Center);

                // --- HEADER ---
                escpos.writeLF("--------------------------------");
                escpos.write(imageWrapper, escposImage);
                String prov = (proveedorNombre != null ? proveedorNombre : "");
                if (prov.length() > 23)
                    prov = prov.substring(0, 23);
                escpos.writeLF(center.setBold(true), prov);
                escpos.writeLF(center.setBold(true), "Nro " + solicitudPago.getId());
                String horaReporte = LocalDateTime.now().format(formatter);
                escpos.writeLF(center, "Hora: " + horaReporte);
                escpos.writeLF("--------------------------------");

                // --- LISTA NOTAS ---
                escpos.writeLF(center.setBold(true), "NOTAS");
                if (notas != null && !notas.isEmpty()) {
                    for (NotaTicketDto n : notas) {
                        String linea = (n.getNumero() != null ? n.getNumero() : "—") + " "
                                + (n.getFecha() != null ? n.getFecha() : "—") + " "
                                + (n.getValorFormateado() != null ? n.getValorFormateado() : "—");
                        escpos.writeLF(linea);
                    }
                } else {
                    escpos.writeLF("  ---");
                }
                escpos.writeLF("--------------------------------");

                // --- LISTA FORMAS DE PAGO ---
                escpos.writeLF(center.setBold(true), "FORMAS DE PAGO");
                if (formasPago != null && !formasPago.isEmpty()) {
                    for (FormaPagoTicketDto fp : formasPago) {
                        String linea = (fp.getFormaPago() != null ? fp.getFormaPago() : "—") + " "
                                + (fp.getFecha() != null ? fp.getFecha() : "—") + " "
                                + (fp.getValorFormateado() != null ? fp.getValorFormateado() : "—");
                        escpos.writeLF(linea);
                    }
                } else {
                    escpos.writeLF("  ---");
                }
                escpos.writeLF("--------------------------------");

                // --- FIRMA DEL RESPONSABLE ---
                String usr = (usuario != null ? usuario : "");
                if (usr.length() > 23)
                    usr = usr.substring(0, 23);
                escpos.writeLF(center.setBold(true), "Responsable:");
                escpos.writeLF(center, usr);
                escpos.writeLF(center, "_________________________");
                escpos.writeLF("--------------------------------");

                // --- CODIGO QR ---
                escpos.feed(2);
                String qrData = "frc-0-SOLPAG-" + solicitudPago.getId() + "-" + solicitudPago.getId()
                        + "-ListSolicitudPagoComponent-null-null";
                escpos.write(qrCode.setSize(8).setJustification(EscPosConst.Justification.Center), qrData);
                escpos.feed(4);
                escpos.close();
                printerOutputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * DTO para fila de nota en ticket de solicitud de pago.
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class NotaTicketDto {
        private String numero;
        private String fecha;
        private String valorFormateado; // formato {{simbolo}} {{valor}}
    }

    /**
     * DTO para fila de forma de pago en ticket de solicitud de pago.
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FormaPagoTicketDto {
        private String formaPago;
        private String fecha;
        private String valorFormateado; // formato {{simbolo}} {{valor}}
    }

    public void printRetiro(RetiroDto retiroDto, String printerName, String local, Boolean reimpresion) {
        try {
            selectedPrintService = printingService.getPrintService(printerName);
            if (selectedPrintService != null) {
                printerOutputStream = new PrinterOutputStream(selectedPrintService);
                // creating the EscPosImage, need buffered image and algorithm.
                // Styles
                Style center = new Style().setJustification(EscPosConst.Justification.Center);

                QRCode qrCode = new QRCode();

                BufferedImage imageBufferedImage = ImageIO.read(new File(imageService.getImagePath() + "logo.png"));
                imageBufferedImage = resize(imageBufferedImage, 200, 100);
                RasterBitImageWrapper imageWrapper = new RasterBitImageWrapper();
                EscPos escpos = new EscPos(printerOutputStream);
                Bitonal algorithm = new BitonalThreshold();
                EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
                imageWrapper.setJustification(EscPosConst.Justification.Center);
                escpos.write(imageWrapper, escposImage);
                if (reimpresion == true) {
                    escpos.writeLF(center, "REIMPRESION");
                }
                if (local != null) {
                    escpos.writeLF(center, "Local: " + local);
                }
                escpos.writeLF(center.setBold(true), "Retiro: " + retiroDto.getId());
                escpos.writeLF(center.setBold(true), "Caja: " + retiroDto.getCajaId());
                if (retiroDto.getUsuario().getPersona().getNombre().length() > 23) {
                    escpos.writeLF("Cajero: " + retiroDto.getUsuario().getPersona().getNombre().substring(0, 23));
                } else {
                    escpos.writeLF("Cajero: " + retiroDto.getUsuario().getPersona().getNombre());
                }
                escpos.writeLF("Fecha " + retiroDto.getFecha().format(formatter));
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "VALORES DE RETIRO");
                escpos.write("Guaranies G$: ");
                String valorGsAper = NumberFormat.getNumberInstance(Locale.GERMAN)
                        .format(retiroDto.getRetiroGs().intValue());
                for (int i = 18; i > valorGsAper.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorGsAper);
                escpos.write("Reales R$: ");
                String valorRsAper = String.format("%.2f", retiroDto.getRetiroRs());
                for (int i = 21; i > valorRsAper.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorRsAper);
                escpos.write("Dolares D$: ");
                String valorDsAper = String.format("%.2f", retiroDto.getRetiroDs());
                for (int i = 20; i > valorDsAper.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorDsAper);
                escpos.writeLF("--------------------------------");
                escpos.feed(4);
                escpos.writeLF(center, ".......................");
                escpos.writeLF(center, "FIRMA RESPONSABLE");
                if (retiroDto.getResponsable().getPersona().getNombre().length() > 23) {
                    escpos.writeLF(center, retiroDto.getResponsable().getPersona().getNombre().substring(0, 23));
                } else {
                    escpos.writeLF(center, retiroDto.getResponsable().getPersona().getNombre());
                }
                escpos.feed(5);
                escpos.close();
                printerOutputStream.close();
            }
        } catch (IOException e) {

        }
    }

    public String imprimirTransferencia(Transferencia transferencia, List<TransferenciaItem> transferenciaItemList,
            Boolean ticket, String printerName) {
        if (ticket != null && ticket == true) {
            try {
                selectedPrintService = printingService.getPrintService(printerName);
                if (selectedPrintService != null) {
                    printerOutputStream = new PrinterOutputStream(selectedPrintService);
                    // creating the EscPosImage, need buffered image and algorithm.
                    // Styles
                    Style center = new Style().setJustification(EscPosConst.Justification.Center);

                    QRCode qrCode = new QRCode();

                    BufferedImage imageBufferedImage = ImageIO.read(new File(imageService.getImagePath() + "logo.png"));
                    imageBufferedImage = resize(imageBufferedImage, 200, 100);
                    BitImageWrapper imageWrapper = new BitImageWrapper();
                    EscPos escpos = new EscPos(printerOutputStream);
                    Bitonal algorithm = new BitonalThreshold();
                    EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
                    imageWrapper.setJustification(EscPosConst.Justification.Center);
                    escpos.writeLF("--------------------------------");
                    escpos.write(imageWrapper, escposImage);
                    String qrData = "frc-0-TRF-" + transferencia.getId() + "-" + transferencia.getId()
                            + "undefined-undefined-undefined";
                    escpos.write(qrCode.setSize(7).setJustification(EscPosConst.Justification.Center), qrData);
                    escpos.feed(2);
                    escpos.writeLF("Fecha: " + transferencia.getCreadoEn().format(formatter));
                    escpos.writeLF("Suc. Origen: " + transferencia.getSucursalOrigen().getNombre());
                    escpos.writeLF("Suc. Destino: " + transferencia.getSucursalDestino().getNombre());
                    escpos.writeLF(
                            "Creado por: " + transferencia.getUsuarioPreTransferencia().getPersona().getNombre());
                    escpos.feed(5);

                    escpos.writeLF(center, "----------------------");
                    escpos.writeLF(center, "Resp. Creacion");
                    escpos.feed(5);

                    escpos.writeLF(center, "----------------------");
                    escpos.writeLF(center, "Resp. Preparacion");
                    escpos.feed(5);

                    escpos.writeLF(center, "----------------------");
                    escpos.writeLF(center, "Resp. Transporte");
                    escpos.feed(5);

                    escpos.writeLF(center, "----------------------");
                    escpos.writeLF(center, "Resp. Recepcion");
                    escpos.feed(5);

                    escpos.close();
                    printerOutputStream.close();
                }
            } catch (IOException e) {

            }
            return null;
        } else {
            try {
                List<TransferenciaItemDto> transferenciaItemDtoList = new ArrayList<>();
                for (int i = 0; i < transferenciaItemList.size(); i++) {
                    TransferenciaItem ti = transferenciaItemList.get(i);
                    TransferenciaItemDto tiDto = new TransferenciaItemDto();
                    tiDto.setCantidad(ti.getCantidadPreTransferencia());
                    Codigo codigo = codigoService
                            .findPrincipalByPresentacionId(ti.getPresentacionPreTransferencia().getId());
                    tiDto.setCodBarra(codigo != null ? codigo.getCodigo() : "");
                    tiDto.setDescripcion(
                            i + 1 + " - " + ti.getPresentacionPreTransferencia().getProducto().getDescripcion());
                    PrecioPorSucursal precio = precioPorSucursalService
                            .findPrincipalByPrecionacionId(ti.getPresentacionPreTransferencia().getId());
                    tiDto.setPrecio(precio != null ? precio.getPrecio() : null);
                    tiDto.setPresentacion(ti.getPresentacionPreTransferencia().getCantidad());
                    if (ti.getVencimientoPreTransferencia() != null) {
                        tiDto.setVencimiento(DateUtils.toStringOnlyDate(ti.getVencimientoPreTransferencia()));
                    }
                    transferenciaItemDtoList.add(tiDto);
                }
                // file = ResourceUtils.getFile("classpath:reports/transferencia.jrxml");
                ClassPathResource resource = new ClassPathResource("reports/transferencia.jrxml");
                InputStream inputStream = resource.getInputStream();
                JasperReport jasperReport = JasperCompileManager.compileReport(inputStream);
                JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(transferenciaItemDtoList);
                Map<String, Object> parameters = new HashMap<>();
                parameters.put("idTransferencia", transferencia.getId());
                parameters.put("qr",
                        "frc-" + transferencia.getSucursalOrigen().getId().toString() + "-TRF-" + transferencia.getId()
                                + "-" + transferencia.getSucursalOrigen().getId().toString()
                                + "-EditTransferenciaComponent-null-null");
                parameters.put("sucursalOrigen", transferencia.getSucursalOrigen().getId() + " - "
                        + transferencia.getSucursalOrigen().getNombre());
                parameters.put("sucursalDestino", transferencia.getSucursalDestino().getId() + " - "
                        + transferencia.getSucursalDestino().getNombre());
                parameters.put("fechaReporte", DateUtils.toString(LocalDateTime.now()));
                parameters.put("responsable", transferencia.getUsuarioPreTransferencia().getNickname());
                parameters.put("usuario", transferencia.getUsuarioPreTransferencia().getNickname());
                parameters.put("creadoEn", DateUtils.toString(transferencia.getCreadoEn()));
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
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

    }

    public String imprimirReporteCobroVentaCredito(Cliente cliente, List<VentaCredito> ventaCreditoList,
            Double totalCobrado, Usuario usuario, Boolean ticket, String printerName) {
        if (ticket != null && ticket == true) {
            // try {
            // selectedPrintService = printingService.getPrintService(printerName);
            // if (selectedPrintService != null) {
            // printerOutputStream = new PrinterOutputStream(selectedPrintService);
            // // creating the EscPosImage, need buffered image and algorithm.
            // //Styles
            // Style center = new
            // Style().setJustification(EscPosConst.Justification.Center);
            //
            // QRCode qrCode = new QRCode();
            //
            // BufferedImage imageBufferedImage = ImageIO.read(new
            // File(imageService.getImagePath() + "logo.png"));
            // imageBufferedImage = resize(imageBufferedImage, 200, 100);
            // BitImageWrapper imageWrapper = new BitImageWrapper();
            // EscPos escpos = new EscPos(printerOutputStream);
            // Bitonal algorithm = new BitonalThreshold();
            // EscPosImage escposImage = new EscPosImage(new
            // CoffeeImageImpl(imageBufferedImage), algorithm);
            // imageWrapper.setJustification(EscPosConst.Justification.Center);
            // escpos.writeLF("--------------------------------");
            // escpos.write(imageWrapper, escposImage);
            // String qrData = "frc-0-TRF-" + transferencia.getId() + "-" +
            // transferencia.getId() + "undefined-undefined-undefined";
            // escpos.write(qrCode.setSize(7).setJustification(EscPosConst.Justification.Center),
            // qrData);
            // escpos.feed(2);
            // escpos.writeLF("Fecha: " + transferencia.getCreadoEn().format(formatter));
            // escpos.writeLF("Suc. Origen: " +
            // transferencia.getSucursalOrigen().getNombre());
            // escpos.writeLF("Suc. Destino: " +
            // transferencia.getSucursalDestino().getNombre());
            // escpos.writeLF("Creado por: " +
            // transferencia.getUsuarioPreTransferencia().getPersona().getNombre());
            // escpos.feed(5);
            //
            // escpos.writeLF(center, "----------------------");
            // escpos.writeLF(center, "Resp. Creacion");
            // escpos.feed(5);
            //
            // escpos.writeLF(center, "----------------------");
            // escpos.writeLF(center, "Resp. Preparacion");
            // escpos.feed(5);
            //
            // escpos.writeLF(center, "----------------------");
            // escpos.writeLF(center, "Resp. Transporte");
            // escpos.feed(5);
            //
            // escpos.writeLF(center, "----------------------");
            // escpos.writeLF(center, "Resp. Recepcion");
            // escpos.feed(5);
            //
            // escpos.close();
            // printerOutputStream.close();
            // }
            // } catch (IOException e) {
            //
            // }
            return null;
        } else {
            try {
                List<VentaCreditoItemDto> ventaCreditoItemDtoList = new ArrayList<>();
                String sucursalCliente = getSucursalDelCliente(cliente);
                for (VentaCredito ti : ventaCreditoList) {
                    VentaCreditoItemDto tiDto = new VentaCreditoItemDto();
                    Sucursal sucursal = sucursalService.findById(ti.getSucursalId()).orElse(null);
                    tiDto.setSucursal(sucursal.getNombre());
                    tiDto.setTotalGs(ti.getValorTotal());
                    tiDto.setVentaCreditoId(String.valueOf(ti.getId()));
                    tiDto.setVentaId(String.valueOf(ti.getVenta().getId()));
                    tiDto.setCreadoEn(DateUtils.toString(ti.getCreadoEn()));
                    tiDto.setNombreCliente(cliente.getPersona().getNombre().toUpperCase());
                    tiDto.setDocumentoCliente(cliente.getPersona().getDocumento());
                    tiDto.setDireccionCliente(cliente.getPersona().getDireccion());
                    tiDto.setSucursalCliente(sucursalCliente);
                    ventaCreditoItemDtoList.add(tiDto);
                }
                // file =
                // ResourceUtils.getFile("classpath:reports/reporte-cobro-venta-credito.jrxml");
                ClassPathResource resource = new ClassPathResource("reports/reporte-cobro-venta-credito.jrxml");
                InputStream inputStream = resource.getInputStream();
                JasperReport jasperReport = JasperCompileManager.compileReport(inputStream);
                JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(ventaCreditoItemDtoList);
                Map<String, Object> parameters = new HashMap<>();
                parameters.put("documento", cliente.getPersona().getDocumento());
                parameters.put("totalCobrado", totalCobrado);
                parameters.put("nombreCliente", cliente.getPersona().getNombre().toUpperCase());
                parameters.put("fechaReporte", DateUtils.toString(LocalDateTime.now()));
                parameters.put("usuario", usuario.getNickname());
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
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

    }

    public String imprimirReporteLucroPorProducto(List<LucroPorProductosDto> lucroPorProductosDtoList,
            String fechaInicio, String fechaFin, String sucursales, String filtro, Usuario usuario) {
        Long cantProductos = Long.valueOf(0);
        Double lucroTotalPorcentaje = 0.0;
        Double lucroTotalGs = 0.0;
        Double costoTotal = 0.0;
        Double ventaTotal = 0.0;
        Double descuentoTotal = 0.0;
        Double aumentoTotal = 0.0;
        List<LucroPorProductosDto> auxList = new ArrayList<>();
        try {
            for (LucroPorProductosDto dto : lucroPorProductosDtoList) {
                // Los cálculos ya están hechos correctamente en ProductoService
                // Solo sumamos los totales para el resumen
                lucroTotalGs += dto.getLucro();
                costoTotal += dto.getCostoTotal();
                ventaTotal += dto.getTotalVenta();
                descuentoTotal += (dto.getTotalDescuento() != null ? dto.getTotalDescuento() : 0.0);
                aumentoTotal += (dto.getTotalAumento() != null ? dto.getTotalAumento() : 0.0);
                auxList.add(dto);
            }
            cantProductos = Long.valueOf(lucroPorProductosDtoList.size());
            lucroTotalPorcentaje = ventaTotal > 0 ? ((lucroTotalGs) / ventaTotal) * 100 : 0.0;
            ClassPathResource resource = new ClassPathResource("reports/lucro-por-producto.jrxml");
            InputStream inputStream = resource.getInputStream();
            JasperReport jasperReport = JasperCompileManager.compileReport(inputStream);
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(auxList);
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("filtroFechaInicio", fechaInicio);
            parameters.put("filtroFechaFin", fechaFin);
            parameters.put("filtroTexto", filtro);
            parameters.put("filtroSucursales", sucursales);
            parameters.put("cantProductos", cantProductos);
            parameters.put("lucroTotalPorcentaje", lucroTotalPorcentaje);
            parameters.put("lucroTotalGs", lucroTotalGs);
            parameters.put("costoTotal", costoTotal);
            parameters.put("ventaTotal", ventaTotal);
            parameters.put("descuentoTotal", descuentoTotal);
            parameters.put("aumentoTotal", aumentoTotal);
            parameters.put("fechaReporte", DateUtils.toString(LocalDateTime.now()));
            parameters.put("usuario", usuario.getNickname());
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
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String imprimirReporteLucroPorFuncionario(List<LucroPorFuncionarioDto> lucroPorFuncionarioDtoList,
            String fechaInicio, String fechaFin, String sucursales, String filtro, Usuario usuario) {
        Long cantFuncionarios = Long.valueOf(0);
        Double lucroTotalPorcentaje = 0.0;
        Double lucroTotalGs = 0.0;
        Double costoTotal = 0.0;
        Double ventaTotal = 0.0;
        Double descuentoTotal = 0.0;
        Double aumentoTotal = 0.0;
        List<LucroPorFuncionarioDto> auxList = new ArrayList<>();
        try {
            for (LucroPorFuncionarioDto dto : lucroPorFuncionarioDtoList) {
                lucroTotalGs += dto.getLucro();
                costoTotal += dto.getCostoTotal();
                ventaTotal += dto.getTotalVenta();
                descuentoTotal += (dto.getTotalDescuento() != null ? dto.getTotalDescuento() : 0.0);
                aumentoTotal += (dto.getTotalAumento() != null ? dto.getTotalAumento() : 0.0);
                auxList.add(dto);
            }
            cantFuncionarios = Long.valueOf(lucroPorFuncionarioDtoList.size());
            lucroTotalPorcentaje = ventaTotal > 0 ? ((lucroTotalGs) / ventaTotal) * 100 : 0.0;
            ClassPathResource resource = new ClassPathResource("reports/lucro-por-funcionario.jrxml");
            InputStream inputStream = resource.getInputStream();
            JasperReport jasperReport = JasperCompileManager.compileReport(inputStream);
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(auxList);
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("filtroFechaInicio", fechaInicio);
            parameters.put("filtroFechaFin", fechaFin);
            parameters.put("filtroTexto", filtro);
            parameters.put("filtroSucursales", sucursales);
            parameters.put("cantFuncionarios", cantFuncionarios);
            parameters.put("lucroTotalPorcentaje", lucroTotalPorcentaje);
            parameters.put("lucroTotalGs", lucroTotalGs);
            parameters.put("costoTotal", costoTotal);
            parameters.put("ventaTotal", ventaTotal);
            parameters.put("descuentoTotal", descuentoTotal);
            parameters.put("aumentoTotal", aumentoTotal);
            parameters.put("fechaReporte", DateUtils.toString(LocalDateTime.now()));
            parameters.put("usuario", usuario.getNickname());
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
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String imprimirReporteCobroVentaCreditoMultiplesClientes(List<Cliente> clientesList,
            Map<Long, List<VentaCredito>> ventaCreditoMap, Usuario usuario) {
        try {
            List<VentaCreditoItemDto> ventaCreditoItemDtoList = new ArrayList<>();
            Double totalGeneral = 0.0;

            for (Cliente cliente : clientesList) {
                List<VentaCredito> ventaCreditoList = ventaCreditoMap.get(cliente.getId());
                if (ventaCreditoList != null && !ventaCreditoList.isEmpty()) {
                    Double totalCliente = 0.0;
                    String sucursalCliente = getSucursalDelCliente(cliente);
                    for (VentaCredito ti : ventaCreditoList) {
                        VentaCreditoItemDto tiDto = new VentaCreditoItemDto();
                        Sucursal sucursal = sucursalService.findById(ti.getSucursalId()).orElse(null);
                        tiDto.setSucursal(sucursal != null ? sucursal.getNombre() : "");
                        tiDto.setTotalGs(ti.getValorTotal());
                        tiDto.setVentaCreditoId(String.valueOf(ti.getId()));
                        tiDto.setVentaId(String.valueOf(ti.getVenta().getId()));
                        tiDto.setCreadoEn(DateUtils.toString(ti.getCreadoEn()));
                        tiDto.setNombreCliente(cliente.getPersona().getNombre().toUpperCase());
                        tiDto.setDocumentoCliente(cliente.getPersona().getDocumento());
                        tiDto.setDireccionCliente(cliente.getPersona().getDireccion());
                        tiDto.setSucursalCliente(sucursalCliente);
                        ventaCreditoItemDtoList.add(tiDto);
                        totalCliente += ti.getValorTotal();
                        totalGeneral += ti.getValorTotal();
                    }
                }
            }

            ClassPathResource resource = new ClassPathResource("reports/reporte-cobro-venta-credito.jrxml");
            InputStream inputStream = resource.getInputStream();
            JasperReport jasperReport = JasperCompileManager.compileReport(inputStream);
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(ventaCreditoItemDtoList);
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("documento", ""); // No se usa en el título, cada cliente tiene su propio encabezado
            parameters.put("totalCobrado", totalGeneral);
            parameters.put("nombreCliente", ""); // No se usa en el título, cada cliente tiene su propio encabezado
            parameters.put("fechaReporte", DateUtils.toString(LocalDateTime.now()));
            parameters.put("usuario", usuario.getNickname());
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
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String imprimirMarcaciones(List<Jornada> jornadaList, String fechaInicio, String fechaFin,
            Usuario usuario) {
        try {
            List<MarcacionItemDto> marcacionItemDtoList = new ArrayList<>();
            for (Jornada jornada : jornadaList) {
                MarcacionItemDto dto = new MarcacionItemDto();
                dto.setId(jornada.getId());
                String nickname = jornada.getUsuario() != null && jornada.getUsuario().getNickname() != null
                        ? jornada.getUsuario().getNickname()
                        : "";
                dto.setUsuario(nickname);

                if (jornada.getMarcacionEntrada() != null) {
                    Marcacion ent = jornada.getMarcacionEntrada();
                    Sucursal sucEnt = ent.getSucursalEntrada() != null ? ent.getSucursalEntrada()
                            : ent.getSucursalSalida();
                    dto.setSucursalEntrada(sucEnt != null ? sucEnt.getNombre() : "");

                    LocalDateTime fEnt = ent.getFechaEntrada() != null ? ent.getFechaEntrada() : ent.getFechaSalida();
                    dto.setFechaEntrada(fEnt != null ? DateUtils.toString(fEnt) : "");
                } else {
                    dto.setSucursalEntrada("");
                    dto.setFechaEntrada("");
                }

                if (jornada.getMarcacionSalida() != null) {
                    Marcacion sal = jornada.getMarcacionSalida();
                    Sucursal sucSal = sal.getSucursalSalida() != null ? sal.getSucursalSalida()
                            : sal.getSucursalEntrada();
                    dto.setSucursalSalida(sucSal != null ? sucSal.getNombre() : "");

                    LocalDateTime fSal = sal.getFechaSalida() != null ? sal.getFechaSalida() : sal.getFechaEntrada();
                    dto.setFechaSalida(fSal != null ? DateUtils.toString(fSal) : "");
                } else {
                    dto.setSucursalSalida(null);
                    dto.setFechaSalida(null);
                }

                dto.setLlegadaTardia(formatMinutes(jornada.getMinutosLlegadaTardia()));
                dto.setHoraExtra(formatMinutes(jornada.getMinutosExtras()));
                dto.setTurno(jornada.getTurno() != null ? jornada.getTurno().toString() : "");

                marcacionItemDtoList.add(dto);
            }
            JasperReport jasperReport = compileReportFromClasspath("reports/marcaciones.jrxml");
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(marcacionItemDtoList);
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("fechaInicio", fechaInicio != null ? fechaInicio : "");
            parameters.put("fechaFin", fechaFin != null ? fechaFin : "");
            parameters.put("fechaReporte", DateUtils.toString(LocalDateTime.now()));
            parameters.put("usuario",
                    usuario != null && usuario.getPersona() != null ? usuario.getPersona().getNombre() : "");
            parameters.put("logo", imageService.getImagePath() + File.separator + "logo.png");
            JasperPrint jasperPrint1 = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
            byte[] pdfBytes = JasperExportManager.exportReportToPdf(jasperPrint1);
            String base64String = Base64.getEncoder().encodeToString(pdfBytes);
            return base64String;
        } catch (JRException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void imprimirCodigoDeBarra(Codigo codigo) {
        try {
            selectedPrintService = printingService.getPrintService("adesivo");
            if (selectedPrintService != null) {
                printerOutputStream = new PrinterOutputStream(selectedPrintService);
                // creating the EscPosImage, need buffered image and algorithm.
                // Styles
                Style center = new Style().setJustification(EscPosConst.Justification.Center);

                QRCode qrCode = new QRCode();

                BufferedImage imageBufferedImage = ImageIO.read(new File(imageService.getImagePath() + "logo.png"));
                imageBufferedImage = resize(imageBufferedImage, 200, 100);
                RasterBitImageWrapper imageWrapper = new RasterBitImageWrapper();
                EscPos escpos = new EscPos(printerOutputStream);
                Bitonal algorithm = new BitonalThreshold();
                EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
                imageWrapper.setJustification(EscPosConst.Justification.Center);
                escpos.writeLF("Hola soy un codigo de barra");
                // escpos.write(imageWrapper, escposImage);
                // if (local != null) {
                // escpos.writeLF(center, "Local: " + local);
                // }
                // escpos.writeLF(center.setBold(true), "Gasto: " + gastoDto.getId());
                // escpos.writeLF(center.setBold(true), "Caja: " + gastoDto.getCajaId());
                // if (gastoDto.getUsuario().getPersona().getNombre().length() > 23) {
                // escpos.writeLF("Cajero: " +
                // gastoDto.getUsuario().getPersona().getNombre().substring(0, 23));
                // } else {
                // escpos.writeLF("Cajero: " + gastoDto.getUsuario().getPersona().getNombre());
                // }
                // escpos.writeLF("Fecha " + gastoDto.getFecha().format(formatter));
                // escpos.writeLF(new Style().setBold(true), "Tipo " +
                // gastoDto.getTipoGasto().getId() + " - " +
                // gastoDto.getTipoGasto().getDescripcion().toUpperCase());
                // if (gastoDto.getObservacion() != null) {
                // escpos.writeLF("Obs: " + gastoDto.getObservacion().toUpperCase());
                // }
                // escpos.writeLF("--------------------------------");
                // escpos.writeLF(center, "VALORES DE GASTO");
                // escpos.write("Guaranies G$: ");
                // String valorGsAper =
                // NumberFormat.getNumberInstance(Locale.GERMAN).format(gastoDto.getRetiroGs().intValue());
                // for (int i = 18; i > valorGsAper.length(); i--) {
                // escpos.write(" ");
                // }
                // escpos.writeLF(valorGsAper);
                // escpos.write("Reales R$: ");
                // String valorRsAper = String.format("%.2f", gastoDto.getRetiroRs());
                // for (int i = 21; i > valorRsAper.length(); i--) {
                // escpos.write(" ");
                // }
                // escpos.writeLF(valorRsAper);
                // escpos.write("Dolares D$: ");
                // String valorDsAper = String.format("%.2f", gastoDto.getRetiroDs());
                // for (int i = 20; i > valorDsAper.length(); i--) {
                // escpos.write(" ");
                // }
                // escpos.writeLF(valorDsAper);
                // escpos.writeLF("--------------------------------");
                // escpos.feed(4);
                // escpos.writeLF(center, ".......................");
                // escpos.writeLF(center, "FIRMA RESPONSABLE");
                // if (gastoDto.getResponsable().getPersona().getNombre().length() > 23) {
                // escpos.writeLF(center,
                // gastoDto.getResponsable().getPersona().getNombre().substring(0, 23));
                // } else {
                // escpos.writeLF(center, gastoDto.getResponsable().getPersona().getNombre());
                // }
                // if (gastoDto.getAutorizadoPor() != null) {
                // escpos.writeLF("--------------------------------");
                // escpos.feed(4);
                // escpos.writeLF(center, ".......................");
                // escpos.writeLF(center, "AUTORIZACION");
                // if (gastoDto.getAutorizadoPor().getPersona().getNombre().length() > 23) {
                // escpos.writeLF(center,
                // gastoDto.getAutorizadoPor().getPersona().getNombre().substring(0, 23));
                // } else {
                // escpos.writeLF(center, gastoDto.getAutorizadoPor().getPersona().getNombre());
                // }
                // }
                escpos.feed(5);
                escpos.close();
                printerOutputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public class TransferenciaItemDto {
        private String descripcion;
        private String codBarra;
        private Double presentacion;
        private Double cantidad;
        private String vencimiento;
        private Double precio;
    }

    /**
     * Nombre de la sucursal a la que pertenece el cliente. La mayoria de las
     * ventas a credito son de funcionarios, asi que la sucursal se toma de su
     * ficha en personas.funcionario (sucursal_id), buscada por la persona del
     * cliente. Un cliente que no es funcionario no tiene sucursal: devuelve "".
     */
    private String getSucursalDelCliente(Cliente cliente) {
        if (cliente == null || cliente.getPersona() == null || cliente.getPersona().getId() == null)
            return "";
        Funcionario funcionario = funcionarioService.findByPersonaId(cliente.getPersona().getId());
        if (funcionario == null || funcionario.getSucursal() == null)
            return "";
        return sucursalService.findById(funcionario.getSucursal().getId()).map(Sucursal::getNombre).orElse("");
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public class VentaCreditoItemDto {
        private String ventaId;
        private String sucursal;
        private String ventaCreditoId;
        private Double totalGs;
        private String creadoEn;
        private String nombreCliente;
        private String documentoCliente;
        private String direccionCliente;
        private String sucursalCliente;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public class MarcacionItemDto {
        private Long id;
        private String usuario;
        private String sucursalEntrada;
        private String fechaEntrada;
        private String sucursalSalida;
        private String fechaSalida;
        private String llegadaTardia;
        private String horaExtra;
        private String turno;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public class VentaTarjetaItemDto {
        private Long id;
        private Long ventaId;
        private String terminal;
        private String codigo;
        private String sucursal;
        private java.math.BigDecimal monto;
        private String montoFormateado;
        private String montoEscaneado;
        private String estado;
        private String creadoEn;
        private String simboloMoneda;
    }

    public String imprimirReporteVentaTarjeta(List<com.franco.dev.domain.financiero.VentaTarjeta> ventaTarjetaList,
            String sucursalFiltro, String terminalFiltro, String estadoFiltro,
            String fechaDesde, String fechaHasta, Usuario usuario) {
        try {
            List<VentaTarjetaItemDto> itemList = new ArrayList<>();
            for (com.franco.dev.domain.financiero.VentaTarjeta vt : ventaTarjetaList) {
                VentaTarjetaItemDto dto = new VentaTarjetaItemDto();
                dto.setId(vt.getId());
                dto.setVentaId(vt.getVenta() != null ? vt.getVenta().getId() : null);
                dto.setTerminal(vt.getTerminalPos() != null ? vt.getTerminalPos().getDescripcion() : "");
                dto.setCodigo(vt.getTerminalPos() != null ? vt.getTerminalPos().getCodigo() : "");
                dto.setSucursal(vt.getSucursal() != null ? vt.getSucursal().getNombre() : "");
                dto.setMonto(vt.getMonto());
                dto.setMontoEscaneado(vt.getMontoEscaneado() != null
                        ? new java.text.DecimalFormat("#,##0").format(vt.getMontoEscaneado())
                        : "-");
                dto.setEstado(vt.getEstado());
                dto.setCreadoEn(vt.getCreadoEn() != null ? DateUtils.toString(vt.getCreadoEn()) : "");
                String simbolo = (vt.getTerminalPos() != null && vt.getTerminalPos().getMoneda() != null)
                        ? vt.getTerminalPos().getMoneda().getSimbolo() : "Gs.";
                dto.setSimboloMoneda(simbolo);
                dto.setMontoFormateado(formatMontoPorMoneda(vt.getMonto(), simbolo));
                itemList.add(dto);
            }
            itemList.sort(java.util.Comparator.comparing(VentaTarjetaItemDto::getSimboloMoneda));

            java.util.Map<String, java.math.BigDecimal> totalesPorMoneda = new java.util.LinkedHashMap<>();
            for (VentaTarjetaItemDto dto : itemList) {
                totalesPorMoneda.merge(dto.getSimboloMoneda(), dto.getMonto() != null ? dto.getMonto() : java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            }
            String totalPorMoneda = "Cantidad de ventas: " + itemList.size() + "\n"
                    + totalesPorMoneda.entrySet().stream()
                            .map(e -> "Total " + e.getKey() + ": " + formatMontoPorMoneda(e.getValue(), e.getKey()))
                            .collect(java.util.stream.Collectors.joining("\n"));
            JasperReport jasperReport = compileReportFromClasspath("reports/venta-tarjeta.jrxml");
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(itemList);
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("sucursalFiltro", sucursalFiltro != null ? sucursalFiltro : "Todas");
            parameters.put("terminalFiltro", terminalFiltro != null ? terminalFiltro : "Todas");
            parameters.put("estadoFiltro", estadoFiltro != null ? estadoFiltro : "Todos");
            parameters.put("fechaDesde", fechaDesde != null ? fechaDesde : "");
            parameters.put("fechaHasta", fechaHasta != null ? fechaHasta : "");
            parameters.put("fechaReporte", DateUtils.toString(LocalDateTime.now()));
            parameters.put("usuario",
                    usuario != null && usuario.getPersona() != null ? usuario.getPersona().getNombre() : "");
            parameters.put("totalPorMoneda", totalPorMoneda);
            parameters.put("logo", imageService.getImagePath() + File.separator + "logo.png");
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
            byte[] pdfBytes = JasperExportManager.exportReportToPdf(jasperPrint);
            return Base64.getEncoder().encodeToString(pdfBytes);
        } catch (JRException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String imprimirSolicitudPagoPDF(
            SolicitudPago solicitudPago,
            String proveedorNombre,
            String observaciones,
            String numerosFactura,
            Double valorTotal,
            java.util.List<SolicitudPagoNotaRowDto> notas,
            java.util.List<SolicitudPagoFormaPagoDetalleDto> detalleFormasPago) {

        try {
            if (numerosFactura == null || numerosFactura.isEmpty()) {
                numerosFactura = "---";
            }
            if (observaciones == null) {
                observaciones = "";
            }
            if (notas == null) {
                notas = new ArrayList<>();
            }
            if (detalleFormasPago == null) {
                detalleFormasPago = new ArrayList<>();
            }

            // Usuario: ya viene cargado con findByIdWithUsuarioAndMoneda
            String usuario = "";
            if (solicitudPago.getUsuario() != null) {
                if (solicitudPago.getUsuario().getPersona() != null
                        && solicitudPago.getUsuario().getPersona().getNombre() != null) {
                    usuario = solicitudPago.getUsuario().getPersona().getNombre();
                } else if (solicitudPago.getUsuario().getNickname() != null) {
                    usuario = solicitudPago.getUsuario().getNickname();
                }
            }

            JasperReport jasperReport = compileReportFromClasspath("reports/solicitud-pago.jrxml");
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(new ArrayList<>());

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("solicitudPagoId", solicitudPago.getId());
            parameters.put("proveedorNombre", proveedorNombre != null ? proveedorNombre : "");
            parameters.put("observaciones", observaciones != null ? observaciones : "");
            parameters.put("numerosFactura", numerosFactura);
            parameters.put("valorTotal", valorTotal != null ? valorTotal : 0.0);
            parameters.put("monedaSimbolo",
                    solicitudPago.getMoneda() != null && solicitudPago.getMoneda().getSimbolo() != null
                            ? solicitudPago.getMoneda().getSimbolo()
                            : "");
            String estadoStr = solicitudPago.getEstado() != null ? solicitudPago.getEstado().toString() : "";
            parameters.put("estado", estadoStr);
            parameters.put("fechaReporte", DateUtils.toString(LocalDateTime.now()));
            parameters.put("usuario", usuario);
            parameters.put("logo", imageService.getImagePath() + File.separator + "logo.png");
            // QR mismo formato que transferencia:
            // frc-{sucursalId}-{tipoEntidad}-{idOrigen}-{idCentral}-{componentToOpen}-null-null
            String qrText = "frc-0-SOLPAG-" + solicitudPago.getId() + "-" + solicitudPago.getId()
                    + "-ListSolicitudPagoComponent-null-null";
            parameters.put("qrText", qrText);
            parameters.put("notasSubreport",
                    compileReportFromClasspath("reports/solicitud-pago-notas-subreport.jrxml"));
            parameters.put("notasDataSource", new JRBeanCollectionDataSource(notas));
            parameters.put("formasSubreport",
                    compileReportFromClasspath("reports/solicitud-pago-formas-subreport.jrxml"));
            parameters.put("formasDataSource", new JRBeanCollectionDataSource(detalleFormasPago.isEmpty()
                    ? java.util.Collections.singletonList(new SolicitudPagoFormaPagoDetalleDto("—", "",
                            "Sin formas de pago", 0.0, "—", "", "—", "—", false))
                    : detalleFormasPago));

            JasperPrint jasperPrint1 = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
            byte[] pdfBytes = JasperExportManager.exportReportToPdf(jasperPrint1);
            String base64String = Base64.getEncoder().encodeToString(pdfBytes);
            return base64String;

        } catch (JRException e) {
            log.error("Error al generar PDF de solicitud de pago: {}", e.getMessage(), e);
            throw new RuntimeException("Error al generar el PDF: " + e.getMessage(), e);
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public class SolicitudPagoItemDto {
        private String solicitudPagoId;
        private String proveedorNombre;
        private String fechaDePago;
        private String formaPago;
        private String observaciones;
        private String grupoId;
        private String estado;
        private String creadoEn;
        private String usuario;
    }

    /**
     * DTO for PDF subreport: one row per nota de recepción.
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SolicitudPagoNotaRowDto {
        private String numero;
        private String fecha;
        private String total;
    }

    /**
     * DTO for PDF report detail row: one per forma de pago (SolicitudPagoDetalle).
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SolicitudPagoFormaPagoDetalleDto {
        private String moneda;
        private String monedaSimbolo;
        private String formaPago;
        private Double valor;
        private String fechaPago;
        private String fechaEmisionCheque;
        private String nominal;
        private String diferido;
        private Boolean esCheque;
    }

    public String imprimirReporteGenericVentas(
            List<ReporteVentaItemDto> itemList,
            String filtroIdVenta,
            String filtroFechaInicio,
            String filtroFechaFin,
            String filtroSucursal,
            String filtroFormaPago,
            String filtroMoneda,
            String filtroEstado,
            String filtroCliente,
            String filtroModo,
            String filtroConObservacion,
            String filtroConDescuento,
            String filtroConAumento,
            Double totalGeneral,
            Double totalEfectivo,
            Double totalTarjeta,
            Double totalConvenio,
            Double totalTransferencia,
            Double totalOtros,
            Usuario usuario) {
        try {
            ClassPathResource resource = new ClassPathResource("reports/reporte-ventas.jrxml");
            InputStream inputStream = resource.getInputStream();
            JasperReport jasperReport = JasperCompileManager.compileReport(inputStream);
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(
                    itemList != null ? itemList : new ArrayList<>());
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("fechaReporte", DateUtils.toString(LocalDateTime.now()));
            parameters.put("usuario", usuario != null ? usuario.getNickname() : "");
            parameters.put("logo", imageService.getImagePath() + File.separator + "logo.png");
            parameters.put("filtroIdVenta", filtroIdVenta != null ? filtroIdVenta : "");
            parameters.put("filtroFechaInicio", filtroFechaInicio != null ? filtroFechaInicio : "");
            parameters.put("filtroFechaFin", filtroFechaFin != null ? filtroFechaFin : "");
            parameters.put("filtroSucursal", filtroSucursal != null ? filtroSucursal : "");
            parameters.put("filtroFormaPago", filtroFormaPago != null ? filtroFormaPago : "");
            parameters.put("filtroMoneda", filtroMoneda != null ? filtroMoneda : "");
            parameters.put("filtroEstado", filtroEstado != null ? filtroEstado : "");
            parameters.put("filtroCliente", filtroCliente != null ? filtroCliente : "");
            parameters.put("filtroModo", filtroModo != null ? filtroModo : "");
            parameters.put("filtroConObservacion", filtroConObservacion != null ? filtroConObservacion : "");
            parameters.put("filtroConDescuento", filtroConDescuento != null ? filtroConDescuento : "");
            parameters.put("filtroConAumento", filtroConAumento != null ? filtroConAumento : "");
            parameters.put("totalGeneral", totalGeneral != null ? totalGeneral : 0.0);
            parameters.put("totalEfectivo", totalEfectivo != null ? totalEfectivo : 0.0);
            parameters.put("totalTarjeta", totalTarjeta != null ? totalTarjeta : 0.0);
            parameters.put("totalConvenio", totalConvenio != null ? totalConvenio : 0.0);
            parameters.put("totalTransferencia", totalTransferencia != null ? totalTransferencia : 0.0);
            parameters.put("totalOtros", totalOtros != null ? totalOtros : 0.0);
            parameters.put("cantidadVentas", itemList != null ? (long) itemList.size() : 0L);
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
            byte[] pdfBytes = JasperExportManager.exportReportToPdf(jasperPrint);
            return Base64.getEncoder().encodeToString(pdfBytes);
        } catch (JRException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String imprimirReporteGenericVentasDetallado(
            List<ReporteVentaDetalladoDto> itemList,
            String filtroIdVenta,
            String filtroFechaInicio,
            String filtroFechaFin,
            String filtroSucursal,
            String filtroFormaPago,
            String filtroMoneda,
            String filtroEstado,
            String filtroCliente,
            String filtroModo,
            String filtroConObservacion,
            String filtroConDescuento,
            String filtroConAumento,
            Double totalGeneral,
            Double totalEfectivo,
            Double totalTarjeta,
            Double totalConvenio,
            Double totalTransferencia,
            Double totalOtros,
            Usuario usuario) {
        try {
            JasperReport jasperReport = compileReportFromClasspath("reports/reporte-ventas-detallado.jrxml");
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(
                    itemList != null ? itemList : new ArrayList<>());
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("fechaReporte", DateUtils.toString(LocalDateTime.now()));
            parameters.put("usuario", usuario != null ? usuario.getNickname() : "");
            parameters.put("logo", imageService.getImagePath() + File.separator + "logo.png");
            parameters.put("filtroIdVenta", filtroIdVenta != null ? filtroIdVenta : "");
            parameters.put("filtroFechaInicio", filtroFechaInicio != null ? filtroFechaInicio : "");
            parameters.put("filtroFechaFin", filtroFechaFin != null ? filtroFechaFin : "");
            parameters.put("filtroSucursal", filtroSucursal != null ? filtroSucursal : "");
            parameters.put("filtroFormaPago", filtroFormaPago != null ? filtroFormaPago : "");
            parameters.put("filtroMoneda", filtroMoneda != null ? filtroMoneda : "");
            parameters.put("filtroEstado", filtroEstado != null ? filtroEstado : "");
            parameters.put("filtroCliente", filtroCliente != null ? filtroCliente : "");
            parameters.put("filtroModo", filtroModo != null ? filtroModo : "");
            parameters.put("filtroConObservacion", filtroConObservacion != null ? filtroConObservacion : "");
            parameters.put("filtroConDescuento", filtroConDescuento != null ? filtroConDescuento : "");
            parameters.put("filtroConAumento", filtroConAumento != null ? filtroConAumento : "");
            parameters.put("totalGeneral", totalGeneral != null ? totalGeneral : 0.0);
            parameters.put("totalEfectivo", totalEfectivo != null ? totalEfectivo : 0.0);
            parameters.put("totalTarjeta", totalTarjeta != null ? totalTarjeta : 0.0);
            parameters.put("totalConvenio", totalConvenio != null ? totalConvenio : 0.0);
            parameters.put("totalTransferencia", totalTransferencia != null ? totalTransferencia : 0.0);
            parameters.put("totalOtros", totalOtros != null ? totalOtros : 0.0);
            parameters.put("cantidadVentas", itemList != null ? (long) itemList.size() : 0L);
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
            byte[] pdfBytes = JasperExportManager.exportReportToPdf(jasperPrint);
            return Base64.getEncoder().encodeToString(pdfBytes);
        } catch (JRException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String safeUpper(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.toUpperCase();
    }

    private String formatFecha(java.time.LocalDateTime fecha) {
        return fecha != null ? DateUtils.toString(fecha) : "---";
    }

    public String imprimirPreGasto(PreGasto preGasto) {
        log.info("Iniciando impresión de PreGasto ID: " + preGasto.getId());
        try {
            JasperReport jasperReport = compileReportFromClasspath("reports/pre-gasto.jrxml");
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(new ArrayList<>());
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("urgencia", safeUpper(preGasto.getNivelUrgencia(), "NORMAL"));
            parameters.put("observaciones", safeUpper(preGasto.getObservaciones(), "---"));

            parameters.put("idPreGasto", preGasto.getId());
            parameters.put("creadoEn", preGasto.getCreadoEn() != null ? DateUtils.toString(preGasto.getCreadoEn()) : "---");
            parameters.put("fechaReporte", DateUtils.toString(LocalDateTime.now()));
            parameters.put("sucursal", preGasto.getSucursalId() + " - "
                    + sucursalService.findById(preGasto.getSucursalId()).map(s -> s.getNombre()).orElse(""));
            parameters.put("sucursalCaja",
                    preGasto.getSucursalCaja() != null ? preGasto.getSucursalCaja().getNombre() : "SIN ASIGNAR");
            parameters.put("tipoGasto",
                    preGasto.getTipoGasto() != null ? preGasto.getTipoGasto().getDescripcion().toUpperCase() : "N/A");
            parameters.put("solicitante",
                    preGasto.getFuncionario() != null ? preGasto.getFuncionario().getNombre().toUpperCase() : "SIN ESPECIFICAR");
            parameters.put("autorizador",
                    preGasto.getAutorizadoPor() != null ? preGasto.getAutorizadoPor().getNombre().toUpperCase() : "PENDIENTE");
            String rawDesc = preGasto.getDescripcion() != null ? preGasto.getDescripcion() : "";
            String descripcionLimpia = rawDesc;
            if (rawDesc.contains(" | ")) {
                descripcionLimpia = rawDesc.split(" \\| ", 2)[0];
            }
            parameters.put("descripcion", safeUpper(descripcionLimpia, "---"));

            String simbolo = preGasto.getMoneda() != null ? preGasto.getMoneda().getSimbolo() : "GS";
            parameters.put("moneda", simbolo);

            log.info("Parámetros del reporte: " + parameters);
            parameters.put("monto", formatMonto(preGasto.getMontoSolicitado(), simbolo));
            parameters.put("fechaVencimiento", formatFecha(preGasto.getFechaVencimiento()));
            parameters.put("nivelUrgencia", safeUpper(preGasto.getNivelUrgencia(), "NORMAL"));

            String beneficiarioTipo = "SIN BENEFICIARIO";
            String beneficiarioNombre = "---";
            if (preGasto.getBeneficiarioPersona() != null) {
                beneficiarioTipo = "PERSONA";
                beneficiarioNombre = safeUpper(preGasto.getBeneficiarioPersona().getNombre(), "---");
            } else if (preGasto.getBeneficiarioProveedor() != null && preGasto.getBeneficiarioProveedor().getPersona() != null) {
                beneficiarioTipo = "PROVEEDOR";
                beneficiarioNombre = safeUpper(preGasto.getBeneficiarioProveedor().getPersona().getNombre(), "---");
            }
            parameters.put("beneficiarioTipo", beneficiarioTipo);
            parameters.put("beneficiarioNombre", beneficiarioNombre);

            String usuarioNick = "";
            if (preGasto.getUsuario() != null) {
                usuarioNick = preGasto.getUsuario().getNickname();
            }
            parameters.put("usuario", usuarioNick != null ? usuarioNick : "---");

            parameters.put("logo", imageService.getImagePath() + File.separator + "logo.png");

            Boolean isBien = preGasto.getEnte() != null;
            parameters.put("isBien", isBien);
            
            boolean isPagoCuota = false;
            if (preGasto.getTipoGasto() != null && Boolean.TRUE.equals(preGasto.getTipoGasto().getEsPagoCuotaActivo())) {
                isPagoCuota = true;
            } else if (rawDesc != null && rawDesc.toUpperCase().startsWith("PAGO -")) {
                isPagoCuota = true;
            }
            parameters.put("isPagoCuota", isPagoCuota);
            
            if (isBien) {
                Long refId = preGasto.getEnte().getReferenciaId();
                String bienNombre = "";
                String bienReferencia = "";
                java.math.BigDecimal montoTotal = java.math.BigDecimal.ZERO;
                java.math.BigDecimal montoPagado = java.math.BigDecimal.ZERO;
                Integer cuotasTotales = 0;
                Integer cuotasPagadas = 0;
                String proveedor = "";
                String situacion = "";
                String mnd = "";

                switch (preGasto.getEnte().getTipoEnte()) {
                    case VEHICULO:
                        com.franco.dev.domain.activos.Vehiculo v = vehiculoService.findById(refId).orElse(null);
                        if (v != null) {
                            bienNombre = "VEHICULO " + (v.getModelo() != null ? v.getModelo().getDescripcion() : "");
                            bienReferencia = "Chapa: " + v.getChapa() + " - Ref #" + v.getId() + " - Ente #"
                                    + preGasto.getEnte().getId();
                            montoTotal = v.getMontoTotal();
                            montoPagado = v.getMontoYaPagado();
                            cuotasTotales = v.getCantidadCuotas();
                            cuotasPagadas = v.getCantidadCuotasPagadas();
                            proveedor = (v.getProveedor() != null && v.getProveedor().getPersona() != null)
                                    ? v.getProveedor().getPersona().getNombre()
                                    : "";
                            situacion = v.getSituacionPago();
                            mnd = v.getMoneda() != null ? v.getMoneda().getSimbolo() : simbolo;
                        }
                        break;
                    case INMUEBLE:
                        com.franco.dev.domain.activos.Inmueble i = inmuebleService.findById(refId).orElse(null);
                        if (i != null) {
                            bienNombre = "INMUEBLE " + (i.getNombreAsignado() != null ? i.getNombreAsignado() : "");
                            bienReferencia = "Dirección: " + i.getDireccion() + " - Ref #" + i.getId() + " - Ente #"
                                    + preGasto.getEnte().getId();
                            montoTotal = i.getMontoTotal();
                            montoPagado = i.getMontoYaPagado();
                            cuotasTotales = i.getCantidadCuotas();
                            cuotasPagadas = i.getCantidadCuotasPagadas();
                            proveedor = (i.getProveedor() != null && i.getProveedor().getPersona() != null)
                                    ? i.getProveedor().getPersona().getNombre()
                                    : "";
                            situacion = i.getSituacionPago();
                            mnd = i.getMoneda() != null ? i.getMoneda().getSimbolo() : simbolo;
                        }
                        break;
                    case MUEBLE:
                        com.franco.dev.domain.activos.Mueble m = muebleService.findById(refId).orElse(null);
                        if (m != null) {
                            bienNombre = "MUEBLE " + (m.getIdentificador() != null ? m.getIdentificador() : "");
                            bienReferencia = m.getDescripcion() + " - Ref #" + m.getId() + " - Ente #"
                                    + preGasto.getEnte().getId();
                            montoTotal = m.getMontoTotal();
                            montoPagado = m.getMontoYaPagado();
                            cuotasTotales = m.getCantidadCuotas();
                            cuotasPagadas = m.getCantidadCuotasPagadas();
                            proveedor = (m.getProveedor() != null && m.getProveedor().getPersona() != null)
                                    ? m.getProveedor().getPersona().getNombre()
                                    : "";
                            situacion = m.getSituacionPago();
                            mnd = m.getMoneda() != null ? m.getMoneda().getSimbolo() : simbolo;
                        }
                        break;
                    case EQUIPO:
                        com.franco.dev.domain.equipos.Equipo eq = equipoService.findById(refId).orElse(null);
                        if (eq != null) {
                            com.franco.dev.domain.equipos.EquipoFinanciero fin = equipoService.resolverFinanciero(eq);
                            bienNombre = "EQUIPO " + (eq.getIdentificador() != null ? eq.getIdentificador() : "");
                            bienReferencia = (eq.getDescripcion() != null ? eq.getDescripcion() : "")
                                    + " - Ref #" + eq.getId() + " - Ente #" + preGasto.getEnte().getId();
                            if (fin != null) {
                                montoTotal = fin.getMontoTotal();
                                montoPagado = fin.getMontoYaPagado();
                                cuotasTotales = fin.getCantidadCuotas();
                                cuotasPagadas = fin.getCantidadCuotasPagadas();
                                proveedor = (fin.getProveedor() != null && fin.getProveedor().getPersona() != null)
                                        ? fin.getProveedor().getPersona().getNombre()
                                        : "";
                                situacion = fin.getSituacionPago();
                                mnd = fin.getMoneda() != null ? fin.getMoneda().getSimbolo() : simbolo;
                            }
                        }
                        break;
                    case INSTITUCION:
                        bienNombre = "INSTITUCION";
                        bienReferencia = "Ref #" + refId + " - Ente #" + preGasto.getEnte().getId();
                        break;
                }

                parameters.put("bienNombre", bienNombre.toUpperCase());
                parameters.put("bienReferencia", bienReferencia);
                parameters.put("bienMontoTotal", formatMonto(montoTotal, mnd));
                parameters.put("bienMontoPagado", formatMonto(montoPagado, mnd));
                java.math.BigDecimal pendiente = (montoTotal != null ? montoTotal : java.math.BigDecimal.ZERO)
                        .subtract(montoPagado != null ? montoPagado : java.math.BigDecimal.ZERO);
                parameters.put("bienSaldoPendiente", formatMonto(pendiente, mnd));
                parameters.put("bienCuotasTotales", cuotasTotales != null ? cuotasTotales : 0);
                parameters.put("bienCuotasPagadas", cuotasPagadas != null ? cuotasPagadas : 0);
                parameters.put("bienCuotasFaltantes",
                        (cuotasTotales != null ? cuotasTotales : 0) - (cuotasPagadas != null ? cuotasPagadas : 0));

                java.math.BigDecimal montoCuota = java.math.BigDecimal.ZERO;
                if (cuotasTotales != null && cuotasTotales > 0 && montoTotal != null) {
                    montoCuota = montoTotal.divide(java.math.BigDecimal.valueOf(cuotasTotales), 2,
                            java.math.RoundingMode.HALF_UP);
                }
                parameters.put("bienMontoCuota", formatMonto(montoCuota, mnd));
                parameters.put("bienCuotaActual", (cuotasPagadas != null ? cuotasPagadas : 0) + 1);
                parameters.put("bienProveedor", proveedor != null ? proveedor.toUpperCase() : "");
                parameters.put("bienSituacion", situacion != null ? situacion.toUpperCase() : "");

                String progreso = "0%";
                if (montoTotal != null && montoTotal.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    double p = (montoPagado.doubleValue() / montoTotal.doubleValue()) * 100;
                    progreso = String.format("%.0f%%", p);
                }
                parameters.put("bienProgreso", progreso);
            }
            String qrText = "frc-" + preGasto.getSucursalId() + "-PREGASTO-" + preGasto.getId() + "-" + preGasto.getId()
                    + "-AdicionarPreGastoComponent-null-null";
            parameters.put("qr", qrText);

            List<PreGastoDetalleFinanzas> finanzas = preGastoDetalleFinanzasService
                    .findByPreGastoIdAndSucursalId(preGasto.getId(), preGasto.getSucursalId());
            StringBuilder finanzasDetalle = new StringBuilder();
            String formaPagoPrincipal = "---";
            if (finanzas != null && !finanzas.isEmpty()) {
                for (PreGastoDetalleFinanzas f : finanzas) {
                    String monedaFin = f.getMoneda() != null && f.getMoneda().getSimbolo() != null
                            ? f.getMoneda().getSimbolo()
                            : simbolo;
                    String forma = safeUpper(f.getFormaPago(), "SIN METODO");
                    if ("---".equals(formaPagoPrincipal)) {
                        formaPagoPrincipal = forma;
                    }
                    finanzasDetalle
                            .append(forma)
                            .append(": ")
                            .append(monedaFin)
                            .append(" ")
                            .append(formatMonto(f.getMonto(), monedaFin))
                            .append("\n");
                }
            }
            parameters.put("formaPago", formaPagoPrincipal);
            parameters.put("finanzasDetalle",
                    finanzasDetalle.length() > 0 ? finanzasDetalle.toString().trim() : "SIN DETALLE DE FINANZAS");

            try {
                BufferedImage qrImage = com.franco.dev.utilitarios.print.QRCodeImageGenerator.generateQRCodeImage(qrText, 160, 160);
                File qrTmpFile = Files.createTempFile("pregasto-qr-" + preGasto.getId() + "-", ".png").toFile();
                qrTmpFile.deleteOnExit();
                ImageIO.write(qrImage, "png", qrTmpFile);
                parameters.put("qrImage", qrTmpFile.getAbsolutePath());
            } catch (WriterException | IOException qrEx) {
                log.warn("No se pudo generar QR para pre-gasto {}: {}", preGasto.getId(), qrEx.getMessage());
                parameters.put("qrImage", null);
            }

            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
            byte[] pdfBytes = JasperExportManager.exportReportToPdf(jasperPrint);
            return Base64.getEncoder().encodeToString(pdfBytes);

        } catch (JRException e) {
            log.error("Error al generar PDF de pre-gasto: {}", e.getMessage(), e);
            throw new RuntimeException("Error al generar el PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Formatea un monto segun su moneda: guaranies sin decimales (10.000),
     * el resto (reales, dolares, etc.) con dos decimales y coma decimal (10,00).
     */
    private String formatMontoPorMoneda(java.math.BigDecimal monto, String simbolo) {
        java.math.BigDecimal valor = monto != null ? monto : java.math.BigDecimal.ZERO;
        boolean esGuarani = simbolo != null
                && (simbolo.trim().equalsIgnoreCase("Gs") || simbolo.trim().equalsIgnoreCase("Gs."));
        java.text.DecimalFormatSymbols simbolos = new java.text.DecimalFormatSymbols(java.util.Locale.GERMANY);
        java.text.DecimalFormat fmt = new java.text.DecimalFormat(esGuarani ? "#,##0" : "#,##0.00", simbolos);
        return fmt.format(valor);
    }

    private String formatMonto(java.math.BigDecimal monto, String simbolo) {
        if (monto == null)
            return "";
        if (simbolo != null && (simbolo.equals("Gs") || simbolo.equals("Gs."))) {
            return java.text.NumberFormat.getNumberInstance(java.util.Locale.GERMAN).format(monto.longValue());
        } else {
            return String.format("%.2f", monto.doubleValue());

        }
    }

    private String formatMinutes(Long minutes) {
        if (minutes == null || minutes == 0) {
            return "00:00";
        }
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        return String.format("%02d:%02d", hours, remainingMinutes);
    }

    // ==================== PEDIDO DE COMPRA ====================

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PedidoItemDto {
        private Integer numero;
        private String descripcion;
        private String presentacion;
        private Double cantidadUnidades;
        private Double precioUnitario;
        private Double subtotal;
        private String vencimiento;
    }

    public String imprimirPedido(Pedido pedido, List<PedidoItem> items, Boolean ticket, String printerName) {
        if (ticket != null && ticket) {
            // ===== ESC/POS TICKET =====
            try {
                selectedPrintService = printingService.getPrintService(printerName);
                if (selectedPrintService != null) {
                    printerOutputStream = new PrinterOutputStream(selectedPrintService);
                    Style center = new Style().setJustification(EscPosConst.Justification.Center);
                    QRCode qrCode = new QRCode();

                    BufferedImage imageBufferedImage = ImageIO
                            .read(new File(imageService.getImagePath() + "logo.png"));
                    imageBufferedImage = resize(imageBufferedImage, 200, 100);
                    BitImageWrapper imageWrapper = new BitImageWrapper();
                    EscPos escpos = new EscPos(printerOutputStream);
                    Bitonal algorithm = new BitonalThreshold();
                    EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
                    imageWrapper.setJustification(EscPosConst.Justification.Center);
                    escpos.writeLF("--------------------------------");
                    escpos.write(imageWrapper, escposImage);
                    escpos.writeLF(center.setBold(true), "PEDIDO DE COMPRA");
                    escpos.writeLF("--------------------------------");
                    escpos.writeLF("Pedido Nro: " + pedido.getId());
                    escpos.writeLF("Fecha: " + (pedido.getCreadoEn() != null
                            ? pedido.getCreadoEn().format(formatter)
                            : ""));
                    String provNombre = pedido.getProveedor() != null
                            && pedido.getProveedor().getPersona() != null
                                    ? pedido.getProveedor().getPersona().getNombre()
                                    : "";
                    if (provNombre.length() > 28) {
                        provNombre = provNombre.substring(0, 28);
                    }
                    escpos.writeLF("Prov: " + provNombre);
                    String monedaStr = pedido.getMoneda() != null
                            && pedido.getMoneda().getDenominacion() != null
                                    ? pedido.getMoneda().getDenominacion()
                                    : "";
                    escpos.writeLF("Moneda: " + monedaStr);
                    escpos.writeLF("--------------------------------");

                    double totalPedido = 0;
                    for (int i = 0; i < items.size(); i++) {
                        PedidoItem item = items.get(i);
                        String desc = item.getProducto() != null ? item.getProducto().getDescripcion() : "";
                        if (desc.length() > 32) {
                            desc = desc.substring(0, 32);
                        }
                        escpos.writeLF((i + 1) + ". " + desc);

                        Double cant = item.getCantidadSolicitada() != null ? item.getCantidadSolicitada() : 0.0;
                        Double precio = item.getPrecioUnitarioSolicitado() != null
                                ? item.getPrecioUnitarioSolicitado()
                                : 0.0;
                        double sub = cant * precio;
                        totalPedido += sub;

                        String cantStr = String.format("%.0f", cant);
                        String precioStr = NumberFormat.getNumberInstance(Locale.GERMAN)
                                .format((long) Math.round(precio));
                        String subStr = NumberFormat.getNumberInstance(Locale.GERMAN)
                                .format((long) Math.round(sub));
                        escpos.writeLF("  " + cantStr + " x " + precioStr + " = " + subStr);

                        if (Boolean.TRUE.equals(item.getEsBonificacion())) {
                            escpos.writeLF("  ** BONIFICACION **");
                        }
                    }

                    escpos.writeLF("--------------------------------");
                    String totalStr = NumberFormat.getNumberInstance(Locale.GERMAN)
                            .format((long) Math.round(totalPedido));
                    escpos.writeLF(center.setBold(true), "TOTAL: " + totalStr);
                    escpos.writeLF("--------------------------------");

                    String qrData = "frc-0-PEDIDO-" + pedido.getId() + "-" + pedido.getId()
                            + "-GestionComprasComponent-null-null";
                    escpos.write(qrCode.setSize(7).setJustification(EscPosConst.Justification.Center), qrData);

                    escpos.feed(4);
                    escpos.writeLF(center, ".......................");
                    escpos.writeLF(center, "Comprador");
                    escpos.feed(5);
                    escpos.close();
                    printerOutputStream.close();
                }
            } catch (IOException e) {
                log.error("Error al imprimir ticket de pedido: {}", e.getMessage(), e);
            }
            return null;
        } else {
            // ===== JASPER PDF =====
            try {
                List<PedidoItemDto> dtoList = new ArrayList<>();
                double montoTotal = 0;
                for (int i = 0; i < items.size(); i++) {
                    PedidoItem item = items.get(i);
                    PedidoItemDto dto = new PedidoItemDto();
                    dto.setNumero(i + 1);

                    // Descripción + código de barras: "PRODUCTO (codBarra)"
                    String desc = item.getProducto() != null ? item.getProducto().getDescripcion() : "";
                    String codBarra = "";
                    if (item.getPresentacionCreacion() != null) {
                        Codigo codigo = codigoService
                                .findPrincipalByPresentacionId(item.getPresentacionCreacion().getId());
                        if (codigo != null && codigo.getCodigo() != null && !codigo.getCodigo().isEmpty()) {
                            codBarra = codigo.getCodigo();
                        }
                    }
                    if (!codBarra.isEmpty()) {
                        desc = desc + " (" + codBarra + ")";
                    }
                    dto.setDescripcion(desc);

                    // Presentación: "1 unid." o "Caja x N unid."
                    String pres = "";
                    if (item.getPresentacionCreacion() != null
                            && item.getPresentacionCreacion().getCantidad() != null) {
                        int cant = item.getPresentacionCreacion().getCantidad().intValue();
                        if (cant <= 1) {
                            pres = "1 unid.";
                        } else {
                            pres = "Caja x " + cant + " unid.";
                        }
                    }
                    dto.setPresentacion(pres);

                    Double cant = item.getCantidadSolicitada() != null ? item.getCantidadSolicitada() : 0.0;
                    Double precio = item.getPrecioUnitarioSolicitado() != null
                            ? item.getPrecioUnitarioSolicitado()
                            : 0.0;
                    double sub = cant * precio;
                    montoTotal += sub;

                    dto.setCantidadUnidades(cant);
                    dto.setPrecioUnitario(precio);
                    dto.setSubtotal(sub);
                    dto.setVencimiento(item.getVencimientoEsperado() != null
                            ? DateUtils.toStringOnlyDate(item.getVencimientoEsperado())
                            : "");
                    dtoList.add(dto);
                }

                JasperReport jasperReport = compileReportFromClasspath("reports/pedido-compra.jrxml");
                JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(dtoList);
                Map<String, Object> parameters = new HashMap<>();
                parameters.put("pedidoId", pedido.getId());
                parameters.put("proveedor", pedido.getProveedor() != null
                        && pedido.getProveedor().getPersona() != null
                                ? pedido.getProveedor().getPersona().getNombre()
                                : "");
                parameters.put("vendedor", pedido.getVendedor() != null
                        && pedido.getVendedor().getPersona() != null
                                ? pedido.getVendedor().getPersona().getNombre()
                                : "");
                parameters.put("moneda", pedido.getMoneda() != null
                        && pedido.getMoneda().getDenominacion() != null
                                ? pedido.getMoneda().getDenominacion()
                                : "");
                parameters.put("monedaSimbolo", pedido.getMoneda() != null
                        && pedido.getMoneda().getSimbolo() != null
                                ? pedido.getMoneda().getSimbolo()
                                : "");
                parameters.put("formaPago", pedido.getFormaPago() != null
                        && pedido.getFormaPago().getDescripcion() != null
                                ? pedido.getFormaPago().getDescripcion()
                                : "");
                parameters.put("plazoCredito", pedido.getPlazoCredito());
                parameters.put("observacion", pedido.getObservacionFormaPago() != null
                        ? pedido.getObservacionFormaPago()
                        : "");
                parameters.put("fechaCreacion", pedido.getCreadoEn() != null
                        ? DateUtils.toString(pedido.getCreadoEn())
                        : "");
                String usuario = "";
                if (pedido.getUsuario() != null) {
                    if (pedido.getUsuario().getPersona() != null
                            && pedido.getUsuario().getPersona().getNombre() != null) {
                        usuario = pedido.getUsuario().getPersona().getNombre();
                    } else if (pedido.getUsuario().getNickname() != null) {
                        usuario = pedido.getUsuario().getNickname();
                    }
                }
                parameters.put("usuario", usuario);
                parameters.put("logo", imageService.getImagePath() + File.separator + "logo.png");
                parameters.put("fechaReporte", DateUtils.toString(LocalDateTime.now()));
                parameters.put("montoTotal", montoTotal);
                parameters.put("qrText", "frc-0-PEDIDO-" + pedido.getId() + "-" + pedido.getId()
                        + "-GestionComprasComponent-null-null");

                JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
                byte[] pdfBytes = JasperExportManager.exportReportToPdf(jasperPrint);
                return Base64.getEncoder().encodeToString(pdfBytes);

            } catch (JRException e) {
                log.error("Error al generar PDF de pedido: {}", e.getMessage(), e);
                throw new RuntimeException("Error al generar PDF del pedido: " + e.getMessage(), e);
            }
        }
    }
}
