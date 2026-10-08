package com.franco.dev.service.financiero;

import com.franco.dev.domain.personas.Usuario;
import graphql.GraphQLException;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.LongType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Idempotencia de las operaciones que mueven plata (issue #376): un pedido repetido con la misma
 * clave devuelve lo que creó el original en vez de registrarlo otra vez.
 *
 * <p>La clave se inserta en {@code financiero.operacion_idempotente} <b>dentro de la transacción de la
 * operación</b>: si la operación se rechaza, la clave se va con el rollback y el reintento corre como
 * un pedido nuevo. Dos pedidos simultáneos con la misma clave se serializan en la clave primaria —
 * el segundo {@code INSERT} espera al primero — y es lo primero que toman los dos, antes de cualquier
 * lock del negocio.</p>
 *
 * <p>Asume READ COMMITTED (el default de la aplicación). Desde una transacción SERIALIZABLE o
 * REPEATABLE READ el {@code ON CONFLICT DO NOTHING} contra una fila que el snapshot no ve falla con
 * un error de serialización en vez de insertar cero filas: no llamarlo desde ahí.</p>
 *
 * <p>Las sentencias son nativas por {@link EntityManager}, que vuelca la sesión antes de ejecutar y
 * <b>no la limpia</b>: las entidades que el llamador ya tiene lockeadas siguen gestionadas.</p>
 */
@Service
public class IdempotenciaService {

    public static final int LARGO_MAXIMO_CLAVE = 64;

    @PersistenceContext
    private EntityManager em;

    /**
     * Ejecuta {@code accion} una sola vez por clave.
     *
     * @param clave     la que manda el cliente; nula o vacía = sin idempotencia (cliente viejo)
     * @param operacion nombre fijo de la operación: una clave no vale para otra
     * @param huella    {@link HuellaPedido} del pedido: una clave no vale para otro contenido
     * @param accion    la operación; corre solo si la clave es nueva
     * @param idDe      id de lo que creó la acción, que es lo que se guarda
     * @param cargar    lee lo ya creado cuando el pedido es una repetición
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T ejecutar(String clave, String operacion, String huella, Usuario usuario,
                          Supplier<T> accion, Function<T, Long> idDe, Function<Long, T> cargar) {
        if (clave == null || clave.trim().isEmpty()) return accion.get();
        if (clave.length() > LARGO_MAXIMO_CLAVE) {
            throw new GraphQLException("Clave de idempotencia inválida");
        }
        Long usuarioId = usuario != null ? usuario.getId() : null;

        int insertadas = em.createNativeQuery(
                        "INSERT INTO financiero.operacion_idempotente (clave, operacion, usuario_id, huella) " +
                        "VALUES (:clave, :operacion, :usuarioId, :huella) ON CONFLICT (clave) DO NOTHING")
                .unwrap(NativeQuery.class)
                .setParameter("clave", clave)
                .setParameter("operacion", operacion)
                .setParameter("usuarioId", usuarioId, LongType.INSTANCE)
                .setParameter("huella", huella)
                .executeUpdate();

        if (insertadas == 0) return yaRegistrado(clave, operacion, huella, usuarioId, cargar);

        T resultado = accion.get();
        Long resultadoId = resultado != null ? idDe.apply(resultado) : null;
        if (resultadoId == null) {
            throw new IllegalStateException("La operación " + operacion + " no devolvió un resultado con id");
        }
        int actualizadas = em.createNativeQuery(
                        "UPDATE financiero.operacion_idempotente SET resultado_id = :resultadoId WHERE clave = :clave")
                .setParameter("resultadoId", resultadoId)
                .setParameter("clave", clave)
                .executeUpdate();
        if (actualizadas != 1) {
            throw new IllegalStateException("No se pudo registrar el resultado de la clave de idempotencia");
        }
        return resultado;
    }

    /** La clave ya está commiteada: es una repetición, o un mal uso de la clave. */
    private <T> T yaRegistrado(String clave, String operacion, String huella, Long usuarioId,
                               Function<Long, T> cargar) {
        @SuppressWarnings("unchecked")
        List<Object[]> filas = em.createNativeQuery(
                        "SELECT operacion, usuario_id, huella, resultado_id " +
                        "FROM financiero.operacion_idempotente WHERE clave = :clave")
                .setParameter("clave", clave)
                .getResultList();
        if (filas.isEmpty()) {
            throw new GraphQLException("No se pudo verificar la clave de idempotencia. Vuelva a intentar.");
        }
        Object[] f = filas.get(0);
        Long usuarioRegistrado = f[1] != null ? ((Number) f[1]).longValue() : null;
        if (!operacion.equals(f[0]) || !huella.equals(f[2]) || !Objects.equals(usuarioId, usuarioRegistrado)) {
            throw new GraphQLException("La clave de idempotencia ya se usó para otro pedido");
        }
        // Nunca se devuelve vacío: el cliente lo leería como otro «sin respuesta» y volvería a reintentar.
        if (f[3] == null) {
            throw new GraphQLException("El pedido original quedó registrado sin resultado. Avise a soporte.");
        }
        T existente = cargar.apply(((Number) f[3]).longValue());
        if (existente == null) {
            throw new GraphQLException("El pedido original quedó registrado sin resultado. Avise a soporte.");
        }
        return existente;
    }
}
