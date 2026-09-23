package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.NotaCredito;
import com.franco.dev.domain.financiero.NotaCreditoItem;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.domain.financiero.enums.MotivoEmisionNotaCredito;
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

import java.time.LocalDateTime;
import java.util.List;

/**
 * Notas de crédito electrónicas. Igual que la remisión: control de rol en la primera línea y el
 * envío encadenado en tres transacciones separadas desde el resolver (D8).
 */
@Slf4j
@Component
public class NotaCreditoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired private NotaCreditoService service;
    @Autowired private DocumentoElectronicoService documentoElectronicoService;
    @Autowired private TimbradoDetalleService timbradoDetalleService;
    @Autowired private SucursalService sucursalService;
    @Autowired private FacturacionSecurityService seg;
    @Autowired private KudeNotaCreditoService kudeService;
    @Autowired(required = false) private SifenService sifenService;
    @Autowired(required = false) private SifenEventoService sifenEventoService;
    @Autowired(required = false) private SifenEnvioSincronoService envioSincronoService;

    // ===================== QUERIES =====================

    public NotaCredito notaCredito(Long id, Long sucursalId) {
        seg.requireVer();
        return service.findByIdAndSucursalId(id, sucursalId).orElse(null);
    }

    public Page<NotaCredito> notaCreditos(Long sucursalId, String fechaInicio, String fechaFin,
                                          int page, int size) {
        seg.requireVer();
        LocalDateTime desde = fechaInicio != null ? DateUtils.stringToDate(fechaInicio) : null;
        LocalDateTime hasta = fechaFin != null ? DateUtils.stringToDate(fechaFin) : null;
        return service.findByFilters(sucursalId, desde, hasta, PageRequest.of(page, size));
    }

    public List<NotaCreditoItem> notaCreditoItems(Long notaCreditoId, Long sucursalId) {
        seg.requireVer();
        return service.findItems(notaCreditoId, sucursalId);
    }

    /** Las notas activas de una factura: el desktop deshabilita el botón si ya hay una. */
    public List<NotaCredito> notaCreditosPorFactura(Long facturaLegalId, Long sucursalId) {
        seg.requireVer();
        return service.findActivasByFactura(facturaLegalId, sucursalId);
    }

    public DocumentoElectronico documentoElectronicoDeNotaCredito(Long notaCreditoId, Long sucursalId) {
        seg.requireVer();
        return documentoElectronicoService.findByNotaCreditoId(notaCreditoId, sucursalId).orElse(null);
    }

    /** KuDE en PDF (base64). El ticket termico no entra en esta entrega. */
    /** Facturas que hoy admiten nota de credito, para el buscador del boton «Adicionar». */
    public List<NotaCreditoService.FacturaParaNotaCredito> facturasParaNotaCredito(
            Long sucursalId, String numero, Integer page, Integer size) {
        return service.facturasParaNotaCredito(sucursalId, numero,
                page != null ? page : 0, size != null ? size : 15);
    }

    public String imprimirNotaCredito(Long id, Long sucursalId, Integer anchoMm, Boolean escpos) {
        seg.requireVer();
        if (Boolean.TRUE.equals(escpos)) {
            throw new GraphQLException("El ticket térmico de la nota de crédito todavía no está disponible");
        }
        NotaCredito nota = service.findByIdAndSucursalId(id, sucursalId)
                .orElseThrow(() -> new GraphQLException("No existe la nota de crédito"));
        List<NotaCreditoItem> items = service.findItems(id, sucursalId);
        TimbradoDetalle timbrado = timbradoDetalleService
                .findByIdAndSucursalId(nota.getTimbradoDetalleId(), sucursalId).orElse(null);
        DocumentoElectronico de = documentoElectronicoService.findByNotaCreditoId(id, sucursalId).orElse(null);
        String cdcFactura = documentoElectronicoService
                .findByFacturaLegalId(nota.getFacturaLegalId(), sucursalId)
                .map(DocumentoElectronico::getCdc).orElse(null);
        try {
            return kudeService.generarPdfBase64(nota, items, timbrado, de, cdcFactura);
        } catch (Exception e) {
            log.error("Error al generar el KuDE de la nota de crédito {}: {}", id, e.getMessage());
            throw new GraphQLException("No se pudo generar el PDF de la nota de crédito: " + e.getMessage());
        }
    }

    // ===================== MUTATIONS =====================

    public NotaCredito crearNotaCreditoDesdeFactura(Long facturaLegalId, Long sucursalId, String motivo,
                                                    String descripcionMotivo, Long usuarioId) {
        // requireEmitir lo llama el service, que es donde también se valida la factura
        return service.crearDesdeFactura(facturaLegalId, sucursalId,
                motivo != null ? MotivoEmisionNotaCredito.valueOf(motivo) : null,
                descripcionMotivo, usuarioId);
    }

    public DocumentoElectronico generarYEnviarNotaCredito(Long id, Long sucursalId) {
        seg.requireEmitir();
        exigirSifenHabilitado();

        NotaCredito nota = service.findByIdAndSucursalId(id, sucursalId)
                .orElseThrow(() -> new GraphQLException("No existe la nota de crédito"));

        DocumentoElectronico de = documentoElectronicoService.findByNotaCreditoId(id, sucursalId)
                .orElseGet(() -> crearDocumento(nota, sucursalId));

        try {
            envioSincronoService.generarYEnviarSincrono(de);
        } catch (Exception e) {
            log.error("Error al enviar la nota de crédito {} a SIFEN: {}", id, e.getMessage());
            throw new GraphQLException("La nota quedó creada pero SIFEN no la recibió: " + e.getMessage());
        }
        return documentoElectronicoService.findByNotaCreditoId(id, sucursalId).orElse(de);
    }

    /** Reenvía el mismo DE, con su CDC: no genera otro ni consume otro número. */
    public DocumentoElectronico reenviarNotaCredito(Long id, Long sucursalId) {
        seg.requireEmitir();
        exigirSifenHabilitado();

        DocumentoElectronico de = documentoElectronicoService.findByNotaCreditoId(id, sucursalId)
                .orElseThrow(() -> new GraphQLException("La nota de crédito todavía no tiene documento electrónico"));
        if (de.getEstado() == EstadoDE.APROBADO) {
            throw new GraphQLException("La nota de crédito ya fue aprobada por SIFEN");
        }
        try {
            envioSincronoService.generarYEnviarSincrono(de);
        } catch (Exception e) {
            throw new GraphQLException("No se pudo reenviar la nota de crédito: " + e.getMessage());
        }
        return documentoElectronicoService.findByNotaCreditoId(id, sucursalId).orElse(de);
    }


    /**
     * Cancela ante SIFEN y recién entonces da de baja localmente.
     *
     * Antes solo hacía la baja lógica: el sistema mostraba la nota anulada y la SET la seguía
     * teniendo por válida. Se descubrió emitiendo en producción el 2026-09-18 (nota de remisión
     * 001-001-0000009, que quedó aprobada y no se pudo cancelar). El orden importa: si SIFEN
     * rechaza el evento, la nota NO se da de baja, porque sigue siendo un documento válido.
     */
    public NotaCredito anularNotaCredito(Long id, Long sucursalId) {
        seg.requireEmitir();
        cancelarEnSifen(documentoElectronicoService.findByNotaCreditoId(id, sucursalId).orElse(null),
                "Cancelación de nota de crédito solicitada por el usuario");
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

    private DocumentoElectronico crearDocumento(NotaCredito nota, Long sucursalId) {
        List<NotaCreditoItem> items = service.findItems(nota.getId(), sucursalId);
        TimbradoDetalle timbrado = timbradoDetalleService
                .findByIdAndSucursalId(nota.getTimbradoDetalleId(), sucursalId)
                .orElseThrow(() -> new GraphQLException("No se encontró el timbrado de la nota"));
        Sucursal sucursal = sucursalService.findById(sucursalId).orElse(null);

        String cdcFactura = documentoElectronicoService
                .findByFacturaLegalId(nota.getFacturaLegalId(), sucursalId)
                .map(DocumentoElectronico::getCdc)
                .orElseThrow(() -> new GraphQLException("La factura asociada no tiene CDC"));

        try {
            return sifenService.crearDocumentoElectronicoNotaCredito(nota, items, timbrado, sucursal, cdcFactura);
        } catch (Exception e) {
            throw new GraphQLException("No se pudo generar el documento electrónico: " + e.getMessage());
        }
    }

    private void exigirSifenHabilitado() {
        if (sifenService == null || envioSincronoService == null) {
            throw new GraphQLException("SIFEN está deshabilitado en este servidor (sifen.enabled=false)");
        }
    }
}
