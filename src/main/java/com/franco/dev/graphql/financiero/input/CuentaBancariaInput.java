package com.franco.dev.graphql.financiero.input;

import com.franco.dev.domain.financiero.enums.TipoCuenta;
import lombok.Data;

@Data
public class CuentaBancariaInput {
    private Long id;
    private Long personaId;
    private Long bancoId;
    private Long monedaId;
    private String numbero;
    private String numero;
    private String nombre;
    private TipoCuenta tipoCuenta;
    // saldo/saldoReservado NO se mapean por el CRUD: los administra el ledger (BancoLedgerService).
    private String titular;
    private String alias;
    private Boolean activo;
    private Boolean disponibleOperacionesFinancieras;
    private Boolean permiteSaldoNegativo;
    private Long usuarioId;
}
