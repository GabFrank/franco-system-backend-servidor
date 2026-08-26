package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Lote;
import com.franco.dev.domain.operaciones.dto.LoteDeProductoDto;
import com.franco.dev.domain.operaciones.enums.EstadoLote;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.repository.operaciones.LoteRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Maestro de lotes. Ver {@link Lote} para el racional del diseño.
 */
@Service
@AllArgsConstructor
public class LoteService extends CrudService<Lote, LoteRepository, Long> {

    private final LoteRepository repository;

    @Override
    public LoteRepository getRepository() {
        return repository;
    }

    /**
     * Normaliza el número de lote: trim + mayúsculas.
     *
     * Es la única implementación de esta regla en el sistema: evita que " lote2026101 " y
     * "LOTE2026101" terminen siendo dos lotes distintos. Se aplica en el backend y no solo en la
     * UI porque mobile y las llamadas directas al GraphQL son puertas de entrada independientes.
     *
     * @return el número normalizado, o null si venía vacío.
     */
    public static String normalizarNumeroLote(String numeroLote) {
        if (numeroLote == null) return null;
        String limpio = numeroLote.trim().toUpperCase();
        return limpio.isEmpty() ? null : limpio;
    }

    /**
     * Devuelve el lote existente para (producto, número) o lo crea si es la primera vez que entra.
     *
     * Esta es la pieza que hace que dos recepciones del mismo lote sumen en el mismo saldo en vez
     * de generar dos lotes distintos: la segunda vez reutiliza la fila y respeta las fechas ya
     * registradas.
     *
     * Si el lote ya existe pero le faltaba una fecha y ahora sí viene, se completa. Nunca se pisa
     * una fecha ya cargada: corregirla es una acción explícita del usuario, no un efecto colateral
     * de recibir mercadería. Sin esta regla un tipeo en una recepción reordenaría el FEFO de stock
     * que ya está en góndola.
     *
     * @param fechaRetiroManual fecha cargada por el operador en la verificación. Opcional: si es
     *                          null se deriva de los días de vencimiento del producto.
     */
    @Transactional
    public Lote obtenerOCrear(Producto producto, String numeroLote, LocalDate fechaVencimiento,
                              LocalDate fechaRetiroManual, Proveedor proveedor, Usuario usuario) {
        String numero = normalizarNumeroLote(numeroLote);
        if (producto == null || producto.getId() == null || numero == null) {
            return null;
        }

        LocalDate fechaRetiro = resolverFechaRetiro(producto, fechaVencimiento, fechaRetiroManual);

        Lote existente = repository.findByProductoIdAndNumeroLote(producto.getId(), numero).orElse(null);
        if (existente != null) {
            boolean modificado = false;
            if (existente.getFechaVencimiento() == null && fechaVencimiento != null) {
                existente.setFechaVencimiento(fechaVencimiento);
                modificado = true;
            }
            // Independiente del vencimiento: el operador puede cargar solo la fecha de retiro, y
            // un lote viejo sin retiro debe poder completarse aunque su vencimiento ya esté.
            if (existente.getFechaRetiro() == null && fechaRetiro != null) {
                existente.setFechaRetiro(fechaRetiro);
                modificado = true;
            }
            if (existente.getProveedor() == null && proveedor != null) {
                existente.setProveedor(proveedor);
                modificado = true;
            }
            if (modificado) {
                existente.setActualizadoEn(LocalDateTime.now());
                return repository.save(existente);
            }
            return existente;
        }

        Lote lote = new Lote();
        lote.setProducto(producto);
        lote.setNumeroLote(numero);
        lote.setFechaVencimiento(fechaVencimiento);
        lote.setFechaRetiro(fechaRetiro);
        lote.setProveedor(proveedor);
        lote.setUsuario(usuario);
        lote.setEstado(EstadoLote.LIBERADO);
        lote.setCreadoEn(LocalDateTime.now());
        return repository.save(lote);
    }

    /**
     * Lo que carga el operador manda sobre el cálculo automático. Sin carga manual se mantiene el
     * comportamiento histórico, así que los productos que ya funcionaban con días de vencimiento
     * no cambian de conducta.
     */
    private LocalDate resolverFechaRetiro(Producto producto, LocalDate fechaVencimiento,
                                          LocalDate fechaRetiroManual) {
        return fechaRetiroManual != null
                ? fechaRetiroManual
                : calcularFechaRetiro(producto, fechaVencimiento);
    }

    /**
     * Fecha a partir de la cual la mercadería debe salir de stock, derivada de los días
     * configurados en el producto. FEFO ordena por esta fecha, no por el vencimiento: la idea es
     * sacar el producto antes de que efectivamente venza, no el último día.
     *
     * Sin vencimiento o sin días configurados no hay fecha de retiro, y FEFO cae al vencimiento.
     */
    private LocalDate calcularFechaRetiro(Producto producto, LocalDate fechaVencimiento) {
        if (fechaVencimiento == null || producto == null) return null;
        Integer dias = producto.getDiasVencimiento();
        if (dias == null || dias <= 0) return null;
        return fechaVencimiento.minusDays(dias);
    }

    /**
     * Las dos fechas de un lote después de aplicar una corrección. Ver {@link #resolverFechas}.
     */
    public static final class FechasLote {

        private final LocalDate fechaVencimiento;
        private final LocalDate fechaRetiro;

        FechasLote(LocalDate fechaVencimiento, LocalDate fechaRetiro) {
            this.fechaVencimiento = fechaVencimiento;
            this.fechaRetiro = fechaRetiro;
        }

        public LocalDate getFechaVencimiento() {
            return fechaVencimiento;
        }

        public LocalDate getFechaRetiro() {
            return fechaRetiro;
        }
    }

    /**
     * Qué fechas quedan cuando alguien corrige un lote.
     *
     * Es la regla más delicada del módulo: la fecha de retiro ordena FEFO en TODA la red, así que
     * lo que se escriba acá reordena stock que ya está en góndola en otras sucursales.
     *
     * <ul>
     * <li><b>Un nulo no borra.</b> El input no puede distinguir "no lo mandé" de "borralo", y
     * borrar un vencimiento no es un caso real del negocio.</li>
     * <li><b>Al cambiar el vencimiento se recalcula el retiro, pero solo si nadie lo había
     * cargado.</b> No hay forma de distinguir un retiro derivado de uno tipeado a mano, así que se
     * respeta el que está: pisarlo reordenaría el FEFO sin que nadie lo pidiera. Es la misma
     * decisión que ya toma {@link #obtenerOCrear}.</li>
     * <li><b>El retiro no puede ser posterior al vencimiento.</b> Hasta ahora lo garantizaba el
     * cálculo automático —una resta—, pero con carga manual deja de estar garantizado.</li>
     * </ul>
     *
     * @param diasVencimiento los del producto, para derivar el retiro. Puede ser nulo.
     * @throws IllegalArgumentException si el retiro resultante es posterior al vencimiento. El
     *                                  resolver lo traduce a un error de GraphQL.
     */
    public static FechasLote resolverFechas(LocalDate vencimientoActual, LocalDate retiroActual,
                                            LocalDate vencimientoNuevo, LocalDate retiroNuevo,
                                            Integer diasVencimiento) {
        LocalDate vencimiento = vencimientoNuevo != null ? vencimientoNuevo : vencimientoActual;

        LocalDate retiro;
        if (retiroNuevo != null) {
            retiro = retiroNuevo;
        } else if (retiroActual != null) {
            retiro = retiroActual;
        } else if (vencimiento != null && diasVencimiento != null && diasVencimiento > 0) {
            retiro = vencimiento.minusDays(diasVencimiento);
        } else {
            retiro = null;
        }

        if (vencimiento != null && retiro != null && retiro.isAfter(vencimiento)) {
            throw new IllegalArgumentException(
                    "La fecha de retiro no puede ser posterior al vencimiento del lote.");
        }

        return new FechasLote(vencimiento, retiro);
    }

    /**
     * Alta manual de un lote, sin tocar stock.
     *
     * Hasta ahora un lote solo nacía al recibir mercadería o desde {@code ajustarStockLote}, que
     * además mueve existencia. Eso deja sin camino el caso real del conteo: el operador tiene el
     * envase en la mano, el lote no está en el sistema y lo único que quiere es registrarlo para
     * poder contarlo. El stock lo pone después la finalización de la toma.
     *
     * El lote nace con saldo CERO —solo el maestro, ninguna fila del ledger— y en estado
     * {@code LIBERADO}.
     *
     * ⚠️ **Si el número ya existe para ese producto, devuelve el existente.** No es un error:
     * la unicidad es (producto, número) y ese lote ES el que el operador tiene en la mano.
     * Rechazarlo lo dejaría sin forma de seguir.
     */
    @Transactional
    public Lote crear(Producto producto, String numeroLote, LocalDate fechaVencimiento,
                      LocalDate fechaRetiro, String observacion, Usuario usuario) {
        if (producto == null || producto.getId() == null) {
            throw new IllegalArgumentException("Falta el producto del lote.");
        }
        if (!Boolean.TRUE.equals(producto.getLote())) {
            // Un lote de un producto que no lleva control de lote es un maestro que nadie va a
            // consultar nunca: ni FEFO ni el conteo lo miran.
            throw new IllegalArgumentException("El producto '" + producto.getDescripcion()
                    + "' no tiene control de lote.");
        }
        String numero = normalizarNumeroLote(numeroLote);
        if (numero == null) {
            throw new IllegalArgumentException("El número de lote es obligatorio.");
        }

        // Misma regla que al corregir: valida retiro <= vencimiento y deriva el retiro de los días
        // del producto cuando nadie lo carga.
        FechasLote fechas = resolverFechas(null, null, fechaVencimiento, fechaRetiro,
                producto.getDiasVencimiento());

        Lote lote = obtenerOCrear(producto, numero, fechas.getFechaVencimiento(),
                fechas.getFechaRetiro(), null, usuario);
        if (lote != null && observacion != null && !observacion.trim().isEmpty()) {
            lote.setObservacion(observacion.trim());
            lote.setActualizadoEn(LocalDateTime.now());
            return repository.save(lote);
        }
        return lote;
    }

    /**
     * Carga o corrige las fechas del maestro de un lote.
     *
     * Es la puerta que faltaba: hasta ahora la fecha de retiro solo se podía setear al CREAR el
     * lote —desde la recepción o desde el ajuste—, así que un lote viejo sin retiro no había forma
     * de completarlo, y uno con una fecha mal tipeada no había forma de corregirlo.
     *
     * El cambio es GLOBAL: el maestro es uno por (producto, número de lote) y replica MAIN_TO_ALL,
     * así que reordena el FEFO en toda la red. Quien llama tiene que haberlo dicho en pantalla.
     */
    @Transactional
    public Lote actualizarFechas(Long loteId, LocalDate fechaVencimiento, LocalDate fechaRetiro,
                                 String motivo, Usuario usuario) {
        Lote lote = repository.findById(loteId)
                .orElseThrow(() -> new IllegalArgumentException("Lote no encontrado: " + loteId));

        Integer dias = lote.getProducto() != null ? lote.getProducto().getDiasVencimiento() : null;
        FechasLote fechas = resolverFechas(lote.getFechaVencimiento(), lote.getFechaRetiro(),
                fechaVencimiento, fechaRetiro, dias);

        lote.setFechaVencimiento(fechas.getFechaVencimiento());
        lote.setFechaRetiro(fechas.getFechaRetiro());
        if (motivo != null && !motivo.trim().isEmpty()) {
            lote.setObservacion(motivo.trim());
        }
        if (usuario != null) {
            lote.setUsuario(usuario);
        }
        lote.setActualizadoEn(LocalDateTime.now());
        return repository.save(lote);
    }

    /**
     * Cambia el estado de un lote. Es el mecanismo de recall: pasar a BLOQUEADO saca el lote de
     * FEFO y del mostrador en todas las sucursales, sin tocar el stock físico.
     */
    @Transactional
    public Lote cambiarEstado(Long loteId, EstadoLote estado, String observacion, Usuario usuario) {
        Lote lote = repository.findById(loteId)
                .orElseThrow(() -> new IllegalArgumentException("Lote no encontrado: " + loteId));
        lote.setEstado(estado);
        if (observacion != null) {
            lote.setObservacion(observacion);
        }
        if (usuario != null) {
            lote.setUsuario(usuario);
        }
        lote.setActualizadoEn(LocalDateTime.now());
        return repository.save(lote);
    }

    public List<Lote> findByProductoId(Long productoId) {
        return repository.findByProductoId(productoId);
    }

    /**
     * Buscador paginado de lotes de un producto, con el saldo de cada uno en una sucursal.
     *
     * Va paginado desde el backend porque un producto de rotación alta acumula un lote por
     * recepción: traerlos todos para que la pantalla filtre en memoria deja de funcionar apenas
     * pasan unas decenas.
     *
     * El texto filtra por número de lote y se normaliza igual que al crearlo, así que buscar
     * "l-20" encuentra "L-2026-88".
     */
    public Page<LoteDeProductoDto> buscarLotesDeProducto(Long productoId, Long sucursalId,
                                                          String texto, Pageable pageable) {
        if (productoId == null) {
            return Page.empty(pageable);
        }
        return repository.buscarLotesDeProducto(productoId, sucursalId, normalizarFiltro(texto), pageable)
                .map(p -> new LoteDeProductoDto(
                        p.getLoteId(),
                        p.getNumeroLote(),
                        p.getFechaVencimiento(),
                        p.getFechaRetiro(),
                        p.getEstado() != null ? EstadoLote.valueOf(p.getEstado()) : null,
                        p.getSaldo() != null ? p.getSaldo() : 0d,
                        p.getSaldoTotal() != null ? p.getSaldoTotal() : 0d));
    }

    /**
     * Un filtro vacío no filtra. El buscador genérico manda '%' cuando el usuario no escribió nada,
     * y ese comodín tiene que traer todo en vez de buscar un lote llamado literalmente "%".
     */
    private String normalizarFiltro(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim();
        if (limpio.isEmpty() || "%".equals(limpio)) {
            return null;
        }
        return limpio.toUpperCase();
    }
}
