package com.franco.dev.service.administrativo;

import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.administrativo.dto.JornadaHistorial;
import com.franco.dev.domain.administrativo.dto.JornadaResumen;
import com.franco.dev.domain.administrativo.enums.EstadoJornada;
import com.franco.dev.domain.administrativo.enums.Turno;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.repository.administrativo.JornadaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@AllArgsConstructor
public class JornadaService extends CrudService<Jornada, JornadaRepository, EmbebedPrimaryKey> {

    private final JornadaRepository repository;

    @Override
    public JornadaRepository getRepository() {
        return repository;
    }

    public List<Jornada> findByUsuarioId(Long usuarioId) {
        return repository.findByUsuarioId(usuarioId);
    }

    /** Pagina las jornadas de un usuario, de la mas reciente a la mas antigua. */
    public List<Jornada> findByUsuarioId(Long usuarioId, Integer page, Integer size) {
        int pagina = page != null && page >= 0 ? page : 0;
        int tamano = size != null && size > 0 ? size : 20;
        return repository.findByUsuarioIdOrderByFechaDescIdDesc(
                usuarioId, PageRequest.of(pagina, tamano));
    }

    public List<Jornada> findByUsuarioIdAndFechaRange(Long usuarioId, String fechaInicio, String fechaFin) {
        return repository.findByUsuarioIdAndFechaRange(usuarioId, fechaInicio, fechaFin);
    }

    public List<Jornada> findByFechaRange(String fechaInicio, String fechaFin) {
        return repository.findByFechaRange(fechaInicio, fechaFin);
    }

    /**
     * Historial de marcaciones: una pagina de jornadas y el resumen de todo lo que cumple el filtro.
     *
     * Filtra, pagina y suma en la base. Antes el desktop pedia el rango entero, filtraba turno y sucursal en
     * memoria y sumaba el resumen el mismo: un mes eran cientos de filas viajando para mostrar quince.
     */
    public JornadaHistorial findHistorial(Long usuarioId, String fechaInicio, String fechaFin, Turno turno,
            Long sucursalEntradaId, Long sucursalSalidaId, Integer page, Integer size) {
        int pagina = page != null && page >= 0 ? page : 0;
        int tamano = size != null && size > 0 ? size : 15;
        boolean porUsuario = usuarioId != null;
        boolean porTurno = turno != null;
        boolean porEntrada = sucursalEntradaId != null;
        boolean porSalida = sucursalSalidaId != null;
        // Los valores de relleno no filtran nada: su bandera va en false.
        Long usuario = porUsuario ? usuarioId : -1L;
        Turno turnoFiltro = porTurno ? turno : Turno.DIA;
        Long entrada = porEntrada ? sucursalEntradaId : -1L;
        Long salida = porSalida ? sucursalSalidaId : -1L;

        Page<Jornada> jornadas = repository.findHistorial(fechaInicio, fechaFin, porUsuario, usuario, porTurno,
                turnoFiltro, porEntrada, entrada, porSalida, salida, PageRequest.of(pagina, tamano));
        List<Object[]> totales = repository.resumenHistorial(fechaInicio, fechaFin, porUsuario, usuario, porTurno,
                turnoFiltro, porEntrada, entrada, porSalida, salida);
        Object[] fila = totales.isEmpty() ? new Object[] { 0, 0, 0, 0 } : totales.get(0);
        JornadaResumen resumen = new JornadaResumen(entero(fila[0]), entero(fila[1]), entero(fila[2]), entero(fila[3]));
        return new JornadaHistorial(jornadas, resumen);
    }

    private static Integer entero(Object valor) {
        return valor == null ? 0 : ((Number) valor).intValue();
    }

    private static Stream<Marcacion> marcacionesDe(Jornada jornada) {
        return Stream.of(jornada.getMarcacionEntrada(), jornada.getMarcacionSalidaAlmuerzo(),
                jornada.getMarcacionEntradaAlmuerzo(), jornada.getMarcacionSalida()).filter(Objects::nonNull);
    }

    /** Atraso de la entrada mas el del retorno del almuerzo. */
    public int minutosLlegadaTardiaTotal(Jornada jornada) {
        long entrada = jornada.getMinutosLlegadaTardia() != null ? jornada.getMinutosLlegadaTardia() : 0L;
        long almuerzo = jornada.getMinutosLlegadaTardiaAlmuerzo() != null ? jornada.getMinutosLlegadaTardiaAlmuerzo() : 0L;
        return (int) (entrada + almuerzo);
    }

    /** La mayor distancia a la sucursal entre las marcaciones de la jornada; null si ninguna la informo. */
    public Integer distanciaMaximaMetros(Jornada jornada) {
        return marcacionesDe(jornada).map(Marcacion::getDistanciaSucursalMetros).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(null);
    }

    /** Alguna marcacion guardo coordenadas. Un 0,0 es un GPS sin lectura, no un lugar. */
    public boolean tieneUbicacion(Jornada jornada) {
        return marcacionesDe(jornada).anyMatch(m -> m.getLatitud() != null && m.getLongitud() != null
                && !(m.getLatitud().compareTo(BigDecimal.ZERO) == 0 && m.getLongitud().compareTo(BigDecimal.ZERO) == 0));
    }

    /**
     * Se puede ajustar a 8 horas una jornada que quedo incompleta, y tambien una que cerro mal: cuando alguien
     * olvida marcar la salida, la entrada del dia siguiente le cierra la jornada y le deja horas extras que no
     * hizo. Esa queda en NORMAL.
     */
    public boolean puedeAjustarA8Horas(Jornada jornada) {
        if (jornada.getEstado() == EstadoJornada.INCOMPLETO) {
            return true;
        }
        return jornada.getMinutosExtras() != null && jornada.getMinutosExtras() > 0;
    }

    public List<Jornada> findByUsuarioIdAndFecha(Long usuarioId, String fecha) {
        return repository.findByUsuarioIdAndFecha(usuarioId, fecha);
    }

    public Optional<Jornada> findByMarcacionEntradaId(Long id) {
        return repository.findByMarcacionEntradaId(id);
    }

    public Optional<Jornada> findIncompletaSinSalidaNocturnaParaSalida(Long usuarioId, java.time.LocalDate fechaSalida) {
        List<Jornada> jornadas = repository.findIncompletasSinSalidaNocturnasByUsuarioId(usuarioId);
        if (jornadas == null || jornadas.isEmpty()) {
            return Optional.empty();
        }
        Optional<Jornada> delMismoDia = jornadas.stream()
                .filter(j -> j.getFecha().equals(fechaSalida))
                .findFirst();
        if (delMismoDia.isPresent()) {
            return delMismoDia;
        }
        return jornadas.stream()
                .filter(j -> j.getFecha().isBefore(fechaSalida))
                .findFirst();
    }

    /**
     * Jornada del día con entrada y sin salida definitiva, cualquier sucursal (p. ej. cloud con sucursal 0).
     */
    public Optional<Jornada> findJornadaAbiertaConEntradaSinSalida(Long usuarioId, java.time.LocalDate fecha) {
        List<Jornada> jornadas = repository.findAbiertasConEntradaSinSalidaByUsuarioIdAndFecha(
                usuarioId, fecha.toString());
        if (jornadas == null || jornadas.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(jornadas.get(0));
    }

    public Optional<Jornada> findJornadaAbiertaSinEntrada(Long usuarioId, java.time.LocalDate fecha) {
        List<Jornada> jornadas = repository.findAbiertasSinEntradaByUsuarioIdAndFecha(
                usuarioId, fecha.toString());
        if (jornadas == null || jornadas.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(jornadas.get(0));
    }

    public Jornada ajustarA8Horas(Long id, Long sucursalId, String observacion) {
        Optional<Jornada> optionalJornada = repository.findById(new EmbebedPrimaryKey(id, sucursalId));
        if (optionalJornada.isPresent()) {
            Jornada jornada = optionalJornada.get();
            jornada.setMinutosTrabajados(480L);
            jornada.setMinutosExtras(0L);
            jornada.setEstado(com.franco.dev.domain.administrativo.enums.EstadoJornada.NORMAL);
            if (observacion != null) {
                jornada.setObservacion(observacion);
            }
            return repository.save(jornada);
        }
        return null;
    }

    public Jornada guardarObservacion(Long id, Long sucursalId, String observacion) {
        Optional<Jornada> optionalJornada = repository.findById(new EmbebedPrimaryKey(id, sucursalId));
        if (optionalJornada.isPresent()) {
            Jornada jornada = optionalJornada.get();
            jornada.setObservacion(observacion);
            return repository.save(jornada);
        }
        return null;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
    public Jornada save(Jornada entity) {
        if (entity.getId() == null) {
            Long lastId = repository.findMaxId(entity.getSucursalId());
            long newId = (lastId == null ? 0L : lastId) + 1L;
            if (newId % 2 == 0) {
                newId++;
            }
            entity.setId(newId);
        }
        Jornada e = super.save(entity);
        return e;
    }
}
