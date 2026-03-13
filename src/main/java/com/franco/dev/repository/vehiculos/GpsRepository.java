package com.franco.dev.repository.vehiculos;

import com.franco.dev.domain.vehiculos.Gps;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface GpsRepository extends HelperRepository<Gps, Long> {

        default Class<Gps> getEntityClass() {
                return Gps.class;
        }

        Optional<Gps> findByImei(String imei);

        @Query("SELECT g FROM Gps g " +
                        "LEFT JOIN FETCH g.vehiculo v " +
                        "LEFT JOIN FETCH v.modelo m " +
                        "LEFT JOIN FETCH m.marca " +
                        "WHERE g.imei = :imei")
        Optional<Gps> findByImeiWithVehiculo(@Param("imei") String imei);

        List<Gps> findByVehiculoId(Long vehiculoId);

        List<Gps> findByActivoTrue();

        @Query("SELECT DISTINCT g FROM Gps g " +
                        "LEFT JOIN FETCH g.vehiculo v " +
                        "LEFT JOIN FETCH v.modelo m " +
                        "LEFT JOIN FETCH m.marca")
        List<Gps> findAllWithVehiculoFetched();

        @Query("SELECT DISTINCT g FROM Gps g " +
                        "LEFT JOIN FETCH g.vehiculo v " +
                        "LEFT JOIN FETCH v.modelo m " +
                        "LEFT JOIN FETCH m.marca " +
                        "WHERE LOWER(g.imei) LIKE %:texto% " +
                        "OR LOWER(g.modeloTracker) LIKE %:texto% " +
                        "OR LOWER(v.chapa) LIKE %:texto%")
        List<Gps> searchByTexto(@Param("texto") String texto);

        @Modifying
        @Query("UPDATE Gps g SET g.ultimaLatitud = :latitud, g.ultimaLongitud = :longitud, " +
                        "g.ultimaFechaReporte = :fechaGps, g.ultimaIgnicion = :ignicion " +
                        "WHERE g.id = :gpsId")
        void actualizarUltimaPosicion(@Param("gpsId") Long gpsId,
                        @Param("latitud") Double latitud,
                        @Param("longitud") Double longitud,
                        @Param("fechaGps") LocalDateTime fechaGps,
                        @Param("ignicion") Boolean ignicion);

        @Query("SELECT DISTINCT g FROM Gps g " +
                        "LEFT JOIN FETCH g.vehiculo v " +
                        "LEFT JOIN FETCH v.modelo m " +
                        "LEFT JOIN FETCH m.marca " +
                        "WHERE g.activo = true AND g.ultimaLatitud IS NOT NULL")
        List<Gps> findAllWithUltimaPosicion();

        @Modifying
        @Query("UPDATE Gps g SET g.modoSueno = :modoSueno WHERE g.id = :gpsId")
        void updateModoSueno(@Param("gpsId") Long gpsId, @Param("modoSueno") Boolean modoSueno);

        @Modifying
        @Query("UPDATE Gps g SET g.intervaloReporte = :intervalo WHERE g.id = :gpsId")
        void updateIntervaloReporte(@Param("gpsId") Long gpsId, @Param("intervalo") Integer intervalo);

        @Modifying
        @Query("UPDATE Gps g SET g.motorBloqueado = :bloqueado WHERE g.id = :gpsId")
        void updateMotorBloqueado(@Param("gpsId") Long gpsId, @Param("bloqueado") Boolean bloqueado);

        @Modifying
        @Query("UPDATE Gps g SET g.alertaVelocidad = :alertaVelocidad, g.velocidadLimite = :velocidadLimite, " +
                        "g.alertaVibracion = :alertaVibracion, g.alertaBateriaBaja = :alertaBateriaBaja, " +
                        "g.alertaAcc = :alertaAcc WHERE g.id = :gpsId")
        void updateConfigAlertas(@Param("gpsId") Long gpsId,
                        @Param("alertaVelocidad") Boolean alertaVelocidad,
                        @Param("velocidadLimite") Integer velocidadLimite,
                        @Param("alertaVibracion") Boolean alertaVibracion,
                        @Param("alertaBateriaBaja") Boolean alertaBateriaBaja,
                        @Param("alertaAcc") Boolean alertaAcc);
}
