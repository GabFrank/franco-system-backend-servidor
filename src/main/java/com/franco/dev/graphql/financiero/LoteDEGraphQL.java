package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.graphql.financiero.input.LoteDEInput;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.LoteDEService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.sifen.SifenService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;

import javax.transaction.Transactional;

@Component
public class LoteDEGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

  @Autowired
  private LoteDEService service;

  @Autowired
  private UsuarioService usuarioService;

  @Autowired
  private DocumentoElectronicoService documentoElectronicoService;

  @Autowired
  private SifenService sifenService;

    public Page<LoteDE> loteDes(int page, int size, EstadoLoteDE estado, String fechaInicio, String fechaFin) {
        return service.findByEstadoOrFechaProcesadoBetween(estado, fechaInicio, fechaFin, page, size);
    }

    public List<LoteDE> loteDesPaginado(int page, int size) {
        return service.findAll(page, size);
    }

    public LoteDE loteDe(Long id) {
        return service.findById(id).orElse(null);
    }

    @Transactional
    public LoteDE saveLoteDe(LoteDEInput input) {
        ModelMapper m = new ModelMapper();
        LoteDE entity = m.map(input, LoteDE.class);
        if (input.getUsuarioId() != null) {
            entity.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        return service.save(entity);
    }

    // ========== NUEVAS CONSULTAS SIFEN ==========

    public List<LoteDE> lotesPendientes() {
        return service.findByEstado(EstadoLoteDE.PENDIENTE_ENVIO);
    }

    public List<LoteDE> lotesEnProceso() {
        return service.findByEstado(EstadoLoteDE.EN_PROCESO);
    }

    // ========== NUEVAS MUTACIONES SIFEN ==========

    @Transactional
    public LoteDE crearLote() {
        try {
            return sifenService.crearLote();
        } catch (Exception e) {
            throw new RuntimeException("Error al crear lote: " + e.getMessage());
        }
    }

    @Transactional
    public Boolean vincularDocumentosALote(Long loteId, List<Long> documentoIds) {
        try {
            LoteDE lote = service.findById(loteId).orElse(null);
            if (lote == null) {
                throw new RuntimeException("Lote no encontrado");
            }

            List<DocumentoElectronico> documentos = documentoElectronicoService.findByIdIn(documentoIds);
            sifenService.vincularDocumentosALote(lote, documentos);

            return true;
        } catch (Exception e) {
            throw new RuntimeException("Error al vincular documentos: " + e.getMessage());
        }
    }

    public EnvioLoteResponse enviarLote(Long loteId) {
        try {
            LoteDE lote = service.findById(loteId).orElse(null);
            if (lote == null) {
                throw new RuntimeException("Lote no encontrado");
            }

            sifenService.enviarLote(lote);

            EnvioLoteResponse response = new EnvioLoteResponse();
            response.setExitoso(true);
            response.setProtocolo(lote.getProtocolo());
            response.setMensaje("Lote enviado exitosamente");
            response.setLote(lote);

            return response;

        } catch (Exception e) {
            EnvioLoteResponse response = new EnvioLoteResponse();
            response.setExitoso(false);
            response.setMensaje("Error al enviar lote: " + e.getMessage());
            return response;
        }
    }

    public ConsultaLoteResponse consultarLote(Long loteId) {
        try {
            LoteDE lote = service.findById(loteId).orElse(null);
            if (lote == null) {
                throw new RuntimeException("Lote no encontrado");
            }

            sifenService.consultarLote(lote);

            ConsultaLoteResponse response = new ConsultaLoteResponse();
            response.setExitoso(true);
            response.setEstado(lote.getEstado());
            response.setMensaje("Lote consultado exitosamente");
            response.setDocumentosProcesados(0); // TODO: Calcular realmente
            response.setLote(lote);

            return response;

        } catch (Exception e) {
            ConsultaLoteResponse response = new ConsultaLoteResponse();
            response.setExitoso(false);
            response.setMensaje("Error al consultar lote: " + e.getMessage());
            return response;
        }
    }

    // ========== CLASES DE RESPUESTA ==========

    public static class EnvioLoteResponse {
        private boolean exitoso;
        private String protocolo;
        private String mensaje;
        private LoteDE lote;

        // Getters y setters
        public boolean isExitoso() { return exitoso; }
        public void setExitoso(boolean exitoso) { this.exitoso = exitoso; }

        public String getProtocolo() { return protocolo; }
        public void setProtocolo(String protocolo) { this.protocolo = protocolo; }

        public String getMensaje() { return mensaje; }
        public void setMensaje(String mensaje) { this.mensaje = mensaje; }

        public LoteDE getLote() { return lote; }
        public void setLote(LoteDE lote) { this.lote = lote; }
    }

    public static class ConsultaLoteResponse {
        private boolean exitoso;
        private EstadoLoteDE estado;
        private String mensaje;
        private int documentosProcesados;
        private LoteDE lote;

        // Getters y setters
        public boolean isExitoso() { return exitoso; }
        public void setExitoso(boolean exitoso) { this.exitoso = exitoso; }

        public EstadoLoteDE getEstado() { return estado; }
        public void setEstado(EstadoLoteDE estado) { this.estado = estado; }

        public String getMensaje() { return mensaje; }
        public void setMensaje(String mensaje) { this.mensaje = mensaje; }

        public int getDocumentosProcesados() { return documentosProcesados; }
        public void setDocumentosProcesados(int documentosProcesados) { this.documentosProcesados = documentosProcesados; }

        public LoteDE getLote() { return lote; }
        public void setLote(LoteDE lote) { this.lote = lote; }
    }
}