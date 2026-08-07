package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.TipoJustificativo;
import com.franco.dev.graphql.rrhh.input.TipoJustificativoInput;
import com.franco.dev.repository.rrhh.JustificativoRepository;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.TipoJustificativoService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class TipoJustificativoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private TipoJustificativoService service;

    @Autowired
    private com.franco.dev.service.rrhh.RrhhSecurityService seg;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private JustificativoRepository justificativoRepository;

    public Optional<TipoJustificativo> tipoJustificativo(Long id) {
        return service.findById(id);
    }

    public List<TipoJustificativo> tiposJustificativo() {
        return service.findAll2();
    }

    public List<TipoJustificativo> tiposJustificativoActivos() {
        return service.findActivos();
    }

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    public Page<TipoJustificativo> tiposJustificativoPage(int page, int size, String nombre, Boolean activo) {
        return service.findPage(nombre, activo, PageRequest.of(page, size));
    }

    public TipoJustificativo saveTipoJustificativo(TipoJustificativoInput input) {
        seg.requireAnyRole(seg.GESTIONAR);
        TipoJustificativo e = input.getId() != null
                ? service.findById(input.getId()).orElse(new TipoJustificativo())
                : new TipoJustificativo();
        e.setNombre(input.getNombre());
        e.setDescripcion(input.getDescripcion());
        if (input.getEvitaPenalizacion() != null) e.setEvitaPenalizacion(input.getEvitaPenalizacion());
        if (input.getDescuentaSalario() != null) e.setDescuentaSalario(input.getDescuentaSalario());
        if (input.getRequiereDocumento() != null) e.setRequiereDocumento(input.getRequiereDocumento());
        if (input.getActivo() != null) e.setActivo(input.getActivo());
        // generadoPorSistema no se expone al usuario: lo controla el backend para los
        // tipos que emiten otros modulos (VACACION / FERIADO).
        if (input.getUsuarioId() != null)
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteTipoJustificativo(Long id) {
        seg.requireAnyRole(seg.GESTIONAR);
        TipoJustificativo e = service.findById(id).orElse(null);
        if (e != null && Boolean.TRUE.equals(e.getGeneradoPorSistema())) {
            throw new GraphQLException("No se puede eliminar un tipo generado por el sistema");
        }
        long enUso = justificativoRepository.countByTipoId(id);
        if (enUso > 0) {
            throw new GraphQLException("No se puede eliminar: el tipo tiene " + enUso
                    + " justificativo/s registrado/s. Desactivelo en lugar de eliminarlo.");
        }
        return service.deleteById(id);
    }
}
