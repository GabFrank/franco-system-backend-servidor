package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.PreGasto;
import com.franco.dev.domain.financiero.enums.EstadoPreGasto;
import com.franco.dev.graphql.financiero.input.PreGastoInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.PreGastoService;
import com.franco.dev.service.financiero.TipoGastoService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PreGastoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PreGastoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private TipoGastoService tipoGastoService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private SucursalService sucursalService;

    public PreGasto preGasto(Long id, Long sucId) {
        return service.findByIdAndSucursalId(id, sucId);
    }

    public List<PreGasto> preGastos(String estado, Long sucId) {
        if (estado != null && !estado.isEmpty()) {
            if (sucId != null && sucId > 0) {
                return service.buscarPorEstadoYSucursal(EstadoPreGasto.valueOf(estado), sucId);
            } else {
                return service.buscarPorEstado(EstadoPreGasto.valueOf(estado));
            }
        }
        return service.findAll2();
    }

    public List<PreGasto> preGastosPorSucursal(String estado, Long sucursalId) {
        return service.buscarPorEstadoYSucursal(EstadoPreGasto.valueOf(estado), sucursalId);
    }

    public List<PreGasto> preGastosPorFuncionario(Long funcionarioId) {
        return service.buscarPorFuncionario(funcionarioId);
    }

    public List<PreGasto> preGastosSearch(String texto, Long sucId) {
        return service.buscarPorTexto(texto, sucId);
    }

    public Page<PreGasto> filterPreGastos(String estado, String inicio, String fin, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page != null ? page : 0, size != null ? size : 15);
        return service.filterPreGastos(estado, inicio, fin, pageable);
    }

    // === Mutations ===

    public PreGasto savePreGasto(PreGastoInput input) {
        ModelMapper m = new ModelMapper();
        PreGasto e = m.map(input, PreGasto.class);

        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getFuncionarioId() != null) {
            e.setFuncionario(personaService.findById(input.getFuncionarioId()).orElse(null));
        }
        if (input.getTipoGastoId() != null) {
            e.setTipoGasto(tipoGastoService.findById(input.getTipoGastoId()).orElse(null));
        }
        if (input.getMonedaId() != null) {
            e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        }
        if (input.getSucursalCajaId() != null) {
            e.setSucursalCaja(sucursalService.findById(input.getSucursalCajaId()).orElse(null));
        }
        if (input.getAutorizadoPorId() != null) {
            e.setAutorizadoPor(personaService.findById(input.getAutorizadoPorId()).orElse(null));
        }
        if (input.getDelegadoAId() != null) {
            e.setDelegadoA(personaService.findById(input.getDelegadoAId()).orElse(null));
        }

        e = service.save(e);
        return e;
    }

    public PreGasto autorizarPreGasto(Long id, Long autorizadorId, Long sucId) {
        PreGasto preGasto = service.autorizar(id, autorizadorId, sucId);
        if (preGasto != null && autorizadorId != null) {
            preGasto.setAutorizadoPor(personaService.findById(autorizadorId).orElse(null));
            preGasto = service.save(preGasto);
        }
        return preGasto;
    }

    public PreGasto rechazarPreGasto(Long id, String motivo, Long sucId) {
        return service.rechazar(id, motivo, sucId);
    }

    public PreGasto tramitarPreGasto(Long id, Long sucId) {
        return service.enviarATramite(id, sucId);
    }

    public PreGasto completarPreGasto(Long id, Long sucId) {
        return service.completar(id, sucId);
    }

    public Boolean deletePreGasto(Long id, Long sucId) {
        PreGasto e = service.findByIdAndSucursalId(id, sucId);
        return service.delete(e);
    }
}
