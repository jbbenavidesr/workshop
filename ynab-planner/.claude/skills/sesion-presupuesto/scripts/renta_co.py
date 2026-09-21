#!/usr/bin/env python3
"""Modelo de renta de persona natural (cédula general, rentas de trabajo, Colombia).

Sirve para responder "¿cuánto debo guardar al mes para la declaración?" y para
evaluar la palanca del aporte voluntario a pensión (FPV).

    python3 scripts/renta_co.py --bruto-mes 22761765 --fpv-mes 2000000 \
        --prepagada-mes 400000 --retenido-mes 2705000 --uvt 52400

    # barrer niveles de FPV para ver el intercambio caja vs impuesto
    python3 scripts/renta_co.py ... --barrer-fpv 0 500000 1000000 1500000 2000000

CALIBRA ANTES DE CREER. Corre el modelo con las cifras del año que YA declaraste
y compara contra lo que efectivamente pagaste. Si no coincide dentro de ~5%, algo
falta (una deducción, dependientes, otro ingreso) y el pronóstico no sirve todavía.

La UVT cambia cada año — la DIAN la fija en octubre. Pásala con --uvt; el default
es una estimación y el resultado se mueve con ella.
"""
import argparse

# Tabla del art. 241 ET, en UVT: (límite superior, tasa marginal, suma fija)
TABLA = [(1090, 0.00, 0), (1700, 0.19, 0), (4100, 0.28, 116),
         (8670, 0.33, 788), (18970, 0.35, 2296), (31000, 0.37, 5901),
         (float("inf"), 0.39, 10352)]
TOPE_GLOBAL_UVT = 1340   # tope de rentas exentas + deducciones (art. 336)
TOPE_25_UVT = 790        # tope de la renta exenta del 25% laboral (art. 206 n°10)
TOPE_SALUD_UVT_MES = 16  # tope de la deducción por medicina prepagada (art. 387)


def pesos(n):
    return f"{n:,.0f}".replace(",", ".")


def impuesto_uvt(u):
    """Impuesto en UVT para una renta líquida gravable de u UVT."""
    prev = 0
    for lim, tasa, fija in TABLA:
        if u <= lim:
            return (u - prev) * tasa + fija
        prev = lim
    return 0.0


def liquidar(bruto_anual, oblig_anual, fpv_anual, prepagada_anual, uvt):
    """Devuelve el desglose de la liquidación anual.

    oblig_anual = salud + pensión obligatoria + fondo de solidaridad. Son
    ingreso no constitutivo de renta (INCRNGO): se restan antes de todo y NO
    consumen el tope global, a diferencia del FPV y la prepagada.
    """
    base = bruto_anual - oblig_anual
    prepagada = min(prepagada_anual, TOPE_SALUD_UVT_MES * 12 * uvt)
    # el 25% exento se calcula sobre lo que queda después de las otras exentas
    ex25 = min(0.25 * (base - fpv_anual - prepagada), TOPE_25_UVT * uvt)
    tope = min(0.40 * base, TOPE_GLOBAL_UVT * uvt)
    pedido = fpv_anual + prepagada + ex25
    exentas = min(pedido, tope)
    rlg = base - exentas
    imp = impuesto_uvt(rlg / uvt) * uvt
    return dict(base=base, prepagada=prepagada, ex25=ex25, tope=tope,
                pedido=pedido, exentas=exentas, holgura=tope - pedido,
                rlg=rlg, uvt_rlg=rlg / uvt, impuesto=round(imp))


def fpv_optimo(prepagada_anual, uvt):
    """FPV anual que agota justo el tope global sin desperdiciar nada.

    Por encima de esto el aporte no da ningún beneficio: el 25% exento ya está
    topado en 790 UVT, así que cada peso extra de FPV solo queda bloqueado.
    """
    return max(0, TOPE_GLOBAL_UVT * uvt - TOPE_25_UVT * uvt
               - min(prepagada_anual, TOPE_SALUD_UVT_MES * 12 * uvt))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--bruto-mes", type=float, required=True)
    ap.add_argument("--oblig-mes", type=float, default=None,
                    help="salud + pensión + solidaridad al mes (INCRNGO)")
    ap.add_argument("--fpv-mes", type=float, default=0)
    ap.add_argument("--prepagada-mes", type=float, default=0)
    ap.add_argument("--retenido-mes", type=float, default=0)
    ap.add_argument("--uvt", type=float, default=52_400, help="UVT del año gravable")
    ap.add_argument("--barrer-fpv", type=float, nargs="*", default=None)
    a = ap.parse_args()

    if a.oblig_mes is None:  # 4% salud + 4% pensión sobre el 100%, aproximación
        a.oblig_mes = a.bruto_mes * 0.08
        print(f"[oblig-mes estimado en {pesos(a.oblig_mes)} — pásalo del desprendible]")

    B, O, P = a.bruto_mes * 12, a.oblig_mes * 12, a.prepagada_mes * 12
    ret = a.retenido_mes * 12
    r = liquidar(B, O, a.fpv_mes * 12, P, a.uvt)

    print(f"\nUVT {pesos(a.uvt)}   bruto año {pesos(B)}   retenido año {pesos(ret)}\n")
    print(f"  ingresos − INCRNGO                 {pesos(r['base']):>14}")
    print(f"  FPV                                {pesos(a.fpv_mes * 12):>14}")
    print(f"  medicina prepagada (tope 16 UVT/m) {pesos(r['prepagada']):>14}")
    print(f"  25% exento (tope 790 UVT)          {pesos(r['ex25']):>14}")
    print(f"  ── suma pedida                     {pesos(r['pedido']):>14}")
    print(f"  ── tope global (1.340 UVT / 40%)   {pesos(r['tope']):>14}   holgura {pesos(r['holgura'])}")
    if r["holgura"] < 0:
        print("     ⚠ te pasaste del tope: parte de lo aportado no da beneficio")
    print(f"\n  renta líquida gravable             {pesos(r['rlg']):>14}  ({r['uvt_rlg']:.0f} UVT)")
    print(f"  IMPUESTO                           {pesos(r['impuesto']):>14}")
    if ret:
        saldo = r["impuesto"] - ret
        etiq = "A PAGAR" if saldo > 0 else "a favor"
        print(f"  − retenido                         {pesos(ret):>14}")
        print(f"  = SALDO {etiq:<26} {pesos(abs(saldo)):>14}   → {pesos(abs(saldo) / 12)}/mes a guardar")

    opt = fpv_optimo(P, a.uvt)
    print(f"\n  FPV que agota el tope: {pesos(opt)}/año = {pesos(opt / 12)}/mes")
    d = opt / 12 - a.fpv_mes
    if abs(d) < 5000:
        print("  → ya estás en el óptimo.")
    elif d > 0:
        print(f"  → te faltan {pesos(d)}/mes para aprovechar todo el beneficio.")
    else:
        print(f"  → estás aportando {pesos(-d)}/mes de más: bloqueado y sin beneficio fiscal.")

    if a.barrer_fpv:
        print(f"\n  {'FPV/mes':>12} {'impuesto año':>15} {'saldo decl.':>14} {'marginal':>10}")
        prev = prev_f = None
        for f in a.barrer_fpv:
            x = liquidar(B, O, f * 12, P, a.uvt)
            marg = f"{100 * (prev - x['impuesto']) / ((f - prev_f) * 12):.0f}%" if prev and f > prev_f else ""
            print(f"  {pesos(f):>12} {pesos(x['impuesto']):>15} {pesos(x['impuesto'] - ret):>14} {marg:>10}")
            prev, prev_f = x["impuesto"], f
        print("  La marginal es lo que cuesta cada peso que dejes de aportar. Es plata")
        print("  cara: para flujo de caja, mira primero la holgura del gasto.")


if __name__ == "__main__":
    main()
