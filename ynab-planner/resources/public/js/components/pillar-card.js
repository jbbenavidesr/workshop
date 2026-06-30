customElements.define(
  'pillar-card',
  class extends HTMLElement {
    /** @type HTMLElement | null */ #bar;
    /** @type HTMLElement | null */ #fill;
    /** @type HTMLElement | null */ #over;
    /** @type HTMLElement | null */ #tick;
    /** @type HTMLElement | null */ #pct;
    /** @type HTMLElement | null */ #amount;

    connectedCallback() {
      if (document.readyState !== 'loading') { this.init(); return; }
      document.addEventListener('DOMContentLoaded', () => this.init(), { once: true });
    }

    init() {
      this.#bar = this.querySelector('.pillar-bar');
      this.#fill = this.querySelector('.pillar-bar .fill');
      this.#over = this.querySelector('.pillar-bar .over');
      this.#tick = this.querySelector('.pillar-bar .tick');
      this.#pct = this.querySelector('.pillar-pct');
      this.#amount = this.querySelector('.pillar-amount');
    }

    // Public: controller calls this with { pillar, amount, actualPct, idealPct }.
    update(p) {
      if (!this.#fill) this.init();
      const g = barGeometry(p.actualPct, p.idealPct);
      if (this.#fill) this.#fill.style.inlineSize = g.solidPct + '%';
      if (this.#over) this.#over.style.inlineSize = g.overPct + '%';
      if (this.#tick) this.#tick.style.insetInlineStart = g.markPct + '%';
      if (this.#bar) {
        if (g.over) this.#bar.dataset.over = 'true';
        else delete this.#bar.dataset.over;
      }
      if (this.#pct) this.#pct.textContent = p.actualPct.toFixed(1) + '% / ' + p.idealPct + '%';
      if (this.#amount) this.#amount.textContent = formatCOP(p.amount);
    }
  },
);

// Target-relative bar geometry — mirrors views/bar-geometry on the server.
function barGeometry(actual, target) {
  const a = Number(actual), t = Number(target);
  if (a <= t) {
    return { solidPct: t > 0 ? (a / t) * 100 : 0, overPct: 0, over: false, markPct: 0 };
  }
  const solid = a > 0 ? (t / a) * 100 : 0;
  return { solidPct: solid, overPct: 100 - solid, over: true, markPct: solid };
}

function formatCOP(n) { return '$' + Number(n).toLocaleString('es-CO'); }
