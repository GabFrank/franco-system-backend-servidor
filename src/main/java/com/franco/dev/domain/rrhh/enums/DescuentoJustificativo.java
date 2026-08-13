package com.franco.dev.domain.rrhh.enums;

/**
 * Cuanto descuenta del salario un justificativo, segun su tipo.
 * Lo aplica LiquidacionSueldoService al armar el borrador del periodo.
 */
public enum DescuentoJustificativo {
    NO,
    MEDIO_DIA,
    DIA_COMPLETO;

    /** Factor sobre el salario diario: 0, 0.5 o 1. */
    public double factor() {
        switch (this) {
            case MEDIO_DIA: return 0.5;
            case DIA_COMPLETO: return 1.0;
            default: return 0.0;
        }
    }
}
