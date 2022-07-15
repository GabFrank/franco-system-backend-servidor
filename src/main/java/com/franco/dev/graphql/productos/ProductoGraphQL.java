package com.franco.dev.graphql.productos;

import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.productos.input.ProductoInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.security.Unsecured;
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

    @Unsecured
    public Optional<Producto> producto(Long id) {return service.findById(id);}

    public List<Producto> productoSearch(String texto, int offset,  Boolean isEnvase) {
        return service.findByAll(texto, offset, isEnvase);}

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
        return imageService.saveImageToPath(image, filename, true);
    }

    public Boolean productoDescripcionExists(String descripcion){
        Pageable pageable = PageRequest.of(1,5);
        return service.findByAll(descripcion, 0, false).isEmpty();
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
}
