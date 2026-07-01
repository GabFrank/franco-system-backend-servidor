package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.ProductoProveedor;
import com.franco.dev.domain.productos.dto.BuscadorProductoFiltros;
import com.franco.dev.domain.productos.dto.BuscadorProductoResultado;
import com.franco.dev.repository.operaciones.PedidoItemRepository;
import com.franco.dev.repository.productos.ProductoProveedorRepository;
import com.franco.dev.service.productos.search.BuscadorProductoInteligenteService;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@AllArgsConstructor
public class ProductoProveedorService extends CrudService<ProductoProveedor, ProductoProveedorRepository, Long> {

    @Autowired
    private final ProductoProveedorRepository repository;
    
    @Autowired
    private final PedidoItemRepository pedidoItemRepository;

    @Autowired
    private final BuscadorProductoInteligenteService buscadorProductoInteligenteService;
//    private final PersonaPublisher personaPublisher;

    public Page<ProductoProveedor> findByProveedorId(Long id, String texto, Pageable pageable) {
        return findByProveedorId(id, texto, null, pageable);
    }

    public Page<ProductoProveedor> findByProveedorId(Long id, String texto, Long pedidoId, Pageable pageable) {
        Page<ProductoProveedor> result;
        
        if(texto!=null){
            texto = "%"+texto.toUpperCase()+"%";
            result = repository.findByProveedorIdAndProductoDescripcionLikeIgnoreCase(id,texto, pageable);
        } else {
            result = repository.findByProveedorIdOrderByProductoDescripcionAsc(id, pageable);
        }
        
        // Si se proporciona pedidoId, calcular yaEnPedido para cada producto
        if (pedidoId != null) {
            // Obtener lista de productoIds que ya están en el pedido
            List<Long> productoIdsEnPedido = pedidoItemRepository.findProductoIdsByPedidoId(pedidoId);
            Set<Long> productoIdsSet = new HashSet<>(productoIdsEnPedido);
            
            // Marcar cada ProductoProveedor con yaEnPedido
            result.getContent().forEach(pp -> {
                if (pp.getProducto() != null && pp.getProducto().getId() != null) {
                    pp.setYaEnPedido(productoIdsSet.contains(pp.getProducto().getId()));
                } else {
                    pp.setYaEnPedido(false);
                }
            });
        } else {
            // Si no hay pedidoId, marcar todos como false
            result.getContent().forEach(pp -> pp.setYaEnPedido(false));
        }
        
        return result;
    }

    public Page<ProductoProveedor> findByProveedorIdInteligente(
            Long id,
            String texto,
            Long pedidoId,
            Pageable pageable) {
        if (texto == null || texto.trim().isEmpty()) {
            return findByProveedorId(id, null, pedidoId, pageable);
        }

        BuscadorProductoFiltros filtros = BuscadorProductoFiltros.builder()
                .texto(texto)
                .proveedorId(id)
                .activo(true)
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .build();

        Page<BuscadorProductoResultado> busqueda = buscadorProductoInteligenteService.buscar(filtros);
        List<ProductoProveedor> contenido = new ArrayList<>();

        for (BuscadorProductoResultado resultado : busqueda.getContent()) {
            if (resultado.getProducto() == null || resultado.getProducto().getId() == null) {
                continue;
            }
            ProductoProveedor pp = repository.findByProductoIdAndProveedorId(
                    resultado.getProducto().getId(), id);
            if (pp != null && (pp.getActivo() == null || Boolean.TRUE.equals(pp.getActivo()))) {
                pp.setProducto(resultado.getProducto());
                contenido.add(pp);
            }
        }

        if (pedidoId != null) {
            List<Long> productoIdsEnPedido = pedidoItemRepository.findProductoIdsByPedidoId(pedidoId);
            Set<Long> productoIdsSet = new HashSet<>(productoIdsEnPedido);
            contenido.forEach(pp -> {
                if (pp.getProducto() != null && pp.getProducto().getId() != null) {
                    pp.setYaEnPedido(productoIdsSet.contains(pp.getProducto().getId()));
                } else {
                    pp.setYaEnPedido(false);
                }
            });
        } else {
            contenido.forEach(pp -> pp.setYaEnPedido(false));
        }

        return new org.springframework.data.domain.PageImpl<>(
                contenido,
                pageable,
                busqueda.getTotalElements());
    }

    public Page<ProductoProveedor> findByProductoId(Long id, Pageable pageable) {
        return repository.findByProductoIdAndActivoTrueOrderByProveedorPersonaNombre(id, pageable);
    }

    @Override
    public ProductoProveedorRepository getRepository() {
        return repository;
    }

}
