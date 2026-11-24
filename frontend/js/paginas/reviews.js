// Tela Reviews: feed com filtros + Mais comentados + Suas reviews
window.Paginas = window.Paginas || {};

Paginas.reviews = {
  titulo: 'Reviews',
  async render(raiz, rota) {
    const filtro = rota.query.filtro || 'todas';
    const soConversas = filtro === 'conversas';
    const [lista, conversas, top, meu] = await Promise.all([
      soConversas ? Promise.resolve([]) : API.reviews(filtro),
      ['todas', 'conversas', 'seguindo'].includes(filtro) ? API.conversas(filtro === 'seguindo' ? 'seguindo' : 'todas').catch(() => []) : Promise.resolve([]),
      API.maisComentados().then((l) => l.slice(0, 4)).catch(() => []),
      API.meuResumo().catch(() => ({ total: 0, media: 0, curtidas: 0 })),
    ]);
    const filtros = { todas: 'Todas', conversas: 'Conversas', seguindo: 'Amigos', recentes: 'Recentes', nota_alta: 'Nota alta', nota_baixa: 'Nota baixa' };
    const u = Sessao.usuario || {};
    // "Todas" e "Amigos": conversas e reviews juntas, mais novas primeiro (igual timeline)
    const data = (x) => quandoMs(x.criado_em);
    const itens = soConversas ? conversas
      : conversas.length ? [...conversas, ...lista].sort((x, y) => data(y) - data(x)) : lista;

    raiz.innerHTML = `
      <div class="colunas">
        <div>
          <div class="chips" style="margin-bottom:16px">
            ${Object.entries(filtros).map(([k, v]) => `<a class="chip roxo ${k === filtro ? 'ativo' : ''}" style="display:inline-flex;align-items:center" href="#/reviews?filtro=${k}">${v}</a>`).join('')}
          </div>
          ${compositorHtml(u)}
          <div id="feed">${itens.map((x) => (x.tipo === 'conversa' ? conversaCard(x) : reviewCard(x))).join('')
            || vazio(soConversas ? 'Ninguém puxou conversa ainda. Que tal pedir uma indicação?' : 'Nenhuma review ainda. Que tal escrever a primeira?')}</div>
        </div>
        <aside class="lateral">
          <section class="card">
            <h2 style="font-size:22px;margin-bottom:14px">Mais comentados</h2>
            ${top.map((f, i) => `
              <a href="#/filme/${f.id}" style="display:flex;align-items:center;gap:12px;margin-top:12px">
                <b style="width:14px">${i + 1}</b>
                <div style="width:42px;aspect-ratio:2/3;border-radius:6px;overflow:hidden">${poster(f)}</div>
                <div><b style="font-size:14px">${esc(f.titulo)}</b><div class="meta">${f.total_reviews} reviews</div></div>
              </a>`).join('') || vazio('Ainda sem dados.')}
          </section>
          <section class="card">
            <h2 style="font-size:22px;margin-bottom:14px">Suas reviews</h2>
            <div class="stats" style="margin:0;justify-content:space-around">
              <div><b>${meu.total}</b><small>reviews</small></div>
              <div><b>${meu.media || '-'}</b><small>nota média</small></div>
              <div><b>${meu.curtidas}</b><small>curtidas</small></div>
            </div>
          </section>
        </aside>
      </div>`;

    ligarReviews($('#feed'));
    ligarConversas($('#feed'));
    ligarCompositor(raiz, (nova) => {
      if (!['todas', 'conversas', 'seguindo'].includes(filtro)) { location.hash = '#/reviews?filtro=conversas'; return; }
      const feed = $('#feed', raiz);
      if (feed.querySelector('.vazio-cinema')) feed.innerHTML = '';
      feed.insertAdjacentHTML('afterbegin', conversaCard(nova, true));
      ligarConversas(feed);
    });
    // Veio de uma notificação ("@x respondeu sua conversa"): abre essa conversa
    if (rota.query.c) {
      const alvo = $(`[data-conversa="${rota.query.c}"]`, raiz);
      if (alvo) { alvo.scrollIntoView({ block: 'center' }); alvo.querySelector('[data-abrir]')?.click(); }
    }
  },
};

// =====================================================================
// CONVERSAS (estilo Twitter): "Me indiquem um filme pra hoje!" e a galera
// responde. Dá pra marcar um filme no post e na resposta (vira cartãozinho).
// =====================================================================
/** Data do servidor (número em ms ou texto "2026-11-24 01:50:00") -> milissegundos */
function quandoMs(q) {
  if (typeof q === 'number') return q;
  const t = Date.parse(String(q || '').replace(' ', 'T'));
  return Number.isNaN(t) ? 0 : t;
}

const filmeMarcadoHtml = (f) => (f ? `
  <a class="conversa-filme" href="#/filme/${f.id}">
    ${poster(f, 'conversa-filme-capa')}
    <span><b>${esc(f.titulo)}</b><small>${[f.tipo === 'serie' ? 'Série' : 'Filme', f.ano].filter(Boolean).join(' · ')}</small></span>
  </a>` : '');

function compositorHtml(u) {
  return `
    <form class="compositor" id="compositor">
      ${avatar(u, 'sm')}
      <div class="compositor-corpo">
        <textarea id="comp-texto" rows="2" maxlength="500" placeholder="Puxe uma conversa... ex: me indiquem um filme" aria-label="Escrever conversa"></textarea>
        <div class="compositor-filme" id="comp-filme" hidden></div>
        <div class="compositor-acoes">
          <button type="button" class="btn btn-mini" id="comp-marcar">${icone('filmes')} Marcar filme</button>
          <span class="compositor-conta" id="comp-conta">0/500</span>
          <button class="btn btn-primario btn-mini" id="comp-postar" disabled>${icone('enviar')} Postar</button>
        </div>
      </div>
    </form>`;
}

/**
 * Seletor de filme com a mini seleção (pôster + nome) igual à busca do topo.
 * onEscolher(filme). Devolve uma função pra limpar.
 */
function seletorDeFilme(caixa, onEscolher, placeholder = 'Qual filme ou série?') {
  caixa.hidden = false;
  caixa.innerHTML = `<div class="seletor-filme"><input class="campo" placeholder="${esc(placeholder)}" aria-label="${esc(placeholder)}" autocomplete="off"><div class="seletor-lista"></div></div>`;
  const input = caixa.querySelector('input'), lista = caixa.querySelector('.seletor-lista');
  let espera = null;
  input.focus();
  input.oninput = () => {
    clearTimeout(espera);
    espera = setTimeout(async () => {
      const q = input.value.trim();
      if (q.length < 2) { lista.innerHTML = ''; return; }
      try {
        const r = await API.sugestoes(q);
        if (input.value.trim() !== q) return;
        lista.innerHTML = r.map((f, i) => `<button type="button" class="sugestao" data-i="${i}" style="animation-delay:${i * 35}ms">${poster(f, 'sugestao-capa')}<span class="sugestao-txt"><b>${esc(f.titulo)}</b><small>${[f.tipo === 'serie' ? 'Série' : 'Filme', f.ano].filter(Boolean).join(' · ')}</small></span></button>`).join('')
          || '<p class="sugestao-nada">Nada encontrado.</p>';
        $$('[data-i]', lista).forEach((b) => (b.onclick = () => {
          const f = r[Number(b.dataset.i)];
          caixa.innerHTML = `<span class="filme-escolhido">${filmeMarcadoHtml(f)}<button type="button" class="icon-btn" aria-label="Tirar filme">${icone('x')}</button></span>`;
          caixa.querySelector('.icon-btn').onclick = () => { caixa.innerHTML = ''; caixa.hidden = true; onEscolher(null); };
          caixa.querySelector('.conversa-filme').onclick = (e) => e.preventDefault();
          onEscolher(f);
        }));
      } catch { lista.innerHTML = ''; }
    }, 220);
  };
  input.onkeydown = (e) => { if (e.key === 'Escape') { caixa.innerHTML = ''; caixa.hidden = true; onEscolher(null); } };
}

function ligarCompositor(raiz, aoPostar) {
  const texto = $('#comp-texto', raiz), postar = $('#comp-postar', raiz), conta = $('#comp-conta', raiz);
  let filme = null;
  const atualizar = () => {
    conta.textContent = `${texto.value.length}/500`;
    postar.disabled = !texto.value.trim();
    texto.style.height = 'auto';
    texto.style.height = `${Math.min(texto.scrollHeight, 220)}px`;   // cresce enquanto escreve
    texto.style.overflowY = texto.scrollHeight > 220 ? 'auto' : 'hidden';
  };
  texto.oninput = atualizar;
  texto.onfocus = () => $('#compositor', raiz).classList.add('aberto');
  // Um botão só: marca o filme no post. Depois de marcar, aparece "Dar nota" (vira review com estrelas).
  $('#comp-marcar', raiz).onclick = () => seletorDeFilme($('#comp-filme', raiz), (f) => {
    filme = f;
    if (f) $('.filme-escolhido', raiz)?.insertAdjacentHTML('beforeend', `<a class="btn btn-mini" href="#/filme/${f.id}/review">${icone('estrela')} Dar nota (review)</a>`);
  });
  $('#compositor', raiz).onsubmit = async (e) => {
    e.preventDefault();
    if (!texto.value.trim()) return;
    postar.disabled = true;
    try {
      const nova = await API.postarConversa(texto.value.trim(), filme?.id || null);
      texto.value = ''; filme = null;
      $('#comp-filme', raiz).innerHTML = ''; $('#comp-filme', raiz).hidden = true;
      atualizar();
      toast('Conversa postada!');
      aoPostar(nova);
    } catch (err) { toast(err.message); postar.disabled = false; }
  };
}

function conversaCard(c, nova = false) {
  return `
    <article class="conversa ${nova ? 'nova' : ''}" data-conversa="${c.id}">
      <a href="#/perfil/${encodeURIComponent(c.autor.username)}" aria-label="Perfil de ${esc(c.autor.nome)}">${avatar(c.autor)}</a>
      <div class="conversa-corpo">
        <div class="conversa-topo"><b>${esc(c.autor.nome)}</b> <span class="texto-suave">@${esc(c.autor.username)} · ${tempoAtras(c.criado_em)}</span>
          <span class="conversa-selo">Conversa</span></div>
        <p class="conversa-texto">${esc(c.texto)}</p>
        ${filmeMarcadoHtml(c.filme)}
        <div class="conversa-acoes">
          <button class="acao" data-abrir aria-expanded="false">${icone('reviews')} <span>${c.respostas}</span> ${c.respostas === 1 ? 'resposta' : 'respostas'}</button>
          <button class="acao ${c.curti ? 'curti' : ''}" data-curtir-conversa aria-pressed="${c.curti}" aria-label="Curtir">${icone('joinha')} <span>${c.curtidas}</span></button>
          ${c.minha ? `<button class="acao" data-apagar-conversa>${icone('x')} Apagar</button>` : ''}
        </div>
        <div class="conversa-fio" hidden></div>
      </div>
    </article>`;
}

function respostaHtml(r) {
  return `
    <div class="resposta">
      ${avatar(r.autor, 'sm')}
      <div><div class="conversa-topo"><b>${esc(r.autor.nome)}</b> <span class="texto-suave">@${esc(r.autor.username)} · ${tempoAtras(r.criado_em)}</span></div>
        <p class="conversa-texto">${esc(r.texto)}</p>${filmeMarcadoHtml(r.filme)}</div>
    </div>`;
}

function ligarConversas(onde) {
  $$('[data-conversa]', onde).forEach((card) => {
    if (card.dataset.ligado) return;
    card.dataset.ligado = '1';
    const id = card.dataset.conversa, fio = $('.conversa-fio', card), abrir = $('[data-abrir]', card);
    abrir.onclick = () => alternarFio(fio, abrir, {
      listar: () => API.respostas(id),
      responder: (t, f) => API.responder(id, t, f),
      contador: $('[data-abrir] span', card),
      vazioTxt: 'Ninguém respondeu ainda. Seja o primeiro a indicar!',
    });
    $('[data-curtir-conversa]', card).onclick = async (e) => {
      const b = e.currentTarget;
      try {
        const r = await API.curtirConversa(id);
        b.classList.toggle('curti', r.curti);
        b.setAttribute('aria-pressed', String(r.curti));
        $('span', b).textContent = r.curtidas;
      } catch (err) { toast(err.message); }
    };
    const apagar = $('[data-apagar-conversa]', card);
    if (apagar) apagar.onclick = async () => {
      if (!(await confirmar({ titulo: 'Apagar conversa?', texto: 'As respostas também somem.', botao: 'Apagar' }))) return;
      try { await API.apagarConversa(id); card.remove(); toast('Conversa apagada'); } catch (err) { toast(err.message); }
    };
  });
}

/**
 * Abre/fecha o fio de respostas (usado nas Conversas e nas Reviews).
 * cfg = { listar(), responder(texto, tmdbId), contador (span do número), vazioTxt }
 */
async function alternarFio(fio, botao, cfg) {
  if (!fio.hidden) { fio.hidden = true; botao.setAttribute('aria-expanded', 'false'); return; }
  fio.hidden = false;
  botao.setAttribute('aria-expanded', 'true');
  fio.innerHTML = carregando();
  try {
    const lista = await cfg.listar();
    fio.innerHTML = `
      <div class="respostas">${lista.map(respostaHtml).join('') || `<p class="texto-suave">${esc(cfg.vazioTxt)}</p>`}</div>
      <form class="responder">
        ${avatar(Sessao.usuario || {}, 'sm')}
        <div class="compositor-corpo">
          <input class="campo" maxlength="500" placeholder="Responda ou indique um filme..." aria-label="Responder">
          <div class="compositor-filme" hidden></div>
          <div class="compositor-acoes">
            <button type="button" class="btn btn-mini" data-indicar>${icone('filmes')} Indicar filme</button>
            <span style="flex:1"></span>
            <button class="btn btn-primario btn-mini">${icone('enviar')} Responder</button>
          </div>
        </div>
      </form>`;
    let filme = null;
    const form = $('.responder', fio), caixa = $('.compositor-filme', fio), input = $('input.campo', form);
    $('[data-indicar]', fio).onclick = () => seletorDeFilme(caixa, (f) => { filme = f; });
    input.focus();
    form.onsubmit = async (e) => {
      e.preventDefault();
      const t = input.value.trim() || (filme ? `Indico: ${filme.titulo}` : '');
      if (!t) return toast('Escreva algo ou indique um filme.');
      try {
        const r = await cfg.responder(t, filme?.id || null);
        $('.respostas > p', fio)?.remove();
        $('.respostas', fio).insertAdjacentHTML('beforeend', respostaHtml({ autor: Sessao.usuario || {}, texto: t, criado_em: new Date().toISOString(), filme }));
        input.value = ''; filme = null; caixa.innerHTML = ''; caixa.hidden = true;
        if (cfg.contador) cfg.contador.textContent = r.respostas;
      } catch (err) { toast(err.message); }
    };
  } catch (err) { fio.innerHTML = erroBox(err); }
}
