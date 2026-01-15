package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.repository.financiero.BancoRepository;
import com.franco.dev.repository.financiero.RetiroRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.fmc.service.NotificationTemplateService;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;

@Service
@AllArgsConstructor
public class RetiroService extends CrudService<Retiro, RetiroRepository, EmbebedPrimaryKey> {

    private final RetiroRepository repository;

    @Override
    public RetiroRepository getRepository() {
        return repository;
    }

    // public List<Retiro> findByDenominacion(String texto){
    // texto = texto.replace(' ', '%');
    // return repository.findByDenominacionIgnoreCaseLike(texto);
    // }

    // public List<Retiro> findByAll(String texto){
    // texto = texto.replace(' ', '%');
    // return repository.findByAll(texto);
    // }

    public List<Retiro> findByCajaSalidaId(Long id) {
        return repository.findByCajaSalidaId(id);
    }

    public List<Retiro> filterRetiros(Long id, Long cajaId, Long sucId, Long responsableId, Long cajeroId,
            Pageable pageable) {
        return repository.findByAll(id, cajaId, sucId, responsableId, cajeroId, pageable);
    }

    public Page<Retiro> filterRetirosPage(Long id, Long cajaId, Long sucId, Long responsableId, Long cajeroId,
            Pageable pageable) {
        return repository.findByAllPage(id, cajaId, sucId, responsableId, cajeroId, pageable);
    }

    public Retiro findByIdAndSucursalId(Long id, Long sucId) {
        return repository.findByIdAndSucursalId(id, sucId);
    }

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private NotificationTemplateService notificationTemplateService;

    @Override
    public Retiro save(Retiro entity) {
        Retiro e = super.save(entity);
        if (e.getResponsable() != null && e.getResponsable().getPersona() != null) {
            Usuario usuario = usuarioService.findByPersonaId(e.getResponsable().getPersona().getId());
            if (usuario != null) {
                try {
                    Sucursal sucursal = sucursalService.findById(e.getSucursalId()).orElse(null);
                    PushNotificationRequest request = notificationTemplateService.retiroRealizado(e, sucursal);
                    request.setUsuarioIds(Collections.singletonList(usuario.getId()));
                    pushNotificationService.sendPushNotificationToToken(request);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
        return e;
    }
}