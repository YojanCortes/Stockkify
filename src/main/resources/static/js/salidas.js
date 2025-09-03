// salidas.js
(() => {
    // --- CSRF (Spring Security) ---
    const CSRF_TOKEN =
        document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
    const CSRF_HEADER =
        document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') ||
        'X-CSRF-TOKEN';

    // --- Estado ---
    const pop = document.getElementById('qtyPop');            // modal
    const backdrop = document.getElementById('qtyBackdrop');  // overlay
    const btnClose = document.getElementById('qtyCloseBtn');  // botón X
    const btnCancel = document.getElementById('cancelQtyBtn');// botón Cancelar
    const qtyInputEl = document.getElementById('qtyInput');   // input cantidad

    let currentProduct = null;
    const cart = new Map(); // key: code, value: {name, qty}

    // --- Utilidades ---
    function qs(sel, scope = document) {
        return scope.querySelector(sel);
    }
    function qsa(sel, scope = document) {
        return Array.from(scope.querySelectorAll(sel));
    }

    // --- Modal helpers ---
    function openQtyModal(product) {
        currentProduct = product;
        if (qtyInputEl) qtyInputEl.value = 1;

        pop?.classList.add('show');
        backdrop?.classList.add('show');
        document.body.classList.add('modal-open');

        // foco tras el render
        setTimeout(() => {
            qtyInputEl?.focus();
            qtyInputEl?.select();
        }, 0);
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

    // --- Lista de productos: abrir modal de cantidad ---
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
        if (e.key === 'Escape' && pop?.classList.contains('show')) {
            closeQtyModal();
        }
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
            // Opcional: recargar para refrescar stocks
            // location.reload();
        } catch (err) {
            alert('Error: ' + (err.message || err));
        }
    });

    // --- Inicialización ---
    document.addEventListener('DOMContentLoaded', () => {
        // Enlaza botones de la lista inicial (servida por el backend)
        wireOpenQtyButtons(document);

        // Si agregas productos dinámicamente por AJAX, vuelve a llamar:
        // wireOpenQtyButtons(scopeDeProductosRecargados);

        // Render inicial de resumen/estado
        renderCart();
    });
})();
