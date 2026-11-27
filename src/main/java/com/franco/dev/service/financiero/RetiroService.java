package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.financiero.enums.EstadoRetiro;
import com.franco.dev.repository.financiero.RetiroRepository;
import com.franco.dev.repository.financiero.RetiroSituacion;
import com.franco.dev.repository.financiero.RetiroVerificacionRepository;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@AllArgsConstructor
public class RetiroService extends CrudService<Retiro, RetiroRepository, EmbebedPrimaryKey> {

    private final RetiroRepository repository;
    private final ApplicationEventPublisher publisher;
    private final RetiroVerificacionRepository verificacionRepository;
    private final TesoreriaSecurityService seguridad;

    /** Avisos por webhook (srv #587). Provider: RetiroDetalleService depende de este servicio. */
    private final com.franco.dev.eventos.EventoEmisor eventoEmisor;
    private final org.springframework.beans.factory.ObjectProvider<RetiroDetalleService> retiroDetalleService;
    private final com.franco.dev.repository.financiero.RetiroDetalleRepository retiroDetalleRepository;

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

    public List<Retiro> findByCajaSalidaIdAndSucursalId(Long cajaId, Long sucId) {
        return repository.findByCajaSalidaIdAndSucursalId(cajaId, sucId);
    }

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

    /** Retiros flotantes (replicados del PDV, sin caja mayor asignada); filtro por sucursal, caja y fechas. */
    public Page<Retiro> findFlotantes(Long sucId, Long cajaId, java.time.LocalDateTime desde,
                                      java.time.LocalDateTime hasta, Pageable pageable) {
        return findFlotantes(sucId, cajaId, null, desde, hasta, pageable);
    }

    /**
     * @param cajaVirtualDestinoId la caja mayor donde se van a ingresar. Al rol acotado le deja solo los
     *                             retiros que esa caja puede recibir; para el resto no filtra.
     */
    public Page<Retiro> findFlotantes(Long sucId, Long cajaId, Long cajaVirtualDestinoId,
                                      java.time.LocalDateTime desde, java.time.LocalDateTime hasta,
                                      Pageable pageable) {
        if (seguridad.esSoloCaja()) {
            List<Long> operables = sucursalesDelAcotado(sucId, cajaVirtualDestinoId);
            if (operables.isEmpty()) return Page.empty(pageable);
            return repository.findFlotantesEnSucursales(operables, cajaId, desde, hasta, pageable);
        }
        return repository.findFlotantes(sucId, cajaId, desde, hasta, pageable);
    }

    /** Tope del aviso de procesados: una caja del PDV tiene pocos retiros, y el diálogo muestra cinco. */
    static final int TOPE_INGRESADOS = 50;

    /**
     * Retiros de una caja del PDV que ya se procesaron, con el mismo recorte del rol acotado que
     * {@link #findFlotantes}: el aviso nunca nombra un retiro que esa lista no podría mostrar.
     */
    public List<com.franco.dev.repository.financiero.RetiroIngresadoDto> findIngresadosDeCaja(
            Long cajaId, Long sucId, Long cajaVirtualDestinoId) {
        Pageable tope = org.springframework.data.domain.PageRequest.of(0, TOPE_INGRESADOS);
        if (seguridad.esSoloCaja()) {
            List<Long> operables = sucursalesDelAcotado(sucId, cajaVirtualDestinoId);
            if (operables.isEmpty()) return java.util.Collections.emptyList();
            return conMontos(repository.findIngresadosDeCajaEnSucursales(cajaId, operables, tope));
        }
        return conMontos(repository.findIngresadosDeCaja(cajaId, sucId, tope));
    }

    /**
     * Completa el monto que entró a caja mayor: la verificación acredita lo contado (puede ser otro monto u otra
     * moneda); el poller y el ingreso manual, lo declarado en retiro_detalle. Si hay verificación vigente, sus
     * montos son los únicos que cuentan.
     */
    private List<com.franco.dev.repository.financiero.RetiroIngresadoDto> conMontos(
            List<com.franco.dev.repository.financiero.RetiroIngresadoDto> lista) {
        if (lista.isEmpty()) return lista;
        java.util.Set<Long> ids = new java.util.HashSet<>();
        lista.forEach(r -> ids.add(r.getRetiroId()));
        java.util.Map<String, java.util.Map<Long, Double>> contado = porRetiro(verificacionRepository.sumarContadoVigente(ids));
        java.util.Map<String, java.util.Map<Long, Double>> declarado = porRetiro(retiroDetalleRepository.sumarPorMoneda(ids));
        for (com.franco.dev.repository.financiero.RetiroIngresadoDto r : lista) {
            String k = r.getRetiroId() + "_" + r.getSucursalId();
            java.util.Map<Long, Double> m = contado.containsKey(k) ? contado.get(k) : declarado.get(k);
            if (m == null) continue;
            r.setRetiroGs(m.get(1L));
            r.setRetiroRs(m.get(2L));
            r.setRetiroDs(m.get(3L));
        }
        return lista;
    }

    /** {@code [retiroId, sucursalId, monedaId, suma]} → clave "retiro_sucursal" → moneda → suma. */
    private static java.util.Map<String, java.util.Map<Long, Double>> porRetiro(List<Object[]> filas) {
        java.util.Map<String, java.util.Map<Long, Double>> m = new java.util.HashMap<>();
        for (Object[] f : filas) {
            if (f[2] == null || f[3] == null) continue;
            m.computeIfAbsent(f[0] + "_" + f[1], x -> new java.util.HashMap<>())
                    .merge(((Number) f[2]).longValue(), ((Number) f[3]).doubleValue(), Double::sum);
        }
        return m;
    }

    /**
     * El rol acotado solo ve los retiros de las sucursales de sus cajas. Vacío es «ninguna»: quien llama
     * corta ahí, nunca llega una lista vacía ni un null a la consulta.
     */
    private List<Long> sucursalesDelAcotado(Long sucId, Long cajaVirtualDestinoId) {
        List<Long> operables = seguridad.sucursalesOperablesIds();
        if (cajaVirtualDestinoId != null) {
            // Lo que no cubre la caja destino lo rechazaría la verificación: no se lista.
            operables = new java.util.ArrayList<>(operables);
            operables.retainAll(seguridad.sucursalesDeCajaIds(cajaVirtualDestinoId));
        }
        if (sucId != null) operables = operables.contains(sucId) ? java.util.Collections.singletonList(sucId)
                : java.util.Collections.emptyList();
        return operables;
    }

    @Override
    public Retiro save(Retiro entity) {
        Retiro e = super.save(entity);
        publisher.publishEvent(new com.franco.dev.fmc.event.RetiroRealizadoEvent(this, e));
        return e;
    }

    /**
     * Cancela ({@code cancelar = true}) o habilita ({@code false}) un retiro. El pedido dice cómo tiene
     * que quedar, así que repetirlo no cambia nada: antes era un interruptor y un reintento o un doble
     * clic deshacía la cancelación (issue #376).
     *
     * <p>Sin el argumento —un desktop anterior— significa cancelar y nunca habilita: sobre un retiro ya
     * cancelado se rechaza, porque ese pedido puede ser tanto un «Habilitar» como la repetición de un
     * «Cancelar», y adivinar mal vuelve a descontar la plata de la caja.</p>
     *
     * <p>El retiro se toma con el mismo lock que la verificación y el ingreso a caja mayor, y su estado
     * se lee de la base. Solo se cancela un retiro que todavía no entró a la caja mayor: cancelado deja
     * de descontar de la caja del PDV ({@code PdvCajaService.generarBalance} lo ignora) y, si además
     * está acreditado en la caja mayor, la plata queda contada dos veces.</p>
     *
     * <p>No recalcula ningún balance, y la filial hace lo mismo cuando el estado le llega por
     * replicación. No pasa por {@link #save}: su override publica RetiroRealizadoEvent («RETIRO
     * REALIZADO»), y cancelar no es realizar un retiro.</p>
     */
    @Transactional
    public Boolean cancelarRetiro(Long id, Long sucId, Boolean cancelar) {
        repository.lockByIdAndSucursalId(id, sucId)
                .orElseThrow(() -> new GraphQLException("Retiro no encontrado: " + id + "/" + sucId));
        RetiroSituacion s = repository.findSituacion(id, sucId)
                .orElseThrow(() -> new GraphQLException("Retiro no encontrado: " + id + "/" + sucId));

        if (Boolean.FALSE.equals(cancelar)) {
            if (s.estaCancelado()) {
                repository.marcarConcluido(id, sucId);
                emitirCancelacion("retiro.reactivado", id, sucId);
            }
            return true;
        }
        if (s.estaCancelado()) {
            if (cancelar == null) {
                throw new GraphQLException("El retiro #" + id + " ya está cancelado. Si querías habilitarlo, actualizá el sistema.");
            }
            return true;
        }
        if (s.getMovimientoCajaVirtualId() != null
                || esVerificado(s.getEstado())
                || verificacionRepository.findVigente(id, sucId).isPresent()) {
            throw new GraphQLException("El retiro #" + id + " ya entró a la caja mayor: anulá primero su verificación.");
        }
        if (s.getCajaVirtualId() != null) {
            throw new GraphQLException("El retiro #" + id + " ya tiene una caja mayor asignada: no se puede cancelar.");
        }
        // Lista blanca: un retiro EN_PROCESO todavía está abierto en el PDV, y habilitarlo después lo
        // dejaría CONCLUIDO y verificable a medio cargar.
        if (s.getEstado() != null && s.getEstado() != EstadoRetiro.CONCLUIDO) {
            throw new GraphQLException("El retiro #" + id + " está en estado " + s.getEstado() + ": no se puede cancelar.");
        }
        repository.marcarCancelado(id, sucId);
        emitirCancelacion("retiro.cancelado", id, sucId);
        return true;
    }

    /** Aviso por webhook (srv #587), en la TX de la cancelacion; si falla el INSERT, la cancelacion sigue. */
    private void emitirCancelacion(String tipo, Long id, Long sucId) {
        if (eventoEmisor == null || !eventoEmisor.encendido()) return;
        Retiro r = repository.findByIdAndSucursalId(id, sucId);
        if (r == null) return;
        RetiroDetalleService rds = retiroDetalleService != null ? retiroDetalleService.getIfAvailable() : null;
        java.util.List<com.franco.dev.domain.financiero.RetiroDetalle> det = rds != null ? rds.findByRetiroId(id, sucId) : null;
        Long usuario = null;
        try {
            usuario = seguridad.currentUsuario() != null ? seguridad.currentUsuario().getId() : null;
        } catch (Exception ignored) {
        }
        eventoEmisor.emitirProyeccionDe(sucId, tipo, "retiro.cancelado".equals(tipo) ? "media" : "baja", usuario,
                "retiro/" + sucId + "/" + id,
                () -> com.franco.dev.eventos.EventosCentral.retiro(r, det),
                () -> com.franco.dev.eventos.EventosCentral.atributosRetiro(r, det));
    }

    private static boolean esVerificado(EstadoRetiro estado) {
        return estado == EstadoRetiro.VERIFICADO_CONCLUIDO_SIN_PROBLEMA
                || estado == EstadoRetiro.VERIFICADO_CONCLUIDO_CON_PROBLEMA;
    }
}
