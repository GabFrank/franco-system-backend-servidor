package com.franco.dev.service.impresion;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

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
import java.util.logging.Logger;

import static com.franco.dev.service.utils.PrintingService.resize;

@Service
public class ImpresionService {

    private static final Logger log = Logger.getLogger(ImpresionService.class.getName());
    
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

    public void printReport(JasperPrint jasperPrint, String filename, String printerName, Boolean silent) throws GraphQLException {
        if (silent == null) silent = false;
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
                JasperExportManager.exportReportToPdfFile(jasperPrint, imageService.getStorageDirectoryPathReports() + File.separator + filename);
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
                //Styles
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
//                escpos.writeLF(center.setBold(true), "SUC. CENTRO");
//                escpos.writeLF(center, "Salto del Guairá");
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
                String valorGsAper = NumberFormat.getNumberInstance(Locale.GERMAN).format(balanceDto.getTotalGsAper().intValue());
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
                String valorGsCierre = NumberFormat.getNumberInstance(Locale.GERMAN).format(balanceDto.getTotalGsCierre().intValue());
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
                String valorTarjeta = NumberFormat.getNumberInstance(Locale.GERMAN).format(balanceDto.getTotalTarjeta().intValue());
                for (int i = 18; i > valorTarjeta.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorTarjeta);
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "VALORES DE CREDITO");
                escpos.write("Guaranies G$: ");
                String valorCredito = NumberFormat.getNumberInstance(Locale.GERMAN).format(balanceDto.getTotalCredito().intValue());
                for (int i = 18; i > valorCredito.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorCredito);
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "VALORES DE RETIRO");
                String valorGsRetiro = NumberFormat.getNumberInstance(Locale.GERMAN).format(balanceDto.getTotalRetiroGs().intValue());
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
                String valorGsGasto = NumberFormat.getNumberInstance(Locale.GERMAN).format(balanceDto.getTotalGastoGs().intValue());
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
                String valorGsDiferencia = NumberFormat.getNumberInstance(Locale.GERMAN).format(balanceDto.getDiferenciaGs().intValue());
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
//                escpos.writeLF("--------------------------------");
//                escpos.writeLF(center, "VENTA TOTAL");
//                String valorGsVenta = NumberFormat.getNumberInstance(Locale.GERMAN).format(balanceDto.getTotalVentaGs().intValue());
//                escpos.write("Guaranies G$: ");
//                for (int i = 18; i > valorGsVenta.length(); i--) {
//                    escpos.write(" ");
//                }
//                escpos.writeLF(valorGsVenta);
//                String valorRsVenta = String.format("%.2f", balanceDto.getTotalVentaRs());
//                escpos.write("Reales R$: ");
//                for (int i = 21; i > valorRsVenta.length(); i--) {
//                    escpos.write(" ");
//                }
//                escpos.writeLF(valorRsVenta);
//                String valorDsVenta = String.format("%.2f", balanceDto.getTotalVentaDs());
//                escpos.write("Dolares D$: ");
//                for (int i = 20; i > valorDsVenta.length(); i--) {
//                    escpos.write(" ");
//                }
//                escpos.writeLF(valorDsVenta);
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
                //Styles
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
                escpos.writeLF(new Style().setBold(true), "Tipo " + gastoDto.getTipoGasto().getId() + " - " + gastoDto.getTipoGasto().getDescripcion().toUpperCase());
                if (gastoDto.getObservacion() != null) {
                    escpos.writeLF("Obs: " + gastoDto.getObservacion().toUpperCase());
                }
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "VALORES DE GASTO");
                escpos.write("Guaranies G$: ");
                String valorGsAper = NumberFormat.getNumberInstance(Locale.GERMAN).format(gastoDto.getRetiroGs().intValue());
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
                //Styles
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
                escpos.writeLF(new Style().setBold(true), "Tipo " + gastoDto.getTipoGasto().getId() + " - " + gastoDto.getTipoGasto().getDescripcion().toUpperCase());
                if (gastoDto.getObservacion() != null) {
                    escpos.writeLF("Obs: " + gastoDto.getObservacion().toUpperCase());
                }
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "VALORES DE GASTO");
                escpos.write("Guaranies G$: ");
                String valorGsAper = NumberFormat.getNumberInstance(Locale.GERMAN).format(gastoDto.getRetiroGs().intValue());
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

    public void printRetiro(RetiroDto retiroDto, String printerName, String local, Boolean reimpresion) {
        try {
            selectedPrintService = printingService.getPrintService(printerName);
            if (selectedPrintService != null) {
                printerOutputStream = new PrinterOutputStream(selectedPrintService);
                // creating the EscPosImage, need buffered image and algorithm.
                //Styles
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
                String valorGsAper = NumberFormat.getNumberInstance(Locale.GERMAN).format(retiroDto.getRetiroGs().intValue());
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

    public String imprimirTransferencia(Transferencia transferencia, List<TransferenciaItem> transferenciaItemList, Boolean ticket, String printerName) {
        log.info("========== INICIO GENERACIÓN REPORTE TRANSFERENCIA ==========");
        log.info("Transferencia ID: " + (transferencia != null ? transferencia.getId() : "NULL"));
        log.info("Ticket: " + ticket);
        log.info("Printer Name: " + printerName);
        log.info("TransferenciaItemList size: " + (transferenciaItemList != null ? transferenciaItemList.size() : "NULL"));
        
        if(ticket!=null && ticket==true){
            log.info("Generando reporte tipo TICKET");
            try {
                selectedPrintService = printingService.getPrintService(printerName);
                if (selectedPrintService != null) {
                    printerOutputStream = new PrinterOutputStream(selectedPrintService);
                    // creating the EscPosImage, need buffered image and algorithm.
                    //Styles
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
                    String qrData = "frc-0-TRF-" + transferencia.getId() + "-" + transferencia.getId() + "undefined-undefined-undefined";
                    escpos.write(qrCode.setSize(7).setJustification(EscPosConst.Justification.Center), qrData);
                    escpos.feed(2);
                    escpos.writeLF("Fecha: " + transferencia.getCreadoEn().format(formatter));
                    escpos.writeLF("Suc. Origen: " + transferencia.getSucursalOrigen().getNombre());
                    escpos.writeLF("Suc. Destino: " + transferencia.getSucursalDestino().getNombre());
                    escpos.writeLF("Creado por: " + transferencia.getUsuarioPreTransferencia().getPersona().getNombre());
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
                log.severe("ERROR al generar ticket: " + e.getMessage());
                e.printStackTrace();
            }
            log.info("========== FIN GENERACIÓN REPORTE TRANSFERENCIA (TICKET) ==========");
            return null;
        } else {
            log.info("Generando reporte tipo PDF");
            try {
                // Validación inicial de parámetros
                if (transferencia == null) {
                    log.severe("ERROR CRÍTICO: Transferencia es NULL");
                    return null;
                }
                log.info("Transferencia validada - ID: " + transferencia.getId());
                
                if (transferenciaItemList == null) {
                    log.severe("ERROR CRÍTICO: transferenciaItemList es NULL");
                    return null;
                }
                log.info("transferenciaItemList validada - Tamaño: " + transferenciaItemList.size());
                
                if (transferenciaItemList.isEmpty()) {
                    log.warning("ADVERTENCIA: transferenciaItemList está VACÍA - El reporte se generará sin datos");
                }
                
                // Validación de relaciones de Transferencia
                try {
                    if (transferencia.getSucursalOrigen() == null) {
                        log.severe("ERROR: transferencia.getSucursalOrigen() es NULL");
                    } else {
                        log.info("Sucursal Origen ID: " + transferencia.getSucursalOrigen().getId() + ", Nombre: " + transferencia.getSucursalOrigen().getNombre());
                    }
                    
                    if (transferencia.getSucursalDestino() == null) {
                        log.severe("ERROR: transferencia.getSucursalDestino() es NULL");
                    } else {
                        log.info("Sucursal Destino ID: " + transferencia.getSucursalDestino().getId() + ", Nombre: " + transferencia.getSucursalDestino().getNombre());
                    }
                    
                    if (transferencia.getUsuarioPreTransferencia() == null) {
                        log.severe("ERROR: transferencia.getUsuarioPreTransferencia() es NULL");
                    } else {
                        log.info("Usuario PreTransferencia ID: " + transferencia.getUsuarioPreTransferencia().getId() + ", Nickname: " + transferencia.getUsuarioPreTransferencia().getNickname());
                    }
                } catch (Exception e) {
                    log.severe("ERROR al acceder a relaciones de Transferencia: " + e.getMessage());
                    e.printStackTrace();
                }
                
                List<TransferenciaItemDto> transferenciaItemDtoList = new ArrayList<>();
                log.info("Iniciando procesamiento de " + transferenciaItemList.size() + " TransferenciaItems");
                
                int itemsProcesadosExitosamente = 0;
                int itemsConErrores = 0;
                
                for (int i = 0; i < transferenciaItemList.size(); i++) {
                    try {
                        TransferenciaItem ti = transferenciaItemList.get(i);
                        log.info("--- Procesando TransferenciaItem #" + (i + 1) + " (ID: " + (ti != null ? ti.getId() : "NULL") + ") ---");
                        
                        if (ti == null) {
                            log.severe("ERROR: TransferenciaItem en índice " + i + " es NULL");
                            itemsConErrores++;
                            continue;
                        }
                        
                        TransferenciaItemDto tiDto = new TransferenciaItemDto();
                        
                        // Validar y obtener cantidadPreTransferencia
                        if (ti.getCantidadPreTransferencia() == null) {
                            log.warning("TransferenciaItem #" + (i + 1) + ": cantidadPreTransferencia es NULL");
                            tiDto.setCantidad(null);
                        } else {
                            tiDto.setCantidad(ti.getCantidadPreTransferencia());
                            log.info("TransferenciaItem #" + (i + 1) + ": cantidadPreTransferencia = " + ti.getCantidadPreTransferencia());
                        }
                        
                        // Validar presentacionPreTransferencia
                        if (ti.getPresentacionPreTransferencia() == null) {
                            log.severe("ERROR CRÍTICO TransferenciaItem #" + (i + 1) + ": presentacionPreTransferencia es NULL");
                            itemsConErrores++;
                            continue;
                        }
                        
                        Long presentacionId = ti.getPresentacionPreTransferencia().getId();
                        log.info("TransferenciaItem #" + (i + 1) + ": presentacionPreTransferencia ID = " + presentacionId);
                        
                        // Buscar código
                        try {
                            Codigo codigo = codigoService.findPrincipalByPresentacionId(presentacionId);
                            if (codigo == null) {
                                log.warning("TransferenciaItem #" + (i + 1) + ": No se encontró código principal para presentacionId " + presentacionId);
                                tiDto.setCodBarra("");
                            } else {
                                tiDto.setCodBarra(codigo.getCodigo() != null ? codigo.getCodigo() : "");
                                log.info("TransferenciaItem #" + (i + 1) + ": codBarra = " + tiDto.getCodBarra());
                            }
                        } catch (Exception e) {
                            log.severe("ERROR al buscar código para TransferenciaItem #" + (i + 1) + ": " + e.getMessage());
                            e.printStackTrace();
                            tiDto.setCodBarra("");
                        }
                        
                        // Validar producto
                        if (ti.getPresentacionPreTransferencia().getProducto() == null) {
                            log.severe("ERROR CRÍTICO TransferenciaItem #" + (i + 1) + ": presentacionPreTransferencia.getProducto() es NULL");
                            itemsConErrores++;
                            continue;
                        }
                        
                        String descripcionProducto = ti.getPresentacionPreTransferencia().getProducto().getDescripcion();
                        if (descripcionProducto == null) {
                            log.warning("TransferenciaItem #" + (i + 1) + ": producto.descripcion es NULL");
                            descripcionProducto = "";
                        }
                        tiDto.setDescripcion((i + 1) + " - " + descripcionProducto);
                        log.info("TransferenciaItem #" + (i + 1) + ": descripcion = " + tiDto.getDescripcion());
                        
                        // Buscar precio
                        try {
                            PrecioPorSucursal precio = precioPorSucursalService.findPrincipalByPrecionacionId(presentacionId);
                            if (precio == null) {
                                log.warning("TransferenciaItem #" + (i + 1) + ": No se encontró precio principal para presentacionId " + presentacionId);
                                tiDto.setPrecio(null);
                            } else {
                                tiDto.setPrecio(precio.getPrecio());
                                log.info("TransferenciaItem #" + (i + 1) + ": precio = " + tiDto.getPrecio());
                            }
                        } catch (Exception e) {
                            log.severe("ERROR al buscar precio para TransferenciaItem #" + (i + 1) + ": " + e.getMessage());
                            e.printStackTrace();
                            tiDto.setPrecio(null);
                        }
                        
                        // Obtener presentacion cantidad
                        if (ti.getPresentacionPreTransferencia().getCantidad() == null) {
                            log.warning("TransferenciaItem #" + (i + 1) + ": presentacion.cantidad es NULL");
                            tiDto.setPresentacion(null);
                        } else {
                            tiDto.setPresentacion(ti.getPresentacionPreTransferencia().getCantidad());
                            log.info("TransferenciaItem #" + (i + 1) + ": presentacion = " + tiDto.getPresentacion());
                        }
                        
                        // Vencimiento
                        if (ti.getVencimientoPreTransferencia() != null) {
                            tiDto.setVencimiento(DateUtils.toStringOnlyDate(ti.getVencimientoPreTransferencia()));
                            log.info("TransferenciaItem #" + (i + 1) + ": vencimiento = " + tiDto.getVencimiento());
                        } else {
                            log.info("TransferenciaItem #" + (i + 1) + ": vencimiento es NULL");
                        }
                        
                        transferenciaItemDtoList.add(tiDto);
                        itemsProcesadosExitosamente++;
                        
                        // Log detallado del DTO creado
                        log.info("TransferenciaItem #" + (i + 1) + " DTO creado - " +
                                "descripcion: '" + tiDto.getDescripcion() + "', " +
                                "codBarra: '" + tiDto.getCodBarra() + "', " +
                                "presentacion: " + tiDto.getPresentacion() + ", " +
                                "cantidad: " + tiDto.getCantidad() + ", " +
                                "precio: " + tiDto.getPrecio() + ", " +
                                "vencimiento: " + (tiDto.getVencimiento() != null ? "'" + tiDto.getVencimiento() + "'" : "null"));
                        log.info("TransferenciaItem #" + (i + 1) + " procesado EXITOSAMENTE");
                        
                    } catch (NullPointerException npe) {
                        log.severe("ERROR NullPointerException en TransferenciaItem #" + (i + 1) + ": " + npe.getMessage());
                        log.severe("Stack trace: " + getStackTraceAsString(npe));
                        itemsConErrores++;
                    } catch (Exception e) {
                        log.severe("ERROR inesperado procesando TransferenciaItem #" + (i + 1) + ": " + e.getMessage());
                        e.printStackTrace();
                        itemsConErrores++;
                    }
                }
                
                log.info("========== RESUMEN PROCESAMIENTO ==========");
                log.info("Total items en lista: " + transferenciaItemList.size());
                log.info("Items procesados exitosamente: " + itemsProcesadosExitosamente);
                log.info("Items con errores: " + itemsConErrores);
                log.info("Total DTOs creados: " + transferenciaItemDtoList.size());
                log.info("===========================================");
                
                if (transferenciaItemDtoList.isEmpty()) {
                    log.severe("ERROR CRÍTICO: transferenciaItemDtoList está VACÍA - El reporte no tendrá datos");
                }
                
                // Cargar y compilar reporte
                log.info("Cargando archivo de reporte: reports/transferencia.jrxml");
                ClassPathResource resource = new ClassPathResource("reports/transferencia.jrxml");
                
                if (!resource.exists()) {
                    log.severe("ERROR CRÍTICO: El archivo reports/transferencia.jrxml NO EXISTE en el classpath");
                }
                
                InputStream inputStream = resource.getInputStream();
                log.info("Archivo de reporte cargado exitosamente");
                
                log.info("Compilando reporte...");
                JasperReport jasperReport = JasperCompileManager.compileReport(inputStream);
                log.info("Reporte compilado exitosamente");
                
                log.info("Creando datasource con " + transferenciaItemDtoList.size() + " items");
                
                // Log detallado de cada DTO antes de crear el datasource
                log.info("========== CONTENIDO COMPLETO DE DTOs ANTES DE CREAR DATASOURCE ==========");
                for (int idx = 0; idx < transferenciaItemDtoList.size(); idx++) {
                    TransferenciaItemDto dto = transferenciaItemDtoList.get(idx);
                    log.info("DTO #" + (idx + 1) + ": " +
                            "descripcion=" + (dto.getDescripcion() != null ? "'" + dto.getDescripcion() + "'" : "null") + ", " +
                            "codBarra=" + (dto.getCodBarra() != null ? "'" + dto.getCodBarra() + "'" : "null") + ", " +
                            "presentacion=" + dto.getPresentacion() + ", " +
                            "cantidad=" + dto.getCantidad() + ", " +
                            "precio=" + dto.getPrecio() + ", " +
                            "vencimiento=" + (dto.getVencimiento() != null ? "'" + dto.getVencimiento() + "'" : "null"));
                }
                log.info("================================================================");
                
                JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(transferenciaItemDtoList);
                log.info("Datasource creado exitosamente");
                
                // Verificar que el datasource tiene datos
                try {
                    if (dataSource.next()) {
                        log.info("Datasource verificado - Primer registro accesible");
                        dataSource.moveFirst(); // Reset para que JasperReports pueda iterar desde el inicio
                    } else {
                        log.severe("ERROR: Datasource está vacío - No hay registros disponibles");
                    }
                    
                    // Contar registros en el datasource
                    int recordCount = 0;
                    dataSource.moveFirst();
                    while (dataSource.next()) {
                        recordCount++;
                    }
                    dataSource.moveFirst(); // Reset al inicio
                    log.info("Datasource contiene " + recordCount + " registros accesibles");
                } catch (Exception e) {
                    log.warning("Error al verificar datasource: " + e.getMessage());
                }
                
                // Preparar parámetros
                log.info("Preparando parámetros del reporte...");
                Map<String, Object> parameters = new HashMap<>();
                
                try {
                    parameters.put("idTransferencia", transferencia.getId());
                    log.info("Parámetro idTransferencia: " + transferencia.getId());
                } catch (Exception e) {
                    log.severe("ERROR al obtener idTransferencia: " + e.getMessage());
                }
                
                try {
                    String qr = "frc-" + transferencia.getSucursalOrigen().getId().toString() + "-TRF-" + transferencia.getId() + "-" + transferencia.getSucursalOrigen().getId().toString() + "-EditTransferenciaComponent-null-null";
                    parameters.put("qr", qr);
                    log.info("Parámetro qr: " + qr);
                } catch (Exception e) {
                    log.severe("ERROR al construir QR: " + e.getMessage());
                }
                
                try {
                    String sucursalOrigen = transferencia.getSucursalOrigen().getId() + " - " + transferencia.getSucursalOrigen().getNombre();
                    parameters.put("sucursalOrigen", sucursalOrigen);
                    log.info("Parámetro sucursalOrigen: " + sucursalOrigen);
                } catch (Exception e) {
                    log.severe("ERROR al obtener sucursalOrigen: " + e.getMessage());
                }
                
                try {
                    String sucursalDestino = transferencia.getSucursalDestino().getId() + " - " + transferencia.getSucursalDestino().getNombre();
                    parameters.put("sucursalDestino", sucursalDestino);
                    log.info("Parámetro sucursalDestino: " + sucursalDestino);
                } catch (Exception e) {
                    log.severe("ERROR al obtener sucursalDestino: " + e.getMessage());
                }
                
                try {
                    String fechaReporte = DateUtils.toString(LocalDateTime.now());
                    parameters.put("fechaReporte", fechaReporte);
                    log.info("Parámetro fechaReporte: " + fechaReporte);
                } catch (Exception e) {
                    log.severe("ERROR al obtener fechaReporte: " + e.getMessage());
                }
                
                try {
                    String responsable = transferencia.getUsuarioPreTransferencia().getNickname();
                    parameters.put("responsable", responsable);
                    log.info("Parámetro responsable: " + responsable);
                } catch (Exception e) {
                    log.severe("ERROR al obtener responsable: " + e.getMessage());
                }
                
                try {
                    String usuario = transferencia.getUsuarioPreTransferencia().getNickname();
                    parameters.put("usuario", usuario);
                    log.info("Parámetro usuario: " + usuario);
                } catch (Exception e) {
                    log.severe("ERROR al obtener usuario: " + e.getMessage());
                }
                
                try {
                    String creadoEn = DateUtils.toString(transferencia.getCreadoEn());
                    parameters.put("creadoEn", creadoEn);
                    log.info("Parámetro creadoEn: " + creadoEn);
                } catch (Exception e) {
                    log.severe("ERROR al obtener creadoEn: " + e.getMessage());
                }
                
                try {
                    String logoPath = imageService.getImagePath() + File.separator + "logo.png";
                    parameters.put("logo", logoPath);
                    log.info("Parámetro logo: " + logoPath);
                    File logoFile = new File(logoPath);
                    if (logoFile.exists()) {
                        log.info("Logo existe en: " + logoPath);
                    } else {
                        log.warning("Logo NO existe en: " + logoPath);
                    }
                } catch (Exception e) {
                    log.severe("ERROR al obtener ruta del logo: " + e.getMessage());
                }
                
                log.info("Total de parámetros configurados: " + parameters.size());
                
                // Generar reporte
                log.info("Generando JasperPrint con reporte y datos...");
                log.info("Parámetros a pasar a fillReport: " + parameters.keySet().size() + " parámetros");
                log.info("Datasource ready - " + transferenciaItemDtoList.size() + " items");
                
                JasperPrint jasperPrint1 = null;
                try {
                    jasperPrint1 = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
                    log.info("JasperPrint generado exitosamente");
                    
                    // Verificar elementos en las páginas generadas
                    if (jasperPrint1 != null && jasperPrint1.getPages() != null) {
                        log.info("JasperPrint tiene " + jasperPrint1.getPages().size() + " páginas");
                        if (!jasperPrint1.getPages().isEmpty()) {
                            java.util.List<?> elements = jasperPrint1.getPages().get(0).getElements();
                            if (elements != null) {
                                log.info("Primera página tiene " + elements.size() + " elementos");
                            } else {
                                log.warning("Primera página NO tiene elementos (elements es null)");
                            }
                        }
                    }
                } catch (JRException jre) {
                    log.severe("ERROR JRException durante fillReport: " + jre.getMessage());
                    if (jre.getCause() != null) {
                        log.severe("Causa: " + jre.getCause().getMessage());
                    }
                    throw jre;
                }
                
                log.info("Número de páginas generadas: " + (jasperPrint1 != null ? jasperPrint1.getPages().size() : "NULL"));
                if (jasperPrint1 != null && !jasperPrint1.getPages().isEmpty()) {
                    log.info("Primera página existe - Total páginas: " + jasperPrint1.getPages().size());
                }
                
                log.info("Exportando a PDF...");
                byte[] pdfBytes = JasperExportManager.exportReportToPdf(jasperPrint1);
                log.info("PDF generado exitosamente - Tamaño: " + pdfBytes.length + " bytes");
                
                log.info("Convirtiendo a Base64...");
                String base64String = Base64.getEncoder().encodeToString(pdfBytes);
                log.info("Base64 generado exitosamente - Longitud: " + base64String.length() + " caracteres");
                
                log.info("========== REPORTE GENERADO EXITOSAMENTE ==========");
                return base64String;
                
            } catch (FileNotFoundException e) {
                log.severe("ERROR FileNotFoundException: " + e.getMessage());
                log.severe("Stack trace: " + getStackTraceAsString(e));
                e.printStackTrace();
                return null;
            } catch (JRException e) {
                log.severe("ERROR JRException al generar reporte: " + e.getMessage());
                log.severe("Stack trace: " + getStackTraceAsString(e));
                e.printStackTrace();
                return null;
            } catch (IOException e) {
                log.severe("ERROR IOException: " + e.getMessage());
                log.severe("Stack trace: " + getStackTraceAsString(e));
                e.printStackTrace();
                return null;
            } catch (NullPointerException npe) {
                log.severe("ERROR NullPointerException CRÍTICO: " + npe.getMessage());
                log.severe("Stack trace: " + getStackTraceAsString(npe));
                npe.printStackTrace();
                return null;
            } catch (Exception e) {
                log.severe("ERROR inesperado: " + e.getMessage());
                log.severe("Tipo de excepción: " + e.getClass().getName());
                log.severe("Stack trace: " + getStackTraceAsString(e));
                e.printStackTrace();
                return null;
            } finally {
                log.info("========== FIN GENERACIÓN REPORTE TRANSFERENCIA ==========");
            }
        }

    }
    
    private String getStackTraceAsString(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    public String imprimirReporteCobroVentaCredito(Cliente cliente, List<VentaCredito> ventaCreditoList, Double totalCobrado, Usuario usuario, Boolean ticket, String printerName) {
        if(ticket!=null && ticket==true){
//            try {
//                selectedPrintService = printingService.getPrintService(printerName);
//                if (selectedPrintService != null) {
//                    printerOutputStream = new PrinterOutputStream(selectedPrintService);
//                    // creating the EscPosImage, need buffered image and algorithm.
//                    //Styles
//                    Style center = new Style().setJustification(EscPosConst.Justification.Center);
//
//                    QRCode qrCode = new QRCode();
//
//                    BufferedImage imageBufferedImage = ImageIO.read(new File(imageService.getImagePath() + "logo.png"));
//                    imageBufferedImage = resize(imageBufferedImage, 200, 100);
//                    BitImageWrapper imageWrapper = new BitImageWrapper();
//                    EscPos escpos = new EscPos(printerOutputStream);
//                    Bitonal algorithm = new BitonalThreshold();
//                    EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
//                    imageWrapper.setJustification(EscPosConst.Justification.Center);
//                    escpos.writeLF("--------------------------------");
//                    escpos.write(imageWrapper, escposImage);
//                    String qrData = "frc-0-TRF-" + transferencia.getId() + "-" + transferencia.getId() + "undefined-undefined-undefined";
//                    escpos.write(qrCode.setSize(7).setJustification(EscPosConst.Justification.Center), qrData);
//                    escpos.feed(2);
//                    escpos.writeLF("Fecha: " + transferencia.getCreadoEn().format(formatter));
//                    escpos.writeLF("Suc. Origen: " + transferencia.getSucursalOrigen().getNombre());
//                    escpos.writeLF("Suc. Destino: " + transferencia.getSucursalDestino().getNombre());
//                    escpos.writeLF("Creado por: " + transferencia.getUsuarioPreTransferencia().getPersona().getNombre());
//                    escpos.feed(5);
//
//                    escpos.writeLF(center, "----------------------");
//                    escpos.writeLF(center, "Resp. Creacion");
//                    escpos.feed(5);
//
//                    escpos.writeLF(center, "----------------------");
//                    escpos.writeLF(center, "Resp. Preparacion");
//                    escpos.feed(5);
//
//                    escpos.writeLF(center, "----------------------");
//                    escpos.writeLF(center, "Resp. Transporte");
//                    escpos.feed(5);
//
//                    escpos.writeLF(center, "----------------------");
//                    escpos.writeLF(center, "Resp. Recepcion");
//                    escpos.feed(5);
//
//                    escpos.close();
//                    printerOutputStream.close();
//                }
//            } catch (IOException e) {
//
//            }
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
                    ventaCreditoItemDtoList.add(tiDto);
                }
                // file = ResourceUtils.getFile("classpath:reports/reporte-cobro-venta-credito.jrxml");
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
                parameters.put("logo", imageService.getImagePath()+File.separator+"logo.png");
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

    public String imprimirReporteLucroPorProducto(List<LucroPorProductosDto> lucroPorProductosDtoList, String fechaInicio, String fechaFin, String sucursales, String filtro, Usuario usuario) {
            Long cantProductos = Long.valueOf(0);
            Double lucroTotalPorcentaje = 0.0;
            Double lucroTotalGs = 0.0;
            Double costoTotal = 0.0;
            Double ventaTotal = 0.0;
            List<LucroPorProductosDto> auxList = new ArrayList<>();
            try {
                for (LucroPorProductosDto dto : lucroPorProductosDtoList) {
                    // Los cálculos ya están hechos correctamente en ProductoService
                    // Solo sumamos los totales para el resumen
                    lucroTotalGs += dto.getLucro();
                    costoTotal += dto.getCostoTotal();
                    ventaTotal += dto.getTotalVenta();
                    auxList.add(dto);
                }
                cantProductos = Long.valueOf(lucroPorProductosDtoList.size());
                lucroTotalPorcentaje = ventaTotal > 0 ? ((ventaTotal-costoTotal) / ventaTotal) * 100 : 0.0;
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
                parameters.put("fechaReporte", DateUtils.toString(LocalDateTime.now()));
                parameters.put("usuario", usuario.getNickname());
                parameters.put("logo", imageService.getImagePath()+File.separator+"logo.png");
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

    public void imprimirCodigoDeBarra(Codigo codigo) {
        try {
            selectedPrintService = printingService.getPrintService("adesivo");
            if (selectedPrintService != null) {
                printerOutputStream = new PrinterOutputStream(selectedPrintService);
                // creating the EscPosImage, need buffered image and algorithm.
                //Styles
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
//                escpos.write(imageWrapper, escposImage);
//                if (local != null) {
//                    escpos.writeLF(center, "Local: " + local);
//                }
//                escpos.writeLF(center.setBold(true), "Gasto: " + gastoDto.getId());
//                escpos.writeLF(center.setBold(true), "Caja: " + gastoDto.getCajaId());
//                if (gastoDto.getUsuario().getPersona().getNombre().length() > 23) {
//                    escpos.writeLF("Cajero: " + gastoDto.getUsuario().getPersona().getNombre().substring(0, 23));
//                } else {
//                    escpos.writeLF("Cajero: " + gastoDto.getUsuario().getPersona().getNombre());
//                }
//                escpos.writeLF("Fecha " + gastoDto.getFecha().format(formatter));
//                escpos.writeLF(new Style().setBold(true), "Tipo " + gastoDto.getTipoGasto().getId() + " - " + gastoDto.getTipoGasto().getDescripcion().toUpperCase());
//                if (gastoDto.getObservacion() != null) {
//                    escpos.writeLF("Obs: " + gastoDto.getObservacion().toUpperCase());
//                }
//                escpos.writeLF("--------------------------------");
//                escpos.writeLF(center, "VALORES DE GASTO");
//                escpos.write("Guaranies G$: ");
//                String valorGsAper = NumberFormat.getNumberInstance(Locale.GERMAN).format(gastoDto.getRetiroGs().intValue());
//                for (int i = 18; i > valorGsAper.length(); i--) {
//                    escpos.write(" ");
//                }
//                escpos.writeLF(valorGsAper);
//                escpos.write("Reales R$: ");
//                String valorRsAper = String.format("%.2f", gastoDto.getRetiroRs());
//                for (int i = 21; i > valorRsAper.length(); i--) {
//                    escpos.write(" ");
//                }
//                escpos.writeLF(valorRsAper);
//                escpos.write("Dolares D$: ");
//                String valorDsAper = String.format("%.2f", gastoDto.getRetiroDs());
//                for (int i = 20; i > valorDsAper.length(); i--) {
//                    escpos.write(" ");
//                }
//                escpos.writeLF(valorDsAper);
//                escpos.writeLF("--------------------------------");
//                escpos.feed(4);
//                escpos.writeLF(center, ".......................");
//                escpos.writeLF(center, "FIRMA RESPONSABLE");
//                if (gastoDto.getResponsable().getPersona().getNombre().length() > 23) {
//                    escpos.writeLF(center, gastoDto.getResponsable().getPersona().getNombre().substring(0, 23));
//                } else {
//                    escpos.writeLF(center, gastoDto.getResponsable().getPersona().getNombre());
//                }
//                if (gastoDto.getAutorizadoPor() != null) {
//                    escpos.writeLF("--------------------------------");
//                    escpos.feed(4);
//                    escpos.writeLF(center, ".......................");
//                    escpos.writeLF(center, "AUTORIZACION");
//                    if (gastoDto.getAutorizadoPor().getPersona().getNombre().length() > 23) {
//                        escpos.writeLF(center, gastoDto.getAutorizadoPor().getPersona().getNombre().substring(0, 23));
//                    } else {
//                        escpos.writeLF(center, gastoDto.getAutorizadoPor().getPersona().getNombre());
//                    }
//                }
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
    }

//    public void printVueltoGasto(GastoDto gastoDto){
//        try {
//            printService = PrinterOutputStream.getPrintServiceByName("TICKET58");
//            if(printService!=null){
//                printerOutputStream  = new PrinterOutputStream(printService);
//                // creating the EscPosImage, need buffered image and algorithm.
//                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
//                //Styles
//                Style center = new Style().setJustification(EscPosConst.Justification.Center);
//
//                QRCode qrCode = new QRCode();
//
//                BufferedImage imageBufferedImage = ImageIO.read(new File(imageService.storageDirectoryPath + "logo.png"));
//                imageBufferedImage = resize(imageBufferedImage, 200, 100);
//                RasterBitImageWrapper imageWrapper = new RasterBitImageWrapper();
//                EscPos escpos = new EscPos(printerOutputStream);
//                Bitonal algorithm = new BitonalThreshold();
//                EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
//                imageWrapper.setJustification(EscPosConst.Justification.Center);
//                escpos.write(imageWrapper, escposImage);
//                escpos.writeLF(center.setBold(true), "SUC. CENTRO");
//                escpos.writeLF(center, "Salto del Guairá");
//                escpos.writeLF(center.setBold(true), "Gasto: "+ gastoDto.getId());
//                if(gastoDto.getUsuario().getPersona().getNombre().length() > 23){
//                    escpos.writeLF("Cajero: " + gastoDto.getUsuario().getPersona().getNombre().substring(0, 23));
//                } else {
//                    escpos.writeLF("Cajero: " + gastoDto.getUsuario().getPersona().getNombre());
//                }
//                escpos.writeLF("Fecha "+ gastoDto.getFecha().format(formatter));
//                escpos.writeLF(new Style().setBold(true) ,"Tipo "+ gastoDto.getTipoGasto().getId() +" - "+ gastoDto.getTipoGasto().getDescripcion().toUpperCase());
//                if(gastoDto.getObservacion()!=null){
//                    escpos.writeLF("Obs: " + gastoDto.getObservacion().toUpperCase());
//                }
//                escpos.writeLF("--------------------------------");
//                escpos.writeLF(center, "VALORES DE GASTO");
//                escpos.write("Guaranies G$: ");
//                String valorGsAper = NumberFormat.getNumberInstance(Locale.GERMAN).format(gastoDto.getRetiroGs().intValue());
//                for (int i = 18; i > valorGsAper.length(); i--) {
//                    escpos.write(" ");
//                }
//                escpos.writeLF(valorGsAper);
//                escpos.write("Reales R$: ");
//                String valorRsAper = String.format("%.2f", gastoDto.getRetiroRs());
//                for (int i = 21; i > valorRsAper.length(); i--) {
//                    escpos.write(" ");
//                }
//                escpos.writeLF(valorRsAper);
//                escpos.write("Dolares D$: ");
//                String valorDsAper = String.format("%.2f", gastoDto.getRetiroDs());
//                for (int i = 20; i > valorDsAper.length(); i--) {
//                    escpos.write(" ");
//                }
//                escpos.writeLF(valorDsAper);
//                escpos.writeLF("--------------------------------");
//                escpos.feed(4);
//                escpos.writeLF(center, ".......................");
//                escpos.writeLF(center, "FIRMA RESPONSABLE");
//                if(gastoDto.getResponsable().getPersona().getNombre().length() > 23){
//                    escpos.writeLF(center, gastoDto.getResponsable().getPersona().getNombre().substring(0, 23));
//                } else {
//                    escpos.writeLF(center, gastoDto.getResponsable().getPersona().getNombre());
//                }
//                if(gastoDto.getAutorizadoPor()!=null){
//                    escpos.writeLF("--------------------------------");
//                    escpos.feed(4);
//                    escpos.writeLF(center, ".......................");
//                    escpos.writeLF(center, "AUTORIZACION");
//                    if(gastoDto.getAutorizadoPor().getPersona().getNombre().length() > 23){
//                        escpos.writeLF(center, gastoDto.getAutorizadoPor().getPersona().getNombre().substring(0, 23));
//                    } else {
//                        escpos.writeLF(center, gastoDto.getAutorizadoPor().getPersona().getNombre());
//                    }
//                }
//                escpos.feed(5);
//                escpos.close();
//                printerOutputStream.close();
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
}

