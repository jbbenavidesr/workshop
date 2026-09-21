"""Lector mínimo de EDN, suficiente para cache.edn y plan.edn.

No es un parser completo de EDN: no maneja sets (#{}), tagged literals ni chars.
Los archivos del planner solo tienen mapas, vectores, strings, números, keywords
y booleanos, así que alcanza. Las keywords vuelven como strings con los dos
puntos incluidos (":monthly"), que es más simple que inventar un tipo nuevo.

Uso:
    from edn_min import read_edn
    cache = read_edn("cache.edn")
"""
import re


class _Reader:
    def __init__(self, text):
        self.t = text
        self.i = 0

    def _skip(self):
        # espacios, comas (que en EDN son espacio en blanco) y comentarios ;
        while self.i < len(self.t):
            c = self.t[self.i]
            if c in " \t\n\r,":
                self.i += 1
            elif c == ";":
                nl = self.t.find("\n", self.i)
                self.i = len(self.t) if nl == -1 else nl + 1
            else:
                return

    def read(self):
        self._skip()
        if self.i >= len(self.t):
            raise ValueError("EOF inesperado")
        c = self.t[self.i]
        if c == "{":
            self.i += 1
            d = {}
            while True:
                self._skip()
                if self.t[self.i] == "}":
                    self.i += 1
                    return d
                k = self.read()
                d[k] = self.read()
        if c == "[":
            self.i += 1
            a = []
            while True:
                self._skip()
                if self.t[self.i] == "]":
                    self.i += 1
                    return a
                a.append(self.read())
        if c == '"':
            self.i += 1
            out = []
            while self.t[self.i] != '"':
                if self.t[self.i] == "\\":
                    out.append(self.t[self.i + 1])
                    self.i += 2
                else:
                    out.append(self.t[self.i])
                    self.i += 1
            self.i += 1
            return "".join(out)
        tok = re.match(r"[^\s\[\]{},;]+", self.t[self.i:]).group(0)
        self.i += len(tok)
        if tok == "true":
            return True
        if tok == "false":
            return False
        if tok == "nil":
            return None
        if re.fullmatch(r"-?\d+", tok):
            return int(tok)
        if re.fullmatch(r"-?\d+\.\d+", tok):
            return float(tok)
        return tok  # keyword o símbolo, tal cual


def read_edn(path):
    with open(path, encoding="utf-8") as fh:
        return _Reader(fh.read()).read()


def pesos(n):
    """Formato colombiano: 1.234.567"""
    return f"{n:,.0f}".replace(",", ".")
