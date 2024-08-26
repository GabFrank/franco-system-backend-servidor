package com.franco.dev.service.financiero;

import com.franco.dev.config.multitenant.*;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.configuracion.InicioSesion;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.financiero.enums.EstadoVentaCredito;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.repository.financiero.VentaCreditoRepository;
import com.franco.dev.repository.financiero.VentaCreditoRepositoryImpl;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.configuracion.InicioSesionService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.InventarioService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.personas.UsuarioService;
import com.sun.org.apache.xpath.internal.operations.Bool;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class VentaCreditoService extends CrudService<VentaCredito, VentaCreditoRepository, EmbebedPrimaryKey> {

    private final Logger log = LoggerFactory.getLogger(VentaCreditoService.class);


    public static final DecimalFormat df = new DecimalFormat("#,###.##");

    private VentaCreditoRepository repository = null;

    @Autowired
    private InicioSesionService inicioSesionService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    private VentaService ventaService;

    @Autowired
    private MultiTenantService multiTenantService;
    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private VentaCreditoRepositoryImpl ventaCreditoRepository;

    //Se tuvo que hacer de esta forma porque hay dependencia circular entre VentaService y VentaCreditoService
    @Autowired
    public VentaCreditoService(@Lazy VentaService ventaService, VentaCreditoRepository repository) {
        this.ventaService = ventaService;
        this.repository = repository;
    }

    @Override
    public VentaCreditoRepository getRepository() {
        return repository;
    }

//    public List<VentaCredito> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public List<VentaCredito> findByClienteAndVencimiento(Long id, LocalDateTime inicio, LocalDateTime fin) {
        return repository.findAllByClienteIdAndCreadoEnLessThanEqualAndCreadoEnGreaterThanEqualOrderByCreadoEnDesc(id, inicio, fin);
    }

    public Page<VentaCredito> findByClienteId(Long id, EstadoVentaCredito estado, Pageable pageable) {
        return repository.findAllByClienteIdAndEstadoOrderByCreadoEnDesc(id, estado, pageable);
    }

    public List<VentaCredito> findWithFilters(Long id, LocalDateTime fechaInicio, LocalDateTime fechaFin, EstadoVentaCredito estado, Boolean cobro) {

        List<List<VentaCredito>> results = new ArrayList<>();
//        Long totalItens = Long.valueOf(0);
//
        for (String key : TenantContext.getAllTenantKeys()) {
            List<VentaCredito> res = null;
//            MultiPage multiPageable = null;
//            int offset = 0;
//            int limit = pageable.getPageSize();
//            if (multiPageableList != null && multiPageableList.size() > 0) {
//                for (MultiPage p : multiPageableList) {
//                    if (p.getTenantId().equals(key)) {
//                        multiPageable = p;
//                        offset = p.getOffset();
//                        if(pageable.getPageNumber() < p.getPage()){
//                            offset = (int) (p.getLastOffset() - p.getLastTotalElement());
//                        }
//                    }
//                }
//            }
            if (fechaInicio != null && fechaFin != null) {
                res = (List<VentaCredito>) (Object) multiTenantService.compartir(key, (params) -> repository.findAllWithDateAndFilters((Long) params[0], (LocalDateTime) params[1], (LocalDateTime) params[2], (EstadoVentaCredito) params[3], (Boolean) params[4]), id, fechaInicio, fechaFin, estado, cobro);
            } else {
                res = (List<VentaCredito>) (Object) multiTenantService.compartir(key, (params) -> repository.findAllWithFilters((Long) params[0], (EstadoVentaCredito) params[1]), id, estado);
//                if (multiPageable != null && multiPageable.getTotalElements() != null) {
//                    totalItens += multiPageable.getTotalElements();
//                } else {
//                    Long res2 = (Long) (Object) multiTenantService.compartir(key, (params) -> repository.countByClienteIdAndEstado((Long) params[0], (EstadoVentaCredito) params[1]), id, estado);
//                    totalItens += res2;
//                }

            }
            if (res != null) results.add(res);

        }



        List<VentaCredito> combinedContent = results.stream()
                .flatMap(page -> page.stream())
//                .sorted(Comparator.comparing(VentaCredito::getCreadoEn).reversed())
//                .limit(pageable.getPageSize()) // Limit the number of items based on pageable size
                .collect(Collectors.toList());

        return combinedContent;
//
//
//        List<MultiPage> newMultiPageList = combinedContent.stream()
//                .collect(Collectors.groupingBy(
//                        VentaCredito::getSucursalId, // Group by SucursalId (which will be the tenantId)
//                        Collectors.counting() // Count the number of items in each group
//                ))
//                .entrySet().stream()
//                .map(entry -> {
//                    MultiPage multiPageable = null;
//                    if (multiPageableList != null && multiPageableList.size() > 0) {
//                        for (MultiPage p : multiPageableList) {
//                            if (p.getTenantId().equals("filial" + String.valueOf(entry.getKey()) + "_bkp")) {
//                                multiPageable = p;
//                            }
//                        }
//                    }
//                    if (multiPageable == null) {
//                        multiPageable = new MultiPage();
//                        multiPageable.setTenantId("filial" + String.valueOf(entry.getKey()) + "_bkp"); // Set tenantId as the SucursalId
//                        multiPageable.setOffset(0);
//                        multiPageable.setLastTotalElement((long) 0);
//                        multiPageable.setLastOffset((long) 0);
//                    }
//                    Long currentOffset = Long.valueOf(multiPageable.getOffset());
//                    multiPageable.setLastTotalElement(multiPageable.getOffset() - multiPageable.getLastOffset());
//                    multiPageable.setLastOffset((long) multiPageable.getOffset());
//                    multiPageable.setOffset((int) (Math.toIntExact(entry.getValue()) + currentOffset)); // Set offset as the count of items
//                    multiPageable.setPage(pageable.getPageNumber());
//                    return multiPageable;
//                })
//                .collect(Collectors.toList());
//
//        if (multiPageableList != null && multiPageableList.size() > 0) {
//            newMultiPageList.addAll(multiPageableList.stream()
//                    .filter(item -> !newMultiPageList.contains(item))
//                    .peek(item -> item.setLastOffset((long) item.getOffset())) // Replace setSomeAttribute with the actual method to modify the attribute
//                    .peek(item -> item.setPage(pageable.getPageNumber())) // Replace setSomeAttribute with the actual method to modify the attribute
//                    .peek(item -> item.setLastTotalElement((long) 0)) // Replace setSomeAttribute with the actual method to modify the attribute
//                    .collect(Collectors.toList()));
//        }
//
//        return new CustomPageImpl<>(combinedContent, pageable, totalItens, newMultiPageList);
//        List<List<VentaCredito>> results = new ArrayList<>();
//        List<MultiPage> newMultiPageList = new ArrayList<>();
//        for(String key: TenantContext.getAllTenantKeys()){
//            Pageable customPageable = pageable;
//            List<VentaCredito> res;
//            MultiPage multiPageable = null;
//            if(multiPageableList != null && multiPageableList.size() > 0){
//                for(MultiPage p : multiPageableList){
//                    if(p.getTenantId().equals(key)){
//                        multiPageable = p;
//                    }
//                }
//            }
//            if(multiPageable!=null){
//                customPageable = PageRequest.of(multiPageable.getPage(), pageable.getPageSize() + multiPageable.getOffset(), pageable.getSort());
//            } else {
//                multiPageable = new MultiPage(key, 0, 0);
//            }
//            if (fechaInicio != null && fechaFin != null) {
//                res = (List<VentaCredito>) (Object) multiTenantService.compartir(key, (params) -> repository.findAllWithDateAndFilters((Long) params[0], (LocalDateTime) params[1], (LocalDateTime) params[2], (EstadoVentaCredito) params[3], (Pageable) params[4]), id, fechaInicio, fechaFin, estado, customPageable);
//            } else {
//                res = (List<VentaCredito>) (Object) multiTenantService.compartir(key, (params) -> ventaCreditoRepository.findVentaCreditoWithFilters((Long) params[0], (EstadoVentaCredito) params[1], (int) params[2], (int) params[3]), id, estado, multiPageable.getOffset(), pageable.getPageSize());
//            }
////            if(multiPageable.getOffset()>0){
////                List<VentaCredito> contentList = res.getContent();
////                List<VentaCredito> trimmedList = contentList.subList(Math.min(multiPageable.getOffset(), contentList.size()), contentList.size());
////                res = new PageImpl<VentaCredito>(trimmedList, res.getPageable(), res.getTotalElements() + multiPageable.getOffset());
////            }
//            results.add(res);
//        }
//
//        long totalElements = results.stream().mapToLong(List::size).sum();
//
//        List<VentaCredito> combinedContent = results.stream()
//                .flatMap(page -> page.stream())
//                .sorted(Comparator.comparing(VentaCredito::getCreadoEn).reversed())
//                .limit(pageable.getPageSize()) // Limit the number of items based on pageable size
//                .collect(Collectors.toList());
//
//        newMultiPageList = combinedContent.stream()
//                .collect(Collectors.groupingBy(
//                        VentaCredito::getSucursalId, // Group by SucursalId (which will be the tenantId)
//                        Collectors.counting() // Count the number of items in each group
//                ))
//                .entrySet().stream()
//                .map(entry -> {
//                    MultiPage multiPage = new MultiPage();
//                    multiPage.setTenantId("filial"+String.valueOf(entry.getKey())+"_bkp"); // Set tenantId as the SucursalId
//                    multiPage.setOffset(Math.toIntExact(entry.getValue())); // Set offset as the count of items
//                    MultiPage existingPage = multiPageableList != null ? multiPageableList.stream()
//                            .filter(mp -> mp.getTenantId().equals(multiPage.getTenantId()))
//                            .findFirst().orElse(null) : null;
//                    int page = 0;
//                    if(existingPage!=null){
//                        page = existingPage.getPage();
//                        if(multiPage.getOffset() >= pageable.getPageSize()){
//                            page++;
//                        }
//                        multiPage.setOffset(existingPage.getOffset() + );
//                    }
//                    multiPage.setPage(page); // Set page to existing page or 0
//                    return multiPage;
//                })
//                .collect(Collectors.toList());
//
//        // Create a new PageImpl object with the combined content and total count
//        return new CustomPageImpl<>(combinedContent, pageable, totalElements, newMultiPageList);
    }

    public List<VentaCredito> findByClienteId(Long id, EstadoVentaCredito estado) {
        List<Sucursal> sucursalList = sucursalService.findAll2();
        //El resultado de esta funcion va a ser una lista de listas
        List<List<VentaCredito>> results = (List<List<VentaCredito>>) (Object) multiTenantService.compartir(
                null,
                (params) -> repository.findAllByClienteIdAndEstadoOrderByCreadoEnDesc(id, estado),
                id, estado);

        // Primero necesitamos convertir la lista de listas en una sola lista
        List<VentaCredito> ventaCreditoList = results.stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());

        // Luego vamos a ordenar for fecha
        return ventaCreditoList.stream()
                .sorted(Comparator.comparing(VentaCredito::getCreadoEn).reversed())
                .collect(Collectors.toList());
    }

    public Long countByClienteIdAndEstado(Long id, EstadoVentaCredito estado) {
        return repository.countByClienteIdAndEstado(id, estado);
    }

    public VentaCredito findByVentaIdAndSucId(Long id, Long sucId) {
        return repository.findByVentaIdAndSucursalId(id, sucId);
    }

    @Override
    public VentaCredito save(VentaCredito entity) {
        log.info("tenant Actual: " + TenantContext.getCurrentTenant());
        Usuario usuario = multiTenantService.compartir("default", (params) -> usuarioService.findByPersonaId(entity.getCliente().getPersona().getId()), entity.getCliente().getPersona().getId());
        log.info("tenant Actual: " + TenantContext.getCurrentTenant());
        if (usuario != null) {
            Page<InicioSesion> inicioSesionPage = multiTenantService.compartir("default", (params) -> inicioSesionService.findByUsuarioIdAndHoraFinIsNul(usuario.getId(), Long.valueOf(0), PageRequest.of(0, 1)), usuario.getId(), Long.valueOf(0), PageRequest.of(0, 1));
            for (InicioSesion inicioSesion : inicioSesionPage.getContent()) {
                if (inicioSesion.getToken() != null) {
                    PushNotificationRequest pNr = new PushNotificationRequest();
                    Sucursal sucursal = multiTenantService.compartir("default", (params) -> sucursalService.findById(entity.getSucursalId()).orElse(null), entity.getSucursalId());
                    pNr.setTitle("Venta a crédito realizada");
                    StringBuilder stb = new StringBuilder();
                    stb.append("Se ha detectado una venta a crédito en la sucursal ");
                    stb.append(sucursal.getNombre());
                    stb.append(" por el valor de ");
                    stb.append(df.format(entity.getValorTotal()));
                    stb.append(" Gs. ");
                    pNr.setMessage(stb.toString());
                    pNr.setToken(inicioSesion.getToken());
                    pNr.setData("/mis-finanzas/list-convenio/" + entity.getId() + "/" + entity.getSucursalId());
                    pushNotificationService.sendPushNotificationToToken(pNr);
                }
            }
        }
        log.info("intentando guardar venta credito en tenant: " + TenantContext.getCurrentTenant());
        VentaCredito e = multiTenantService.compartir("filial"+entity.getSucursalId()+"_bkp", (params) -> super.save(entity), entity);
        log.info("tenant Actual: " + TenantContext.getCurrentTenant());
//        personaPublisher.publish(p);
        return e;
    }

    public Boolean cancelarVentaCredito(Long id, Long sucId) {
        VentaCredito ventaCredito = findById(new EmbebedPrimaryKey(id, sucId)).orElse(null);
        if (ventaCredito != null) {
            try {
                Venta venta = ventaService.findById(new EmbebedPrimaryKey(ventaCredito.getVenta().getId(), sucId)).orElse(null);
                if (venta != null && venta.getEstado() != VentaEstado.CANCELADA) {
                    venta.setEstado(VentaEstado.CANCELADA);
                    venta = ventaService.save(venta);
                }
                ventaCredito.setEstado(EstadoVentaCredito.CANCELADO);
                this.save(ventaCredito);
                return true;
            } catch (Exception e) {
                throw new GraphQLException("No se puedo cancelar la venta");
            }
        } else {
            throw new GraphQLException("Venta credito no encontrada");
        }

    }

    public VentaCredito findByIdAndSucursalId(Long id, Long sucId){
        return repository.findByIdAndSucursalId(id, sucId);
    }

}