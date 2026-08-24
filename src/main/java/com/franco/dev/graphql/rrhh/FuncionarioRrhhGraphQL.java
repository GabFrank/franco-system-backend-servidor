package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.FuncionarioCargoHistorico;
import com.franco.dev.domain.rrhh.FuncionarioDocumento;
import com.franco.dev.domain.rrhh.FuncionarioSalarioHistorico;
import com.franco.dev.graphql.rrhh.input.CambioCargoInput;
import com.franco.dev.graphql.rrhh.input.CambioSalarioInput;
import com.franco.dev.graphql.rrhh.input.FuncionarioDocumentoInput;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.rrhh.FuncionarioDocumentoService;
import com.franco.dev.service.rrhh.FuncionarioCargoHistoricoService;
import com.franco.dev.service.rrhh.FuncionarioRrhhService;
import com.franco.dev.service.rrhh.FuncionarioSalarioHistoricoService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class FuncionarioRrhhGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private FuncionarioRrhhService funcionarioRrhhService;

    @Autowired
    private FuncionarioCargoHistoricoService cargoHistoricoService;

    @Autowired
    private FuncionarioSalarioHistoricoService salarioHistoricoService;

    @Autowired
    private FuncionarioDocumentoService documentoService;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private com.franco.dev.service.rrhh.RrhhSecurityService seg;

    // ----- Queries -----

    public List<FuncionarioCargoHistorico> funcionarioCargoHistoricos(Long funcionarioId) {
        seg.requireVer();
        return cargoHistoricoService.findByFuncionarioId(funcionarioId);
    }

    public List<FuncionarioSalarioHistorico> funcionarioSalarioHistoricos(Long funcionarioId) {
        seg.requireVer();
        return salarioHistoricoService.findByFuncionarioId(funcionarioId);
    }

    public List<FuncionarioDocumento> funcionarioDocumentos(Long funcionarioId) {
        seg.requireVer();
        return documentoService.findByFuncionarioId(funcionarioId);
    }

    public String funcionarioDocumentoContenido(Long id) {
        seg.requireVer();
        return documentoService.getContenidoBase64(id);
    }

    // ----- Mutations -----

    public Funcionario cambiarCargoFuncionario(CambioCargoInput input) {
        seg.requireAnyRole(seg.GESTIONAR);
        return funcionarioRrhhService.cambiarCargo(
                input.getFuncionarioId(),
                input.getCargoId(),
                parseFecha(input.getFecha()),
                input.getMotivo(),
                input.getAutorizadoPorId());
    }

    public Funcionario cambiarSalarioFuncionario(CambioSalarioInput input) {
        seg.requireAnyRole(seg.GESTIONAR, seg.CONFIG);
        return funcionarioRrhhService.cambiarSalario(
                input.getFuncionarioId(),
                input.getNuevoSalario(),
                input.getMonedaId(),
                parseFecha(input.getFecha()),
                input.getMotivo(),
                input.getAutorizadoPorId());
    }

    public Funcionario egresarFuncionario(Long funcionarioId, String fecha, String motivo) {
        seg.requireAnyRole(seg.GESTIONAR, seg.APROBAR);
        return funcionarioRrhhService.egresar(funcionarioId, parseFecha(fecha), motivo);
    }

    /**
     * El egreso vigente del funcionario, para que el dialogo de reversa precargue el
     * credito en vez de pedirle al usuario que lo reconstruya. Null si el egreso es
     * anterior al historico.
     */
    public com.franco.dev.domain.rrhh.FuncionarioEgresoHistorico egresoVigenteFuncionario(Long funcionarioId) {
        seg.requireVer();
        return funcionarioRrhhService.egresoVigente(funcionarioId).orElse(null);
    }

    /**
     * Revierte un egreso hecho por error. Mismo gating que egresar: quien puede sacar a
     * alguien es quien puede volver a meterlo.
     *
     * <p>El credito va como parametro porque el egreso lo borra y no queda guardado en
     * ninguna parte -- ver {@code FuncionarioRrhhService.revertirEgreso}.</p>
     */
    public Funcionario revertirEgresoFuncionario(Long funcionarioId, Float credito, String motivo) {
        seg.requireAnyRole(seg.GESTIONAR, seg.APROBAR);
        return funcionarioRrhhService.revertirEgreso(funcionarioId, credito, motivo);
    }

    public FuncionarioDocumento saveFuncionarioDocumento(FuncionarioDocumentoInput input) {
        seg.requireAnyRole(seg.GESTIONAR);
        FuncionarioDocumento e = input.getId() != null
                ? documentoService.findById(input.getId()).orElse(new FuncionarioDocumento())
                : new FuncionarioDocumento();
        if (input.getFuncionarioId() != null)
            e.setFuncionario(funcionarioService.findById(input.getFuncionarioId()).orElse(null));
        e.setTipo(input.getTipo());
        // si viene contenido nuevo, persistir el binario en disco
        if (input.getContenidoBase64() != null && !input.getContenidoBase64().isBlank()) {
            String fileName = documentoService.guardarArchivo(
                    input.getFuncionarioId(),
                    input.getTipo() != null ? input.getTipo().name() : "OTRO",
                    input.getNombreArchivo(),
                    input.getContenidoBase64());
            e.setNombreArchivo(fileName);
            e.setRutaRelativa("rrhh/funcionario-documentos/" + fileName);
        }
        if (input.getMimeType() != null) e.setMimeType(input.getMimeType());
        if (input.getTamanoBytes() != null) e.setTamanoBytes(input.getTamanoBytes());
        if (input.getVencimiento() != null && stringToDate(input.getVencimiento()) != null)
            e.setVencimiento(stringToDate(input.getVencimiento()).toLocalDate());
        e.setObservacion(input.getObservacion());
        return documentoService.save(e);
    }

    public FuncionarioDocumento anularFuncionarioDocumento(Long id) {
        seg.requireAnyRole(seg.GESTIONAR);
        return documentoService.anular(id);
    }

    private LocalDate parseFecha(String s) {
        if (s == null || s.isBlank()) return null;
        return stringToDate(s) != null ? stringToDate(s).toLocalDate() : null;
    }
}
