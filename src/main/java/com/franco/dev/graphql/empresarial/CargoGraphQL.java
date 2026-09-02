package com.franco.dev.graphql.empresarial;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.empresarial.Cargo;
import com.franco.dev.domain.general.Ciudad;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.empresarial.input.CargoInput;
import com.franco.dev.service.empresarial.CargoService;
import com.franco.dev.service.general.CiudadService;
import com.franco.dev.service.general.PaisService;
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
public class CargoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private CargoService service;

    @Autowired
    private UsuarioService usuarioService;


    @Autowired
    private MultiTenantService multiTenantService;

    @Autowired
    private com.franco.dev.repository.personas.FuncionarioRepository funcionarioRepository;

    @Autowired
    private com.franco.dev.repository.rrhh.FuncionarioCargoHistoricoRepository funcionarioCargoHistoricoRepository;

    @Autowired
    private com.franco.dev.repository.financiero.TipoGastoRepository tipoGastoRepository;

    public Optional<Cargo> cargo(Long id) {return service.findById(id);}

    public List<Cargo> cargos(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<Cargo> cargosSearch(String texto){
        return service.findByAll(texto);
    }


    public Cargo saveCargo(CargoInput input){
        ModelMapper m = new ModelMapper();
        Cargo e = m.map(input, Cargo.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setSupervisadoPor(service.findById(input.getSupervisadoPorId()).orElse(null));
        e = service.save(e);
        return e;
    }

    /**
     * CrudService.deleteById atrapa la excepcion y devuelve false, y el desktop toma como
     * exito cualquier respuesta sin errors: borrar un cargo en uso mostraba "eliminado con
     * exito" sin haber borrado nada. Chequeamos antes y explicamos por que no se puede.
     * Mismo patron que ProveedorServicioGraphQL.deleteProveedorServicio.
     */
    public Boolean deleteCargo(Long id) throws GraphQLException {
        Long funcionarios = funcionarioRepository.countByCargoId(id);
        if (funcionarios != null && funcionarios > 0) {
            throw new GraphQLException(
                    "No se puede eliminar: hay " + funcionarios + " funcionario(s) con este cargo");
        }
        Long historico = funcionarioCargoHistoricoRepository.countByCargoId(id);
        if (historico != null && historico > 0) {
            throw new GraphQLException(
                    "No se puede eliminar: el cargo aparece en " + historico + " registro(s) del historico de cargos");
        }
        Long subcargos = service.countBySupervisadoPorId(id);
        if (subcargos != null && subcargos > 0) {
            throw new GraphQLException(
                    "No se puede eliminar: hay " + subcargos + " cargo(s) que dependen de este");
        }
        // financiero.tipo_gasto.cargo_id es el cargo autorizante del tipo de gasto, y su FK
        // es ON DELETE SET NULL: sin este chequeo el borrado NO falla, simplemente deja al
        // tipo de gasto sin autorizante y nadie se entera hasta que alguien intente
        // autorizar uno. Es peor que un error: es una baja silenciosa de una regla de
        // control. Ver V0__initial_schema.sql, constraint tipo_gasto_cargo_s\\fk.
        Long tiposGasto = tipoGastoRepository.countByCargoId(id);
        if (tiposGasto != null && tiposGasto > 0) {
            throw new GraphQLException(
                    "No se puede eliminar: el cargo autoriza " + tiposGasto + " tipo(s) de gasto");
        }
        return service.deleteById(id);
    }

    public Long countCargo(){
        return service.count();
    }


}
