package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.personas.enums.TipoCliente;
import com.franco.dev.repository.personas.FuncionarioRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@AllArgsConstructor
public class FuncionarioService extends CrudService<Funcionario, FuncionarioRepository, Long> {

    private final FuncionarioRepository repository;
    private final UsuarioService usuarioService;
    private final ClienteService clienteService;

    @Override
    public FuncionarioRepository getRepository() {
        return repository;
    }

    public Page<Funcionario> findAllWithPage(Long id, String nombre, List<Long> sucursalList, Pageable pageable) {
        return repository.findAllWithFilterAndPage(id, nombre, sucursalList, null, null, null, null, pageable);
    }

    public Page<Funcionario> findAllWithPage(Long id, String nombre, List<Long> sucursalList,
            Boolean activo, Long cargoId, Boolean diarista, Boolean fasePrueba, Pageable pageable) {
        return repository.findAllWithFilterAndPage(id, nombre, sucursalList, activo, cargoId, diarista, fasePrueba, pageable);
    }

    public Funcionario findByPersonaId(Long id) {
        return repository.findByPersonaId(id);
    }

    public Funcionario findByUsuarioId(Long id) {
        return repository.findByUsuarioId(id);
    }

    public List<Funcionario> findByPersonaNombre(String texto) {
        return repository.findByIdOrPersonaNombre(texto.toUpperCase());
    }

    @Override
    @Transactional
    public Funcionario save(Funcionario entity) {
        boolean esNuevo = entity.getId() == null;

        // Se lee ANTES de persistir. En el path de update la entity NO viene detached:
        // el resolver la obtiene con findById y la muta, así que está managed y sucia
        // dentro del EntityManager de la request (OpenEntityManagerInViewFilter).
        // Por eso findActivoById lleva el hint flushMode=COMMIT: sin él Hibernate
        // auto-flushea el UPDATE antes del SELECT y activoAnterior vendría ya con el
        // valor nuevo, salteando la cascada en ambos sentidos.
        Boolean activoAnterior = esNuevo ? null : repository.findActivoById(entity.getId());

        if (esNuevo) {
            entity.setCreadoEn(LocalDateTime.now());
            entity.setActivo(true);
        }
        if (Boolean.FALSE.equals(entity.getActivo())) {
            // El resolver re-sincroniza cliente.credito desde funcionario.credito después
            // de este save; sin esto el cliente recuperaría el crédito que la cascada borró.
            entity.setCredito(0f);
        }

        Funcionario e = repository.save(entity);

        if (!esNuevo && !Objects.equals(activoAnterior, e.getActivo())) {
            aplicarCascadaEstado(e);
        }
        return e;
    }

    /**
     * Propaga el estado activo/inactivo del funcionario a su usuario y a su cliente.
     * Al inactivar, el cliente pierde el beneficio de funcionario: pasa a NORMAL con crédito 0.
     * Al reactivar vuelve a FUNCIONARIO, pero el crédito NO se restaura: se carga a mano.
     */
    private void aplicarCascadaEstado(Funcionario f) {
        Persona persona = f.getPersona();
        if (persona == null || persona.getId() == null) {
            log.warn("Funcionario {} sin persona: no se cascadea el estado activo", f.getId());
            return;
        }
        boolean activo = Boolean.TRUE.equals(f.getActivo());

        Usuario usuario = usuarioService.findByPersonaId(persona.getId());
        if (usuario == null) usuario = f.getUsuario();
        if (usuario != null) {
            usuario.setActivo(activo);
            usuarioService.save(usuario);
        }

        Cliente cliente = clienteService.findByPersonaId(persona.getId());
        if (cliente != null) {
            cliente.setActivo(activo);
            cliente.setTipo(activo ? TipoCliente.FUNCIONARIO : TipoCliente.NORMAL);
            cliente.setCredito(0f);
            clienteService.save(cliente);
        }
    }
}
