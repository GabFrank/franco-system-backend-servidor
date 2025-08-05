package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.NotaRecepcionItem;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.graphql.operaciones.input.NotaRecepcionInput;
import com.franco.dev.graphql.operaciones.input.NotaRecepcionItemInput;
import com.franco.dev.service.financiero.DocumentoService;
import com.franco.dev.service.operaciones.*;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import graphql.GraphQLException;

// Enum para filtro de verificación
enum FiltroVerificacion {
    TODOS,
    PENDIENTES,
    VERIFICADOS,
    RECHAZADOS
}

@Component
public class NotaRecepcionItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private NotaRecepcionItemService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private NotaRecepcionService notaRecepcionService;
    @Autowired
    private PedidoItemService pedidoItemService;
    @Autowired
    private PedidoService pedidoService;
    
    @Autowired
    private PresentacionService presentacionService;

    public Optional<NotaRecepcionItem> notaRecepcionItem(Long id) {return service.findById(id);}

    public List<NotaRecepcionItem> notaRecepcionItemList(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<NotaRecepcionItem> notaRecepcionItemListPorNotaRecepcionId(Long id){
        return service.findByNotaRecepcionId(id);
    }

    public Page<NotaRecepcionItem> notaRecepcionItemListPorNotaRecepcionIdYSucursales(
            Long notaRecepcionId, List<Long> sucursalesIds, Integer page, Integer size, FiltroVerificacion filtroVerificacion, String filtroTexto) {
        
        if (notaRecepcionId == null) {
            throw new GraphQLException("ID de nota de recepción es requerido");
        }
        
        if (sucursalesIds == null || sucursalesIds.isEmpty()) {
            throw new GraphQLException("Lista de IDs de sucursales es requerida");
        }
        
        if (page == null) page = 0;
        if (size == null) size = 10;
        if (filtroVerificacion == null) filtroVerificacion = FiltroVerificacion.TODOS;
        
        Pageable pageable = PageRequest.of(page, size);
        
        switch (filtroVerificacion) {
            case PENDIENTES:
                return service.findByNotaRecepcionIdAndSucursalesIdsAndNoVerificados(
                    notaRecepcionId, sucursalesIds, filtroTexto, pageable);
            case VERIFICADOS:
                return service.findByNotaRecepcionIdAndSucursalesIdsAndVerificados(
                    notaRecepcionId, sucursalesIds, filtroTexto, pageable);
            case RECHAZADOS:
                return service.findByNotaRecepcionIdAndSucursalesIdsAndRechazados(
                    notaRecepcionId, sucursalesIds, filtroTexto, pageable);
            case TODOS:
            default:
                return service.findByNotaRecepcionIdAndSucursalesIds(
                    notaRecepcionId, sucursalesIds, filtroTexto, pageable);
        }
    }

    public NotaRecepcionItem saveNotaRecepcionItem(NotaRecepcionItemInput input){
        ModelMapper m = new ModelMapper();
        NotaRecepcionItem e = m.map(input, NotaRecepcionItem.class);
        if(input.getNotaRecepcionId()!=null) e.setNotaRecepcion(notaRecepcionService.findById(input.getNotaRecepcionId()).orElse(null));
        if(input.getPedidoItemId()!=null) e.setPedidoItem(pedidoItemService.findById(input.getPedidoItemId()).orElse(null));
        if(input.getPresentacionEnNotaId()!=null) e.setPresentacionEnNota(presentacionService.findById(input.getPresentacionEnNotaId()).orElse(null));
        if(input.getUsuarioId()!=null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteNotaRecepcionItem(Long id){
        return service.deleteById(id);
    }

    public Long countNotaRecepcionItem(){
        return service.count();
    }

    public List<NotaRecepcionItem> adicionarItens(Long id, List<NotaRecepcionItemInput> inputList){
        List<NotaRecepcionItem> notaRecepcionItemList = new ArrayList<>();
        for(NotaRecepcionItemInput input: inputList){
            notaRecepcionItemList.add(saveNotaRecepcionItem(input));
        }
        return notaRecepcionItemList;
    }

    public Boolean removerItens(Long id, List<NotaRecepcionItemInput> inputList){
        Boolean res = false;
        List<NotaRecepcionItem> notaRecepcionItemList = new ArrayList<>();
        for(NotaRecepcionItemInput input: inputList){
            if(input.getId()!=null){
                res = deleteNotaRecepcionItem(input.getId());
            }
        }
        return res;
    }

    public List<NotaRecepcionItem> buscarSobrantes(Long id){
        return service.findByPedidoIdAndPedidoItemIsNull(id);
    }
}
