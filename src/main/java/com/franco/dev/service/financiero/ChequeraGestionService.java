package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Chequera;
import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.domain.financiero.enums.EstadoChequera;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.ChequeraInput;
import com.franco.dev.repository.financiero.ChequeRepository;
import com.franco.dev.repository.financiero.ChequeraRepository;
import com.franco.dev.repository.financiero.CuentaBancariaRepository;
import graphql.GraphQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.TreeSet;

/**
 * Alta y edición de chequeras (issue #376).
 *
 * <p>Antes la edición armaba la entidad entera con lo que mandaba el formulario y la guardaba, sin lock.
 * El desktop manda siempre el «siguiente número» que tenía en pantalla —también al desactivar desde la
 * lista—: si entre medio se había emitido un cheque, el correlativo volvía atrás y el próximo cheque
 * repetía un número.</p>
 *
 * <p>Acá la edición se decide <b>sobre lo que hay en la base</b>: la chequera se toma con el mismo lock
 * que usa {@code ChequeGestionService.emitir} y se relee. El correlativo solo va hacia adelante; una
 * chequera anulada no se reactiva; y dos chequeras no anuladas de una misma cuenta no comparten números.</p>
 */
@Service
@RequiredArgsConstructor
public class ChequeraGestionService {

    private final ChequeraRepository chequeraRepository;
    private final ChequeRepository chequeRepository;
    private final CuentaBancariaRepository cuentaBancariaRepository;
    private final BloqueoTransaccionalService bloqueo;

    @PersistenceContext
    private EntityManager entityManager;

    /** Para los tests unitarios, que arman el servicio a mano. */
    void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public Chequera guardar(ChequeraInput pedido, Usuario usuario) {
        return pedido.getId() == null ? crear(pedido, usuario) : editar(pedido);
    }

    private Chequera crear(ChequeraInput pedido, Usuario usuario) {
        if (pedido.getCuentaBancariaId() == null) throw new GraphQLException("La chequera requiere una cuenta bancaria.");
        long desde = entero(pedido.getRangoDesde(), "desde");
        long hasta = entero(pedido.getRangoHasta(), "hasta");
        validarRango(desde, hasta);
        long siguiente = pedido.getSiguienteNumero() != null ? pedido.getSiguienteNumero() : desde;
        if (siguiente < desde || siguiente > hasta) {
            throw new GraphQLException("El próximo número (" + siguiente + ") tiene que estar dentro del rango " + desde + "–" + hasta + ".");
        }
        // El lock por nombre antes de buscar: dos altas simultáneas con el mismo rango pasarían las dos.
        bloqueo.tomar(claveDeCuenta(pedido.getCuentaBancariaId()));
        CuentaBancaria cuenta = cuenta(pedido.getCuentaBancariaId());
        rechazarSiSeSuperpone(cuenta.getId(), desde, hasta, null);

        Chequera chequera = new Chequera();
        chequera.setCuentaBancaria(cuenta);
        chequera.setNombre(pedido.getNombre());
        chequera.setFirmantes(pedido.getFirmantes());
        chequera.setRangoDesde((double) desde);
        chequera.setRangoHasta((double) hasta);
        chequera.setSiguienteNumero(siguiente);
        chequera.setEstado(pedido.getEstado() == EstadoChequera.ANULADA ? EstadoChequera.ANULADA : EstadoChequera.ACTIVA);
        chequera.setFechaRetiro(pedido.getFechaRetiro());
        chequera.setCreadoEn(LocalDateTime.now());
        chequera.setUsuario(usuario);
        return chequeraRepository.save(chequera);
    }

    private Chequera editar(ChequeraInput pedido) {
        Long id = pedido.getId();
        // La cuenta actual, sin cargar la entidad: hace falta para el lock por nombre, que va primero.
        Long cuentaActualId = chequeraRepository.findCuentaIdById(id)
                .orElseThrow(() -> new GraphQLException("Chequera no encontrada: " + id));
        Long cuentaPedidaId = pedido.getCuentaBancariaId() != null ? pedido.getCuentaBancariaId() : cuentaActualId;
        // Las dos cuentas, por id ascendente, si cambia: dos ediciones cruzadas no se traban.
        for (Long cuentaId : new TreeSet<>(java.util.Arrays.asList(cuentaActualId, cuentaPedidaId))) {
            bloqueo.tomar(claveDeCuenta(cuentaId));
        }
        Chequera chequera = chequeraRepository.lockById(id)
                .orElseThrow(() -> new GraphQLException("Chequera no encontrada: " + id));
        entityManager.refresh(chequera);
        if (chequera.getCuentaBancaria() == null || !cuentaActualId.equals(chequera.getCuentaBancaria().getId())) {
            throw new GraphQLException("La chequera cambió mientras se guardaba. Volvé a abrirla e intentá de nuevo.");
        }

        if (chequera.getEstado() == EstadoChequera.ANULADA) {
            // Terminal: una pantalla vieja no «reactiva» una chequera que otro anuló, y reactivarla
            // esquivaría el control de superposición. Solo se le pueden corregir los textos.
            if (pedido.getEstado() != null && pedido.getEstado() != EstadoChequera.ANULADA) {
                throw new GraphQLException("Una chequera anulada no se reactiva. Si se anuló por error, creá otra.");
            }
            chequera.setNombre(pedido.getNombre());
            chequera.setFirmantes(pedido.getFirmantes());
            return chequeraRepository.save(chequera);
        }

        if (pedido.getEstado() == EstadoChequera.ANULADA) {
            // Anular es solo anular: «Desactivar» manda la fila entera como la tenía la pantalla, y aplicarla
            // pisaría el rango o el correlativo que otro corrigió. Tampoco se valida nada: una chequera mal
            // cargada desde antes tiene que poder anularse.
            chequera.setNombre(pedido.getNombre());
            chequera.setFirmantes(pedido.getFirmantes());
            chequera.setEstado(EstadoChequera.ANULADA);
            return chequeraRepository.save(chequera);
        }

        Long primerEmitido = aLong(chequeRepository.minNumeroPorChequera(id));
        Long ultimoEmitido = aLong(chequeRepository.maxNumeroPorChequera(id));
        boolean emitio = ultimoEmitido != null;

        boolean cambiaCuenta = !cuentaPedidaId.equals(cuentaActualId);
        if (cambiaCuenta) {
            if (emitio) throw new GraphQLException("No se puede cambiar la cuenta de una chequera que ya emitió cheques.");
            chequera.setCuentaBancaria(cuenta(cuentaPedidaId));
        }

        long desdeBase = chequera.getRangoDesde() != null ? chequera.getRangoDesde().longValue() : 1L;
        long hastaBase = chequera.getRangoHasta() != null ? chequera.getRangoHasta().longValue() : desdeBase;
        long desde = pedido.getRangoDesde() != null ? entero(pedido.getRangoDesde(), "desde") : desdeBase;
        long hasta = pedido.getRangoHasta() != null ? entero(pedido.getRangoHasta(), "hasta") : hastaBase;
        validarRango(desde, hasta);
        boolean cambiaRango = desde != desdeBase || hasta != hastaBase;
        // Solo si toca el rango: un dato viejo que ya estaba mal no frena una corrección de nombre.
        if (cambiaRango && emitio && (primerEmitido < desde || ultimoEmitido > hasta)) {
            throw new GraphQLException("El rango tiene que incluir los cheques ya emitidos (" + primerEmitido + "–" + ultimoEmitido + ").");
        }

        // El correlativo solo va hacia adelante. Lo que llega menor o igual al de la base se ignora sin
        // rechazar: es lo que manda una pantalla abierta desde antes, y aplicarlo repetiría números.
        long siguienteGuardado = chequera.getSiguienteNumero() != null ? chequera.getSiguienteNumero() : desdeBase;
        // Si la base quedó detrás de lo emitido (el retroceso que hacía la versión anterior), se repara acá:
        // medir «hacia adelante» contra ese valor dejaría elegir un número ya usado.
        long siguienteBase = emitio ? Math.max(siguienteGuardado, ultimoEmitido + 1) : siguienteGuardado;
        long siguiente = pedido.getSiguienteNumero() != null && pedido.getSiguienteNumero() > siguienteBase
                ? pedido.getSiguienteNumero() : siguienteBase;
        if (cambiaRango && !emitio && (siguiente < desde || siguiente > hasta)) {
            // Corrige el rango de una chequera que todavía no emitió: el correlativo acompaña al rango nuevo
            // en vez de dejarla agotada o rechazar por el número que quedó en pantalla.
            Long pedida = pedido.getSiguienteNumero();
            siguiente = pedida != null && pedida >= desde && pedida <= hasta ? pedida : desde;
        }
        // Solo si toca el rango o el correlativo: un dato viejo que ya estaba mal no frena lo demás.
        boolean cambiaSiguiente = siguiente != siguienteBase;
        if ((cambiaRango || cambiaSiguiente) && (siguiente < desde || siguiente > hasta + 1)) {
            throw new GraphQLException("El próximo número (" + siguiente + ") queda fuera del rango " + desde + "–" + hasta + ".");
        }

        EstadoChequera estado = pedido.getEstado() != null ? pedido.getEstado() : chequera.getEstado();
        if (siguiente > hasta) {
            estado = EstadoChequera.AGOTADA;   // sin números: una pantalla vieja no la deja activa
        } else if (estado == EstadoChequera.AGOTADA && chequera.getEstado() == EstadoChequera.AGOTADA
                && siguienteGuardado > hastaBase) {
            // Estaba agotada por falta de números y se le amplió el rango: vuelve a servir. La pantalla
            // manda «agotada» porque es lo que tenía cargado, y dejarla así la escondía de la emisión.
            estado = EstadoChequera.ACTIVA;
        }
        if (estado == null) estado = EstadoChequera.ACTIVA;

        if (cambiaRango || cambiaCuenta) {
            rechazarSiSeSuperpone(cuentaPedidaId, desde, hasta, id);
        }

        chequera.setNombre(pedido.getNombre());
        chequera.setFirmantes(pedido.getFirmantes());
        chequera.setRangoDesde((double) desde);
        chequera.setRangoHasta((double) hasta);
        chequera.setSiguienteNumero(siguiente);
        chequera.setEstado(estado);
        // Fecha de retiro, fecha de alta y usuario creador: el desktop no los manda; no se tocan.
        if (pedido.getFechaRetiro() != null) chequera.setFechaRetiro(pedido.getFechaRetiro());
        return chequeraRepository.save(chequera);
    }

    private CuentaBancaria cuenta(Long cuentaId) {
        return cuentaBancariaRepository.findById(cuentaId)
                .orElseThrow(() -> new GraphQLException("Cuenta bancaria no encontrada: " + cuentaId));
    }

    private void rechazarSiSeSuperpone(Long cuentaId, long desde, long hasta, Long exceptoId) {
        List<Chequera> otras = chequeraRepository.findSuperpuestas(cuentaId, (double) desde, (double) hasta, exceptoId);
        if (otras.isEmpty()) return;
        Chequera otra = otras.get(0);
        String nombre = otra.getNombre() != null ? otra.getNombre() : ("#" + otra.getId());
        throw new GraphQLException("El rango " + desde + "–" + hasta + " se superpone con la chequera " + nombre
                + " (" + otra.getRangoDesde().longValue() + "–" + otra.getRangoHasta().longValue() + ").");
    }

    private static void validarRango(long desde, long hasta) {
        if (desde < 1) throw new GraphQLException("El rango tiene que empezar en un número mayor a cero.");
        if (hasta < desde) throw new GraphQLException("El rango «hasta» no puede ser menor que «desde».");
    }

    private static long entero(Double valor, String cual) {
        if (valor == null || valor.isNaN() || valor.isInfinite()) throw new GraphQLException("Falta el rango «" + cual + "».");
        if (valor != Math.rint(valor) || Math.abs(valor) > 9.0e15) {
            throw new GraphQLException("El rango «" + cual + "» tiene que ser un número entero.");
        }
        return valor.longValue();
    }

    private static Long aLong(Double numero) {
        return numero != null ? numero.longValue() : null;
    }

    private static String claveDeCuenta(Long cuentaId) {
        return "CHEQUERAS_CUENTA:" + cuentaId;
    }
}
