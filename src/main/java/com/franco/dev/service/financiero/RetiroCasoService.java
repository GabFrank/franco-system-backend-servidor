package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.RetiroCaso;
import com.franco.dev.domain.financiero.RetiroVerificacionDetalle;
import com.franco.dev.domain.financiero.enums.EstadoCasoRetiro;
import com.franco.dev.domain.financiero.enums.VeredictoCasoRetiro;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.RetiroCasoRepository;
import com.franco.dev.repository.financiero.RetiroRepository;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Casos de diferencia de un retiro: tomarlos, soltarlos y resolverlos.
 *
 * <p>Cada operación corre en <b>una transacción</b> y toma el caso con lock (issue #376). Antes vivían
 * en el resolver, sin transacción: resolver guardaba el caso y recién después anulaba la verificación
 * —si la anulación fallaba, el caso quedaba resuelto con la verificación vigente—, y las tres leían el
 * caso sin lock y lo guardaban entero, así que dos a la vez se pisaban.</p>
 *
 * <p>Orden de locks: el retiro (solo si se va a anular la verificación) y después el caso.
 * {@link RetiroVerificacionService#anular} arranca por el retiro; tomar el caso primero y el retiro
 * después se trabaría con una anulación directa de la misma verificación.</p>
 */
@Service
@RequiredArgsConstructor
public class RetiroCasoService {

    private final RetiroCasoRepository casoRepository;
    private final RetiroRepository retiroRepository;
    private final RetiroVerificacionService verificacionService;
    private final UsuarioService usuarioService;
    private final PersonaService personaService;
    private final EntityManager entityManager;

    /**
     * El caso, tomado con lock y con su estado leído de la base. {@code findById} puede devolver la
     * instancia que ya estuviera cargada en la request; el {@code refresh} la bloquea y la recarga.
     *
     * <p><b>Nada puede modificar el caso antes de llamar acá</b>: el {@code refresh} descarta en silencio
     * los cambios que todavía no se escribieron.</p>
     */
    private RetiroCaso tomar(RetiroCaso caso) {
        entityManager.refresh(caso, LockModeType.PESSIMISTIC_WRITE);
        return caso;
    }

    private RetiroCaso buscar(Long casoId) {
        return casoRepository.findById(casoId)
                .orElseThrow(() -> new GraphQLException("Caso no encontrado: " + casoId));
    }

    /**
     * Asigna el caso a {@code usuarioId} y lo pasa a investigación. No reabre un caso resuelto ni le
     * saca el caso a quien lo está investigando (salvo el superusuario, para destrabar casos de gente
     * que ya no está). Volver a tomar el propio no hace nada: un reintento no falla.
     */
    @Transactional
    public RetiroCaso asignar(Long casoId, Long usuarioId, boolean esSuperusuario) {
        RetiroCaso caso = tomar(buscar(casoId));
        if (caso.getEstado() == EstadoCasoRetiro.RESUELTO) {
            throw new GraphQLException("El caso ya está resuelto");
        }
        Usuario asignado = usuarioService.findById(usuarioId)
                .orElseThrow(() -> new GraphQLException("Usuario no encontrado: " + usuarioId));

        // El que contó no puede investigarse a sí mismo: quien verifica también puede ser el
        // problema, y esa es justamente una de las hipótesis que el caso deja abiertas.
        Usuario conto = caso.getVerificacion() != null ? caso.getVerificacion().getUsuario() : null;
        if (conto != null && conto.getId().equals(usuarioId)) {
            throw new GraphQLException("El caso no puede asignarse a quien hizo la verificación");
        }

        Usuario actual = caso.getAsignadoA();
        if (actual != null && actual.getId().equals(usuarioId)) {
            return caso;
        }
        if (actual != null && !esSuperusuario) {
            throw new GraphQLException("El caso ya lo tomó " + nombreDe(actual) + ".");
        }

        caso.setAsignadoA(asignado);
        caso.setEstado(EstadoCasoRetiro.EN_INVESTIGACION);
        return casoRepository.save(caso);
    }

    /**
     * Devuelve el caso a ABIERTO. Sin esto, tomarlo por error lo deja trabado a nombre de uno
     * para siempre.
     */
    @Transactional
    public RetiroCaso soltar(Long casoId) {
        RetiroCaso caso = tomar(buscar(casoId));
        if (caso.getEstado() == EstadoCasoRetiro.RESUELTO) {
            throw new GraphQLException("El caso ya está resuelto");
        }
        caso.setEstado(EstadoCasoRetiro.ABIERTO);
        caso.setAsignadoA(null);
        return casoRepository.save(caso);
    }

    /**
     * Cierra el caso con un veredicto tipado.
     *
     * El veredicto es obligatorio porque es la única parte del cierre que se puede contar
     * después: cuántos faltantes tuvo una sucursal, cuántas veces contó mal el mismo receptor.
     * El informe explica; el veredicto clasifica.
     *
     * Cuando se determinó que contó mal tesorería, lo acreditado en la caja mayor quedó
     * equivocado, así que se ofrece anular la verificación en el mismo acto — cerrar el caso
     * dejando la caja con el monto errado sería documentar el problema y conservarlo. Las dos
     * cosas van en la misma transacción: si la anulación no pasa, el caso tampoco queda resuelto.
     */
    @Transactional
    public RetiroCaso resolver(Long casoId, VeredictoCasoRetiro veredicto, String resolucion,
                               Long responsablePersonaId, Long reintegroRetiroId,
                               Boolean anularVerificacion, Usuario actual, boolean esSuperusuario) {
        boolean pidioAnular = Boolean.TRUE.equals(anularVerificacion);
        RetiroCaso caso = buscar(casoId);
        // El retiro va antes que el caso, y solo si se va a anular: anular() arranca por el retiro, y el
        // retiro llega por replicación — un lock sobre su fila frena al apply worker mientras dure.
        if (pidioAnular) {
            retiroRepository.lockByIdAndSucursalId(caso.getRetiroId(), caso.getSucursalId());
        }
        tomar(caso);
        if (caso.getEstado() == EstadoCasoRetiro.RESUELTO) {
            throw new GraphQLException("El caso ya está resuelto");
        }

        // Cierra el que investigó, no cualquiera con el rol. Si no, "tomar" no significa nada
        // y el informe lo puede firmar alguien que no habló con nadie. El ADMIN pasa por encima
        // para destrabar casos de gente que ya no está.
        Usuario asignado = caso.getAsignadoA();
        boolean esMio = asignado != null && actual != null && asignado.getId().equals(actual.getId());
        if (!esMio && !esSuperusuario) {
            throw new GraphQLException(asignado != null
                    ? "El caso lo está investigando " + nombreDe(asignado) + ". Que lo cierre esa persona, o que lo suelte."
                    : "Tomá el caso antes de resolverlo");
        }

        if (veredicto == null) {
            throw new GraphQLException("Falta el veredicto: sin él el caso no se puede clasificar");
        }
        VeredictoCasoRetiro v = veredicto;

        // Un responsable sin nombre no sirve para nada: el veredicto que apunta a un lado tiene
        // que decir a quién. Los otros dos veredictos existen justamente porque no hay a quién.
        boolean exigeResponsable = v == VeredictoCasoRetiro.FALTANTE_PDV
                || v == VeredictoCasoRetiro.SOBRANTE_PDV
                || v == VeredictoCasoRetiro.ERROR_DE_CONTEO_TESORERIA;
        if (exigeResponsable && responsablePersonaId == null) {
            throw new GraphQLException("Este veredicto necesita un responsable identificado");
        }
        if (v == VeredictoCasoRetiro.REINTEGRADO && reintegroRetiroId == null) {
            throw new GraphQLException("Indicá el retiro por el que se repuso la diferencia");
        }
        validarSigno(caso, v);
        if (pidioAnular && v != VeredictoCasoRetiro.ERROR_DE_CONTEO_TESORERIA) {
            throw new GraphQLException("Solo se anula la verificación cuando el error fue del conteo de tesorería");
        }
        // Antes esto se salteaba en silencio y el caso se resolvía igual, sin anular nada.
        if (pidioAnular && caso.getVerificacion() == null) {
            throw new GraphQLException("Este caso no tiene una verificación para anular. Resolvelo sin anularla.");
        }

        caso.setEstado(EstadoCasoRetiro.RESUELTO);
        caso.setVeredicto(v);
        caso.setResolucion(resolucion != null ? resolucion.toUpperCase() : null);
        caso.setReintegroRetiroId(v == VeredictoCasoRetiro.REINTEGRADO ? reintegroRetiroId : null);
        // El reintegro se busca en la misma sucursal del caso: un retiro de otra filial no
        // repone el faltante de esta.
        caso.setReintegroSucursalId(v == VeredictoCasoRetiro.REINTEGRADO ? caso.getSucursalId() : null);
        caso.setResponsablePersona(responsablePersonaId != null
                ? personaService.findById(responsablePersonaId).orElse(null) : null);
        caso.setResueltoPor(actual);
        caso.setResueltoEn(LocalDateTime.now());
        RetiroCaso guardado = casoRepository.save(caso);

        // Después de guardar: anular() no toca un caso ya RESUELTO, con lo cual el veredicto del
        // investigador sobrevive. Si lanza, la transacción se deshace entera, caso incluido.
        if (pidioAnular) {
            verificacionService.anular(caso.getVerificacion().getId(),
                    "ERROR DE CONTEO - CASO " + caso.getId(), actual);
        }
        return guardado;
    }

    /**
     * El veredicto tiene que coincidir con el signo de lo que se contó.
     *
     * "Vino menos" y "vino más" se miden siempre <b>desde el sobre</b> (contado − declarado), no
     * desde la caja del cajero, donde el mismo hecho se ve al revés: si al sobre le faltaron 10,
     * a esa caja le sobran 10. Sin este control, la mitad de los casos termina clasificada con el
     * signo invertido y el veredicto deja de servir para contar nada, que es su único propósito.
     *
     * Un retiro puede faltar en una moneda y sobrar en otra: alcanza con que exista una moneda
     * del signo elegido.
     */
    private void validarSigno(RetiroCaso caso, VeredictoCasoRetiro v) {
        if (v != VeredictoCasoRetiro.FALTANTE_PDV && v != VeredictoCasoRetiro.SOBRANTE_PDV) return;
        if (caso.getVerificacion() == null) return;

        boolean hayFaltante = false, haySobrante = false;
        for (RetiroVerificacionDetalle d : caso.getVerificacion().getDetalles()) {
            if (d.getDiferencia() == null) continue;
            int signo = d.getDiferencia().compareTo(BigDecimal.ZERO);
            if (signo < 0) hayFaltante = true;
            if (signo > 0) haySobrante = true;
        }

        if (v == VeredictoCasoRetiro.FALTANTE_PDV && !hayFaltante) {
            throw new GraphQLException(haySobrante
                    ? "El conteo dice que vino de más, no de menos. Revisá el veredicto."
                    : "Este retiro no tiene faltante registrado");
        }
        if (v == VeredictoCasoRetiro.SOBRANTE_PDV && !haySobrante) {
            throw new GraphQLException(hayFaltante
                    ? "El conteo dice que vino de menos, no de más. Si la plata quedó en la caja del cajero, igual al sobre le faltó."
                    : "Este retiro no tiene sobrante registrado");
        }
    }

    /** Nombre legible del usuario, para que el rechazo diga a quién reclamarle. */
    private String nombreDe(Usuario u) {
        if (u == null) return "otra persona";
        if (u.getPersona() != null && u.getPersona().getNombre() != null) return u.getPersona().getNombre();
        return u.getNickname() != null ? u.getNickname() : "otra persona";
    }
}
