package com.franco.dev.service.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.domain.financiero.NotaRemision;
import com.franco.dev.domain.financiero.NotaRemisionItem;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.financiero.enums.ModalidadTransporteNr;
import com.franco.dev.domain.financiero.enums.MotivoEmisionNotaRemision;
import com.franco.dev.domain.financiero.enums.OrigenNotaRemision;
import com.franco.dev.domain.financiero.enums.ResponsableEmisionNr;
import com.franco.dev.domain.financiero.enums.TipoTransporteNr;
import com.franco.dev.domain.operaciones.HojaRuta;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.TransferenciaItemService;
import com.franco.dev.service.operaciones.TransferenciaService;
import graphql.GraphQLException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Arma el borrador de una nota de remisión según su origen. **Toda la lógica fiscal vive acá, no en
 * el desktop**: el cliente muestra, deja editar lo editable y manda.
 *
 * Reglas por origen:
 *  - TRANSFERENCIA: motivo traslado entre locales, y entonces SIFEN exige que el receptor sea la
 *    propia empresa (mismo RUC que el emisor). Salida = sucursal de origen, entrega = destino.
 *  - FACTURA: motivo traslado por ventas, receptor = cliente de la factura, ítems de la factura.
 *  - MANUAL: solo la salida precargada; el resto lo carga el usuario.
 */
@Slf4j
@Service
public class NotaRemisionPrellenadoService {

    private final TransferenciaService transferenciaService;
    private final TransferenciaItemService transferenciaItemService;
    private final FacturaLegalService facturaLegalService;
    private final FacturaLegalItemService facturaLegalItemService;
    private final TimbradoDetalleService timbradoDetalleService;
    private final SucursalService sucursalService;
    private final FacturacionSecurityService seg;

    public NotaRemisionPrellenadoService(TransferenciaService transferenciaService,
                                         TransferenciaItemService transferenciaItemService,
                                         FacturaLegalService facturaLegalService,
                                         FacturaLegalItemService facturaLegalItemService,
                                         TimbradoDetalleService timbradoDetalleService,
                                         SucursalService sucursalService,
                                         FacturacionSecurityService seg) {
        this.transferenciaService = transferenciaService;
        this.transferenciaItemService = transferenciaItemService;
        this.facturaLegalService = facturaLegalService;
        this.facturaLegalItemService = facturaLegalItemService;
        this.timbradoDetalleService = timbradoDetalleService;
        this.sucursalService = sucursalService;
        this.seg = seg;
    }

    /** Cabecera + ítems sugeridos. No persiste nada. */
    public NotaRemisionPrellenada prellenar(OrigenNotaRemision origen, Long referenciaId, Long sucursalId) {
        seg.requireEmitirNr();
        if (origen == null) {
            throw new GraphQLException("Falta el origen de la nota de remisión");
        }
        TimbradoDetalle timbrado = timbradoElectronicoDe(sucursalId);

        switch (origen) {
            case TRANSFERENCIA: return desdeTransferencia(referenciaId, sucursalId, timbrado);
            case FACTURA:       return desdeFactura(referenciaId, sucursalId, timbrado);
            default:            return manual(sucursalId, timbrado);
        }
    }

    private NotaRemisionPrellenada manual(Long sucursalId, TimbradoDetalle timbrado) {
        NotaRemision nota = base(sucursalId, timbrado);
        nota.setOrigen(OrigenNotaRemision.MANUAL);
        return new NotaRemisionPrellenada(nota, new ArrayList<>());
    }

    private NotaRemisionPrellenada desdeTransferencia(Long transferenciaId, Long sucursalId, TimbradoDetalle timbrado) {
        if (transferenciaId == null) {
            throw new GraphQLException("Falta la transferencia");
        }
        Transferencia transferencia = transferenciaService.findById(transferenciaId)
                .orElseThrow(() -> new GraphQLException("No existe la transferencia " + transferenciaId));

        // La nota la emite quien despacha. Sin esta validación, el id de transferencia es global y
        // cualquiera podía prellenar con el chofer, el vehículo y las direcciones de otra sucursal.
        Sucursal sucursalOrigen = transferencia.getSucursalOrigen();
        if (sucursalOrigen != null && sucursalId != null && !sucursalId.equals(sucursalOrigen.getId())) {
            throw new GraphQLException("La transferencia " + transferenciaId
                    + " sale de otra sucursal: la nota de remisión la emite la sucursal de origen");
        }

        NotaRemision nota = base(sucursalId, timbrado);
        nota.setOrigen(OrigenNotaRemision.TRANSFERENCIA);
        nota.setTransferenciaId(transferenciaId);
        nota.setMotivoEmision(MotivoEmisionNotaRemision.TRASLADO_ENTRE_LOCALES);

        // Traslado entre locales: el receptor es la propia empresa, con su mismo RUC.
        nota.setReceptorNombre(timbrado.getTimbrado().getRazonSocial());
        nota.setReceptorRuc(timbrado.getTimbrado().getRuc());
        nota.setReceptorDireccion(timbrado.getDireccion());
        nota.setReceptorDepartamento(timbrado.getDepartamento());
        nota.setReceptorCiudad(timbrado.getCiudad());
        nota.setReceptorCodigoCiudad(codigoCiudad(timbrado.getCodigoCiudad()));

        Sucursal destino = transferencia.getSucursalDestino();
        if (destino != null) {
            nota.setEntregaDireccion(destino.getDireccion());
            if (destino.getCiudad() != null) {
                nota.setEntregaCiudad(destino.getCiudad().getDescripcion());
                nota.setEntregaCodigoCiudad(codigoCiudad(destino.getCiudad().getCodigo()));
            }
        }

        HojaRuta hojaRuta = transferencia.getHojaRuta();
        if (hojaRuta != null) {
            if (hojaRuta.getVehiculo() != null) {
                nota.setVehiculoId(hojaRuta.getVehiculo().getId());
                nota.setVehiculoMatricula(hojaRuta.getVehiculo().getChapa());
                if (hojaRuta.getVehiculo().getModelo() != null
                        && hojaRuta.getVehiculo().getModelo().getMarca() != null) {
                    nota.setVehiculoMarca(hojaRuta.getVehiculo().getModelo().getMarca().getDescripcion());
                }
            }
            Persona chofer = hojaRuta.getChofer();
            if (chofer != null) {
                nota.setChoferPersonaId(chofer.getId());
                nota.setChoferNombre(chofer.getNombre());
                nota.setChoferDocumento(chofer.getDocumento());
            }
            if (hojaRuta.getFechaSalida() != null) {
                nota.setFechaInicioTraslado(hojaRuta.getFechaSalida().toLocalDate());
            }
            if (hojaRuta.getFechaLlegada() != null) {
                nota.setFechaFinTraslado(hojaRuta.getFechaLlegada().toLocalDate());
            }
        }

        List<NotaRemisionItem> items = new ArrayList<>();
        for (TransferenciaItem origen : transferenciaItemService.findByTransferenciaId(transferenciaId)) {
            Presentacion presentacion = presentacionDeLaEtapa(origen);
            Double cantidad = cantidadDeLaEtapa(origen);
            if (cantidad == null || cantidad <= 0) {
                continue;
            }
            NotaRemisionItem item = new NotaRemisionItem();
            item.setSucursalId(sucursalId);
            item.setCantidad(BigDecimal.valueOf(cantidad));
            if (presentacion != null) {
                item.setPresentacionId(presentacion.getId());
                item.setDescripcion(presentacion.getProducto() != null
                        ? presentacion.getProducto().getDescripcion() : presentacion.getDescripcion());
                if (presentacion.getProducto() != null) {
                    item.setProductoId(presentacion.getProducto().getId());
                }
            }
            if (item.getDescripcion() == null) {
                item.setDescripcion("ITEM DE TRANSFERENCIA");
            }
            items.add(item);
        }
        return new NotaRemisionPrellenada(nota, items);
    }

    private NotaRemisionPrellenada desdeFactura(Long facturaId, Long sucursalId, TimbradoDetalle timbrado) {
        if (facturaId == null) {
            throw new GraphQLException("Falta la factura");
        }
        FacturaLegal factura = facturaLegalService.findByIdAndSucursalId(facturaId, sucursalId);
        if (factura == null) {
            throw new GraphQLException("No existe la factura " + facturaId + " en la sucursal " + sucursalId);
        }

        NotaRemision nota = base(sucursalId, timbrado);
        nota.setOrigen(OrigenNotaRemision.FACTURA);
        nota.setFacturaLegalId(facturaId);
        nota.setMotivoEmision(MotivoEmisionNotaRemision.TRASLADO_POR_VENTAS);

        if (factura.getCliente() != null && factura.getCliente().getPersona() != null) {
            nota.setClienteId(factura.getCliente().getId());
            nota.setReceptorNombre(factura.getCliente().getPersona().getNombre());
            nota.setReceptorRuc(factura.getCliente().getPersona().getDocumento());
        }
        if (nota.getReceptorNombre() == null) {
            nota.setReceptorNombre(factura.getNombre());
            nota.setReceptorRuc(factura.getRuc());
        }
        nota.setReceptorDireccion(factura.getDireccion());

        List<NotaRemisionItem> items = new ArrayList<>();
        // Con sucursalId: la PK de factura_legal_item es compuesta (id, sucursal_id), asi que el
        // overload sin sucursal puede traer los items de la factura del mismo numero de OTRA sucursal.
        for (FacturaLegalItem origen : facturaLegalItemService.findByFacturaLegalId(facturaId, sucursalId)) {
            NotaRemisionItem item = new NotaRemisionItem();
            item.setSucursalId(sucursalId);
            item.setDescripcion(origen.getDescripcion());
            item.setCantidad(origen.getCantidad() != null
                    ? BigDecimal.valueOf(origen.getCantidad()) : BigDecimal.ONE);
            item.setUnidadMedida(origen.getUnidadMedida());
            if (origen.getProducto() != null) item.setProductoId(origen.getProducto().getId());
            if (origen.getPresentacion() != null) item.setPresentacionId(origen.getPresentacion().getId());
            items.add(item);
        }
        return new NotaRemisionPrellenada(nota, items);
    }

    /** Cabecera común: timbrado, fecha, salida desde la sucursal y valores por defecto. */
    private NotaRemision base(Long sucursalId, TimbradoDetalle timbrado) {
        NotaRemision nota = new NotaRemision();
        nota.setSucursalId(sucursalId);
        nota.setTimbradoDetalleId(timbrado.getId());
        nota.setFecha(LocalDateTime.now());
        nota.setResponsableEmision(ResponsableEmisionNr.EMISOR_FACTURA);
        nota.setTipoTransporte(TipoTransporteNr.PROPIO);
        nota.setModalidadTransporte(ModalidadTransporteNr.TERRESTRE);
        nota.setFechaInicioTraslado(LocalDate.now());
        nota.setActivo(true);

        nota.setSalidaDireccion(timbrado.getDireccion());
        nota.setSalidaDepartamento(timbrado.getDepartamento());
        nota.setSalidaCiudad(timbrado.getCiudad());
        nota.setSalidaCodigoCiudad(codigoCiudad(timbrado.getCodigoCiudad()));

        Optional<Sucursal> sucursal = sucursalService.findById(sucursalId);
        if (sucursal.isPresent() && sucursal.get().getDireccion() != null) {
            nota.setSalidaDireccion(sucursal.get().getDireccion());
        }
        return nota;
    }

    /** El timbrado electrónico activo de la sucursal. Sin él no hay nota que emitir. */
    private TimbradoDetalle timbradoElectronicoDe(Long sucursalId) {
        List<TimbradoDetalle> detalles = timbradoDetalleService.findBySucursalId(sucursalId);
        if (detalles == null || detalles.isEmpty()) {
            throw new GraphQLException("La sucursal " + sucursalId + " no tiene timbrado cargado");
        }
        return detalles.stream()
                .filter(d -> Boolean.TRUE.equals(d.getActivo()))
                .filter(d -> d.getTimbrado() != null && Boolean.TRUE.equals(d.getTimbrado().getIsElectronico()))
                .findFirst()
                .orElseThrow(() -> new GraphQLException(
                        "La sucursal " + sucursalId + " no tiene un timbrado electrónico activo"));
    }

    private static Presentacion presentacionDeLaEtapa(TransferenciaItem item) {
        if (item.getPresentacionTransporte() != null) return item.getPresentacionTransporte();
        if (item.getPresentacionPreparacion() != null) return item.getPresentacionPreparacion();
        return item.getPresentacionPreTransferencia();
    }

    private static Double cantidadDeLaEtapa(TransferenciaItem item) {
        if (item.getCantidadTransporte() != null) return item.getCantidadTransporte();
        if (item.getCantidadPreparacion() != null) return item.getCantidadPreparacion();
        return item.getCantidadPreTransferencia();
    }

    private static Integer codigoCiudad(String codigo) {
        if (codigo == null || codigo.trim().isEmpty()) return null;
        try {
            return Integer.parseInt(codigo.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Lo que devuelve la query. El nombre tiene que coincidir con el tipo del .graphqls
     * (NotaRemisionPrellenada): kickstart parea tipo y clase por nombre, y si no coinciden el
     * contexto de Spring no levanta — algo que el CI no atrapa porque no arranca la app.
     */
    public static class NotaRemisionPrellenada {
        private final NotaRemision notaRemision;
        private final List<NotaRemisionItem> items;

        public NotaRemisionPrellenada(NotaRemision notaRemision, List<NotaRemisionItem> items) {
            this.notaRemision = notaRemision;
            this.items = items;
        }

        public NotaRemision getNotaRemision() { return notaRemision; }
        public List<NotaRemisionItem> getItems() { return items; }
    }
}
