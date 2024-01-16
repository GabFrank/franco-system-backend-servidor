package com.franco.dev.service.impresion;

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
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.franco.dev.service.utils.PrintingService.resize;

@Service
public class ImpresionService {

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
        if(ticket!=null && ticket==true){
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

            }
            return null;
        } else {
            File file = null;
            try {
                List<TransferenciaItemDto> transferenciaItemDtoList = new ArrayList<>();
                for (int i = 0; i < transferenciaItemList.size(); i++) {
                    TransferenciaItem ti = transferenciaItemList.get(i);
                    TransferenciaItemDto tiDto = new TransferenciaItemDto();
                    tiDto.setCantidad(ti.getCantidadPreTransferencia());
                    Codigo codigo = codigoService.findPrincipalByPresentacionId(ti.getPresentacionPreTransferencia().getId());
                    tiDto.setCodBarra(codigo != null ? codigo.getCodigo() : "");
                    tiDto.setDescripcion(i + 1 + " - " + ti.getPresentacionPreTransferencia().getProducto().getDescripcion());
                    PrecioPorSucursal precio = precioPorSucursalService.findPrincipalByPrecionacionId(ti.getPresentacionPreTransferencia().getId());
                    tiDto.setPrecio(precio != null ? precio.getPrecio() : null);
                    tiDto.setPresentacion(ti.getPresentacionPreTransferencia().getCantidad());
                    if(ti.getVencimientoPreTransferencia()!=null){
                        tiDto.setVencimiento(DateUtils.toStringOnlyDate(ti.getVencimientoPreTransferencia()));
                    }
                    transferenciaItemDtoList.add(tiDto);
                }
                file = ResourceUtils.getFile(imageService.getResourcesPath() + File.separator + "transferencia.jrxml");
                JasperReport jasperReport = JasperCompileManager.compileReport(file.getAbsolutePath());
                JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(transferenciaItemDtoList);
                Map<String, Object> parameters = new HashMap<>();
                parameters.put("idTransferencia", transferencia.getId());
                parameters.put("qr", "frc-" + transferencia.getSucursalOrigen().getId().toString() + "-TRF-" + transferencia.getId() + "-" + transferencia.getSucursalOrigen().getId().toString() + "-EditTransferenciaComponent-null-null");
                parameters.put("sucursalOrigen", transferencia.getSucursalOrigen().getId() + " - " + transferencia.getSucursalOrigen().getNombre());
                parameters.put("sucursalDestino", transferencia.getSucursalDestino().getId() + " - " + transferencia.getSucursalDestino().getNombre());
                parameters.put("fechaReporte", DateUtils.toString(LocalDateTime.now()));
                parameters.put("responsable", transferencia.getUsuarioPreTransferencia().getNickname());
                parameters.put("usuario", transferencia.getUsuarioPreTransferencia().getNickname());
                parameters.put("creadoEn", DateUtils.toString(transferencia.getCreadoEn()));
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
            }
        }

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
            File file = null;
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
                file = ResourceUtils.getFile(imageService.getResourcesPath() + File.separator + "reporte-cobro-venta-credito.jrxml");
                JasperReport jasperReport = JasperCompileManager.compileReport(file.getAbsolutePath());
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
            }
        }

    }

    public String imprimirReporteLucroPorProducto(List<LucroPorProductosDto> lucroPorProductosDtoList, String fechaInicio, String fechaFin, String sucursales, String filtro, Usuario usuario) {
            File file = null;
            Long cantProductos = Long.valueOf(0);
            Double lucroTotalPorcentaje = 0.0;
            Double lucroTotalGs = 0.0;
            Double costoTotal = 0.0;
            Double ventaTotal = 0.0;
            List<LucroPorProductosDto> auxList = new ArrayList<>();
            try {
                for (LucroPorProductosDto dto : lucroPorProductosDtoList) {
                    if(dto.getCostoUnitario() == 0){
                        dto.setCostoUnitario((dto.getTotalVenta() / dto.getCantidad()) * 0.80);
                    } else {
                        dto.setCostoUnitario(dto.getCostoUnitario() / dto.getCantidad());
                    }
                    dto.setLucro((dto.getTotalVenta() - (dto.getCostoUnitario() * dto.getCantidad())));
                    dto.setVentaMedia(dto.getTotalVenta() / dto.getCantidad());
                    dto.setPercent((dto.getLucro() * 100) / dto.getTotalVenta());
                    dto.setMargen(((dto.getVentaMedia() * 100) / dto.getCostoUnitario()) - 100);
                    lucroTotalGs += dto.getLucro();
                    costoTotal += dto.getCostoUnitario() * dto.getCantidad();
                    ventaTotal += dto.getTotalVenta();
                    auxList.add(dto);
                }
                cantProductos = Long.valueOf(lucroPorProductosDtoList.size());
                lucroTotalPorcentaje = ((ventaTotal-costoTotal) / ventaTotal);
                file = ResourceUtils.getFile(imageService.getResourcesPath() + File.separator + "lucro-por-producto.jrxml");
                JasperReport jasperReport = JasperCompileManager.compileReport(file.getAbsolutePath());
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
