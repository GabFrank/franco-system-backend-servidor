package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.domain.financiero.OperacionFinanciera;

import java.util.ArrayList;
import java.util.List;

/**
 * Descripción de los movimientos de caja y de banco que genera una operación financiera (desk #721). Un solo
 * armador para las dos patas: antes el banco decía «Depósito bancario (op #116)» y la caja el enum crudo
 * «DEPOSITO_BANCARIO (op #116)».
 *
 * <p>{@code Depósito · Boleta 2443309 · COFRE CENTRAL} en el banco y {@code Depósito · Boleta 2443309 · BNF
 * 019001922421} en la caja: tipo, comprobante y la otra punta. Sin comprobante queda {@code (op #N)}, para no
 * perder la referencia. V277.5 aplica la misma regla, en SQL, a los movimientos ya registrados.
 */
public final class OperacionFinancieraDescripcion {

    /** De qué lado de la operación está el movimiento: la descripción nombra el otro. */
    public enum Lado { ORIGEN, DESTINO }

    private OperacionFinancieraDescripcion() { }

    public static String de(OperacionFinanciera op, Lado lado) {
        List<String> partes = new ArrayList<>();
        String comprobante = op.getNumeroComprobante() != null ? op.getNumeroComprobante().trim() : "";
        if (comprobante.isEmpty()) {
            partes.add(rotulo(op) + " (op #" + op.getId() + ")");
        } else {
            partes.add(rotulo(op));
            partes.add(etiquetaComprobante(op) + " " + comprobante);
        }
        String otra = lado == Lado.ORIGEN ? nombreDestino(op) : nombreOrigen(op);
        if (otra != null && !otra.isEmpty()) partes.add(otra);
        return String.join(" · ", partes);
    }

    static String rotulo(OperacionFinanciera op) {
        if (op.getTipoOperacion() == null) return "Operación financiera";
        switch (op.getTipoOperacion()) {
            case DEPOSITO_BANCARIO: return "Depósito";
            case RETIRO_BANCARIO: return "Retiro bancario";
            case TRANSFERENCIA_BANCARIA: return "Transf. bancaria";
            case TRANSFERENCIA_ENTRE_CAJAS: return "Transf. entre cajas";
            case CAMBIO_DIVISA: return "Cambio de divisa";
            default: return "Operación financiera";
        }
    }

    /** El depósito trae boleta; el resto, un comprobante genérico. */
    static String etiquetaComprobante(OperacionFinanciera op) {
        return op.getTipoOperacion() == com.franco.dev.domain.financiero.enums.TipoOperacionFinanciera.DEPOSITO_BANCARIO
                ? "Boleta" : "Comp.";
    }

    private static String nombreOrigen(OperacionFinanciera op) {
        return op.getCajaMayorOrigen() != null ? nombre(op.getCajaMayorOrigen()) : nombre(op.getCuentaBancariaOrigen());
    }

    private static String nombreDestino(OperacionFinanciera op) {
        return op.getCajaMayorDestino() != null ? nombre(op.getCajaMayorDestino()) : nombre(op.getCuentaBancariaDestino());
    }

    private static String nombre(CajaVirtual caja) {
        return caja != null ? caja.getNombre() : null;
    }

    /** «BNF 019001922421»: banco y número; si falta el banco, el nombre de la cuenta. */
    private static String nombre(CuentaBancaria cuenta) {
        if (cuenta == null) return null;
        String banco = cuenta.getBanco() != null ? cuenta.getBanco().getNombre() : null;
        String numero = cuenta.getNumero();
        if (banco != null && numero != null) return banco + " " + numero;
        if (numero != null) return numero;
        return cuenta.getNombre();
    }
}
