package com.franco.dev.graphql.financiero;

import java.util.List;

import javax.transaction.Transactional;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.graphql.financiero.input.DocumentoElectronicoInput;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.sifen.SifenService;
import com.roshka.sifen.core.beans.response.RespuestaConsultaDE;

import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;

@Component
public class DocumentoElectronicoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

  @Autowired
  private DocumentoElectronicoService service;

  @Autowired
  private UsuarioService usuarioService;

  @Autowired
  private FacturaLegalService facturaLegalService;

  @Autowired(required = false)
  private SifenService sifenService;

  public List<DocumentoElectronico> documentoElectronicos(int page, int size) {
    Pageable pageable = PageRequest.of(page, size);
    return service.findAll(pageable);
  }
  
  @Transactional
  public DocumentoElectronico saveDocumentoElectronico(DocumentoElectronicoInput input) {
    ModelMapper m = new ModelMapper();
    DocumentoElectronico e = m.map(input, DocumentoElectronico.class);
    if (input.getUsuarioId() != null) {
      e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
    }
    return service.save(e);
  }

  // ========== NUEVAS CONSULTAS SIFEN ==========

  public DocumentoElectronico documentoElectronico(Long id, Long sucursalId) {
    return service.findById(new EmbebedPrimaryKey(id, sucursalId)).orElse(null);
  }

  public DocumentoElectronico documentoElectronicoPorCdc(String cdc) {
    return service.findByCdc(cdc).orElse(null);
  }

  public List<DocumentoElectronico> documentoElectronicosPorEstado(EstadoDE estado, int page, int size) {
    // TODO: Implementar paginación real con service.findByFilters(estado, null, null, pageable)
    return service.findByEstado(estado);
  }

  public List<DocumentoElectronico> documentoElectronicosPorLote(Long loteId, Long sucursalId) {
    return service.findByLoteDeIdAndSucursalIdAndEstado(loteId, sucursalId, null);
  }

  // ========== NUEVAS MUTACIONES SIFEN ==========

  @Transactional
  public DocumentoElectronico crearDocumentoElectronicoDesdeFactura(Long facturaId, Long sucursalId) {
    if (sifenService == null) {
      throw new RuntimeException("La funcionalidad SIFEN está deshabilitada. Configure sifen.enabled=true en application.properties");
    }

    FacturaLegal factura = facturaLegalService.findByIdAndSucursalId(facturaId, sucursalId);
    if (factura == null) {
      throw new RuntimeException("Factura no encontrada");
    }

    try {
      return sifenService.crearDocumentoElectronico(factura);
    } catch (Exception e) {
      throw new RuntimeException("Error al crear documento electrónico: " + e.getMessage());
    }
  }

  public ConsultaDEResponse consultarDocumentoElectronico(String cdc) {
    if (sifenService == null) {
      ConsultaDEResponse errorResponse = new ConsultaDEResponse();
      errorResponse.setEstadoSifen("ERROR");
      errorResponse.setCodigoRespuesta("9999");
      errorResponse.setMensajeRespuesta("La funcionalidad SIFEN está deshabilitada. Configure sifen.enabled=true en application.properties");
      errorResponse.setExitoso(false);
      return errorResponse;
    }

    try {
      RespuestaConsultaDE respuesta = sifenService.consultarDE(cdc);

      // Buscar el DE en la base de datos por CDC (se actualiza automáticamente por consultarDE)
      DocumentoElectronico de = service.findByCdc(cdc).orElse(null);

      ConsultaDEResponse graphqlResponse = new ConsultaDEResponse();
      graphqlResponse.setDocumentoElectronico(de);
      
      // Los códigos de SIFEN: 0300 = Aprobado, otros = Error/Rechazado
      String codigoRespuesta = respuesta.getdCodRes();
      String mensajeRespuesta = respuesta.getdMsgRes();
      boolean exitoso = "0300".equals(codigoRespuesta);
      
      graphqlResponse.setEstadoSifen(exitoso ? "Aprobado" : "Rechazado");
      graphqlResponse.setCodigoRespuesta(codigoRespuesta);
      graphqlResponse.setMensajeRespuesta(mensajeRespuesta);
      graphqlResponse.setExitoso(exitoso);

      return graphqlResponse;

    } catch (Exception e) {
      ConsultaDEResponse errorResponse = new ConsultaDEResponse();
      errorResponse.setEstadoSifen("ERROR");
      errorResponse.setCodigoRespuesta("9999");
      errorResponse.setMensajeRespuesta("Error: " + e.getMessage());
      errorResponse.setExitoso(false);
      return errorResponse;
    }
  }

  // ========== CLASES DE RESPUESTA ==========

  public static class ConsultaDEResponse {
    private DocumentoElectronico documentoElectronico;
    private String estadoSifen;
    private String codigoRespuesta;
    private String mensajeRespuesta;
    private boolean exitoso;

    // Getters y setters
    public DocumentoElectronico getDocumentoElectronico() { return documentoElectronico; }
    public void setDocumentoElectronico(DocumentoElectronico documentoElectronico) { this.documentoElectronico = documentoElectronico; }

    public String getEstadoSifen() { return estadoSifen; }
    public void setEstadoSifen(String estadoSifen) { this.estadoSifen = estadoSifen; }

    public String getCodigoRespuesta() { return codigoRespuesta; }
    public void setCodigoRespuesta(String codigoRespuesta) { this.codigoRespuesta = codigoRespuesta; }

    public String getMensajeRespuesta() { return mensajeRespuesta; }
    public void setMensajeRespuesta(String mensajeRespuesta) { this.mensajeRespuesta = mensajeRespuesta; }

    public boolean isExitoso() { return exitoso; }
    public void setExitoso(boolean exitoso) { this.exitoso = exitoso; }
  }
}
