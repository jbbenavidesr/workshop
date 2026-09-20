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

  // reveal a revert button only for categories the plan actually overrides
  const overridden = new Set(view.overridden);
  for (const btn of document.querySelectorAll('[data-reset]')) {
    btn.hidden = !overridden.has(btn.value);
  }
  const resetAll = document.querySelector('#reset-all');
  if (resetAll) resetAll.hidden = overridden.size === 0;

  // slack moves with every edit — the engine recomputes and formats it, we place it
  for (const [id, s] of Object.entries(view.slack)) {
    const pill = document.querySelector(`category-row[data-id="${id}"] .pill`);
    if (!pill) continue;
    pill.textContent = s.text;
    pill.dataset.slack = s.kind;
  }
  // ponytail: the sparkline's target line and over-budget tint still reflect the
  // amount at page load; they catch up on reload. Send the geometry too if that bites.
}

function format(n) { return '$' + Number(n).toLocaleString('es-CO'); }

if (form) {
  // category-row events bubble up to the form
  form.addEventListener('contribution-changed', recalc);
}
