package com.franco.dev.graphql.administrativo;

import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.graphql.administrativo.input.MarcacionInput;
import com.franco.dev.service.administrativo.MarcacionService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphqlErrorException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class MarcacionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final Logger log = LoggerFactory.getLogger(MarcacionGraphQL.class);

    /** Cuantas veces se intenta guardar una marcacion que choca con otra transaccion. */
    static final int INTENTOS_GUARDADO = 5;

    /**
     * Techo de la espera antes del segundo intento; crece con cada uno. La espera real se sortea
     * por debajo de ese techo, y el sorteo es lo que importa: con una espera fija, las que
     * chocaron juntas reintentan juntas y vuelven a chocar. Medido contra una base real con 4
     * marcaciones simultaneas (MarcacionReintentoConcurrenteIT): con 3 intentos y espera fija, 4
     * de 24 se quedaban sin guardar.
     */
    static final long ESPERA_REINTENTO_MS = 120;

    static final String MENSAJE_CHOQUE =
            "No se pudo registrar la marcación porque se estaba guardando otra al mismo tiempo. Volvé a intentar.";

    /** Lo que Postgres cancela para que la aplicacion reintente: fallo de serializacion y deadlock. */
    private static final String SQLSTATE_SERIALIZACION = "40001";
    private static final String SQLSTATE_DEADLOCK = "40P01";

    @Autowired
    private MarcacionService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private ImpresionService impresionService;

    @Autowired
    private com.franco.dev.service.administrativo.JornadaService jornadaService;

    public Optional<Marcacion> marcacion(Long id, Long sucursalId) {
        return service.findById(new EmbebedPrimaryKey(id, sucursalId));
    }

    public Page<Marcacion> marcaciones(String fechaInicio, String fechaFin, Integer page, Integer size) {
        if (page == null)
            page = 0;
        if (size == null)
            size = 10;
        if (fechaInicio != null && fechaFin != null) {
            return service.findByFechaRange(fechaInicio, fechaFin, page, size);
        }
        return service.findAllPaged(page, size);
    }

    public Page<Marcacion> marcacionesPorUsuario(Long usuarioId, String fechaInicio, String fechaFin, Integer page,
            Integer size) {
        if (page == null)
            page = 0;
        if (size == null)
            size = 10;
        if (fechaInicio != null && fechaFin != null) {
            return service.findByUsuarioIdAndFechaRange(usuarioId, fechaInicio, fechaFin, page, size);
        }
        return service.findByUsuarioId(usuarioId, page, size);
    }

    /**
     * Guarda la marcacion, reintentando si choca con otra transaccion.
     *
     * MarcacionService.save corre en SERIALIZABLE y lee max(id) adentro: dos marcaciones casi
     * simultaneas —aunque sean de sucursales distintas— hacen que Postgres cancele una con
     * SQLState 40001 y "the transaction might succeed if retried". Nadie reintentaba, y a quien
     * marcaba le salia el texto de Hibernate (bodega, 06/10/2026 08:00:48).
     *
     * El reintento va aca y no sobre el servicio porque tiene que envolver a la transaccion, y
     * este metodo no es transaccional. Cada intento arma la entidad de nuevo desde el input: ver
     * {@link #guardar}.
     */
    public Marcacion saveMarcacion(MarcacionInput marcacion) {
        for (int intento = 1; ; intento++) {
            try {
                return guardar(marcacion);
            } catch (RuntimeException ex) {
                if (!esChoqueDeTransacciones(ex)) {
                    throw ex;
                }
                if (intento >= INTENTOS_GUARDADO) {
                    log.error("Marcación no guardada tras {} intentos por choque de transacciones. usuario={} sucursal={}",
                            intento, marcacion.getUsuarioId(), marcacion.getSucursalId(), ex);
                    throw errorDeChoque(ex);
                }
                log.warn("Choque de transacciones al guardar marcación, reintentando ({}/{}). usuario={} sucursal={}",
                        intento, INTENTOS_GUARDADO, marcacion.getUsuarioId(), marcacion.getSucursalId());
                if (!esperar(ThreadLocalRandom.current().nextLong(10, ESPERA_REINTENTO_MS * intento))) {
                    throw errorDeChoque(ex);
                }
            }
        }
    }

    /**
     * Si en la cadena de causas hay un fallo de serializacion o un deadlock de Postgres.
     *
     * Se mira el SQLState y no la clase de Spring: ConcurrencyFailureException tambien cubre el
     * lock timeout y el bloqueo optimista, que no son este caso. Y se recorre la cadena porque
     * el fallo llega envuelto distinto segun donde salte: MarcacionService.procesarJornada lo
     * mete en un RuntimeException, y en el commit viene dentro de TransactionSystemException.
     */
    static boolean esChoqueDeTransacciones(Throwable ex) {
        int saltos = 0;
        for (Throwable causa = ex; causa != null && saltos < 20; causa = causa.getCause(), saltos++) {
            if (causa instanceof SQLException) {
                String estado = ((SQLException) causa).getSQLState();
                if (SQLSTATE_SERIALIZACION.equals(estado) || SQLSTATE_DEADLOCK.equals(estado)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Error listo para mostrar: GraphqlExceptionHandler desanida los GraphQLError, asi que llega
     * al cliente sin el prefijo "Exception while fetching data".
     */
    private static GraphqlErrorException errorDeChoque(RuntimeException causa) {
        return GraphqlErrorException.newErrorException().message(MENSAJE_CHOQUE).cause(causa).build();
    }

    /** Devuelve false si el hilo fue interrumpido: ahi no se reintenta. */
    private static boolean esperar(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Arma la entidad desde el input y la guarda. Un intento.
     *
     * Se arma de cero en cada intento, y no es un detalle: el intento fallido deja la entidad
     * con el id que calculo, MarcacionService.prepararMarcacion solo asigna id si viene nulo, y
     * guardar con un id ya puesto es un merge. Reusar el objeto mandaria un id viejo que otra
     * transaccion pudo haber ocupado, y pisaria la marcacion de otra persona.
     */
    private Marcacion guardar(MarcacionInput marcacion) {
        Marcacion e = new Marcacion();
        if (marcacion.getId() != null && marcacion.getSucursalId() != null) {
            Optional<Marcacion> existing = service
                    .findById(new EmbebedPrimaryKey(marcacion.getId(), marcacion.getSucursalId()));
            if (existing.isPresent()) {
                e = existing.get();
            } else {
                e.setId(marcacion.getId());
                e.setSucursalId(marcacion.getSucursalId());
            }
        } else {
            if (marcacion.getId() != null)
                e.setId(marcacion.getId());
            if (marcacion.getSucursalId() != null)
                e.setSucursalId(marcacion.getSucursalId());
        }
        if (marcacion.getDeviceId() != null)
            e.setDeviceId(marcacion.getDeviceId());
        if (marcacion.getDeviceInfo() != null)
            e.setDeviceInfo(marcacion.getDeviceInfo());
        if (marcacion.getCodigo() != null)
            e.setCodigo(marcacion.getCodigo());
        if (marcacion.getTipo() != null)
            e.setTipo(marcacion.getTipo());

        if (marcacion.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(marcacion.getUsuarioId()).orElse(null));
        }

        if (marcacion.getSucursalEntradaId() != null) {
            e.setSucursalEntrada(sucursalService.findById(marcacion.getSucursalEntradaId()).orElse(null));
        } else if (marcacion.getSucursalId() != null) {
            e.setSucursalEntrada(sucursalService.findById(marcacion.getSucursalId()).orElse(null));
        }

        if (marcacion.getSucursalSalidaId() != null) {
            e.setSucursalSalida(sucursalService.findById(marcacion.getSucursalSalidaId()).orElse(null));
        } else if (marcacion.getSucursalId() != null
                && marcacion.getTipo() == com.franco.dev.domain.administrativo.enums.TipoMarcacion.SALIDA) {
            e.setSucursalSalida(sucursalService.findById(marcacion.getSucursalId()).orElse(null));
        }

        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        if (marcacion.getFechaEntrada() != null) {
            e.setFechaEntrada(LocalDateTime.parse(marcacion.getFechaEntrada(), formatter));
        }
        if (marcacion.getFechaSalida() != null) {
            e.setFechaSalida(LocalDateTime.parse(marcacion.getFechaSalida(), formatter));
        }

        if (marcacion.getEsSalidaAlmuerzo() != null) {
            e.setEsSalidaAlmuerzo(marcacion.getEsSalidaAlmuerzo());
        }

        if (marcacion.getLatitud() != null)
            e.setLatitud(marcacion.getLatitud());
        if (marcacion.getLongitud() != null)
            e.setLongitud(marcacion.getLongitud());
        if (marcacion.getPrecisionGps() != null)
            e.setPrecisionGps(marcacion.getPrecisionGps());
        if (marcacion.getDistanciaSucursalMetros() != null)
            e.setDistanciaSucursalMetros(marcacion.getDistanciaSucursalMetros());

        // Como se identifico a la persona. Se copian solo si vinieron, igual que el resto:
        // el desktop llama a este mismo metodo y no los manda.
        if (marcacion.getMetodoRegistro() != null)
            e.setMetodoRegistro(marcacion.getMetodoRegistro());
        if (marcacion.getSimilitudFacial() != null)
            e.setSimilitudFacial(marcacion.getSimilitudFacial());
        if (marcacion.getMargenSegundoCandidato() != null)
            e.setMargenSegundoCandidato(marcacion.getMargenSegundoCandidato());

        return service.save(e);
    }

    public Marcacion reprocesarJornadaDeMarcacion(Long id, Long sucursalId) {
        return service.reprocesarJornadaDeMarcacion(id, sucursalId);
    }

    public String imprimirReporteMarcaciones(Long usuarioId, String fechaInicio, String fechaFin,
            Long usuarioResponsableId) {
        com.franco.dev.domain.personas.Usuario usuarioReporte = null;

        if (usuarioResponsableId != null) {
            usuarioReporte = usuarioService.findById(usuarioResponsableId).orElse(null);
        }

        List<com.franco.dev.domain.administrativo.Jornada> jornadaList;
        if (usuarioId != null && fechaInicio != null && fechaFin != null) {
            jornadaList = jornadaService.findByUsuarioIdAndFechaRange(usuarioId, fechaInicio, fechaFin);
        } else if (usuarioId != null) {
            jornadaList = jornadaService.findByUsuarioId(usuarioId);
        } else if (fechaInicio != null && fechaFin != null) {
            jornadaList = jornadaService.findByFechaRange(fechaInicio, fechaFin);
        } else {
            jornadaList = jornadaService.findAll2();
        }

        return impresionService.imprimirMarcaciones(jornadaList, fechaInicio, fechaFin, usuarioReporte);
    }

}
