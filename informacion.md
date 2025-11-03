-- comando para exportar archivo jar a produccion
./mvnw clean package -Dspring.profiles.active=prod -Dflyway.skip=true


-- iniciar base de datos
pg_ctl -D /usr/local/var/postgres -l /usr/local/var/postgres/logfile restart
pg_ctl -D /usr/local/var/postgres2 -l /usr/local/var/postgres2/logfile restart

-- logs
tail -f /usr/local/var/postgres/log/
tail -f /usr/local/var/postgres2/log/

## Sucursales con facturacion electronica funcionando
1 - Suc Centro 
3 - Suc Rotonda
4 - Suc Industrial 
5 - Suc Km5
6 - Suc Calle 10
7 - Suc Katuete 1 
8 - Suc Paloma 1 
9 - Suc Troncal San Antonio
10 - Suc Katuete 2
11 - Suc Puente 
12 - Suc Troncal Plaza
14 - Suc Canindeyu
18 - Suc Curuguaty
20 - Suc Paloma 2
21 - Suc Renacer
22 - Suc Canindeyu 2 
23 - Suc Ruta 7
24 - Suc Km2 ✅