package com.franco.dev.graphql.financiero;

import java.util.List;

import javax.transaction.Transactional;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.graphql.financiero.input.DocumentoElectronicoInput;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.personas.UsuarioService;

import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;

@Component
public class DocumentoElectronicoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

  @Autowired
  private DocumentoElectronicoService service;

  @Autowired
  private UsuarioService usuarioService;

  public List<DocumentoElectronico> documentoElectronicos(int page, int size) {
    Pageable pageable = PageRequest.of(page, size);
    return service.findAll(pageable);
  }
  
  @Transactional
  public DocumentoElectronico saveDocumentoElectronico(DocumentoElectronicoInput input) {
    ModelMapper m = new ModelMapper();
    DocumentoElectronico e = m.map(input, DocumentoElectronico.class);
    if (input.getUsuarioId() != null) {
      e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
    }
    return service.save(e);
  }
}
