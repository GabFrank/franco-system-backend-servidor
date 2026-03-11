package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.enums.TipoCliente;
import com.franco.dev.graphql.personas.ConsultaRucResponse;
import com.franco.dev.repository.personas.ClienteRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.sifen.SifenService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class ClienteService extends CrudService<Cliente, ClienteRepository, Long> {

    private final ClienteRepository repository;
    private final PersonaService personaService;
    private final SifenService sifenService;

    public ClienteService(ClienteRepository repository, PersonaService personaService,
        @Autowired(required = false) SifenService sifenService) {
          this.repository = repository;
          this.personaService = personaService;
          this.sifenService = sifenService;
    }

    @Override
    public ClienteRepository getRepository() {
        return repository;
    }

    public Cliente findByPersonaId(Long id) {
        return repository.findByPersonaId(id);
    }

    public List<Cliente> findByAll(String texto) {
        texto = texto.replace(' ', '%');
        return repository.findByPersona(texto);
    }

    public Page<Cliente> findByAll2(String texto, TipoCliente tipoCliente, Integer page, Integer size) {
        texto = texto != null ? texto.replace(' ', '%').toUpperCase() : "%";
        Pageable pagina = PageRequest.of(page, size);
        return repository.findByAll(texto, tipoCliente, pagina);
    }

    @Override
    public Cliente save(Cliente entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        Cliente p = super.save(entity);
        // personaPublisher.publish(p);
        return p;
    }

    public ConsultaRucResponse consultaRuc(String ruc) {
        // TODO: Implementar la consulta al RUC
        return null;
    }

    public Cliente findByPersonaDocumento(String texto) {
        return repository.findByPersonaDocumento(texto);
    }

    /**
     * Busca o crea un cliente por documento, consultando SIFEN si es necesario.
     *
     * @param documento Documento/RUC del cliente (solo números, sin guiones)
     * @return Cliente encontrado o creado, o null si no se pudo crear
     */
    public Cliente buscarOCrearClientePorDocumento(String documento) {
        // 1. Buscar cliente existente en BD
        Cliente cliente = repository.findByPersonaDocumento(documento);
        if (cliente != null && Boolean.TRUE.equals(cliente.getVerificadoSet())) {
            log.debug("Cliente encontrado y verificado: {}", cliente.getId());
            return cliente; // Cliente ya existe y está verificado
        }

        // 2. Si no existe o no está verificado, consultar SIFEN
        if (sifenService == null) {
            log.warn("SIFEN no está disponible, retornando cliente existente si hay");
            return cliente; // Retornar cliente existente si hay
        }

        ConsultaRucResponse respuestaSifen = sifenService.consultaRuc(documento);
        if (respuestaSifen == null) {
            log.warn("No se pudo obtener respuesta de SIFEN para documento: {}", documento);
            return cliente; // Retornar cliente existente si hay
        }

        // 3. Validar que sea contribuyente
        if (respuestaSifen.getRuc() == null || respuestaSifen.getRuc().trim().isEmpty()) {
            log.warn("El documento {} no es contribuyente de SET", documento);
            throw new RuntimeException("El cliente no es contribuyente de SET");
        }

        // 4. Crear o actualizar Persona con datos de SIFEN
        Persona persona = cliente != null ? cliente.getPersona() : null;
        if (persona == null) {
            persona = personaService.findByDocumento(documento);
        }

        if (persona == null) {
            persona = new Persona();
            persona.setDocumento(documento);
        }

        // Actualizar datos desde SIFEN
        String nombreProcesado = formatearRazonSocial(respuestaSifen.getRazonSocial());
        if (nombreProcesado != null && !nombreProcesado.isEmpty()) {
            persona.setNombre(nombreProcesado.toUpperCase());
        }
        if (respuestaSifen.getDireccion() != null && !respuestaSifen.getDireccion().trim().isEmpty()) {
            persona.setDireccion(respuestaSifen.getDireccion().toUpperCase());
        }

        // Guardar persona
        persona = personaService.save(persona);

        // 5. Crear o actualizar Cliente
        if (cliente == null) {
            cliente = new Cliente();
            cliente.setPersona(persona);
            cliente.setCreadoEn(LocalDateTime.now());
            cliente.setTipo(TipoCliente.NORMAL);
        }

        cliente.setVerificadoSet(true);
        cliente.setTributa("ACTIVO".equalsIgnoreCase(respuestaSifen.getEstadoContribuyente()));

        // Guardar cliente
        return save(cliente);
    }

    /**
     * Formatea la razón social de SIFEN (apellido, nombre -> nombre apellido).
     */
    private String formatearRazonSocial(String razonSocial) {
        if (razonSocial == null || razonSocial.trim().isEmpty()) {
            return null;
        }

        String valor = razonSocial.trim();
        int indiceComa = valor.indexOf(',');

        if (indiceComa < 0) {
            return valor; // No hay coma, retornar tal cual
        }

        String apellido = valor.substring(0, indiceComa).trim();
        String nombres = valor.substring(indiceComa + 1).trim();

        if (nombres.isEmpty()) {
            return valor.replace(",", " ").trim();
        }

        if (apellido.isEmpty()) {
            return nombres;
        }

        return nombres + " " + apellido;
    }

}