package com.franco.dev.graphql.empresarial;

import com.franco.dev.domain.empresarial.Impresora;
import com.franco.dev.graphql.empresarial.input.ImpresoraInput;
import com.franco.dev.service.empresarial.ImpresoraService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.impresion.CupsAdminService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.franco.dev.config.multitenant.CustomPage;
import com.franco.dev.config.multitenant.CustomPageImpl;

import java.util.List;
import java.util.Optional;

@Component
public class ImpresoraGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ImpresoraService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private CupsAdminService cupsAdminService;

    public Optional<Impresora> impresora(Long id) {
        return service.findById(id);
    }

    public List<Impresora> impresoras(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<Impresora> impresorasPorSucursalId(Long id) {
        return service.findBySucursalId(id);
    }

    public CustomPage<Impresora> impresoraSearchPage(String texto, Integer page, Integer size) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 10 : size;
        Pageable pageable = PageRequest.of(p, s);
        Page<Impresora> pageResult = service.buscarConPagina(texto, p, s);
        return new CustomPageImpl<>(pageResult.getContent(), pageable, pageResult.getTotalElements(), null);
    }

    public List<Impresora> impresorasActivas() {
        return service.findActivas();
    }

    /**
     * Colas de impresion visibles localmente en el host que atiende esta consulta
     * (en Linux, las colas CUPS, incluyendo las que apuntan a un device-uri remoto
     * via IPP). Sirve para descubrir impresoras del servidor/filial al dar de alta,
     * no solo las del desktop.
     */
    public List<String> impresorasDelSistema() {
        return cupsAdminService.listarColasInstaladas();
    }

    /**
     * ModelMapper con matching STRICT (solo nombres exactos). Con la estrategia por defecto,
     * {@code smbUsuario} y {@code usuarioId} caian los dos sobre el destino
     * {@code Impresora.usuario.usuario} (Usuario se auto-referencia) y el mapeo abortaba con
     * "matches multiple source property hierarchies". Los ids (sucursalId/usuarioId) se resuelven
     * a mano mas abajo, asi que no perdemos nada al exigir nombres exactos.
     */
    private static final ModelMapper MAPPER = strictMapper();

    static ModelMapper strictMapper() {
        ModelMapper m = new ModelMapper();
        m.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
        return m;
    }

    public Impresora saveImpresora(ImpresoraInput input) {
        Impresora e = MAPPER.map(input, Impresora.class);
        if (input.getSucursalId() != null) {
            e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        }
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        return service.save(e);
    }

    public Boolean deleteImpresora(Long id) {
        return service.deleteById(id);
    }

    public Long countImpresora() {
        return service.count();
    }
}
