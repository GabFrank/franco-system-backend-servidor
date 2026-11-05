package com.franco.dev.service.operaciones;

import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.NotaRecepcionItem;
import com.franco.dev.domain.operaciones.NotaRecepcionItemDistribucion;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.PedidoItemDistribucion;
import com.franco.dev.domain.operaciones.enums.DestinoRechazoNotaRecepcion;
import com.franco.dev.domain.operaciones.enums.NotaRecepcionEstado;
import com.franco.dev.domain.operaciones.enums.NotaRecepcionItemEstado;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.graphql.operaciones.dto.RechazoNotaRecepcionItemResultadoDTO;
import com.franco.dev.graphql.operaciones.input.DividirPedidoItemInput;
import com.franco.dev.graphql.operaciones.input.NotaRecepcionItemInput;
import com.franco.dev.graphql.operaciones.input.ParteDivisionPedidoItemInput;
import com.franco.dev.graphql.operaciones.input.RechazarNotaRecepcionItemInput;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
import graphql.GraphQLException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Recepción documental: guardar un ítem de nota, rechazarlo y dividir un ítem del pedido en notas.
 *
 * El rechazo y la división antes los armaba el desktop con varias mutaciones sueltas y un corte a mitad dejaba datos a
 * medias (#488, #501). Acá son una transacción que hace los mismos pasos con los mismos datos: el resultado guardado es
 * el de siempre, solo que ahora es todo o nada.
 */
@Service
public class RecepcionDocumentalService {

    static final String SIN_COTIZACION = "No se pudo obtener la cotización: el rechazo no se registró. Intentá de nuevo.";
    static final String SIN_MONEDA = "No se conoce la moneda del pedido: el rechazo no se registró. Recargá el pedido.";

    @Autowired
    private NotaRecepcionItemService notaRecepcionItemService;
    @Autowired
    private NotaRecepcionService notaRecepcionService;
    @Autowired
    private PedidoItemService pedidoItemService;
    @Autowired
    private PresentacionService presentacionService;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private NotaRecepcionItemDistribucionService notaRecepcionItemDistribucionService;
    @Autowired
    private PedidoItemDistribucionService pedidoItemDistribucionService;
    @Autowired
    private CambioService cambioService;

    /**
     * Alta o edición de un ítem de nota (es el cuerpo de {@code saveNotaRecepcionItem}, sin cambios): guarda, recalcula
     * el valor de la nota y, si el ítem es de un pedido y no tiene distribuciones, las crea en proporción a las del pedido.
     */
    public NotaRecepcionItem guardarItem(NotaRecepcionItemInput input) {
        ModelMapper m = new ModelMapper();
        NotaRecepcionItem e = m.map(input, NotaRecepcionItem.class);
        if(input.getNotaRecepcionId()!=null) e.setNotaRecepcion(notaRecepcionService.findById(input.getNotaRecepcionId()).orElse(null));
        if(input.getPedidoItemId()!=null) e.setPedidoItem(pedidoItemService.findById(input.getPedidoItemId()).orElse(null));
        if(input.getPresentacionEnNotaId()!=null) e.setPresentacionEnNota(presentacionService.findById(input.getPresentacionEnNotaId()).orElse(null));
        if(input.getUsuarioId()!=null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        
        // Guardar el item
        NotaRecepcionItem savedItem = notaRecepcionItemService.save(e);
        if (savedItem.getNotaRecepcion() != null && savedItem.getNotaRecepcion().getId() != null) {
            notaRecepcionService.actualizarValorNota(savedItem.getNotaRecepcion().getId());
        }
        // Si tiene pedidoItem, crear distribuciones automáticamente basándose en PedidoItemDistribucion
        // Esto es necesario cuando se divide un item manualmente para cargarlo en múltiples notas
        if (savedItem.getPedidoItem() != null && savedItem.getPedidoItem().getId() != null) {
            // Verificar si ya tiene distribuciones
            List<NotaRecepcionItemDistribucion> distribucionesExistentes = 
                notaRecepcionItemDistribucionService.findByNotaRecepcionItemId(savedItem.getId());
            
            // Solo crear si no tiene distribuciones
            if (distribucionesExistentes.isEmpty()) {
                // Obtener distribuciones del pedidoItem
                List<PedidoItemDistribucion> pedidoDistribuciones = 
                    pedidoItemDistribucionService.findByPedidoItemId(savedItem.getPedidoItem().getId());
                
                if (pedidoDistribuciones != null && !pedidoDistribuciones.isEmpty()) {
                    List<NotaRecepcionItemDistribucion> distribuciones = new ArrayList<>();
                    
                    for (PedidoItemDistribucion pedidoDist : pedidoDistribuciones) {
                        NotaRecepcionItemDistribucion distribucion = new NotaRecepcionItemDistribucion();
                        distribucion.setNotaRecepcionItem(savedItem);
                        distribucion.setSucursalInfluencia(pedidoDist.getSucursalInfluencia());
                        distribucion.setSucursalEntrega(pedidoDist.getSucursalEntrega());
                        // Calcular cantidad proporcional basándose en cantidadEnNota vs cantidadSolicitada
                        // Usar cantidadSolicitada del pedidoItem
                        Double cantidadSolicitada = savedItem.getPedidoItem().getCantidadSolicitada();
                        Double cantidadProporcional = calcularCantidadProporcional(
                            savedItem.getCantidadEnNota(),
                            cantidadSolicitada,
                            pedidoDist.getCantidadAsignada()
                        );
                        distribucion.setCantidad(cantidadProporcional);
                        distribucion.setCreadoEn(LocalDateTime.now());
                        distribuciones.add(distribucion);
                    }
                    
                    // Guardar las distribuciones
                    if (!distribuciones.isEmpty()) {
                        notaRecepcionItemDistribucionService.saveDistribuciones(distribuciones);
                    }
                }
            }
        }
        
        return savedItem;
    }

    /**
     * Calcula la cantidad proporcional para una distribución cuando se divide un item
     * @param cantidadEnNota Cantidad del item en la nota
     * @param cantidadSolicitada Cantidad solicitada original del pedidoItem
     * @param cantidadAsignada Cantidad asignada en la distribución del pedidoItem
     * @return Cantidad proporcional para la distribución del notaRecepcionItem
     */
    private Double calcularCantidadProporcional(Double cantidadEnNota, Double cantidadSolicitada, Double cantidadAsignada) {
        if (cantidadSolicitada == null || cantidadSolicitada == 0) {
            return cantidadAsignada; // Si no hay cantidad solicitada, usar la asignada completa
        }
        if (cantidadEnNota == null || cantidadEnNota == 0) {
            return 0.0;
        }
        if (cantidadAsignada == null) {
            return 0.0;
        }
        // Calcular proporción: (cantidadEnNota / cantidadSolicitada) * cantidadAsignada
        return (cantidadEnNota / cantidadSolicitada) * cantidadAsignada;
    }

    // ---------------------------------------------------------------- Rechazo

    @Transactional
    public RechazoNotaRecepcionItemResultadoDTO rechazar(RechazarNotaRecepcionItemInput input) {
        validarRechazo(input);
        Presentacion presentacion = presentacion(input.getPresentacionId());
        double cantidadBase = aUnidadesBase(input.getCantidad(), presentacion);

        if (input.getNotaRecepcionItemId() == null) {
            PedidoItem pedidoItem = pedidoItem(input.getPedidoItemId());
            NotaRecepcionItemInput rechazo = NotaRecepcionItemInput.desdePedidoItem(pedidoItem, presentacion, cantidadBase,
                    NotaRecepcionItemEstado.RECHAZADO, input.getObservacion(), input.getMotivoRechazo());
            return guardarRechazoEnDestino(input, rechazo, pedidoItem.getPedido(), notaElegida(input), cantidadBase);
        }

        NotaRecepcionItem item = notaRecepcionItemService.findById(input.getNotaRecepcionItemId())
                .orElseThrow(() -> new GraphQLException("El ítem a rechazar ya no existe. Recargá la nota."));
        double cantidadOriginal = item.getCantidadEnNota() != null ? item.getCantidadEnNota() : 0;
        if (cantidadBase < cantidadOriginal) {
            return rechazarParcial(input, item, presentacion, cantidadOriginal, cantidadBase);
        }
        // Total: el mismo ítem pasa a RECHAZADO (conserva su presentación y su cantidad)
        NotaRecepcionItemInput rechazo = NotaRecepcionItemInput.copiaDe(item);
        marcarRechazado(rechazo, input);
        return guardarRechazoEnDestino(input, rechazo, pedidoDe(item), item.getNotaRecepcion(), cantidadBase);
    }

    private void validarRechazo(RechazarNotaRecepcionItemInput input) {
        if (input.getDestino() == null) {
            throw new GraphQLException("Elegí a qué nota va el rechazo.");
        }
        if (input.getCantidad() == null || input.getCantidad() <= 0) {
            throw new GraphQLException("La cantidad a rechazar debe ser mayor a cero.");
        }
        if (input.getMotivoRechazo() == null || input.getMotivoRechazo().trim().isEmpty()) {
            throw new GraphQLException("El motivo del rechazo es obligatorio.");
        }
        if (input.getNotaRecepcionItemId() == null && input.getPedidoItemId() == null) {
            throw new GraphQLException("Falta el ítem del pedido a rechazar.");
        }
    }

    /** Se reduce el ítem original a lo que queda y se crea otro ítem RECHAZADO con lo rechazado. */
    private RechazoNotaRecepcionItemResultadoDTO rechazarParcial(RechazarNotaRecepcionItemInput input, NotaRecepcionItem item,
                                                                 Presentacion presentacion, double cantidadOriginal,
                                                                 double cantidadBase) {
        NotaRecepcionItemInput restante = NotaRecepcionItemInput.copiaDe(item);
        restante.setCantidadEnNota(cantidadOriginal - cantidadBase);
        guardarItem(restante);

        NotaRecepcionItemInput rechazado = NotaRecepcionItemInput.copiaDe(item);
        rechazado.setId(null);
        rechazado.setCantidadEnNota(cantidadBase);
        if (presentacion != null) {
            rechazado.setPresentacionEnNotaId(presentacion.getId());
        }
        rechazado.setEsBonificacion(Boolean.TRUE.equals(item.getEsBonificacion()));
        marcarRechazado(rechazado, input);
        return new RechazoNotaRecepcionItemResultadoDTO(guardarItem(rechazado), cantidadBase);
    }

    private void marcarRechazado(NotaRecepcionItemInput item, RechazarNotaRecepcionItemInput input) {
        item.setEstado(NotaRecepcionItemEstado.RECHAZADO);
        item.setMotivoRechazo(input.getMotivoRechazo());
        item.setObservacion(input.getObservacion());
    }

    private NotaRecepcion notaElegida(RechazarNotaRecepcionItemInput input) {
        if (input.getDestino() != DestinoRechazoNotaRecepcion.NOTA_EXISTENTE) {
            return null;
        }
        if (input.getNotaRecepcionId() == null) {
            throw new GraphQLException("Elegí la nota de recepción del rechazo.");
        }
        return notaRecepcionService.findById(input.getNotaRecepcionId())
                .orElseThrow(() -> new GraphQLException("La nota elegida ya no existe. Recargá las notas."));
    }

    /** Guarda el ítem en la nota existente o en una nota nueva del pedido. */
    private RechazoNotaRecepcionItemResultadoDTO guardarRechazoEnDestino(RechazarNotaRecepcionItemInput input,
                                                                         NotaRecepcionItemInput rechazo, Pedido pedido,
                                                                         NotaRecepcion notaExistente, double cantidadBase) {
        NotaRecepcion nota = input.getDestino() == DestinoRechazoNotaRecepcion.NOTA_EXISTENTE
                ? notaExistente
                : crearNota(pedido, input.getDestino() == DestinoRechazoNotaRecepcion.NOTA_RECHAZO);
        rechazo.setNotaRecepcionId(nota != null ? nota.getId() : null);
        return new RechazoNotaRecepcionItemResultadoDTO(guardarItem(rechazo), cantidadBase);
    }

    /**
     * Nota nueva PENDIENTE_CONCILIACION en la moneda del pedido. Una moneda que no es guaraní necesita su cotización:
     * sin ella no se guarda (antes quedaba con 1 y el total en Gs salía mal, #390). Como {@code saveNotaRecepcion} desde
     * #439: la nota «de rechazo» queda marcada y numera aparte, y el alta lleva creado_en.
     */
    private NotaRecepcion crearNota(Pedido pedido, boolean deRechazo) {
        Moneda moneda = pedido != null ? pedido.getMoneda() : null;
        if (moneda == null) {
            throw new GraphQLException(SIN_MONEDA);
        }
        NotaRecepcion nota = new NotaRecepcion();
        nota.setPedido(pedido);
        nota.setNumero(siguienteNumero(pedido, deRechazo ? n -> Boolean.TRUE.equals(n.getEsNotaRechazo()) : n -> true));
        nota.setTipoBoleta("");
        nota.setFecha(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));
        nota.setMoneda(moneda);
        nota.setCotizacion(cotizacion(moneda));
        nota.setEstado(NotaRecepcionEstado.PENDIENTE_CONCILIACION);
        nota.setPagado(false);
        nota.setEsNotaRechazo(deRechazo);
        nota.setCreadoEn(LocalDateTime.now());
        return notaRecepcionService.save(nota);
    }

    /** El mayor número de las notas del pedido que cumplen el filtro, más uno. */
    private Integer siguienteNumero(Pedido pedido, Predicate<NotaRecepcion> filtro) {
        return notaRecepcionService.findByPedidoId(pedido.getId()).stream()
                .filter(filtro)
                .map(NotaRecepcion::getNumero)
                .filter(Objects::nonNull)
                .mapToInt(numero -> Math.max(numero, 0))
                .max()
                .orElse(0) + 1;
    }

    private Double cotizacion(Moneda moneda) {
        if ("GUARANI".equals(moneda.getDenominacion()) || moneda.getId() == null) {
            return 1.0;
        }
        Cambio cambio = cambioService.findLastByMonedaId(moneda.getId());
        Double tasa = cambio == null ? null
                : cambio.getValorEnGsCompraMercado() != null ? cambio.getValorEnGsCompraMercado()
                : cambio.getValorEnGsVentaMercado() != null ? cambio.getValorEnGsVentaMercado()
                : cambio.getValorEnGs();
        if (tasa == null || !(tasa > 0)) {
            throw new GraphQLException(SIN_COTIZACION);
        }
        return tasa;
    }

    // ---------------------------------------------------------------- División

    /**
     * Cada parte va a una nota ya cargada del pedido: la nota nueva se carga antes desde el desktop, con su número y
     * timbrado. Una parte sin cantidad o sin nota válida rechaza toda la división; antes se salteaba en silencio (nota 0
     * = «Crear Nueva Nota») y el desktop avisaba éxito sin haber guardado nada.
     */
    @Transactional
    public List<NotaRecepcionItem> dividir(DividirPedidoItemInput input) {
        PedidoItem pedidoItem = pedidoItem(input.getPedidoItemId());
        Presentacion presentacion = presentacion(input.getPresentacionId());
        List<ParteDivisionPedidoItemInput> partes = input.getPartes() == null ? List.of() : input.getPartes();
        if (partes.stream().noneMatch(Objects::nonNull)) {
            throw new GraphQLException("La división no tiene partes.");
        }
        Pedido pedido = pedidoItem.getPedido();
        List<NotaRecepcionItem> guardados = new ArrayList<>();
        int numeroParte = 0;
        for (ParteDivisionPedidoItemInput parte : partes) {
            if (parte == null) {
                continue;
            }
            numeroParte++;
            if (parte.getCantidad() == null || !(parte.getCantidad() > 0)) {
                throw new GraphQLException("La parte " + numeroParte + " no tiene cantidad: no se dividió el ítem.");
            }
            NotaRecepcion nota = notaDeParte(parte.getNotaRecepcionId(), pedido, numeroParte);
            NotaRecepcionItemInput item = NotaRecepcionItemInput.desdePedidoItem(pedidoItem, presentacion,
                    aUnidadesBase(parte.getCantidad(), presentacion), NotaRecepcionItemEstado.CONCILIADO, "", null);
            item.setNotaRecepcionId(nota.getId());
            guardados.add(guardarItem(item));
        }
        return guardados;
    }

    /** La nota elegida para una parte: tiene que existir y ser del mismo pedido. */
    private NotaRecepcion notaDeParte(Long notaRecepcionId, Pedido pedido, int numeroParte) {
        if (notaRecepcionId == null || notaRecepcionId <= 0) {
            throw new GraphQLException("La parte " + numeroParte
                    + " no tiene una nota cargada: creá la nota antes de dividir. No se dividió el ítem.");
        }
        NotaRecepcion nota = notaRecepcionService.findById(notaRecepcionId)
                .orElseThrow(() -> new GraphQLException("La nota de la parte " + numeroParte
                        + " ya no existe: no se dividió el ítem. Recargá las notas."));
        Long pedidoDeLaNota = nota.getPedido() != null ? nota.getPedido().getId() : null;
        if (pedido != null && !Objects.equals(pedidoDeLaNota, pedido.getId())) {
            throw new GraphQLException("La nota de la parte " + numeroParte
                    + " es de otro pedido: no se dividió el ítem. Recargá las notas.");
        }
        return nota;
    }

    // ---------------------------------------------------------------- Comunes

    /** La cantidad llega en presentaciones; sin presentación (o con cantidad 0) ya es la cantidad base. */
    private double aUnidadesBase(Double cantidad, Presentacion presentacion) {
        Double porUnidad = presentacion != null ? presentacion.getCantidad() : null;
        return porUnidad != null && porUnidad > 0 ? cantidad * porUnidad : cantidad;
    }

    private Presentacion presentacion(Long id) {
        return id != null ? presentacionService.findById(id).orElse(null) : null;
    }

    private PedidoItem pedidoItem(Long id) {
        if (id == null) {
            throw new GraphQLException("Falta el ítem del pedido.");
        }
        return pedidoItemService.findById(id)
                .orElseThrow(() -> new GraphQLException("El ítem del pedido ya no existe. Recargá el pedido."));
    }

    private Pedido pedidoDe(NotaRecepcionItem item) {
        if (item.getPedidoItem() != null && item.getPedidoItem().getPedido() != null) {
            return item.getPedidoItem().getPedido();
        }
        return item.getNotaRecepcion() != null ? item.getNotaRecepcion().getPedido() : null;
    }
}
