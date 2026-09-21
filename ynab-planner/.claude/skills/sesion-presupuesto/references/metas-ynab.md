# Metas de YNAB: tipos, mapeo y trampas

Lee esto cuando vayas a **entregar el checklist de cambios** o cuando un número
del diagnóstico no cuadre con lo que muestra YNAB.

## Los cinco tipos y cómo los lee el motor

`ynab/mapping.clj` traduce el `goal_type` de la API a una spec interna. La cuota
mensual sale de `engine/target.clj`.

| YNAB `goal_type` | En la UI | spec interna | cuota mensual |
|---|---|---|---|
| `MF` | *Monthly Funding* | `:monthly` | el monto, tal cual |
| `NEED` cadencia 1 | "Necesito X cada mes" | `:monthly` | el monto |
| `NEED` cadencia 13 | "Necesito X cada año" | `:needed-cadence :yearly` | **monto ÷ 12** |
| `NEED` cadencia 2 | "Necesito X cada semana" | `:needed-cadence :weekly` | monto × 52 ÷ 12 |
| `NEED` cadencia 0 | "Tener X para \<fecha\>" (una vez) | `:balance-by-date` | (meta − saldo) ÷ meses que faltan |
| `NEED` cadencia 3–12 | "cada 2…11 meses" | `:needed-cadence :monthly :every n` | monto ÷ n |
| `NEED` cadencia 14 | "cada 2 años" | `:needed-cadence :yearly :every 2` | monto ÷ 24 |
| `TBD` | *Target Category Balance by Date* | `:balance-by-date` | igual que arriba |
| `TB` | *Target Category Balance* (sin fecha) | `:balance-rolling` | `goal_under_funded` completo |
| sin meta | — | `:none` | 0 |

`goal_cadence` viene en dos familias. Para 0, 1, 2 y 13 el período lo dice
`goal_cadence_frequency` ("cada 2 meses" = cadencia 1, frecuencia 2), y el mapeo
lo traduce a `:every n`. Para 3–12 y 14 la frecuencia se ignora y el período va
codificado en la cadencia misma (3 = cada 2 meses … 12 = cada 11 meses).

## Elegir el tipo correcto al proponer un cambio

- **Gasto que se repite todos los meses** → `NEED` mensual. Aburrido y correcto.
- **Gasto anual** (suscripción, seguro, impuesto) → `NEED` anual con la **fecha
  real de cobro**. El motor lo reparte en 12; YNAB lo pide completo al vencer.
  Esa discrepancia es conocida, no un bug — ver la nota de abajo.
- **Fondo que tiene un tamaño y se acaba** (colchón, fondo de emergencia, meta de
  viaje) → `TBD` "tener X para \<fecha\>". Cuando llega al monto **deja de pedir
  solo**, que es justo lo que quieres de un colchón.
- **Float que oscila alrededor de un número** (préstamos por cobrar, colchón de
  tasa de cambio) → también `TBD`. Si el saldo sube solo (te pagaron, el dólar
  subió) YNAB deja de pedir; si cae, vuelve a pedir. El comportamiento correcto
  sin que nadie tenga que acordarse.
- **Categoría de puro seguimiento** que ni siquiera es un gasto → `:skip true` en
  `plan.edn`, no una meta en YNAB.

Un fondo de emergencia con `NEED` mensual sin fin es un goteo infinito: nunca se
declara completo y compite para siempre con todo lo demás. Dale tamaño.

## Trampas (todas encontradas en sesiones reales)

**El cache viejo tiene las frecuencias mal.** Hasta 2026-09-20 `mapping.clj`
ignoraba `goal_cadence_frequency`, así que "300.000 cada 2 meses" se leía como
300.000/mes: el doble. Ya está arreglado, pero **un `cache.edn` sincronizado
antes del fix conserva el error** — si los números no cuadran con YNAB, sincroniza
antes de investigar nada más.

**Fechas vencidas de metas anuales.** Una `NEED` anual cuyo mes ya pasó sigue
pidiendo plata como si no se hubiera pagado. Después de pagar un seguro o un
impuesto, **mueve la fecha al año siguiente** o el checklist te va a perseguir
con una meta ya cumplida.

**Las anuales se reparten ÷12, YNAB las pide completas.** Para el motor una meta
de 5.500.000 en septiembre son 458.333/mes todo el año. Para YNAB, si estamos en
agosto, son 5.500.000 ya. Las dos están bien: el motor planea un objetivo mensual
estable, YNAB fondea este mes. Cuando la fecha está cerca y el saldo bajo, gana
YNAB — revisa a mano. (Está en `docs/roadmap.md` como tensión conocida.)

**Categorías internas.** `Inflow: Ready to Assign` tiene `internal: true` y el
cliente ya la filtra. Las de `Credit Card Payments` no son gasto: van a `:skip`.

**`plan.edn` lo reescribe la app con `pprint`: los comentarios NO sobreviven.**
Cualquier cosa que quieras recordar va en `NOTAS.md`, nunca en un comentario del
`plan.edn`. Los `:spec` override también pueden desaparecer si se usa el revert.

## Consultar la API directamente

El motor es offline por diseño, pero para verificar configuración de metas sí
vale ir a la fuente (solo GET, nunca escribir):

```bash
set -a && . ./.env && set +a
BUDGET=$(python3 -c "import re;print(re.search(r':budget-id \"([^\"]+)',open('plan.edn').read()).group(1))")
curl -s -H "Authorization: Bearer $YNAB_TOKEN" \
  "https://api.ynab.com/v1/budgets/$BUDGET/categories"          # metas crudas
curl -s -H "Authorization: Bearer $YNAB_TOKEN" \
  "https://api.ynab.com/v1/budgets/$BUDGET/months"              # ingreso real por mes
curl -s -H "Authorization: Bearer $YNAB_TOKEN" \
  "https://api.ynab.com/v1/budgets/$BUDGET/transactions?since_date=2026-01-01"
```

`/months` es la mejor fuente para el ingreso real por mes (`income`, `budgeted`,
`activity`). `/transactions` agrupado por `payee_name` separa las fuentes de
ingreso — útil cuando hay que distinguir el sueldo estable del variable. El
salario suele venir como transacción *split*: el bruto entra a Ready to Assign y
las deducciones salen como subtransacciones negativas.

## Formato del checklist

Agrupa por tipo de trabajo, porque son gestos distintos en la UI:

1. **Solo cambiar el monto** — una tabla de "de → a". Es el 80% y va de corrido.
2. **Cambiar tipo o fecha de meta** — una línea por categoría diciendo qué tiene
   hoy, qué debe quedar, y **por qué** (la razón evita que se deshaga después).
3. **Metas nuevas** — categoría y meta completa, con fecha.
4. **No tocar, solo no fondear** — las pausadas. Deja la meta quieta y asigna 0.
   Se van a ver en rojo y está bien: es verdad, se pausaron. Cambiar la meta para
   que no se vea en rojo es esconderse la decisión.
5. **Lo que queda pendiente de decidir** — con el número que falta para decidirlo.
