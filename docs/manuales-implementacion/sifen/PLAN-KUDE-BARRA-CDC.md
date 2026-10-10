# Plan: barra del CDC despareja en el KuDE de factura electronica

Rama `fix/sifen-kude-barra-cdc` (desde `develop`). Pieza: central. Sin cambios de DB, GraphQL ni clientes.

## Sintoma

En el KuDE (`reports/factura-electronica-kude.jrxml`, banda `summary`) la barra azul del CDC sale
en forma de escalon: el rotulo `CDC:` queda de una altura y el numero de otra, y la parte que
crece tapa la leyenda «ESTE DOCUMENTO ES UNA REPRESENTACION GRAFICA...».

## Causa (reproducida)

La barra son **dos elementos opacos sueltos**, cada uno con su propio fondo:

- `staticText` `CDC:` en `x=125 w=25 h=14`, alto fijo;
- `textField` del numero en `x=150 w=263 h=14` con `textAdjust="StretchHeight"`.

Los dos van en `SansSerif` negrita 10. Jasper mide el texto con la fuente fisica a la que la JVM
mapea `SansSerif`, que cambia por maquina. Con una fuente angosta (Noto, esta maquina) los 44
digitos entran en 263 pt y la barra sale de una linea. Con una ancha (DejaVu Sans, lo habitual en
un servidor Linux sin escritorio) miden ~306 pt: el numero parte en dos lineas, el campo se estira
a 23 pt y el rotulo se queda en 14. Ademas `CDC:` no entra en 25 pt y pierde los dos puntos.

Medido llenando la plantilla con un CDC de 44 digitos:

| Fuente | rotulo | numero |
|---|---|---|
| SansSerif -> Noto Sans | `h=14` | `h=14` |
| DejaVu Sans | `h=14`, texto `CDC` | `h=23`, pisa la leyenda de `y=253` |

Aun en el caso bueno la barra termina en `x=413`, mientras el bloque de texto llega a `x=548`.

## Cambio

Fase unica.

1. Reemplazar los dos elementos por **un `frame` opaco** `x=125 y=236 w=423 h=16`
   (`backcolor="#B7CCE6"`, el mismo ancho que los textos del bloque) con adentro:
   - `staticText` `CDC:` `x=0 w=38 h=16`, izquierda, sangria 4;
   - `textField` del numero `x=38 w=385 h=16`, izquierda, `textAdjust="ScaleFont"`: si alguna
     fuente no entrara, achica la letra en vez de crecer en alto. 385 pt cubre los ~306 de DejaVu.
   Las dos fuentes con `size="10"` explicito.
   El fondo lo pone solo el frame, asi el alto de la barra no depende del texto.
2. Test nuevo `FacturaElectronicaKudeJrxmlTest` (el `.jrxml` se compila en runtime, el build no lo
   valida): compila y llena la plantilla y verifica que rotulo y numero viven en un mismo frame
   opaco, con el mismo alto que el frame, y que el CDC se imprime entero. Se comprueba que falla
   con la plantilla vieja.

No se cambia el contenido: el CDC sigue saliendo como 44 digitos corridos. (El KuDE de ejemplo de
la SET lo agrupa de a 4; queda como decision aparte.)

## Verificacion

- Render local de antes/despues con `SansSerif` y forzando `DejaVu Sans`.
- `./mvnw clean verify -B -DskipFlyway=true`.

## Auditoria del plan (paso 5)

Dos ejes, sin bloqueantes ni contradicciones entre si.

| Hallazgo | Que se hizo |
|---|---|
| A: las fuentes no declaraban `size`; `ScaleFont` parte del default | `size="10"` explicito en las dos |
| A: la URL (`y=224 h=10`, `StretchHeight`) pisaria la barra si partiera en dos lineas | No aplica: es el literal `https://ekuatia.set.gov.py/consultas/` (`FacturaLegalGraphQL:1889`), una linea. Riesgo previo, sin cambio |
| A: el mismo PDF sale por correo (`FacturaCorreoService`) y por la impresion | Deseado; va en la descripcion del PR |
| B: el test podia dar verde con la plantilla vieja segun la fuente del runner | El nucleo del test es estructural (un frame opaco con los dos textos, mismo alto): falla con la vieja en cualquier fuente. Comprobado revirtiendo |
| B: los parametros viven inline en el resolver, no hay service que llamar | El test arma el mapa a mano; solo cubre la plantilla |
| B: una plantilla rota no hace fallar el health check ni dispara rollback | Tras desplegar a alpha, generar un KuDE real antes de promover |

## Sin verificar

- Que fuente usa realmente cada servidor (bodega, farmacia, alpha). El arreglo no depende de eso.
- La impresion desde el desktop de un KuDE real en un servidor: se ve recien tras desplegar.
