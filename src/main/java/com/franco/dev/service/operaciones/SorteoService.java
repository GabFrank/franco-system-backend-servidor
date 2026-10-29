package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Sorteo;
import com.franco.dev.domain.operaciones.SorteoPremio;
import com.franco.dev.domain.operaciones.SorteoSucursal;
import com.franco.dev.domain.operaciones.enums.ModoParticipacionSorteo;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.operaciones.input.SorteoInput;
import com.franco.dev.graphql.operaciones.input.SorteoPremioInput;
import com.franco.dev.repository.operaciones.SorteoRepository;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.utilitarios.DateUtils;
import graphql.GraphQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Alta, edicion y corte de sorteos para clientes. Solo el central escribe; las filiales los leen por
 * replicacion. "Hoy" es -03 fijo. Un sorteo con participaciones no puede cambiar las reglas de juego
 * (monto, modo, tope, tarjeta, sucursales): hay que cortarlo y crear uno nuevo.
 */
@Service
public class SorteoService {

    static final ZoneOffset ZONA = ZoneOffset.ofHours(-3);

    private final SorteoRepository repository;
    private final SucursalService sucursalService;
    private final ProductoService productoService;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    public SorteoService(SorteoRepository repository, SucursalService sucursalService,
                         ProductoService productoService, JdbcTemplate jdbcTemplate) {
        this(repository, sucursalService, productoService, jdbcTemplate, Clock.system(ZONA));
    }

    SorteoService(SorteoRepository repository, SucursalService sucursalService,
                  ProductoService productoService, JdbcTemplate jdbcTemplate, Clock clock) {
        this.repository = repository;
        this.sucursalService = sucursalService;
        this.productoService = productoService;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional
    public Sorteo guardar(SorteoInput input, Usuario autor) {
        if (input == null) throw new GraphQLException("Faltan los datos del sorteo.");
        Sorteo existente = null;
        if (input.getId() != null) {
            existente = repository.findById(input.getId())
                    .orElseThrow(() -> new GraphQLException("El sorteo " + input.getId() + " no existe."));
            if (!Boolean.TRUE.equals(existente.getActivo())) {
                throw new GraphQLException("El sorteo está cortado: creá uno nuevo.");
            }
        }

        String nombre = input.getNombre() != null ? input.getNombre().trim() : "";
        if (nombre.isEmpty()) throw new GraphQLException("El nombre del sorteo es obligatorio.");
        if (input.getMontoMinimo() == null || input.getMontoMinimo().isNaN()
                || input.getMontoMinimo().isInfinite() || input.getMontoMinimo() <= 0) {
            throw new GraphQLException("El monto mínimo tiene que ser mayor a cero.");
        }
        ModoParticipacionSorteo modo = parseModo(input.getModoParticipacion());
        if (modo == ModoParticipacionSorteo.CANTIDAD_FIJA
                && (input.getCantidadFija() == null || input.getCantidadFija() < 1)) {
            throw new GraphQLException("La cantidad fija de tickets tiene que ser al menos 1.");
        }
        Integer cantidadFija = modo == ModoParticipacionSorteo.CANTIDAD_FIJA ? input.getCantidadFija() : null;
        if (input.getTopePorVenta() != null && input.getTopePorVenta() < 1) {
            throw new GraphQLException("El tope de tickets por venta tiene que ser al menos 1.");
        }
        LocalDate desde = DateUtils.stringToLocalDate(input.getFechaDesde());
        LocalDate hasta = DateUtils.stringToLocalDate(input.getFechaHasta());
        LocalDate fechaSorteo = DateUtils.stringToLocalDate(input.getFechaSorteo());
        if (fechaSorteo == null) throw new GraphQLException("Falta la fecha del sorteo.");
        LocalDate desdeEfectivo = desde != null ? desde : (existente != null ? existente.getFechaDesde() : null);
        if (desdeEfectivo != null && hasta != null && hasta.isBefore(desdeEfectivo)) {
            throw new GraphQLException("La fecha hasta no puede ser anterior a la fecha desde.");
        }
        boolean todas = Boolean.TRUE.equals(input.getTodasSucursales());
        Set<Long> sucursalIds = new LinkedHashSet<>();
        if (!todas) {
            if (input.getSucursalIds() != null) sucursalIds.addAll(input.getSucursalIds());
            sucursalIds.remove(null);
            if (sucursalIds.isEmpty()) throw new GraphQLException("Elegí al menos una sucursal.");
            for (Long sucursalId : sucursalIds) {
                if (sucursalId == 0L) throw new GraphQLException("El central (sucursal 0) no vende: no lleva sorteos.");
                if (sucursalService.findById(sucursalId).orElse(null) == null) {
                    throw new GraphQLException("La sucursal " + sucursalId + " no existe.");
                }
            }
        }
        List<SorteoPremio> premios = armarPremios(input.getPremios());
        Long idExcluido = existente != null ? existente.getId() : -1L;
        if (repository.existsByNombreIgnoreCaseAndActivoTrueAndIdNot(nombre, idExcluido)) {
            throw new GraphQLException("Ya hay un sorteo activo con el nombre \"" + nombre + "\".");
        }

        BigDecimal monto = BigDecimal.valueOf(input.getMontoMinimo());
        boolean incluyeTarjeta = !Boolean.FALSE.equals(input.getIncluyeTarjeta());
        Sorteo s;
        if (existente == null) {
            s = new Sorteo();
            s.setActivo(true);
            s.setCreadoEn(LocalDateTime.now(clock));
        } else {
            s = existente;
            boolean cambiaReglas = s.getMontoMinimo().compareTo(monto) != 0
                    || s.getModoParticipacion() != modo
                    || !Objects.equals(s.getCantidadFija(), cantidadFija)
                    || !Objects.equals(s.getTopePorVenta(), input.getTopePorVenta())
                    || !Objects.equals(s.getIncluyeTarjeta(), incluyeTarjeta)
                    || !Objects.equals(s.getTodasSucursales(), todas)
                    || !idsActuales(s).equals(sucursalIds);
            if (cambiaReglas && tieneParticipaciones(s.getId())) {
                throw new GraphQLException("El sorteo ya tiene participaciones: no se pueden cambiar el monto, el modo ni las sucursales. Cortalo y creá uno nuevo.");
            }
        }
        s.setNombre(nombre);
        s.setDescripcion(input.getDescripcion() != null && !input.getDescripcion().trim().isEmpty()
                ? input.getDescripcion().trim() : null);
        s.setMontoMinimo(monto);
        s.setModoParticipacion(modo);
        s.setCantidadFija(cantidadFija);
        s.setTopePorVenta(input.getTopePorVenta());
        s.setIncluyeTarjeta(incluyeTarjeta);
        s.setFechaDesde(desde != null ? desde : (existente == null ? LocalDate.now(clock) : s.getFechaDesde()));
        s.setFechaHasta(hasta);
        s.setFechaSorteo(fechaSorteo);
        s.setTodasSucursales(todas);
        s.setUsuario(autor);

        s.getPremios().clear();
        for (SorteoPremio p : premios) {
            p.setSorteo(s);
            s.getPremios().add(p);
        }
        // Diff y no clear()+add: Hibernate inserta antes de borrar huerfanos y chocaria con uq_sorteo_sucursal.
        s.getSucursales().removeIf(ss -> !sucursalIds.contains(ss.getSucursalId()));
        Set<Long> presentes = idsActuales(s);
        for (Long sucursalId : sucursalIds) {
            if (presentes.contains(sucursalId)) continue;
            SorteoSucursal ss = new SorteoSucursal();
            ss.setSorteo(s);
            ss.setSucursalId(sucursalId);
            s.getSucursales().add(ss);
        }
        return repository.save(s);
    }

    @Transactional
    public Sorteo cortar(Long id, Usuario autor) {
        Sorteo s = repository.findById(id).orElseThrow(() -> new GraphQLException("El sorteo " + id + " no existe."));
        if (Boolean.FALSE.equals(s.getActivo())) return s;
        s.setActivo(false);
        s.setUsuario(autor);
        return repository.save(s);
    }

    public Sorteo buscar(Long id) {
        return repository.findById(id).orElse(null);
    }

    public Page<Sorteo> filtrar(String texto, Boolean soloVigentes, int page, int size) {
        String t = texto != null && !texto.trim().isEmpty() ? texto.trim().toUpperCase() : null;
        return repository.filtrar(t, Boolean.TRUE.equals(soloVigentes), LocalDate.now(clock), PageRequest.of(page, size));
    }

    public boolean tieneParticipaciones(Long sorteoId) {
        Boolean r = jdbcTemplate.queryForObject(
                "select exists(select 1 from operaciones.sorteo_participacion where sorteo_id = ?)",
                Boolean.class, sorteoId);
        return Boolean.TRUE.equals(r);
    }

    private static Set<Long> idsActuales(Sorteo s) {
        Set<Long> ids = new HashSet<>();
        for (SorteoSucursal ss : s.getSucursales()) ids.add(ss.getSucursalId());
        return ids;
    }

    private static ModoParticipacionSorteo parseModo(String modo) {
        try {
            if (modo != null) return ModoParticipacionSorteo.valueOf(modo.trim());
        } catch (IllegalArgumentException ignored) {
            // cae al error de abajo
        }
        throw new GraphQLException("El modo de participación no es válido.");
    }

    private List<SorteoPremio> armarPremios(List<SorteoPremioInput> inputs) {
        if (inputs == null || inputs.isEmpty()) throw new GraphQLException("Cargá al menos un premio.");
        List<SorteoPremio> premios = new ArrayList<>();
        int orden = 1;
        for (SorteoPremioInput in : inputs) {
            String descripcion = in != null && in.getDescripcion() != null ? in.getDescripcion().trim() : "";
            Long productoId = in != null ? in.getProductoId() : null;
            if (descripcion.isEmpty() && productoId == null) {
                throw new GraphQLException("Cada premio necesita una descripción o un producto.");
            }
            if (in.getCantidad() == null || in.getCantidad() < 1) {
                throw new GraphQLException("La cantidad de cada premio tiene que ser al menos 1.");
            }
            SorteoPremio p = new SorteoPremio();
            p.setOrden(orden++);
            p.setDescripcion(descripcion.isEmpty() ? null : descripcion);
            p.setCantidad(in.getCantidad());
            if (productoId != null) {
                Producto producto = productoService.findById(productoId).orElse(null);
                if (producto == null) throw new GraphQLException("El producto " + productoId + " no existe.");
                p.setProducto(producto);
            }
            premios.add(p);
        }
        return premios;
    }
}
