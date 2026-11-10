package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.repository.financiero.MonedaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@AllArgsConstructor
public class MonedaService extends CrudService<Moneda, MonedaRepository, Long> {

    private final MonedaRepository repository;

    @Override
    public MonedaRepository getRepository() {
        return repository;
    }

//    public List<Moneda> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public List<Moneda> findByAll(String texto) {
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto);
    }

    public Moneda findByDescripcion(String descripcion) {
        return repository.findByDenominacion(descripcion);
    }

    @Override
    public Moneda save(Moneda entity) {
        Moneda e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    @Override
    public List<Moneda> findAll2() {
        return repository.findAllByOrderByIdAsc();
    }

    /**
     * La moneda de la empresa: la marcada como principal y, si no hay ninguna, el guarani. Es la
     * moneda de consolidacion y el respaldo de toda la configuracion por modulo.
     */
    public Moneda empresa() {
        Moneda principal = repository.findFirstByPrincipalTrueOrderByIdAsc();
        return principal != null ? principal : guarani();
    }

    /**
     * El guarani, la moneda en la que esta expresada la cotizacion ({@code cambio.valor_en_gs}).
     * Por codigo ISO; mientras una base no lo tenga cargado, por el nombre y despues por el id 1,
     * que es lo que asumia el codigo hasta ahora. Este es el unico lugar que mira el nombre.
     */
    public Moneda guarani() {
        Moneda m = repository.findFirstByCodigoIsoIgnoreCase(ISO_GUARANI);
        if (m == null) m = repository.findFirstByDenominacionContainingIgnoreCaseOrderByIdAsc("GUARAN");
        if (m == null) m = repository.findById(1L).orElse(null);
        return m;
    }

    public Moneda porCodigoIso(String codigoIso) {
        if (codigoIso == null || codigoIso.trim().isEmpty()) return null;
        return repository.findFirstByCodigoIsoIgnoreCase(codigoIso.trim());
    }

    /** Mismo criterio que {@code guarani()}: ISO, y sin ISO cargado, el nombre. */
    public static boolean esGuarani(Moneda m) {
        if (m == null) return false;
        if (m.getCodigoIso() != null && !m.getCodigoIso().trim().isEmpty()) {
            return ISO_GUARANI.equalsIgnoreCase(m.getCodigoIso().trim());
        }
        return m.getDenominacion() != null && m.getDenominacion().toUpperCase(Locale.ROOT).contains("GUARAN");
    }

    /**
     * Decimales de la moneda. Mismo respaldo que SolicitudPagoNotaRecepcionService.redondearAMoneda:
     * sin dato, 0 para el guarani y 2 para el resto.
     */
    public static int decimales(Moneda m) {
        if (m != null && m.getDecimales() != null && m.getDecimales() >= 0) return m.getDecimales();
        return esGuarani(m) ? 0 : 2;
    }

    public static boolean misma(Moneda a, Moneda b) {
        return a != null && b != null && Objects.equals(a.getId(), b.getId());
    }

    public static final String ISO_GUARANI = "PYG";
}