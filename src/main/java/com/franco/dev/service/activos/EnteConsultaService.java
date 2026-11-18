package com.franco.dev.service.activos;

import com.franco.dev.config.multitenant.CustomPageImpl;
import com.franco.dev.domain.activos.Ente;
import com.franco.dev.domain.activos.EnteSucursal;
import com.franco.dev.domain.activos.enums.TipoEnte;
import com.franco.dev.graphql.activos.dto.EnteDashboardSummary;
import com.franco.dev.graphql.activos.dto.EnteSearchResponse;
import com.franco.dev.repository.activos.EnteRepository;
import com.franco.dev.service.financiero.PreGastoService;
import com.franco.dev.service.financiero.dto.EnteFinancialSummaryDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Vista de los entes para pantalla: cada Ente con sus campos de resumen financiero (descripcion,
 * montos, cuotas, proveedor) y sus sucursales.
 *
 * Estaba dentro de {@link EnteService}; se separo porque el resumen sale de PreGastoService, que
 * consulta los bienes (mueble, inmueble, vehiculo, equipo), y los bienes dependen de EnteService para
 * dar de alta o baja su Ente: era un ciclo de beans (issue #651). Los metodos son los mismos que tenia
 * EnteService. La busqueda calcula el resumen de cada ente en memoria (N+1): ver issue #652.
 */
@Service
public class EnteConsultaService {

    private final EnteRepository repository;
    private final PreGastoService preGastoService;
    private final EnteSucursalService enteSucursalService;

    public EnteConsultaService(EnteRepository repository, PreGastoService preGastoService,
            EnteSucursalService enteSucursalService) {
        this.repository = repository;
        this.preGastoService = preGastoService;
        this.enteSucursalService = enteSucursalService;
    }

    public Page<Ente> findAllWithFilters(String texto, Long sucursalId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        String formattedText = (texto != null && !texto.trim().isEmpty()) ? "%" + texto.trim().toLowerCase() + "%" : null;
        Page<Ente> res = repository.findAllWithFilters(formattedText, sucursalId, pageable);
        res.getContent().forEach(this::populateTransientFields);
        return res;
    }

    public EnteSearchResponse searchWithSummary(String texto, Long sucursalId, TipoEnte tipoEnte, String situacionPago,
            String estadoCuota, int page, int size) {
        // Obtenemos todos los candidatos básicos de la DB (filtramos por texto,
        // sucursal y tipo si se provee)
        // Usamos una lista completa para poder calcular el resumen total
        String formattedText = (texto != null && !texto.trim().isEmpty()) ? "%" + texto.trim().toLowerCase() + "%" : null;
        List<Ente> allCandidates = repository.findAllWithFiltersList(formattedText, sucursalId);

        // Filtramos y poblamos campos transientes en Java
        List<Ente> filtered = allCandidates.stream()
                .map(this::populateTransientFields)
                .filter(e -> tipoEnte == null || e.getTipoEnte() == tipoEnte)
                .filter(e -> situacionPago == null || situacionPago.isEmpty()
                        || (e.getSituacionPago() != null && e.getSituacionPago().equalsIgnoreCase(situacionPago)))
                .filter(e -> estadoCuota == null || estadoCuota.isEmpty()
                        || (e.getEstadoCuota() != null && e.getEstadoCuota().equalsIgnoreCase(estadoCuota)))
                .collect(Collectors.toList());

        // Calculamos el resumen sobre el set filtrado completo
        EnteDashboardSummary summary = calcularResumen(filtered);

        // Pagínación manual sobre la lista filtrada
        int start = Math.min(page * size, filtered.size());
        int end = Math.min((page + 1) * size, filtered.size());
        List<Ente> pagedList = filtered.subList(start, end);

        Pageable pageable = PageRequest.of(page, size);
        return new EnteSearchResponse(
                new CustomPageImpl<>(pagedList, pageable, (long) filtered.size(), null),
                summary);
    }

    private EnteDashboardSummary calcularResumen(List<Ente> rows) {
        EnteDashboardSummary summary = new EnteDashboardSummary();
        summary.setTotalBienes(rows.size());
        summary.setPagados(0);
        summary.setEnPago(0);
        summary.setCuotasFaltantes(0);
        summary.setTotalGastado(0.0);
        summary.setTotalComprometido(0.0);
        summary.setTotalPendiente(0.0);

        Map<String, Integer> monedaCounts = new HashMap<>();

        for (Ente row : rows) {
            boolean pagado = "PAGADO".equalsIgnoreCase(row.getSituacionPago())
                    || (row.getMontoPendiente() != null && row.getMontoPendiente() <= 0);
            if (pagado)
                summary.setPagados(summary.getPagados() + 1);
            else
                summary.setEnPago(summary.getEnPago() + 1);

            if (row.getCuotasFaltantes() != null)
                summary.setCuotasFaltantes(summary.getCuotasFaltantes() + row.getCuotasFaltantes());
            if (row.getMontoYaPagado() != null)
                summary.setTotalGastado(summary.getTotalGastado() + row.getMontoYaPagado());
            if (row.getMontoTotal() != null)
                summary.setTotalComprometido(summary.getTotalComprometido() + row.getMontoTotal());
            if (row.getMontoPendiente() != null)
                summary.setTotalPendiente(summary.getTotalPendiente() + row.getMontoPendiente());

            String mon = row.getMonedaSimbolo() != null ? row.getMonedaSimbolo() : "Gs.";
            monedaCounts.put(mon, monedaCounts.getOrDefault(mon, 0) + 1);
        }

        String monedaP = monedaCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Gs.");
        summary.setMonedaPrincipal(monedaP);

        return summary;
    }

    public Ente populateTransientFields(Ente ente) {
        if (ente == null)
            return null;
        EnteFinancialSummaryDTO summary = preGastoService.getFinancialSummary(ente.getId());
        if (summary != null) {
            ente.setDescripcion(summary.getDescripcion());
            ente.setMontoTotal(summary.getMontoTotal() != null ? summary.getMontoTotal().doubleValue() : null);
            ente.setMontoYaPagado(summary.getMontoYaPagado() != null ? summary.getMontoYaPagado().doubleValue() : null);
            ente.setMontoPendiente(
                    summary.getMontoPendiente() != null ? summary.getMontoPendiente().doubleValue() : null);
            ente.setCuotasTotales(summary.getCuotasTotales());
            ente.setCuotasPagadas(summary.getCuotasPagadas());
            ente.setCuotasFaltantes(summary.getCuotasFaltantes());
            ente.setDiaVencimiento(summary.getDiaVencimiento());
            ente.setDiasParaVencer(summary.getDiasParaVencer());
            ente.setEstadoCuota(summary.getEstadoCuota());
            ente.setSituacionPago(summary.getSituacionPago());
            ente.setMonedaSimbolo(summary.getMonedaSimbolo());
            ente.setProveedorNombre(summary.getProveedorNombre());
        }
        List<EnteSucursal> asignaciones = enteSucursalService.findByEnteId(ente.getId());
        if (asignaciones != null && !asignaciones.isEmpty()) {
            ente.setSucursalesConcatenadas(asignaciones.stream()
                    .map(a -> a.getSucursal().getNombre())
                    .collect(Collectors.joining(", ")));
            ente.setSucursalIds(asignaciones.stream()
                    .map(a -> a.getSucursal().getId())
                    .collect(Collectors.toList()));
        } else {
            ente.setSucursalesConcatenadas("Sin sucursal");
        }
        return ente;
    }
}
