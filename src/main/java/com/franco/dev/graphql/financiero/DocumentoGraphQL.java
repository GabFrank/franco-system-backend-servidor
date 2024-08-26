package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.Documento;
import com.franco.dev.graphql.financiero.input.BancoInput;
import com.franco.dev.graphql.financiero.input.DocumentoInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.financiero.BancoService;
import com.franco.dev.service.financiero.DocumentoService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
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
public class DocumentoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private DocumentoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PropagacionService propagacionService;

    public Optional<Documento> documento(Long id) {return service.findById(id);}

    public List<Documento> documentos(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }


    public Documento saveDocumento(DocumentoInput input){
        ModelMapper m = new ModelMapper();
        Documento e = m.map(input, Documento.class);
        if(input.getUsuarioId()!=null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        e = service.save(e);
//        propagacionService.propagarEntidad(e, TipoEntidad.DOCUMENTO);
        return e;    }

    public Boolean deleteDocumento(Long id){

        Boolean ok = service.deleteById(id);
//        if(ok) propagacionService.eliminarEntidad(id, TipoEntidad.DOCUMENTO);
        return ok;    }

    public Long countDocumento(){
        return service.count();
    }


}
