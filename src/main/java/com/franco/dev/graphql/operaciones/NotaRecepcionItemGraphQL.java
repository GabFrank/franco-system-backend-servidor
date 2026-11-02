package com.franco.dev.graphql.operaciones;

import com.franco.dev.service.operaciones.compras.ComprasSecurityService;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.NotaRecepcionItem;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.graphql.operaciones.dto.ResumenItemsNotaDTO;
import com.franco.dev.graphql.operaciones.dto.ResumenItemsNotaCompletoDTO;
import com.franco.dev.graphql.operaciones.dto.ItemPorEstadoDTO;
import com.franco.dev.graphql.operaciones.dto.ResumenPorNotaDTO;
import com.franco.dev.graphql.operaciones.input.NotaRecepcionInput;
import com.franco.dev.graphql.operaciones.input.NotaRecepcionItemInput;
import com.franco.dev.service.financiero.DocumentoService;
import com.franco.dev.service.operaciones.*;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private ComprasSecurityService seg;

    @Autowired
    private NotaRecepcionItemService service;

    @Autowired
    private RecepcionDocumentalService recepcionDocumentalService;

    @Autowired
    private NotaRecepcionService notaRecepcionService;

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
        seg.exigirCompras();
        return recepcionDocumentalService.guardarItem(input);
    }

    public Boolean deleteNotaRecepcionItem(Long id){
        seg.exigirCompras();
        Optional<NotaRecepcionItem> itemOpt = service.findById(id);
        Long notaRecepcionId = itemOpt.map(NotaRecepcionItem::getNotaRecepcion).map(NotaRecepcion::getId).orElse(null);
        boolean deleted = service.deleteById(id);
        if (deleted && notaRecepcionId != null) {
            notaRecepcionService.actualizarValorNota(notaRecepcionId);
        }
        return deleted;
    }

    public Long countNotaRecepcionItem(){
        return service.count();
    }

    public List<NotaRecepcionItem> adicionarItens(Long id, List<NotaRecepcionItemInput> inputList){
        seg.exigirAdmin(); // sin pantalla que la llame
        List<NotaRecepcionItem> notaRecepcionItemList = new ArrayList<>();
        for(NotaRecepcionItemInput input: inputList){
            notaRecepcionItemList.add(saveNotaRecepcionItem(input));
        }
        if (id != null) {
            notaRecepcionService.actualizarValorNota(id);
        }
        return notaRecepcionItemList;
    }

    public Boolean removerItens(Long id, List<NotaRecepcionItemInput> inputList){
        seg.exigirAdmin(); // sin pantalla que la llame
        Boolean res = false;
        for(NotaRecepcionItemInput input: inputList){
            if(input.getId()!=null){
                res = deleteNotaRecepcionItem(input.getId());
            }
        }
        if (id != null) {
            notaRecepcionService.actualizarValorNota(id);
        }
        return res;
    }

    public List<NotaRecepcionItem> buscarSobrantes(Long id){
        return service.findByPedidoIdAndPedidoItemIsNull(id);
    }

    /**
     * Obtiene items de una nota de recepción de forma paginada
     */
    public Page<NotaRecepcionItem> itemsNotaPaginados(Long notaId, Integer page, Integer size, String sort, String direction) {
        if (notaId == null) {
            throw new GraphQLException("ID de nota de recepción es requerido");
        }
        
        if (page == null) page = 0;
        if (size == null) size = 20;
        
        Pageable pageable = PageRequest.of(page, size);
        
        // TODO: Implementar ordenamiento personalizado cuando se necesite
        // Por ahora usamos el ordenamiento por defecto del repositorio
        
        return service.findItemsByNotaIdPaginados(notaId, pageable);
    }

    /**
     * Obtiene resumen de items para múltiples notas
     */
    public ResumenItemsNotaCompletoDTO resumenItemsNota(List<Long> notaIds) {
        if (notaIds == null || notaIds.isEmpty()) {
            throw new GraphQLException("Lista de IDs de notas es requerida");
        }
        
        // Obtener resumen general
        Object[] resumenGeneral = service.getResumenGeneralItems(notaIds);
        Long totalItems = 0L;
        Double totalCantidad = 0.0;
        
        if (resumenGeneral != null && resumenGeneral.length >= 2) {
            totalItems = (Long) resumenGeneral[0];
            totalCantidad = (Double) resumenGeneral[1];
        }
        
        // Obtener resumen por nota
        List<ResumenItemsNotaDTO> resumenPorNota = service.getResumenItemsPorNota(notaIds);
        
        // Convertir ResumenItemsNotaDTO a ResumenPorNotaDTO
        List<ResumenPorNotaDTO> resumenPorNotaConvertido = resumenPorNota.stream()
            .map(dto -> new ResumenPorNotaDTO(
                dto.getNotaId(),
                dto.getTotalItems(),
                dto.getTotalCantidad(),
                dto.getEstado()
            ))
            .collect(java.util.stream.Collectors.toList());
        
        // Crear items por estado basado en el resumen por nota
        Map<String, ItemPorEstadoDTO> estadoMap = new HashMap<>();
        for (ResumenItemsNotaDTO dto : resumenPorNota) {
            String estado = dto.getEstado();
            if (!estadoMap.containsKey(estado)) {
                estadoMap.put(estado, new ItemPorEstadoDTO(estado, 0L, 0.0));
            }
            ItemPorEstadoDTO itemEstado = estadoMap.get(estado);
            itemEstado.setCount(itemEstado.getCount() + dto.getTotalItems());
            itemEstado.setCantidad(itemEstado.getCantidad() + dto.getTotalCantidad());
        }
        
        List<ItemPorEstadoDTO> itemsPorEstado = new ArrayList<>(estadoMap.values());
        
        return new ResumenItemsNotaCompletoDTO(
            totalItems,
            totalCantidad,
            itemsPorEstado,
            resumenPorNotaConvertido
        );
    }
}
