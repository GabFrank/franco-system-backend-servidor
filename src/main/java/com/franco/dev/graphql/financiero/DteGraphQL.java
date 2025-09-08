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

    public DocumentoElectronico reintentarGeneracionDte(Long dteId, Long usuarioId) {
        dteService.generarYFirmarXmlConNode(dteId, usuarioId);
        return dteService.findById(dteId);
    }


    public Boolean wipeDteData() {
        documentoElectronicoRepository.deleteAll();
        loteDteRepository.deleteAll();
        return true;
    }

    public Boolean fixNullCreadoEn() {
        List<DocumentoElectronico> docs = documentoElectronicoRepository.findAll();
        int fixed = 0;
        for (DocumentoElectronico doc : docs) {
            if (doc.getCreadoEn() == null) {
                doc.setCreadoEn(LocalDateTime.now());
                documentoElectronicoRepository.save(doc);
                fixed++;
            }
        }
        return fixed > 0;
    }

    public DteMetricsDto dteMetrics() {
        // Usar consultas eficientes de conteo con JPQL
        long total = documentoElectronicoRepository.count();

        // Contar documentos por estado usando consultas JPQL eficientes
        // NOTA: Usando PENDIENTE_APROBACION ya que es el estado real usado en la base de datos
        long pendientes = countDocumentosByEstado("PENDIENTE_APROBACION");
        long generados = countDocumentosByEstado("GENERADO");
        long enviados = countDocumentosByEstado("ENVIADO");
        long aprobados = countDocumentosByEstado("APROBADO");
        long rechazados = countDocumentosByEstado("RECHAZADO");
        long cancelados = countDocumentosByEstado("CANCELADO");

        return new DteMetricsDto(total, pendientes, generados, enviados, aprobados, rechazados, cancelados);
    }

    /**
     * Método auxiliar para contar documentos por estado
     */
    private long countDocumentosByEstado(String estado) {
        try {
            // Usar paginación con tamaño razonable para obtener el total correcto
            return documentoElectronicoRepository.findByEstadoSifen(estado, PageRequest.of(0, 1000)).getTotalElements();
        } catch (Exception ex) {
            return 0;
        }
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


