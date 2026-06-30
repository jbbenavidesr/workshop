import './components/pillar-card.js';
import './components/category-row.js';

const form = document.querySelector('#plan-form');

// Progressive enhancement: serialize the plan form, recompute live, patch DOM.
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

  for (const p of view.pillars) {
    const el = document.querySelector(`pillar-card[data-pillar="${p.pillar}"]`);
    if (el) el.update(p);
  }

  const badge = document.querySelector('#diff-count');
  if (badge) badge.textContent = view.diffCount;
}

function format(n) { return '$' + Number(n).toLocaleString('es-CO'); }

if (form) {
  // category-row events bubble up to the form
  form.addEventListener('contribution-changed', recalc);
}
