package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.Justificativo;
import com.franco.dev.domain.rrhh.Penalizacion;
import com.franco.dev.domain.rrhh.enums.PenalizacionTipo;
import com.franco.dev.graphql.rrhh.input.JustificarJornadaInput;
import com.franco.dev.graphql.rrhh.input.PenalizacionInput;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.JustificativoService;
import com.franco.dev.service.rrhh.TipoJustificativoService;
import com.franco.dev.service.rrhh.PenalizacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;
import static com.franco.dev.utilitarios.DateUtils.stringToLocalDate;

@Component
public class PenalizacionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PenalizacionService service;

    @Autowired
    private com.franco.dev.service.rrhh.RrhhSecurityService seg;

    @Autowired
    private JustificativoService justificativoService;

    @Autowired
    private TipoJustificativoService tipoJustificativoService;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<Penalizacion> penalizacion(Long id) {
        seg.requireVer();
        return service.findById(id);
    }

    public List<Penalizacion> penalizacionesPorFuncionario(Long funcionarioId) {
        seg.requireVer();
        return service.findByFuncionarioId(funcionarioId);
    }

    public List<Penalizacion> penalizacionesPorFuncionarioYRango(Long funcionarioId, String desde, String hasta) {
        seg.requireVer();
        return service.findByFuncionarioIdAndFechaBetween(
                funcionarioId,
                stringToDate(desde) != null ? stringToDate(desde).toLocalDate() : null,
                stringToDate(hasta) != null ? stringToDate(hasta).toLocalDate() : null);
    }

    public List<Penalizacion> penalizacionesPorJornada(Long jornadaId, Long sucursalId) {
        seg.requireVer();
        return service.findByJornada(jornadaId, sucursalId);
    }

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    public Page<Penalizacion> penalizacionesPage(int page, int size, Long funcionarioId, String desde, String hasta,
                                                 PenalizacionTipo tipo) {
        seg.requireVer();
        return service.findPage(funcionarioId, stringToLocalDate(desde), stringToLocalDate(hasta), tipo,
                PageRequest.of(page, size));
    }

    /** Cuantas amonestaciones no anuladas acumula el funcionario (chip del legajo). */
    public Long contarAdvertencias(Long funcionarioId) {
        seg.requireVer();
        return service.contarAdvertencias(funcionarioId);
    }

    public Penalizacion savePenalizacion(PenalizacionInput input) {
        seg.requireAnyRole(seg.GESTIONAR);
        // Una penalizacion es siempre un descuento positivo. Un monto negativo cargado como
        // "correccion" de otra penalizacion genera su propio item de DESCUENTO en la
        // liquidacion, donde no se compensa con nada: el funcionario termina cobrando de
        // menos y en el recibo no se ve por que. Para corregir hay que anular y volver a
        // cargar, que es lo que deja rastro.
        if (input.getMonto() != null && input.getMonto().signum() < 0) {
            throw new graphql.GraphQLException(
                    "El monto de una penalizacion no puede ser negativo. Para corregir una penalizacion, anulala y carga la nueva.");
        }
        Penalizacion e = input.getId() != null
                ? service.findById(input.getId()).orElse(new Penalizacion())
                : new Penalizacion();
        if (input.getFuncionarioId() != null)
            e.setFuncionario(funcionarioService.findById(input.getFuncionarioId()).orElse(null));
        e.setJornadaId(input.getJornadaId());
        e.setSucursalId(input.getSucursalId());
        e.setTipo(input.getTipo());
        e.setDescripcion(input.getDescripcion());
        e.setMonto(input.getMonto());
        // El numero correlativo lo pone el backend al crear (PenalizacionService.save).
        // Solo se acepta del cliente al EDITAR, para poder corregir uno cargado mal; en el
        // alta se ignora, asi una llamada GraphQL directa no puede saltear la numeracion.
        if (input.getId() != null && input.getNumeroAdvertencia() != null) {
            e.setNumeroAdvertencia(input.getNumeroAdvertencia());
        }
        if (input.getFirmada() != null) e.setFirmada(input.getFirmada());
        if (input.getFechaHecho() != null && stringToDate(input.getFechaHecho()) != null)
            e.setFechaHecho(stringToDate(input.getFechaHecho()).toLocalDate());
        if (input.getFecha() != null && stringToDate(input.getFecha()) != null)
            e.setFecha(stringToDate(input.getFecha()).toLocalDate());
        if (input.getAutoGenerada() != null) e.setAutoGenerada(input.getAutoGenerada());
        if (input.getAnulada() != null) e.setAnulada(input.getAnulada());
        if (input.getRegistradoPorId() != null)
            e.setRegistradoPor(usuarioService.findById(input.getRegistradoPorId()).orElse(null));
        return service.save(e);
    }

    public Penalizacion anularPenalizacion(Long id) {
        seg.requireAnyRole(seg.GESTIONAR);
        return service.anular(id);
    }

    /** Genera las penalizaciones automaticas por tardanza de la fecha dada. Devuelve la cantidad generada. */
    public Integer generarPenalizacionesAuto(String fecha) {
        seg.requireAnyRole(seg.GESTIONAR);
        LocalDate f = stringToDate(fecha) != null ? stringToDate(fecha).toLocalDate() : LocalDate.now().minusDays(1);
        return service.generarPenalizacionesAuto(f);
    }

    /**
     * Genera las penalizaciones automaticas de un rango de fechas.
     *
     * <p>La generacion es idempotente por jornada, asi que re-correr un rango que se
     * solapa con otro ya corrido no duplica nada.</p>
     */
    public Integer generarPenalizacionesAutoRango(String desde, String hasta) {
        seg.requireAnyRole(seg.GESTIONAR);
        LocalDate d = stringToDate(desde) != null ? stringToDate(desde).toLocalDate() : null;
        LocalDate h = stringToDate(hasta) != null ? stringToDate(hasta).toLocalDate() : null;
        return service.generarPenalizacionesAutoRango(d, h);
    }

    /**
     * Justifica una jornada: crea una novedad JUSTIFICADO y anula las
     * penalizaciones auto-generadas de esa jornada.
     */
    public Boolean justificarJornada(JustificarJornadaInput input) {
        seg.requireAnyRole(seg.GESTIONAR);
        Justificativo n = new Justificativo();
        if (input.getFuncionarioId() != null)
            n.setFuncionario(funcionarioService.findById(input.getFuncionarioId()).orElse(null));
        if (input.getFecha() != null && stringToDate(input.getFecha()) != null)
            n.setFecha(stringToDate(input.getFecha()).toLocalDate());
        // el tipo sale del catalogo, ya no de un enum
        n.setTipo(tipoJustificativoService.findByNombre("JUSTIFICADO"));
        n.setJornadaId(input.getJornadaId());
        n.setSucursalId(input.getSucursalId());
        n.setObservacion(input.getObservacion());
        if (input.getRegistradoPorId() != null)
            n.setRegistradoPor(usuarioService.findById(input.getRegistradoPorId()).orElse(null));
        justificativoService.save(n);
        service.anularAutoDeJornada(input.getJornadaId(), input.getSucursalId());
        return true;
    }
}
