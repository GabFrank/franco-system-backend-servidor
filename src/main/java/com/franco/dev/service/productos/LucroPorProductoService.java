package com.franco.dev.service.productos;

import com.franco.dev.domain.operaciones.dto.LucroPorProductosDto;
import com.franco.dev.repository.productos.ProductoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

/**
 * Reporte de lucro por producto (#611).
 *
 * <p>El cálculo es una sola consulta para todas las sucursales pedidas. La lista paginada, además,
 * guarda el resultado unos minutos: el desktop pide cada página por separado y, sin caché, cada
 * cambio de página recalculaba el período entero para devolver 20 filas.
 */
@Service
public class LucroPorProductoService {

    /** Cuánto vale un resultado guardado. Pasado ese tiempo, la página siguiente recalcula. */
    static final Duration VIGENCIA = Duration.ofMinutes(3);

    /** Combinaciones de filtros guardadas a la vez; cada una pesa del orden de 1 MB. */
    static final int MAXIMO_GUARDADOS = 10;

    /** Relleno para un {@code IN} que no filtra: Hibernate no acepta listas vacías. */
    private static final List<Long> SIN_FILTRO = Collections.singletonList(-1L);

    private final ProductoRepository repository;
    private final Clock reloj;

    /** Orden de inserción: al pasar el máximo se descarta el más viejo. */
    private final LinkedHashMap<List<Object>, Guardado> guardados = new LinkedHashMap<>();

    @Autowired
    public LucroPorProductoService(ProductoRepository repository) {
        this(repository, Clock.systemUTC());
    }

    LucroPorProductoService(ProductoRepository repository, Clock reloj) {
        this.repository = repository;
        this.reloj = reloj;
    }

    /**
     * Lucro por producto, ordenado por total de venta de mayor a menor. Siempre recalcula.
     *
     * <p>Una lista de sucursales nula, vacía o que solo trae {@code null} (lo que manda «Todas» en el
     * desktop) no filtra por sucursal.
     */
    public List<LucroPorProductosDto> calcular(String inicio, String fin, List<Long> sucursalIdList,
            List<Long> usuarioIdList, List<Long> productoIdList, Long subfamiliaId, Long familiaId) {
        List<Long> sucursales = sinNulos(sucursalIdList);
        List<Long> usuarios = sinNulos(usuarioIdList);
        List<Long> productos = sinNulos(productoIdList);

        List<LucroPorProductosDto> result = new ArrayList<>(repository.findLucroPorProducto(
                sucursales.isEmpty() ? SIN_FILTRO : sucursales,
                stringToDate(inicio), stringToDate(fin),
                usuarios.isEmpty() ? SIN_FILTRO : usuarios,
                productos.isEmpty() ? SIN_FILTRO : productos,
                subfamiliaId, familiaId,
                !sucursales.isEmpty(), !usuarios.isEmpty(), !productos.isEmpty()));

        result.forEach(LucroPorProductoService::calcularDerivados);
        result.sort((a, b) -> b.getTotalVenta().compareTo(a.getTotalVenta()));
        return result;
    }

    /**
     * Igual que {@link #calcular}, pero reutiliza un resultado de hace menos de {@link #VIGENCIA} con
     * los mismos filtros. Con {@code refrescar} recalcula siempre y reemplaza lo guardado: es el caso
     * de la primera página, que el desktop pide al tocar «Filtrar».
     *
     * <p>La lista devuelta es de solo lectura y la comparten los pedidos con los mismos filtros.
     */
    public List<LucroPorProductosDto> calcularConCache(String inicio, String fin, List<Long> sucursalIdList,
            List<Long> usuarioIdList, List<Long> productoIdList, Long subfamiliaId, Long familiaId,
            boolean refrescar) {
        List<Object> clave = Arrays.asList(inicio, fin, ordenados(sucursalIdList), ordenados(usuarioIdList),
                ordenados(productoIdList), subfamiliaId, familiaId);
        return obtener(clave, refrescar, () -> calcular(inicio, fin, sucursalIdList, usuarioIdList,
                productoIdList, subfamiliaId, familiaId));
    }

    /**
     * El cálculo corre fuera del lock: tarda segundos y no debe frenar otros pedidos. Si dos pedidos
     * iguales llegan juntos, los dos calculan y queda el último.
     */
    List<LucroPorProductosDto> obtener(List<Object> clave, boolean refrescar,
            Supplier<List<LucroPorProductosDto>> calculo) {
        if (!refrescar) {
            synchronized (guardados) {
                Guardado g = guardados.get(clave);
                if (g != null && reloj.instant().isBefore(g.venceEn)) {
                    return g.lista;
                }
            }
        }
        List<LucroPorProductosDto> lista = Collections.unmodifiableList(calculo.get());
        synchronized (guardados) {
            Instant ahora = reloj.instant();
            guardados.values().removeIf(g -> !ahora.isBefore(g.venceEn));
            guardados.remove(clave);
            guardados.put(clave, new Guardado(lista, ahora.plus(VIGENCIA)));
            Iterator<List<Object>> masViejo = guardados.keySet().iterator();
            while (guardados.size() > MAXIMO_GUARDADOS) {
                masViejo.next();
                masViejo.remove();
            }
        }
        return lista;
    }

    int cantidadGuardados() {
        synchronized (guardados) {
            return guardados.size();
        }
    }

    /** Costo unitario, venta media, lucro, margen y porcentaje a partir de las sumas de la consulta. */
    static void calcularDerivados(LucroPorProductosDto dto) {
        if (dto.getCantidad() == null || dto.getCantidad() <= 0) {
            dto.setCostoUnitario(0.0);
            dto.setVentaMedia(0.0);
            dto.setLucro(0.0);
            dto.setMargen(0.0);
            dto.setPercent(0.0);
            return;
        }
        dto.setCostoUnitario(dto.getCostoTotal() / dto.getCantidad());
        dto.setVentaMedia(dto.getTotalVenta() > 0 ? dto.getTotalVenta() / dto.getCantidad() : 0.0);

        if (dto.getTotalDescuento() != null) {
            dto.setTotalDescuento((double) Math.round(dto.getTotalDescuento()));
        }
        if (dto.getTotalAumento() != null) {
            dto.setTotalAumento((double) Math.round(dto.getTotalAumento()));
        }

        // Lucro = total venta - costo total - descuento + aumento.
        dto.setLucro((double) Math.round(dto.getTotalVenta() - dto.getCostoTotal()
                - (dto.getTotalDescuento() != null ? dto.getTotalDescuento() : 0.0)
                + (dto.getTotalAumento() != null ? dto.getTotalAumento() : 0.0)));

        // Margen: sobre el costo. Porcentaje: sobre la venta.
        dto.setMargen(dto.getCostoTotal() > 0 ? (dto.getLucro() / dto.getCostoTotal()) * 100 : 0.0);
        dto.setPercent(dto.getTotalVenta() > 0 ? (dto.getLucro() / dto.getTotalVenta()) * 100 : 0.0);
    }

    private static List<Long> sinNulos(List<Long> ids) {
        if (ids == null) {
            return Collections.emptyList();
        }
        return ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    private static List<Long> ordenados(List<Long> ids) {
        List<Long> r = new ArrayList<>(sinNulos(ids));
        Collections.sort(r);
        return r;
    }

    private static final class Guardado {
        final List<LucroPorProductosDto> lista;
        final Instant venceEn;

        Guardado(List<LucroPorProductosDto> lista, Instant venceEn) {
            this.lista = lista;
            this.venceEn = venceEn;
        }
    }
}
