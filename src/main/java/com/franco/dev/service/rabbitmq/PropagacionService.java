package com.franco.dev.service.rabbitmq;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.Conteo;
import com.franco.dev.domain.financiero.Maletin;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.operaciones.Inventario;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.enums.EtapaTransferencia;
import com.franco.dev.graphql.financiero.FacturaLegalGraphQL;
import com.franco.dev.rabbit.RabbitMQConection;
import com.franco.dev.rabbit.dto.RabbitDto;
import com.franco.dev.rabbit.dto.SaveConteoDto;
import com.franco.dev.rabbit.dto.SaveFacturaDto;
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

import java.io.File;
import java.io.IOException;
import java.util.List;

@Service
public class PropagacionService {

    private final Logger log = LoggerFactory.getLogger(PropagacionService.class);
    Long sucursalVerificar = null;
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

    @Autowired
    private FacturaLegalGraphQL facturaLegalGraphQL;

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
//                propagarArchivo(imageService.getImagePath(), "", imageService.getImagePath(), "images.zip", sucId);
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

    public Object crudEntidad(RabbitDto dto) {
        log.info("recibiendo entidad para guardar");
        switch (dto.getTipoEntidad()) {
            case USUARIO:
                log.info("cargando usuario: ");
                return guardar(usuarioService, dto);
            case PAIS:
                log.info("cargando Pais: ");
                return guardar(paisService, dto);

            case CIUDAD:
                log.info("cargando Ciudad: ");
                return guardar(ciudadService, dto);

            case PERSONA:
                log.info("cargando Persona: ");
                return guardar(personaService, dto);

            case FAMILIA:
                log.info("cargando Familia: ");
                return guardar(familiaService, dto);

            case SUBFAMILIA:
                log.info("cargando subfamilia: ");
                return guardar(subFamiliaService, dto);

            case PRODUCTO:
                log.info("cargando productos: ");
                return guardar(productoService, dto);

            case TIPO_PRESENTACION:
                log.info("cargando tipo presentacion: ");
                return guardar(tipoPresentacionService, dto);

            case PRESENTACION:
                log.info("cargando presentacion: ");
                return guardar(presentacionService, dto);

            case PDV_CATEGORIA:
                log.info("cargando pdv categoria: ");
                return guardar(pdvCategoriaService, dto);

            case PDV_GRUPO:
                log.info("cargando pdv grupo: ");
                return guardar(pdvGrupoService, dto);

            case PDV_GRUPO_PRODUCTO:
                log.info("cargando pdv grupo producto: ");
                return guardar(pdvGruposProductosService, dto);

            case SUCURSAL:
                log.info("cargando sucursal: ");
                return guardar(sucursalService, dto);

            case TIPO_PRECIO:
                log.info("cargando tipo precio: ");
                return guardar(tipoPrecioService, dto);

            case PRECIO_POR_SUCURSAL:
                log.info("cargando precio por sucursal: ");
                return guardar(precioPorSucursalService, dto);

            case BANCO:
                log.info("cargando banco: ");
                return guardar(bancoService, dto);

            case MONEDA:
                log.info("cargando moneda: ");
                return guardar(monedaService, dto);

            case MONEDAS_BILLETES:
                log.info("cargando moneda billetes: ");
                return guardar(monedaBilleteService, dto);

            case CUENTA_BANCARIA:
                log.info("cargando cuenta bancaria: ");
                return guardar(cuentaBancariaService, dto);

            case FORMA_DE_PAGO:
                log.info("cargando forma pago: ");
                return guardar(formaPagoService, dto);

            case DOCUMENTO:
                log.info("cargando docuemento: ");
                return guardar(documentoService, dto);

            case MALETIN:
                log.info("cargando maletin: ");
                return guardar(maletinService, dto);

            case CARGO:
                log.info("cargando cargo: ");
                return guardar(cargoService, dto);

            case TIPO_GASTO:
                log.info("cargando tipo gasto: ");
                return guardar(tipoGastoService, dto);

            case TIPO_GASTO_UPDATE:
                log.info("cargando tipo gasto: ");
                return guardar(tipoGastoService, dto);

            case CODIGO:
                log.info("cargando codigo: ");
                return guardar(codigoService, dto);

            case CAMBIO:
                log.info("cargando cambio: ");
                return guardar(codigoService, dto);

            case BARRIO:
                log.info("cargando barrio: ");
                return guardar(barrioService, dto);

            case CONTACTO:
                log.info("cargando contacto: ");
                return guardar(contactoService, dto);

            case CLIENTE:
                log.info("cargando cliente: ");
                return guardar(clienteService, dto);

            case FUNCIONARIO:
                log.info("cargando funncioario: ");
                return guardar(funcionarioService, dto);

            case PROVEEDOR:
                log.info("cargando proveedor: ");
                return guardar(proveedorService, dto);

            case VENDEDOR:
                log.info("cargando vendedor: ");
                return guardar(vendedorService, dto);

            case ROLE:
                log.info("cargando role: ");
                return guardar(roleService, dto);

            case USUARIO_ROLE:
                log.info("cargando usuario role: ");
                return guardar(usuarioRoleService, dto);

            case TRANSFERENCIA:
                log.info("creando transferencia: ");
                Object obj = guardar(transferenciaService, dto);
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
                return obj;
            case TRANSFERENCIA_ITEM:
                log.info("creando transferencia item: ");
                return guardar(transferenciaItemService, dto);

            case INVENTARIO:
                log.info("creando inventario item: ");
                return guardar(inventarioService, dto);

            case INVENTARIO_PRODUCTO:
                log.info("creando inventario producto item: ");
                return guardar(inventarioProductoService, dto);

            case INVENTARIO_PRODUCTO_ITEM:
                log.info("creando inventario producto item item: ");
                return guardar(inventarioProductoItemService, dto);
            case FACTURA:
                log.info("creando factura legal: ");
                return guardarFacturas(dto);
            default:
                return null;
        }
    }

    public <T> Object guardar(CrudService service, RabbitDto dto) {
        switch (dto.getTipoAccion()) {
            case GUARDAR:
                T nuevaEntidad = (T) service.save(dto.getEntidad());
                if (nuevaEntidad != null) {
                    log.info("guardado con exito");
                    propagarEntidad(nuevaEntidad, dto.getTipoEntidad(), dto.getIdSucursalOrigen());
                }
                return nuevaEntidad;
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
                return ok;
            default:
                return null;
        }
    }

    public Object enviarEntidad(RabbitDto dto){
        switch (dto.getTipoEntidad()){
            case CLIENTE:
                if(dto.getEntidad()!=null && dto.getEntidad().getClass().getName().equals("Long")){
                    return clienteService.findById((Long) dto.getEntidad()).orElse(null);
                }
            default:
                return null;
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

    public void propagarArchivo(String path, String fileName, String finalFileName, String finalPath, Long sucId) {
        ZipUtil.pack(new File(path + fileName), new File(finalPath + finalFileName + ".zip"));
        File file = new File(finalPath + finalFileName + ".zip");
        if (file.isFile()) {
            byte[] fileContent = new byte[0];
            try {
                fileContent = FileUtils.readFileToByteArray(file);
                sender.enviar(RabbitMQConection.FILIAL_KEY + "." + sucId, new RabbitDto(TipoAccion.GUARDAR_ARCHIVO, TipoEntidad.ARCHIVO, path + fileName, sucId, fileContent));
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
        if (sucId != null) {
            sender.enviar(RabbitMQConection.FILIAL_KEY + "." + sucId, new RabbitDto(entity, TipoAccion.GUARDAR, tipoEntidad));
        } else {
            sender.enviar(RabbitMQConection.FILIAL_KEY, new RabbitDto(entity, TipoAccion.GUARDAR, tipoEntidad));
        }
    }

    public <T> T propagarEntidadAndRecibir(T entity, TipoEntidad tipoEntidad, Long sucId) {
        log.info("Propagando entidad a sucursal: " + sucId + ", entidad:" + tipoEntidad.name());
        return (T) sender.enviarAndRecibir(RabbitMQConection.FILIAL_KEY + "." + sucId, new RabbitDto(entity, TipoAccion.GUARDAR, tipoEntidad));
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

    public Inventario finalizarInventario(Inventario inventario, Long sucId) {
        return (Inventario) sender.enviarAndRecibir(RabbitMQConection.FILIAL_KEY + "." + sucId.toString(), new RabbitDto(inventario, TipoAccion.FINALIZAR_INVENTARIO, TipoEntidad.INVENTARIO));
    }

    public PdvCaja buscarCajaAbiertaPorSucursal(Long id, Long sucId) {
        log.info("Solicitando caja a sucursal " + sucId);
        PdvCaja pdvCaja = (PdvCaja) sender.enviarAndRecibir(RabbitMQConection.FILIAL_KEY + "." + sucId.toString(), new RabbitDto(id, TipoAccion.SOLICITAR_CAJA_ABIERTA, TipoEntidad.PDV_CAJA, sucId));
        return pdvCaja;
    }

    public Conteo saveConteo(SaveConteoDto dto) {
        log.info("guardando conteo en suc " + dto.getSucId());
        Conteo conteo = (Conteo) sender.enviarAndRecibir(RabbitMQConection.FILIAL_KEY + "." + dto.getSucId().toString(), new RabbitDto(dto, TipoAccion.GUARDAR, TipoEntidad.CONTEO, dto.getSucId()));
        return conteo;
    }

    public Maletin maletinPorDescripcionPorSucursal(String texto, Long sucId) {
        log.info("solicitando maletin a " + sucId);
        Maletin maletin = (Maletin) sender.enviarAndRecibir(RabbitMQConection.FILIAL_KEY + "." + sucId.toString(), new RabbitDto(texto, TipoAccion.SOLICITAR_MALETIN, TipoEntidad.MALETIN));
        return maletin;
    }

    public Boolean guardarFacturas(RabbitDto dto) {
        SaveFacturaDto saveFacturaDto = (SaveFacturaDto) dto.getEntidad();
        return facturaLegalGraphQL.saveFacturaLegal(saveFacturaDto.getFacturaLegalInput(), saveFacturaDto.getFacturaLegalItemInputList()) != null;
    }
}





















