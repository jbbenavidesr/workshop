#!/usr/bin/env python3
"""Diagnóstico del presupuesto: la foto que abre una sesión de ajuste.

Puramente offline — lee cache.edn y plan.edn, nunca toca la red. Replica el
cálculo de monthly del motor (engine/target.clj) para que los números coincidan
con lo que muestra la app.

    python3 scripts/diagnostico.py [--dir .] [--mes 2026-10]

Imprime seis bloques, en el orden en que conviene leerlos:

  1. INGRESO Y PILARES      dónde está parado el plan hoy
  2. COSTO REAL AMORTIZADO  lo que de verdad cuesta vivir cada mes
  3. GASTO SIN META         el hallazgo más grande casi siempre está acá
  4. SUBFINANCIADAS         metas que son un sobregiro silencioso
  5. HOLGURA                metas con más plata de la que se gasta
  6. BANDERAS               dónde los números de arriba son poco confiables

Los bloques 3-5 son los tres desajustes posibles. El 6 existe porque un promedio
con n=1 o una categoría con saldo gordo pueden hacer que 3-5 mientan.
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from edn_min import read_edn, pesos  # noqa: E402


# ── cálculo de la cuota mensual, igual que engine/target.clj ────────────────
def ym(s):
    return int(s[:4]) * 12 + int(s[5:7])


def derive_monthly(spec, balance, plan_month):
    """Cuota mensual en pesos para una spec de meta. Espejo de derive-monthly."""
    t = spec.get(":type")
    if t == ":monthly":
        return spec.get(":amount", 0)
    if t == ":balance-rolling":
        return spec.get(":monthly", 0)
    if t == ":needed-cadence":
        cad, amt = spec.get(":cadence"), spec.get(":amount", 0)
        if cad == ":yearly":
            return round(amt / 12)
        if cad == ":weekly":
            return round(amt * 52 / 12)
        return amt
    if t == ":balance-by-date":
        meses = max(1, ym(spec[":date"]) - ym(plan_month) + 1)
        return max(0, round((spec[":target"] - (balance or 0)) / meses))
    return 0


def mean(xs):
    return round(sum(xs) / len(xs)) if xs else 0


def median(xs):
    v = sorted(xs)
    n = len(v)
    if not n:
        return 0
    return v[n // 2] if n % 2 else mean([v[n // 2 - 1], v[n // 2]])


def spec_label(spec):
    t = spec.get(":type")
    if t == ":monthly":
        return "mensual"
    if t == ":needed-cadence":
        return f"cada {spec.get(':cadence', '?')[1:]} {pesos(spec.get(':amount', 0))}"
    if t == ":balance-by-date":
        return f"tener {pesos(spec.get(':target', 0))} para {spec.get(':date', '')[:7]}"
    if t == ":balance-rolling":
        return "saldo objetivo"
    return "SIN META"


def load(dir_, plan_month):
    cache, plan = read_edn(f"{dir_}/cache.edn"), read_edn(f"{dir_}/plan.edn")
    hist = cache.get(":history", {})
    rows = []
    for c in cache[":categories"]:
        ov = plan.get(":categories", {}).get(c[":id"], {})
        spec = ov.get(":spec", c.get(":ynab-spec", {}))
        # solo meses cerrados: el mes en curso siempre se ve artificialmente bajo
        xs = [r[":spent"] for r in hist.get(c[":id"], []) if r[":month"] < plan_month]
        monthly = derive_monthly(spec, c.get(":balance"), plan_month)
        rows.append(dict(
            id=c[":id"], name=c[":name"].strip(), group=c.get(":group-name", ""),
            pillar=ov.get(":pillar"), skip=bool(ov.get(":skip")),
            spec=spec, override=":spec" in ov, bal=c.get(":balance") or 0,
            # congelada = tú la pusiste en 0 a propósito; ≠ nunca tuvo meta
            congelada=monthly == 0 and ":spec" in ov,
            sin_meta_ynab=(c.get(":ynab-spec", {}) or {}).get(":type") in (None, ":none"),
            monthly=monthly, series=xs, n=len(xs),
            avg6=mean(xs[-6:]), avg12=mean(xs), med=median(xs), mx=max(xs) if xs else 0,
            meses=[r[":month"] for r in hist.get(c[":id"], []) if r[":month"] < plan_month],
        ))
    return cache, plan, rows


def es_grumosa(r):
    """¿Su gasto llega en pocos golpes grandes en vez de todos los meses?

    Importa porque el desembolso de una de estas NO es su costo del mes: se
    venía ahorrando. Para ellas el costo real es la cuota, no lo que salió."""
    t = r["spec"].get(":type")
    if t == ":balance-by-date" or (t == ":needed-cadence" and r["spec"].get(":cadence") == ":yearly"):
        return True
    nz = [x for x in r["series"] if x > 0]
    return bool(nz) and len(nz) * 3 <= r["n"] and r["mx"] > 3 * max(1, r["avg12"])


def tendencia(r):
    """(últimos 3 meses, los 3 anteriores) — para distinguir ruido de deriva."""
    s = r["series"]
    return (mean(s[-3:]), mean(s[-6:-3])) if len(s) >= 6 else (None, None)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", default=".")
    ap.add_argument("--mes", default=None, help="mes del plan YYYY-MM (default: el más nuevo del cache + 1)")
    a = ap.parse_args()

    tmp = read_edn(f"{a.dir}/cache.edn")
    meses = sorted({m[":month"] for v in tmp.get(":history", {}).values() for m in v})
    plan_month = a.mes or (meses[-1] if meses else "2026-01")
    cache, plan, rows = load(a.dir, plan_month)
    act = [r for r in rows if not r["skip"]]
    inc = plan.get(":income", 0)
    W = 34

    print(f"══ mes del plan {plan_month} · sync {cache.get(':synced-at', '?')[:16]} ══\n")

    # 1 ─ ingreso y pilares
    tot = sum(r["monthly"] for r in act)
    print("① INGRESO Y PILARES")
    metas = plan.get(":pillar-targets", {})
    for p in (":necesidades", ":gustos", ":ahorro", ":donaciones", None):
        s = sum(r["monthly"] for r in act if r["pillar"] == p)
        if not s and p is None:
            continue
        m = metas.get(p)
        objetivo = f"meta {m}%" if m else "SIN PILAR"
        print(f"   {str(p or '(sin pilar)'):<14}{pesos(s):>13}  {100 * s / max(tot, 1):5.1f}%   {objetivo}")
    d = tot - inc
    estado = "cuadra" if abs(d) < 1000 else ("SOBRA " + pesos(-d) if d < 0 else "FALTA " + pesos(d))
    print(f"   {'TOTAL':<14}{pesos(tot):>13}   vs ingreso {pesos(inc)}  →  {estado}")
    skipped = [r for r in rows if r["skip"]]
    if skipped:
        print(f"   ({len(skipped)} en :skip, fuera del plan: {', '.join(r['name'] for r in skipped[:4])}…)")

    # 2 ─ costo real amortizado
    print("\n② COSTO REAL POR MES  (las grumosas cuentan por su cuota, no por el desembolso)")
    gasto = {r["id"]: {h[":month"]: h[":spent"] for h in cache[":history"].get(r["id"], [])} for r in act}
    cerrados = [m for m in meses if m < plan_month][-12:]
    for m in cerrados:
        crudo = sum(gasto[r["id"]].get(m, 0) for r in act)
        amort = sum(r["monthly"] if es_grumosa(r) else gasto[r["id"]].get(m, 0) for r in act)
        print(f"   {m}   crudo {pesos(crudo):>12}   amortizado {pesos(amort):>12}")
    if len(cerrados) >= 4:
        u4 = round(sum(sum(r["monthly"] if es_grumosa(r) else gasto[r["id"]].get(m, 0)
                           for r in act) for m in cerrados[-4:]) / 4)
        print(f"   {'promedio últimos 4':<10}{'':>18}{pesos(u4):>25}   ← el ritmo actual, contra ingreso {pesos(inc)}")
    print("   El amortizado es el que compara contra el ingreso. El crudo tiene picos que")
    print("   ya venían ahorrados y valles de los meses en que no tocaba pagar.")

    # 3 ─ gasto sin meta (solo las que nunca tuvieron meta en YNAB; las congeladas
    #     a propósito con un :spec 0 son una decisión, no un olvido)
    print("\n③ GASTO SIN META  (dinero fantasma: sale todos los años y nadie lo presupuestó)")
    fantasma = [r for r in act if r["monthly"] == 0 and r["mx"] > 0
                and r["sin_meta_ynab"] and not r["congelada"]]
    if not fantasma:
        print("   — ninguno —")
    for r in sorted(fantasma, key=lambda r: -r["mx"]):
        anual = r["mx"] if r["n"] <= 2 else sum(r["series"])
        print(f"   {r['name'][:W]:<{W+2}} max {pesos(r['mx']):>11}  total {pesos(sum(r['series'])):>11}  n={r['n']:<3} "
              f"→ ~{pesos(round(anual / 12))}/mes si es anual")
    cong = [r for r in act if r["congelada"] and r["mx"] > 0]
    if cong:
        print(f"   (congeladas a propósito, no confundir: {', '.join(r['name'] for r in cong)})")

    # 4 y 5 ─ los dos desajustes. Las grumosas quedan fuera: comparar su cuota contra
    # el promedio de gasto es comparar peras con manzanas — el desembolso de una meta
    # anual aparece completo en un mes y en cero los otros once.
    comparables = [r for r in act if r["monthly"] > 0 and not es_grumosa(r)]

    print("\n④ SUBFINANCIADAS  (la meta es menor que el gasto típico)")
    sub = [r for r in comparables if r["n"] >= 3 and r["avg6"] - r["monthly"] > 50_000]
    if not sub:
        print("   — ninguna —")
    for r in sorted(sub, key=lambda r: r["monthly"] - r["avg6"]):
        t3, t3p = tendencia(r)
        flecha = "" if t3 is None else ("  ↑ subiendo" if t3 > t3p * 1.25 else "  ↓ bajando" if t3 * 1.25 < t3p else "")
        print(f"   {r['name'][:W]:<{W+2}} meta {pesos(r['monthly']):>11}  avg6 {pesos(r['avg6']):>11} "
              f" med {pesos(r['med']):>11}  falta {pesos(r['avg6'] - r['monthly']):>10}{flecha}")

    print("\n⑤ HOLGURA  (la meta es mayor que el gasto típico — de acá sale la plata)")
    hol = [r for r in comparables if r["mx"] > 0 and r["monthly"] - r["avg6"] > 50_000]
    if not hol:
        print("   — ninguna —")
    for r in sorted(hol, key=lambda r: -(r["monthly"] - r["avg6"])):
        t3, t3p = tendencia(r)
        flecha = "  ↑ OJO, subiendo" if t3 is not None and t3 > t3p * 1.25 else ""
        print(f"   {r['name'][:W]:<{W+2}} meta {pesos(r['monthly']):>11}  avg6 {pesos(r['avg6']):>11} "
              f" holgura {pesos(r['monthly'] - r['avg6']):>10}  saldo {pesos(r['bal']):>11}{flecha}")
    grum = [r for r in act if es_grumosa(r) and r["monthly"] > 0]
    if grum:
        print(f"   ({len(grum)} grumosas excluidas de ④ y ⑤ — su cuota no se compara con un promedio: "
              f"{', '.join(r['name'] for r in grum[:5])}…)")

    # 6 ─ banderas
    print("\n⑥ BANDERAS  (dónde desconfiar de los números de arriba)")
    flags = []
    for r in act:
        if r["monthly"] > 0 and 0 < r["n"] < 4:
            flags.append(f"{r['name'][:W]:<{W+2}} n={r['n']} — el promedio no significa nada todavía")
        if r["n"] >= 6 and r["mx"] > 4 * max(1, r["med"]) and r["med"] > 0:
            flags.append(f"{r['name'][:W]:<{W+2}} muy dispersa (max {pesos(r['mx'])} vs mediana {pesos(r['med'])}) — usa la mediana")
        if r["monthly"] == 0 and r["bal"] > 0 and r["mx"] > 0:
            flags.append(f"{r['name'][:W]:<{W+2}} sin cuota pero con saldo {pesos(r['bal'])} — ¿el saldo alcanza o se olvidó?")
        if r["bal"] < 0:
            flags.append(f"{r['name'][:W]:<{W+2}} SALDO NEGATIVO {pesos(r['bal'])} — otra categoría lo está cubriendo")
        if r["pillar"] is None:
            flags.append(f"{r['name'][:W]:<{W+2}} sin pilar — queda fuera de la distribución")
        if r["n"] >= 6 and r["avg6"] < 0:
            flags.append(f"{r['name'][:W]:<{W+2}} entra plata neta ({pesos(r['avg6'])}/mes) — no es un gasto, es float o seguimiento")
    for f in flags:
        print("   " + f)
    if not flags:
        print("   — ninguna —")

    ovr = [r for r in act if r["override"]]
    if ovr:
        print(f"\n   {len(ovr)} categorías con :spec override en plan.edn (checklist de YNAB pendiente de aplicar)")


if __name__ == "__main__":
    main()
