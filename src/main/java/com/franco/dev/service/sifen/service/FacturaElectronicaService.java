package com.franco.dev.service.sifen.service;

import com.franco.dev.domain.personas.FacturaElectronicaInput;
import com.franco.dev.domain.personas.GeneracionXmlResponse;
import com.franco.dev.service.sifen.dto.request.FacturaElectronicaRequest;
import com.franco.dev.service.sifen.service.GeneracionXmlService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class FacturaElectronicaService {
  
  private final GeneracionXmlService generacionXmlService;

  public GeneracionXmlResponse generarXmlFactura(FacturaElectronicaInput input) {
    // Convertir input de dominio a request de servicio
    FacturaElectronicaRequest request = 
        convertirInputARequest(input);
    
    // Llamar al servicio de generación de XML
    com.franco.dev.service.sifen.dto.response.GeneracionXmlResponse sifenResponse = 
        generacionXmlService.generarXmlFactura(request);
    
    // Mapear del DTO de servicio al DTO de dominio
    GeneracionXmlResponse domainResponse = new GeneracionXmlResponse();
    domainResponse.setProcesamientoCorrecto(sifenResponse.isProcesamientoCorrecto());
    domainResponse.setCodigoRespuesta(sifenResponse.getCodigoRespuesta());
    domainResponse.setMensajeRespuesta(sifenResponse.getMensajeRespuesta());
    domainResponse.setTimestamp(sifenResponse.getTimestamp());
    domainResponse.setXmlGenerado(sifenResponse.getXmlGenerado());
    domainResponse.setNumeroControl(sifenResponse.getNumeroControl());
    domainResponse.setCodigoSeguridad(sifenResponse.getCodigoSeguridad());
    domainResponse.setQrCode(sifenResponse.getQrCode());
    domainResponse.setFechaHoraFirma(sifenResponse.getFechaHoraFirma());
    domainResponse.setHash(sifenResponse.getHash());
    domainResponse.setArchivoXml(sifenResponse.getArchivoXml());
    domainResponse.setArchivoPdf(sifenResponse.getArchivoPdf());
    domainResponse.setEstadoDocumento(sifenResponse.getEstadoDocumento());
    domainResponse.setMensajeValidacion(sifenResponse.getMensajeValidacion());
    
    return domainResponse;
  }

  private FacturaElectronicaRequest convertirInputARequest(FacturaElectronicaInput input) {
    FacturaElectronicaRequest request = 
        new FacturaElectronicaRequest();
    
    // Mapear campos básicos
    request.setRucEmisor(input.getRucEmisor());
    request.setRazonSocialEmisor(input.getRazonSocialEmisor());
    request.setDireccionEmisor(input.getDireccionEmisor());
    request.setTelefonoEmisor(input.getTelefonoEmisor());
    request.setEmailEmisor(input.getEmailEmisor());
    request.setRucReceptor(input.getRucReceptor());
    request.setRazonSocialReceptor(input.getRazonSocialReceptor());
    request.setDireccionReceptor(input.getDireccionReceptor());
    request.setTelefonoReceptor(input.getTelefonoReceptor());
    request.setEmailReceptor(input.getEmailReceptor());
    request.setTipoDocumento(input.getTipoDocumento());
    request.setNumeroDocumento(input.getNumeroDocumento());
    request.setFechaEmision(java.time.LocalDateTime.parse(input.getFechaEmision()));
    request.setMoneda(input.getMoneda());
    request.setTipoCambio(input.getTipoCambio());
    request.setCondicionVenta(input.getCondicionVenta());
    request.setTipoPago(input.getTipoPago());
    request.setTotalGravado10(input.getTotalGravado10());
    request.setTotalGravado5(input.getTotalGravado5());
    request.setTotalExento(input.getTotalExento());
    request.setTotalIva10(input.getTotalIva10());
    request.setTotalIva5(input.getTotalIva5());
    request.setTotalGeneral(input.getTotalGeneral());
    request.setObservaciones(input.getObservaciones());
    request.setCodigoEstablecimiento(input.getCodigoEstablecimiento());
    request.setCodigoPuntoExpedicion(input.getCodigoPuntoExpedicion());
    request.setCodigoSucursal(input.getCodigoSucursal());
    
    // TODO: Mapear items si es necesario
    
    return request;
  }
}
