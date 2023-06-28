package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.personas.enums.TipoCliente;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.repository.financiero.FacturaLegalRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.PersonaService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.franco.dev.utilitarios.DateUtils.toDate;

@Service
@AllArgsConstructor
public class FacturaLegalService extends CrudService<FacturaLegal, FacturaLegalRepository, EmbebedPrimaryKey> {

    private final FacturaLegalRepository repository;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private ClienteService clienteService;

    @Override
    public FacturaLegalRepository getRepository() {
        return repository;
    }

    public List<FacturaLegal> findByCajaId(Long id) {
        return repository.findByCajaId(id);
    }

    public FacturaLegal findByVentaIdAndSucursalId(Long id, Long sucId){
        return repository.findByVentaIdAndSucursalId(id, sucId);
    }

    public List<FacturaLegal> findByAll(String fechaInicio, String fechaFin, List<Long> sucId, String ruc, String nombre, Boolean iva5, Boolean iva10) {
        LocalDateTime inicio = toDate(fechaInicio);
        LocalDateTime fin = toDate(fechaFin);
        return repository.findByCreadoEnBetweenAndSucursalId(inicio, fin, sucId, nombre, ruc);
    }

    @Override
    public FacturaLegal save(FacturaLegal entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getCliente() == null) {
            Cliente newCliente = crearCliente(entity.getNombre(), entity.getRuc(), entity.getDireccion(), entity.getUsuario());
            entity.setCliente(newCliente);
        }
        FacturaLegal e = super.save(entity);
        return e;
    }

    public Cliente crearCliente(String nombre, String ruc, String direccion, Usuario usuario) {
        if (nombre != null && ruc != null) {
            Persona foundPersona = personaService.findByDocumento(ruc);
            if (foundPersona == null) {
                foundPersona = new Persona();
                foundPersona.setDocumento(ruc);
                foundPersona.setNombre(nombre);
                foundPersona.setDireccion(direccion);
                foundPersona.setUsuario(usuario);
                foundPersona = personaService.save(foundPersona);
                propagacionService.propagarEntidad(foundPersona, TipoEntidad.PERSONA);
            }
            Cliente newCliente = new Cliente();
            newCliente.setUsuario(usuario);
            newCliente.setPersona(foundPersona);
            newCliente.setTipo(TipoCliente.NORMAL);
            newCliente = clienteService.save(newCliente);
            propagacionService.propagarEntidad(newCliente, TipoEntidad.CLIENTE);
            return newCliente;
        } else {
            return null;
        }
    }
}