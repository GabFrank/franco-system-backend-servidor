package com.franco.dev.scheduler;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.LoteDte;
import com.franco.dev.domain.financiero.DteEstado;
import com.franco.dev.repository.financiero.DocumentoElectronicoRepository;
import com.franco.dev.repository.financiero.LoteDteRepository;
import com.franco.dev.service.financiero.DteNodeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DteScheduler {

    private final DocumentoElectronicoRepository documentoElectronicoRepository;
    private final LoteDteRepository loteDteRepository;
    private final DteNodeClient nodeClient;

    @Value("${dte.node.mock:true}")
    private boolean mockMode;

    // Cada 10 minutos
    @Scheduled(fixedRate = 600_000)
    @Transactional
    public void procesarLotesPendientes() {
        log.info("[DTE] Buscando documentos 'GENERADO' para enviar a SIFEN");
        List<DocumentoElectronico> docs = documentoElectronicoRepository.findTop50ByEstadoSifenOrderByIdAsc(DteEstado.GENERADO.name());
        if (docs.isEmpty()) {
            log.info("[DTE] No hay documentos en estado GENERADO");
            return;
        }

        LoteDte nuevoLote = new LoteDte();
        nuevoLote.setFechaEnvio(LocalDateTime.now());

        if (mockMode) {
            // Modo mock: no llamar al Node, simular envío inmediato
            nuevoLote.setEstadoSifen(DteEstado.RECIBIDO_POR_SIFEN.name());
            nuevoLote.setIdProtocoloSifen("mock-" + System.currentTimeMillis());
            nuevoLote = loteDteRepository.save(nuevoLote);
            final LoteDte loteRef = nuevoLote;
            docs.forEach(d -> {
                d.setLote(loteRef);
                d.setEstadoSifen(DteEstado.ENVIADO.name());
            });
            documentoElectronicoRepository.saveAll(docs);
            log.info("[DTE] (MOCK) Lote {} simulado como RECIBIDO_POR_SIFEN", nuevoLote.getId());
            return;
        } else {
            nuevoLote.setEstadoSifen(DteEstado.ENVIADO.name());
            nuevoLote = loteDteRepository.save(nuevoLote);
        }

        final LoteDte loteRef = nuevoLote;
        docs.forEach(d -> {
            d.setLote(loteRef);
            d.setEstadoSifen("ENVIADO");
        });
        documentoElectronicoRepository.saveAll(docs);

        try {
            String idProtocolo = nodeClient.enviarLote(
                    docs.stream().map(DocumentoElectronico::getXmlFirmado).collect(Collectors.toList())
            );
            nuevoLote.setIdProtocoloSifen(idProtocolo);
            nuevoLote.setEstadoSifen(DteEstado.RECIBIDO_POR_SIFEN.name());
            loteDteRepository.save(nuevoLote);
            log.info("[DTE] Lote {} enviado. Protocolo: {}", nuevoLote.getId(), idProtocolo);
        } catch (Exception e) {
            log.error("[DTE] Error enviando lote {}", nuevoLote.getId(), e);
            nuevoLote.setEstadoSifen(DteEstado.ERROR_ENVIO.name());
            loteDteRepository.save(nuevoLote);
            // Revertir estados
            docs.forEach(d -> d.setEstadoSifen(DteEstado.GENERADO.name()));
            documentoElectronicoRepository.saveAll(docs);
        }
    }

    // Cada 5 minutos
    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void consultarResultadosDeLotes() {
        log.info("[DTE] Consultando resultados de lotes 'RECIBIDO_POR_SIFEN'");
        List<LoteDte> lotes = loteDteRepository.findByEstadoSifen(DteEstado.RECIBIDO_POR_SIFEN.name());
        if (lotes.isEmpty()) return;
        if (mockMode) {
            Random rnd = new Random();
            String[] motivosRechazo = new String[]{
                    "RUC del receptor inválido",
                    "Timbrado vencido",
                    "Monto total inconsistente",
                    "IVA mal calculado",
                    "Fecha de emisión fuera de rango",
                    "Campos obligatorios faltantes"
            };
            for (LoteDte lote : lotes) {
                List<DocumentoElectronico> docs = documentoElectronicoRepository.findByLoteId(lote.getId());
                for (DocumentoElectronico d : docs) {
                    boolean aprobado = rnd.nextInt(10) < 8; // 80%
                    d.setEstadoSifen(aprobado ? DteEstado.APROBADO.name() : DteEstado.RECHAZADO.name());
                    if (aprobado) {
                        d.setMensajeSifen("Aprobado por SIFEN (mock)");
                    } else {
                        String motivo = motivosRechazo[rnd.nextInt(motivosRechazo.length)];
                        d.setMensajeSifen("Rechazado: " + motivo);
                    }
                }
                documentoElectronicoRepository.saveAll(docs);
                lote.setEstadoSifen(DteEstado.PROCESADO_OK.name());
                lote.setRespuestaSifen("<mock>procesado</mock>");
                loteDteRepository.save(lote);
                log.info("[DTE] (MOCK) Lote {} procesado con {} documentos", lote.getId(), docs.size());
            }
            return;
        }
        for (LoteDte lote : lotes) {
            try {
                String respuesta = nodeClient.consultarLote(lote.getIdProtocoloSifen());
                lote.setRespuestaSifen(respuesta);
                lote.setEstadoSifen(DteEstado.PROCESADO_OK.name());
                loteDteRepository.save(lote);
            } catch (Exception e) {
                log.error("[DTE] Error consultando lote {}", lote.getId(), e);
                lote.setEstadoSifen(DteEstado.ERROR_CONSULTA.name());
                loteDteRepository.save(lote);
            }
        }
    }
}


