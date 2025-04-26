package com.franco.dev.graphql.financiero;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.domain.financiero.dto.ResumenFacturasDto;
import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.graphql.financiero.input.FacturaLegalInput;
import com.franco.dev.graphql.financiero.input.FacturaLegalItemInput;
import com.franco.dev.rabbit.dto.SaveFacturaDto;
import com.franco.dev.security.Unsecured;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.financiero.FacturaLegalItemService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.TimbradoDetalleService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.operaciones.CobroDetalleService;
import com.franco.dev.service.operaciones.DeliveryService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.utilitarios.print.escpos.EscPos;
import com.franco.dev.utilitarios.print.escpos.EscPosConst;
import com.franco.dev.utilitarios.print.escpos.Style;
import com.franco.dev.utilitarios.print.escpos.barcode.QRCode;
import com.franco.dev.utilitarios.print.escpos.image.*;
import com.franco.dev.utilitarios.print.output.PrinterOutputStream;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.apache.poi.ss.usermodel.Workbook;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import javax.print.PrintService;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.franco.dev.service.impresion.ImpresionService.shortDateTime;
import static com.franco.dev.service.utils.PrintingService.resize;
import static com.franco.dev.utilitarios.CalcularVerificadorRuc.getDigitoVerificadorString;

@Component
public class FacturaLegalGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private FacturaLegalService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private VentaService ventaService;

    @Autowired
    private TimbradoDetalleService timbradoDetalleService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private FacturaLegalItemGraphQL facturaLegalItemGraphQL;

    private PrintService printService;

    private PrinterOutputStream printerOutputStream;

    @Autowired
    private ImageService imageService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private FacturaLegalItemService facturaLegalItemService;

    @Autowired
    private CobroDetalleService cobroDetalleService;

    @Autowired
    private CambioService cambioService;

    @Autowired
    private ImpresionService impresionService;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private MultiTenantService multiTenantService;

    public DecimalFormat df = new DecimalFormat("#,###.##");

    public FacturaLegal facturaLegal(Long id, Long sucId) {
        return service.findByIdAndSucursalId(id, sucId);
    }

    public List<FacturaLegal> facturaLegales(int page, int size, Long sucId) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public FacturaLegal facturaLegalPorVenta(Long id, Long sucId) {
        return service.findByVentaIdAndSucursalId(id, sucId);
    }

    @Unsecured
    @Transactional
    public FacturaLegal saveFacturaLegal(FacturaLegalInput input, List<FacturaLegalItemInput> facturaLegalItemInputList) {
        ModelMapper m = new ModelMapper();
        FacturaLegal e = m.map(input, FacturaLegal.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getClienteId() != null) {
            e.setCliente(clienteService.findById(input.getClienteId()).orElse(null));
        } else {
            if (input.getNombre() != null && input.getRuc() != null) {
                Persona nuevaPersona = personaService.findByDocumento(input.getRuc());
                if (nuevaPersona == null) {
                    nuevaPersona = new Persona();
                    nuevaPersona.setNombre(input.getNombre());
                    nuevaPersona.setDocumento(input.getRuc());
                    nuevaPersona.setUsuario(e.getUsuario());
                    nuevaPersona.setDireccion(input.getDireccion());
                    nuevaPersona = personaService.save(nuevaPersona);
                }
                if (nuevaPersona != null) {
                    nuevaPersona = personaService.save(nuevaPersona);
                    Cliente cli = clienteService.findByPersonaId(nuevaPersona.getId());
                    if (cli == null) {
                        cli = new Cliente();
                        cli.setPersona(nuevaPersona);
                        cli.setUsuario(e.getUsuario());
                        cli.setCredito((float) 0);
                        cli = clienteService.save(cli);
                    }
                    if (cli != null) {
                        cli = clienteService.save(cli);
                        e.setCliente(cli);
                    }
                }


            }
        }
        if (input.getTimbradoDetalleId() != null)
            e.setTimbradoDetalle(timbradoDetalleService.findById(input.getTimbradoDetalleId()).orElse(null));
        if (e.getTimbradoDetalle() != null) {
            timbradoDetalleService.save(e.getTimbradoDetalle());
            e = service.save(e);
            if (e.getId() != null) {
                input.setId(e.getId());
                input.setClienteId(e.getCliente().getId());
            }
            Long sucId = e.getTimbradoDetalle().getPuntoDeVenta().getSucursal().getId();
            e = service.save(e);
            for (FacturaLegalItemInput fi : facturaLegalItemInputList) {
                fi.setFacturaLegalId(e.getId());
                if (input.getUsuarioId() != null) fi.setUsuarioId(e.getUsuario().getId());
                facturaLegalItemGraphQL.saveFacturaLegalItem(fi, sucId);
            }
        }
        return e;
    }

    public Boolean deleteFacturaLegal(Long id, Long sucId) {
        return service.deleteByIdAndSucursalId(id, sucId);
    }


    public Long countFacturaLegal() {
        return service.count();
    }

    public Page<FacturaLegal> facturaLegales(Integer page, Integer size, String fechaInicio, String fechaFin, List<Long> sucId, String ruc, String nombre, Boolean iva5, Boolean iva10) {
        Page<FacturaLegal> response = service.findByAll(page, size, fechaInicio, fechaFin, sucId, ruc, nombre, iva5, iva10);
        return response;
    }

    public ResumenFacturasDto findResumenFacturas(String fechaInicio, String fechaFin, List<Long> sucId, String ruc, String nombre, Boolean iva5, Boolean iva10) {
        ResumenFacturasDto response = service.findResumenFacturas(fechaInicio, fechaFin, sucId, ruc, nombre, iva5, iva10);
        return response;
    }

    public Boolean reimprimirFacturaLegal(Long id, Long sucId, String printerName) {
        FacturaLegal facturaLegal = service.findByIdAndSucursalId(id, sucId);
        List<FacturaLegalItem> facturaLegalItemList = facturaLegalItemService.findByFacturaLegalId(id, sucId);
        try {
            printTicket58mmFactura(facturaLegal.getVenta(), facturaLegal, facturaLegalItemList, printerName);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public void printTicket58mmFactura(Venta venta, FacturaLegal facturaLegal, List<FacturaLegalItem> facturaLegalItemList, String printerName) throws Exception {
        SaveFacturaDto saveFacturaDto = new SaveFacturaDto();
        printService = PrinterOutputStream.getPrintServiceByName(printerName);
        Sucursal sucursal = sucursalService.findById(facturaLegal.getSucursalId()).orElse(null);
        Delivery delivery = null;
        if (venta != null) delivery = venta.getDelivery();
        Double aumento = 0.0;
        Double vueltoGs = 0.0;
        Double vueltoRs = 0.0;
        Double vueltoDs = 0.0;
        Double pagadoGs = 0.0;
        Double pagadoRs = 0.0;
        Double pagadoDs = 0.0;
        Double ventaIva10 = 0.0;
        Double ventaIva5 = 0.0;
        Double ventaIva0 = 0.0;
        Double totalIva10 = 0.0;
        Double totalIva5 = 0.0;
        Double totalIva = 0.0;
        Double totalFinal = 0.0;
        Double precioDeliveryGs = 0.0;
        Double precioDeliveryRs = 0.0;
        Double precioDeliveryDs = 0.0;
        Double cambioRs = cambioService.findLastByMonedaId(Long.valueOf(2)).getValorEnGs();
        Double cambioDs = cambioService.findLastByMonedaId(Long.valueOf(3)).getValorEnGs();

        if (delivery != null) {
            precioDeliveryGs = delivery.getPrecio().getValor();
            precioDeliveryRs = precioDeliveryGs / cambioRs;
            precioDeliveryDs = precioDeliveryGs / cambioDs;
        }

        if (printService != null) {
            printerOutputStream = this.printerOutputStream != null ? this.printerOutputStream : new PrinterOutputStream(printService);
            // creating the EscPosImage, need buffered image and algorithm.
            //Styles
            Style center = new Style().setJustification(EscPosConst.Justification.Center);
            Style factura = new Style().setJustification(EscPosConst.Justification.Center).setFontSize(Style.FontSize._1, Style.FontSize._1);
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
            escpos.writeLF(factura, facturaLegal.getTimbradoDetalle().getTimbrado().getRazonSocial().toUpperCase());
            escpos.writeLF(factura, "RUC: " + facturaLegal.getTimbradoDetalle().getTimbrado().getRuc());
            escpos.writeLF(factura, "Timbrado: " + facturaLegal.getTimbradoDetalle().getTimbrado().getNumero());
            escpos.writeLF(factura, "De " + facturaLegal.getTimbradoDetalle().getTimbrado().getFechaInicio().format(impresionService.shortDate) + " a " + facturaLegal.getTimbradoDetalle().getTimbrado().getFechaFin().format(impresionService.shortDate));
            Long numeroFacturaAux = Long.valueOf(facturaLegal.getNumeroFactura());
            StringBuilder numeroFacturaString = new StringBuilder();
            for (int i = 7; i > numeroFacturaAux.toString().length(); i--) {
                numeroFacturaString.append("0");
            }
            if (facturaLegal.getNumeroFactura() != null) {
                numeroFacturaString.append(facturaLegal.getNumeroFactura());
            } else {
                numeroFacturaString.append(numeroFacturaAux.toString());
            }
            escpos.writeLF(factura, "Nro: " + sucursal.getCodigoEstablecimientoFactura() + "-" + facturaLegal.getTimbradoDetalle().getPuntoExpedicion() + "-" + numeroFacturaString.toString());
            escpos.writeLF(center, "Condición: " + (facturaLegal.getCredito() == false ? "Contado" : "Crédito"));

            if (sucursal != null) {
                escpos.writeLF(center, "Suc: " + sucursal.getNombre());
                if (sucursal.getCiudad() != null) {
                    escpos.writeLF(center, sucursal.getCiudad().getDescripcion());
                    if (sucursal.getDireccion() != null) {
                        escpos.writeLF(center, sucursal.getNombre() + " - " + sucursal.getDireccion());
                    }
                }
            }
            if (venta != null) escpos.writeLF(center.setBold(true), "Venta: " + venta.getId());
            if (delivery != null) {
                escpos.writeLF(center, "Modo: Delivery");
            }
            if (venta != null && venta.getUsuario() != null) {
                escpos.writeLF("Cajero: " + venta.getUsuario().getPersona().getNombre());
            }

            escpos.writeLF("Fecha: " + facturaLegal.getCreadoEn().format(shortDateTime));
            escpos.writeLF("--------------------------------");

            String nombreCliente = facturaLegal.getNombre().toUpperCase();
            nombreCliente = nombreCliente.replace("Ñ", "N")
                    .replace("Á", "A")
                    .replace("É", "E")
                    .replace("Í", "I")
                    .replace("Ó", "O")
                    .replace("Ú", "U");
            escpos.writeLF("Cliente: " + nombreCliente);

            if (facturaLegal.getRuc() != null) {
                if (!facturaLegal.getRuc().contains("-")) {
                    facturaLegal.setRuc(facturaLegal.getRuc() + getDigitoVerificadorString(facturaLegal.getRuc()));
                }
                ;
            }

            escpos.writeLF("CI/RUC: " + facturaLegal.getRuc());
            if (facturaLegal.getDireccion() != null)
                escpos.writeLF("Dir: " + facturaLegal.getDireccion());

            escpos.writeLF("--------------------------------");

            escpos.writeLF("Producto");
            escpos.writeLF("Cant  IVA   P.U              P.T");
            escpos.writeLF("--------------------------------");
            for (FacturaLegalItem vi : facturaLegalItemList) {
                Integer iva = null;
                if (vi.getPresentacion() != null) {
                    iva = vi.getPresentacion().getProducto().getIva();
                }
                Double total = vi.getTotal();
                if (iva == null) {
                    iva = 10;
                }
                switch (iva) {
                    case 10:
                        ventaIva10 += total;
                        totalIva10 += total / 11;
                        break;
                    case 5:
                        totalIva5 += total / 21;
                        ventaIva5 += total;
                        break;
                    case 0:
                        ventaIva0 += total;
                        break;

                }
                totalFinal += total;
                String cantidad = vi.getCantidad().intValue() + " (" + vi.getCantidad() + ") " + iva + "%";
                escpos.writeLF(vi.getDescripcion());
                escpos.write(new Style().setBold(true), cantidad);
                String valorUnitario = NumberFormat.getNumberInstance(Locale.GERMAN).format(vi.getPrecioUnitario().intValue());
                String valorTotal = NumberFormat.getNumberInstance(Locale.GERMAN).format(total.intValue());
                for (int i = 14; i > cantidad.length(); i--) {
                    escpos.write(" ");
                }
                escpos.write(valorUnitario);
                for (int i = 16 - valorUnitario.length(); i > valorTotal.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorTotal);
            }
            escpos.writeLF("--------------------------------");
            String valorGs = df.format(totalFinal);
            if(facturaLegal.getDescuento()!=null && facturaLegal.getDescuento().compareTo(0.0) > 0){
                String descuento = df.format(facturaLegal.getDescuento());
                escpos.write("Total parcial: ");
                for (int i = 17; i > valorGs.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorGs);
                escpos.write("Total descuento: ");
                for (int i = 15; i > descuento.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(descuento);
                String totalFinalConDesc = df.format(totalFinal - facturaLegal.getDescuento());
                escpos.write("Total final: ");
                for (int i = 19; i > totalFinalConDesc.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(new Style().setBold(true), totalFinalConDesc);
            } else {
                escpos.write("Total Gs: ");
                for (int i = 22; i > valorGs.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(new Style().setBold(true), valorGs);
            }

            escpos.writeLF("--------Liquidación IVA---------");
            Double porcentajeDescuento = (facturaLegal.getDescuento() != null && facturaLegal.getDescuento().compareTo(0.0) != 0) ? (facturaLegal.getDescuento() / totalFinal) : null;
            escpos.write("Gravadas 10%:");
            Double desc10 = porcentajeDescuento != null ? (totalIva10 - (totalIva10 * porcentajeDescuento)) : null;
            String totalIva10S = df.format(desc10 == null ? totalIva10.intValue() : desc10.intValue());
            for (int i = 19; i > totalIva10S.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(totalIva10S);
            escpos.write("Gravadas 5%: ");
            Double desc5 = porcentajeDescuento != null ? (totalIva5 - (totalIva5 * porcentajeDescuento)) : null;
            String totalIva5S = df.format(desc5 == null ? totalIva5.intValue() : desc5.intValue());
            for (int i = 19; i > totalIva5S.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(totalIva5S);
            escpos.write("Exentas:     ");
            for (int i = 19; i > 1; i--) {
                escpos.write(" ");
            }
            escpos.writeLF("0");
            Double totalFinalIva = totalIva10 + totalIva5;
            Double descFinal = porcentajeDescuento != null ? (totalFinalIva - (totalFinalIva * porcentajeDescuento)) : null;
            String totalFinalIvaS = df.format(descFinal == null ? totalFinalIva.intValue() : descFinal.intValue());
            escpos.write("Total IVA:   ");
            for (int i = 19; i > totalFinalIvaS.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(totalFinalIvaS);
//            escpos.writeLF("--------Liquidación IVA---------");
//            escpos.write("Gravadas 10%:");
//            Double totalIvaFinal = totalIva10 + totalIva5;
//            String totalIvaFinalS = NumberFormat.getNumberInstance(Locale.GERMAN).format(totalIvaFinal.intValue());
//            for (int i = 19; i > totalIvaFinalS.length(); i--) {
//                escpos.write(" ");
//            }
//            escpos.writeLF(iva10s);
//            escpos.write("Gravadas 5%: ");
//            for (int i = 19; i > 1; i--) {
//                escpos.write(" ");
//            }
//            escpos.writeLF("0");

            escpos.writeLF("--------------------------------");
            if (sucursal != null && sucursal.getNroDelivery() != null) {
                escpos.write(center, "Delivery? Escaneá el código qr o escribinos al ");
                escpos.writeLF(center, sucursal.getNroDelivery());
            }
            if (sucursal.getNroDelivery() != null) {
                escpos.write(qrCode.setSize(5).setJustification(EscPosConst.Justification.Center), "wa.me/" + sucursal.getNroDelivery());
            }
            escpos.feed(1);
            escpos.writeLF(center.setBold(true), "GRACIAS POR LA PREFERENCIA");
//            escpos.writeLF("--------------------------------");
//            escpos.write( "Conservar este papel ");
            escpos.feed(5);

            try {
                if (true) {
                    escpos.close();
                    printerOutputStream.close();
                    this.printerOutputStream = null;
                } else {
                    this.printerOutputStream = printerOutputStream;
                }
//                if (facturaLegal.getId() == null) {
//                    Long numero = timbradoDetalleService.aumentarNumeroFactura(timbradoDetalle);
//                    facturaLegal.setTimbradoDetalleId(timbradoDetalle.getId());
//                    if(venta!=null){
//                        facturaLegal.setVentaId(venta.getId());
//                        facturaLegal.setFecha(venta.getCreadoEn());
//                        facturaLegal.setClienteId(venta.getCliente().getId());
//                        facturaLegal.setCajaId(venta.getCaja().getId());
//                    }
//                    facturaLegal.setTotalFinal(totalFinal);
//                    facturaLegal.setIvaParcial5(totalIva5);
//                    facturaLegal.setIvaParcial10(totalIva10);
//                    facturaLegal.setViaTributaria(false);
//                    facturaLegal.setAutoimpreso(true);
//                    facturaLegal.setNumeroFactura(numero.intValue());
//                    facturaLegal.setTotalParcial5(ventaIva5);
//                    facturaLegal.setTotalParcial10(ventaIva10);
//                    facturaLegal.setTotalParcial0(ventaIva0);
//                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    public String generarExcelFacturas(String fechaInicio, String fechaFin, Long sucId) {
        Workbook res = service.createExcelWorkbook(fechaInicio, fechaFin, sucId);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            res.write(outputStream);
            String base64String = Base64.getEncoder().encodeToString(outputStream.toByteArray());
            return base64String;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public String generarExcelFacturasZip(String fechaInicio, String fechaFin, List<Long> sucIdList) {
        List<Workbook> workbookList = new ArrayList<>();
        List<String> sucursalNames = new ArrayList<>();
        for (Long id : sucIdList) {
            Workbook workbook = service.createExcelWorkbook(fechaInicio, fechaFin, id);
            if (workbook != null && workbook.getSheetAt(0) != null) {
                workbookList.add(workbook);
                sucursalNames.add(workbook.getSheetName(0));
            }
        }
        for (int i = 0; i < workbookList.size(); i++) {
            try (FileOutputStream fileOut = new FileOutputStream(workbookList.get(i).getSheetName(0) + ".xlsx")) {
                workbookList.get(i).write(fileOut);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try (FileOutputStream fos = new FileOutputStream("facturas-bodega-franco-" + fechaInicio.substring(0, 10) + ".zip");
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            for (String fileName : sucursalNames) {
                File fileToZip = new File(fileName + ".xlsx");
                FileInputStream fis = new FileInputStream(fileToZip);
                ZipEntry zipEntry = new ZipEntry(fileToZip.getName());
                zos.putNextEntry(zipEntry);

                byte[] bytes = new byte[1024];
                int length;
                while ((length = fis.read(bytes)) >= 0) {
                    zos.write(bytes, 0, length);
                }
                fis.close();
                boolean deleted = fileToZip.delete();
                if (!deleted) {
                    // Log or handle the case where the file couldn't be deleted
                    System.err.println("Could not delete file: " + fileToZip.getName());
                }
            }
            zos.close();
            fos.close();
            File zipedFile = new File("facturas-bodega-franco-" + fechaInicio.substring(0, 10) + ".zip");
            byte[] fileContent = Files.readAllBytes(zipedFile.toPath());
            String res = Base64.getEncoder().encodeToString(fileContent);
            boolean deleted = zipedFile.delete();
            if (!deleted) {
                // Log or handle the case where the file couldn't be deleted
                System.err.println("Could not delete file: " + zipedFile.getName());
            }
            return res;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }


}
