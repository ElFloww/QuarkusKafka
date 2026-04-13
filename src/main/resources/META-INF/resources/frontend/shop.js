/* ================================================================
   Tuuuur Merch Shop — logique front-end
   ================================================================ */

// ── État du panier ──────────────────────────────────────────────
// cart = [{ id, name, price, requiredRank, qty }, ...]
let cart = [];

// ── Gestion du panier ───────────────────────────────────────────

function addToCart(id, name, price, requiredRank) {
  const existing = cart.find(i => i.id === id);
  if (existing) {
    existing.qty += 1;
  } else {
    cart.push({ id, name, price: parseFloat(price), requiredRank, qty: 1 });
  }
  renderCart();

  // Feedback visuel sur la carte article
  const btn  = document.getElementById('btn-' + id);
  const card = document.getElementById('card-' + id);
  if (btn)  { btn.classList.add('added'); btn.textContent = '✓ Dans le panier'; }
  if (card) { card.classList.add('in-cart'); }

  showToast('🛒', `${name} ajouté au panier`);
}

function removeFromCart(id) {
  cart = cart.filter(i => i.id !== id);
  renderCart();
  // Reset card button
  const btn  = document.getElementById('btn-' + id);
  const card = document.getElementById('card-' + id);
  if (btn)  { btn.classList.remove('added'); btn.textContent = '+ Ajouter au panier'; }
  if (card) { card.classList.remove('in-cart'); }
}

function updateQty(id, delta) {
  const item = cart.find(i => i.id === id);
  if (!item) return;
  item.qty = Math.max(1, item.qty + delta);
  renderCart();
}

function renderCart() {
  const emptyEl  = document.getElementById('cartEmpty');
  const itemsEl  = document.getElementById('cartItems');
  const totalBlk = document.getElementById('cartTotalBlock');
  const countEl  = document.getElementById('cartCount');
  const totalEl  = document.getElementById('cartTotal');

  // Mise à jour du compteur navbar
  const totalQty = cart.reduce((s, i) => s + i.qty, 0);
  if (countEl) countEl.textContent = totalQty;

  if (cart.length === 0) {
    emptyEl.style.display  = 'block';
    itemsEl.style.display  = 'none';
    totalBlk.style.display = 'none';
    return;
  }

  emptyEl.style.display  = 'none';
  itemsEl.style.display  = 'flex';
  totalBlk.style.display = 'block';

  itemsEl.innerHTML = cart.map(item => `
    <div class="cart-item">
      <div class="ci-info">
        <div class="ci-name">${item.name}</div>
        <div class="ci-price">${(item.price * item.qty).toFixed(2)} €</div>
      </div>
      <div class="cart-qty">
        <button class="qty-btn" onclick="updateQty('${item.id}', -1)">−</button>
        <span class="qty-num">${item.qty}</span>
        <button class="qty-btn" onclick="updateQty('${item.id}', +1)">+</button>
      </div>
      <button class="ci-remove" onclick="removeFromCart('${item.id}')" title="Retirer">✕</button>
    </div>
  `).join('');

  const total = cart.reduce((s, i) => s + i.price * i.qty, 0);
  if (totalEl) totalEl.textContent = total.toFixed(2) + ' €';
}

// ── Checkout ─────────────────────────────────────────────────────

async function checkout() {
  if (cart.length === 0) return;

  const playerId   = (document.getElementById('playerId').value   || '').trim() || 'player-demo';
  const playerName = (document.getElementById('playerName').value || '').trim() || 'Joueur Anonymous';
  const playerRank = document.getElementById('playerRank').value;

  const btn      = document.getElementById('btnCheckout');
  const feedback = document.getElementById('checkoutFeedback');

  btn.disabled    = true;
  btn.textContent = '⏳ Envoi en cours…';
  feedback.style.display = 'none';

  const snapshot = [...cart]; // copie pour ne pas être affecté par un clear prématuré
  const results  = [];
  let   hasError = false;

  for (const item of snapshot) {
    try {
      const res = await fetch('/api/orders', {
        method:  'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          playerId,
          playerName,
          playerRank,
          itemId:   item.id,
          quantity: item.qty
        })
      });
      const data = await res.json();
      if (res.status === 202) {
        results.push(`✅ ${item.name} (${item.qty}×) → ${data.orderId.substring(0, 8)}…`);
      } else {
        results.push(`❌ ${item.name} : ${data.error || 'erreur'}`);
        hasError = true;
      }
    } catch (err) {
      results.push(`❌ ${item.name} : serveur inaccessible`);
      hasError = true;
    }
  }

  // Afficher résumé
  feedback.innerHTML  = results.map(r => `<div>${r}</div>`).join('');
  feedback.className  = hasError ? 'feedback-err' : 'feedback-ok';
  feedback.style.display = 'block';

  if (!hasError) {
    // Vider le panier et reset les boutons/cartes
    snapshot.forEach(item => {
      const btnCard  = document.getElementById('btn-' + item.id);
      const cardEl   = document.getElementById('card-' + item.id);
      if (btnCard) { btnCard.classList.remove('added'); btnCard.textContent = '+ Ajouter au panier'; }
      if (cardEl)  { cardEl.classList.remove('in-cart'); }
    });
    cart = [];
    renderCart();
    showToast('⚡', `${snapshot.length} commande(s) lancée(s) !`);
    setTimeout(refreshOrders, 2000);
  }

  btn.disabled    = false;
  btn.innerHTML   = '⚡ Passer commande';
}

// ── Rafraîchissement du tableau des commandes ─────────────────────────

async function refreshOrders() {
  try {
    const res    = await fetch('/api/orders');
    const orders = await res.json();
    const wrap   = document.getElementById('ordersContainer');
    if (!wrap) return;

    if (!orders.length) return;

    wrap.innerHTML = `
      <table>
        <thead>
          <tr>
            <th>ID</th><th>Joueur</th><th>Rang</th><th>Article</th>
            <th>Qté</th><th>Montant</th><th>Statut</th>
          </tr>
        </thead>
        <tbody>
          ${orders.map(o => `
            <tr>
              <td style="font-family:monospace;font-size:.71rem;color:var(--text-3);">${o.orderId.substring(0, 8)}…</td>
              <td style="font-weight:600;">${o.playerName}</td>
              <td><span class="rank-tag rank-${o.playerRank}" style="font-size:.62rem;">${o.playerRank}</span></td>
              <td>${o.itemName}</td>
              <td style="text-align:center;color:var(--text-2);">${o.quantity}</td>
              <td style="color:var(--orange);font-weight:700;">${o.totalAmount}€</td>
              <td><span class="status-pill status-${o.status}">${o.status}</span></td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    `;
  } catch (e) { /* silencieux */ }
}

// ── Toast notification ────────────────────────────────────────────

let toastTimer = null;
function showToast(icon, msg) {
  const el   = document.getElementById('toast');
  const ico  = document.getElementById('toastIcon');
  const msgEl = document.getElementById('toastMsg');
  if (!el) return;
  ico.textContent  = icon;
  msgEl.textContent = msg;
  el.classList.add('show');
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), 2800);
}

// ── Navigation ──────────────────────────────────────────────────
function scrollToCart() {
  const el = document.getElementById('sidebarCart');
  if (el) el.scrollIntoView({ behavior: 'smooth' });
}

// ── Auto-refresh ──────────────────────────────────────────────────
setInterval(refreshOrders, 5000);
