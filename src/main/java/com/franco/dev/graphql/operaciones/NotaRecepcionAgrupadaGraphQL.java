package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.NotaRecepcionAgrupada;
import com.franco.dev.domain.operaciones.enums.PedidoEstado;
import com.franco.dev.graphql.operaciones.input.NotaRecepcionAgrupadaInput;
import com.franco.dev.graphql.operaciones.input.NotaRecepcionInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.DocumentoService;
import com.franco.dev.service.operaciones.CompraService;
import com.franco.dev.service.operaciones.NotaRecepcionAgrupadaService;
import com.franco.dev.service.operaciones.NotaRecepcionService;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.personas.ProveedorService;
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

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class NotaRecepcionAgrupadaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private NotaRecepcionAgrupadaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProveedorService proveedorService;

    @Autowired
    private SucursalService sucursalService;

    public Page<NotaRecepcionAgrupada> notaRecepcionListPorUsuarioId(Long id, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.getRepository().findByUsuarioIdOrderByIdDesc(id, pageable);
    }

    public Page<NotaRecepcionAgrupada> notaRecepcionListPorProveedorId(Long id, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.getRepository().findByProveedorId(id, pageable);
    }

    public NotaRecepcionAgrupada saveNotaRecepcionAgrupada(NotaRecepcionAgrupadaInput input) {
        ModelMapper m = new ModelMapper();
        NotaRecepcionAgrupada e = m.map(input, NotaRecepcionAgrupada.class);
        if (input.getProveedorId() != null)
            e.setProveedor(proveedorService.findById(input.getProveedorId()).orElse(null));
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getSucursalId() != null) e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        if (input.getCreadoEn() != null) e.setCreadoEn(stringToDate(input.getCreadoEn()));
        return service.save(e);
    }
}
