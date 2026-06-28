customElements.define(
  'category-row',
  class extends HTMLElement {
    /** @type HTMLInputElement | null */ #input;
    /** @type HTMLElement | null */ #echo;

    connectedCallback() {
      if (document.readyState !== 'loading') { this.init(); return; }
      document.addEventListener('DOMContentLoaded', () => this.init(), { once: true });
    }

    init() {
      this.#input = this.querySelector('input[type="number"]');
      this.#echo = this.querySelector('.amount-echo');
      if (!this.#input) { console.warn(this, 'A number input is required'); return; }
      // Listen on the row: 'input' (live echo) and 'change' (amount OR pillar select).
      this.addEventListener('input', this);
      this.addEventListener('change', this);
      this.#renderEcho();
    }

    handleEvent(event) {
      if (event.type === 'input' && event.target === this.#input) this.#renderEcho();
      else if (event.type === 'change') this.#onChange();
    }

    #renderEcho() {
      if (!this.#echo || !this.#input) return;
      const n = Number(this.#input.value);
      this.#echo.textContent = Number.isFinite(n) ? '$' + n.toLocaleString('es-CO') : '';
    }

    #onChange() {
      this.dispatchEvent(new CustomEvent('contribution-changed', {
        bubbles: true, detail: { id: this.dataset.id }
      }));
    }
  },
);
