package com.franco.dev.repository.configuracion;

import com.franco.dev.domain.configuracion.InicioSesion;
import com.franco.dev.fmc.service.NotificationTemplateService;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.graphql.configuracion.input.InicioSesionInput;
import com.franco.dev.service.configuracion.InicioSesionService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class InicioSesionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private InicioSesionService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private SucursalService sucursalService;

    public Optional<InicioSesion> inicioSesion(Long id) {
        return service.findById(id);
    }

    public List<InicioSesion> inicioSesiones() {
        return service.findAll();
    }

    public Page<InicioSesion> inicioSesionListPorUsuarioIdAndAbierto(Long id, Long sucId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findByUsuarioIdAndHoraFinIsNul(id, sucId, pageable);
    }

    public InicioSesion saveInicioSesion(InicioSesionInput input) {
        ModelMapper m = new ModelMapper();
        InicioSesion e = m.map(input, InicioSesion.class);
        if (input.getUsuarioId() != null)
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getSucursalId() != null)
            e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));

        if (e.getSucursal() == null) {
            e.setSucursal(sucursalService.sucursalActual());
        }

        if (e.getSucursal() == null) {
            e.setSucursal(sucursalService.findById(0L).orElse(null));
        }

        e.setIdDispositivo(input.getIdDispositivo());
        e.setToken(input.getToken());
        e.setTipoDespositivo(input.getTipoDespositivo());

        if (input.getHoraInicio() != null)
            e.setHoraInicio(stringToDate(input.getHoraInicio()));
        if (input.getHoraFin() != null)
            e.setHoraFin(stringToDate(input.getHoraFin()));
        if (input.getCreadoEn() != null)
            e.setCreadoEn(stringToDate(input.getCreadoEn()));

        InicioSesion saved = service.save(e);

        return saved;
    }

    public Boolean deleteInicioSesion(Long id) {
        return service.deleteById(id);
    }

    public Long countInicioSesion() {
        return service.count();
    }

    public Boolean actualizarTokenFcm(String tokenFcm) {
        try {
            org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                return false;
            }

            String username = authentication.getName();
            com.franco.dev.domain.personas.Usuario usuario = usuarioService.findByNickname(username)
                    .orElse(null);

            if (usuario == null) {
                return false;
            }

            List<InicioSesion> sesionesConToken = service.getRepository().findByToken(tokenFcm);

            if (!sesionesConToken.isEmpty()) {
                InicioSesion sesionExistente = sesionesConToken.get(0);
                if (sesionExistente.getUsuario() != null &&
                        sesionExistente.getUsuario().getId().equals(usuario.getId()) &&
                        sesionExistente.getHoraFin() == null) {
                    return true;
                }
            }
            Pageable pageable = PageRequest.of(0, 100);
            Page<InicioSesion> sesionesActivas = service.findByUsuarioIdAndHoraFinIsNul(usuario.getId(), null,
                    pageable);

            if (sesionesActivas.isEmpty()) {
                return false;
            }
            InicioSesion sesionParaActualizar = null;

            for (InicioSesion sesion : sesionesActivas.getContent()) {
                if (sesion.getToken() == null || sesion.getToken().trim().isEmpty()) {
                    sesionParaActualizar = sesion;
                    break;
                }
            }
            if (sesionParaActualizar == null) {
                sesionParaActualizar = sesionesActivas.getContent().get(0);
            }

            sesionParaActualizar.setToken(tokenFcm);
            service.save(sesionParaActualizar);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public Boolean notificarInicioSesion(Long usuarioId) {
        try {
            if (usuarioId == null) {
                return false;
            }

            com.franco.dev.domain.personas.Usuario usuario = usuarioService.findById(usuarioId).orElse(null);

            if (usuario == null) {
                return false;
            }

            String nombreUsuario = usuario.getPersona() != null && usuario.getPersona().getNombre() != null
                    ? usuario.getPersona().getNombre()
                    : usuario.getNickname();

            com.franco.dev.fmc.model.PushNotificationRequest requestBienvenida = new com.franco.dev.fmc.model.PushNotificationRequest();
            requestBienvenida.setTitle("SE HA INICIADO SESION EN SU CUENTA");
            requestBienvenida
                    .setMessage("BIENVENIDO " + (nombreUsuario != null ? nombreUsuario.toUpperCase() : "USUARIO"));
            requestBienvenida.setData("/");
            requestBienvenida.setType("LOGIN");
            requestBienvenida.setUsuarioIds(java.util.Collections.singletonList(usuarioId));

            pushNotificationService.sendPushNotificationToToken(requestBienvenida);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

}
