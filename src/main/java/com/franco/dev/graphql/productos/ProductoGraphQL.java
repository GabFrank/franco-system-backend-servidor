package com.franco.dev.graphql.productos;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.dto.LucroPorProductosDto;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.productos.input.ProductoInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.security.Unsecured;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.*;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.service.utils.PrintingService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

@Component
public class ProductoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final Logger log = Logger.getLogger(String.valueOf(ProductoService.class));

    @Autowired
    private ProductoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SubFamiliaService subFamiliaService;

    @Autowired
    private IngredienteService ingredienteService;

    @Autowired
    private MovimientoStockService movimientoStockService;

    @Autowired
    private ImageService imageService;

    @Autowired
    private Environment env;

    @Autowired
    private PrintingService printingService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private PresentacionService presentacionService;

    @Autowired
    private ImpresionService impresionService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private CodigoService codigoService;

    @Unsecured
    public Optional<Producto> producto(Long id) {return service.findById(id);}

    public List<Producto> productoSearch(String texto, int offset,  Boolean isEnvase, Boolean activo) {
        return service.findByAll(texto, offset, isEnvase, activo);}

    public List<Producto> productos(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAllForPdv();
    }

    public Producto saveProducto(ProductoInput input){
        Producto p = service.save(input);
        propagacionService.propagarEntidad(p, TipoEntidad.PRODUCTO);
        return p;
    }

    public Producto updateProducto(Long id, ProductoInput input){
        ModelMapper m = new ModelMapper();
        Producto p = service.getOne(id);
        p = m.map(input, Producto.class);
        return service.save(p);
    }

    public Float productoPorSucursalStock(Long proId, Long sucId){
        return propagacionService.solicitarStockByProducto(proId, sucId);
    }

    public Boolean deleteProducto(Long id){
        Boolean ok = service.deleteById(id);
        if(ok) propagacionService.eliminarEntidad(id, TipoEntidad.PRODUCTO);
        return ok;
    }

    public Long countProducto(){
        return service.count();
    }

    public List<Producto> productoPorProveedorId(Long id, String texto){
        return service.findByProveedorId(id, texto);
    }

    public Producto productoPorCodigo(String texto) { return service.findByCodigo(texto);};

    public Boolean saveImagenProducto(String image, String filename) throws IOException {
        return false;
    }

    public Boolean productoDescripcionExists(String descripcion){
        Pageable pageable = PageRequest.of(1,5);
        return service.findByAll(descripcion, 0, false, false).isEmpty();
    }

    public Producto printProducto(Long id) {
        propagacionService.verficarConexion((long) 1);
        return null;
    }

    public String exportarReporte(String texto) throws FileNotFoundException {
        return service.exportarReporte(texto);
    }

    public List<Producto> findByPdvGrupoProducto(Long id){
        return service.findByGrupoProductoId(id);
    }

    public String lucroPorProducto(String fechaInicio, String fechaFin, List<Long> sucursalIdList, Long usuarioId, List<Long> usuarioIdList, List<Long> productoIdList){
        Usuario usuario = usuarioService.findById(usuarioId).orElse(null);
        StringBuilder filtro = new StringBuilder();
        if(usuario!=null){
            filtro.append("Cajero: ");
            filtro.append(usuario.getId() + " - " + usuario.getNickname());
        }
        if(usuario!=null && sucursalIdList!=null && sucursalIdList.size() > 0){
            filtro.append(" - ");
        }
        if(sucursalIdList!=null && sucursalIdList.size() > 0){
            filtro.append("Sucursales: ");
        }
        for(Long sucId: sucursalIdList){
            Sucursal suc = sucursalService.findById(sucId).orElse(null);
            if(suc!=null) filtro.append(suc.getNombre() + " , ");
        }
        List<LucroPorProductosDto> lucroPorProductosDtoList = service.findLucroPorProductos(fechaInicio, fechaFin, sucursalIdList, usuarioIdList, productoIdList);
        return impresionService.imprimirReporteLucroPorProducto(lucroPorProductosDtoList, fechaInicio, fechaFin, "", filtro.toString(), usuario);
    }

    public Boolean imprimirCodigoBarra(Long codigoId){
        Codigo codigo = codigoService.findById(codigoId).orElse(null);
        if(codigo!=null){
            impresionService.imprimirCodigoDeBarra(codigo);
            return true;
        } else {
            return false;
        }
    }
}
