package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.EventoDte;
import com.franco.dev.service.financiero.DteService;
import com.franco.dev.repository.financiero.DocumentoElectronicoRepository;
import com.franco.dev.repository.financiero.LoteDteRepository;
import com.franco.dev.domain.financiero.LoteDte;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import com.franco.dev.domain.dto.DteMetricsDto;

@Component
public class DteGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private DteService dteService;
    @Autowired
    private DocumentoElectronicoRepository documentoElectronicoRepository;
    @Autowired
    private LoteDteRepository loteDteRepository;

    public DocumentoElectronico documentoElectronico(Long id) {
        return dteService.findById(id);
    }

    public List<EventoDte> eventosPorDte(Long dteId) {
        return dteService.listarEventosPorDte(dteId);
    }

    public Page<DocumentoElectronico> documentosElectronicos(Integer page, Integer size, String estado, String fechaDesde, String fechaHasta, String cdc, Long sucursalId) {
        int p = page != null ? page : 0;
        int s = size != null ? size : 10;
        return dteService.findFiltered(estado, fechaDesde, fechaHasta, p, s, cdc, sucursalId);
    }

    public DocumentoElectronico generarDocumentoElectronico(Long ventaId, Long sucursalId, Long usuarioId) {
        return dteService.iniciarGeneracionDte(ventaId, sucursalId, usuarioId);
    }

    public EventoDte registrarEventoDte(Long documentoElectronicoId, Integer tipoEvento, Long usuarioId, String motivo, String observacion) {
        return dteService.registrarEvento(documentoElectronicoId, tipoEvento, usuarioId, motivo, observacion);
    }

    // Fuerza el envío de un lote con hasta 50 documentos "GENERADO"
    public LoteDte enviarLoteNow(Long usuarioId) {
        List<DocumentoElectronico> docs = documentoElectronicoRepository.findTop50ByEstadoSifenOrderByIdAsc("GENERADO");
        if (docs.isEmpty()) return null;
        LoteDte nuevoLote = new LoteDte();
        nuevoLote.setEstadoSifen("ENVIANDO");
        if (usuarioId != null) {
            nuevoLote.setUsuario(dteService.getUsuarioService().findById(usuarioId).orElse(null));
        }
        nuevoLote = loteDteRepository.save(nuevoLote);
        final LoteDte loteRef = nuevoLote;
        docs.forEach(d -> { d.setLote(loteRef); d.setEstadoSifen("ENVIADO"); });
        documentoElectronicoRepository.saveAll(docs);
        String protocoloMock = "mock-protocolo-" + System.currentTimeMillis();
        nuevoLote.setIdProtocoloSifen(protocoloMock);
        nuevoLote.setEstadoSifen("RECIBIDO_POR_SIFEN");
        nuevoLote = loteDteRepository.save(nuevoLote);
        return nuevoLote;
    }

    // Marca un lote como procesado con respuesta mock
    public Boolean consultarLotesNow() {
        List<LoteDte> lotes = loteDteRepository.findByEstadoSifen("RECIBIDO_POR_SIFEN");
        for (LoteDte lote : lotes) {
            List<DocumentoElectronico> docs = documentoElectronicoRepository.findByLoteId(lote.getId());
            for (DocumentoElectronico d : docs) {
                d.setEstadoSifen("APROBADO");
            }
            documentoElectronicoRepository.saveAll(docs);
            lote.setRespuestaSifen("<mock>aprobado</mock>");
            lote.setEstadoSifen("PROCESADO_OK");
            loteDteRepository.save(lote);
        }
        return true;
    }

    public DocumentoElectronico reintentarGeneracionDte(Long dteId, Long usuarioId) {
        dteService.generarYFirmarXmlConNode(dteId, usuarioId);
        return dteService.findById(dteId);
    }

    public Boolean seedDteMock(Integer cantidad, Integer diasAtras) {
        int cant = cantidad != null ? cantidad : 20;
        int dias = diasAtras != null ? diasAtras : 30;
        Random rnd = new Random();
        LocalDateTime ahora = LocalDateTime.now();
        for (int i = 0; i < cant; i++) {
            DocumentoElectronico d = new DocumentoElectronico();
            d.setEstadoSifen(i % 4 == 0 ? "PENDIENTE" : (i % 4 == 1 ? "GENERADO" : (i % 4 == 2 ? "ENVIADO" : "APROBADO")));
            d.setCdc(null);
            d.setUrlQr(null);
            d.setXmlFirmado(null);
            d.setCreadoEn(ahora.minusDays(rnd.nextInt(Math.max(dias, 1))).minusHours(rnd.nextInt(24)));
            documentoElectronicoRepository.save(d);
        }
        return true;
    }

    public Boolean wipeDteData() {
        documentoElectronicoRepository.deleteAll();
        loteDteRepository.deleteAll();
        return true;
    }

    public DteMetricsDto dteMetrics() {
        long total = documentoElectronicoRepository.count();
        long pendientes = documentoElectronicoRepository.findByEstadoSifen("PENDIENTE", PageRequest.of(0,1)).getTotalElements();
        long generados = documentoElectronicoRepository.findByEstadoSifen("GENERADO", PageRequest.of(0,1)).getTotalElements();
        long enviados = documentoElectronicoRepository.findByEstadoSifen("ENVIADO", PageRequest.of(0,1)).getTotalElements();
        long aprobados = documentoElectronicoRepository.findByEstadoSifen("APROBADO", PageRequest.of(0,1)).getTotalElements();
        long rechazados = documentoElectronicoRepository.findByEstadoSifen("RECHAZADO", PageRequest.of(0,1)).getTotalElements();
        long cancelados = documentoElectronicoRepository.findByEstadoSifen("CANCELADO", PageRequest.of(0,1)).getTotalElements();
        return new DteMetricsDto(total, pendientes, generados, enviados, aprobados, rechazados, cancelados);
    }

    public List<LoteDte> lotesRecientes(Integer limit) {
        int lim = limit != null ? limit : 10;
        return loteDteRepository.findTop10ByOrderByIdDesc().subList(0, Math.min(lim, 10));
    }

    public List<DocumentoElectronico> dteRechazadosRecientes(Integer limit) {
        int lim = limit != null ? limit : 5;
        return documentoElectronicoRepository.findByEstadoSifenOrderByIdDesc("RECHAZADO", PageRequest.of(0, lim)).getContent();
    }
}


