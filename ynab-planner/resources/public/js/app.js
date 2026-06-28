import './components/distribution-bar.js';
import './components/category-row.js';

const form = document.querySelector('#plan-form');

// Progressive enhancement: serialize the whole form, recompute live, patch DOM.
async function recalc() {
  const res = await fetch('/plan/update', {
    method: 'POST',
    headers: { 'Accept': 'application/json',
               'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(new FormData(form))
  });
  if (res.ok) applyView(await res.json());
  else console.error('plan update failed:', res.status);
}

function applyView(view) {
  const total = document.querySelector('#total');
  if (total) total.textContent = format(view.balance.total);
  const diff = document.querySelector('#difference');
  if (diff) {
    diff.textContent = format(view.balance.difference);
    diff.dataset.status = view.balance.status;
  }
  for (const p of view.distribution.pillars) {
    const el = document.querySelector(`distribution-bar[data-pillar="${p.pillar}"]`);
    if (el) el.update(p);
  }

  // Re-render the diff checklist, preserving the server-rendered <h2> heading.
  const diffSection = document.querySelector('#diff');
  if (diffSection) {
    // Remove any existing <ul> or <p> after the heading (the dynamic part).
    for (const child of [...diffSection.children]) {
      if (child.tagName !== 'H2') child.remove();
    }
    if (!view.diff || view.diff.length === 0) {
      const p = document.createElement('p');
      p.textContent = 'Sin cambios — el plan coincide con YNAB.';
      diffSection.appendChild(p);
    } else {
      const ul = document.createElement('ul');
      for (const item of view.diff) {
        const li = document.createElement('li');
        li.textContent = `${item.name}: ${format(item.current)} → ${format(item.planned)}`;
        ul.appendChild(li);
      }
      diffSection.appendChild(ul);
    }
  }
}

function format(n) { return '$' + Number(n).toLocaleString('es-CO'); }

if (form) {
  // category-row events bubble up to the form
  form.addEventListener('contribution-changed', recalc);
  form.querySelector('#income')?.addEventListener('change', recalc);
}
