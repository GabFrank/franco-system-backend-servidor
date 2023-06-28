package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.graphql.financiero.input.GastoInput;
import com.franco.dev.service.financiero.GastoService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.financiero.TipoGastoService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

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

    public Optional<Gasto> gasto(Long id, Long sucId) {
        return service.findById(new EmbebedPrimaryKey(id, sucId));
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
        e = service.save(e);
        return e;
    }

    public List<Gasto> filterGastos(Long id, Long cajaId, Long sucId, Long responsableId, Integer page, Integer size){
        Pageable pageable = PageRequest.of(page, size);
        return service.filterGastos(id, cajaId, sucId, responsableId, pageable);
    }

//    public List<Gasto> gastosSearch(String texto){
//        return service.findByAll(texto);
//    }

    public Boolean deleteGasto(Long id, Long sucId) {
        return service.deleteById(new EmbebedPrimaryKey(id, sucId));
    }

    public Long countGasto() {
        return service.count();
    }


}
