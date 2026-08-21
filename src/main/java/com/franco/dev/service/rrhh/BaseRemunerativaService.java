package com.franco.dev.service.rrhh;

import com.franco.dev.repository.rrhh.LiquidacionItemRepository;
import com.franco.dev.service.rrhh.builder.BaseRemunerativa;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Responde "cuanto percibio este funcionario en este anio", una sola vez y para todos.
 *
 * <p>Lo consumen el aguinaldo anual, el aguinaldo proporcional del finiquito, la base del
 * IPS del finiquito y la base de la indemnizacion. Antes cada uno resolvia lo mismo por su
 * cuenta y con formulas distintas.</p>
 */
@Service
public class BaseRemunerativaService {

    @Autowired
    private LiquidacionItemRepository liquidacionItemRepository;

    /**
     * Percibido del anio, mes por mes. Cuentan las liquidaciones APROBADA y PAGADA; un
     * BORRADOR no, porque todavia puede cambiar.
     */
    public BaseRemunerativa.Resultado percibidoAnual(Long funcionarioId, int anio) {
        if (funcionarioId == null) return BaseRemunerativa.de(null);
        List<Object[]> filas = liquidacionItemRepository.percibidoPorPeriodo(funcionarioId, anio);
        List<BaseRemunerativa.Mes> meses = new ArrayList<>();
        if (filas != null) {
            for (Object[] f : filas) {
                if (f == null || f.length < 2) continue;
                int mes = BaseRemunerativa.mesDePeriodo(f[0] != null ? f[0].toString() : null);
                if (mes == 0) continue;
                BigDecimal monto = f[1] instanceof BigDecimal
                        ? (BigDecimal) f[1]
                        : new BigDecimal(String.valueOf(f[1] != null ? f[1] : "0"));
                meses.add(new BaseRemunerativa.Mes(mes, monto));
            }
        }
        return BaseRemunerativa.de(meses);
    }
}
