package com.franco.dev.service.productos.search;

import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.dto.BuscadorProductoFiltros;
import com.franco.dev.domain.productos.dto.BuscadorProductoResultado;
import com.franco.dev.domain.productos.enums.TipoCoincidenciaBuscador;
import com.franco.dev.repository.productos.ProductoProveedorRepository;
import com.franco.dev.repository.productos.ProductoRepository;
import com.franco.dev.service.productos.ProductoService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Buscador inteligente genérico de productos.
 * Combina Lucene (texto + prefijo de código) sin alterar los endpoints tradicionales existentes.
 * Cualquier módulo puede consumirlo; por ahora solo Compras lo utiliza desde el frontend.
 */
@Service
@RequiredArgsConstructor
public class BuscadorProductoInteligenteService {

    private static final int MIN_CODIGO_PREFIJO = 3;

    private final ProductoService productoService;
    private final ProductoRepository productoRepository;
    private final ProductoSearchService productoSearchService;
    private final CodigoSearchService codigoSearchService;
    private final ProductoProveedorRepository productoProveedorRepository;

    @Value("${app.search.producto.enabled:true}")
    private boolean productoSearchEnabled;

    public Page<BuscadorProductoResultado> buscar(BuscadorProductoFiltros filtros) {
        if (filtros == null || filtros.getTexto() == null || filtros.getTexto().trim().isEmpty()) {
            return Page.empty();
        }

        String texto = filtros.getTexto().trim();
        int pageSize = filtros.getSize() > 0 ? filtros.getSize() : 10;
        int pageNumber = Math.max(filtros.getPage(), 0);
        int fetchLimit = filtros.getFetchLimit();
        Boolean activoFiltro = filtros.getActivo() != null ? filtros.getActivo() : true;

        LinkedHashMap<Long, BuscadorProductoResultado> ordenados = new LinkedHashMap<>();

        agregarCoincidenciaExacta(texto, ordenados);
        agregarCoincidenciasCodigo(texto, fetchLimit, ordenados);
        agregarCoincidenciaPorId(texto, ordenados);
        agregarCoincidenciasTexto(texto, fetchLimit, activoFiltro, ordenados);

        List<BuscadorProductoResultado> filtrados = aplicarFiltroProveedor(
                new ArrayList<>(ordenados.values()),
                filtros.getProveedorId());

        int total = filtrados.size();
        int from = Math.min(pageNumber * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<BuscadorProductoResultado> pagina = from >= to
                ? new ArrayList<>()
                : filtrados.subList(from, to);

        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        return new PageImpl<>(pagina, pageable, total);
    }

    private void agregarCoincidenciaExacta(String texto, Map<Long, BuscadorProductoResultado> destino) {
        if (!pareceCodigoBarras(texto)) {
            return;
        }
        Producto producto = productoService.findByCodigo(texto);
        if (producto != null && producto.getId() != null) {
            destino.putIfAbsent(
                    producto.getId(),
                    new BuscadorProductoResultado(producto, texto.toUpperCase(), TipoCoincidenciaBuscador.CODIGO_EXACTO));
        }
    }

    /**
     * Coincidencia parcial de código en cualquier posición: encuentra el producto tanto por el
     * código completo como omitiendo los ceros a la izquierda o escribiendo solo un tramo
     * interno o la terminación.
     */
    private void agregarCoincidenciasCodigo(
            String texto,
            int fetchLimit,
            Map<Long, BuscadorProductoResultado> destino) {
        if (!pareceCodigoBarras(texto) || texto.length() < MIN_CODIGO_PREFIJO) {
            return;
        }

        String fragmento = texto.toUpperCase();
        for (Long id : codigoSearchService.buscarProductoIdsPorCoincidencia(texto, fetchLimit)) {
            if (id == null || destino.containsKey(id)) {
                continue;
            }
            productoRepository.findById(id).ifPresent(producto -> destino.put(
                    id,
                    new BuscadorProductoResultado(producto, fragmento, TipoCoincidenciaBuscador.CODIGO_PARCIAL)));
        }
    }

    private void agregarCoincidenciaPorId(String texto, Map<Long, BuscadorProductoResultado> destino) {
        if (!texto.matches("\\d{1,8}")) {
            return;
        }
        try {
            Long id = Long.parseLong(texto);
            if (destino.containsKey(id)) {
                return;
            }
            productoRepository.findById(id).ifPresent(producto -> destino.put(
                    id,
                    new BuscadorProductoResultado(producto, null, TipoCoincidenciaBuscador.ID)));
        } catch (NumberFormatException ignored) {
            // noop
        }
    }

    private void agregarCoincidenciasTexto(
            String texto,
            int fetchLimit,
            Boolean activo,
            Map<Long, BuscadorProductoResultado> destino) {
        if (!pareceTextoDescriptivo(texto) && pareceCodigoBarras(texto)) {
            return;
        }

        List<Long> ids;
        if (productoSearchEnabled && productoSearchService.textoBusquedaValido(texto)) {
            ids = productoSearchService.buscarIdsPorTexto(texto, fetchLimit, activo, false, null, null);
        } else {
            ids = productoRepository.findIdsByDescripcionLike(texto.replace(" ", "%").toUpperCase(), fetchLimit);
        }

        for (Long id : ids) {
            if (id == null || destino.containsKey(id)) {
                continue;
            }
            productoRepository.findById(id).ifPresent(producto -> destino.put(
                    id,
                    new BuscadorProductoResultado(producto, null, TipoCoincidenciaBuscador.TEXTO)));
        }
    }

    private List<BuscadorProductoResultado> aplicarFiltroProveedor(
            List<BuscadorProductoResultado> resultados,
            Long proveedorId) {
        if (proveedorId == null || resultados.isEmpty()) {
            return resultados;
        }

        List<Long> productoIds = resultados.stream()
                .map(r -> r.getProducto() != null ? r.getProducto().getId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (productoIds.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> permitidos = new LinkedHashSet<>(
                productoProveedorRepository.findProductoIdsByProveedorIdAndProductoIdIn(proveedorId, productoIds));

        return resultados.stream()
                .filter(r -> r.getProducto() != null && permitidos.contains(r.getProducto().getId()))
                .collect(Collectors.toList());
    }

    private boolean pareceCodigoBarras(String texto) {
        if (texto == null || texto.isBlank() || texto.contains(" ")) {
            return false;
        }
        return texto.matches("\\d{3,}") || texto.matches("^[A-Za-z0-9\\-._]{4,32}$");
    }

    private boolean pareceTextoDescriptivo(String texto) {
        return texto != null && texto.matches(".*[a-zA-ZáéíóúÁÉÍÓÚñÑ].*");
    }
}
