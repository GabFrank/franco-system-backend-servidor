package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Devolucion;
import com.franco.dev.domain.operaciones.DevolucionItem;
import com.franco.dev.domain.operaciones.NotaCreditoDevolucion;
import com.franco.dev.domain.operaciones.NotaCreditoDevolucionItem;
import com.franco.dev.domain.operaciones.RetiroDevolucion;
import com.franco.dev.domain.operaciones.dto.AcreditacionPreviewDto;
import com.franco.dev.domain.operaciones.dto.AcreditacionPreviewLineaDto;
import com.franco.dev.domain.operaciones.enums.DevolucionEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.operaciones.input.NotaCreditoDevolucionItemInput;
import com.franco.dev.repository.operaciones.NotaCreditoDevolucionItemRepository;
import com.franco.dev.repository.operaciones.NotaCreditoDevolucionRepository;
import com.franco.dev.service.productos.CostosPorProductoService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.service.productos.ProductoService;
import graphql.GraphQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Nota de credito consolidada por retiro a proveedor. Reemplaza la acreditacion
 * per-devolucion: consolida los items de todas las devoluciones de un retiro por
 * producto y genera una unica nota de credito.
 */
@Service
public class NotaCreditoDevolucionService {

    @Autowired
    private NotaCreditoDevolucionRepository repository;
    @Autowired
    private NotaCreditoDevolucionItemRepository itemRepository;
    @Autowired
    private RetiroDevolucionService retiroDevolucionService;
    @Autowired
    private DevolucionService devolucionService;
    @Autowired
    private DevolucionItemService devolucionItemService;
    @Autowired
    private CostosPorProductoService costosPorProductoService;
    @Autowired
    private PresentacionService presentacionService;
    @Autowired
    private ProductoService productoService;

    public Optional<NotaCreditoDevolucion> findById(Long id) {
        return repository.findById(id);
    }

    /** Nota vigente del retiro (las ANULADAS no cuentan). */
    public Optional<NotaCreditoDevolucion> findByRetiroId(Long retiroId) {
        return repository.findFirstByRetiroIdAndEstadoNotOrderByIdDesc(retiroId,
                NotaCreditoDevolucion.ESTADO_ANULADA);
    }

    /** Notas revertidas del retiro, la mas reciente primero. */
    public List<NotaCreditoDevolucion> findAnuladasByRetiroId(Long retiroId) {
        return repository.findByRetiroIdAndEstadoOrderByIdDesc(retiroId,
                NotaCreditoDevolucion.ESTADO_ANULADA);
    }

    public List<NotaCreditoDevolucionItem> itemsByNotaCredito(Long notaCreditoId) {
        return itemRepository.findByNotaCreditoId(notaCreditoId);
    }

    /** Costo medio actual del producto por unidad base (0.0 si no hay). */
    private double costoMedioBase(Long productoId) {
        if (productoId == null) return 0.0;
        try {
            CostoPorProducto c = costosPorProductoService.findLastByProductoId(productoId);
            if (c != null && c.getCostoMedio() != null && c.getCostoMedio() > 0.0) {
                return c.getCostoMedio();
            }
        } catch (Exception ignored) {
        }
        return 0.0;
    }

    /**
     * Costo por unidad base a partir del valor declarado en los items de la
     * devolucion. Fallback para productos sin CostoPorProducto.
     */
    private double costoDeclaradoBase(Double valorDeclarado, double cantidadBase) {
        if (valorDeclarado == null || valorDeclarado <= 0.0 || cantidadBase <= 0.0) return 0.0;
        return valorDeclarado / cantidadBase;
    }

    /**
     * Reparte el monto de la nota entre las devoluciones del retiro. Cada una pesa
     * lo que valen sus items al costo por unidad base que la nota le dio a cada
     * producto; la ultima se lleva el resto, asi la suma da exacto el total de la
     * nota. Si ningun producto de las devoluciones esta en la nota, se reparte por
     * cantidad base, y si tampoco hay cantidades, en partes iguales.
     */
    static Map<Long, Double> repartirMonto(Map<Long, List<DevolucionItem>> itemsPorDevolucion,
                                           List<NotaCreditoDevolucionItemInput> lineasNota,
                                           double montoTotal) {
        Map<Long, Double> totalPorProducto = new LinkedHashMap<>();
        Map<Long, Double> cantidadPorProducto = new LinkedHashMap<>();
        for (NotaCreditoDevolucionItemInput l : lineasNota) {
            if (l.getProductoId() == null) continue;
            totalPorProducto.merge(l.getProductoId(), l.getTotal() != null ? l.getTotal() : 0.0, Double::sum);
            cantidadPorProducto.merge(l.getProductoId(), l.getCantidadBase() != null ? l.getCantidadBase() : 0.0, Double::sum);
        }

        Map<Long, Double> pesoValor = new LinkedHashMap<>();
        Map<Long, Double> pesoCantidad = new LinkedHashMap<>();
        for (Map.Entry<Long, List<DevolucionItem>> e : itemsPorDevolucion.entrySet()) {
            double valor = 0.0;
            double cantidad = 0.0;
            for (DevolucionItem item : e.getValue()) {
                double cantidadBase = (item.getCantidad() != null ? item.getCantidad() : 0.0) * factor(item);
                cantidad += cantidadBase;
                Long prodId = item.getProducto() != null ? item.getProducto().getId() : null;
                Double cantNota = prodId != null ? cantidadPorProducto.get(prodId) : null;
                if (cantNota != null && cantNota > 0.0) {
                    valor += cantidadBase * (totalPorProducto.get(prodId) / cantNota);
                }
            }
            pesoValor.put(e.getKey(), valor);
            pesoCantidad.put(e.getKey(), cantidad);
        }

        Map<Long, Double> pesos = pesoValor;
        if (suma(pesos) <= 0.0) pesos = pesoCantidad;
        double total = suma(pesos);

        Map<Long, Double> montos = new LinkedHashMap<>();
        int n = pesos.size();
        int i = 0;
        double asignado = 0.0;
        for (Map.Entry<Long, Double> e : pesos.entrySet()) {
            i++;
            double monto;
            if (i == n) {
                monto = montoTotal - asignado;
            } else {
                monto = total > 0.0 ? montoTotal * e.getValue() / total : montoTotal / n;
            }
            asignado += monto;
            montos.put(e.getKey(), monto);
        }
        return montos;
    }

    private static double suma(Map<Long, Double> valores) {
        return valores.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    private static double factor(DevolucionItem item) {
        if (item.getPresentacion() != null && item.getPresentacion().getCantidad() != null) {
            return item.getPresentacion().getCantidad();
        }
        return 1.0;
    }

    /**
     * Preview de consolidacion para acreditar un retiro. Agrupa por producto en
     * unidad base y valoriza a costo medio. No persiste nada.
     */
    @Transactional(readOnly = true)
    public AcreditacionPreviewDto previewAcreditacion(Long retiroId) {
        RetiroDevolucion retiro = retiroDevolucionService.findById(retiroId)
                .orElseThrow(() -> new GraphQLException("Retiro no encontrado: " + retiroId));
        if (!RetiroDevolucion.ESTADO_CONFIRMADO.equals(retiro.getEstado())) {
            throw new GraphQLException("Solo se puede acreditar un retiro CONFIRMADO");
        }

        List<Devolucion> devoluciones = devolucionService.findByRetiroId(retiroId);
        // productoId -> cantidad base acumulada
        Map<Long, Double> cantidadPorProducto = new LinkedHashMap<>();
        // productoId -> valor declarado acumulado (cantidad * costo cargado en el item)
        Map<Long, Double> valorDeclaradoPorProducto = new LinkedHashMap<>();
        for (Devolucion d : devoluciones) {
            for (DevolucionItem item : devolucionItemService.findByDevolucionId(d.getId())) {
                Long prodId = item.getProducto() != null ? item.getProducto().getId() : null;
                if (prodId == null) continue;
                double cantidad = item.getCantidad() != null ? item.getCantidad() : 0.0;
                double cantidadBase = cantidad * factor(item);
                cantidadPorProducto.merge(prodId, cantidadBase, Double::sum);
                if (item.getCostoUnitario() != null && item.getCostoUnitario() > 0.0) {
                    valorDeclaradoPorProducto.merge(prodId, cantidad * item.getCostoUnitario(), Double::sum);
                }
            }
        }

        List<AcreditacionPreviewLineaDto> lineas = new ArrayList<>();
        double montoTotal = 0.0;
        for (Map.Entry<Long, Double> e : cantidadPorProducto.entrySet()) {
            Long prodId = e.getKey();
            double cantidadBase = e.getValue();
            // Sin CostoPorProducto la linea salia en 0 y el operador tenia que
            // retipear el costo: se usa entonces el declarado en los items.
            double costoMedio = costoMedioBase(prodId);
            if (costoMedio <= 0.0) {
                costoMedio = costoDeclaradoBase(valorDeclaradoPorProducto.get(prodId), cantidadBase);
            }
            double total = cantidadBase * costoMedio;
            montoTotal += total;

            Producto producto = productoService.findById(prodId).orElse(null);
            String descripcion = producto != null ? producto.getDescripcion() : null;
            List<Presentacion> presentaciones = presentacionService.findByProductoId(prodId).stream()
                    .filter(p -> !Boolean.FALSE.equals(p.getActivo()))
                    .collect(Collectors.toList());

            lineas.add(new AcreditacionPreviewLineaDto(prodId, descripcion, cantidadBase,
                    costoMedio, total, presentaciones));
        }

        return new AcreditacionPreviewDto(retiroId, retiro.getProveedor(), lineas, montoTotal);
    }

    /**
     * Crea la nota de credito consolidada del retiro y pasa todas sus
     * devoluciones a ACREDITADO. El retiro debe estar CONFIRMADO y no acreditado.
     */
    @Transactional
    public NotaCreditoDevolucion acreditarRetiro(Long retiroId, String nroNotaCredito, LocalDateTime fecha,
                                                 List<NotaCreditoDevolucionItemInput> items, Usuario usuario) {
        RetiroDevolucion retiro = retiroDevolucionService.findById(retiroId)
                .orElseThrow(() -> new GraphQLException("Retiro no encontrado: " + retiroId));
        if (!RetiroDevolucion.ESTADO_CONFIRMADO.equals(retiro.getEstado())) {
            throw new GraphQLException("Solo se puede acreditar un retiro CONFIRMADO");
        }
        if (findByRetiroId(retiroId).isPresent()) {
            throw new GraphQLException("El retiro ya tiene una nota de credito registrada");
        }
        if (items == null || items.isEmpty()) {
            throw new GraphQLException("La nota de credito no tiene items");
        }

        List<Devolucion> devoluciones = devolucionService.findByRetiroId(retiroId);
        if (devoluciones.isEmpty()) {
            throw new GraphQLException("El retiro no tiene devoluciones");
        }
        for (Devolucion d : devoluciones) {
            if (d.getEstado() != DevolucionEstado.RETIRADO) {
                throw new GraphQLException("Todas las devoluciones del retiro deben estar RETIRADO para acreditar");
            }
        }

        double montoTotal = items.stream()
                .mapToDouble(i -> i.getTotal() != null ? i.getTotal() : 0.0).sum();

        NotaCreditoDevolucion nota = new NotaCreditoDevolucion();
        nota.setRetiro(retiro);
        nota.setProveedor(retiro.getProveedor());
        nota.setNroNotaCredito(nroNotaCredito);
        nota.setFecha(fecha != null ? fecha : LocalDateTime.now());
        nota.setMontoTotal(montoTotal);
        nota.setSaldo(montoTotal);
        nota.setEstado(NotaCreditoDevolucion.ESTADO_DISPONIBLE);
        nota.setUsuario(usuario);
        nota.setCreadoEn(LocalDateTime.now());
        NotaCreditoDevolucion guardada = repository.save(nota);

        for (NotaCreditoDevolucionItemInput in : items) {
            NotaCreditoDevolucionItem item = new NotaCreditoDevolucionItem();
            item.setNotaCredito(guardada);
            if (in.getProductoId() != null) {
                item.setProducto(productoService.findById(in.getProductoId()).orElse(null));
            }
            if (in.getPresentacionId() != null) {
                item.setPresentacion(presentacionService.findById(in.getPresentacionId()).orElse(null));
            }
            item.setCantidad(in.getCantidad());
            item.setCostoUnitario(in.getCostoUnitario());
            item.setCantidadBase(in.getCantidadBase());
            item.setTotal(in.getTotal());
            itemRepository.save(item);
        }

        // Cada devolucion del retiro pasa a ACREDITADO (reusa la maquina de estados
        // y copia el nro de nota para compatibilidad de lectura del cliente), con su
        // parte del monto de la nota.
        Map<Long, List<DevolucionItem>> itemsPorDevolucion = new LinkedHashMap<>();
        for (Devolucion d : devoluciones) {
            itemsPorDevolucion.put(d.getId(), devolucionItemService.findByDevolucionId(d.getId()));
        }
        Map<Long, Double> montos = repartirMonto(itemsPorDevolucion, items, montoTotal);
        for (Devolucion d : devoluciones) {
            devolucionService.acreditar(d.getId(), nroNotaCredito, montos.get(d.getId()), usuario);
        }

        return guardada;
    }

    /**
     * Revierte la acreditacion: solo si la nota no fue usada (saldo == montoTotal).
     * Devuelve las devoluciones a RETIRADO y deja la nota ANULADA (sin saldo, con
     * quien y cuando), sin borrarla: el numero lo emite el proveedor y tiene que
     * quedar para auditar (issue #207). El retiro se puede volver a acreditar.
     */
    @Transactional
    public Boolean revertir(Long notaCreditoId, Usuario usuario) {
        NotaCreditoDevolucion nota = repository.findById(notaCreditoId)
                .orElseThrow(() -> new GraphQLException("Nota de credito no encontrada: " + notaCreditoId));
        if (NotaCreditoDevolucion.ESTADO_ANULADA.equals(nota.getEstado())) {
            throw new GraphQLException("La nota de credito ya fue revertida");
        }
        double montoTotal = nota.getMontoTotal() != null ? nota.getMontoTotal() : 0.0;
        double saldo = nota.getSaldo() != null ? nota.getSaldo() : 0.0;
        if (Math.abs(saldo - montoTotal) > 0.001) {
            throw new GraphQLException("No se puede revertir: la nota de credito ya fue usada parcialmente");
        }

        List<Devolucion> devoluciones = devolucionService.findByRetiroId(nota.getRetiro().getId());
        for (Devolucion d : devoluciones) {
            if (d.getEstado() == DevolucionEstado.ACREDITADO) {
                d.setEstado(DevolucionEstado.RETIRADO);
                d.setResolucion(null);
                d.setFinalizado(false);
                d.setNroNotaCredito(null);
                d.setMontoAcreditado(null);
                devolucionService.save(d);
            }
        }

        nota.setEstado(NotaCreditoDevolucion.ESTADO_ANULADA);
        nota.setSaldo(0.0);
        nota.setAnuladoPor(usuario);
        nota.setAnuladoEn(LocalDateTime.now());
        repository.save(nota);
        return true;
    }
}
