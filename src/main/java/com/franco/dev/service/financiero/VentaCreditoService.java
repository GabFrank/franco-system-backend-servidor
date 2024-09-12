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
            if (fechaInicio != null && fechaFin != null) {
                return repository.findAllWithDateAndFilters(id, fechaInicio, fechaFin, estado, cobro);
            } else {
                return repository.findAllWithFilters(id, estado);
            }
    }

    public List<VentaCredito> findByClienteId(Long id, EstadoVentaCredito estado) {
        return repository.findAllByClienteIdAndEstadoOrderByCreadoEnDesc(id, estado);
    }

    public Long countByClienteIdAndEstado(Long id, EstadoVentaCredito estado) {
        return repository.countByClienteIdAndEstado(id, estado);
    }

    public VentaCredito findByVentaIdAndSucId(Long id, Long sucId) {
        return repository.findByVentaIdAndSucursalId(id, sucId);
    }

    @Override
    public VentaCredito save(VentaCredito entity) {
        Usuario usuario = usuarioService.findByPersonaId(entity.getCliente().getPersona().getId());
        if (usuario != null) {
            Page<InicioSesion> inicioSesionPage = inicioSesionService.findByUsuarioIdAndHoraFinIsNul(usuario.getId(), Long.valueOf(0), PageRequest.of(0, 1));
            for (InicioSesion inicioSesion : inicioSesionPage.getContent()) {
                if (inicioSesion.getToken() != null) {
                    PushNotificationRequest pNr = new PushNotificationRequest();
                    Sucursal sucursal = sucursalService.findById(entity.getSucursalId()).orElse(null);
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
        VentaCredito e = super.save(entity);
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