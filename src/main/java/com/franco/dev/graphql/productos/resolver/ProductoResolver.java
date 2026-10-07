package com.franco.dev.graphql.productos.resolver;

import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.*;
import com.franco.dev.domain.productos.enums.TipoConservacion;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.operaciones.NotaRecepcionItemService;
import com.franco.dev.service.operaciones.PedidoItemService;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.*;
import com.franco.dev.service.utils.ImageService;
import graphql.kickstart.tools.GraphQLResolver;
import kotlin.collections.ArrayDeque;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@Component
public class ProductoResolver implements GraphQLResolver<Producto> {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SubFamiliaService subFamiliaService;

    @Autowired
    private IngredienteService ingredienteService;

    @Autowired
    private ProductoIngredienteService productoIngredienteService;

    @Autowired
    private CostosPorProductoService costosPorProductoService;

    @Autowired
    private CodigoService codigoService;

    @Autowired
    private MovimientoStockService movimientoStockService;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private PedidoItemService pedidoItemService;

    @Autowired
    private NotaRecepcionItemService notaRecepcionItemService;

    @Autowired
    private ProductoImagenService productoImagenService;

    @Autowired
    private ImageService imageService;

    @Autowired
    private FotoProductoService fotoProductoService;

    @Autowired
    private PresentacionService presentacionService;

    @Autowired
    private PresentacionResolver presentacionResolver;

    public Usuario usuario(Producto e){
        if(e.getUsuario()!=null) {
            return usuarioService.findById(e.getUsuario().getId()).orElse(null);
        } else {
            return null;
        }
    }

    public TipoConservacion tipoConservacion(Producto e){ return e.getTipoConservacion(); }

    public List<ProductoIngrediente> ingredientesList(Producto p){
        List<Ingrediente> ingredienteList = new ArrayDeque<>();
        List<ProductoIngrediente> productoIngredienteList = productoIngredienteService.findByProducto(p.getId());
        for(ProductoIngrediente pi : productoIngredienteList){
            ingredienteList.add(ingredienteService.findById(pi.getIngrediente().getId()).orElse(null));
        }
        return productoIngredienteList;
    }

    public Double existenciaTotal(Producto p){
        return movimientoStockService.stockByProductoId(p.getId());
    }

    public List<ProductoCompra> productoUltimasCompras(Producto p){
        List<ProductoCompra> pcList = new ArrayList<>();
        
        // Buscar directamente en NotaRecepcionItem, ordenado por fecha de creación descendente
        Pageable pageable = PageRequest.of(0, 5);
        Page<com.franco.dev.domain.operaciones.NotaRecepcionItem> notaItemsPage = notaRecepcionItemService.findUltimasComprasByProductoId(p.getId(), pageable);
        List<com.franco.dev.domain.operaciones.NotaRecepcionItem> notaItems = notaItemsPage.getContent();
        
        for (com.franco.dev.domain.operaciones.NotaRecepcionItem notaItem : notaItems){
            ProductoCompra pc = new ProductoCompra();
            
            // Obtener cantidad desde NotaRecepcionItem
            pc.setCantidad(notaItem.getCantidadEnNota() != null ? notaItem.getCantidadEnNota() : 0.0);
            
            // Obtener precio desde NotaRecepcionItem
            pc.setPrecio(notaItem.getPrecioUnitarioEnNota() != null ? notaItem.getPrecioUnitarioEnNota() : 0.0);
            
            // Obtener fecha de creación
            pc.setCreadoEn(notaItem.getCreadoEn() != null ? notaItem.getCreadoEn() : LocalDateTime.now());
            
            // Obtener pedido desde NotaRecepcion -> Pedido
            Pedido pedido = null;
            if(notaItem.getNotaRecepcion() != null && notaItem.getNotaRecepcion().getPedido() != null) {
                pedido = pedidoService.findById(notaItem.getNotaRecepcion().getPedido().getId()).orElse(null);
            }
            pc.setPedido(pedido);
            
            // Obtener presentación en nota
            pc.setPresentacionEnNota(notaItem.getPresentacionEnNota());

            // Moneda y cotización de la nota
            if (notaItem.getNotaRecepcion() != null) {
                pc.setMoneda(notaItem.getNotaRecepcion().getMoneda());
                pc.setCotizacion(notaItem.getNotaRecepcion().getCotizacion());
            }

            pcList.add(pc);
        }
        return pcList;
    }

    public List<Presentacion> presentaciones(Producto p){
        return presentacionService.findByProductoId(p.getId());
    }

    /** El original. Las listas piden {@code imagenPrincipalMiniatura}; las vistas grandes, la mediana. */
    public String imagenPrincipal(Producto p) {
        return fotoProductoService.deProducto(p.getId(), FotoProductoService.Tamano.ORIGINAL);
    }

    public String imagenPrincipalMiniatura(Producto p) {
        return fotoProductoService.deProducto(p.getId(), FotoProductoService.Tamano.MINIATURA);
    }

    public String imagenPrincipalMediana(Producto p) {
        return fotoProductoService.deProducto(p.getId(), FotoProductoService.Tamano.MEDIANA);
    }

    public String codigoPrincipal(Producto p){
        Presentacion presentacion = presentacionService.findByPrincipalAndProductoId(true, p.getId());
        if(presentacion!=null){
            if(presentacionResolver.codigoPrincipal(presentacion)!=null){
                return presentacionResolver.codigoPrincipal(presentacion).getCodigo();
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public String precioPrincipal(Producto p){
        Presentacion presentacion = presentacionService.findByPrincipalAndProductoId(true, p.getId());
        if(presentacion!=null){
            PrecioPorSucursal precio = presentacionResolver.precioPrincipal(presentacion);
            return precio != null ? precio.getPrecio().toString() : null;
        } else {
            return null;
        }
    }

    public CostoPorProducto costo(Producto p){
        return costosPorProductoService.findLastByProductoId(p.getId());
    }

}
