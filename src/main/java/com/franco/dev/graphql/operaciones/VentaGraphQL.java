package com.franco.dev.graphql.operaciones;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.Venta;

import com.franco.dev.domain.operaciones.VentaItem;

import com.franco.dev.domain.operaciones.VentaPorFuncionario;
import com.franco.dev.domain.operaciones.VentaPorSucursal;
import com.franco.dev.domain.operaciones.dto.LucroPorFuncionarioDto;
import com.franco.dev.domain.operaciones.dto.MovimientoReporteDetalladoDto;
import com.franco.dev.domain.operaciones.dto.ReporteVentaDetalladoDto;
import com.franco.dev.domain.operaciones.dto.VentaItemReporteDetalladoDto;
import com.franco.dev.domain.operaciones.dto.VentaObservacionReporteDetalladoDto;
import com.franco.dev.domain.operaciones.dto.VentaPorPeriodoV1Dto;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Familia;
import com.franco.dev.domain.productos.Subfamilia;
import com.franco.dev.graphql.financiero.FacturaLegalGraphQL;
import com.franco.dev.graphql.financiero.VentaCreditoGraphQL;
import com.franco.dev.graphql.operaciones.input.LucroPorFuncionarioResponse;
import com.franco.dev.graphql.operaciones.input.LucroPorFuncionarioSummary;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.graphql.operaciones.input.CobroInput;
import com.franco.dev.graphql.operaciones.input.VentaInput;
import com.franco.dev.graphql.operaciones.input.VentaItemInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.FormaPagoService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.MovimientoCajaService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.operaciones.DeliveryService;
import com.franco.dev.service.operaciones.VentaItemService;
import com.franco.dev.service.operaciones.VentaObservacionService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.domain.operaciones.dto.ReporteVentaItemDto;
import com.franco.dev.domain.operaciones.VentaObservacion;
import com.franco.dev.service.operaciones.CobroDetalleService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.CostosPorProductoService;
import com.franco.dev.service.productos.FamiliaService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.service.productos.SubFamiliaService;
import com.franco.dev.service.reports.TicketReportService;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.service.utils.PrintingService;
import com.franco.dev.utilitarios.print.escpos.EscPos;
import com.franco.dev.utilitarios.print.escpos.EscPosConst;
import com.franco.dev.utilitarios.print.escpos.Style;
import com.franco.dev.utilitarios.print.escpos.barcode.QRCode;
import com.franco.dev.utilitarios.print.escpos.image.*;
import com.franco.dev.utilitarios.print.output.PrinterOutputStream;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.print.PrintService;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.franco.dev.service.utils.PrintingService.resize;

@Component
public class VentaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final Logger log = LoggerFactory.getLogger(VentaGraphQL.class);
    @Autowired
    public VentaItemGraphQL ventaItemGraphQL;
    @Autowired
    public CobroGraphQL cobroGraphQL;
    @Autowired
    private VentaService service;
    @Autowired
    private VentaItemService ventaItemService;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private ClienteService clienteService;
    @Autowired
    private FormaPagoService formaPagoService;
    @Autowired
    private PdvCajaService pdvCajaService;
    @Autowired
    private TicketReportService ticketReportService;
    @Autowired
    private ImageService imageService;
    @Autowired
    private SucursalService sucursalService;
    @Autowired
    private MovimientoCajaService movimientoCajaService;

    private PrinterOutputStream printerOutputStream;
    @Autowired
    private ProductoService productoService;
    @Autowired
    private PrintingService printingService;
    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private CostosPorProductoService costosPorProductoService;

    @Autowired
    private VentaCreditoGraphQL ventaCreditoGraphQL;

    @Autowired
    private FacturaLegalGraphQL facturaLegalGraphQL;

    @Autowired
    private MultiTenantService multiTenantService;

    @Autowired
    private ImpresionService impresionService;

    @Autowired
    private CobroDetalleService cobroDetalleService;

    @Autowired
    private VentaObservacionService ventaObservacionService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private FamiliaService familiaService;

    @Autowired
    private SubFamiliaService subFamiliaService;

    private Sucursal sucursal;

    public Optional<Venta> venta(Long id, Long sucId) {
        return service.findById(new EmbebedPrimaryKey(id, sucId));
    }

    public List<Venta> ventas(int page, int size, Long sucId) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    // public List<Venta> ventaSearch(String texto){
    // return service.findByAll(texto);
    // }

    public Venta saveVenta(VentaInput ventaInput, List<VentaItemInput> ventaItemList, CobroInput cobroInput,
            List<CobroDetalleInput> cobroDetalleList, Boolean ticket, String printerName, String local)
            throws Exception {
        Venta venta = null;
        Cobro cobro = cobroGraphQL.saveCobro(cobroInput, cobroDetalleList, ventaInput.getCajaId());
        List<VentaItem> ventaItemList1 = new ArrayList<>();
        if (cobro != null) {
            ModelMapper m = new ModelMapper();
            Venta e = m.map(ventaInput, Venta.class);
            if (ventaInput.getUsuarioId() != null)
                e.setUsuario(usuarioService.findById(ventaInput.getUsuarioId()).orElse(null));
            if (ventaInput.getClienteId() != null)
                e.setCliente(clienteService.findById(ventaInput.getClienteId()).orElse(null));
            if (ventaInput.getFormaPagoId() != null)
                e.setFormaPago(formaPagoService.findById(ventaInput.getFormaPagoId()).orElse(null));
            if (ventaInput.getCajaId() != null)
                e.setCaja(pdvCajaService.findById(e.getCaja().getId(), e.getCaja().getSucursalId()));
            if (ventaInput.getDeliveryId() != null)
                e.setDelivery(deliveryService
                        .findById(new EmbebedPrimaryKey(ventaInput.getDeliveryId(), ventaInput.getSucursalId()))
                        .orElse(null));
            e.setCobro(cobro);
            venta = service.save(e);
            if (venta != null) {
                ventaItemList1 = ventaItemGraphQL.saveVentaItemList(ventaItemList, venta.getId());
            }
        }
        if (venta.getId() == null) {
            deshacerVenta(venta, cobro, venta.getSucursalId());
        } else {
            try {
                if (ticket)
                    printTicket58mm(venta, cobro, ventaItemList1, cobroDetalleList, false, printerName, local);
            } catch (Exception e) {
                return venta;
            }
        }
        return venta;
    }

    public Boolean deleteVenta(Long id, Long sucId) {
        Boolean ok = service.deleteById(new EmbebedPrimaryKey(id, sucId));
        return ok;
    }

    public Long countVenta() {
        return service.count();
    }

    public void deshacerVenta(Venta venta, Cobro cobro, Long sucId) {
        if (cobro != null) {
            cobroGraphQL.deleteCobro(cobro.getId(), sucId);
        }
    }

    public void printTicket58mm(Venta venta, Cobro cobro, List<VentaItem> ventaItemList,
            List<CobroDetalleInput> cobroDetalleList, Boolean reimpresion, String printerName, String local)
            throws Exception {
        PrintService selectedPrintService = null;
        // if (sucursal == null) {
        // sucursal = sucursalService.sucursalActual();
        // }
        Double descuento = 0.0;
        Double aumento = 0.0;
        Double vueltoGs = 0.0;
        Double vueltoRs = 0.0;
        Double vueltoDs = 0.0;
        Double pagadoGs = 0.0;
        Double pagadoRs = 0.0;
        Double pagadoDs = 0.0;
        for (CobroDetalleInput cdi : cobroDetalleList) {
            if (cdi.getAumento()) {
                aumento += cdi.getValor() * cdi.getCambio();
            }
            if (cdi.getDescuento()) {
                aumento += cdi.getValor() * cdi.getCambio();
            }
            if (cdi.getVuelto()) {
                if (cdi.getMonedaId() == 1) {
                    vueltoGs = cdi.getValor();
                }
                if (cdi.getMonedaId() == 2) {
                    vueltoRs = cdi.getValor();
                }
                if (cdi.getMonedaId() == 3) {
                    vueltoDs = cdi.getValor();
                }
            }
        }
        selectedPrintService = printingService.getPrintService(printerName);
        if (selectedPrintService == null) {
            selectedPrintService = printingService.setPrintService(printerName);
        }
        if (selectedPrintService != null) {
            printerOutputStream = new PrinterOutputStream(selectedPrintService);

            // creating the EscPosImage, need buffered image and algorithm.
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
            // Styles
            Style center = new Style().setJustification(EscPosConst.Justification.Center);

            QRCode qrCode = new QRCode();

            BufferedImage imageBufferedImage = ImageIO.read(new File(imageService.getImagePath() + "logo.png"));
            imageBufferedImage = resize(imageBufferedImage, 200, 100);
            RasterBitImageWrapper imageWrapper = new RasterBitImageWrapper();
            EscPos escpos = null;
            escpos = new EscPos(printerOutputStream);
            Bitonal algorithm = new BitonalThreshold();
            EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
            imageWrapper.setJustification(EscPosConst.Justification.Center);
            escpos.write(imageWrapper, escposImage);
            // escpos.writeLF(center, "Av. Paraguay c/ 30 de julio");
            // escpos.writeLF(center, "Salto del Guairá");
            if (reimpresion == true) {
                escpos.writeLF(center.setBold(true), "REIMPRESION");
            }
            if (sucursal != null) {
                escpos.writeLF(center, "Suc: " + sucursal.getNombre());
            }
            if (local != null) {
                escpos.writeLF(center, "Local: " + local);
            }
            escpos.writeLF(center.setBold(true), "Venta: " + venta.getId());

            if (venta.getUsuario().getPersona().getNombre().length() > 23) {
                escpos.writeLF("Cajero: " + venta.getUsuario().getPersona().getNombre().substring(0, 23));

            } else {
                escpos.writeLF("Cajero: " + venta.getUsuario().getPersona().getNombre());
            }

            escpos.writeLF("Fecha: " + venta.getCreadoEn().format(formatter));
            escpos.writeLF("--------------------------------");

            if (venta.getCliente() != null) {
                escpos.writeLF("Cliente: " + venta.getCliente().getPersona().getNombre().substring(0, 22));
            }
            escpos.writeLF("Producto");
            escpos.writeLF("Cant    P.U                 P.T");
            escpos.writeLF("--------------------------------");
            for (VentaItem vi : ventaItemList) {
                String cantidad = vi.getCantidad().intValue() + " (" + vi.getPresentacion().getCantidad() + ")";
                escpos.writeLF(vi.getProducto().getDescripcion());
                escpos.write(new Style().setBold(true), cantidad);
                String valorUnitario = NumberFormat.getNumberInstance(Locale.GERMAN)
                        .format(vi.getPrecioVenta().getPrecio().intValue());
                String valorTotal = String
                        .valueOf(vi.getPrecioVenta().getPrecio().intValue() * vi.getCantidad().intValue());
                for (int i = 10; i > cantidad.length(); i--) {
                    escpos.write(" ");
                }
                escpos.write(valorUnitario);
                for (int i = 20 - valorUnitario.length(); i > valorTotal.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(NumberFormat.getNumberInstance(Locale.GERMAN)
                        .format(vi.getPrecioVenta().getPrecio().intValue() * vi.getCantidad().intValue()));
            }
            escpos.writeLF("--------------------------------");
            String valorGs = NumberFormat.getNumberInstance(Locale.GERMAN).format(venta.getTotalGs().intValue());
            for (int i = 22; i > valorGs.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(valorGs);
            log.info(valorGs);
            escpos.write("Total Rs: ");
            String valorRs = String.format("%.2f", venta.getTotalRs());
            for (int i = 22; i > valorGs.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(valorRs);
            escpos.write("Total Ds: ");
            // String valorDs = NumberFormat.getNumberInstance(new Locale("sk",
            // "SK")).format(venta.getTotalDs());
            String valorDs = String.format("%.2f", venta.getTotalDs());
            for (int i = 22; i > valorGs.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(valorDs);

            if (sucursal != null && sucursal.getNroDelivery() != null) {
                escpos.write(center, "Delivery? Escaneá el código qr o escribinos al ");
                escpos.writeLF(center, sucursal.getNroDelivery());
            }
            // escpos.write(qrCode.setSize(5).setJustification(EscPosConst.Justification.Center),
            // "wa.me/595986128000");
            escpos.feed(1);
            escpos.writeLF(center.setBold(true), "GRACIAS POR LA PREFERENCIA");
            escpos.feed(5);
            escpos.close();
            printerOutputStream.close();
        }
    }

    public Boolean reimprimirVenta(Long id, String printerName, String local, Long sucId) throws Exception {
        Venta venta = service.findById(new EmbebedPrimaryKey(id, sucId)).orElse(null);
        if (venta != null) {
            Cobro cobro = cobroGraphQL.cobro(venta.getCobro().getId(), sucId).orElse(null);
            List<VentaItem> ventaItemList = ventaItemGraphQL.ventaItemListPorVentaId(venta.getId(), sucId);
            if (cobro != null) {
                List<CobroDetalleInput> cobroDetalleList = new ArrayList<>();
                FacturaLegal facturaLegal = facturaLegalGraphQL.facturaLegalPorVenta(venta.getId(),
                        venta.getSucursalId());
                if (facturaLegal != null) {
                    facturaLegalGraphQL.reimprimirFacturaLegal(facturaLegal.getId(), venta.getSucursalId(),
                            printerName);
                } else {
                    printTicket58mm(venta, cobro, ventaItemList, cobroDetalleList, true, printerName, local);
                }
                return true;
            }
        }
        return false;
    }

    public Page<Venta> ventasPorCajaId(
            Long idVenta,
            Long idCaja,
            Integer page,
            Integer size,
            Boolean asc,
            Long sucId,
            Long formaPago,
            VentaEstado estado,
            Boolean isDelivery,
            Long monedaId,
            Boolean conDescuento,
            Boolean conAumento) {

        Pageable pageable;
        if (page != null) {
            pageable = PageRequest.of(page, size);
        } else {
            pageable = PageRequest.of(0, 15);
        }
        return service.onSearch(idVenta, idCaja, pageable, asc, sucId, formaPago, estado, isDelivery, monedaId,
                conDescuento, conAumento);
    }

    public Page<Venta> searchVenta(
            Long idVenta,
            Long idCaja,
            int page,
            int size,
            Boolean asc,
            Long sucId,
            Long formaPago,
            VentaEstado estado,
            Boolean isDelivery,
            Long monedaId,
            Boolean conDescuento,
            Boolean conAumento) {

        Pageable pageable = PageRequest.of(page, size);
        return service.onSearch(idVenta, idCaja, pageable, asc, sucId, formaPago, estado, isDelivery, monedaId,
                conDescuento, conAumento);
    }

    public List<VentaPorPeriodoV1Dto> ventaPorPeriodo(String inicio, String fin, Long sucId) {
        return service.ventaPorPeriodo(inicio, fin, sucId);
    }

    public List<VentaPorSucursal> ventasPorSucursal(String inicio, String fin) {
        return service.ventaPorSucursal(inicio, fin);
    }

    public List<VentaPorSucursal> ventasPorSucursalAndUsuario(Long usuarioId, String inicio, String fin) {
        return service.ventaPorSucursalAndUsuario(usuarioId, inicio, fin);
    }

    public List<VentaPorFuncionario> ventasPorFuncionario(String inicio, String fin, Long sucId, Long usuarioId) {
        return service.ventasPorFuncionario(inicio, fin, sucId, usuarioId);
    }

    public List<com.franco.dev.domain.operaciones.VentaPorHora> ventasPorHora(String fecha, Long sucId) {
        return service.ventasPorHora(fecha, sucId);
    }

    public List<com.franco.dev.domain.operaciones.VentaPorMes> ventasPorMes(Integer anio, Long sucId) {
        return service.ventasPorMes(anio, sucId);
    }

    public Boolean cancelarVenta(Long id, Long sucId) {
        Venta venta = service.findByIdAndSucursalId(id, sucId);
        return service.cancelarVenta(venta);
    }

    public Page<Venta> ventasGenericFilter(
            Long idVenta,
            Long idCaja,
            Integer page,
            Integer size,
            Boolean asc,
            Long sucId,
            Long formaPago,
            VentaEstado estado,
            Boolean isDelivery,
            Long monedaId,
            Boolean conDescuento,
            Boolean conAumento,
            Boolean conObservacion,
            Long clienteId,
            String fechaInicio,
            String fechaFin) {

        Pageable pageable;
        if (page != null) {
            pageable = PageRequest.of(page, size);
        } else {
            pageable = PageRequest.of(0, 15);
        }
        return service.onGenericSearch(
                idVenta,
                idCaja,
                pageable,
                asc,
                sucId,
                formaPago,
                estado,
                isDelivery,
                monedaId,
                conDescuento,
                conAumento,
                conObservacion,
                clienteId,
                fechaInicio, fechaFin);
    }

    public String reporteGenericVentas(
            Long idVenta,
            Long idCaja,
            Long sucId,
            Long formaPago,
            VentaEstado estado,
            Boolean isDelivery,
            Long monedaId,
            Boolean conDescuento,
            Boolean conAumento,
            Boolean conObservacion,
            Long clienteId,
            String fechaInicio,
            String fechaFin,
            Long usuarioId) {

        // Cargar todas las ventas sin límite de paginación
        Pageable allPages = PageRequest.of(0, Integer.MAX_VALUE);
        Page<Venta> ventaPage = service.onGenericSearch(
                idVenta, idCaja, allPages, false,
                sucId, formaPago, estado, isDelivery, monedaId,
                conDescuento, conAumento, conObservacion, clienteId,
                fechaInicio, fechaFin);

        List<Venta> ventas = ventaPage.getContent();

        // Acumuladores de totales por forma de pago (en Gs.)
        double totalGeneral       = 0.0;
        double totalEfectivo      = 0.0;
        double totalTarjeta       = 0.0;
        double totalConvenio      = 0.0;
        double totalTransferencia = 0.0;
        double totalOtros         = 0.0;

        List<ReporteVentaItemDto> itemList = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        for (Venta v : ventas) {
            double ventaTotalGs = v.getTotalGs() != null ? v.getTotalGs() : 0.0;
            totalGeneral += ventaTotalGs;

            // Resolver forma de pago y moneda usando la lógica del VentaResolver:
            // si la venta no tiene formaPago directo, buscar en el cobro
            FormaPago fp = v.getFormaPago();
            Moneda moneda = null;
            if (v.getCobro() != null) {
                List<CobroDetalle> detalles = cobroDetalleService.findByCobroId(
                        v.getCobro().getId(), v.getSucursalId());
                if (detalles != null && !detalles.isEmpty()) {
                    CobroDetalle primerDetalle = detalles.get(0);
                    if (fp == null) {
                        fp = primerDetalle.getFormaPago();
                    }
                    moneda = primerDetalle.getMoneda();
                }
            }

            String fpDesc = fp != null && fp.getDescripcion() != null
                    ? fp.getDescripcion().toUpperCase() : "";

            if (fpDesc.contains("EFECTIVO")) {
                totalEfectivo += ventaTotalGs;
            } else if (fpDesc.contains("TARJETA")) {
                totalTarjeta += ventaTotalGs;
            } else if (fpDesc.contains("CONVENIO")) {
                totalConvenio += ventaTotalGs;
            } else if (fpDesc.contains("TRANSFERENCIA")) {
                totalTransferencia += ventaTotalGs;
            } else {
                totalOtros += ventaTotalGs;
            }

            // Construir fila para el reporte
            ReporteVentaItemDto dto = new ReporteVentaItemDto();
            dto.setVentaId(v.getId());
            dto.setSucursal(v.getSucursal() != null ? v.getSucursal().getNombre() : "");
            dto.setCliente(v.getCliente() != null && v.getCliente().getPersona() != null
                    ? v.getCliente().getPersona().getNombre() : "");
            dto.setFecha(v.getCreadoEn() != null ? v.getCreadoEn().format(formatter) : "");
            dto.setFormaPago(fp != null && fp.getDescripcion() != null ? fp.getDescripcion() : "");
            dto.setMoneda(moneda != null && moneda.getDenominacion() != null ? moneda.getDenominacion() : "");
            dto.setEstado(v.getEstado() != null ? v.getEstado().toString() : "");
            dto.setTotalGs(ventaTotalGs);
            itemList.add(dto);
        }

        // Resolver nombres reales de los filtros
        Usuario usuario = usuarioId != null
                ? usuarioService.findById(usuarioId).orElse(null) : null;

        String filtroSucursalStr;
        if (sucId != null) {
            Sucursal suc = sucursalService.findById(sucId).orElse(null);
            filtroSucursalStr = suc != null ? suc.getNombre() : "Sucursal " + sucId;
        } else {
            filtroSucursalStr = "Todas";
        }

        String filtroFpStr;
        if (formaPago != null) {
            FormaPago fp = formaPagoService.findById(formaPago).orElse(null);
            filtroFpStr = fp != null ? fp.getDescripcion() : "FormaPago " + formaPago;
        } else {
            filtroFpStr = "Todas";
        }

        String filtroMonedaStr;
        if (monedaId != null) {
            Moneda m = monedaService.findById(monedaId).orElse(null);
            filtroMonedaStr = m != null ? m.getDenominacion() : "Moneda " + monedaId;
        } else {
            filtroMonedaStr = "Todas";
        }

        String filtroEstadoStr  = estado    != null ? estado.toString() : "Todos";
        String filtroClienteStr;
        if (clienteId != null) {
            Cliente c = clienteService.findById(clienteId).orElse(null);
            filtroClienteStr = (c != null && c.getPersona() != null) ? c.getPersona().getNombre() : "ID: " + clienteId;
        } else {
            filtroClienteStr = "Todos";
        }

        String filtroModoStr;
        if (isDelivery == null) {
            filtroModoStr = "Todos";
        } else if (isDelivery) {
            filtroModoStr = "Delivery";
        } else {
            filtroModoStr = "Local";
        }

        String filtroConObsStr   = conObservacion != null && conObservacion ? "Sí" : "Todos";
        String filtroConDescStr  = conDescuento   != null && conDescuento   ? "Sí" : "Todos";
        String filtroConAumStr   = conAumento     != null && conAumento     ? "Sí" : "Todos";
        
        String filtroIdVentaStr  = idVenta        != null ? idVenta.toString() : "Todos";

        return impresionService.imprimirReporteGenericVentas(
                itemList,
                filtroIdVentaStr,
                fechaInicio != null ? fechaInicio : "-",
                fechaFin    != null ? fechaFin    : "-",
                filtroSucursalStr, filtroFpStr, filtroMonedaStr,
                filtroEstadoStr, filtroClienteStr,
                filtroModoStr, filtroConObsStr, filtroConDescStr, filtroConAumStr,
                totalGeneral, totalEfectivo, totalTarjeta,
                totalConvenio, totalTransferencia, totalOtros,
                usuario);
    }

    public String reporteGenericVentasDetallado(
            Long idVenta,
            Long idCaja,
            Long sucId,
            Long formaPago,
            VentaEstado estado,
            Boolean isDelivery,
            Long monedaId,
            Boolean conDescuento,
            Boolean conAumento,
            Boolean conObservacion,
            Long clienteId,
            String fechaInicio,
            String fechaFin,
            Long usuarioId) {

        // Cargar todas las ventas sin límite de paginación
        Pageable allPages = PageRequest.of(0, Integer.MAX_VALUE);
        Page<Venta> ventaPage = service.onGenericSearch(
                idVenta, idCaja, allPages, false,
                sucId, formaPago, estado, isDelivery, monedaId,
                conDescuento, conAumento, conObservacion, clienteId,
                fechaInicio, fechaFin);

        List<Venta> ventas = ventaPage.getContent();

        // Acumuladores de totales por forma de pago (en Gs.)
        double totalGeneral       = 0.0;
        double totalEfectivo      = 0.0;
        double totalTarjeta       = 0.0;
        double totalConvenio      = 0.0;
        double totalTransferencia = 0.0;
        double totalOtros         = 0.0;

        List<ReporteVentaDetalladoDto> itemList = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        for (Venta v : ventas) {
            double ventaTotalGs = v.getTotalGs() != null ? v.getTotalGs() : 0.0;
            totalGeneral += ventaTotalGs;

            // Resolver forma de pago y moneda usando la lógica del VentaResolver:
            // si la venta no tiene formaPago directo, buscar en el cobro
            FormaPago fp = v.getFormaPago();
            Moneda moneda = null;
            List<CobroDetalle> cobroDetalleList = new ArrayList<>();
            if (v.getCobro() != null) {
                cobroDetalleList = cobroDetalleService.findByCobroId(
                        v.getCobro().getId(), v.getSucursalId());
                if (cobroDetalleList != null && !cobroDetalleList.isEmpty()) {
                    CobroDetalle primerDetalle = cobroDetalleList.get(0);
                    if (fp == null) {
                        fp = primerDetalle.getFormaPago();
                    }
                    moneda = primerDetalle.getMoneda();
                }
            }

            String fpDesc = fp != null && fp.getDescripcion() != null
                    ? fp.getDescripcion().toUpperCase() : "";

            if (fpDesc.contains("EFECTIVO")) {
                totalEfectivo += ventaTotalGs;
            } else if (fpDesc.contains("TARJETA")) {
                totalTarjeta += ventaTotalGs;
            } else if (fpDesc.contains("CONVENIO")) {
                totalConvenio += ventaTotalGs;
            } else if (fpDesc.contains("TRANSFERENCIA")) {
                totalTransferencia += ventaTotalGs;
            } else {
                totalOtros += ventaTotalGs;
            }

            // Items de la venta (producto, presentación, cantidad, precio, costo unitario/total)
            List<VentaItem> ventaItemList = ventaItemService.findByVentaId(v.getId(), v.getSucursalId());
            List<VentaItemReporteDetalladoDto> itemsDto = new ArrayList<>();
            double costoTotalVenta = 0.0;
            for (VentaItem vi : ventaItemList) {
                double cantidad = vi.getCantidad() != null ? vi.getCantidad() : 0.0;
                double precioCosto = vi.getPrecioCosto() != null ? vi.getPrecioCosto() : 0.0;
                double precio = vi.getPrecio() != null ? vi.getPrecio() : 0.0;
                double costoTotalItem = precioCosto * cantidad;
                costoTotalVenta += costoTotalItem;
                itemsDto.add(new VentaItemReporteDetalladoDto(
                        vi.getProducto() != null ? vi.getProducto().getDescripcion() : "",
                        vi.getPresentacion() != null ? vi.getPresentacion().getDescripcion() : "",
                        cantidad,
                        precio,
                        precioCosto,
                        costoTotalItem,
                        vi.getPrecio() != null && vi.getCantidad() != null ? precio * cantidad : 0.0));
            }

            // Movimientos de cobro (operación, forma de pago, moneda, valor en moneda/Gs) y totales
            List<MovimientoReporteDetalladoDto> movimientosDto = new ArrayList<>();
            double totalRecibidoGs = 0.0;
            double totalRecibidoRs = 0.0;
            double totalRecibidoDs = 0.0;
            double totalRecibido = 0.0;
            double totalDescuento = 0.0;
            double totalAumento = 0.0;
            double totalFinal = 0.0;
            for (CobroDetalle cd : cobroDetalleList) {
                String operacion = cd.getPago() != null && cd.getPago() ? "PAGO"
                        : cd.getDescuento() != null && cd.getDescuento() ? "DESCUENTO"
                        : cd.getAumento() != null && cd.getAumento() ? "AUMENTO"
                        : cd.getVuelto() != null && cd.getVuelto() ? "VUELTO"
                        : "PROBLEMA";
                double valor = cd.getValor() != null ? cd.getValor() : 0.0;
                double cambio = cd.getCambio() != null ? cd.getCambio() : 1.0;
                String denominacion = cd.getMoneda() != null && cd.getMoneda().getDenominacion() != null
                        ? cd.getMoneda().getDenominacion() : "";

                movimientosDto.add(new MovimientoReporteDetalladoDto(
                        operacion,
                        cd.getFormaPago() != null ? cd.getFormaPago().getDescripcion() : "",
                        denominacion,
                        valor,
                        valor * cambio));

                boolean recibido = (cd.getPago() != null && cd.getPago()) || (cd.getVuelto() != null && cd.getVuelto());
                boolean aumento = cd.getAumento() != null && cd.getAumento();
                boolean descuento = cd.getDescuento() != null && cd.getDescuento();
                if ("GUARANI".equals(denominacion)) {
                    if (recibido) {
                        totalRecibidoGs += valor;
                        totalRecibido += valor;
                        totalFinal += valor;
                    } else if (aumento) {
                        totalAumento += valor;
                        totalFinal += valor;
                    } else if (descuento) {
                        totalDescuento += valor;
                    }
                } else if ("REAL".equals(denominacion)) {
                    if (recibido) {
                        totalRecibidoRs += valor;
                        totalRecibido += valor * cambio;
                        totalFinal += valor * cambio;
                    } else if (aumento) {
                        totalAumento += valor * cambio;
                        totalFinal += valor * cambio;
                    } else if (descuento) {
                        totalDescuento += valor * cambio;
                    }
                } else if ("DOLAR".equals(denominacion)) {
                    if (recibido) {
                        totalRecibidoDs += valor;
                        totalRecibido += valor * cambio;
                        totalFinal += valor * cambio;
                    } else if (aumento) {
                        totalAumento += valor * cambio;
                        totalFinal += valor * cambio;
                    } else if (descuento) {
                        totalDescuento += valor * cambio;
                    }
                }
            }

            // Observaciones de la venta (descripción + motivo)
            List<VentaObservacion> ventaObservacionList = ventaObservacionService
                    .findByVentaIdAndSucursalId(v.getId(), v.getSucursalId());
            List<VentaObservacionReporteDetalladoDto> observacionesDto = new ArrayList<>();
            for (VentaObservacion vo : ventaObservacionList) {
                observacionesDto.add(new VentaObservacionReporteDetalladoDto(
                        vo.getCreadoEn() != null ? vo.getCreadoEn().format(formatter) : "",
                        vo.getDescripcion() != null ? vo.getDescripcion() : "",
                        vo.getMotivoObservacion() != null && vo.getMotivoObservacion().getDescripcion() != null
                                ? vo.getMotivoObservacion().getDescripcion() : ""));
            }

            ReporteVentaDetalladoDto dto = new ReporteVentaDetalladoDto();
            dto.setVentaId(v.getId());
            dto.setSucursal(v.getSucursal() != null ? v.getSucursal().getNombre() : "");
            dto.setCliente(v.getCliente() != null && v.getCliente().getPersona() != null
                    ? v.getCliente().getPersona().getNombre() : "");
            dto.setFecha(v.getCreadoEn() != null ? v.getCreadoEn().format(formatter) : "");
            dto.setFormaPago(fp != null && fp.getDescripcion() != null ? fp.getDescripcion() : "");
            dto.setMoneda(moneda != null && moneda.getDenominacion() != null ? moneda.getDenominacion() : "");
            dto.setEstado(v.getEstado() != null ? v.getEstado().toString() : "");
            dto.setResponsable(v.getUsuario() != null && v.getUsuario().getNickname() != null
                    ? v.getUsuario().getNickname() : "");
            dto.setTotalGs(ventaTotalGs);
            dto.setTotalRecibidoGs(totalRecibidoGs);
            dto.setTotalRecibidoRs(totalRecibidoRs);
            dto.setTotalRecibidoDs(totalRecibidoDs);
            dto.setTotalRecibido(totalRecibido);
            dto.setTotalDescuento(totalDescuento);
            dto.setTotalAumento(totalAumento);
            dto.setTotalFinal(totalFinal);
            dto.setCostoTotalVenta(costoTotalVenta);
            dto.setItemsDataSource(new JRBeanCollectionDataSource(itemsDto));
            dto.setMovimientosDataSource(new JRBeanCollectionDataSource(movimientosDto));
            dto.setObservacionesDataSource(new JRBeanCollectionDataSource(observacionesDto));
            itemList.add(dto);
        }

        // Resolver nombres reales de los filtros (idéntico a reporteGenericVentas)
        Usuario usuario = usuarioId != null
                ? usuarioService.findById(usuarioId).orElse(null) : null;

        String filtroSucursalStr;
        if (sucId != null) {
            Sucursal suc = sucursalService.findById(sucId).orElse(null);
            filtroSucursalStr = suc != null ? suc.getNombre() : "Sucursal " + sucId;
        } else {
            filtroSucursalStr = "Todas";
        }

        String filtroFpStr;
        if (formaPago != null) {
            FormaPago fp = formaPagoService.findById(formaPago).orElse(null);
            filtroFpStr = fp != null ? fp.getDescripcion() : "FormaPago " + formaPago;
        } else {
            filtroFpStr = "Todas";
        }

        String filtroMonedaStr;
        if (monedaId != null) {
            Moneda m = monedaService.findById(monedaId).orElse(null);
            filtroMonedaStr = m != null ? m.getDenominacion() : "Moneda " + monedaId;
        } else {
            filtroMonedaStr = "Todas";
        }

        String filtroEstadoStr  = estado    != null ? estado.toString() : "Todos";
        String filtroClienteStr;
        if (clienteId != null) {
            Cliente c = clienteService.findById(clienteId).orElse(null);
            filtroClienteStr = (c != null && c.getPersona() != null) ? c.getPersona().getNombre() : "ID: " + clienteId;
        } else {
            filtroClienteStr = "Todos";
        }

        String filtroModoStr;
        if (isDelivery == null) {
            filtroModoStr = "Todos";
        } else if (isDelivery) {
            filtroModoStr = "Delivery";
        } else {
            filtroModoStr = "Local";
        }

        String filtroConObsStr   = conObservacion != null && conObservacion ? "Sí" : "Todos";
        String filtroConDescStr  = conDescuento   != null && conDescuento   ? "Sí" : "Todos";
        String filtroConAumStr   = conAumento     != null && conAumento     ? "Sí" : "Todos";

        String filtroIdVentaStr  = idVenta        != null ? idVenta.toString() : "Todos";

        return impresionService.imprimirReporteGenericVentasDetallado(
                itemList,
                filtroIdVentaStr,
                fechaInicio != null ? fechaInicio : "-",
                fechaFin    != null ? fechaFin    : "-",
                filtroSucursalStr, filtroFpStr, filtroMonedaStr,
                filtroEstadoStr, filtroClienteStr,
                filtroModoStr, filtroConObsStr, filtroConDescStr, filtroConAumStr,
                totalGeneral, totalEfectivo, totalTarjeta,
                totalConvenio, totalTransferencia, totalOtros,
                usuario);
    }

    public String lucroPorFuncionario(String fechaInicio, String fechaFin, List<Long> sucursalIdList, Long usuarioId,
            List<Long> usuarioIdList, List<Long> productoIdList, Long subfamiliaId, Long familiaId) {
        Usuario usuario = usuarioService.findById(usuarioId).orElse(null);
        StringBuilder filtro = new StringBuilder();
        if (usuarioIdList != null && !usuarioIdList.isEmpty()) {
            filtro.append("Funcionario: ");
            for (int i = 0; i < usuarioIdList.size(); i++) {
                Usuario u = usuarioService.findById(usuarioIdList.get(i)).orElse(null);
                if (u != null) {
                    filtro.append(u.getNickname());
                    if (i < usuarioIdList.size() - 1) {
                        filtro.append(", ");
                    }
                }
            }
        }
        if (filtro.length() > 0 && sucursalIdList != null && sucursalIdList.size() > 0) {
            filtro.append("\n");
        }
        if (sucursalIdList != null && sucursalIdList.size() > 0) {
            if (sucursalIdList.size() > 1) {
                filtro.append("Sucursales: ");
            } else {
                filtro.append("Sucursal: ");
            }
        }
        for (Long sucId : sucursalIdList) {
            Sucursal suc = sucursalService.findById(sucId).orElse(null);
            if (suc != null)
                filtro.append(suc.getNombre() + ", ");
        }
        if (familiaId != null) {
            Familia familia = familiaService.findById(familiaId).orElse(null);
            if (familia != null && familia.getNombre() != null) {
                filtro.append("\nFamilia: " + familia.getNombre());
            }
        }
        if (subfamiliaId != null) {
            Subfamilia subfamilia = subFamiliaService.findById(subfamiliaId).orElse(null);
            if (subfamilia != null && subfamilia.getNombre() != null) {
                filtro.append("\nSubfamilia: " + subfamilia.getNombre());
            }
        }
        List<LucroPorFuncionarioDto> lucroPorFuncionarioDtoList = service.findLucroPorFuncionarios(fechaInicio, fechaFin,
                sucursalIdList, usuarioIdList, productoIdList, subfamiliaId, familiaId);
        return impresionService.imprimirReporteLucroPorFuncionario(lucroPorFuncionarioDtoList, fechaInicio, fechaFin, "",
                filtro.toString(), usuario);
    }

    public LucroPorFuncionarioResponse lucroPorFuncionarioList(
            String fechaInicio,
            String fechaFin,
            List<Long> sucursalIdList,
            List<Long> usuarioIdList,
            List<Long> productoIdList,
            Long subfamiliaId,
            Integer page,
            Integer size,
            Long familiaId) {

        List<LucroPorFuncionarioDto> fullList = service.findLucroPorFuncionarios(fechaInicio, fechaFin,
                sucursalIdList, usuarioIdList, productoIdList, subfamiliaId, familiaId);

        LucroPorFuncionarioSummary summary = new LucroPorFuncionarioSummary();
        summary.setCantidad(0.0);
        summary.setCostoTotal(0.0);
        summary.setTotalVenta(0.0);
        summary.setLucro(0.0);
        summary.setTotalDescuento(0.0);
        summary.setTotalAumento(0.0);

        for (LucroPorFuncionarioDto dto : fullList) {
            summary.setCantidad(summary.getCantidad() + (dto.getCantidad() != null ? dto.getCantidad() : 0));
            summary.setCostoTotal(summary.getCostoTotal() + (dto.getCostoTotal() != null ? dto.getCostoTotal() : 0));
            summary.setTotalVenta(summary.getTotalVenta() + (dto.getTotalVenta() != null ? dto.getTotalVenta() : 0));
            summary.setLucro(summary.getLucro() + (dto.getLucro() != null ? dto.getLucro() : 0));
            summary.setTotalDescuento(
                    summary.getTotalDescuento() + (dto.getTotalDescuento() != null ? dto.getTotalDescuento() : 0));
            summary.setTotalAumento(
                    summary.getTotalAumento() + (dto.getTotalAumento() != null ? dto.getTotalAumento() : 0));
        }

        if (summary.getTotalVenta() > 0) {
            summary.setMargen((summary.getLucro() / summary.getTotalVenta()) * 100);
        } else {
            summary.setMargen(0.0);
        }

        int start = 0;
        int end = fullList.size();

        if (page != null && size != null) {
            start = page * size;
            end = Math.min(start + size, fullList.size());
        }

        List<LucroPorFuncionarioDto> pagedContent;
        if (start >= fullList.size()) {
            pagedContent = new ArrayList<>();
        } else {
            pagedContent = fullList.subList(start, end);
        }

        LucroPorFuncionarioResponse response = new LucroPorFuncionarioResponse();
        response.setContent(pagedContent);
        response.setTotalElements((long) fullList.size());
        response.setSummary(summary);

        return response;
    }
}
