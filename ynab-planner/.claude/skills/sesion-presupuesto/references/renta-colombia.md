# Renta de persona natural (Colombia) — lo que hace falta para presupuestarla

Lee esto cuando la pregunta sea "¿cuánto guardo para la declaración?", "¿me
conviene aportar más al FPV?" o "¿de dónde salió este saldo a pagar?".

El cálculo está en `scripts/renta_co.py`. Esto explica el porqué, para que puedas
juzgar si el resultado tiene sentido.

## Por qué importa presupuestarla

La retención en la fuente **no** es el impuesto: es un anticipo. Si retienen de
menos, en agosto del año siguiente llega un saldo a pagar que nadie presupuestó.
Es el caso clásico de "gasto sin meta": sale todos los años, es grande, y no
tiene categoría. Merece una meta anual con fecha, no una sorpresa.

## La secuencia (cédula general, rentas de trabajo)

```
  ingresos brutos laborales
− aportes obligatorios (salud + pensión + fondo de solidaridad)   ← INCRNGO
  ─────────────────────────────────────────────────────────────
= base
− rentas exentas y deducciones, TODAS dentro de un tope común:
     · aporte voluntario a pensión / FPV / AFC
     · medicina prepagada          (tope 16 UVT por mes)
     · dependientes                (si aplica)
     · intereses de vivienda       (si aplica)
     · 25% exento laboral          (tope 790 UVT al año)
  tope conjunto: el menor entre 40% de la base y 1.340 UVT
  ─────────────────────────────────────────────────────────────
= renta líquida gravable  →  tabla del art. 241  →  impuesto
− retenciones del año                             =  saldo a pagar
```

Dos cosas que se confunden seguido:

**Los aportes obligatorios NO consumen el tope.** Son ingreso no constitutivo de
renta: salen antes, y el tope de 1.340 UVT ni los ve. El FPV y la prepagada sí lo
consumen.

**El 25% exento se calcula sobre lo que queda después de las otras exentas**, y
está topado en 790 UVT al año. Como casi siempre queda topado, en la práctica
**el tope global menos 790 UVT es todo el espacio que hay** para repartir entre
FPV, prepagada y lo demás.

## La consecuencia: el FPV tiene un óptimo exacto

```
FPV óptimo al año = (1.340 − 790) × UVT − prepagada − otras deducciones
```

- **Por debajo**: estás dejando beneficio sin usar.
- **Por encima**: cero beneficio adicional. La plata queda bloqueada (10 años, o
  vivienda) a cambio de nada.

Y en el otro sentido: **cada peso que dejes de aportar al FPV cuesta tu tasa
marginal en impuesto**. Con una renta líquida en el tramo de 1.700–4.100 UVT eso
es 28% parejo. Es una palanca real de flujo de caja, pero cara — antes de usarla,
mira la holgura del gasto.

Ojo con el detalle de forma: la deducción por salud del art. 387 la toma **quien
hace el pago**. Si la prepagada la paga otra persona y no está a nombre del
declarante, vale confirmarlo con el contador antes de contarla.

## Salario integral

Para **retención mensual** la base es el 70% del salario integral. Para la
**declaración anual** el ingreso es el total. No mezcles las dos bases: si
calculas el anual sobre el 70% el impuesto sale muy por debajo.

## La UVT se mueve todos los años

La DIAN la fija en octubre para el año siguiente. Todos los topes están en UVT,
así que **el resultado se mueve con ella**. Si estás estimando la UVT del año en
curso, dilo explícitamente y presupuesta con holgura.

## Calibrar antes de creer

Corre el modelo con las cifras de un año **ya declarado** y compáralo con lo que
se pagó de verdad. Si no queda dentro de ~5%, falta algo (una deducción, otro
ingreso, dependientes) y el pronóstico no sirve todavía.

Cuidado con un error fácil: el saldo que se paga en agosto corresponde al **año
gravable anterior**, no al actual. Si el salario cambió entre uno y otro, calibrar
contra las cifras de hoy da un falso positivo.

## Cuánto presupuestar

No el saldo calculado pelado. Súbele por la UVT estimada, por ingresos extra no
previstos y porque la retención muchas veces se liquida por *procedimiento 2*
(un porcentaje fijo semestral) que no coincide con el cálculo punto a punto.
Sobrar en esta categoría no duele; faltar sí.

## Independientes por prestación de servicios

Cotizan sobre el 40% de los ingresos, con piso de 1 SMMLV. Salud 12,5% +
pensión 16% del IBC. Con ingresos donde el 40% queda por debajo del mínimo,
manda el piso — que suele ser bastante más de lo que la gente espera. Verifica
el SMMLV del año antes de dar el número.
