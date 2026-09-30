package com.franco.dev.service.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.dto.ResumenFiscalContribuyente;
import com.franco.dev.domain.financiero.dto.ResumenFiscalTimbrado;
import com.franco.dev.domain.financiero.dto.ResumenFiscalVentas;
import com.franco.dev.repository.financiero.FacturaLegalRepository;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.GraphQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Resumen de las ventas facturadas de un mes para el contador: exentas, gravadas 5 % y gravadas
 * 10 % (base e IVA) y el detalle por timbrado. Solo datos: la declaracion (formulario 120) la
 * arma el contador.
 *
 * <p>Sale de {@code factura_legal}, donde caen igual las facturas en papel (autoimpresor) y las
 * electronicas. Quedan afuera las anuladas: {@code activo = false} en papel, y en electronicas
 * ademas las que SIFEN dejo CANCELADAS (ahi {@code activo} suele quedar en NULL). Las RECHAZADAS
 * por SIFEN tampoco suman (no tienen validez fiscal) pero se informan aparte con su monto.</p>
 *
 * <p>En {@code factura_legal} el total por tasa incluye el IVA; el reporte muestra la base sin
 * IVA, como el formulario 120. Se redondea a guaranies por fila de detalle y los totales suman
 * esas filas, asi el detalle cuadra exacto con el resumen.</p>
 */
@Service
@RequiredArgsConstructor
public class ResumenFiscalVentasService {

    static final String[] MESES = {
            "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    };

    private final FacturaLegalRepository facturaLegalRepository;
    private final SucursalService sucursalService;

    @Transactional(readOnly = true)
    public ResumenFiscalVentas resumen(Integer anio, Integer mes, List<Long> sucIds) {
        validarPeriodo(anio, mes);
        LocalDateTime inicio = LocalDateTime.of(anio, mes, 1, 0, 0);
        LocalDateTime fin = inicio.plusMonths(1);
        List<Long> sucursales = normalizarSucIds(sucIds);
        List<Object[]> filas = sucursales.isEmpty()
                ? facturaLegalRepository.resumenFiscalVentasSinSucursal(inicio, fin)
                : facturaLegalRepository.resumenFiscalVentas(inicio, fin, sucursales);
        return armar(anio, mes, etiquetaSucursales(sucursales), filas);
    }

    static void validarPeriodo(Integer anio, Integer mes) {
        if (anio == null || anio < 2000 || anio > 2100) {
            throw new GraphQLException("Año inválido: " + anio);
        }
        if (mes == null || mes < 1 || mes > 12) {
            throw new GraphQLException("Mes inválido: " + mes);
        }
    }

    /**
     * Arma el resumen desde las filas de {@code FacturaLegalRepository.resumenFiscalVentas*}:
     * [0] ruc, [1] razon social, [2] sucursal id, [3] sucursal, [4] establecimiento,
     * [5] punto de expedicion, [6] timbrado, [7] electronico, [8] emitidas, [9] anuladas,
     * [10] numero desde, [11] numero hasta, [12] total 10 %, [13] IVA 10 %, [14] total 5 %,
     * [15] IVA 5 %, [16] exentas, [17] rechazadas, [18] monto rechazadas.
     */
    static ResumenFiscalVentas armar(int anio, int mes, String sucursalesFiltro, List<Object[]> filas) {
        ResumenFiscalVentas resumen = new ResumenFiscalVentas();
        resumen.setAnio(anio);
        resumen.setMes(mes);
        resumen.setPeriodo(MESES[mes - 1] + " " + anio);
        resumen.setSucursalesFiltro(sucursalesFiltro);

        Map<String, List<ResumenFiscalTimbrado>> porRuc = new TreeMap<>();
        Map<String, String> razonPorRuc = new LinkedHashMap<>();
        for (Object[] f : filas) {
            String ruc = texto(f[0]);
            razonPorRuc.putIfAbsent(ruc, texto(f[1]));
            porRuc.computeIfAbsent(ruc, k -> new ArrayList<>()).add(detalle(f));
        }
        porRuc.forEach((ruc, detalle) -> resumen.getContribuyentes()
                .add(contribuyente(ruc, razonPorRuc.get(ruc), detalle)));
        return resumen;
    }

    private static ResumenFiscalTimbrado detalle(Object[] f) {
        ResumenFiscalTimbrado d = new ResumenFiscalTimbrado();
        d.setSucursalId(entero(f[2]));
        d.setSucursal(texto(f[3]));
        d.setEstablecimiento(texto(f[4]));
        d.setPuntoExpedicion(texto(f[5]));
        d.setTimbrado(texto(f[6]));
        d.setElectronico(Boolean.TRUE.equals(f[7]));
        d.setTipo(d.getElectronico() ? "Electrónica" : "Papel");
        d.setEmitidas(entero(f[8]));
        d.setAnuladas(entero(f[9]));
        d.setNumeroDesde(numeroFactura(d.getEstablecimiento(), d.getPuntoExpedicion(), f[10]));
        d.setNumeroHasta(numeroFactura(d.getEstablecimiento(), d.getPuntoExpedicion(), f[11]));
        long total10 = guaranies(f[12]);
        long iva10 = guaranies(f[13]);
        long total5 = guaranies(f[14]);
        long iva5 = guaranies(f[15]);
        long exentas = guaranies(f[16]);
        d.setGravada10((double) (total10 - iva10));
        d.setIva10((double) iva10);
        d.setGravada5((double) (total5 - iva5));
        d.setIva5((double) iva5);
        d.setExentas((double) exentas);
        d.setTotalFacturado((double) (total10 + total5 + exentas));
        d.setRechazadas(entero(f[17]));
        d.setMontoRechazadas((double) guaranies(f[18]));
        return d;
    }

    private static ResumenFiscalContribuyente contribuyente(String ruc, String razonSocial,
                                                            List<ResumenFiscalTimbrado> detalle) {
        ResumenFiscalContribuyente c = new ResumenFiscalContribuyente();
        c.setRuc(ruc);
        c.setRazonSocial(razonSocial);
        c.setDetalle(detalle);
        c.setGravada10(sumar(detalle, ResumenFiscalTimbrado::getGravada10));
        c.setIva10(sumar(detalle, ResumenFiscalTimbrado::getIva10));
        c.setGravada5(sumar(detalle, ResumenFiscalTimbrado::getGravada5));
        c.setIva5(sumar(detalle, ResumenFiscalTimbrado::getIva5));
        c.setExentas(sumar(detalle, ResumenFiscalTimbrado::getExentas));
        c.setTotalBase(c.getGravada10() + c.getGravada5() + c.getExentas());
        c.setTotalIva(c.getIva10() + c.getIva5());
        c.setTotalFacturado(c.getTotalBase() + c.getTotalIva());
        c.setEmitidas(detalle.stream().mapToLong(ResumenFiscalTimbrado::getEmitidas).sum());
        c.setAnuladas(detalle.stream().mapToLong(ResumenFiscalTimbrado::getAnuladas).sum());
        c.setRechazadas(detalle.stream().mapToLong(ResumenFiscalTimbrado::getRechazadas).sum());
        c.setMontoRechazadas(sumar(detalle, ResumenFiscalTimbrado::getMontoRechazadas));
        return c;
    }


    private String etiquetaSucursales(List<Long> sucursales) {
        if (sucursales.isEmpty()) {
            return "Todas";
        }
        return sucursales.stream()
                .map(id -> {
                    Sucursal s = sucursalService.findById(id).orElse(null);
                    return s != null && s.getNombre() != null ? s.getNombre() : "Sucursal " + id;
                })
                .collect(Collectors.joining(", "));
    }

    /** null o vacio = todas. Acepta la sucursal 0 (SERVIDOR): es una sucursal real. */
    private static List<Long> normalizarSucIds(List<Long> sucIds) {
        if (sucIds == null) {
            return new ArrayList<>();
        }
        return sucIds.stream().filter(Objects::nonNull).filter(id -> id >= 0).distinct()
                .collect(Collectors.toList());
    }

    private static String numeroFactura(String establecimiento, String punto, Object numero) {
        if (numero == null) {
            return "";
        }
        return nulo(establecimiento) + "-" + nulo(punto) + "-"
                + String.format("%07d", ((Number) numero).longValue());
    }

    private static Double sumar(List<ResumenFiscalTimbrado> detalle,
                                java.util.function.Function<ResumenFiscalTimbrado, Double> campo) {
        return detalle.stream().mapToDouble(campo::apply).sum();
    }

    private static long guaranies(Object valor) {
        if (valor == null) {
            return 0L;
        }
        BigDecimal v = valor instanceof BigDecimal ? (BigDecimal) valor : new BigDecimal(valor.toString());
        return v.setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private static Long entero(Object valor) {
        return valor != null ? ((Number) valor).longValue() : 0L;
    }

    private static String texto(Object valor) {
        return valor != null ? valor.toString().trim() : "";
    }

    private static String nulo(String valor) {
        return valor != null ? valor : "";
    }
}
