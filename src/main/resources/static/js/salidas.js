// salidas.js
(() => {
    // --- CSRF (Spring Security) ---
    const CSRF_TOKEN =
        document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
    const CSRF_HEADER =
        document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') ||
        'X-CSRF-TOKEN';

    // --- Config opcional para API (desactivada por defecto) ---
    const USE_API = false;             // cámbialo a true si quieres validar contra backend
    const API_BASE = '/api/productos'; // ej: GET /api/productos/{codigo}

    // --- Estado / refs modal ---
    const pop = document.getElementById('qtyPop');            // modal
    const backdrop = document.getElementById('qtyBackdrop');  // overlay
    const btnClose = document.getElementById('qtyCloseBtn');  // botón X (si existe en tu HTML)
    const btnCancel = document.getElementById('cancelQtyBtn');// botón Cancelar (si existe)
    const qtyInputEl = document.getElementById('qtyInput');   // input cantidad

    let currentProduct = null;
    const cart = new Map(); // key: code, value: {name, qty}

    // --- Utilidades ---
    function qs(sel, scope = document) { return scope.querySelector(sel); }
    function qsa(sel, scope = document) { return Array.from(scope.querySelectorAll(sel)); }
    // Polyfill CSS.escape simple para selectores por data-code
    function cssEscape(str = '') {
        if (window.CSS && typeof window.CSS.escape === 'function') return window.CSS.escape(str);
        return String(str).replace(/["\\#.;?%^&[\]{}()<>\s]/g, '\\$&');
    }

    // --- Modal helpers ---
    function openQtyModal(product) {
        currentProduct = product;
        if (qtyInputEl) qtyInputEl.value = 1;

        pop?.classList.add('show');
        backdrop?.classList.add('show');
        document.body.classList.add('modal-open');

        setTimeout(() => { qtyInputEl?.focus(); qtyInputEl?.select(); }, 0);
    }
    function closeQtyModal() {
        pop?.classList.remove('show');
        backdrop?.classList.remove('show');
        document.body.classList.remove('modal-open');
        currentProduct = null;
    }

    // --- UI: contadores / vacío ---
    function updateSummary() {
        const kinds = cart.size;
        let units = 0;
        cart.forEach((v) => (units += v.qty));

        qs('#kinds').textContent = kinds;
        qs('#units').textContent = units;

        const hint = qs('#emptyHint');
        if (hint) hint.style.display = kinds === 0 ? 'block' : 'none';
    }

    // --- UI: render de carrito ---
    function renderCart() {
        const list = qs('#cartList');
        if (!list) return;

        list.innerHTML = '';

        if (cart.size === 0) {
            const hint = document.createElement('div');
            hint.className = 'subtext';
            hint.style.padding = '14px';
            hint.textContent = 'Aún no hay productos en la salida.';
            list.appendChild(hint);
            updateSummary();
            return;
        }

        cart.forEach((v, code) => {
            const row = document.createElement('div');
            row.className = 'cart-row';
            row.innerHTML = `
        <div class="qty-box">
          <div class="qty-btn" data-dec>-</div>
          <input class="qty-input" value="${v.qty}" />
          <div class="qty-btn" data-inc>+</div>
        </div>
        <div>
          <div style="font-weight:600">${v.name}</div>
          <div class="subtext">Código: ${code}</div>
        </div>
        <div class="trash" title="Quitar"><i class="fas fa-times"></i></div>
      `;

            // Handlers cantidad
            row.querySelector('[data-dec]').addEventListener('click', () => {
                if (v.qty > 1) {
                    v.qty--;
                    row.querySelector('.qty-input').value = v.qty;
                    updateSummary();
                }
            });
            row.querySelector('[data-inc]').addEventListener('click', () => {
                v.qty++;
                row.querySelector('.qty-input').value = v.qty;
                updateSummary();
            });
            row.querySelector('.qty-input').addEventListener('change', (e) => {
                const n = Math.max(1, parseInt(e.target.value || '1', 10));
                v.qty = n;
                e.target.value = n;
                updateSummary();
            });

            // Quitar producto
            row.querySelector('.trash').addEventListener('click', () => {
                cart.delete(code);
                renderCart();
            });

            list.appendChild(row);
        });

        updateSummary();
    }

    // --- Lista de productos: abrir modal de cantidad (desde el “+” de la lista) ---
    function wireOpenQtyButtons(scope) {
        qsa('[data-open-qty]', scope).forEach((btn) => {
            btn.addEventListener('click', (e) => {
                const row = e.currentTarget.closest('.product-row');
                if (!row) return;
                openQtyModal({
                    code: row.getAttribute('data-code'),
                    name: row.getAttribute('data-name')
                });
            });
        });
    }

    // --- Cerrar modal por botones y fondo ---
    btnClose?.addEventListener('click', closeQtyModal);
    btnCancel?.addEventListener('click', closeQtyModal);
    backdrop?.addEventListener('click', closeQtyModal);

    // --- Cerrar con tecla ESC ---
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && pop?.classList.contains('show')) closeQtyModal();
    });

    // --- Confirmar (agregar) y cerrar automáticamente ---
    qs('#addToCartBtn')?.addEventListener('click', () => {
        if (!currentProduct) return;
        const qty = Math.max(1, parseInt(qtyInputEl?.value || '1', 10));
        if (cart.has(currentProduct.code)) {
            cart.get(currentProduct.code).qty += qty;
        } else {
            cart.set(currentProduct.code, { name: currentProduct.name, qty });
        }
        renderCart();
        closeQtyModal(); // cierre automático tras confirmar
    });

    // --- Confirmar con Enter dentro del input ---
    qtyInputEl?.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            qs('#addToCartBtn')?.click();
        }
    });

    // =========================
    //  Formulario "agregar por código"
    // =========================
    const addByCodeForm = document.getElementById('addByCodeForm');
    const codeInput = document.getElementById('codeInput');
    const formMsg = document.getElementById('formMsg');
    let msgTimer = null;

    function showFormMsg(text, kind = 'error') {
        if (!formMsg) return;
        formMsg.textContent = text;
        formMsg.classList.remove('ok', 'error');
        formMsg.classList.add(kind);
        formMsg.style.opacity = '1';
        if (msgTimer) clearTimeout(msgTimer);
        msgTimer = setTimeout(() => {
            formMsg.style.opacity = '0';
            setTimeout(() => { formMsg.textContent = ''; }, 200);
        }, 2000);
    }

    // Busca en el DOM por data-code y, opcionalmente, valida por API si USE_API=true
    async function addProductByCode(codeRaw) {
        const code = String(codeRaw || '').trim();
        if (!code) return;

        // 1) Lookup en DOM (lista actual)
        const row = document.querySelector(`.product-row[data-code="${cssEscape(code)}"]`);
        if (row) {
            const name = row.getAttribute('data-name') || 'Producto';
            if (cart.has(code)) {
                cart.get(code).qty += 1;
            } else {
                cart.set(code, { name, qty: 1 });
            }
            renderCart();
            showFormMsg('Producto agregado', 'ok');
            return;
        }

        // 2) (Opcional) Validar contra API si está activo
        if (USE_API) {
            try {
                const res = await fetch(`${API_BASE}/${encodeURIComponent(code)}`, {
                    method: 'GET',
                    headers: { 'Accept': 'application/json' }
                });
                if (res.ok) {
                    const p = await res.json(); // { codigoBarras, nombre, ... }
                    const codeStr = String(p.codigoBarras ?? code);
                    const nameStr = String(p.nombre ?? 'Producto');
                    if (cart.has(codeStr)) {
                        cart.get(codeStr).qty += 1;
                    } else {
                        cart.set(codeStr, { name: nameStr, qty: 1 });
                    }
                    renderCart();
                    showFormMsg('Producto agregado', 'ok');
                    return;
                }
            } catch (e) {
                // silencioso; cae al mensaje de no existe
            }
        }

        // 3) No existe (ni DOM, ni API)
        showFormMsg('No existe el producto', 'error');
    }

    // Submit del formulario: evita navegación, agrega y limpia siempre
    addByCodeForm?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const code = codeInput?.value.trim();
        if (codeInput) codeInput.value = ''; // limpiar SIEMPRE
        await addProductByCode(code);
        codeInput?.focus();
    });

    // --- Vaciar carrito ---
    qs('#btnVaciar')?.addEventListener('click', () => {
        cart.clear();
        renderCart();
    });

    // --- Registrar salida (POST batch) ---
    qs('#btnRegistrar')?.addEventListener('click', async () => {
        const motivo = qs('#motivo')?.value.trim() || '';
        const referencia = qs('#referencia')?.value.trim() || '';
        const usuario = qs('#usuario')?.value.trim() || '';

        if (cart.size === 0) {
            alert('No hay productos en la salida.');
            return;
        }

        const items = Array.from(cart.entries()).map(([codigo, v]) => ({
            codigo,
            cantidad: v.qty
        }));

        try {
            const res = await fetch('/salidas/registrar-lote', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    [CSRF_HEADER]: CSRF_TOKEN
                },
                body: JSON.stringify({ items, motivo, referencia, usuario })
            });

            if (!res.ok) {
                const txt = await res.text();
                throw new Error(txt || 'Error al registrar salida.');
            }

            const data = await res.json(); // { ok: true, registrados: n, ... }
            alert(`Salida registrada.\nLíneas procesadas: ${data.registrados}`);
            cart.clear();
            renderCart();
            // location.reload(); // si quieres refrescar stocks visualmente
        } catch (err) {
            alert('Error: ' + (err.message || err));
        }
    });

    // --- Inicialización ---
    document.addEventListener('DOMContentLoaded', () => {
        wireOpenQtyButtons(document); // botones de la lista
        renderCart();                 // estado inicial
    });
})();
