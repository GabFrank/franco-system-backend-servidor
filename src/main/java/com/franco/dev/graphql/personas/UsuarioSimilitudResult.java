package com.franco.dev.graphql.personas;

import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A quien reconocio el 1:N, y con cuanta ventaja sobre el siguiente.
 *
 * El margen es el dato que decide si una identificacion es solida. Una similitud de 0,71
 * contra un segundo candidato de 0,45 identifica; la misma 0,71 contra un 0,69 es una
 * moneda al aire. Sin el margen las dos llegan al cliente iguales, y despues no hay forma
 * de distinguir un falso positivo de un acierto en los datos.
 *
 * `similitudSegundo` y `margen` son null cuando hay un solo enrolado: no hay contra quien
 * comparar, y decir "margen 1" afirmaria una certeza que nadie midio.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UsuarioSimilitudResult {
    private Usuario usuario;
    private Double similitud;
    private Double similitudSegundo;
    private Double margen;

    /** Compatibilidad con el uso anterior, que no conocia el segundo candidato. */
    public UsuarioSimilitudResult(Usuario usuario, Double similitud) {
        this(usuario, similitud, null, null);
    }
}
