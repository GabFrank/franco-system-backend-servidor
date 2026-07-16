package com.franco.dev.graphql.financiero;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.financiero.dto.ResumenFacturasDto;
import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.graphql.financiero.input.FacturaLegalInput;
import com.franco.dev.graphql.financiero.input.FacturaLegalItemInput;
import com.franco.dev.security.Unsecured;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.FacturaLegalItemService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.FacturaLegalFilialService;
import com.franco.dev.service.financiero.TimbradoDetalleService;
import com.franco.dev.graphql.financiero.dto.SaveFacturaLegalToFilialResponse;
import com.franco.dev.service.financiero.dto.FacturaLegalFilialResponse;
import com.franco.dev.service.sifen.SifenEventoService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.operaciones.CobroDetalleService;
import com.franco.dev.service.operaciones.DeliveryService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.service.productos.CodigoService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.utilitarios.print.QRCodeImageGenerator;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.franco.dev.service.impresion.ImpresionService.shortDate;
import static com.franco.dev.service.impresion.ImpresionService.shortDateTime;
import static com.franco.dev.service.utils.PrintingService.resize;
import static com.franco.dev.utilitarios.CalcularVerificadorRuc.getDigitoVerificadorString;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.util.ResourceUtils;
import com.franco.dev.utilitarios.DateUtils;

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

    @Autowired
    private DocumentoElectronicoService documentoElectronicoService;

    @Autowired
    private SifenEventoService sifenEventoService;

    @Autowired
    private FacturaLegalFilialService facturaLegalFilialService;

    @Autowired
    private CodigoService codigoService;

    @Autowired
    private ProductoService productoService;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FacturaLegalGraphQL.class);

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
    public FacturaLegal saveFacturaLegal(FacturaLegalInput input,
            List<FacturaLegalItemInput> facturaLegalItemInputList) {
        ModelMapper m = new ModelMapper();
        FacturaLegal e = m.map(input, FacturaLegal.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getClienteId() != null) {
            e.setCliente(clienteService.findById(input.getClienteId()).orElse(null));
        } else {
            // Solo crear cliente automáticamente si hay nombre y ruc Y no se especificó
            // explícitamente que no se debe crear
            // Si clienteId es null y hay nombre/ruc, intentar buscar o crear cliente
            // PERO: si el frontend quiere crear factura sin cliente, simplemente no crear
            // cliente aquí
            // El servicio FacturaLegalService.save() también tiene lógica para crear
            // cliente si es necesario
            // Por lo tanto, aquí solo creamos cliente si realmente no existe y hay datos
            // suficientes
            if (input.getNombre() != null && input.getRuc() != null) {
                // Buscar persona existente por documento
                Persona nuevaPersona = personaService.findByDocumento(input.getRuc());
                if (nuevaPersona != null) {
                    // Si existe persona, buscar cliente asociado
                    Cliente cli = clienteService.findByPersonaId(nuevaPersona.getId());
                    if (cli != null) {
                        e.setCliente(cli);
                    }
                    // Si no hay cliente pero hay persona, NO crear cliente automáticamente aquí
                    // Dejar que el servicio FacturaLegalService.save() decida si crear cliente
                }
                // Si no existe persona, NO crear persona/cliente aquí
                // Dejar que el servicio FacturaLegalService.save() decida si crear cliente
            }
            // Si no hay clienteId, la factura se guardará sin cliente (cliente = null)
            // El servicio FacturaLegalService.save() puede crear cliente si es necesario
        }
        if (input.getTimbradoDetalleId() != null)
            e.setTimbradoDetalle(timbradoDetalleService
                    .findByIdAndSucursalId(input.getTimbradoDetalleId(), input.getSucursalId()).orElse(null));
        if (e.getTimbradoDetalle() != null) {
            timbradoDetalleService.save(e.getTimbradoDetalle());
            e = service.save(e);
            if (e.getId() != null) {
                input.setId(e.getId());
                if (e.getCliente() != null) {
                    input.setClienteId(e.getCliente().getId());
                }
            }
            Long sucId = e.getTimbradoDetalle().getPuntoDeVenta().getSucursal().getId();
            e = service.save(e);
            for (FacturaLegalItemInput fi : facturaLegalItemInputList) {
                fi.setFacturaLegalId(e.getId());
                if (input.getUsuarioId() != null)
                    fi.setUsuarioId(e.getUsuario().getId());
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

    public Page<FacturaLegal> facturaLegales(Integer page, Integer size, String fechaInicio, String fechaFin,
            List<Long> sucId, String ruc, String nombre, Boolean iva5, Boolean iva10, Boolean isElectronico,
            Boolean activo, Boolean sinNombre) {
        Page<FacturaLegal> response = service.findByAll(page, size, fechaInicio, fechaFin, sucId, ruc, nombre, iva5,
                iva10, isElectronico, activo, sinNombre);
        return response;
    }

    public Optional<FacturaLegal> facturaLegalByCdc(String cdc) {
        return service.findByCdc(cdc);
    }

    public ResumenFacturasDto findResumenFacturas(String fechaInicio, String fechaFin, List<Long> sucId, String ruc,
            String nombre, Boolean iva5, Boolean iva10, Boolean sinNombre) {
        ResumenFacturasDto response = service.findResumenFacturas(fechaInicio, fechaFin, sucId, ruc, nombre, iva5,
                iva10, sinNombre);
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

    public void printTicket58mmFactura(Venta venta, FacturaLegal facturaLegal,
            List<FacturaLegalItem> facturaLegalItemList, String printerName) throws Exception {
        printTicket58mmFactura(venta, facturaLegal, facturaLegalItemList, printerName, null);
    }

    /**
     * Igual a {@link #printTicket58mmFactura(Venta, FacturaLegal, List, String)}, pero si se pasa
     * un {@code destino} no nulo escribe el ESC/POS ahí en vez de abrir la impresora local (usado
     * para rutear la impresión a otra sucursal vía PrintRouterService). Con destino=null el
     * comportamiento es idéntico al método original.
     */
    public void printTicket58mmFactura(Venta venta, FacturaLegal facturaLegal,
            List<FacturaLegalItem> facturaLegalItemList, String printerName, OutputStream destino) throws Exception {
        // Verificar si es moneda extranjera y redirigir al método correspondiente
        boolean esMonedaExtranjera = facturaLegal.getMonedaExtranjera() != null
                && !facturaLegal.getMonedaExtranjera().trim().isEmpty()
                && facturaLegal.getTipoCambio() != null;

        if (esMonedaExtranjera) {
            if (facturaLegalItemList == null) {
                facturaLegalItemList = facturaLegalItemService.findByFacturaLegalId(facturaLegal.getId());
            }
            printTicket58mmFacturaMonedaExtranjera(venta, facturaLegal,
                    facturaLegalItemList, printerName,
                    facturaLegal.getMonedaExtranjera(), facturaLegal.getTipoCambio(), destino);
            return;
        }

        if (facturaLegalItemList == null) {
            facturaLegalItemList = facturaLegalItemService.findByFacturaLegalId(facturaLegal.getId());
        }

        if (destino == null) {
            printService = PrinterOutputStream.getPrintServiceByName(printerName);
        }
        Sucursal sucursal = sucursalService.findById(facturaLegal.getSucursalId()).orElse(null);
        Delivery delivery = null;
        if (venta != null)
            delivery = venta.getDelivery();
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

        if (destino != null || printService != null) {
            OutputStream salida = destino;
            if (salida == null) {
                printerOutputStream = this.printerOutputStream != null ? this.printerOutputStream
                        : new PrinterOutputStream(printService);
                salida = printerOutputStream;
            }
            // creating the EscPosImage, need buffered image and algorithm.
            // Styles
            Style center = new Style().setJustification(EscPosConst.Justification.Center);
            Style factura = new Style().setJustification(EscPosConst.Justification.Center)
                    .setFontSize(Style.FontSize._1, Style.FontSize._1);
            QRCode qrCode = new QRCode();

            BufferedImage imageBufferedImage = ImageIO.read(new File(imageService.getImagePath() + "logo.png"));
            imageBufferedImage = resize(imageBufferedImage, 200, 100);
            BitImageWrapper imageWrapper = new BitImageWrapper();
            EscPos escpos = new EscPos(salida);
            Bitonal algorithm = new BitonalThreshold();
            EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
            imageWrapper.setJustification(EscPosConst.Justification.Center);
            escpos.writeLF("--------------------------------");
            escpos.write(imageWrapper, escposImage);
            escpos.writeLF(factura, facturaLegal.getTimbradoDetalle().getTimbrado().getRazonSocial().toUpperCase());
            escpos.writeLF(factura, "RUC: " + facturaLegal.getTimbradoDetalle().getTimbrado().getRuc());
            escpos.writeLF(factura, "Timbrado: " + facturaLegal.getTimbradoDetalle().getTimbrado().getNumero());
            // Si el timbrado es electrónico, no se imprime la fecha de inicio y fin
            if (facturaLegal.getTimbradoDetalle().getTimbrado().getIsElectronico() != Boolean.TRUE) {
                escpos.writeLF(factura,
                        "De " + facturaLegal.getTimbradoDetalle().getTimbrado().getFechaInicio().format(shortDate)
                                + " a " +
                                facturaLegal.getTimbradoDetalle().getTimbrado().getFechaFin().format(shortDate));
            }
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
            escpos.writeLF(factura, "Nro: " + sucursal.getCodigoEstablecimientoFactura() + "-"
                    + facturaLegal.getTimbradoDetalle().getPuntoExpedicion() + "-" + numeroFacturaString.toString());
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
            if (venta != null)
                escpos.writeLF(center.setBold(true), "Venta: " + venta.getId());
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
                // Prioridad 1: IVA del item directamente
                Integer iva = vi.getIva();

                // Prioridad 2: IVA del producto vinculado directamente
                if (iva == null && vi.getProducto() != null) {
                    iva = vi.getProducto().getIva();
                }
                // Prioridad 3: IVA del producto a través de la presentación
                else if (iva == null && vi.getPresentacion() != null) {
                    iva = vi.getPresentacion().getProducto().getIva();
                }

                // Fallback: lookup producto por descripcion (UPPER+TRIM) si iva todavia null.
                if (iva == null && vi.getDescripcion() != null) {
                    List<Producto> matches = productoService.findByDescripcionNormalized(vi.getDescripcion());
                    if (matches.size() == 1 && matches.get(0).getIva() != null) {
                        iva = matches.get(0).getIva();
                    }
                }
                if (iva == null) {
                    log.warn("IVA no resoluble al imprimir ticket para item desc='{}', default 10", vi.getDescripcion());
                    iva = 10;
                }

                Double total = vi.getTotal();
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
                String valorUnitario = NumberFormat.getNumberInstance(Locale.GERMAN)
                        .format(vi.getPrecioUnitario().intValue());
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
            if (facturaLegal.getDescuento() != null && facturaLegal.getDescuento().compareTo(0.0) > 0) {
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
            // Leer los parciales persistidos en la factura (ya tienen el descuento
            // aplicado proporcionalmente por el builder). Antes este bloque
            // recalculaba desde los items locales y aplicaba descuento extra,
            // generando inconsistencia con PDF/KUDE que leen los parciales
            // persistidos.
            Double ivaParcial10Persist = facturaLegal.getIvaParcial10() != null ? facturaLegal.getIvaParcial10() : 0.0;
            Double ivaParcial5Persist = facturaLegal.getIvaParcial5() != null ? facturaLegal.getIvaParcial5() : 0.0;
            escpos.write("Gravadas 10%:");
            String totalIva10S = df.format(ivaParcial10Persist.intValue());
            for (int i = 19; i > totalIva10S.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(totalIva10S);
            escpos.write("Gravadas 5%: ");
            String totalIva5S = df.format(ivaParcial5Persist.intValue());
            for (int i = 19; i > totalIva5S.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(totalIva5S);
            escpos.write("Exentas:     ");
            for (int i = 19; i > 1; i--) {
                escpos.write(" ");
            }
            escpos.writeLF("0");
            Double totalFinalIvaPersist = ivaParcial10Persist + ivaParcial5Persist;
            String totalFinalIvaS = df.format(totalFinalIvaPersist.intValue());
            escpos.write("Total IVA:   ");
            for (int i = 19; i > totalFinalIvaS.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(totalFinalIvaS);
            // escpos.writeLF("--------Liquidación IVA---------");
            // escpos.write("Gravadas 10%:");
            // Double totalIvaFinal = totalIva10 + totalIva5;
            // String totalIvaFinalS =
            // NumberFormat.getNumberInstance(Locale.GERMAN).format(totalIvaFinal.intValue());
            // for (int i = 19; i > totalIvaFinalS.length(); i--) {
            // escpos.write(" ");
            // }
            // escpos.writeLF(iva10s);
            // escpos.write("Gravadas 5%: ");
            // for (int i = 19; i > 1; i--) {
            // escpos.write(" ");
            // }
            // escpos.writeLF("0");

            escpos.writeLF("--------------------------------");

            // Generar código QR si es documento electrónico
            if (facturaLegal.getTimbradoDetalle().getTimbrado().getIsElectronico() != null
                    && facturaLegal.getTimbradoDetalle().getTimbrado().getIsElectronico()) {

                Optional<DocumentoElectronico> documentoElectronicoOpt = documentoElectronicoService
                        .findByFacturaLegalId(facturaLegal.getId(), facturaLegal.getSucursalId());
                DocumentoElectronico documentoElectronico = documentoElectronicoOpt.orElse(null);

                String cdc = documentoElectronico != null ? documentoElectronico.getCdc() : null;
                String urlQr = documentoElectronico != null ? documentoElectronico.getUrlQr() : null;

                // Imprimir QR como imagen generada por ZXing
                if (urlQr != null) {
                    try {
                        BufferedImage qrImage = QRCodeImageGenerator.generateQRCodeImage(urlQr, 250, 250);

                        imageWrapper.setJustification(EscPosConst.Justification.Center);
                        EscPosImage escposImageQR = new EscPosImage(new CoffeeImageImpl(qrImage), algorithm);

                        escpos.write(imageWrapper, escposImageQR);
                        escpos.feed(1);

                    } catch (Exception e) {
                        e.printStackTrace();
                        escpos.writeLF(center, "ERROR: No se pudo generar el código QR.");
                        escpos.writeLF(center, "URL de consulta:");
                        escpos.writeLF(center, urlQr);
                        escpos.feed(1);
                    }
                }

                // Texto requerido por SIFEN debajo del QR
                escpos.writeLF(center,
                        "Consulte la validez de esta Factura Electronica con el numero de CDC impreso abajo en:");
                escpos.writeLF(center, "https://ekuatia.set.gov.py/consultas");

                // Formatear CDC en grupos de 4 dígitos
                if (cdc != null) {
                    String cdcFormateado = cdc.replaceAll("\\s+", "");
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < cdcFormateado.length(); i += 4) {
                        if (i > 0)
                            sb.append(" ");
                        sb.append(cdcFormateado.substring(i, Math.min(i + 4, cdcFormateado.length())));
                    }
                    escpos.writeLF(center, sb.toString());
                }

                escpos.writeLF(center,
                        "ESTE DOCUMENTO ES UNA REPRESENTACION GRAFICA DE UN DOCUMENTO ELECTRONICO (XML)");
                escpos.writeLF("--------------------------------");
            }

            if (sucursal != null && sucursal.getNroDelivery() != null) {
                escpos.write(center, "Delivery? Escaneá el código qr o escribinos al ");
                escpos.writeLF(center, sucursal.getNroDelivery());
            }
            if (sucursal != null && sucursal.getNroDelivery() != null) {
                escpos.write(qrCode.setSize(5).setJustification(EscPosConst.Justification.Center),
                        "wa.me/" + sucursal.getNroDelivery());
            }
            escpos.feed(1);
            escpos.writeLF(center.setBold(true), "GRACIAS POR LA PREFERENCIA");
            // escpos.writeLF("--------------------------------");
            // escpos.write( "Conservar este papel ");
            escpos.feed(5);

            try {
                if (true) {
                    escpos.close();
                    if (destino == null) {
                        printerOutputStream.close();
                        this.printerOutputStream = null;
                    }
                } else {
                    this.printerOutputStream = printerOutputStream;
                }
                // if (facturaLegal.getId() == null) {
                // Long numero = timbradoDetalleService.aumentarNumeroFactura(timbradoDetalle);
                // facturaLegal.setTimbradoDetalleId(timbradoDetalle.getId());
                // if(venta!=null){
                // facturaLegal.setVentaId(venta.getId());
                // facturaLegal.setFecha(venta.getCreadoEn());
                // facturaLegal.setClienteId(venta.getCliente().getId());
                // facturaLegal.setCajaId(venta.getCaja().getId());
                // }
                // facturaLegal.setTotalFinal(totalFinal);
                // facturaLegal.setIvaParcial5(totalIva5);
                // facturaLegal.setIvaParcial10(totalIva10);
                // facturaLegal.setViaTributaria(false);
                // facturaLegal.setAutoimpreso(true);
                // facturaLegal.setNumeroFactura(numero.intValue());
                // facturaLegal.setTotalParcial5(ventaIva5);
                // facturaLegal.setTotalParcial10(ventaIva10);
                // facturaLegal.setTotalParcial0(ventaIva0);
                // }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    /**
     * Imprime un ticket de factura de 58mm en moneda extranjera.
     * Todos los valores se muestran convertidos a la moneda extranjera
     * seleccionada.
     * 
     * @param venta                La venta asociada (opcional)
     * @param facturaLegal         La factura legal a imprimir
     * @param facturaLegalItemList Lista de items de la factura
     * @param printerName          Nombre de la impresora
     * @param monedaExtranjera     Código de moneda extranjera (ej: "USD", "EUR")
     * @param tipoCambio           Tipo de cambio utilizado
     */
    public void printTicket58mmFacturaMonedaExtranjera(Venta venta, FacturaLegal facturaLegal,
            List<FacturaLegalItem> facturaLegalItemList, String printerName, String monedaExtranjera, Double tipoCambio)
            throws Exception {
        printTicket58mmFacturaMonedaExtranjera(venta, facturaLegal, facturaLegalItemList, printerName,
                monedaExtranjera, tipoCambio, null);
    }

    /**
     * Igual a {@link #printTicket58mmFacturaMonedaExtranjera(Venta, FacturaLegal, List, String, String, Double)},
     * pero si se pasa un {@code destino} no nulo escribe el ESC/POS ahí en vez de abrir la impresora
     * local (usado para rutear la impresión a otra sucursal vía PrintRouterService). Con destino=null
     * el comportamiento es idéntico al método original.
     */
    public void printTicket58mmFacturaMonedaExtranjera(Venta venta, FacturaLegal facturaLegal,
            List<FacturaLegalItem> facturaLegalItemList, String printerName, String monedaExtranjera,
            Double tipoCambio, OutputStream destino)
            throws Exception {

        if (facturaLegalItemList == null) {
            facturaLegalItemList = facturaLegalItemService.findByFacturaLegalId(facturaLegal.getId());
        }

        if (destino == null) {
            printService = PrinterOutputStream.getPrintServiceByName(printerName);
        }
        Sucursal sucursal = sucursalService.findById(facturaLegal.getSucursalId()).orElse(null);
        Delivery delivery = null;
        if (venta != null)
            delivery = venta.getDelivery();
        Double descuento = facturaLegal.getDescuento() != null ? facturaLegal.getDescuento() : 0.0;

        // Convertir todos los valores a moneda extranjera
        Double totalFinal = facturaLegal.getTotalFinal();
        Double totalIva10 = facturaLegal.getIvaParcial10() != null ? facturaLegal.getIvaParcial10() : 0.0;
        Double totalIva5 = facturaLegal.getIvaParcial5() != null ? facturaLegal.getIvaParcial5() : 0.0;
        Double totalIva = totalIva10 + totalIva5;

        // Convertir valores usando el tipo de cambio
        // Total parcial = total final + descuento (en guaraníes), luego convertir
        Double totalParcialGs = totalFinal + descuento;
        Double totalParcialExtranjera = totalParcialGs / tipoCambio;
        Double totalFinalExtranjera = totalFinal / tipoCambio;
        Double descuentoExtranjera = descuento / tipoCambio;
        Double totalIva10Extranjera = totalIva10 / tipoCambio;
        Double totalIva5Extranjera = totalIva5 / tipoCambio;
        Double totalIvaExtranjera = totalIva / tipoCambio;
        Double totalParcial0Extranjera = (facturaLegal.getTotalParcial0() != null ? facturaLegal.getTotalParcial0()
                : 0.0) / tipoCambio;

        if (destino != null || printService != null) {
            OutputStream salida = destino;
            if (salida == null) {
                printerOutputStream = this.printerOutputStream != null ? this.printerOutputStream
                        : new PrinterOutputStream(printService);
                salida = printerOutputStream;
            }
            // Styles
            Style center = new Style().setJustification(EscPosConst.Justification.Center);
            Style factura = new Style().setJustification(EscPosConst.Justification.Center)
                    .setFontSize(Style.FontSize._1, Style.FontSize._1);

            EscPos escpos = new EscPos(salida);
            BitImageWrapper imageWrapper = new BitImageWrapper();
            Bitonal algorithm = new BitonalThreshold();

            escpos.writeLF("--------------------------------");
            escpos.writeLF(factura, facturaLegal.getTimbradoDetalle().getTimbrado().getRazonSocial().toUpperCase());
            escpos.writeLF(factura, "RUC: " + facturaLegal.getTimbradoDetalle().getTimbrado().getRuc());
            escpos.writeLF(factura, "Timbrado: " + facturaLegal.getTimbradoDetalle().getTimbrado().getNumero());

            // Si el timbrado es electrónico, no se imprime la fecha de inicio y fin
            if (facturaLegal.getTimbradoDetalle().getTimbrado().getIsElectronico() != Boolean.TRUE) {
                escpos.writeLF(factura, "De "
                        + facturaLegal.getTimbradoDetalle().getTimbrado().getFechaInicio()
                                .format(shortDate)
                        + " a "
                        + facturaLegal.getTimbradoDetalle().getTimbrado().getFechaFin()
                                .format(shortDate));
            }

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
            escpos.writeLF(factura, "Nro: " + sucursal.getCodigoEstablecimientoFactura() + "-"
                    + facturaLegal.getTimbradoDetalle().getPuntoExpedicion() + "-" + numeroFacturaString.toString());
            escpos.writeLF(center, "Condicion: " + (facturaLegal.getCredito() == false ? "Contado" : "Crédito"));

            // Mostrar cambio utilizado
            escpos.writeLF(center.setBold(true), "Cambio: " +
                    String.format(Locale.GERMAN, "%.2f", tipoCambio) + " Gs/" + monedaExtranjera.toUpperCase());

            // Mostrar información de dirección del timbrado detalle
            TimbradoDetalle timbradoDetalle = facturaLegal.getTimbradoDetalle();
            if (timbradoDetalle.getDireccion() != null && !timbradoDetalle.getDireccion().trim().isEmpty()) {
                escpos.writeLF(center, timbradoDetalle.getDireccion());
            }
            if (timbradoDetalle.getCiudad() != null && !timbradoDetalle.getCiudad().trim().isEmpty()) {
                escpos.writeLF(center, timbradoDetalle.getCiudad());
            }
            if (timbradoDetalle.getDepartamento() != null && !timbradoDetalle.getDepartamento().trim().isEmpty()) {
                escpos.writeLF(center, timbradoDetalle.getDepartamento());
            }

            if (venta != null)
                escpos.writeLF(center.setBold(true), "Venta: " + venta.getId());
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
            }

            escpos.writeLF("CI/RUC: " + facturaLegal.getRuc());
            if (facturaLegal.getDireccion() != null)
                escpos.writeLF("Dir: " + facturaLegal.getDireccion());

            escpos.writeLF("--------------------------------");

            // Pre-calcular todos los valores para detectar overflow
            List<String> valorUnitarioList = new ArrayList<>();
            List<String> valorTotalList = new ArrayList<>();
            List<Integer> ivaList = new ArrayList<>();
            List<String> cantidadStrList = new ArrayList<>();
            List<String> cantidadSinIvaList = new ArrayList<>();
            List<String> descripcionList = new ArrayList<>();

            int maxValorUnitarioLength = 0;
            int maxValorTotalLength = 0;

            for (FacturaLegalItem vi : facturaLegalItemList) {
                // Prioridad 1: IVA del item directamente
                Integer iva = vi.getIva();

                // Prioridad 2: IVA del producto vinculado directamente
                if (iva == null && vi.getProducto() != null) {
                    iva = vi.getProducto().getIva();
                }
                // Prioridad 3: IVA del producto a través de la presentación
                else if (iva == null && vi.getPresentacion() != null) {
                    iva = vi.getPresentacion().getProducto().getIva();
                }

                // Fallback: lookup producto por descripcion (UPPER+TRIM) si iva todavia null.
                if (iva == null && vi.getDescripcion() != null) {
                    List<Producto> matches = productoService.findByDescripcionNormalized(vi.getDescripcion());
                    if (matches.size() == 1 && matches.get(0).getIva() != null) {
                        iva = matches.get(0).getIva();
                    }
                }
                if (iva == null) {
                    log.warn("IVA no resoluble al imprimir ticket para item desc='{}', default 10", vi.getDescripcion());
                    iva = 10;
                }

                // Construir string de cantidad con unidad de medida si está disponible
                // Truncar "UNIDAD" a "UN"
                String unidadMedida = vi.getUnidadMedida();
                if (unidadMedida != null && !unidadMedida.trim().isEmpty()) {
                    if (unidadMedida.equalsIgnoreCase("UNIDAD")) {
                        unidadMedida = "UN";
                    }
                }

                // Cantidad sin IVA (para layout alternativo)
                String cantidadSinIva;
                if (unidadMedida != null && !unidadMedida.trim().isEmpty()) {
                    cantidadSinIva = vi.getCantidad().intValue() + " (" + unidadMedida + ")";
                } else {
                    cantidadSinIva = String.valueOf(vi.getCantidad().intValue());
                }

                // Cantidad con IVA (para layout normal)
                String cantidadStr;
                if (unidadMedida != null && !unidadMedida.trim().isEmpty()) {
                    cantidadStr = vi.getCantidad().intValue() + " " + unidadMedida + " " + iva + "%";
                } else {
                    cantidadStr = vi.getCantidad().intValue() + " " + iva + "%";
                }

                // Convertir precios a moneda extranjera
                Double precioUnitarioExtranjera = vi.getPrecioUnitario() / tipoCambio;
                Double totalItemExtranjera = vi.getTotal() / tipoCambio;

                // Formatear con 2-3 decimales según necesidad
                String valorUnitario = formatearMonedaExtranjera(precioUnitarioExtranjera);
                String valorTotal = formatearMonedaExtranjera(totalItemExtranjera);

                valorUnitarioList.add(valorUnitario);
                valorTotalList.add(valorTotal);
                ivaList.add(iva);
                cantidadStrList.add(cantidadStr);
                cantidadSinIvaList.add(cantidadSinIva);
                // Forzar mayúsculas en descripción
                String descripcion = vi.getDescripcion() != null ? vi.getDescripcion().toUpperCase() : "";
                descripcionList.add(descripcion);

                maxValorUnitarioLength = Math.max(maxValorUnitarioLength, valorUnitario.length());
                maxValorTotalLength = Math.max(maxValorTotalLength, valorTotal.length());
            }

            // Calcular longitudes de los totales para detectar overflow
            String parcialExtStr = formatearMonedaExtranjera(totalParcialExtranjera);
            String descExtStr = formatearMonedaExtranjera(descuentoExtranjera);
            String finalExtStr = formatearMonedaExtranjera(totalFinalExtranjera);
            String totalIva10ExtS = formatearMonedaExtranjera(totalIva10Extranjera);
            String totalIva5ExtS = formatearMonedaExtranjera(totalIva5Extranjera);
            String totalIva0ExtS = formatearMonedaExtranjera(totalParcial0Extranjera);
            String totalFinalIvaExtS = formatearMonedaExtranjera(totalIvaExtranjera);

            int maxTotalLength = Math.max(Math.max(parcialExtStr.length(), descExtStr.length()),
                    Math.max(finalExtStr.length(), Math.max(totalIva10ExtS.length(),
                            Math.max(totalIva5ExtS.length(),
                                    Math.max(totalIva0ExtS.length(), totalFinalIvaExtS.length())))));

            // Calcular ancho total de la línea de totales en layout normal
            // Formato: "USD. " (5 chars) + parcial (9 espacios reservados) + desc (9
            // espacios) + final (10 espacios) = 33 caracteres
            // Pero debemos verificar si los valores reales caben
            int anchoLineaTotales = (monedaExtranjera.toUpperCase() + ". ").length() +
                    Math.max(9, parcialExtStr.length()) +
                    Math.max(9, descExtStr.length()) +
                    Math.max(10, finalExtStr.length());

            // Calcular ancho de línea de IVA en layout normal
            // Formato: "Gravadas 10%:" (14 chars) + valor (19 espacios reservados) = 33
            // caracteres
            int anchoLineaIva = Math.max(14 + 19,
                    Math.max("Gravadas 10%:".length() + totalIva10ExtS.length(),
                            Math.max("Gravadas 5%: ".length() + totalIva5ExtS.length(),
                                    Math.max("Exentas:     ".length() + totalIva0ExtS.length(),
                                            "Total IVA:   ".length() + totalFinalIvaExtS.length()))));

            // Detectar overflow:
            // 1. Si algún valor formateado de items tiene más de 10 caracteres
            // 2. Si el ancho total de la línea de totales excede 32 caracteres (ancho
            // típico de 58mm)
            // 3. Si el ancho total de la línea de IVA excede 32 caracteres
            // 4. Si la suma de precio unitario + total + espacios excede 32 caracteres
            boolean usarLayoutAlternativo = maxValorUnitarioLength > 10 ||
                    maxValorTotalLength > 10 ||
                    maxTotalLength > 10 ||
                    anchoLineaTotales > 32 ||
                    anchoLineaIva > 32 ||
                    (maxValorUnitarioLength + maxValorTotalLength + 10) > 32;

            if (usarLayoutAlternativo) {
                // Layout alternativo: 2 líneas por producto
                escpos.writeLF("Producto");
                escpos.writeLF("--------------------------------");
                for (int i = 0; i < facturaLegalItemList.size(); i++) {
                    FacturaLegalItem vi = facturaLegalItemList.get(i);
                    escpos.writeLF(descripcionList.get(i));

                    // Línea 1: Cantidad y IVA (32 caracteres totales)
                    escpos.write("Cantidad");
                    for (int j = 8; j < 25; j++) {
                        escpos.write(" ");
                    }
                    escpos.writeLF("Iva");

                    // Mostrar cantidad sin IVA (ej: "328 (UN)")
                    String cantidadSinIvaStr = cantidadSinIvaList.get(i);
                    escpos.write(cantidadSinIvaStr);
                    // Rellenar espacios hasta la columna 25 (17 espacios después de "Cantidad" de 8
                    // chars = 25)
                    for (int j = cantidadSinIvaStr.length(); j < 25; j++) {
                        escpos.write(" ");
                    }
                    // Mostrar IVA separado
                    escpos.writeLF(ivaList.get(i) + "%");

                    // Línea 2: Precio Unitario y Total (32 caracteres: 15 para P.U. y 17 para
                    // Total)
                    escpos.write("P.U.");
                    for (int j = 4; j < 15; j++) {
                        escpos.write(" ");
                    }
                    escpos.writeLF("Total");

                    // P.U. con máximo 15 caracteres y Total alineado a la izquierda en la misma
                    // línea
                    String puStr = valorUnitarioList.get(i);
                    escpos.write(puStr);
                    // Rellenar espacios hasta 15 caracteres
                    for (int j = puStr.length(); j < 15; j++) {
                        escpos.write(" ");
                    }

                    // Total alineado a la izquierda en la misma línea (empieza en columna 16, sin
                    // espacios adicionales)
                    String totalStr = valorTotalList.get(i);
                    // Si el total es muy largo, truncar o ajustar
                    if (totalStr.length() > 17) {
                        // Si excede, usar solo 17 caracteres
                        totalStr = totalStr.substring(0, Math.min(17, totalStr.length()));
                    }
                    // Escribir el Total en la misma línea (alineado a la izquierda después del
                    // P.U.)
                    escpos.write(totalStr);
                    escpos.writeLF(""); // Nueva línea al final

                    escpos.writeLF("--------------------------------");
                }
            } else {
                // Layout normal
                escpos.writeLF("Producto");
                escpos.writeLF("Cant  IVA   P.U              P.T");
                escpos.writeLF("--------------------------------");
                for (int i = 0; i < facturaLegalItemList.size(); i++) {
                    FacturaLegalItem vi = facturaLegalItemList.get(i);
                    escpos.writeLF(descripcionList.get(i));
                    escpos.write(new Style().setBold(true), cantidadStrList.get(i));

                    for (int j = 14; j > cantidadStrList.get(i).length(); j--) {
                        escpos.write(" ");
                    }
                    escpos.write(valorUnitarioList.get(i));
                    for (int j = 16 - valorUnitarioList.get(i).length(); j > valorTotalList.get(i).length(); j--) {
                        escpos.write(" ");
                    }
                    escpos.writeLF(valorTotalList.get(i));
                }
            }

            // Sección de totales en moneda extranjera
            escpos.writeLF("------------Totales-------------");

            // Usar layout alternativo si hay overflow
            if (usarLayoutAlternativo) {
                escpos.writeLF(monedaExtranjera.toUpperCase() + ".");
                escpos.writeLF("Parcial: " + parcialExtStr);
                escpos.writeLF("Desc.: " + descExtStr);
                escpos.writeLF("Final: " + finalExtStr);
            } else {
                escpos.write("   "); // 4 espacios para moneda
                escpos.write("   Parcial"); // 7 chars
                escpos.write("    "); // 2 espacios = 9 total
                escpos.write("Desc."); // 5 chars
                escpos.write("     "); // 4 espacios = 9 total
                escpos.writeLF("Final"); // 5 chars

                // Línea de moneda extranjera
                escpos.write(monedaExtranjera.toUpperCase() + ". ");
                int espaciosParcialExt = 9 - parcialExtStr.length();
                for (int i = 0; i < espaciosParcialExt; i++) {
                    escpos.write(" ");
                }
                escpos.write(parcialExtStr);

                int espaciosDescExt = 9 - descExtStr.length();
                for (int i = 0; i < espaciosDescExt; i++) {
                    escpos.write(" ");
                }
                escpos.write(descExtStr);

                int espaciosFinalExt = 10 - finalExtStr.length();
                for (int i = 0; i < espaciosFinalExt; i++) {
                    escpos.write(" ");
                }
                escpos.writeLF(finalExtStr);
            }

            // Sección de liquidación IVA en moneda extranjera
            escpos.writeLF("--------Liquidacion IVA---------");
            if (usarLayoutAlternativo) {
                escpos.writeLF("Gravadas 10%: " + totalIva10ExtS);
                escpos.writeLF("Gravadas 5%: " + totalIva5ExtS);
                escpos.writeLF("Exentas: " + totalIva0ExtS);
                escpos.writeLF("Total IVA: " + totalFinalIvaExtS);
            } else {
                escpos.write("Gravadas 10%:");
                for (int i = 19; i > totalIva10ExtS.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(totalIva10ExtS);
                escpos.write("Gravadas 5%: ");
                for (int i = 19; i > totalIva5ExtS.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(totalIva5ExtS);
                escpos.write("Exentas:     ");
                for (int i = 19; i > totalIva0ExtS.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(totalIva0ExtS);
                escpos.write("Total IVA:   ");
                for (int i = 19; i > totalFinalIvaExtS.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(totalFinalIvaExtS);
            }

            escpos.writeLF("--------------------------------");

            // Generar código QR si es documento electrónico
            if (facturaLegal.getTimbradoDetalle().getTimbrado().getIsElectronico() != null
                    && facturaLegal.getTimbradoDetalle().getTimbrado().getIsElectronico()) {

                Optional<DocumentoElectronico> documentoElectronicoOpt = documentoElectronicoService
                        .findByFacturaLegalId(facturaLegal.getId(), facturaLegal.getSucursalId());
                DocumentoElectronico documentoElectronico = documentoElectronicoOpt.orElse(null);

                String cdc = documentoElectronico != null ? documentoElectronico.getCdc() : null;
                String urlQr = documentoElectronico != null ? documentoElectronico.getUrlQr() : null;

                // Imprimir QR como imagen generada por ZXing
                if (urlQr != null) {
                    try {
                        BufferedImage qrImage = QRCodeImageGenerator.generateQRCodeImage(urlQr, 250, 250);

                        imageWrapper.setJustification(EscPosConst.Justification.Center);
                        EscPosImage escposImageQR = new EscPosImage(new CoffeeImageImpl(qrImage), algorithm);

                        escpos.write(imageWrapper, escposImageQR);
                        escpos.feed(1);

                    } catch (Exception e) {
                        e.printStackTrace();
                        escpos.writeLF(center, "ERROR: No se pudo generar el código QR.");
                        escpos.writeLF(center, "URL de consulta:");
                        escpos.writeLF(center, urlQr);
                        escpos.feed(1);
                    }
                }

                escpos.writeLF(center,
                        "Consulte la validez de esta Factura Electronica con el numero de CDC impreso abajo en:");
                escpos.writeLF(center, "https://ekuatia.set.gov.py/consultas");

                if (cdc != null) {
                    String cdcFormateado = cdc.replaceAll("\\s+", "");
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < cdcFormateado.length(); i += 4) {
                        if (i > 0)
                            sb.append(" ");
                        sb.append(cdcFormateado.substring(i, Math.min(i + 4, cdcFormateado.length())));
                    }
                    escpos.writeLF(center, sb.toString());
                }

                escpos.writeLF(center,
                        "ESTE DOCUMENTO ES UNA REPRESENTACION GRAFICA DE UN DOCUMENTO ELECTRONICO (XML)");
                escpos.writeLF("--------------------------------");
            }
            escpos.feed(1);
            escpos.writeLF(center.setBold(true), "GRACIAS POR LA PREFERENCIA");
            escpos.feed(5);

            try {
                if (true) {
                    escpos.close();
                    if (destino == null) {
                        printerOutputStream.close();
                        this.printerOutputStream = null;
                    }
                } else {
                    this.printerOutputStream = printerOutputStream;
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    /**
     * Formatea un valor en moneda extranjera con 2 decimales si no tiene más,
     * o 3 decimales redondeando hacia arriba si tiene más.
     * Usa separadores de miles (punto) y coma decimal según Locale.GERMAN.
     * 
     * @param valor El valor a formatear
     * @return String formateado con 2 o 3 decimales según necesidad, con
     *         separadores de miles
     */
    private String formatearMonedaExtranjera(Double valor) {
        if (valor == null || valor.isNaN() || valor.isInfinite()) {
            return "0,00";
        }

        // Usar BigDecimal para precisión
        BigDecimal valorBD = BigDecimal.valueOf(valor);

        // Redondear a 2 decimales
        BigDecimal valor2Dec = valorBD.setScale(2, RoundingMode.HALF_UP);

        // Verificar si el valor tiene más de 2 decimales significativos
        // Si el valor original es diferente al redondeado a 2 decimales por más de
        // 0.005,
        // significa que tiene decimales significativos más allá de 2
        BigDecimal diferencia = valorBD.subtract(valor2Dec).abs();
        BigDecimal umbral = new BigDecimal("0.005"); // Mitad del último decimal de 2 cifras

        // Usar NumberFormat para obtener separadores de miles automáticamente
        NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.GERMAN);

        // Si la diferencia es mayor al umbral, usar 3 decimales redondeando hacia
        // arriba
        if (diferencia.compareTo(umbral) > 0) {
            // Tiene más decimales significativos, usar 3 decimales redondeando hacia arriba
            BigDecimal valor3Dec = valorBD.setScale(3, RoundingMode.UP);
            numberFormat.setMinimumFractionDigits(3);
            numberFormat.setMaximumFractionDigits(3);
            return numberFormat.format(valor3Dec.doubleValue());
        } else {
            // No tiene más decimales significativos, usar 2 decimales
            numberFormat.setMinimumFractionDigits(2);
            numberFormat.setMaximumFractionDigits(2);
            return numberFormat.format(valor2Dec.doubleValue());
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
        try (FileOutputStream fos = new FileOutputStream(
                "facturas-bodega-franco-" + fechaInicio.substring(0, 10) + ".zip");
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

    // Field resolver for documentoElectronico
    public DocumentoElectronico documentoElectronico(FacturaLegal facturaLegal) {
        if (facturaLegal == null || facturaLegal.getId() == null || facturaLegal.getSucursalId() == null) {
            return null;
        }
        Optional<DocumentoElectronico> de = documentoElectronicoService.findByFacturaLegalId(
                facturaLegal.getId(),
                facturaLegal.getSucursalId());
        return de.orElse(null);
    }

    // Mutation to update factura legal
    @Transactional
    public FacturaLegal updateFacturaLegal(FacturaLegalInput input) {
        if (input.getId() == null || input.getSucursalId() == null) {
            throw new IllegalArgumentException("ID y SucursalId son requeridos");
        }

        ModelMapper m = new ModelMapper();
        FacturaLegal entity = m.map(input, FacturaLegal.class);

        // Set cliente if provided
        if (input.getClienteId() != null) {
            Cliente cliente = clienteService.findById(input.getClienteId()).orElse(null);
            entity.setCliente(cliente);
        }

        // Set usuario if provided
        if (input.getUsuarioId() != null) {
            entity.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }

        return service.update(entity);
    }

    // Mutation to nominate electronic invoice
    @Transactional
    public Boolean nominarFacturaElectronica(Long facturaLegalId, Long sucursalId, Long clienteId) {
        try {
            // Get factura
            FacturaLegal factura = service.findByIdAndSucursalId(facturaLegalId, sucursalId);
            if (factura == null) {
                throw new IllegalArgumentException("Factura no encontrada");
            }

            // Validate it's electronic
            if (factura.getCdc() == null || factura.getCdc().isEmpty()) {
                throw new IllegalArgumentException("La factura no es electrónica");
            }

            // Validate it's not already nominada
            if (factura.getCliente() != null) {
                throw new IllegalStateException("La factura ya está nominada");
            }

            // Get cliente
            Cliente cliente = clienteService.findById(clienteId).orElse(null);
            if (cliente == null) {
                throw new IllegalArgumentException("Cliente no encontrado");
            }

            // Call SIFEN service to nominate
            sifenEventoService.nominarReceptor(factura.getCdc(), cliente);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error al nominar factura electrónica: " + e.getMessage());
        }
    }

    public String cancelarFacturaLegal(Long facturaLegalId, Long sucursalId, Boolean cancelarVenta) {
        try {
            // Buscar la factura
            FacturaLegal factura = service.findByIdAndSucursalId(facturaLegalId, sucursalId);
            if (factura == null) {
                return "ERROR: Factura no encontrada";
            }

            // Verificar si ya está cancelada
            if (factura.getActivo() != null && !factura.getActivo()) {
                return "ERROR: La factura ya está cancelada";
            }

            // Determinar si es factura electrónica
            boolean esElectronica = factura.getCdc() != null && !factura.getCdc().isEmpty();

            if (esElectronica) {
                // Factura electrónica - usar SIFEN
                // El método cancelarDE ya maneja su propia transacción y guarda el evento
                try {
                    sifenEventoService.cancelarDE(factura.getCdc(), "Cancelación solicitada por usuario");

                    // SIFEN aceptó el evento (puede estar PENDIENTE o APROBADO)
                    // Si también quiere cancelar la venta
                    if (cancelarVenta && factura.getVenta() != null) {
                        boolean ventaCancelada = ventaService.cancelarVenta(factura.getVenta());
                        if (ventaCancelada) {
                            return "EXITO: Factura electrónica cancelada y venta cancelada";
                        } else {
                            return "EXITO: Factura electrónica cancelada (venta no pudo ser cancelada)";
                        }
                    } else {
                        // Solo cancelar factura - marcar como inactiva
                        factura.setActivo(false);
                        service.save(factura);
                        return "EXITO: Factura electrónica cancelada (venta mantiene activa)";
                    }

                } catch (Exception e) {
                    // SIFEN rechazó el evento - el evento ya fue guardado en la BD con estado
                    // RECHAZADO
                    String mensajeError = e.getMessage() != null ? e.getMessage() : "Error desconocido";
                    return "ERROR_SIFEN: " + mensajeError;
                }

            } else {
                // Factura no electrónica
                if (cancelarVenta && factura.getVenta() != null) {
                    // Cancelar venta
                    boolean ventaCancelada = ventaService.cancelarVenta(factura.getVenta());
                    if (ventaCancelada) {
                        // Marcar factura como inactiva
                        factura.setActivo(false);
                        service.save(factura);
                        return "EXITO: Factura cancelada y venta cancelada";
                    } else {
                        return "ERROR: No se pudo cancelar la venta";
                    }
                } else {
                    // Solo cancelar factura
                    factura.setActivo(false);
                    service.save(factura);
                    return "EXITO: Factura cancelada (venta mantiene activa)";
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: " + e.getMessage();
        }
    }

    @Transactional
    public SaveFacturaLegalToFilialResponse saveFacturaLegalToFilial(
            FacturaLegalInput entity,
            List<FacturaLegalItemInput> detalleList,
            Long sucursalId,
            Long timbradoDetalleId,
            Long monedaId,
            Double tipoCambio) {
        try {
            // Si se proporciona monedaId y tipoCambio, actualizar el input
            if (monedaId != null && tipoCambio != null) {
                // Aquí podrías obtener la denominación de la moneda si es necesario
                // Por ahora, usamos el tipoCambio directamente
                entity.setTipoCambio(tipoCambio);
            }

            // Llamar al servicio REST para crear la factura en el servidor filial
            FacturaLegalFilialResponse response = facturaLegalFilialService.crearFacturaLegalEnFilial(
                    entity,
                    detalleList != null ? detalleList : new ArrayList<>(),
                    sucursalId,
                    timbradoDetalleId);

            // Mapear la respuesta del servidor filial a la respuesta GraphQL
            SaveFacturaLegalToFilialResponse graphQLResponse = new SaveFacturaLegalToFilialResponse();
            graphQLResponse.setFacturaId(response.getId());
            graphQLResponse.setNumeroFactura(response.getNumeroFactura());
            graphQLResponse.setCdc(response.getCdc());
            graphQLResponse.setUrlQr(response.getUrlQr());
            graphQLResponse.setEstadoDocumentoElectronico(response.getEstadoDocumentoElectronico());
            graphQLResponse.setMensajeRespuestaSifen(response.getMensajeRespuestaSifen());
            graphQLResponse.setDocumentoElectronicoGenerado(response.getDocumentoElectronicoGenerado());

            return graphQLResponse;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error al crear factura en servidor filial: " + e.getMessage(), e);
        }
    }

    public String descargarXmlFacturaElectronica(Long id, Long sucId) {
        try {
            // Obtener factura legal
            FacturaLegal factura = service.findByIdAndSucursalId(id, sucId);
            if (factura == null) {
                throw new IllegalArgumentException("Factura no encontrada");
            }

            // Obtener documento electrónico
            Optional<DocumentoElectronico> docOpt = documentoElectronicoService.findByFacturaLegalId(id, sucId);
            if (!docOpt.isPresent()) {
                throw new IllegalArgumentException("La factura no tiene documento electrónico asociado");
            }

            DocumentoElectronico documentoElectronico = docOpt.get();
            String xmlOriginal = documentoElectronico.getXmlOriginal();

            if (xmlOriginal == null || xmlOriginal.isEmpty()) {
                throw new IllegalArgumentException("El documento electrónico no tiene XML original");
            }

            // Convertir XML a Base64
            String base64String = Base64.getEncoder().encodeToString(xmlOriginal.getBytes("UTF-8"));
            return base64String;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error al descargar XML: " + e.getMessage(), e);
        }
    }

    public String descargarPdfFacturaElectronica(Long id, Long sucId) {
        try {
            // Obtener factura legal con todas las relaciones necesarias
            FacturaLegal factura = service.findByIdAndSucursalId(id, sucId);
            if (factura == null) {
                throw new IllegalArgumentException("Factura no encontrada");
            }

            // Obtener documento electrónico
            Optional<DocumentoElectronico> docOpt = documentoElectronicoService.findByFacturaLegalId(id, sucId);
            if (!docOpt.isPresent()) {
                throw new IllegalArgumentException("La factura no tiene documento electrónico asociado");
            }

            DocumentoElectronico documentoElectronico = docOpt.get();

            // Obtener sucursal
            Sucursal sucursal = sucursalService.findById(sucId).orElse(null);

            // Obtener items de la factura
            List<FacturaLegalItem> items = facturaLegalItemService.findByFacturaLegalId(id, sucId);

            // Preparar datos para el reporte Jasper
            // Verificar si tiene moneda extranjera
            boolean tieneMonedaExtranjera = factura.getMonedaExtranjera() != null
                    && !factura.getMonedaExtranjera().isEmpty();
            Double tipoCambio = factura.getTipoCambio() != null ? factura.getTipoCambio() : 1.0;

            // Cargar y compilar el template Jasper
            File file = ResourceUtils.getFile("classpath:reports/factura-electronica-kude.jrxml");
            JasperReport jasperReport = JasperCompileManager.compileReport(file.getAbsolutePath());

            // Preparar datasource con items
            List<FacturaItemDto> itemDtoList = new ArrayList<>();
            for (FacturaLegalItem item : items) {
                FacturaItemDto dto = new FacturaItemDto();
                dto.setId(item.getId());
                dto.setDescripcion(item.getDescripcion() != null ? item.getDescripcion() : "");
                dto.setCantidad(item.getCantidad() != null ? item.getCantidad() : 0.0f);
                dto.setPrecioUnitario(item.getPrecioUnitario() != null ? item.getPrecioUnitario() : 0.0);
                dto.setIva(item.getIva() != null ? item.getIva() : 0);
                dto.setTotal(item.getTotal() != null ? item.getTotal() : 0.0);

                // Obtener presentación desde el item o fallback desde el ventaItem
                Presentacion presentacion = item.getPresentacion();
                if (presentacion == null && item.getVentaItem() != null) {
                    presentacion = item.getVentaItem().getPresentacion();
                }

                // Obtener ID de la presentación para la columna de código
                String codigo = "";
                if (presentacion != null && presentacion.getId() != null) {
                    codigo = String.valueOf(presentacion.getId());
                }
                dto.setCodigo(codigo);
                // Obtener la cantidad de la presentación
                String descPresentacion = "";
                if (presentacion != null && presentacion.getCantidad() != null) {
                    descPresentacion = df.format(presentacion.getCantidad());
                }
                dto.setDescripcionPresentacion(descPresentacion);

                // Si tiene moneda extranjera, convertir valores
                if (tieneMonedaExtranjera && tipoCambio > 0) {
                    dto.setPrecioUnitario(dto.getPrecioUnitario() / tipoCambio);
                    dto.setTotal(dto.getTotal() / tipoCambio);
                }

                itemDtoList.add(dto);
            }

            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(itemDtoList);

            // Preparar parámetros del reporte
            Map<String, Object> parameters = new HashMap<>();

            // Logo - solo si NO tiene moneda extranjera
            if (!tieneMonedaExtranjera) {
                String logoPath = imageService.getImagePath() + File.separator + "logo.png";
                File logoFile = new File(logoPath);
                if (logoFile.exists()) {
                    parameters.put("logo", logoPath);
                } else {
                    parameters.put("logo", "");
                }
            } else {
                parameters.put("logo", "");
            }

            // Datos del emisor (Timbrado)
            if (factura.getTimbradoDetalle() != null && factura.getTimbradoDetalle().getTimbrado() != null) {
                com.franco.dev.domain.financiero.Timbrado timbrado = factura.getTimbradoDetalle().getTimbrado();
                parameters.put("razonSocial", timbrado.getRazonSocial() != null ? timbrado.getRazonSocial() : "");
                parameters.put("rucEmisor", timbrado.getRuc() != null ? timbrado.getRuc() : "");
                parameters.put("numeroTimbrado", timbrado.getNumero() != null ? timbrado.getNumero() : "");
                parameters.put("fechaInicioVigencia",
                        timbrado.getFechaInicio() != null ? DateUtils.toString(timbrado.getFechaInicio()) : "");

                // Dirección del emisor: formatear como "{{direccion}}, {{ciudad}},
                // {{departamento}}"
                StringBuilder direccionEmisorBuilder = new StringBuilder();
                String direccion = factura.getTimbradoDetalle().getDireccion() != null
                        ? factura.getTimbradoDetalle().getDireccion()
                        : "";
                String ciudad = factura.getTimbradoDetalle().getCiudad() != null
                        ? factura.getTimbradoDetalle().getCiudad()
                        : "";
                String departamento = factura.getTimbradoDetalle().getDepartamento() != null
                        ? factura.getTimbradoDetalle().getDepartamento()
                        : "";

                if (!direccion.isEmpty()) {
                    direccionEmisorBuilder.append(direccion);
                }
                if (!ciudad.isEmpty()) {
                    if (direccionEmisorBuilder.length() > 0) {
                        direccionEmisorBuilder.append(", ");
                    }
                    direccionEmisorBuilder.append(ciudad);
                }
                if (!departamento.isEmpty()) {
                    if (direccionEmisorBuilder.length() > 0) {
                        direccionEmisorBuilder.append(", ");
                    }
                    direccionEmisorBuilder.append(departamento);
                }

                // Si no hay datos en timbradoDetalle, usar datos del timbrado como fallback
                if (direccionEmisorBuilder.length() == 0 && timbrado.getDomicilioFiscalDireccion() != null) {
                    direccionEmisorBuilder.append(timbrado.getDomicilioFiscalDireccion());
                }

                parameters.put("direccionEmisor", direccionEmisorBuilder.toString());

                if (factura.getTimbradoDetalle().getTelefono() != null) {
                    parameters.put("telefonoEmisor", factura.getTimbradoDetalle().getTelefono());
                } else if (timbrado.getTelefono() != null) {
                    parameters.put("telefonoEmisor", timbrado.getTelefono());
                } else {
                    parameters.put("telefonoEmisor", "");
                }

                parameters.put("emailEmisor", timbrado.getEmail() != null ? timbrado.getEmail() : "");
                parameters.put("actividadEconomica",
                        timbrado.getDescActividadEconomicaPrincipal() != null
                                ? timbrado.getDescActividadEconomicaPrincipal()
                                : "");
            } else {
                parameters.put("razonSocial", "");
                parameters.put("rucEmisor", "");
                parameters.put("numeroTimbrado", "");
                parameters.put("fechaInicioVigencia", "");
                parameters.put("direccionEmisor", "");
                parameters.put("telefonoEmisor", "");
                parameters.put("emailEmisor", "");
                parameters.put("actividadEconomica", "");
            }

            // Datos de la factura
            // Formatear número de factura:
            // {codigoEstablecimiento}-{puntoExpedicion}-{numeroFactura}
            String codigoEstablecimiento = sucursal != null && sucursal.getCodigoEstablecimientoFactura() != null
                    ? sucursal.getCodigoEstablecimientoFactura()
                    : "000";
            String puntoExpedicion = factura.getTimbradoDetalle() != null
                    && factura.getTimbradoDetalle().getPuntoExpedicion() != null
                            ? factura.getTimbradoDetalle().getPuntoExpedicion()
                            : "000";
            String numeroFacturaFormateado = "";
            if (factura.getNumeroFactura() != null) {
                // Formatear número con padding de 7 dígitos
                String numeroStr = String.format("%07d", factura.getNumeroFactura());
                numeroFacturaFormateado = codigoEstablecimiento + "-" + puntoExpedicion + "-" + numeroStr;
            }
            parameters.put("numeroFactura", numeroFacturaFormateado);
            parameters.put("cdc", documentoElectronico.getCdc() != null ? documentoElectronico.getCdc() : "");
            parameters.put("fechaEmision", factura.getFecha() != null ? DateUtils.toString(factura.getFecha()) : "");
            parameters.put("presupuesto", ""); // No disponible en la entidad actual
            parameters.put("ordenAsociada", ""); // No disponible en la entidad actual

            // Datos del cliente
            parameters.put("rucCliente", factura.getRuc() != null ? factura.getRuc() : "");
            parameters.put("nombreCliente", factura.getNombre() != null ? factura.getNombre() : "");
            parameters.put("fantasiaCliente", factura.getNombre() != null ? factura.getNombre() : "");
            parameters.put("direccionCliente", factura.getDireccion() != null ? factura.getDireccion() : "");
            parameters.put("ciudadCliente", ""); // No disponible directamente
            parameters.put("departamentoCliente", ""); // No disponible directamente
            parameters.put("telefonoCliente", ""); // No disponible directamente
            parameters.put("emailCliente", ""); // No disponible directamente

            // Condiciones de venta
            parameters.put("contado", factura.getCredito() != null && !factura.getCredito());
            parameters.put("credito", factura.getCredito() != null && factura.getCredito());
            parameters.put("cuotas", "1"); // No disponible directamente
            parameters.put("moneda", tieneMonedaExtranjera ? factura.getMonedaExtranjera() : "PYG");
            parameters.put("tipoCambio", tieneMonedaExtranjera && tipoCambio != null ? tipoCambio.toString() : "");
            parameters.put("plazo", ""); // No disponible directamente

            // Totales - convertir si tiene moneda extranjera
            Double total0 = factura.getTotalParcial0() != null ? factura.getTotalParcial0() : 0.0;
            Double total5 = factura.getTotalParcial5() != null ? factura.getTotalParcial5() : 0.0;
            Double total10 = factura.getTotalParcial10() != null ? factura.getTotalParcial10() : 0.0;
            Double iva5 = factura.getIvaParcial5() != null ? factura.getIvaParcial5() : 0.0;
            Double iva10 = factura.getIvaParcial10() != null ? factura.getIvaParcial10() : 0.0;
            Double totalFinal = factura.getTotalFinal() != null ? factura.getTotalFinal() : 0.0;

            if (tieneMonedaExtranjera && tipoCambio > 0) {
                total0 = total0 / tipoCambio;
                total5 = total5 / tipoCambio;
                total10 = total10 / tipoCambio;
                iva5 = iva5 / tipoCambio;
                iva10 = iva10 / tipoCambio;
                totalFinal = totalFinal / tipoCambio;
            }

            parameters.put("subtotalExentas", total0);
            parameters.put("subtotal5", total5);
            parameters.put("subtotal10", total10);
            parameters.put("totalOperacion", totalFinal);
            parameters.put("totalIva5", iva5);
            parameters.put("totalIva10", iva10);
            parameters.put("totalIva", iva5 + iva10);
            parameters.put("totalFinal", totalFinal);

            // Total en Guaraníes - si tiene moneda extranjera, calcular como totalFinal *
            // tipoCambio
            // El totalFinal original en guaraníes
            Double totalFinalOriginal = factura.getTotalFinal() != null ? factura.getTotalFinal() : 0.0;
            Double totalEnGuarani = totalFinalOriginal;
            if (tieneMonedaExtranjera && tipoCambio > 0) {
                // Si tiene moneda extranjera, totalEnGuarani = totalFinal en moneda extranjera
                // * tipoCambio
                totalEnGuarani = totalFinal * tipoCambio;
            }
            parameters.put("totalEnGuarani", totalEnGuarani);

            // URL de validación SET
            parameters.put("urlValidacion", "https://ekuatia.set.gov.py/consultas/");

            // QR Code - generar imagen del QR desde urlQr del documento electrónico
            String urlQr = documentoElectronico.getUrlQr() != null ? documentoElectronico.getUrlQr() : "";
            String qrImagePath = "";
            if (urlQr != null && !urlQr.isEmpty()) {
                try {
                    // Generar imagen QR
                    BufferedImage qrImage = QRCodeImageGenerator.generateQRCodeImage(urlQr, 200, 200);

                    // Guardar imagen temporalmente
                    File tempQrFile = File.createTempFile("qr_", ".png");
                    ImageIO.write(qrImage, "PNG", tempQrFile);
                    qrImagePath = tempQrFile.getAbsolutePath();

                    // El archivo temporal se eliminará cuando se cierre el proceso o se puede
                    // eliminar después de generar el PDF
                } catch (Exception e) {
                    e.printStackTrace();
                    // Si falla la generación del QR, continuar sin él
                    qrImagePath = "";
                }
            }
            parameters.put("qrImagePath", qrImagePath);

            // Generar PDF
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);

            // Limpiar archivo temporal del QR si existe
            if (!qrImagePath.isEmpty()) {
                try {
                    File tempFile = new File(qrImagePath);
                    if (tempFile.exists()) {
                        tempFile.delete();
                    }
                } catch (Exception e) {
                    // Ignorar errores al eliminar el archivo temporal
                }
            }
            byte[] pdfBytes = JasperExportManager.exportReportToPdf(jasperPrint);
            String base64String = Base64.getEncoder().encodeToString(pdfBytes);
            return base64String;

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error al generar PDF: " + e.getMessage(), e);
        }
    }

    // DTO para items del reporte PDF
    public static class FacturaItemDto {
        private Long id;
        private String codigo;
        private String descripcion;
        private Integer iva;
        private String descripcionPresentacion;
        private Float cantidad;
        private Double precioUnitario;
        private Double total;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getCodigo() {
            return codigo;
        }

        public void setCodigo(String codigo) {
            this.codigo = codigo;
        }

        public String getDescripcion() {
            return descripcion;
        }

        public void setDescripcion(String descripcion) {
            this.descripcion = descripcion;
        }

        public Integer getIva() {
            return iva;
        }

        public void setIva(Integer iva) {
            this.iva = iva;
        }

        public String getDescripcionPresentacion() {
            return descripcionPresentacion;
        }

        public void setDescripcionPresentacion(String descripcionPresentacion) {
            this.descripcionPresentacion = descripcionPresentacion;
        }

        public Float getCantidad() {
            return cantidad;
        }

        public void setCantidad(Float cantidad) {
            this.cantidad = cantidad;
        }

        public Double getPrecioUnitario() {
            return precioUnitario;
        }

        public void setPrecioUnitario(Double precioUnitario) {
            this.precioUnitario = precioUnitario;
        }

        public Double getTotal() {
            return total;
        }

        public void setTotal(Double total) {
            this.total = total;
        }
    }
}
