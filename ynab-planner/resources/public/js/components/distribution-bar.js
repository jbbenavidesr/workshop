customElements.define(
  'distribution-bar',
  class extends HTMLElement {
    /** @type HTMLElement | null */ #fill;
    /** @type HTMLElement | null */ #pct;
    /** @type HTMLElement | null */ #amount;

    connectedCallback() {
      if (document.readyState !== 'loading') { this.init(); return; }
      document.addEventListener('DOMContentLoaded', () => this.init(), { once: true });
    }

    init() {
      this.#fill = this.querySelector('.bar > span');
      this.#pct = this.querySelector('.pct');
      this.#amount = this.querySelector('.amount');
    }

    // Public: controller calls this with { pillar, amount, actualPct, idealPct }.
    update(p) {
      if (!this.#fill) this.init();
      if (this.#fill) this.#fill.style.inlineSize = Math.min(100, p.actualPct) + '%';
      if (this.#pct) this.#pct.textContent = p.actualPct.toFixed(1) + '% / ' + p.idealPct + '%';
      if (this.#amount) this.#amount.textContent = formatCOP(p.amount);
    }
  },
);

function formatCOP(n) { return '$' + Number(n).toLocaleString('es-CO'); }
