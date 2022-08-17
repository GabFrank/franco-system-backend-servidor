package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.*;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.graphql.financiero.input.BancoInput;
import com.franco.dev.graphql.financiero.input.ConteoInput;
import com.franco.dev.graphql.financiero.input.ConteoMonedaInput;
import com.franco.dev.rabbit.dto.SaveConteoDto;
import com.franco.dev.service.financiero.*;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class ConteoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ConteoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService paisService;

    @Autowired
    private ConteoMonedaGraphQL conteoMonedaGraphQL;

    @Autowired
    private PdvCajaService pdvCajaService;

    @Autowired
    private MovimientoCajaService movimientoCajaService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private CambioService cambioService;

    @Autowired
    private PropagacionService propagacionService;

    private static final Logger log = LoggerFactory.getLogger(ConteoGraphQL.class);

    public Optional<Conteo> conteo(Long id) {return service.findById(id);}

    public List<Conteo> conteos(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }


    public Conteo saveConteo(ConteoInput input, List<ConteoMonedaInput> conteoMonedaInputList, Long cajaId, Boolean apertura, Long sucId){
        SaveConteoDto saveConteoDto = new SaveConteoDto();
        saveConteoDto.setConteoInput(input);
        saveConteoDto.setConteoMonedaInputList(conteoMonedaInputList);
        saveConteoDto.setApertura(apertura);
        saveConteoDto.setCajaId(cajaId);
        saveConteoDto.setSucId(sucId);
        return propagacionService.saveConteo(saveConteoDto);
    }

    public Boolean deleteConteo(Long id){
        return service.deleteById(id);
    }

    public Long countConteo(){
        return service.count();
    }


}
