package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.configuracion.InicioSesion;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.financiero.enums.EstadoVentaCredito;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.repository.financiero.VentaCreditoRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.configuracion.InicioSesionService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class VentaCreditoService extends CrudService<VentaCredito, VentaCreditoRepository, EmbebedPrimaryKey> {

    public static final DecimalFormat df = new DecimalFormat("#,###.##");

    private final VentaCreditoRepository repository;

    @Autowired
    private InicioSesionService inicioSesionService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    @Override
    public VentaCreditoRepository getRepository() {
        return repository;
    }

    @Autowired
    private PushNotificationService pushNotificationService;

//    public List<VentaCredito> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public List<VentaCredito> findByClienteAndVencimiento(Long id, LocalDateTime inicio, LocalDateTime fin) {
        return repository.findAllByClienteIdAndCreadoEnLessThanEqualAndCreadoEnGreaterThanEqualOrderByCreadoEnDesc(id, inicio, fin);
    }

    public List<VentaCredito> findByClienteId(Long id, EstadoVentaCredito estado, Pageable pageable) {
        return repository.findAllByClienteIdAndEstadoOrderByCreadoEnDesc(id, estado, pageable);
    }

    public List<VentaCredito> findByClienteId(Long id, EstadoVentaCredito estado) {
        return repository.findAllByClienteIdAndEstadoOrderByCreadoEnDesc(id, estado);
    }

    public Long countByClienteIdAndEstado(Long id, EstadoVentaCredito estado){
        return repository.countByClienteIdAndEstado(id, estado);
    }

    public VentaCredito findByVentaIdAndSucId(Long id, Long sucId){
        return repository.findByVentaIdAndSucursalId(id, sucId);
    }

    @Override
    public VentaCredito save(VentaCredito entity) {
        Usuario usuario = usuarioService.findByPersonaId(entity.getCliente().getPersona().getId());
        Page<InicioSesion> inicioSesionPage = inicioSesionService.findByUsuarioIdAndHoraFinIsNul(usuario.getId(), Long.valueOf(0), PageRequest.of(0, 1));
        if(inicioSesionPage.getContent().size() == 1){
            InicioSesion inicioSesion = inicioSesionPage.getContent().get(0);
            if(inicioSesion.getToken() != null){
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
        VentaCredito e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}