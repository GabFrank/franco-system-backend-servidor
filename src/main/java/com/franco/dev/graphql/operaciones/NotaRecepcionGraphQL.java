package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.graphql.operaciones.input.NotaRecepcionInput;
import com.franco.dev.service.financiero.DocumentoService;
import com.franco.dev.service.operaciones.CompraService;
import com.franco.dev.service.operaciones.NotaRecepcionService;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.personas.UsuarioService;
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
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class NotaRecepcionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private NotaRecepcionService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private CompraService compraService;
    @Autowired
    private DocumentoService documentoService;
    @Autowired
    private PedidoService pedidoService;

    public Optional<NotaRecepcion> notaRecepcion(Long id) {
        return service.findById(id);
    }

    public List<NotaRecepcion> notaRecepcions(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<NotaRecepcion> notaRecepcionPorPedidoId(Long id) {
        return service.findByPedidoId(id);
    }

    public Page<NotaRecepcion> notaRecepcionPorPedidoIdAndNumero(Long id, Integer numero, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        if(numero!=null){
            String texto = "%"+numero+"%";
            return service.findByPedidoIdAndNumero(id, texto, pageable);
        } else {
            return service.findByPedidoId(id, pageable);
        }
    }

    public NotaRecepcion saveNotaRecepcion(NotaRecepcionInput input) {
        ModelMapper m = new ModelMapper();
        NotaRecepcion e = m.map(input, NotaRecepcion.class);
        if (input.getCompraId() != null) e.setCompra(compraService.findById(input.getCompraId()).orElse(null));
        if (input.getDocumentoId() != null)
            e.setDocumento(documentoService.findById(input.getDocumentoId()).orElse(null));
        if (input.getPedidoId() != null) e.setPedido(pedidoService.findById(input.getPedidoId()).orElse(null));
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getFecha() != null) e.setFecha(stringToDate(input.getFecha()));
        if (input.getCreadoEn() != null) e.setCreadoEn(stringToDate(input.getCreadoEn()));
        return service.save(e);

    }

    public Boolean deleteNotaRecepcion(Long id) {
        return service.deleteById(id);
    }

    public Long countNotaRecepcion() {
        return service.count();
    }

    public Integer countNotaRecepcionPorPedidoId(Long id){
        return service.getRepository().countByPedidoId(id);
    }
}
