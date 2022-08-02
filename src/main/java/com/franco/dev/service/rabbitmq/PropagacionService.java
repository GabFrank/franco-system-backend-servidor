package com.franco.dev.service.rabbitmq;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Inventario;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.enums.EtapaTransferencia;
import com.franco.dev.rabbit.RabbitMQConection;
import com.franco.dev.rabbit.dto.RabbitDto;
import com.franco.dev.rabbit.enums.TipoAccion;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.rabbit.sender.Sender;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.empresarial.CargoService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.*;
import com.franco.dev.service.general.BarrioService;
import com.franco.dev.service.general.CiudadService;
import com.franco.dev.service.general.ContactoService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.operaciones.*;
import com.franco.dev.service.personas.*;
import com.franco.dev.service.productos.*;
import com.franco.dev.service.productos.pdv.PdvCategoriaService;
import com.franco.dev.service.productos.pdv.PdvGrupoService;
import com.franco.dev.service.productos.pdv.PdvGruposProductosService;
import com.franco.dev.service.utils.ImageService;
import com.rabbitmq.client.Channel;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zeroturnaround.zip.ZipUtil;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class PropagacionService {

    Long sucursalVerificar = null;
    private final Logger log = LoggerFactory.getLogger(PropagacionService.class);
    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private Sender sender;

    @Autowired
    private PaisService paisService;

    @Autowired
    private CiudadService ciudadService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private CargoService cargoService;

    @Autowired
    private FamiliaService familiaService;

    @Autowired
    private SubFamiliaService subFamiliaService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private TipoPresentacionService tipoPresentacionService;

    @Autowired
    private PresentacionService presentacionService;

    @Autowired
    private PdvCategoriaService pdvCategoriaService;

    @Autowired
    private PdvGrupoService pdvGrupoService;

    @Autowired
    private PdvGruposProductosService pdvGruposProductosService;

    @Autowired
    private TipoPrecioService tipoPrecioService;

    @Autowired
    private PrecioPorSucursalService precioPorSucursalService;

    @Autowired
    private BancoService bancoService;

    @Autowired
    private CuentaBancariaService cuentaBancariaService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private MonedaBilleteService monedaBilleteService;

    @Autowired
    private FormaPagoService formaPagoService;

    @Autowired
    private DocumentoService documentoService;

    @Autowired
    private MaletinService maletinService;

    @Autowired
    private TipoGastoService tipoGastoService;

    @Autowired
    private CodigoService codigoService;

    @Autowired
    private CambioService cambioService;

    @Autowired
    private BarrioService barrioService;

    @Autowired
    private ContactoService contactoService;

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private ProveedorService proveedorService;

    @Autowired
    private VendedorService vendedorService;

    @Autowired
    private VendedorProveedorService vendedorProveedorService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private TransferenciaService transferenciaService;

    @Autowired
    private TransferenciaItemService transferenciaItemService;

    @Autowired
    private UsuarioRoleService usuarioRoleService;

    @Autowired
    private InventarioService inventarioService;

    @Autowired
    private InventarioProductoService inventarioProductoService;

    @Autowired
    private InventarioProductoItemService inventarioProductoItemService;

    @Autowired
    private ImageService imageService;

    public void verficarConexion(Long sucId) {
        sucursalVerificar = sucId;
        RabbitDto dato = new RabbitDto();
        dato.setTipoAccion(TipoAccion.VERIFICAR);
        sender.enviar(RabbitMQConection.FILIAL_KEY + "." + sucId, dato);
    }

    public void propagarDB(RabbitDto dto) {
        Long sucId = dto.getIdSucursalOrigen();
        Sucursal sucursal = sucursalService.findById(sucId).orElse(null);
        switch (dto.getTipoEntidad()) {
            case USUARIO:
                log.info("Propagando usuarios: ");
                propagar(TipoEntidad.USUARIO, sucId, usuarioService);
                break;
            case PAIS:
                log.info("Propagando paises: ");
                propagar(TipoEntidad.PAIS, sucId, paisService);
                break;
            case CIUDAD:
                log.info("Propagando ciudades: ");
                propagar(TipoEntidad.CIUDAD, sucId, ciudadService);
                break;
            case PERSONA:
                log.info("Propagando personas: ");
                propagar(TipoEntidad.PERSONA, sucId, personaService);
                break;
            case USUARIO_UPDATE:
                log.info("Propagando update de usuario: ");
                propagar(TipoEntidad.USUARIO_UPDATE, sucId, usuarioService);
                break;
            case FAMILIA:
                log.info("Propagando familia: ");
                propagar(TipoEntidad.FAMILIA, sucId, familiaService);
                break;
            case SUBFAMILIA:
                log.info("Propagando subfamilia: ");
                propagar(TipoEntidad.SUBFAMILIA, sucId, subFamiliaService);
                break;
            case PRODUCTO:
                log.info("Propagando producto: ");
                propagar(TipoEntidad.PRODUCTO, sucId, productoService);
                break;
            case TIPO_PRESENTACION:
                log.info("Propagando tipo presentacion: ");
                propagar(TipoEntidad.TIPO_PRESENTACION, sucId, tipoPresentacionService);
                break;
            case PRESENTACION:
                log.info("Propagando presentacion: ");
                propagar(TipoEntidad.PRESENTACION, sucId, presentacionService);
                break;
            case PDV_CATEGORIA:
                log.info("Propagando pdv categria: ");
                propagar(TipoEntidad.PDV_CATEGORIA, sucId, pdvCategoriaService);
                break;
            case PDV_GRUPO:
                log.info("Propagando pdv grupo: ");
                propagar(TipoEntidad.PDV_GRUPO, sucId, pdvGrupoService);
                break;
            case PDV_GRUPO_PRODUCTO:
                log.info("Propagando pdv grupo producto: ");
                propagar(TipoEntidad.PDV_GRUPO_PRODUCTO, sucId, pdvGruposProductosService);
                break;
            case SUCURSAL:
                log.info("Propagando sucursal: ");
                propagar(TipoEntidad.SUCURSAL, sucId, sucursalService);
                break;
            case TIPO_PRECIO:
                log.info("Propagando tipo precio: ");
                propagar(TipoEntidad.TIPO_PRECIO, sucId, tipoPrecioService);
                break;
            case PRECIO_POR_SUCURSAL:
                log.info("Propagando precio por sucursal: ");
                propagar(TipoEntidad.PRECIO_POR_SUCURSAL, sucId, precioPorSucursalService);
                break;
            case BANCO:
                log.info("Propagando banco: ");
                propagar(TipoEntidad.BANCO, sucId, bancoService);
                break;
            case CUENTA_BANCARIA:
                log.info("Propagando cuenta bancaria: ");
                propagar(TipoEntidad.CUENTA_BANCARIA, sucId, cuentaBancariaService);
                break;
            case MONEDA:
                log.info("Propagando moneda: ");
                propagar(TipoEntidad.MONEDA, sucId, monedaService);
                break;
            case MONEDAS_BILLETES:
                log.info("Propagando monedas billetes: ");
                propagar(TipoEntidad.MONEDAS_BILLETES, sucId, monedaBilleteService);
                break;
            case FORMA_DE_PAGO:
                log.info("Propagando forma de pago: ");
                propagar(TipoEntidad.FORMA_DE_PAGO, sucId, formaPagoService);
                break;
            case DOCUMENTO:
                log.info("Propagando docuemento: ");
                propagar(TipoEntidad.DOCUMENTO, sucId, documentoService);
                break;
            case MALETIN:
                log.info("Propagando maletin: ");
                propagar(TipoEntidad.MALETIN, sucId, maletinService);
                break;
            case CARGO:
                log.info("Propagando cargo: ");
                propagar(TipoEntidad.CARGO, sucId, cargoService);
                break;
            case TIPO_GASTO:
                log.info("Propagando tipo gasto: ");
                propagar(TipoEntidad.TIPO_GASTO, sucId, tipoGastoService);
                break;
            case TIPO_GASTO_UPDATE:
                log.info("Propagando tipo gasto: ");
                propagar(TipoEntidad.TIPO_GASTO_UPDATE, sucId, tipoGastoService);
                break;
            case CODIGO:
                log.info("Propagando codigo: ");
                propagar(TipoEntidad.CODIGO, sucId, codigoService);
                break;
            case CAMBIO:
                log.info("Propagando cambio: ");
                propagar(TipoEntidad.CAMBIO, sucId, cambioService);
                break;
            case BARRIO:
                log.info("Propagando codigo: ");
                propagar(TipoEntidad.BARRIO, sucId, codigoService);
                break;
            case CONTACTO:
                log.info("Propagando contacto: ");
                propagar(TipoEntidad.CONTACTO, sucId, contactoService);
                break;
            case CLIENTE:
                log.info("Propagando cliente: ");
                propagar(TipoEntidad.CLIENTE, sucId, clienteService);
                break;
            case FUNCIONARIO:
                log.info("Propagando funncionario: ");
                propagar(TipoEntidad.FUNCIONARIO, sucId, funcionarioService);
                break;
            case PROVEEDOR:
                log.info("Propagando proveedor: ");
                propagar(TipoEntidad.PROVEEDOR, sucId, proveedorService);
                break;
            case VENDEDOR:
                log.info("Propagando vendedor: ");
                propagar(TipoEntidad.VENDEDOR, sucId, vendedorService);
                break;
            case ROLE:
                log.info("Propagando role: ");
                propagar(TipoEntidad.ROLE, sucId, roleService);
                break;
            case USUARIO_ROLE:
                log.info("Propagando usuario role: ");
                propagar(TipoEntidad.USUARIO_ROLE, sucId, usuarioRoleService);
                propagarArchivo(imageService.getImagePath(), "", imageService.getImagePath(), "images.zip", sucId);
                break;
//            case TRANSFERENCIA:
//                log.info("Propagando transferencia: ");
//                propagar(TipoEntidad.TRANSFERENCIA, sucId, transferenciaService);
//                break;
//            case TRANSFERENCIA_ITEM:
//                log.info("Propagando transferencia item: ");
//                propagar(TipoEntidad.TRANSFERENCIA_ITEM, sucId, transferenciaItemService);
//                break;
            default:
                break;
        }
    }

    public void crudEntidad(RabbitDto dto) {
        log.info("recibiendo entidad para guardar");
        switch (dto.getTipoEntidad()) {
            case USUARIO:
                log.info("cargando usuario: ");
                guardar(usuarioService, dto);
                break;
            case PAIS:
                log.info("cargando Pais: ");
                guardar(paisService, dto);
                break;
            case CIUDAD:
                log.info("cargando Ciudad: ");
                guardar(ciudadService, dto);
                break;
            case PERSONA:
                log.info("cargando Persona: ");
                guardar(personaService, dto);
                break;
            case FAMILIA:
                log.info("cargando Familia: ");
                guardar(familiaService, dto);
                break;
            case SUBFAMILIA:
                log.info("cargando subfamilia: ");
                guardar(subFamiliaService, dto);
                break;
            case PRODUCTO:
                log.info("cargando productos: ");
                guardar(productoService, dto);
                break;
            case TIPO_PRESENTACION:
                log.info("cargando tipo presentacion: ");
                guardar(tipoPresentacionService, dto);
                break;
            case PRESENTACION:
                log.info("cargando presentacion: ");
                guardar(presentacionService, dto);
                break;
            case PDV_CATEGORIA:
                log.info("cargando pdv categoria: ");
                guardar(pdvCategoriaService, dto);
                break;
            case PDV_GRUPO:
                log.info("cargando pdv grupo: ");
                guardar(pdvGrupoService, dto);
                break;
            case PDV_GRUPO_PRODUCTO:
                log.info("cargando pdv grupo producto: ");
                guardar(pdvGruposProductosService, dto);
                break;
            case SUCURSAL:
                log.info("cargando sucursal: ");
                guardar(sucursalService, dto);
                break;
            case TIPO_PRECIO:
                log.info("cargando tipo precio: ");
                guardar(tipoPrecioService, dto);
                break;
            case PRECIO_POR_SUCURSAL:
                log.info("cargando precio por sucursal: ");
                guardar(precioPorSucursalService, dto);
                break;
            case BANCO:
                log.info("cargando banco: ");
                guardar(bancoService, dto);
                break;
            case MONEDA:
                log.info("cargando moneda: ");
                guardar(monedaService, dto);
                break;
            case MONEDAS_BILLETES:
                log.info("cargando moneda billetes: ");
                guardar(monedaBilleteService, dto);
                break;
            case CUENTA_BANCARIA:
                log.info("cargando cuenta bancaria: ");
                guardar(cuentaBancariaService, dto);
                break;
            case FORMA_DE_PAGO:
                log.info("cargando forma pago: ");
                guardar(formaPagoService, dto);
                break;
            case DOCUMENTO:
                log.info("cargando docuemento: ");
                guardar(documentoService, dto);
                break;
            case MALETIN:
                log.info("cargando maletin: ");
                guardar(maletinService, dto);
                break;
            case CARGO:
                log.info("cargando cargo: ");
                guardar(cargoService, dto);
                break;
            case TIPO_GASTO:
                log.info("cargando tipo gasto: ");
                guardar(tipoGastoService, dto);
                break;
            case TIPO_GASTO_UPDATE:
                log.info("cargando tipo gasto: ");
                guardar(tipoGastoService, dto);
                break;
            case CODIGO:
                log.info("cargando codigo: ");
                guardar(codigoService, dto);
                break;
            case CAMBIO:
                log.info("cargando cambio: ");
                guardar(codigoService, dto);
                break;
            case BARRIO:
                log.info("cargando barrio: ");
                guardar(barrioService, dto);
                break;
            case CONTACTO:
                log.info("cargando contacto: ");
                guardar(contactoService, dto);
                break;
            case CLIENTE:
                log.info("cargando cliente: ");
                guardar(clienteService, dto);
                break;
            case FUNCIONARIO:
                log.info("cargando funncioario: ");
                guardar(funcionarioService, dto);
                break;
            case PROVEEDOR:
                log.info("cargando proveedor: ");
                guardar(proveedorService, dto);
                break;
            case VENDEDOR:
                log.info("cargando vendedor: ");
                guardar(vendedorService, dto);
                break;
            case ROLE:
                log.info("cargando role: ");
                guardar(roleService, dto);
                break;
            case USUARIO_ROLE:
                log.info("cargando usuario role: ");
                guardar(usuarioRoleService, dto);
                break;
            case TRANSFERENCIA:
                log.info("creando transferencia: ");
                guardar(transferenciaService, dto);
                Transferencia transferencia = (Transferencia) dto.getEntidad();
                if (transferencia != null && transferencia.getEtapa() == EtapaTransferencia.TRANSPORTE_EN_CAMINO) {
                    propagarEntidad(transferencia, TipoEntidad.TRANSFERENCIA, transferencia.getSucursalDestino().getId());
                    List<TransferenciaItem> transferenciaItemList = transferenciaItemService.findByTransferenciaItemId(transferencia.getId());
                    for (TransferenciaItem ti : transferenciaItemList) {
                        propagarEntidad(ti, TipoEntidad.TRANSFERENCIA_ITEM, transferencia.getSucursalDestino().getId());
                    }
                } else if (transferencia != null && transferencia.getEtapa() == EtapaTransferencia.RECEPCION_EN_VERIFICACION) {
                    propagarEntidad(transferencia, TipoEntidad.TRANSFERENCIA, transferencia.getSucursalOrigen().getId());
                } else if (transferencia != null && transferencia.getEtapa() == EtapaTransferencia.RECEPCION_CONCLUIDA) {
                    propagarEntidad(transferencia, TipoEntidad.TRANSFERENCIA, transferencia.getSucursalOrigen().getId());
                    List<TransferenciaItem> transferenciaItemList = transferenciaItemService.findByTransferenciaItemId(transferencia.getId());
                    for (TransferenciaItem ti : transferenciaItemList) {
                        propagarEntidad(ti, TipoEntidad.TRANSFERENCIA_ITEM, transferencia.getSucursalOrigen().getId());
                    }
                }
                break;
            case TRANSFERENCIA_ITEM:
                log.info("creando transferencia item: ");
                guardar(transferenciaItemService, dto);
                break;
            case INVENTARIO:
                log.info("creando inventario item: ");
                guardar(inventarioService, dto);
                break;
            case INVENTARIO_PRODUCTO:
                log.info("creando inventario producto item: ");
                guardar(inventarioProductoService, dto);
                break;
            case INVENTARIO_PRODUCTO_ITEM:
                log.info("creando inventario producto item item: ");
                guardar(inventarioProductoItemService, dto);
                break;
            default:
                break;
        }
    }

    public <T> void guardar(CrudService service, RabbitDto dto) {
        switch (dto.getTipoAccion()) {
            case GUARDAR:
                T nuevaEntidad = (T) service.save(dto.getEntidad());
                if (nuevaEntidad != null) {
                    log.info("guardado con exito");
                    propagarEntidad(nuevaEntidad, dto.getTipoEntidad(), dto.getIdSucursalOrigen());
                }
                break;
            case DELETE:
                Boolean ok = false;
                if (dto.getEntidad() instanceof Long) {
                    ok = service.deleteById((Long) dto.getEntidad());
                } else {
                    ok = service.delete(dto.getEntidad());
                }
                if (ok) {
                    log.info("eliminado con exito");
                }
                break;
            default:
                break;
        }
    }

    public <T> void propagar(TipoEntidad tipoEntidad, Long sucId, CrudService service) {
        List<T> list = service.findAll2();
        log.info("cantidad de itenes: " + list.size());
        sender.enviar(RabbitMQConection.FILIAL_KEY + "." + sucId, new RabbitDto(list, TipoAccion.SOLICITAR_DB, tipoEntidad));
    }

    public <T> void propagarEntidad(T entity, TipoEntidad tipoEntidad) {
        log.info("Propagando entidad a todas las sucursales: " + tipoEntidad.name());
        sender.enviar(RabbitMQConection.FILIAL_KEY, new RabbitDto(entity, TipoAccion.GUARDAR, tipoEntidad));
    }

    public void propagarImagen(String image, String filename, TipoEntidad tipoEntidad) {
        log.info("Propagando imagen a todas las sucursales: " + tipoEntidad.name());
        sender.enviar(RabbitMQConection.FILIAL_KEY, new RabbitDto(image, TipoAccion.GUARDAR_IMAGEN, tipoEntidad, filename));
    }

    public void propagarImagen(String image, String filename, TipoEntidad tipoEntidad, Long sucId) {
        log.info("Propagando imagen a todas las sucursales: " + tipoEntidad.name());
        sender.enviar(RabbitMQConection.FILIAL_KEY, new RabbitDto(image, TipoAccion.GUARDAR_IMAGEN, tipoEntidad, filename));
    }

    public void propagarArchivo(String path, String fileName, String finalFileName, String finalPath, Long sucId){
        ZipUtil.pack(new File(path+fileName), new File(finalPath+finalFileName+".zip"));
        File file = new File(finalPath+finalFileName+".zip");
        if(file.isFile()){
            byte[] fileContent = new byte[0];
            try {
                fileContent = FileUtils.readFileToByteArray(file);
                sender.enviar(RabbitMQConection.FILIAL_KEY+ "." + sucId, new RabbitDto(TipoAccion.GUARDAR_ARCHIVO, TipoEntidad.ARCHIVO, path+fileName, sucId, fileContent));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("bad type");
        }
        try {
            imageService.deleteFile(file.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public <T> void propagarEntidad(T entity, TipoEntidad tipoEntidad, Long sucId) {
        log.info("Propagando entidad a sucursal: " + sucId + ", entidad:" + tipoEntidad.name());
        sender.enviar(RabbitMQConection.FILIAL_KEY + "." + sucId, new RabbitDto(entity, TipoAccion.GUARDAR, tipoEntidad));
    }

    public void eliminarEntidad(Long id, TipoEntidad tipoEntidad) {
        log.info("Eliminando entidad en todas las sucursales");
        sender.enviar(RabbitMQConection.FILIAL_KEY, new RabbitDto(id, TipoAccion.DELETE, tipoEntidad));
    }

    public <T> void eliminarEntidad(T entity, TipoEntidad tipoEntidad, Long idSucursalOrigen) {
        log.info("Eliminando entidad en todas las sucursales");
        sender.enviar(RabbitMQConection.FILIAL_KEY, new RabbitDto(entity, TipoAccion.DELETE, tipoEntidad));
    }

    public void eliminarEntidad(Long id, TipoEntidad tipoEntidad, Long sucId) {
        log.info("Eliminando entidad en todas las sucursales");
        sender.enviar(RabbitMQConection.FILIAL_KEY + sucId, new RabbitDto(id, TipoAccion.DELETE, tipoEntidad));
    }

    public void propagarTransferencia(Transferencia t, Long id) {
        propagarEntidad(t, TipoEntidad.TRANSFERENCIA, id);
        List<TransferenciaItem> transferenciaItemList = transferenciaItemService.findByTransferenciaItemId(t.getId());
        for (TransferenciaItem ti : transferenciaItemList) {
            propagarEntidad(ti, TipoEntidad.TRANSFERENCIA_ITEM, id);
        }
    }

    public void propagarSucursales(Channel channel) {
        List<Sucursal> sucursalList = sucursalService.findAll2();
    }

    public void setSucursalConfigured(Long id) {
        Sucursal sucursal = sucursalService.findById(id).orElse(null);
        if (sucursal != null) {
            sucursal.setIsConfigured(true);
            sucursal = sucursalService.save(sucursal);
            propagarEntidad(sucursal, TipoEntidad.SUCURSAL);
            sender.enviar(RabbitMQConection.FILIAL_KEY + "." + id.toString(), new RabbitDto(sucursal, TipoAccion.GUARDAR, TipoEntidad.LOCAL));
        }
    }

    public Float solicitarStockByProducto(Long productoId, Long sucursalId) {
//        return sender.enviar(RabbitMQConection.FILIAL_KEY + sucId, new RabbitDto(id, TipoAccion.DELETE, tipoEntidad));
        return (Float) sender.enviarAndRecibir(RabbitMQConection.FILIAL_KEY + "." + sucursalId.toString(), new RabbitDto(productoId, TipoAccion.SOLICITAR_STOCK_PRODUCTO, TipoEntidad.PRODUCTO));
    }

    public Inventario finalizarInventario(Inventario inventario, Long sucId){
        return (Inventario) sender.enviarAndRecibir(RabbitMQConection.FILIAL_KEY + "." + sucId.toString(), new RabbitDto(inventario, TipoAccion.FINALIZAR_INVENTARIO, TipoEntidad.INVENTARIO));
    }
}
