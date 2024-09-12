package com.franco.dev.graphql.financiero;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.graphql.financiero.input.GastoInput;
import com.franco.dev.service.financiero.GastoService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.financiero.TipoGastoService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.utilitarios.StringUtils;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GastoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private GastoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private GastoDetalleGraphQL gastoDetalleGraphQL;

    @Autowired
    private PdvCajaService pdvCajaService;

    @Autowired
    private ImpresionService impresionService;

    @Autowired
    private TipoGastoService tipoGastoService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Gasto gasto(Long id, Long sucId) {
        return service.findByIdAndSucursalId(id, sucId);
    }

    public List<Gasto> gastos(int page, int size, Long sucId) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<Gasto> gastosPorCajaId(Long id, Long sucId) {
        return service.findByCajaId(id, sucId);
    }

    public List<Gasto> gastosPorFecha(String inicio, String fin, Long sucId) {
        return service.findByDate(inicio, fin, sucId);
    }

    public Gasto saveGasto(GastoInput input, String printerName, String local) throws GraphQLException {
        ModelMapper m = new ModelMapper();
        Gasto e = m.map(input, Gasto.class);
//        e = service.save(e);
//        multiTenantService.compartir(null, (Gasto s) -> service.save(s), e);
        return e;
    }

    public Page<Gasto> filterGastos(Long id, Long cajaId, Long sucId, Long responsableId, String descripcion, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        descripcion = StringUtils.convertToCustomFormat(descripcion);
        String finalDescripcion = descripcion;
        return service.filterGastosPage(id, cajaId, sucId, responsableId, finalDescripcion, pageable);
    }

//    public List<Gasto> gastosSearch(String texto){
//        return service.findByAll(texto);
//    }

    public Boolean deleteGasto(Long id, Long sucId) {
        Gasto gasto = service.findByIdAndSucursalId(id, sucId);
        if (gasto != null) {
            return service.delete(gasto);
        } else {
            throw new GraphQLException("No se pudo eliminar el gasto");
        }
    }

    public Long countGasto() {
        return service.count();
    }


}
