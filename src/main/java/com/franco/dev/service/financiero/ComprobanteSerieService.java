package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.ComprobanteSerie;
import com.franco.dev.repository.financiero.ComprobanteSerieRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Numeración correlativa de comprobantes (CN3). */
@Service
@AllArgsConstructor
public class ComprobanteSerieService {

    private final ComprobanteSerieRepository repository;

    public List<ComprobanteSerie> findAll() { return repository.findAll(); }

    public Optional<ComprobanteSerie> findById(Long id) { return repository.findById(id); }

    public ComprobanteSerie save(ComprobanteSerie s) {
        if (s.getSiguiente() == null) s.setSiguiente(1L);
        if (s.getActivo() == null) s.setActivo(true);
        if (s.getPrefijo() == null) s.setPrefijo("");
        if (s.getRellenoCeros() == null) s.setRellenoCeros(0);
        return repository.save(s);
    }

    /**
     * Devuelve el siguiente número formateado de la serie del tipo dado e incrementa
     * el correlativo, de forma atómica (lock pesimista). Si no hay serie para el tipo,
     * devuelve null (numeración opcional).
     */
    @Transactional
    public String siguienteNumero(String tipo) {
        Optional<ComprobanteSerie> opt = repository.lockByTipo(tipo);
        if (!opt.isPresent()) return null;
        ComprobanteSerie s = opt.get();
        if (Boolean.FALSE.equals(s.getActivo())) return null;
        long n = s.getSiguiente() != null ? s.getSiguiente() : 1L;
        s.setSiguiente(n + 1);
        repository.save(s);
        String num = String.valueOf(n);
        int relleno = s.getRellenoCeros() != null ? s.getRellenoCeros() : 0;
        if (relleno > 0) {
            StringBuilder sb = new StringBuilder(num);
            while (sb.length() < relleno) sb.insert(0, '0');
            num = sb.toString();
        }
        return (s.getPrefijo() != null ? s.getPrefijo() : "") + num;
    }
}
