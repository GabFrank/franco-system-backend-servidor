package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.CajaVirtualAcceso;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.CajaVirtualAccesoRepository;
import com.franco.dev.repository.financiero.CajaVirtualRepository;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Administracion de la lista de accesos de una caja virtual.
 *
 * <p>Quien administra la lista es el <b>propietario</b> de la caja (quien la creo) o un
 * superusuario; eso lo verifica el resolver con
 * {@code TesoreriaSecurityService.requirePropietarioCaja}. Este servicio se ocupa de las
 * reglas del dato.</p>
 */
@Service
@AllArgsConstructor
public class CajaVirtualAccesoService {

    private final CajaVirtualAccesoRepository repository;
    private final CajaVirtualRepository cajaVirtualRepository;
    private final UsuarioService usuarioService;

    @Transactional(readOnly = true)
    public List<CajaVirtualAcceso> listar(Long cajaVirtualId) {
        return repository.findByCajaVirtualIdOrderByIdAsc(cajaVirtualId);
    }

    /**
     * Otorga o actualiza el acceso de un usuario a una caja. Idempotente: si ya tenia una fila
     * se le actualizan los permisos en vez de duplicarla.
     */
    @Transactional
    public CajaVirtualAcceso otorgar(Long cajaVirtualId, Long usuarioId,
                                     Boolean puedeLeer, Boolean puedeEscribir, Usuario otorgadoPor) {
        CajaVirtual caja = cajaVirtualRepository.findById(cajaVirtualId)
                .orElseThrow(() -> new GraphQLException("Caja no encontrada: " + cajaVirtualId));
        Usuario usuario = usuarioService.findById(usuarioId)
                .orElseThrow(() -> new GraphQLException("Usuario no encontrado: " + usuarioId));

        // El propietario ya tiene lectura y escritura implicitas: darle una fila seria ruido
        // que ademas se puede revocar, dejando la caja sin quien la administre.
        if (caja.getUsuario() != null && caja.getUsuario().getId().equals(usuarioId)) {
            throw new GraphQLException("El responsable de la caja ya tiene acceso total; no hace falta otorgarselo.");
        }

        boolean escribir = Boolean.TRUE.equals(puedeEscribir);
        // Escribir sin poder leer no tiene sentido operativo (moverias plata a ciegas).
        boolean leer = escribir || Boolean.TRUE.equals(puedeLeer);
        if (!leer) {
            throw new GraphQLException("El acceso tiene que habilitar al menos la lectura. Para quitarlo, revocalo.");
        }

        CajaVirtualAcceso a = repository.findByCajaVirtualIdAndUsuarioId(cajaVirtualId, usuarioId)
                .orElseGet(CajaVirtualAcceso::new);
        a.setCajaVirtual(caja);
        a.setUsuario(usuario);
        a.setPuedeLeer(leer);
        a.setPuedeEscribir(escribir);
        a.setOtorgadoPor(otorgadoPor);
        return repository.save(a);
    }

    @Transactional
    public Boolean revocar(Long cajaVirtualId, Long usuarioId) {
        repository.findByCajaVirtualIdAndUsuarioId(cajaVirtualId, usuarioId)
                .ifPresent(repository::delete);
        return true;
    }

    /**
     * Transfiere la propiedad de la caja a otro usuario.
     *
     * <p>Sin esto, la caja de alguien que se va de la empresa queda sin quien administre su
     * lista de accesos. El acceso explicito del nuevo duenio, si lo tenia, se elimina: pasa a
     * tener permisos implicitos y la fila seria revocable.</p>
     */
    @Transactional
    public CajaVirtual transferirPropiedad(Long cajaVirtualId, Long nuevoPropietarioId) {
        CajaVirtual caja = cajaVirtualRepository.findById(cajaVirtualId)
                .orElseThrow(() -> new GraphQLException("Caja no encontrada: " + cajaVirtualId));
        Usuario nuevo = usuarioService.findById(nuevoPropietarioId)
                .orElseThrow(() -> new GraphQLException("Usuario no encontrado: " + nuevoPropietarioId));
        caja.setUsuario(nuevo);
        repository.findByCajaVirtualIdAndUsuarioId(cajaVirtualId, nuevoPropietarioId)
                .ifPresent(repository::delete);
        return cajaVirtualRepository.save(caja);
    }
}
