customElements.define(
  'category-row',
  class extends HTMLElement {
    /** @type HTMLInputElement | null */ #input;

    connectedCallback() {
      if (document.readyState !== 'loading') { this.init(); return; }
      document.addEventListener('DOMContentLoaded', () => this.init(), { once: true });
    }

    init() {
      this.#input = this.querySelector('input[type="number"]');
      if (!this.#input) { console.warn(this, 'A number input is required'); return; }
      this.addEventListener('change', this);
    }

    handleEvent(event) {
      if (event.type === 'change') this.#onChange();
    }

    #onChange() {
      this.dispatchEvent(new CustomEvent('contribution-changed', {
        bubbles: true, detail: { id: this.dataset.id }
      }));
    }
  },
);
