package com.franco.dev.service.productos;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.PrecioEspecialSucursal;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.graphql.productos.input.PrecioEspecialSucursalInput;
import com.franco.dev.repository.productos.PrecioEspecialSucursalRepository;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.utilitarios.DateUtils;
import graphql.GraphQLException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Alta, edicion y corte de precios especiales por sucursal (SPEC-PRECIO-ESPECIAL-SUCURSAL.md).
 * <p>
 * La superposicion se valida aca y no en la base: dos especiales activos del mismo precio y
 * sucursal no pueden compartir un dia. Un alta con varias sucursales es todo o nada: se valida
 * todo antes del primer save. "Hoy" es -03 fijo, igual que PrecioEspecialLector del filial.
 */
@Service
public class PrecioEspecialSucursalService {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneOffset ZONA = ZoneOffset.ofHours(-3);

    private final PrecioEspecialSucursalRepository repository;
    private final PrecioPorSucursalService precioService;
    private final SucursalService sucursalService;

    public PrecioEspecialSucursalService(PrecioEspecialSucursalRepository repository,
                                         PrecioPorSucursalService precioService,
                                         SucursalService sucursalService) {
        this.repository = repository;
        this.precioService = precioService;
        this.sucursalService = sucursalService;
    }

    @Transactional
    public List<PrecioEspecialSucursal> crear(PrecioEspecialSucursalInput input, Usuario autor) {
        if (input == null) throw new GraphQLException("Faltan los datos del precio especial.");
        validarPrecio(input.getPrecio());
        LocalDate desde = DateUtils.stringToLocalDate(input.getFechaDesde());
        LocalDate hasta = DateUtils.stringToLocalDate(input.getFechaHasta());
        validarRango(desde, hasta);
        // findById(null) de CrudService devuelve null, no Optional.empty()
        PrecioPorSucursal precio = input.getPrecioId() == null ? null
                : precioService.findById(input.getPrecioId()).orElse(null);
        if (precio == null) throw new GraphQLException("El precio " + input.getPrecioId() + " no existe.");
        Set<Long> ids = new LinkedHashSet<>(input.getSucursalIds() != null ? input.getSucursalIds() : Collections.emptyList());
        ids.remove(null);
        if (ids.isEmpty()) throw new GraphQLException("Elegí al menos una sucursal.");

        List<Sucursal> sucursales = new ArrayList<>();
        for (Long sucursalId : ids) {
            if (sucursalId == 0L) throw new GraphQLException("El central (sucursal 0) no vende: no lleva precio especial.");
            Sucursal s = sucursalService.findById(sucursalId).orElse(null);
            if (s == null) throw new GraphQLException("La sucursal " + sucursalId + " no existe.");
            validarSinSuperposicion(precio.getId(), s, desde, hasta, null);
            sucursales.add(s);
        }

        List<PrecioEspecialSucursal> creados = new ArrayList<>();
        for (Sucursal s : sucursales) {
            PrecioEspecialSucursal e = new PrecioEspecialSucursal();
            e.setPrecioPorSucursal(precio);
            e.setSucursal(s);
            e.setPrecio(input.getPrecio());
            e.setFechaDesde(desde);
            e.setFechaHasta(hasta);
            e.setActivo(true);
            e.setUsuario(autor);
            e.setCreadoEn(LocalDateTime.now(ZONA));
            creados.add(repository.save(e));
        }
        return creados;
    }

    @Transactional
    public PrecioEspecialSucursal editar(Long id, Double precio, String fechaDesde, String fechaHasta, Usuario autor) {
        PrecioEspecialSucursal e = buscar(id);
        if (!Boolean.TRUE.equals(e.getActivo())) {
            throw new GraphQLException("El precio especial " + id + " está cortado: cargá uno nuevo.");
        }
        validarPrecio(precio);
        LocalDate desde = DateUtils.stringToLocalDate(fechaDesde);
        LocalDate hasta = DateUtils.stringToLocalDate(fechaHasta);
        validarRango(desde, hasta);
        validarSinSuperposicion(e.getPrecioPorSucursal().getId(), e.getSucursal(), desde, hasta, e.getId());
        e.setPrecio(precio);
        e.setFechaDesde(desde);
        e.setFechaHasta(hasta);
        e.setUsuario(autor);
        return repository.save(e);
    }

    @Transactional
    public PrecioEspecialSucursal cortar(Long id, Usuario autor) {
        PrecioEspecialSucursal e = buscar(id);
        if (Boolean.FALSE.equals(e.getActivo())) return e;
        e.setActivo(false);
        e.setUsuario(autor);
        return repository.save(e);
    }

    public List<PrecioEspecialSucursal> porPrecio(Long precioId) {
        return repository.findByPrecioPorSucursalIdOrderByIdDesc(precioId);
    }

    public Page<PrecioEspecialSucursal> filtrar(Long sucursalId, String texto, Boolean soloVigentes, int page, int size) {
        String t = texto != null && !texto.trim().isEmpty() ? texto.trim().toUpperCase() : null;
        return repository.filtrar(sucursalId, t, Boolean.TRUE.equals(soloVigentes), LocalDate.now(ZONA), PageRequest.of(page, size));
    }

    /** Dos rangos de dias inclusivos (null = abierto) comparten al menos un dia. */
    static boolean seSuperponen(LocalDate d1, LocalDate h1, LocalDate d2, LocalDate h2) {
        boolean empiezaAntesDeQueTermineElOtro = d1 == null || h2 == null || !d1.isAfter(h2);
        boolean elOtroEmpiezaAntesDeQueTermine = d2 == null || h1 == null || !d2.isAfter(h1);
        return empiezaAntesDeQueTermineElOtro && elOtroEmpiezaAntesDeQueTermine;
    }

    private void validarSinSuperposicion(Long precioId, Sucursal sucursal, LocalDate desde, LocalDate hasta, Long excluirId) {
        for (PrecioEspecialSucursal otro : repository.findByPrecioPorSucursalIdAndSucursalIdAndActivoTrue(precioId, sucursal.getId())) {
            if (excluirId != null && excluirId.equals(otro.getId())) continue;
            if (seSuperponen(desde, hasta, otro.getFechaDesde(), otro.getFechaHasta())) {
                throw new GraphQLException("La sucursal " + sucursal.getNombre() + " ya tiene un precio especial ("
                        + otro.getPrecio() + ") " + rango(otro.getFechaDesde(), otro.getFechaHasta())
                        + ". Cortalo o elegí otras fechas.");
            }
        }
    }

    private static String rango(LocalDate desde, LocalDate hasta) {
        return (desde != null ? "desde el " + desde.format(DIA) : "desde siempre")
                + (hasta != null ? " hasta el " + hasta.format(DIA) : " sin fecha de fin");
    }

    private static void validarPrecio(Double precio) {
        if (precio == null || precio <= 0) throw new GraphQLException("El precio especial tiene que ser mayor a cero.");
    }

    private static void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new GraphQLException("La fecha hasta no puede ser anterior a la fecha desde.");
        }
    }

    private PrecioEspecialSucursal buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> new GraphQLException("El precio especial " + id + " no existe."));
    }
}
