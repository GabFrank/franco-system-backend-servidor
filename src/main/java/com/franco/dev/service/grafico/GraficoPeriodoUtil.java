package com.franco.dev.service.grafico;

import com.franco.dev.domain.grafico.DesgloseAnhoGrafico;
import com.franco.dev.domain.grafico.DesglosePeriodoGrafico;
import com.franco.dev.graphql.grafico.input.PeriodoGraficoInput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class GraficoPeriodoUtil {

    private GraficoPeriodoUtil() {
    }

    public static List<Long> normalizarSucIds(List<Long> sucIds) {
        if (sucIds == null || sucIds.isEmpty()) {
            return Collections.singletonList(null);
        }
        List<Long> ids = sucIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
        return ids.isEmpty() ? Collections.singletonList(null) : ids;
    }

    public static List<Long> normalizarUsuarioIds(List<Long> usuarioIds) {
        if (usuarioIds == null || usuarioIds.isEmpty()) {
            return Collections.emptyList();
        }
        return usuarioIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
    }

    public static Integer extraerAnho(String inicio) {
        if (inicio == null || inicio.length() < 4) {
            return null;
        }
        String prefijo = inicio.substring(0, 4);
        if (!prefijo.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return Integer.parseInt(prefijo);
    }

    public static void agregarDesgloseAnho(
            List<DesgloseAnhoGrafico> desgloseAnhos,
            Integer anio,
            double total,
            Double cantidad) {
        if (anio == null || desgloseAnhos == null) {
            return;
        }
        DesgloseAnhoGrafico slice = desgloseAnhos.stream()
                .filter(d -> anio.equals(d.getAnio()))
                .findFirst()
                .orElseGet(() -> {
                    DesgloseAnhoGrafico d = new DesgloseAnhoGrafico();
                    d.setAnio(anio);
                    d.setTotal(0.0);
                    d.setCantidad(0.0);
                    desgloseAnhos.add(d);
                    return d;
                });
        slice.setTotal(slice.getTotal() + total);
        if (cantidad != null) {
            slice.setCantidad((slice.getCantidad() != null ? slice.getCantidad() : 0.0) + cantidad);
        }
    }

    public static void agregarDesglose(
            List<DesglosePeriodoGrafico> desglose,
            String etiqueta,
            double total,
            Double cantidad) {
        if (etiqueta == null) {
            return;
        }
        DesglosePeriodoGrafico slice = desglose.stream()
                .filter(d -> etiqueta.equals(d.getEtiqueta()))
                .findFirst()
                .orElseGet(() -> {
                    DesglosePeriodoGrafico d = new DesglosePeriodoGrafico();
                    d.setEtiqueta(etiqueta);
                    d.setTotal(0.0);
                    d.setCantidad(0.0);
                    desglose.add(d);
                    return d;
                });
        slice.setTotal(slice.getTotal() + total);
        if (cantidad != null) {
            slice.setCantidad((slice.getCantidad() != null ? slice.getCantidad() : 0.0) + cantidad);
        }
    }

    public static void fusionarSucursalesTexto(StringBuilder destino, String origen) {
        if (origen == null || origen.isBlank()) {
            return;
        }
        Set<String> nombres = new LinkedHashSet<>();
        if (destino.length() > 0) {
            for (String parte : destino.toString().split(",\\s*")) {
                if (!parte.isBlank()) {
                    nombres.add(parte.trim());
                }
            }
        }
        for (String parte : origen.split(",\\s*")) {
            if (!parte.isBlank()) {
                nombres.add(parte.trim());
            }
        }
        destino.setLength(0);
        destino.append(String.join(", ", nombres));
    }

    public static boolean esMultiPeriodo(List<PeriodoGraficoInput> periodos) {
        return periodos != null && periodos.size() > 1;
    }
}
