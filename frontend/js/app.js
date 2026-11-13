// =====================================================================
// Roteador do site (SPA com "#/rota") -> funciona em navegador e WebView
// Cada arquivo em js/paginas/ registra suas telas em Paginas[...]
// =====================================================================

// soPc: no celular o item some da barra de baixo (vira atalho dentro do Perfil)
const MENU = [
  { rota: 'inicio', nome: 'Início', ico: 'inicio' },
  { rota: 'filmes', nome: 'Filmes e séries', curto: 'Filmes', ico: 'filmes', tambem: ['filme', 'serie'] },
  { rota: 'reviews', nome: 'Reviews', ico: 'reviews' },
  { rota: 'listas', nome: 'Listas', ico: 'listas', tambem: ['lista'] },
  { rota: 'casa', nome: 'Smart Home', ico: 'lampada', soPc: true },
  { rota: 'perfil', nome: 'Perfil', ico: 'perfil', tambem: ['meus-filmes', 'editar-perfil', 'conexoes'] },
];

// "#/filme/123/review?x=1" -> { nome: 'filme', partes: ['123','review'], query: {x:'1'} }
function lerRota() {
  const [caminho, qs = ''] = location.hash.replace(/^#\/?/, '').split('?');
  const [nome = '', ...partes] = caminho.split('/').filter(Boolean);
  return { nome, partes, query: Object.fromEntries(new URLSearchParams(qs)) };
}

function montarLayout(rota, titulo) {
  const u = Sessao.usuario || {};
  const ativo = (m) => m.rota === rota.nome || (m.tambem || []).includes(rota.nome);
  $('#app').innerHTML = `
    <div class="layout">
      <aside class="sidebar">
        <a class="logo" href="#/inicio"><img src="/img/logo.svg" alt=""><span>LetterRoyale</span></a>
        <nav class="nav" aria-label="Menu principal">
          ${MENU.map((m) => `<a href="#/${m.rota}" class="${ativo(m) ? 'ativo' : ''} ${m.soPc ? 'so-pc' : ''}" ${ativo(m) ? 'aria-current="page"' : ''}>${icone(m.ico)}<span class="nome-longo">${m.nome}</span><span class="nome-curto">${m.curto || m.nome}</span></a>`).join('')}
        </nav>
        <div class="user-chip">
          ${avatar(u, 'sm')}
          <div class="info">${esc(u.nome)}<small>@${esc(u.username)}</small></div>
          <button class="icon-btn" id="sair" title="Sair" style="width:34px;height:34px;background:none">${icone('sair')}</button>
        </div>
      </aside>
      <main class="main">
        ${DECO_FUNDO}
        <header class="topbar">
          <h1>${esc(titulo)}</h1>
          <form class="busca" id="busca" role="search">
            ${icone('busca')}
            <label class="sr-only" for="q">Buscar</label>
            <input id="q" type="search" placeholder="Buscar filmes, séries, atores..." value="${esc(rota.query.q || '')}" enterkeyhint="search">
          </form>
          <div class="sino">
            <button class="icon-btn sino-btn" id="sino" aria-haspopup="dialog" aria-expanded="false" aria-label="Notificações">${icone('sino')}<span class="sino-num" id="sino-num" hidden></span></button>
            <div class="sino-painel" id="sino-painel" role="dialog" aria-label="Notificações" hidden></div>
          </div>
          <div class="menu-foto">
            <button class="menu-foto-btn" id="menu-foto" aria-haspopup="menu" aria-expanded="false" aria-label="Menu da conta">${avatar(u)}</button>
            <div class="menu-foto-lista" id="menu-foto-lista" role="menu" hidden>
              <div class="menu-foto-quem">${avatar(u, 'sm')}<div><b>${esc(u?.nome || '')}</b><small>@${esc(u?.username || '')}</small></div></div>
              <a role="menuitem" href="#/perfil">${icone('perfil')} Meu perfil</a>
              <a role="menuitem" href="#/perfil?aba=avaliacoes">${icone('reviews')} Minhas reviews</a>
              <a role="menuitem" href="#/conexoes">${icone('conexoes')} Conexões</a>
              <a role="menuitem" href="#/casa" class="so-celular-flex">${icone('lampada')} Smart Home</a>
              <a role="menuitem" href="#/editar-perfil">${icone('lapis')} Editar perfil</a>
              <button role="menuitem" type="button" id="menu-sair" class="sair">${icone('sair')} Sair</button>
            </div>
          </div>
        </header>
        <div id="conteudo"></div>
      </main>
    </div>`;

  // Menu da foto (canto de cima): abre/fecha, fecha ao clicar fora ou apertar Esc
  const mf = $('#menu-foto'), mfl = $('#menu-foto-lista');
  const fecharMenuFoto = () => { mfl.hidden = true; mf.setAttribute('aria-expanded', 'false'); };
  mf.onclick = (e) => {
    e.stopPropagation();
    const abrir = mfl.hidden;
    mfl.hidden = !abrir; mf.setAttribute('aria-expanded', String(abrir));
    if (abrir) mfl.querySelector('[role=menuitem]').focus();
  };
  mfl.onclick = (e) => { if (e.target.closest('a')) fecharMenuFoto(); };
  mfl.onkeydown = (e) => {
    const itens = [...mfl.querySelectorAll('[role=menuitem]')].filter((i) => i.offsetParent);
    const i = itens.indexOf(document.activeElement);
    if (e.key === 'Escape') { fecharMenuFoto(); mf.focus(); }
    if (e.key === 'ArrowDown') { e.preventDefault(); itens[(i + 1) % itens.length].focus(); }
    if (e.key === 'ArrowUp') { e.preventDefault(); itens[(i - 1 + itens.length) % itens.length].focus(); }
  };
  document.addEventListener('click', (e) => { if (!e.target.closest('.menu-foto')) fecharMenuFoto(); });
  $('#menu-sair').onclick = () => sairDaConta();
  ligarSino();

  $('#busca').onsubmit = (e) => {
    e.preventDefault();
    const q = $('#q').value.trim();
    // Se estiver olhando séries, a busca continua em séries
    const tipo = rota.nome === 'filmes' && ['serie', 'filme', 'doc'].includes(rota.query.tipo) ? `&tipo=${rota.query.tipo}` : '';
    location.hash = q ? `#/filmes?q=${encodeURIComponent(q)}${tipo}` : '#/filmes';
  };
  $('#sair').onclick = () => sairDaConta();
  return $('#conteudo');
}

async function navegar() {
  const rota = lerRota();
  const logado = !!Sessao.token;

  // Rota vazia: logado vai pro Início, senão Boas-vindas
  if (!rota.nome) rota.nome = logado ? 'inicio' : 'boas-vindas';

  const pagina = Paginas[rota.nome];
  if (!pagina) { location.hash = '#/'; return; }
  if (!pagina.publica && !logado) { location.hash = '#/login'; return; }
  if (pagina.publica && logado && rota.nome !== 'boas-vindas') { location.hash = '#/inicio'; return; }

  window.scrollTo(0, 0);
  const titulo = typeof pagina.titulo === 'function' ? pagina.titulo(rota) : pagina.titulo;
  document.title = `${titulo || 'Início'} · LetterRoyale`;

  if (pagina.publica) {
    $('#app').innerHTML = '';
    await pagina.render($('#app'), rota);
  } else {
    const alvo = montarLayout(rota, titulo);
    alvo.innerHTML = carregando();
    if (window.MINHAS_NOTAS === null) await carregarMinhasNotas();   // 1x por sessão
    try { await pagina.render(alvo, rota); }
    catch (e) { console.error(e); alvo.innerHTML = erroBox(e); }
  }
  melhorarSelects($('#app'));   // troca as listas do navegador pela lista do site
  ligarSegmentados($('#app'));  // pílula que desliza nas opções
}

// Quando um filme do Modo Cinema termina, o site pergunta a opinião (em qualquer tela)
let perguntando = false;
async function verificarFimDeFilme() {
  if (!Sessao.token || perguntando) return;
  perguntando = true;   // trava ANTES de perguntar ao servidor: evita abrir 2 avisos ao mesmo tempo
  let est;
  try { est = await API.cinema(); } catch { perguntando = false; return; }
  if (!est.terminou || !est.filme) { perguntando = false; return; }
  const f = est.filme;
  const ehEp = !!f.episodio;
  const dlg = document.createElement('dialog');
  const prox = est.proximo_fila;
  dlg.className = 'fim-filme';
  dlg.innerHTML = `
    <div style="display:flex;gap:16px;align-items:center">
      <div style="width:80px;flex-shrink:0;border-radius:8px;overflow:hidden">${poster(f)}</div>
      <div><h3 style="margin:0 0 6px">🎬 Fim de ${esc(f.titulo)}${ehEp ? ` · T${f.temporada}E${f.episodio}` : ''}!</h3>
      <p class="sinopse">${ehEp ? 'A série ficou como "Assistindo" no seu perfil. O que está achando?' : 'Já marcamos como assistido. O que achou do filme?'}</p></div>
    </div>
    ${prox ? `<div class="proximo-fila"><span class="meta">Próximo da fila</span><b>${esc(prox.titulo)}${prox.episodio ? ` · ${esc(textoEpisodio(prox))}` : ''}</b></div>` : ''}
    <div class="acoes acoes-iguais acoes-linha">
      <button class="btn" value="depois">Depois</button>
      <button class="btn ${est.proximo_fila || ehEp ? '' : 'btn-primario'}" value="avaliar">Avaliar agora</button>
      ${est.proximo_fila ? `<button class="btn btn-primario" value="proximo">${icone('play')} Assistir o próximo</button>`
        : ehEp ? `<button class="btn btn-primario" value="prox-ep">${icone('play')} Próximo episódio</button>` : ''}
    </div>`;
  document.body.appendChild(dlg);
  dlg.showModal();
  dlg.addEventListener('close', () => { dlg.remove(); perguntando = false; });
  dlg.querySelectorAll('button').forEach((b) => (b.onclick = async () => {
    if (b.value === 'prox-ep') {
      // Série sem fila: começa o episódio seguinte (ou o 1º da próxima temporada)
      try {
        const ep = await proximoEpisodio(f);
        if (!ep) { toast('Esse foi o último episódio disponível. 🎉'); }
        else {
          await API.cinemaIniciar({ tmdb_id: f.id, modo: 'assistindo', ritmo: 'normal', temporada: ep.temporada, episodio: ep.episodio });
          toast(`🎬 Começando T${ep.temporada}E${ep.episodio}!`);
        }
      } catch (err) { toast(err.message); }
      dlg.close();
      if (location.hash.startsWith('#/casa')) navegar();
      return;
    }
    if (b.value === 'proximo') {
      // Começa o 1º da fila (a avaliação do que acabou fica pra depois, em Meus filmes)
      try { const e = await API.assistirProximo(); toast(`🎬 Começando ${e.filme?.titulo || 'o próximo'}!`); }
      catch (err) { toast(err.message); }
      dlg.close();
      if (location.hash.startsWith('#/casa')) navegar();
      return;
    }
    await API.cinemaParar().catch(() => {});
    dlg.close();
    if (b.value === 'avaliar') location.hash = `#/filme/${f.id}/review`;
  }));
}

async function proximoEpisodio(f) {
  const hoje = new Date().toISOString().slice(0, 10);
  const saiu = (e) => !e.data || e.data <= hoje;
  const t = await API.temporada(f.id, f.temporada);
  const seg = t.episodios.find((e) => e.numero === f.episodio + 1);
  if (seg) return saiu(seg) ? { temporada: f.temporada, episodio: seg.numero } : null;
  try {
    const t2 = await API.temporada(f.id, f.temporada + 1);
    const pri = t2.episodios[0];
    return pri && saiu(pri) ? { temporada: f.temporada + 1, episodio: pri.numero } : null;
  } catch { return null; }
}

// ---------------------------------------------------------------------
// SININHO: bolinha com quantas não lidas + painel com as notificações
// ---------------------------------------------------------------------
const ICONE_NOTIF = { seguiu: 'perfil', review: 'reviews', curtida: 'coracao', estreou: 'filmes', episodio: 'play', cinema: 'lampada', lista: 'listas' };
let naoLidas = 0;

function pintarSino(n) {
  naoLidas = n;
  const num = $('#sino-num');
  if (!num) return;
  num.hidden = !n;
  num.textContent = n > 9 ? '9+' : String(n);
  $('#sino').setAttribute('aria-label', n ? `Notificações: ${n} não lida${n > 1 ? 's' : ''}` : 'Notificações');
}

async function contarNotificacoes() {
  if (!Sessao.token || !$('#sino')) return;
  try { pintarSino((await API.notificacoesContagem()).nao_lidas); } catch {}
}

function itemNotificacao(n) {
  const figura = n.ator ? avatar(n.ator, 'sm')
    : n.poster ? `<img class="notif-poster" src="${esc(n.poster)}" alt="">`
    : `<span class="notif-ico">${icone(ICONE_NOTIF[n.tipo] || 'sino')}</span>`;
  return `
    <a class="notif ${n.lida ? '' : 'nova'}" href="${esc(n.link || '#/inicio')}" data-notif="${n.id}">
      ${figura}
      <span class="notif-txt">${esc(n.texto)}<small>${tempoAtras(n.criado_em)}</small></span>
      ${n.lida ? '' : '<i class="notif-ponto" aria-label="não lida"></i>'}
    </a>`;
}

async function abrirSino() {
  const painel = $('#sino-painel'), btn = $('#sino');
  painel.hidden = false;
  btn.setAttribute('aria-expanded', 'true');
  painel.innerHTML = `<div class="sino-topo"><b>Notificações</b></div>${carregando()}`;
  let d;
  try { d = await API.notificacoes(); } catch (e) { painel.innerHTML = erroBox(e); return; }
  pintarSino(d.nao_lidas);
  painel.innerHTML = `
    <div class="sino-topo"><b>Notificações</b>${d.nao_lidas ? '<button type="button" class="link" id="sino-todas">Marcar todas como lidas</button>' : ''}</div>
    <div class="sino-lista">${d.itens.length ? d.itens.map(itemNotificacao).join('')
      : '<p class="sino-vazio">Nada por aqui ainda. Quando alguém te seguir, curtir sua review ou um filme do seu Quero assistir estrear, aparece aqui.</p>'}</div>`;
  const todas = $('#sino-todas');
  if (todas) todas.onclick = async () => {
    try { pintarSino((await API.notificacoesTodasLidas()).nao_lidas); } catch {}
    $$('.notif.nova', painel).forEach((a) => { a.classList.remove('nova'); a.querySelector('.notif-ponto')?.remove(); });
    todas.remove();
  };
  $$('[data-notif]', painel).forEach((a) => (a.onclick = () => {
    if (a.classList.contains('nova')) API.notificacaoLida(a.dataset.notif).then((r) => pintarSino(r.nao_lidas)).catch(() => {});
    fecharSino();
  }));
}

function fecharSino() {
  const painel = $('#sino-painel');
  if (!painel || painel.hidden) return;
  painel.hidden = true;
  $('#sino').setAttribute('aria-expanded', 'false');
}

function ligarSino() {
  pintarSino(naoLidas);
  $('#sino').onclick = (e) => {
    e.stopPropagation();
    if ($('#sino-painel').hidden) abrirSino(); else fecharSino();
  };
  $('#sino-painel').onclick = (e) => e.stopPropagation();
  $('#sino-painel').onkeydown = (e) => { if (e.key === 'Escape') { fecharSino(); $('#sino').focus(); } };
  contarNotificacoes();
}

document.addEventListener('click', fecharSino);   // clicou fora: fecha o painel do sininho
window.addEventListener('hashchange', () => { navegar(); verificarFimDeFilme(); });
navegar();
verificarFimDeFilme();
setInterval(verificarFimDeFilme, 20000);   // confere a cada 20s se o filme acabou
setInterval(contarNotificacoes, 60000);   // a bolinha do sininho se atualiza a cada 1 minuto
