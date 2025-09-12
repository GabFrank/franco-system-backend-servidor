package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.ConsultaRucResponse;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.enums.TipoCliente;
import com.franco.dev.repository.personas.ClienteRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.sifen.service.SifenService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class ClienteService extends CrudService<Cliente, ClienteRepository, Long> {

    private final ClienteRepository repository;
    private final SifenService sifenService;


    @Override
    public ClienteRepository getRepository() {
        return repository;
    }

    public Cliente findByPersonaId(Long id){
        return repository.findByPersonaId(id);
    }

    public List<Cliente> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByPersona(texto);
    }

    public Page<Cliente> findByAll2(String texto, TipoCliente tipoCliente, Integer page, Integer size){
        texto = texto != null ? texto.replace(' ', '%').toUpperCase(): "%";
        Pageable pagina = PageRequest.of(page, size);
        return  repository.findByAll(texto, tipoCliente, pagina);
    }

    @Override
    public Cliente save(Cliente entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        Cliente p = super.save(entity);
//        personaPublisher.publish(p);
        return p;
    }

    public ConsultaRucResponse consultaRuc(String ruc) {
        com.franco.dev.service.sifen.dto.response.ConsultaRucResponse sifenResponse = sifenService.consultaRuc(ruc);
        // Mapear del DTO de servicio al DTO de dominio
        ConsultaRucResponse domainResponse = new ConsultaRucResponse();
        domainResponse.setProcesamientoCorrecto(sifenResponse.isProcesamientoCorrecto());
        domainResponse.setCodigoRespuesta(sifenResponse.getCodigoRespuesta());
        domainResponse.setMensajeRespuesta(sifenResponse.getMensajeRespuesta());
        domainResponse.setTimestamp(sifenResponse.getTimestamp());
        domainResponse.setRuc(sifenResponse.getRuc());
        domainResponse.setRazonSocial(sifenResponse.getRazonSocial());
        domainResponse.setEstadoContribuyente(sifenResponse.getEstadoContribuyente());
        domainResponse.setCodigoEstadoContribuyente(sifenResponse.getCodigoEstadoContribuyente());
        domainResponse.setEsFacturadorElectronico(sifenResponse.getEsFacturadorElectronico());
        return domainResponse;
    }

}
