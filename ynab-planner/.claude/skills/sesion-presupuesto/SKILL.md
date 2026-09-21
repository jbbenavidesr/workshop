---
name: sesion-presupuesto
description: >-
  Conduce una sesión de planeación del presupuesto familiar sobre los datos de
  YNAB de este repo (cache.edn + plan.edn): diagnostica el gasto real amortizado,
  encuentra gastos sin meta, holgura y sobregiros silenciosos, revisa los pilares,
  modela la renta y el FPV, y entrega un checklist de cambios para aplicar en YNAB.
  Úsala SIEMPRE que la conversación toque el presupuesto, los targets, los pilares,
  las metas de ahorro o el flujo de caja de la casa — incluso si no dicen
  "presupuesto": preguntas como "¿de dónde saco 500k?", "me subieron el arriendo",
  "¿alcanza para el viaje?", "¿cuánto guardo para la declaración?", "¿me conviene
  aportar más al FPV?", "cuadremos el mes", "¿este gasto nuevo dónde entra?",
  "revisemos en qué se nos está yendo la plata" o "¿podemos pagar X?" son todas
  esta sesión. También al volver de un sync para revisar cómo va el mes.
---

# Sesión de presupuesto

Planear los targets de un presupuesto no es aritmética, es una conversación con
plata de por medio. Tu trabajo es traer los números tan honestos que la decisión
se vuelva obvia — y después dejar que la decisión la tomen ellos.

Esto es de una pareja. Varias categorías no son gasto, son valores: el diezmo,
la salud, la plata de salir juntos, la formación. Cortar ahí sin preguntar es
cortar donde no te corresponde.

## Antes de abrir la boca

1. **Lee `NOTAS.md`** en la raíz del repo. Ahí están las convenciones del modelo,
   las metas vigentes y la bitácora. Te ahorra re-preguntar lo ya decidido — y
   re-proponer lo ya descartado, que es peor.
2. **Corre el diagnóstico:**
   ```bash
   python3 .claude/skills/sesion-presupuesto/scripts/diagnostico.py --mes YYYY-MM
   ```
   Seis bloques: pilares, costo real amortizado, gasto sin meta, subfinanciadas,
   holgura y banderas. Léelo entero antes de opinar.
3. **Mira la antigüedad del sync.** Si `cache.edn` tiene semanas, dilo y ofrece
   `clojure -M:run` → Sync. No sincronices por tu cuenta sin avisar.

## Los cinco tiempos de la sesión

### 1. Fijar cuál es el ingreso de verdad

Separa **estable** de **variable** y planea la base solo contra el estable. Un
ingreso que puede desaparecer no sostiene un gasto recurrente; sirve para
acelerar metas, que se pueden pausar sin que se caiga nada.

Del bruto, lo único que se decide es lo voluntario. Las deducciones obligatorias
van `:skip` en `plan.edn`: siguen en YNAB para tener la foto completa, pero
meterlas al plan solo distorsiona el 10/20/30/40 con plata que nadie puede mover.

Para el ingreso real por mes, `/months` de la API (`income`) es mejor fuente que
el cache. Para separar fuentes, agrupa transacciones por `payee_name`.

### 2. Medir lo que de verdad cuesta vivir

**El desembolso de un mes no es el costo de ese mes.** Un seguro que se pagó en
septiembre se venía ahorrando desde enero: su costo mensual es la cuota, no el
golpe. Sin amortizar, septiembre parece una catástrofe y octubre un milagro, y
las dos lecturas son falsas.

El bloque ② del diagnóstico ya lo hace. Compara el **amortizado** contra el
ingreso estable — nunca el crudo.

Si alguien objeta que un pico "sí fue plata que salió", tiene razón y no cambia
nada: la pregunta de planeación es cuánto hay que apartar cada mes.

### 3. Encontrar los tres desajustes

Siempre en este orden, porque así es como rinden:

**Gasto sin meta** (bloque ③). Casi siempre el hallazgo más grande y el más
invisible: sale todos los años, es grande, nadie lo presupuestó. Impuestos
anuales, tratamientos periódicos, honorarios. Ojo con la diferencia entre *nunca
tuvo meta* y *se congeló a propósito* — el script las separa.

**Sobregiro silencioso** (bloque ④): la meta es menor que el gasto real, mes tras
mes. Mira la **serie**, no el promedio. Una categoría que va 1,0 → 1,5 → 2,0 →
2,4 no tiene un promedio de 1,7: tiene una tendencia, y el promedio la esconde.

**Holgura** (bloque ⑤): de acá sale la plata. Pero antes de cantarla como
recorte, mira el **saldo**: una meta baja con saldo gordo no está subfinanciada,
está resuelta.

### 4. Cerrar la brecha — con ellos, no por ellos

Presenta la aritmética completa y deja que escojan. El formato que funciona:

```
disponible    X
lo que piden  Y
────────────────
faltan        Y − X
```

Después las opciones **con su costo**, no como recomendaciones sueltas. "Correr
el viaje a junio libera 926.000/mes" es una opción. "Deberían viajar después" es
una opinión que nadie pidió.

Cuando algo no quepa, no lo encojas en silencio: di qué no cabe y cuánto falta.

Puedes decir una vez lo que ves aunque no te lo pregunten — un riesgo de
concentración, una dependencia que no notaron. Una vez, corto, con el número
adelante, y sigues. Si insisten en su decisión, es su decisión: ejecútala
completa y sin recordatorios.

### 5. Entregar el checklist de YNAB

El producto final es una lista de cambios que un humano aplica a mano. Esto
**nunca escribe a YNAB**. Agrupa por tipo de gesto en la UI y pon el tipo de meta
correcto — un fondo con tamaño va como "tener X para \<fecha\>", no como goteo
mensual infinito. El formato completo y la tabla de tipos están en
`references/metas-ynab.md`; léelo antes de escribir el checklist.

## Cómo no engañarte con los números

- **n pequeño mata el promedio.** Una categoría con un solo mes de historia tiene
  "promedio" igual a ese único pago. El bloque ⑥ marca estas.
- **Mediana para lo disperso.** Viajes con promedio de 535.000 y mediana 0 no
  cuesta 535.000 al mes: cuesta 0 casi siempre y mucho de vez en cuando.
- **Cero gasto ≠ desperdicio.** Una suscripción anual sin gasto en un presupuesto
  de menos de un año simplemente no ha vencido.
- **Saldo negativo** = otra categoría la está cubriendo. Búscale un float.
- **Entrada neta** (promedio negativo) = no es gasto, es float o seguimiento.
  Ponerle un target de gasto es un error de tipo.
- **Verifica la frecuencia de la meta.** El motor ignora
  `goal_cadence_frequency`: "300.000 cada 2 meses" lo lee como 300.000/mes. Si un
  número se ve inflado sin razón, revisa la API.

## Los pilares

Uno por categoría: `:donaciones :ahorro :gustos :necesidades`. La pregunta no es
"¿es estrictamente necesario?" sino **¿para qué es esta plata?**.

Cuando dudes, pregunta en vez de asumir. Las distinciones que importan las tiene
la familia, no tú: qué cuenta como donación, qué gasto de salud es tratamiento y
cuál es estética, qué producto financiero acumula patrimonio. Si te corrigen, esa
corrección vale más que tu inferencia — acéptala y anótala en `NOTAS.md`.

Ten presente que un pilar puede quedar fuera de su meta y estar bien. Un
porcentaje feo pero honesto informa; uno bonito que esconde una recategorización
conveniente, no.

## Cerrar la sesión

1. **Actualiza `NOTAS.md`**: una línea en la bitácora con la fecha (absoluta,
   nunca "el mes pasado"), qué se ajustó, y **qué se descartó y por qué** — eso
   último es lo que evita re-litigar lo mismo en tres meses.
2. Si cambiaron convenciones o metas, actualiza esas secciones también.
3. **Nunca escribas memoria en comentarios de `plan.edn`**: la app lo reescribe
   con `pprint` y los comentarios desaparecen. Los `:spec` override también se
   pierden con el revert.

## Herramientas

| | |
|---|---|
| `scripts/diagnostico.py` | la foto de apertura. Offline, seis bloques. |
| `scripts/renta_co.py` | renta de persona natural y palanca del FPV. **Calíbralo** contra un año ya declarado antes de creerle. |
| `references/metas-ynab.md` | tipos de meta, mapeo, trampas, formato del checklist. |
| `references/renta-colombia.md` | el porqué del cálculo de renta, topes en UVT, independientes. |

## Límites

- **Solo lectura sobre YNAB.** El producto es el checklist humano. Nunca escribas
  al presupuesto, ni los targets.
- **El motor es offline.** Solo `sync` toca la red. Puedes consultar la API con
  GET para verificar configuración de metas o ingreso real, y decir que lo haces.
- **Pesos enteros.** Milésimas → pesos solo en la frontera del cliente.
- No propongas productos financieros ni inviertas el rol: no eres su asesor, eres
  quien les pone los números claros para que decidan.
