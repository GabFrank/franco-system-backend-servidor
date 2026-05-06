package com.franco.dev.service.administrativo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AutorizacionAuditService {

    private final JdbcTemplate jdbcTemplate;

    public AutorizacionAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void registrarPreGastoAutorizado(Long funcionarioId, Long autorizadorId, Long usuarioId, Long sucursalId,
            String observacion) {
        registrar(funcionarioId, autorizadorId, usuarioId, sucursalId, "AUTORIZADO", observacion);
    }

    public void registrarPreGastoRechazado(Long funcionarioId, Long autorizadorId, Long usuarioId, Long sucursalId,
            String observacion) {
        registrar(funcionarioId, autorizadorId, usuarioId, sucursalId, "NO_AUTORIZADO", observacion);
    }

    private void registrar(Long funcionarioId, Long autorizadorId, Long usuarioId, Long sucursalId, String estadoAutorizacion,
            String observacion) {
        jdbcTemplate.update(
                "INSERT INTO administrativo.autorizacion " +
                        "(funcionario_id, autorizador_id, tipo_autorizacion, estado_autorizacion, observacion, usuario_id, sucursal_id) " +
                        "VALUES (?, ?, CAST(? AS administrativo.tipo_autorizacion), CAST(? AS administrativo.estado_autorizacion), ?, ?, ?)",
                funcionarioId,
                autorizadorId,
                "PRE_GASTO",
                estadoAutorizacion,
                observacion,
                usuarioId,
                sucursalId
        );
    }
}
