// Funções de interface reaproveitadas por todas as páginas
const $ = (sel, raiz = document) => raiz.querySelector(sel);
const $$ = (sel, raiz = document) => [...raiz.querySelectorAll(sel)];

// SEMPRE use esc() ao colocar texto vindo do banco/API no HTML (evita XSS)
function esc(t) {
  return String(t ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function toast(msg) {
  const t = $('#toast');
  t.textContent = msg;
  t.classList.add('on');
  clearTimeout(toast._t);
  toast._t = setTimeout(() => t.classList.remove('on'), 2600);
}

const inicial = (nome) => esc((nome || '?').trim()[0].toUpperCase());
const corAvatar = (nome = '') => ['#8585b8', '#b06ff2', '#4fb3e8', '#f27474', '#2ecc71', '#e6b82e'][nome.length % 6];
const avatar = (u, cls = '') =>
  `<div class="avatar ${cls}" style="background:${esc(u?.avatar_cor || corAvatar(u?.nome))}">${u?.avatar_url ? fotoAvatar(u.avatar_url, u?.nome) : inicial(u?.nome)}</div>`;
// referrerpolicy: a foto do Google não abre se o navegador contar de qual site veio.
// Se a imagem não carregar, volta pra letra inicial.
function fotoAvatar(url, nome) {
  return `<img src="${esc(url)}" alt="" referrerpolicy="no-referrer" data-inicial="${inicial(nome)}" onerror="this.replaceWith(document.createTextNode(this.dataset.inicial))">`;
}

const duracao = (min) => (min ? `${Math.floor(min / 60)}h ${String(min % 60).padStart(2, '0')}min` : '');
const dataBR = (iso) => (iso ? new Date(iso.length === 10 ? iso + 'T12:00' : iso).toLocaleDateString('pt-BR', { day: 'numeric', month: 'short', year: 'numeric' }) : '');
function tempoAtras(iso) {
  const s = (Date.now() - new Date(iso.replace(' ', 'T')).getTime()) / 1000;
  if (s < 3600) return `há ${Math.max(1, Math.floor(s / 60))} min`;
  if (s < 86400) return `há ${Math.floor(s / 3600)} h`;
  const d = Math.floor(s / 86400);
  if (d < 7) return `há ${d} dia${d > 1 ? 's' : ''}`;
  const w = Math.floor(d / 7);
  return w < 5 ? `há ${w} semana${w > 1 ? 's' : ''}` : dataBR(iso);
}

function poster(f, cls = '') {
  return f.poster
    ? `<img src="${esc(f.poster)}" alt="Pôster de ${esc(f.titulo)}" loading="lazy" class="${cls}">`
    : `<div class="sem-poster">${esc(f.titulo)}</div>`;
}

// Minhas notas (tmdb_id -> nota), pra mostrar "5.0 ★" em QUALQUER pôster do site
window.MINHAS_NOTAS = null;
async function carregarMinhasNotas() {
  if (!Sessao.token) { window.MINHAS_NOTAS = {}; return; }
  try { window.MINHAS_NOTAS = await API.minhasNotas(); } catch { window.MINHAS_NOTAS = window.MINHAS_NOTAS || {}; }
}
const seloMinhaNota = (nota) => (nota != null
  ? `<span class="selo-nota" aria-label="Sua nota: ${Number(nota).toFixed(1)} de 5">${Number(nota).toFixed(1)} <i>★</i></span>` : '');

// Etiqueta no cantinho do pôster: FILME ou SÉRIE
// Documentário tem etiqueta própria (filme ou série)
function seloTipo(f) {
  if (!f || f.id == null) return '';
  if (f.documentario) return `<span class="selo-serie doc">${Number(f.id) < 0 ? 'Doc · Série' : 'Documentário'}</span>`;
  return `<span class="selo-serie ${Number(f.id) < 0 ? '' : 'filme'}">${Number(f.id) < 0 ? 'Série' : 'Filme'}</span>`;
}
// "T1 • E3 · Nome do episódio" (fila / Modo Cinema)
const textoEpisodio = (f) => (f && f.episodio ? `T${f.temporada} • E${f.episodio}${f.episodio_titulo ? ` · ${f.episodio_titulo}` : ''}` : '');

function filmeCard(f) {
  const minha = f.minha_nota ?? window.MINHAS_NOTAS?.[f.id];
  return `
    <a class="filme-card" href="#/filme/${f.id}">
      <div class="poster">${poster(f)}${seloMinhaNota(minha)}${seloTipo(f)}</div>
      <h3>${esc(f.titulo)}</h3>
      <div class="meta">${f.ano || ''} ${f.nota ? `<span class="estrela">★</span> ${f.nota}` : ''}</div>
    </a>`;
}

function estrelasTexto(n) {
  return `<span class="estrela">★</span> ${Number(n).toFixed(1)}`;
}

const carregando = () => '<div class="carregando" aria-label="Carregando"></div>';
const vazio = (txt) => `<div class="vazio">${esc(txt)}</div>`;
const erroBox = (e) => `<div class="vazio">😕 ${esc(e.message)}</div>`;

// Card de review (usado em Reviews, Detalhes e Avaliações)
function reviewCard(r, { mostrarFilme = true } = {}) {
  return `
    <article class="review" data-review="${r.id}">
      ${mostrarFilme ? `<a href="#/filme/${r.filme.id}" class="mini-poster">${poster(r.filme)}</a>` : `<a href="#/perfil/${encodeURIComponent(r.autor.username)}" aria-label="Perfil de ${esc(r.autor.nome)}">${avatar(r.autor)}</a>`}
      <div class="corpo">
        <div class="cab">
          ${mostrarFilme ? `<a href="#/filme/${r.filme.id}">${esc(r.filme.titulo)}</a>` : `<a href="#/perfil/${encodeURIComponent(r.autor.username)}">${esc(r.autor.nome)}</a>`}
          <span>${estrelasTexto(r.nota)}</span>
          <span class="meta">${mostrarFilme ? `<a class="link-perfil" href="#/perfil/${encodeURIComponent(r.autor.username)}">@${esc(r.autor.username)}</a> • ` : ''}${tempoAtras(r.criado_em)}</span>
        </div>
        ${r.texto ? `<p class="${r.tem_spoiler ? 'spoiler' : ''}" title="${r.tem_spoiler ? 'Contém spoiler — toque para ver' : ''}">${esc(r.texto)}</p>` : ''}
        <button class="curtir ${r.curti ? 'ativo' : ''}" data-curtir="${r.id}" aria-label="Curtir">${icone('joinha')} <span>${r.curtidas}</span></button>
      </div>
    </article>`;
}

// Liga os botões de curtir/spoiler dentro de um container
function ligarReviews(raiz) {
  raiz.addEventListener('click', async (e) => {
    const sp = e.target.closest('.spoiler');
    if (sp) { sp.classList.remove('spoiler'); return; }
    const b = e.target.closest('[data-curtir]');
    if (!b) return;
    try {
      const r = await API.curtir(b.dataset.curtir);
      b.classList.toggle('ativo', r.curti);
      b.querySelector('span').textContent = r.curtidas;
    } catch (err) { toast(err.message); }
  });
}

// Pop-up "Adicionar à lista"
async function abrirAdicionarLista(tmdbId) {
  const dlg = document.createElement('dialog');
  dlg.innerHTML = `<h3>Adicionar à lista</h3><div class="lista-opcoes">${carregando()}</div>
    <div class="acoes"><button class="btn" data-fechar>Fechar</button></div>`;
  document.body.appendChild(dlg);
  dlg.showModal();
  dlg.addEventListener('close', () => dlg.remove());
  dlg.querySelector('[data-fechar]').onclick = () => dlg.close();
  try {
    const listas = (await API.listas('minhas'));
    dlg.querySelector('.lista-opcoes').innerHTML = listas.map((l) =>
      `<button class="item" data-lista="${l.id}"><span class="ico">${icone(l.icone)}</span><span class="txt">${esc(l.nome)}<small>${l.total} filmes</small></span></button>`
    ).join('') || vazio('Você ainda não tem listas.');
    dlg.querySelectorAll('[data-lista]').forEach((b) => (b.onclick = async () => {
      try { await API.addNaLista(b.dataset.lista, tmdbId); toast('Adicionado à lista!'); dlg.close(); }
      catch (err) { toast(err.message); }
    }));
  } catch (err) { dlg.querySelector('.lista-opcoes').innerHTML = erroBox(err); }
}

// Pede um texto num pop-up (no lugar do prompt(), que algumas WebViews bloqueiam)
function perguntar(titulo, placeholder = '') {
  return new Promise((ok) => {
    const dlg = document.createElement('dialog');
    dlg.innerHTML = `<form method="dialog"><h3>${esc(titulo)}</h3>
      <input class="campo" name="v" placeholder="${esc(placeholder)}" required maxlength="60">
      <div class="acoes"><button type="button" class="btn" data-cancelar>Cancelar</button><button class="btn btn-primario" value="ok">Salvar</button></div></form>`;
    document.body.appendChild(dlg);
    dlg.showModal();
    // "Cancelar" fecha direto (antes ele tentava enviar o formulário e o campo obrigatório travava)
    dlg.querySelector('[data-cancelar]').onclick = () => { dlg.returnValue = ''; dlg.close(); };
    dlg.addEventListener('close', () => {
      const v = dlg.returnValue === 'ok' ? dlg.querySelector('input').value.trim() : null;
      dlg.remove(); ok(v || null);
    });
  });
}

// Pergunta "tem certeza?" no visual do site (no lugar do confirm() do navegador).
// Uso: if (!(await confirmar({ titulo: 'Apagar lista?', texto: '...', botao: 'Apagar' }))) return;
function confirmar({ titulo = 'Tem certeza?', texto = '', botao = 'Confirmar', perigo = true } = {}) {
  return new Promise((ok) => {
    const dlg = document.createElement('dialog');
    dlg.className = 'confirmar';
    dlg.innerHTML = `<h3>${esc(titulo)}</h3>${texto ? `<p class="sinopse">${esc(texto)}</p>` : ''}
      <div class="acoes"><button type="button" class="btn" data-nao>Cancelar</button>
      <button type="button" class="btn btn-primario" data-sim>${esc(botao)}</button></div>`;
    document.body.appendChild(dlg);
    let resposta = false;
    dlg.querySelector('[data-nao]').onclick = () => dlg.close();
    dlg.querySelector('[data-sim]').onclick = () => { resposta = true; dlg.close(); };
    dlg.addEventListener('close', () => { dlg.remove(); ok(resposta); });
    dlg.showModal();
    dlg.querySelector('[data-nao]').focus();   // foco no Cancelar: Enter sem querer não apaga nada
  });
}

// Bolinhas com as cores do pôster (Modo Cinema). atual = cor que está na lâmpada agora
function paletaHtml(cores, atual) {
  if (!cores || !cores.length) return '';
  return `<span class="paleta" title="Cores do pôster usadas pela lâmpada">${cores.map((c) =>
    `<i style="background:${esc(c)}" class="${c === atual ? 'atual' : ''}"></i>`).join('')}</span>`;
}

// =====================================================================
// STATUS DO FILME (igual app de jogos: Desejados / Jogando / Zerados / Arquivados)
// =====================================================================
const STATUS_FILME = {
  quero_assistir: { nome: 'Quero assistir', sub: 'Lista de desejos', ico: 'marcador' },
  assistindo: { nome: 'Assistindo', sub: 'Vendo agora', ico: 'ampulheta' },
  assistido: { nome: 'Assistido', sub: 'Filmes concluídos', ico: 'bandeira' },
  abandonado: { nome: 'Abandonado', sub: 'Parei no meio', ico: 'arquivar' },
};

// Card de filme com a nota do usuário no cantinho (prateleiras do perfil)
function filmeCardComNota(f) {
  const nota = f.minha_nota != null ? Number(f.minha_nota) : null;
  return `
    <a class="filme-card" href="#/filme/${f.id}">
      <div class="poster">${poster(f)}${seloTipo(f)}
        ${nota != null ? `<span class="selo-nota" aria-label="Sua nota: ${Number(nota).toFixed(1)} de 5">${Number(nota).toFixed(1)} <i>★</i></span>` : ''}
      </div>
      <h3>${esc(f.titulo)}</h3>
      <div class="meta">${f.ano || ''}</div>
    </a>`;
}

// Carrossel com barra de rolagem visível + setas (no PC)
function carrosselHtml(itensHtml, vazioTxt = 'Nada por aqui ainda.') {
  if (!itensHtml) return vazio(vazioTxt);
  return `
    <div class="carrossel-box">
      <button class="seta-car esq" aria-label="Voltar">${icone('voltar')}</button>
      <div class="carrossel">${itensHtml}</div>
      <button class="seta-car dir" aria-label="Avançar">${icone('seta')}</button>
    </div>`;
}
function ligarCarrosseis(raiz = document) {
  $$('.carrossel-box', raiz).forEach((box) => {
    const trilho = $('.carrossel', box);
    const passo = () => Math.max(200, trilho.clientWidth * 0.8);
    const atualizar = () => {
      $('.esq', box).disabled = trilho.scrollLeft <= 4;
      $('.dir', box).disabled = trilho.scrollLeft + trilho.clientWidth >= trilho.scrollWidth - 4;
    };
    $('.esq', box).onclick = () => trilho.scrollBy({ left: -passo(), behavior: 'smooth' });
    $('.dir', box).onclick = () => trilho.scrollBy({ left: passo(), behavior: 'smooth' });
    trilho.addEventListener('scroll', atualizar, { passive: true });
    setTimeout(atualizar, 50);
  });
}

// =====================================================================
// BARRA DE ESTRELAS (aceita meia estrela: clique na metade esquerda = ,5)
// =====================================================================
const ROTULOS_NOTA = { 0.5: 'Péssimo', 1: 'Ruim', 1.5: 'Fraco', 2: 'Regular', 2.5: 'Ok', 3: 'Bom', 3.5: 'Muito bom', 4: 'Muito bom', 4.5: 'Excelente', 5: 'Obra-prima' };

function barraEstrelas(alvo, { valor = 0, onChange, tamanho = 36 } = {}) {
  let nota = valor;
  alvo.innerHTML = `
    <div class="barra-estrelas" role="slider" tabindex="0" aria-label="Nota" aria-valuemin="0" aria-valuemax="5" style="--tam:${tamanho}px">
      ${[1, 2, 3, 4, 5].map((n) => `<span class="est" data-n="${n}"><i class="cheia"></i></span>`).join('')}
    </div>
    <span class="meta rotulo-nota"></span>`;
  const barra = $('.barra-estrelas', alvo);
  const pintar = (v) => {
    $$('.est', barra).forEach((e) => {
      const n = Number(e.dataset.n);
      e.querySelector('.cheia').style.width = (v >= n ? 100 : v >= n - 0.5 ? 50 : 0) + '%';
    });
    barra.setAttribute('aria-valuenow', v);
    $('.rotulo-nota', alvo).textContent = v ? `${v.toFixed(1)} • ${ROTULOS_NOTA[v]}` : 'Toque nas estrelas';
  };
  const valorDoPonto = (e) => {
    const est = e.target.closest('.est');
    if (!est) return null;
    const r = est.getBoundingClientRect();
    return Number(est.dataset.n) - ((e.clientX - r.left) < r.width / 2 ? 0.5 : 0);
  };
  barra.addEventListener('mousemove', (e) => { const v = valorDoPonto(e); if (v) pintar(v); });
  barra.addEventListener('mouseleave', () => pintar(nota));
  barra.addEventListener('click', (e) => {
    const v = valorDoPonto(e);
    if (!v) return;
    nota = v === nota ? 0 : v;   // clicar de novo na mesma nota limpa
    pintar(nota);
    onChange && onChange(nota);
  });
  barra.addEventListener('keydown', (e) => {
    if (!['ArrowRight', 'ArrowLeft', 'ArrowUp', 'ArrowDown'].includes(e.key)) return;
    e.preventDefault();
    nota = Math.min(5, Math.max(0, nota + (e.key === 'ArrowRight' || e.key === 'ArrowUp' ? 0.5 : -0.5)));
    pintar(nota);
    onChange && onChange(nota);
  });
  pintar(nota);
  return { get valor() { return nota; } };
}

// =====================================================================
// JANELA "ADICIONAR À" (status do filme + coleções) — estilo bottom sheet
// =====================================================================
function abrirStatus(f, statusAtual, aoMudar) {
  let escolhido = statusAtual || null;
  const dlg = document.createElement('dialog');
  dlg.className = 'sheet';
  dlg.innerHTML = `
    <div class="sheet-alca"></div>
    <h3 style="text-align:center">Adicionar à</h3>
    <div class="sheet-opcoes" role="radiogroup">
      ${Object.entries(STATUS_FILME).map(([k, s]) => `
        <button class="item opcao-status ${k === escolhido ? 'escolhido' : ''}" data-status="${k}" role="radio" aria-checked="${k === escolhido}"
          ${naoEstreou(f) && k !== 'quero_assistir' ? `disabled title="Estreia em ${dataBR(f.lancamento)}"` : ''}>
          <span class="ico">${icone(s.ico)}</span>
          <span class="txt">${s.nome}<small>${s.sub}</small></span>
          <span class="marca">${icone('check')}</span>
        </button>`).join('')}
    </div>
    <button class="item colecao" style="margin-top:16px">
      <span class="ico">${icone('listas')}</span><span class="txt">Coleção<small>Adicionar a uma das suas listas</small></span>${icone('seta', 'width="18"')}
    </button>
    <div class="acoes" style="margin-top:16px">
      ${statusAtual ? '<button class="btn" data-remover>Remover</button>' : ''}
      <button class="btn btn-primario" data-continuar style="flex:1">Continuar</button>
    </div>`;
  document.body.appendChild(dlg);
  dlg.showModal();
  dlg.addEventListener('close', () => dlg.remove());

  $$('.opcao-status', dlg).forEach((b) => (b.onclick = () => {
    escolhido = b.dataset.status;
    $$('.opcao-status', dlg).forEach((x) => { x.classList.toggle('escolhido', x === b); x.setAttribute('aria-checked', x === b); });
  }));
  $('.colecao', dlg).onclick = () => { dlg.close(); abrirAdicionarLista(f.id); };
  const salvar = async (novo) => {
    try {
      await API.status(f.id, novo);
      toast(novo ? `Salvo em "${STATUS_FILME[novo].nome}"` : 'Removido das suas prateleiras');
      dlg.close();
      aoMudar && aoMudar(novo);
    } catch (err) { toast(err.message); }
  };
  $('[data-continuar]', dlg).onclick = () => (escolhido ? salvar(escolhido) : toast('Escolha uma opção.'));
  const rem = $('[data-remover]', dlg);
  if (rem) rem.onclick = () => salvar(null);
}

// Texto/ícone do botão de status na tela do filme
function botaoStatusHtml(status) {
  const s = STATUS_FILME[status];
  return s ? `${icone(status === 'assistido' ? 'check' : s.ico)} ${s.nome}` : `${icone('mais')} Adicionar à`;
}

// Qualquer pop-up fecha ao tocar fora dele (no fundo escuro)
document.addEventListener('click', (e) => {
  if (e.target instanceof HTMLDialogElement && e.target.open) {
    const r = e.target.getBoundingClientRect();
    const dentro = e.clientX >= r.left && e.clientX <= r.right && e.clientY >= r.top && e.clientY <= r.bottom;
    if (!dentro) { e.target.returnValue = ''; e.target.close(); }
  }
});

// Desenhos de cinema bem apagados no fundo (igual ao Figma): no formulário de entrada e em todas as telas
const _rolo = '<svg viewBox="0 0 64 64" fill="currentColor"><path d="M32 4a28 28 0 1 0 0 56 28 28 0 0 0 0-56Zm0 8a6 6 0 1 1 0 12 6 6 0 0 1 0-12Zm-14 14a6 6 0 1 1 0 12 6 6 0 0 1 0-12Zm28 0a6 6 0 1 1 0 12 6 6 0 0 1 0-12ZM32 40a6 6 0 1 1 0 12 6 6 0 0 1 0-12Zm0-11a3 3 0 1 1 0 6 3 3 0 0 1 0-6Z"/></svg>';
const _claq = '<svg viewBox="0 0 64 64" fill="currentColor"><path d="M6 26h52v26a6 6 0 0 1-6 6H12a6 6 0 0 1-6-6V26Z"/><path d="M5 14 55 6l2 11-50 8L5 14Zm9 1 6 7 6-1-6-7-6 1Zm13-2 6 7 6-1-6-7-6 1Zm13-2 6 7 5-1-6-7-5 1Z"/></svg>';
const _fita = '<svg viewBox="0 0 64 64" fill="currentColor"><path d="M8 10h48v44H8V10Zm4 4v6h6v-6h-6Zm0 12v6h6v-6h-6Zm0 12v6h6v-6h-6Zm34-24v6h6v-6h-6Zm0 12v6h6v-6h-6Zm0 12v6h6v-6h-6ZM22 16v32h20V16H22Z"/></svg>';
const DECO_CINEMA = `<div class="deco-cinema" aria-hidden="true">
  <i style="top:9%;left:4%;width:90px;transform:rotate(-18deg)">${_fita}</i>
  <i style="top:12%;right:6%;width:120px">${_rolo}</i>
  <i style="bottom:14%;left:2%;width:110px;transform:rotate(14deg)">${_claq}</i>
  <i style="bottom:8%;left:24%;width:120px">${_rolo}</i>
  <i style="bottom:12%;right:5%;width:100px;transform:rotate(-24deg)">${_fita}</i>
</div>`;

const _pipoca = '<svg viewBox="0 0 64 64" fill="currentColor"><path d="M16 26h32l-4 32H20l-4-32Zm8 4 2 24h3l-1-24h-4Zm10 0-1 24h3l2-24h-4Z"/><path d="M20 24a7 7 0 0 1 4-13 8 8 0 0 1 15-1 7 7 0 0 1 6 14H20Z"/></svg>';
const _ingresso = '<svg viewBox="0 0 64 64" fill="currentColor"><path d="M6 18h52v9a5 5 0 0 0 0 10v9H6v-9a5 5 0 0 0 0-10v-9Zm34 4v4h3v-4h-3Zm0 8v4h3v-4h-3Zm0 8v4h3v-4h-3Z"/></svg>';
// Fundo das telas do app (fica fixo atrás do conteúdo, só na área da direita do menu)
const DECO_FUNDO = `<div class="deco-cinema fundo-app" aria-hidden="true">
  <i style="top:4%;left:18%;width:110px;transform:rotate(-14deg)">${_pipoca}</i>
  <i style="top:6%;left:46%;width:120px;transform:rotate(18deg)">${_claq}</i>
  <i style="top:30%;right:4%;width:130px">${_rolo}</i>
  <i style="top:40%;left:3%;width:100px;transform:rotate(-20deg)">${_fita}</i>
  <i style="top:58%;left:52%;width:120px;transform:rotate(10deg)">${_pipoca}</i>
  <i style="bottom:6%;left:14%;width:130px">${_rolo}</i>
  <i style="bottom:10%;left:38%;width:120px;transform:rotate(-12deg)">${_ingresso}</i>
  <i style="bottom:4%;right:8%;width:110px;transform:rotate(22deg)">${_fita}</i>
  <i style="width:90px">${_rolo}</i>
  <i style="width:100px">${_claq}</i>
  <i style="width:84px">${_ingresso}</i>
  <i style="width:96px">${_pipoca}</i>
  <i style="width:88px">${_fita}</i>
  <i style="width:104px">${_rolo}</i>
  <i style="width:86px">${_claq}</i>
  <i style="width:78px">${_ingresso}</i>
  <i style="width:92px">${_pipoca}</i>
  <i style="width:100px">${_rolo}</i>
  <i style="width:82px">${_fita}</i>
  <i style="width:94px">${_claq}</i>
  <i style="width:80px">${_pipoca}</i>
  <i style="width:76px">${_ingresso}</i>
  <i style="width:88px">${_rolo}</i>
  <i style="width:90px">${_fita}</i>
</div>`;

// =====================================================================
// LISTA DE OPÇÕES DO SITE (no lugar da lista branca/azul do navegador)
// O <select> continua lá escondido: o resto do código segue usando .value e "change".
// =====================================================================
function melhorarSelects(raiz = document) {
  $$('select.chip:not([data-pronto])', raiz).forEach((sel) => {
    sel.dataset.pronto = '1';
    const botao = document.createElement('button');
    botao.type = 'button';
    botao.className = sel.className + ' dd-botao';
    botao.setAttribute('aria-haspopup', 'listbox');
    botao.setAttribute('aria-expanded', 'false');
    botao.setAttribute('aria-label', sel.getAttribute('aria-label') || '');
    const texto = () => { botao.textContent = sel.selectedOptions[0]?.textContent || ''; botao.classList.toggle('escolhido', !!sel.value); };
    texto();
    sel.classList.add('sr-only'); sel.tabIndex = -1; sel.setAttribute('aria-hidden', 'true');
    sel.after(botao);
    sel.addEventListener('change', texto);
    botao.onclick = () => abrirListaSite(sel, botao);
    botao.onkeydown = (e) => { if (e.key === 'ArrowDown') { e.preventDefault(); abrirListaSite(sel, botao); } };
  });
}

function abrirListaSite(sel, botao) {
  fecharListaSite();
  const lista = document.createElement('div');
  lista.className = 'dd-lista';
  lista.setAttribute('role', 'listbox');
  lista.innerHTML = [...sel.options].map((o, i) =>
    `<button type="button" role="option" data-i="${i}" class="${o.selected ? 'sel' : ''}" aria-selected="${o.selected}">${esc(o.textContent)}</button>`).join('');
  document.body.appendChild(lista);
  botao.setAttribute('aria-expanded', 'true');

  // Posição: embaixo do botão (ou em cima, se não couber), presa à tela
  const r = botao.getBoundingClientRect();
  const larg = Math.max(r.width, 200);
  lista.style.minWidth = larg + 'px';
  lista.style.left = Math.min(r.left, innerWidth - larg - 8) + 'px';
  const espacoBaixo = innerHeight - r.bottom - 12;
  if (espacoBaixo >= 220 || espacoBaixo >= r.top) { lista.style.top = r.bottom + 6 + 'px'; lista.style.maxHeight = Math.min(340, espacoBaixo) + 'px'; }
  else { lista.style.bottom = innerHeight - r.top + 6 + 'px'; lista.style.maxHeight = Math.min(340, r.top - 12) + 'px'; }

  const itens = $$('[role=option]', lista);
  const atual = itens.find((b) => b.classList.contains('sel')) || itens[0];
  atual?.scrollIntoView({ block: 'nearest' });
  atual?.focus({ preventScroll: true });

  const escolher = (b) => {
    const i = Number(b.dataset.i);
    if (sel.selectedIndex !== i) { sel.selectedIndex = i; sel.dispatchEvent(new Event('change', { bubbles: true })); }
    fecharListaSite(); botao.focus();
  };
  itens.forEach((b) => (b.onclick = () => escolher(b)));
  lista.onkeydown = (e) => {
    const i = itens.indexOf(document.activeElement);
    if (e.key === 'ArrowDown') { e.preventDefault(); itens[Math.min(i + 1, itens.length - 1)].focus(); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); itens[Math.max(i - 1, 0)].focus(); }
    else if (e.key === 'Home') { e.preventDefault(); itens[0].focus(); }
    else if (e.key === 'End') { e.preventDefault(); itens[itens.length - 1].focus(); }
    else if (e.key === 'Escape' || e.key === 'Tab') { fecharListaSite(); botao.focus(); }
    else if (e.key.length === 1) {   // digitar uma letra pula pra opção que começa com ela
      const k = e.key.toLowerCase();
      const prox = itens.slice(i + 1).concat(itens.slice(0, i + 1)).find((b) => b.textContent.trim().toLowerCase().startsWith(k));
      prox?.focus();
    }
  };
  abrirListaSite.botao = botao;
}

function fecharListaSite() {
  $$('.dd-lista').forEach((l) => l.remove());
  abrirListaSite.botao?.setAttribute('aria-expanded', 'false');
}
// Fecha ao clicar fora, rolar a página ou mudar o tamanho da janela
document.addEventListener('mousedown', (e) => { if (!e.target.closest('.dd-lista, .dd-botao')) fecharListaSite(); });
addEventListener('resize', fecharListaSite);
addEventListener('scroll', (e) => { if (!e.target.closest?.('.dd-lista')) fecharListaSite(); }, true);
addEventListener('hashchange', fecharListaSite);

// =====================================================================
// SELETOR DE COR DO SITE (no lugar do seletor do navegador)
// abrirSeletorCor(botao, '#f27474', { aoMudar(hex) {...}, aoEscolher(hex) {...} })
//   aoMudar: enquanto arrasta (pra mostrar na hora) • aoEscolher: quando solta / digita / clica numa cor pronta
// =====================================================================
const CORES_PRONTAS = ['#f27474', '#e6b82e', '#2ecc71', '#4fb3e8', '#7478f2', '#b06ff2', '#ff8a3d', '#ffffff'];

function hexParaHsv(hex) {
  const n = parseInt(hex.slice(1), 16), r = (n >> 16) / 255, g = ((n >> 8) & 255) / 255, b = (n & 255) / 255;
  const max = Math.max(r, g, b), d = max - Math.min(r, g, b);
  let h = 0;
  if (d) h = max === r ? ((g - b) / d) % 6 : max === g ? (b - r) / d + 2 : (r - g) / d + 4;
  return { h: (h * 60 + 360) % 360, s: max ? d / max : 0, v: max };
}
function hsvParaHex({ h, s, v }) {
  const f = (n) => { const k = (n + h / 60) % 6; return v - v * s * Math.max(0, Math.min(k, 4 - k, 1)); };
  return '#' + [f(5), f(3), f(1)].map((x) => Math.round(x * 255).toString(16).padStart(2, '0')).join('');
}

function abrirSeletorCor(ancora, corInicial, { aoMudar = () => {}, aoEscolher = () => {} } = {}) {
  fecharSeletorCor();
  let hsv = hexParaHsv(/^#[0-9a-f]{6}$/i.test(corInicial || '') ? corInicial : '#7478f2');
  const pop = document.createElement('div');
  pop.className = 'cor-pop';
  pop.setAttribute('role', 'dialog');
  pop.setAttribute('aria-label', 'Escolher cor');
  pop.innerHTML = `
    <div class="cor-area" tabindex="0" aria-label="Saturação e brilho"><i class="cor-pino"></i></div>
    <div class="cor-matiz" tabindex="0" aria-label="Cor"><i class="cor-pino"></i></div>
    <div class="cor-linha">
      <span class="cor-previa"></span>
      <label class="cor-hex"><span>#</span><input maxlength="6" spellcheck="false" aria-label="Código da cor (hex)"></label>
      <button type="button" class="btn btn-primario cor-ok">OK</button>
    </div>
    <div class="cor-prontas">${CORES_PRONTAS.map((c) => `<button type="button" data-c="${c}" style="background:${c}" aria-label="Cor ${c}"></button>`).join('')}</div>`;
  document.body.appendChild(pop);

  const area = $('.cor-area', pop), matiz = $('.cor-matiz', pop), hexIn = $('.cor-hex input', pop);
  const desenhar = (avisar = true) => {
    const hex = hsvParaHex(hsv);
    area.style.background = `linear-gradient(to top, #000, transparent), linear-gradient(to right, #fff, hsl(${hsv.h} 100% 50%))`;
    $('.cor-pino', area).style.cssText = `left:${hsv.s * 100}%;top:${(1 - hsv.v) * 100}%;background:${hex}`;
    $('.cor-pino', matiz).style.cssText = `left:${(hsv.h / 360) * 100}%;background:hsl(${hsv.h} 100% 50%)`;
    $('.cor-previa', pop).style.background = hex;
    if (document.activeElement !== hexIn) hexIn.value = hex.slice(1);
    if (avisar) aoMudar(hex);
  };
  const escolher = () => aoEscolher(hsvParaHex(hsv));

  // Arrastar na área (saturação/brilho) e na barra de cor
  const arrastar = (el, aplicar) => {
    el.onpointerdown = (e) => {
      el.setPointerCapture(e.pointerId);
      const mover = (ev) => { const r = el.getBoundingClientRect(); aplicar(Math.min(1, Math.max(0, (ev.clientX - r.left) / r.width)), Math.min(1, Math.max(0, (ev.clientY - r.top) / r.height))); desenhar(); };
      mover(e);
      el.onpointermove = mover;
      el.onpointerup = () => { el.onpointermove = null; escolher(); };
    };
  };
  arrastar(area, (x, y) => { hsv.s = x; hsv.v = 1 - y; });
  arrastar(matiz, (x) => { hsv.h = x * 359.9; });
  // Teclado: setas mexem a cor
  area.onkeydown = (e) => {
    const p = e.shiftKey ? .1 : .02, m = { ArrowLeft: [-p, 0], ArrowRight: [p, 0], ArrowUp: [0, p], ArrowDown: [0, -p] }[e.key];
    if (!m) return; e.preventDefault();
    hsv.s = Math.min(1, Math.max(0, hsv.s + m[0])); hsv.v = Math.min(1, Math.max(0, hsv.v + m[1])); desenhar(); escolher();
  };
  matiz.onkeydown = (e) => {
    const m = { ArrowLeft: -5, ArrowRight: 5 }[e.key] * (e.shiftKey ? 4 : 1);
    if (!m) return; e.preventDefault(); hsv.h = (hsv.h + m + 360) % 360; desenhar(); escolher();
  };
  hexIn.oninput = () => {
    const v = hexIn.value.replace(/[^0-9a-f]/gi, '').slice(0, 6); hexIn.value = v;
    if (v.length === 6) { hsv = hexParaHsv('#' + v); desenhar(); }
  };
  hexIn.onkeydown = (e) => { if (e.key === 'Enter') { e.preventDefault(); escolher(); fecharSeletorCor(); } };
  $$('.cor-prontas button', pop).forEach((b) => (b.onclick = () => { hsv = hexParaHsv(b.dataset.c); desenhar(); escolher(); }));
  $('.cor-ok', pop).onclick = () => { escolher(); fecharSeletorCor(); ancora.focus(); };
  pop.onkeydown = (e) => { if (e.key === 'Escape') { e.stopPropagation(); e.preventDefault(); fecharSeletorCor(); ancora.focus(); } };

  // Posição: perto do botão, sem sair da tela (funciona até dentro de pop-up)
  const dlg = ancora.closest('dialog');
  if (dlg) dlg.appendChild(pop);
  const r = ancora.getBoundingClientRect(), w = 260, h = pop.offsetHeight || 330;
  pop.style.left = Math.max(8, Math.min(r.left, innerWidth - w - 8)) + 'px';
  pop.style.top = (r.bottom + 8 + h < innerHeight ? r.bottom + 8 : Math.max(8, r.top - h - 8)) + 'px';
  desenhar(false);
  area.focus();
  setTimeout(() => document.addEventListener('pointerdown', foraDoSeletor, true));
}
function foraDoSeletor(e) { if (!e.target.closest('.cor-pop')) fecharSeletorCor(); }
function fecharSeletorCor() {
  $$('.cor-pop').forEach((p) => p.remove());
  document.removeEventListener('pointerdown', foraDoSeletor, true);
}

// Filme que ainda não estreou (data de lançamento no futuro)
function naoEstreou(f) {
  if (!f || !f.lancamento) return false;
  return f.lancamento > new Date().toISOString().slice(0, 10);
}

// Avisa ENQUANTO digita se o @ ou o e-mail já são de outra conta.
// tipo: 'username' | 'email'. Devolve uma função que diz se está ok (pra travar o "Criar").
function verificarDisponivel(input, tipo) {
  const aviso = document.createElement('p');
  aviso.className = 'aviso-campo';
  aviso.setAttribute('aria-live', 'polite');
  (input.closest('.input-ico, .input-prefixo') || input).after(aviso);
  let ok = true, timer, ultimo = '';
  const mostrar = (livre, texto) => {
    ok = livre;
    aviso.textContent = texto || '';
    aviso.className = 'aviso-campo ' + (texto ? (livre ? 'livre' : 'ocupado') : '');
    input.setAttribute('aria-invalid', String(!livre));
  };
  input.addEventListener('input', () => {
    clearTimeout(timer);
    const v = input.value.trim();
    if (!v) { mostrar(true, ''); return; }
    timer = setTimeout(async () => {
      if (v === ultimo) return;
      ultimo = v;
      try {
        const r = (await API.disponivel({ [tipo]: v }))[tipo];
        if (input.value.trim() !== v) return;   // a pessoa já mudou o texto
        mostrar(r.livre, r.livre ? (tipo === 'username' ? `@${v.replace(/^@/, '').toLowerCase()} está livre ✓` : '') : r.motivo);
      } catch { mostrar(true, ''); }
    }, 400);
  });
  return () => ok;
}

// ---------------------------------------------------------------------
// Avisos de formulário no padrão do site (no lugar do balão do navegador,
// tipo "Inclua um @ no endereço de e-mail"). Vale para TODOS os formulários.
// ---------------------------------------------------------------------
function textoDoErro(c) {
  const v = c.validity;
  if (v.valueMissing) return c.type === 'checkbox' ? 'Marque esta opção para continuar.' : 'Preencha este campo.';
  if (v.typeMismatch && c.type === 'email') return 'Digite um e-mail válido, por exemplo: nome@email.com';
  if (v.typeMismatch && c.type === 'url') return 'Digite um link válido, começando com https://';
  if (v.tooShort) return `Use pelo menos ${c.minLength} caracteres (agora tem ${c.value.length}).`;
  if (v.tooLong) return `Use no máximo ${c.maxLength} caracteres.`;
  if (v.patternMismatch) return c.title || 'O formato não está certo.';
  if (v.rangeUnderflow) return `O mínimo é ${c.min}.`;
  if (v.rangeOverflow) return `O máximo é ${c.max}.`;
  if (v.stepMismatch) return 'Esse valor não é aceito aqui.';
  return c.validationMessage || 'Confira este campo.';
}
function tirarBalaoCampo(c) {
  const b = c._balao;
  if (b) { b.remove(); c._balao = null; c.removeAttribute('aria-invalid'); }
}
let ultimoAvisoForm = 0;
document.addEventListener('invalid', (e) => {
  const c = e.target;
  e.preventDefault();                       // some o balão do navegador
  const agora = Date.now();
  if (agora - ultimoAvisoForm < 300) return; // mostra só no 1º campo com problema
  ultimoAvisoForm = agora;
  $$('.balao-campo').forEach((b) => b.remove());
  const balao = document.createElement('p');
  balao.className = 'balao-campo';
  balao.setAttribute('role', 'alert');
  balao.innerHTML = `<span class="ico" aria-hidden="true">!</span><span>${esc(textoDoErro(c))}</span>`;
  (c.closest('.input-ico, .input-prefixo') || c).after(balao);
  c._balao = balao;
  c.setAttribute('aria-invalid', 'true');
  c.focus({ preventScroll: true });
  c.scrollIntoView({ block: 'center', behavior: 'smooth' });
  const limpar = () => { tirarBalaoCampo(c); c.removeEventListener('input', limpar); c.removeEventListener('change', limpar); };
  c.addEventListener('input', limpar);
  c.addEventListener('change', limpar);
}, true);

// Faixa de pôsteres que desliza sem parar (Boas-vindas e telas de entrada no celular).
// Os pôsteres vão repetidos pra faixa ser sempre mais larga que a tela.
function faixaPosters(lista) {
  if (!lista.length) return '';
  let cheia = lista;
  while (cheia.length < 14) cheia = cheia.concat(lista);
  const imgs = cheia.map((f) => `<img src="${esc(f.poster)}" alt="" loading="lazy">`).join('');
  return `<div class="trilho">${imgs}${imgs}</div>`;
}

// Sair da conta: limpa tudo e vai na hora para a tela de Boas-vindas
function sairDaConta() {
  Sessao.sair();
  window.MINHAS_NOTAS = null;
  $$('dialog[open]').forEach((d) => d.close());
  if (location.hash === '#/' || location.hash === '' || location.hash === '#/boas-vindas') navegar();
  else location.hash = '#/';
}

// ---------------------------------------------------------------------
// Botões de opção (Abertas/Meio/Fechadas, Calmo/Normal/Festa): a "pílula"
// amarela desliza até a opção escolhida, suave igual ao botão de liga/desliga.
// ---------------------------------------------------------------------
function posicionarPilula(seg, animar = true) {
  const pil = seg.querySelector('.seg-pilula');
  const at = seg.querySelector('button.ativo');
  if (!pil) return;
  if (!animar) pil.style.transition = 'none';
  if (!at) { pil.style.opacity = '0'; }
  else {
    pil.style.opacity = '1';
    pil.style.width = `${at.offsetWidth}px`;
    pil.style.transform = `translateX(${at.offsetLeft}px)`;
  }
  if (!animar) { void pil.offsetWidth; pil.style.transition = ''; }
}
function ligarSegmentados(raiz = document) {
  $$('.segmentado:not(.com-pilula)', raiz).forEach((seg) => {
    seg.classList.add('com-pilula');
    seg.insertAdjacentHTML('afterbegin', '<i class="seg-pilula" aria-hidden="true"></i>');
    posicionarPilula(seg, false);
    if (window.ResizeObserver) new ResizeObserver(() => posicionarPilula(seg, false)).observe(seg);
  });
}
// Marca a opção escolhida e anima a pílula até ela
function escolherSegmento(botao) {
  const seg = botao.closest('.segmentado');
  seg.querySelectorAll('button').forEach((b) => { b.classList.toggle('ativo', b === botao); b.setAttribute('aria-pressed', String(b === botao)); });
  posicionarPilula(seg);
}
