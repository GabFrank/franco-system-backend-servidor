package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.NotaRemision;
import com.franco.dev.domain.financiero.NotaRemisionItem;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.financiero.enums.*;
import com.franco.dev.graphql.financiero.input.NotaRemisionInput;
import com.franco.dev.graphql.financiero.input.NotaRemisionItemInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.*;
import com.franco.dev.service.sifen.SifenEnvioSincronoService;
import com.franco.dev.service.sifen.SifenEventoService;
import com.franco.dev.service.sifen.SifenService;
import com.franco.dev.utilitarios.DateUtils;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Notas de remisión electrónicas. **Toda mutation empieza por el control de rol**
 * ({@link FacturacionSecurityService}): en este repo no hay `@PreAuthorize` y `@AdminSecured` está
 * roto (issue #177), así que la autorización es explícita o no existe.
 *
 * El envío a SIFEN va en tres transacciones separadas (D8 del plan): primero se crea la nota con su
 * número, después el DE con su CDC, y recién después el lote y el envío. Encadenarlas acá, y no
 * dentro de un `@Transactional`, es lo que evita que un timeout de lectura revierta un número que
 * SIFEN ya aceptó.
 */
@Slf4j
@Component
public class NotaRemisionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired private NotaRemisionService service;
    @Autowired private NotaRemisionPrellenadoService prellenadoService;
    @Autowired private DocumentoElectronicoService documentoElectronicoService;
    @Autowired private TimbradoDetalleService timbradoDetalleService;
    @Autowired private SucursalService sucursalService;
    @Autowired private FacturacionSecurityService seg;
    @Autowired private KudeNotaRemisionService kudeService;
    @Autowired(required = false) private SifenService sifenService;
    @Autowired(required = false) private SifenEventoService sifenEventoService;
    @Autowired(required = false) private SifenEnvioSincronoService envioSincronoService;

    // ===================== QUERIES =====================

    public NotaRemision notaRemision(Long id, Long sucursalId) {
        seg.requireVer();
        return service.findByIdAndSucursalId(id, sucursalId).orElse(null);
    }

    public Page<NotaRemision> notaRemisiones(Long sucursalId, String fechaInicio, String fechaFin,
                                             int page, int size) {
        seg.requireVer();
        LocalDateTime desde = fechaInicio != null ? DateUtils.stringToDate(fechaInicio) : null;
        LocalDateTime hasta = fechaFin != null ? DateUtils.stringToDate(fechaFin) : null;
        return service.findByFilters(sucursalId, desde, hasta, PageRequest.of(page, size));
    }

    public List<NotaRemisionItem> notaRemisionItems(Long notaRemisionId, Long sucursalId) {
        seg.requireVer();
        return service.findItems(notaRemisionId, sucursalId);
    }

    /** La nota activa de una transferencia, para que el desktop deshabilite el botón si ya existe. */
    public NotaRemision notaRemisionPorTransferencia(Long transferenciaId, Long sucursalId) {
        seg.requireVer();
        List<NotaRemision> notas = service.findActivasByTransferencia(transferenciaId, sucursalId);
        return notas.isEmpty() ? null : notas.get(0);
    }

    public DocumentoElectronico documentoElectronicoDeNotaRemision(Long notaRemisionId, Long sucursalId) {
        seg.requireVer();
        return documentoElectronicoService.findByNotaRemisionId(notaRemisionId, sucursalId).orElse(null);
    }

    /** Borrador según el origen: el desktop no arma datos fiscales. */
    public NotaRemisionPrellenadoService.NotaRemisionPrellenada prellenarNotaRemision(String origen, Long referenciaId,
                                                                         Long sucursalId) {
        return prellenadoService.prellenar(OrigenNotaRemision.valueOf(origen), referenciaId, sucursalId);
    }

    /**
     * KuDE en PDF (base64). {@code escpos} queda para el ticket térmico, que no entra en esta
     * entrega: se rechaza explícitamente en vez de devolver un PDF que la impresora no entiende.
     */
    public String imprimirNotaRemision(Long id, Long sucursalId, Integer anchoMm, Boolean escpos) {
        seg.requireVer();
        if (Boolean.TRUE.equals(escpos)) {
            throw new GraphQLException("El ticket térmico de la nota de remisión todavía no está disponible");
        }
        NotaRemision nota = service.findByIdAndSucursalId(id, sucursalId)
                .orElseThrow(() -> new GraphQLException("No existe la nota de remisión"));
        List<NotaRemisionItem> items = service.findItems(id, sucursalId);
        TimbradoDetalle timbrado = timbradoDetalleService
                .findByIdAndSucursalId(nota.getTimbradoDetalleId(), sucursalId).orElse(null);
        DocumentoElectronico de = documentoElectronicoService.findByNotaRemisionId(id, sucursalId).orElse(null);
        try {
            return kudeService.generarPdfBase64(nota, items, timbrado, de);
        } catch (Exception e) {
            log.error("Error al generar el KuDE de la nota de remisión {}: {}", id, e.getMessage());
            throw new GraphQLException("No se pudo generar el PDF de la nota de remisión: " + e.getMessage());
        }
    }

    // ===================== MUTATIONS =====================

    public NotaRemision saveNotaRemision(NotaRemisionInput input, List<NotaRemisionItemInput> items) {
        // requireEmitir lo llama el service, que es donde también se valida
        NotaRemision nota = aEntidad(input);
        List<NotaRemisionItem> itemsEntidad = new ArrayList<>();
        if (items != null) {
            for (NotaRemisionItemInput itemInput : items) {
                itemsEntidad.add(aEntidad(itemInput, input.getSucursalId()));
            }
        }
        return service.crear(nota, itemsEntidad);
    }

    /**
     * Genera el DE y lo manda a SIFEN. Si el DE ya existe (un reintento después de un envío fallido)
     * no se regenera: se reenvía el mismo, con su CDC.
     */
    public DocumentoElectronico generarYEnviarNotaRemision(Long id, Long sucursalId) {
        seg.requireEmitir();
        exigirSifenHabilitado();

        NotaRemision nota = service.findByIdAndSucursalId(id, sucursalId)
                .orElseThrow(() -> new GraphQLException("No existe la nota de remisión"));

        DocumentoElectronico de = documentoElectronicoService.findByNotaRemisionId(id, sucursalId)
                .orElseGet(() -> crearDocumento(nota, sucursalId));

        try {
            envioSincronoService.generarYEnviarSincrono(de);
        } catch (Exception e) {
            // El DE ya está persistido con su CDC: el reenvío lo recupera sin consumir otro número.
            log.error("Error al enviar la nota de remisión {} a SIFEN: {}", id, e.getMessage());
            throw new GraphQLException("La nota quedó creada pero SIFEN no la recibió: " + e.getMessage());
        }
        return documentoElectronicoService.findByNotaRemisionId(id, sucursalId).orElse(de);
    }

    /** Reenvía un DE que quedó sin llegar a SIFEN. No genera un documento nuevo. */
    public DocumentoElectronico reenviarNotaRemision(Long id, Long sucursalId) {
        seg.requireEmitir();
        exigirSifenHabilitado();

        DocumentoElectronico de = documentoElectronicoService.findByNotaRemisionId(id, sucursalId)
                .orElseThrow(() -> new GraphQLException("La nota de remisión todavía no tiene documento electrónico"));
        if (de.getEstado() == EstadoDE.APROBADO) {
            throw new GraphQLException("La nota de remisión ya fue aprobada por SIFEN");
        }
        try {
            envioSincronoService.generarYEnviarSincrono(de);
        } catch (Exception e) {
            throw new GraphQLException("No se pudo reenviar la nota de remisión: " + e.getMessage());
        }
        return documentoElectronicoService.findByNotaRemisionId(id, sucursalId).orElse(de);
    }


    /**
     * Cancela ante SIFEN y recién entonces da de baja localmente.
     *
     * Antes solo hacía la baja lógica: el sistema mostraba la nota anulada y la SET la seguía
     * teniendo por válida. Se descubrió emitiendo en producción el 2026-09-18 (nota de remisión
     * 001-001-0000009, que quedó aprobada y no se pudo cancelar). El orden importa: si SIFEN
     * rechaza el evento, la nota NO se da de baja, porque sigue siendo un documento válido.
     */
    public NotaRemision anularNotaRemision(Long id, Long sucursalId) {
        seg.requireEmitir();
        cancelarEnSifen(documentoElectronicoService.findByNotaRemisionId(id, sucursalId).orElse(null),
                "Cancelación de nota de remisión solicitada por el usuario");
        return service.anular(id, sucursalId);
    }

    /** Sin documento electrónico aprobado no hay nada que cancelar: la baja es solo local. */
    private void cancelarEnSifen(DocumentoElectronico de, String motivo) {
        if (de == null || de.getEstado() != EstadoDE.APROBADO) {
            return;
        }
        if (sifenEventoService == null) {
            throw new GraphQLException("SIFEN está deshabilitado: no se puede cancelar un documento aprobado");
        }
        try {
            sifenEventoService.cancelarDE(de.getCdc(), motivo);
        } catch (Exception e) {
            throw new GraphQLException("SIFEN rechazó la cancelación, así que la nota sigue vigente: "
                    + e.getMessage());
        }
    }

    // ===================== INTERNO =====================

    private DocumentoElectronico crearDocumento(NotaRemision nota, Long sucursalId) {
        List<NotaRemisionItem> items = service.findItems(nota.getId(), sucursalId);
        TimbradoDetalle timbrado = timbradoDetalleService
                .findByIdAndSucursalId(nota.getTimbradoDetalleId(), sucursalId)
                .orElseThrow(() -> new GraphQLException("No se encontró el timbrado de la nota"));
        Sucursal sucursal = sucursalService.findById(sucursalId).orElse(null);

        String cdcAsociado = null;
        if (nota.getFacturaLegalId() != null) {
            cdcAsociado = documentoElectronicoService
                    .findByFacturaLegalId(nota.getFacturaLegalId(), sucursalId)
                    .map(DocumentoElectronico::getCdc)
                    .orElse(null);
        }
        try {
            return sifenService.crearDocumentoElectronicoNotaRemision(nota, items, timbrado, sucursal, cdcAsociado);
        } catch (Exception e) {
            throw new GraphQLException("No se pudo generar el documento electrónico: " + e.getMessage());
        }
    }

    private void exigirSifenHabilitado() {
        if (sifenService == null || envioSincronoService == null) {
            throw new GraphQLException("SIFEN está deshabilitado en este servidor (sifen.enabled=false)");
        }
    }

    private NotaRemision aEntidad(NotaRemisionInput input) {
        NotaRemision nota = new NotaRemision();
        nota.setId(input.getId());
        nota.setSucursalId(input.getSucursalId());
        nota.setTimbradoDetalleId(input.getTimbradoDetalleId());
        nota.setFecha(input.getFecha() != null ? DateUtils.stringToDate(input.getFecha()) : LocalDateTime.now());
        nota.setOrigen(input.getOrigen() != null ? OrigenNotaRemision.valueOf(input.getOrigen()) : null);
        nota.setTransferenciaId(input.getTransferenciaId());
        nota.setFacturaLegalId(input.getFacturaLegalId());
        nota.setMotivoEmision(input.getMotivoEmision() != null
                ? MotivoEmisionNotaRemision.valueOf(input.getMotivoEmision()) : null);
        nota.setResponsableEmision(input.getResponsableEmision() != null
                ? ResponsableEmisionNr.valueOf(input.getResponsableEmision()) : ResponsableEmisionNr.EMISOR_FACTURA);
        nota.setKmEstimado(input.getKmEstimado());
        nota.setFechaInicioTraslado(aFecha(input.getFechaInicioTraslado()));
        nota.setFechaFinTraslado(aFecha(input.getFechaFinTraslado()));
        nota.setFechaEstimadaFactura(aFecha(input.getFechaEstimadaFactura()));
        nota.setClienteId(input.getClienteId());
        nota.setReceptorNombre(mayusculas(input.getReceptorNombre()));
        nota.setReceptorRuc(input.getReceptorRuc());
        nota.setReceptorDireccion(mayusculas(input.getReceptorDireccion()));
        nota.setReceptorDepartamento(mayusculas(input.getReceptorDepartamento()));
        nota.setReceptorCodigoCiudad(input.getReceptorCodigoCiudad());
        nota.setReceptorCiudad(mayusculas(input.getReceptorCiudad()));
        nota.setSalidaDireccion(mayusculas(input.getSalidaDireccion()));
        nota.setSalidaDepartamento(mayusculas(input.getSalidaDepartamento()));
        nota.setSalidaCodigoCiudad(input.getSalidaCodigoCiudad());
        nota.setSalidaCiudad(mayusculas(input.getSalidaCiudad()));
        nota.setEntregaDireccion(mayusculas(input.getEntregaDireccion()));
        nota.setEntregaDepartamento(mayusculas(input.getEntregaDepartamento()));
        nota.setEntregaCodigoCiudad(input.getEntregaCodigoCiudad());
        nota.setEntregaCiudad(mayusculas(input.getEntregaCiudad()));
        nota.setTipoTransporte(input.getTipoTransporte() != null
                ? TipoTransporteNr.valueOf(input.getTipoTransporte()) : TipoTransporteNr.PROPIO);
        nota.setModalidadTransporte(input.getModalidadTransporte() != null
                ? ModalidadTransporteNr.valueOf(input.getModalidadTransporte()) : ModalidadTransporteNr.TERRESTRE);
        nota.setTransportistaNombre(mayusculas(input.getTransportistaNombre()));
        nota.setTransportistaRuc(input.getTransportistaRuc());
        nota.setTransportistaDireccion(mayusculas(input.getTransportistaDireccion()));
        nota.setVehiculoId(input.getVehiculoId());
        nota.setVehiculoMarca(mayusculas(input.getVehiculoMarca()));
        nota.setVehiculoMatricula(mayusculas(input.getVehiculoMatricula()));
        nota.setChoferPersonaId(input.getChoferPersonaId());
        nota.setChoferNombre(mayusculas(input.getChoferNombre()));
        nota.setChoferDocumento(input.getChoferDocumento());
        nota.setChoferDireccion(mayusculas(input.getChoferDireccion()));
        nota.setUsuarioId(input.getUsuarioId());
        return nota;
    }

    private NotaRemisionItem aEntidad(NotaRemisionItemInput input, Long sucursalId) {
        NotaRemisionItem item = new NotaRemisionItem();
        item.setSucursalId(sucursalId);
        item.setProductoId(input.getProductoId());
        item.setPresentacionId(input.getPresentacionId());
        item.setCodigo(input.getCodigo());
        item.setDescripcion(mayusculas(input.getDescripcion()));
        item.setCantidad(input.getCantidad());
        item.setUnidadMedida(input.getUnidadMedida());
        return item;
    }

    private static LocalDate aFecha(String valor) {
        if (valor == null || valor.trim().isEmpty()) return null;
        LocalDateTime fecha = DateUtils.stringToDate(valor);
        return fecha != null ? fecha.toLocalDate() : null;
    }

    private static String mayusculas(String valor) {
        return valor != null ? valor.trim().toUpperCase() : null;
    }
}
