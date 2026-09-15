package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CapturaMuestra;
import com.franco.dev.domain.financiero.FormatoTerminalPos;
import com.franco.dev.repository.financiero.CapturaMuestraRepository;
import com.franco.dev.service.financiero.ocr.CuponOcrService;
import com.franco.dev.service.financiero.ocr.DerivadorMapa;
import com.franco.dev.service.financiero.ocr.MotorOcr;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * El cupon de muestra con el que se configura un formato.
 *
 * <p><b>Por que esto vive en central y no se pide prestado al filial.</b> El ABM de formatos es de
 * central: es central quien publica el formato, su patron y su mapa a las 24 filiales. Un diseno
 * que dependiera de una captura del filial ademas no cerraba: la venta con tarjeta se bloquea
 * cuando la terminal no tiene formato, asi que <b>antes de registrar el formato no hay ni puede
 * haber capturas de ese ticket</b>. Solo habria servido para formatos que ya operan, que son
 * justamente los que menos lo necesitan.
 *
 * <p><b>El ciclo vivo sigue siendo en memoria; lo que se guarda es la evidencia.</b> El Map de
 * abajo es el estado de la captura mientras dura --ESPERANDO, LISTO, ERROR-- y se pierde en un
 * reinicio, que es aceptable porque el ciclo dura minutos. Pero la foto y lo que el OCR leyo SI se
 * persisten en {@code financiero.captura_muestra} apenas la lectura sale bien: sin eso no hay forma
 * de volver a mirar el cupon que produjo un mapa, ni de juntar el corpus que la etapa 6 necesita
 * para proponer el patron sola.
 *
 * <p><b>Lo que sigue siendo cierto del diseno original:</b> Una muestra vive lo que dura configurar un
 * formato --se sube la foto, se lee, se revisa el mapa propuesto, se guarda-- y despues no le
 * sirve a nadie. Lo que queda persistido es el resultado: las regiones, que si tienen tabla y se
 * replican. Guardar la muestra habria costado una migracion en una banda que ya esta casi agotada,
 * y un job de purga, para conservar algo que se descarta en la misma sesion.
 *
 * <p>La consecuencia hay que saberla: <b>un reinicio de central borra las muestras abiertas</b>. Es
 * aceptable porque el ciclo entero dura minutos y se rehace sacando otra foto. El dia que haga
 * falta el corpus de N cupones para el asistente (etapa 6), eso si necesita tabla, y es otra cosa.
 *
 * <p><b>De la imagen solo sobrevive lo que se usa.</b> Los bytes del JPEG se descartan apenas el
 * OCR corre: lo que se guarda son las lineas leidas y el tamano, que es lo unico que la derivacion
 * necesita para normalizar. Asi una muestra en memoria pesa kilobytes y no megabytes.
 */
@Slf4j
@Service
public class CapturaMuestraService {

    /** Una muestra abierta. */
    public static final class Muestra {
        public final String token;
        public final Long formatoTerminalPosId;
        public final LocalDateTime expiraEn;
        /** ESPERANDO | LISTO | ERROR */
        public volatile String estado = "ESPERANDO";
        public volatile String textoOcr;
        public volatile String error;
        public volatile Integer msOcr;
        /** Lo que la derivacion necesita. Se guarda esto y NO los bytes de la foto. */
        volatile MotorOcr.Resultado lectura;
        volatile int ancho, alto;
        /** El id de la fila persistida, cuando la lectura salio bien. */
        public volatile Long guardadaId;

        Muestra(String token, Long formatoId, LocalDateTime expira) {
            this.token = token;
            this.formatoTerminalPosId = formatoId;
            this.expiraEn = expira;
        }

        public boolean vencida() {
            return LocalDateTime.now().isAfter(expiraEn);
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int BYTES_TOKEN = 24;
    private static final int MINUTOS_VALIDEZ = 20;

    /**
     * Tope de muestras vivas a la vez.
     * <p>
     * Esto es memoria del proceso de central, no una tabla: sin tope, una pantalla que abriera
     * muestras en un bucle podria hacerla crecer sin limite. 50 es holgado --configurar un formato
     * es una tarea de a una-- y el barrido de vencidas corre antes de mirar el tope, asi que el
     * caso normal nunca lo toca.
     */
    private static final int MAX_VIVAS = 50;

    private final Map<String, Muestra> muestras = new ConcurrentHashMap<String, Muestra>();

    private final CuponOcrService ocr;
    private final DerivadorMapa derivador;
    private final CapturaMuestraRepository repository;

    /** Donde van los JPEG. Relativo al working dir si no es absoluto, igual que el filial. */
    private final String rutaImagenes;

    private static final DateTimeFormatter CARPETA = DateTimeFormatter.ofPattern("yyyy/MM");

    public CapturaMuestraService(CuponOcrService ocr, DerivadorMapa derivador,
                                 CapturaMuestraRepository repository,
                                 @Value("${frc.captura-muestra.ruta-imagenes:muestras}") String rutaImagenes) {
        this.ocr = ocr;
        this.derivador = derivador;
        this.repository = repository;
        this.rutaImagenes = rutaImagenes;
    }

    /** Abre una muestra y devuelve su token. */
    public Muestra abrir(Long formatoTerminalPosId) {
        if (!ocr.disponible()) {
            throw new IllegalStateException(
                    "El lector de cupones no esta disponible en el servidor, asi que no se puede "
                    + "derivar el mapa. El resto de la configuracion del formato funciona igual.");
        }
        purgarVencidas();
        if (muestras.size() >= MAX_VIVAS) {
            throw new IllegalStateException("Hay demasiadas capturas de muestra abiertas. "
                    + "Esperá unos minutos y probá de nuevo.");
        }

        byte[] bytes = new byte[BYTES_TOKEN];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Muestra m = new Muestra(token, formatoTerminalPosId,
                LocalDateTime.now().plusMinutes(MINUTOS_VALIDEZ));
        muestras.put(token, m);
        return m;
    }

    public Optional<Muestra> porToken(String token) {
        Muestra m = token == null ? null : muestras.get(token);
        return Optional.ofNullable(m);
    }

    /**
     * Recibe la foto y corre el OCR.
     *
     * <p>A diferencia de la captura del filial, el token <b>no se consume</b>: acá no hay una venta
     * que proteger de un doble registro, y al configurar un formato es normal sacar tres o cuatro
     * fotos hasta que salga una legible. Vence por tiempo, nada mas.
     */
    public Muestra recibir(String token, byte[] jpeg) {
        Muestra m = muestras.get(token);
        if (m == null) throw new IllegalArgumentException("codigo desconocido");
        if (m.vencida()) throw new IllegalStateException("el codigo vencio; pedi uno nuevo");

        try {
            int[] tam = ocr.tamano(jpeg);
            MotorOcr.Resultado r = ocr.leer(jpeg);
            if (r.lineas.isEmpty()) {
                m.estado = "ERROR";
                m.error = "no se leyo nada; asegurate de que la foto sea del cupon";
                return m;
            }
            m.lectura = r;
            m.ancho = tam[0];
            m.alto = tam[1];
            m.textoOcr = r.textoPorRenglones();
            m.msOcr = (int) r.msTotal;
            m.error = null;
            m.estado = "LISTO";
            // Recien acá, con una lectura buena: una foto que no se pudo leer no es evidencia de
            // nada y solo ocuparia disco.
            persistir(m, jpeg);
        } catch (Exception e) {
            log.error("fallo el OCR de la muestra {}", token, e);
            m.estado = "ERROR";
            m.error = "no se pudo leer la foto";
        }
        return m;
    }

    /**
     * Guarda la foto en disco y la fila que la describe.
     *
     * <p><b>No puede tumbar la captura.</b> Si el disco esta lleno o el directorio no se puede
     * crear, se loguea y se sigue: el operador ya tiene su lectura en pantalla y el mapa se deriva
     * igual desde la muestra en memoria. Perder la evidencia es molesto; perder la configuracion
     * del formato a mitad de camino, no.
     *
     * <p>Se guarda la fila primero para tener el id, y con el id se nombra el archivo: asi dos
     * muestras del mismo minuto no se pisan.
     */
    private void persistir(Muestra m, byte[] jpeg) {
        try {
            FormatoTerminalPos formato = new FormatoTerminalPos();
            formato.setId(m.formatoTerminalPosId);

            CapturaMuestra fila = new CapturaMuestra();
            fila.setFormatoTerminalPos(formato);
            fila.setToken(m.token);
            fila.setAncho(m.ancho);
            fila.setAlto(m.alto);
            fila.setTextoOcr(m.textoOcr);
            fila.setMsOcr(m.msOcr);
            fila = repository.save(fila);

            String relativa = LocalDateTime.now().format(CARPETA) + "/" + fila.getId() + ".jpg";
            Path destino = Paths.get(rutaImagenes, relativa);
            Files.createDirectories(destino.getParent());
            Files.write(destino, jpeg);

            fila.setRutaImagen(relativa);
            repository.save(fila);
            m.guardadaId = fila.getId();
        } catch (IOException e) {
            log.error("no se pudo guardar la imagen de la muestra {}", m.token, e);
        } catch (Exception e) {
            log.error("no se pudo persistir la muestra {}", m.token, e);
        }
    }

    /** Las muestras guardadas de un formato, la mas nueva primero. */
    public List<CapturaMuestra> guardadasDe(Long formatoTerminalPosId) {
        return repository.findByFormatoTerminalPosIdOrderByCreadoEnDesc(formatoTerminalPosId);
    }

    /** Los bytes de una muestra guardada, si el archivo sigue estando. */
    public Optional<byte[]> imagenDe(Long id) {
        return repository.findById(id).flatMap(f -> {
            if (f.getRutaImagen() == null) return Optional.<byte[]>empty();
            try {
                Path p = Paths.get(rutaImagenes, f.getRutaImagen());
                if (!Files.exists(p)) return Optional.<byte[]>empty();
                return Optional.of(Files.readAllBytes(p));
            } catch (IOException e) {
                log.error("no se pudo leer la imagen de la muestra {}", id, e);
                return Optional.<byte[]>empty();
            }
        });
    }

    /**
     * Borra una muestra guardada, con su archivo.
     *
     * <p>Hace falta poder borrar: una foto salio movida, se derivo con el cupon equivocado, o el
     * ticket quedo con datos que no se quieren conservar. Sin esto la unica salida era esperar la
     * purga, que son seis meses.
     *
     * <p>El archivo primero y la fila despues, igual que la purga: al reves, un fallo entre las dos
     * deja un archivo huerfano que nadie va a volver a mirar ni a borrar.
     */
    public boolean eliminar(Long id) {
        return repository.findById(id).map(f -> {
            try {
                if (f.getRutaImagen() != null) {
                    Files.deleteIfExists(Paths.get(rutaImagenes, f.getRutaImagen()));
                }
            } catch (IOException e) {
                // Se borra la fila igual: una fila que apunta a un archivo que no se pudo borrar es
                // peor que un archivo suelto --sigue mostrandose en la pantalla y falla al abrirla--.
                log.error("no se pudo borrar la imagen de la muestra {}", id, e);
            }
            repository.delete(f);
            return true;
        }).orElse(false);
    }

    /**
     * Borra las muestras mas viejas que la fecha dada, con su archivo. Devuelve cuantas borro.
     *
     * <p>El archivo primero y la fila despues: al reves, un fallo entre las dos deja un archivo
     * huerfano que nadie va a volver a mirar ni a borrar.
     */
    public int purgarAnterioresA(LocalDateTime limite) {
        int borradas = 0;
        for (CapturaMuestra f : repository.findByCreadoEnBefore(limite)) {
            try {
                if (f.getRutaImagen() != null) {
                    Files.deleteIfExists(Paths.get(rutaImagenes, f.getRutaImagen()));
                }
                repository.delete(f);
                borradas++;
            } catch (Exception e) {
                log.error("no se pudo purgar la muestra {}", f.getId(), e);
            }
        }
        return borradas;
    }

    /**
     * Propone el mapa a partir de la muestra ya leida.
     *
     * <p>No persiste nada: la propuesta la revisa una persona y la guarda
     * {@code guardarRegionesDerivadas}, que es quien aplica las reglas de sobrescritura.
     */
    public DerivadorMapa.Resultado derivar(String token, FormatoTerminalPos formato) {
        Muestra m = muestras.get(token);
        if (m == null) {
            return DerivadorMapa.Resultado.fallo("esa captura de muestra ya no existe; sacá otra foto");
        }
        if (!"LISTO".equals(m.estado) || m.lectura == null) {
            return DerivadorMapa.Resultado.fallo("todavía no hay una lectura buena de esta muestra");
        }
        // La muestra se abrio PARA un formato, y tiene que derivarse contra ese. Sin este chequeo,
        // una foto sacada para el formato A se podia aplicar al patron del formato B --por
        // reutilizar un token entre pantallas-- y el mapa resultante describiria cajas de un
        // ticket que no es el suyo, sin que nada avisara. Despues ese mapa baja a las 24 filiales.
        if (m.formatoTerminalPosId != null && !m.formatoTerminalPosId.equals(formato.getId())) {
            return DerivadorMapa.Resultado.fallo(
                    "esta foto se saco para otro formato; saca una nueva desde este");
        }
        // El mapeo va junto con el patron: sin el, la region saldria nombrada con el GRUPO
        // --`auth`-- y no con la clave del mapeo --`codigoAutorizacion`--, y la validacion del ABM
        // la rechazaria con "el mapeo no produce el campo auth". Medido de punta a punta el
        // 2026-09-12, corriendo el flujo completo desde la pantalla.
        return derivador.derivar(m.lectura.lineas, formato.getPatron(), formato.getMapeo(),
                m.ancho, m.alto);
    }

    /**
     * Saca las vencidas.
     * <p>
     * Sin scheduler: corre al abrir una muestra nueva, que es el unico momento en que el mapa
     * puede crecer. Un @Scheduled seria un hilo mas para barrer un mapa que casi siempre esta
     * vacio.
     */
    private void purgarVencidas() {
        Iterator<Map.Entry<String, Muestra>> it = muestras.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().vencida()) it.remove();
        }
    }

    /** Cierra una muestra cuando el usuario termino. */
    public void cerrar(String token) {
        if (token != null) muestras.remove(token);
    }

    public int minutosValidez() {
        return MINUTOS_VALIDEZ;
    }

    /** Solo para el chequeo de disponibilidad de la pantalla. */
    public boolean lectorDisponible() {
        return ocr.disponible();
    }

    /** Las lineas leidas, para mostrar el texto crudo al administrador. */
    public List<MotorOcr.Linea> lineasDe(String token) {
        Muestra m = muestras.get(token);
        return m == null || m.lectura == null ? null : m.lectura.lineas;
    }
}
