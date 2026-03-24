package com.franco.dev.service.impresion;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.dto.LucroPorProductosDto;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.graphql.financiero.input.PdvCajaBalanceDto;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.impresion.dto.GastoDto;
import com.franco.dev.service.impresion.dto.RetiroDto;
import com.franco.dev.service.productos.CodigoService;
import com.franco.dev.service.productos.PrecioPorSucursalService;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.service.utils.PrintingService;
import com.franco.dev.utilitarios.DateUtils;
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

    /** Cache de reportes Jasper compilados para evitar recompilación y problemas de classloader (web app stopped) */
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
    private MultiTenantService multiTenantService;

    public static DateTimeFormatter shortDate = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    public static DateTimeFormatter shortDateTime = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    /**
     * Compila un reporte JRXML desde los recursos del classpath (dentro del JAR).
     * Usa ClassPathResource y cache para evitar problemas de classloader (web app stopped) y mejorar rendimiento.
     * @param resourcePath Ruta del archivo JRXML (ej: "reports/solicitud-pago.jrxml")
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
        if (silent == null)
            silent = false;
        PrintRequestAttributeSet printRequestAttributeSet = new HashPrintRequestAttributeSet();
        printRequestAttributeSet.add(MediaSizeName.ISO_A4);
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
                escpos.writeLF(new Style().setBold(true), "Tipo " + gastoDto.getTipoGasto().getId() + " - "
                        + gastoDto.getTipoGasto().getDescripcion().toUpperCase());
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
                escpos.writeLF(new Style().setBold(true), "Tipo " + gastoDto.getTipoGasto().getId() + " - "
                        + gastoDto.getTipoGasto().getDescripcion().toUpperCase());
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
     * Estructura: Header (logo, proveedor, nro, hora reporte) | Lista notas | Lista formas pago | Firma | QR
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
                if (prov.length() > 23) prov = prov.substring(0, 23);
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
                if (usr.length() > 23) usr = usr.substring(0, 23);
                escpos.writeLF(center.setBold(true), "Responsable:");
                escpos.writeLF(center, usr);
                escpos.writeLF(center, "_________________________");
                escpos.writeLF("--------------------------------");

                // --- CODIGO QR ---
                escpos.feed(2);
                String qrData = "frc-0-SOLPAG-" + solicitudPago.getId() + "-" + solicitudPago.getId() + "-ListSolicitudPagoComponent-null-null";
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

    public String imprimirReporteCobroVentaCreditoMultiplesClientes(List<Cliente> clientesList,
            Map<Long, List<VentaCredito>> ventaCreditoMap, Usuario usuario) {
        try {
            List<VentaCreditoItemDto> ventaCreditoItemDtoList = new ArrayList<>();
            Double totalGeneral = 0.0;

            for (Cliente cliente : clientesList) {
                List<VentaCredito> ventaCreditoList = ventaCreditoMap.get(cliente.getId());
                if (ventaCreditoList != null && !ventaCreditoList.isEmpty()) {
                    Double totalCliente = 0.0;
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

    public String imprimirMarcaciones(List<Marcacion> marcacionList, String fechaInicio, String fechaFin,
            Usuario usuario) {
        try {
            List<MarcacionItemDto> marcacionItemDtoList = new ArrayList<>();
            for (Marcacion marcacion : marcacionList) {
                MarcacionItemDto dto = new MarcacionItemDto();
                dto.setId(marcacion.getId());
                String nickname = marcacion.getUsuario() != null && marcacion.getUsuario().getNickname() != null
                        ? marcacion.getUsuario().getNickname()
                        : "";
                dto.setUsuario(nickname);
                dto.setSucursalEntrada(marcacion.getSucursalEntrada() != null
                        ? marcacion.getSucursalEntrada().getNombre()
                        : "");
                dto.setFechaEntrada(marcacion.getFechaEntrada() != null
                        ? DateUtils.toString(marcacion.getFechaEntrada())
                        : "");
                dto.setSucursalSalida(marcacion.getSucursalSalida() != null
                        ? marcacion.getSucursalSalida().getNombre()
                        : null);
                dto.setFechaSalida(marcacion.getFechaSalida() != null
                        ? DateUtils.toString(marcacion.getFechaSalida())
                        : null);
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
                if (solicitudPago.getUsuario().getPersona() != null && solicitudPago.getUsuario().getPersona().getNombre() != null) {
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
            parameters.put("monedaSimbolo", solicitudPago.getMoneda() != null && solicitudPago.getMoneda().getSimbolo() != null ? solicitudPago.getMoneda().getSimbolo() : "");
            String estadoStr = solicitudPago.getEstado() != null ? solicitudPago.getEstado().toString() : "";
            parameters.put("estado", estadoStr);
            parameters.put("fechaReporte", DateUtils.toString(LocalDateTime.now()));
            parameters.put("usuario", usuario);
            parameters.put("logo", imageService.getImagePath() + File.separator + "logo.png");
            // QR mismo formato que transferencia: frc-{sucursalId}-{tipoEntidad}-{idOrigen}-{idCentral}-{componentToOpen}-null-null
            String qrText = "frc-0-SOLPAG-" + solicitudPago.getId() + "-" + solicitudPago.getId() + "-ListSolicitudPagoComponent-null-null";
            parameters.put("qrText", qrText);
            parameters.put("notasSubreport", compileReportFromClasspath("reports/solicitud-pago-notas-subreport.jrxml"));
            parameters.put("notasDataSource", new JRBeanCollectionDataSource(notas));
            parameters.put("formasSubreport", compileReportFromClasspath("reports/solicitud-pago-formas-subreport.jrxml"));
            parameters.put("formasDataSource", new JRBeanCollectionDataSource(detalleFormasPago.isEmpty()
                ? java.util.Collections.singletonList(new SolicitudPagoFormaPagoDetalleDto("—", "", "Sin formas de pago", 0.0, "—", "", "—", "—", false))
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

//    public void printVueltoGasto(GastoDto gastoDto){
}
