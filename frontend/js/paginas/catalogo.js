// Tela Catálogo (menu "Filmes") — TODOS os filmes do TMDB com filtros e paginação
window.Paginas = window.Paginas || {};

let cacheGeneros = null;
let cacheGenerosSerie = null;
let cacheStreamings = null;

/** Nome que o site mostra pra cada gênero do TMDB (o mesmo das fileiras do Início). */
const NOMES_GENERO = {
  28: 'Ação', 12: 'Aventura', 16: 'Animação', 35: 'Comédia', 80: 'Crime', 99: 'Documentário', 18: 'Drama',
  10751: 'Família', 14: 'Fantasia', 36: 'História', 27: 'Terror', 10402: 'Música', 9648: 'Mistério',
  10749: 'Romance', 878: 'Ficção científica', 10770: 'Filme de TV', 53: 'Suspense', 10752: 'Guerra', 37: 'Faroeste',
};

Paginas.filmes = {
  // Veio do "Ver todos" de uma categoria (?genero=53): o título já diz qual é ("Catálogo: Suspense")
  titulo: (rota) => (rota.query.q ? `Busca: ${rota.query.q}`
    : rota.query.genero && NOMES_GENERO[rota.query.genero] ? `Catálogo: ${NOMES_GENERO[rota.query.genero]}` : 'Catálogo'),
  async render(raiz, rota) {
    const f = { pagina: 1, ordem: 'popularity.desc', ...rota.query };   // filtros vêm da URL
    // tipo: todos (padrão) | filme | serie | doc. Link antigo com gênero/classificação = filmes.
    const tipo = ['filme', 'serie', 'doc', 'todos'].includes(f.tipo) ? f.tipo : (f.genero || f.classificacao ? 'filme' : 'todos');
    const serie = tipo === 'serie';
    const misto = tipo === 'todos' || tipo === 'doc';   // filmes + séries juntos (sem gênero/classificação)

    if (!cacheGeneros) cacheGeneros = await API.generos().catch(() => []);
    if (serie && !cacheGenerosSerie) cacheGenerosSerie = await API.generos('serie').catch(() => []);
    // O TMDB chama Suspense de "Thriller": aqui fica com o nome que o site usa nas categorias
    const generos = (serie ? cacheGenerosSerie : cacheGeneros).map((g) => ({ ...g, name: NOMES_GENERO[g.id] || g.name }));
    if (!cacheStreamings) cacheStreamings = await API.streamings().catch(() => []);

    const anoAtual = new Date().getFullYear();
    const anos = Array.from({ length: 80 }, (_, i) => anoAtual - i);
    const opt = (v, txt, atual) => `<option value="${v}" ${String(atual) === String(v) ? 'selected' : ''}>${esc(txt)}</option>`;

    raiz.innerHTML = `
      <div class="tipo-catalogo" role="tablist" aria-label="Tipo">
        ${[['todos', 'Todos', 'busca'], ['filme', 'Filmes', 'filmes'], ['serie', 'Séries', 'listas'], ['doc', 'Documentários', 'olho', 'Docs']].map(([k, nome, ico, curto]) =>
          `<button role="tab" class="${tipo === k ? 'ativo' : ''}" aria-selected="${tipo === k}" aria-label="${nome}" data-tipo="${k}">${icone(ico)} <span class="tc-longo">${nome}</span><span class="tc-curto">${curto || nome}</span></button>`).join('')}
      </div>
      <div class="barra-catalogo">
        <div class="chips" style="flex:1;min-width:0">
          <button class="chip ${!f.genero && !f.ano && !f.classificacao && !f.streaming ? 'ativo' : ''}" data-limpar>Todos</button>
          ${misto ? '' : `<select class="chip" data-f="genero" aria-label="Gênero">${opt('', 'Gênero', f.genero)}${generos.map((g) => opt(g.id, g.name, f.genero)).join('')}</select>`}
          <select class="chip" data-f="ano" aria-label="Ano">${opt('', 'Ano', f.ano)}${anos.map((a) => opt(a, a, f.ano)).join('')}</select>
          ${serie || misto ? '' : `<select class="chip" data-f="classificacao" aria-label="Classificação">${opt('', 'Classificação', f.classificacao)}${['L', '10', '12', '14', '16', '18'].map((c) => opt(c, c === 'L' ? 'Livre' : `${c} anos`, f.classificacao)).join('')}</select>`}
          <select class="chip" data-f="streaming" aria-label="Streaming">${opt('', 'Streaming', f.streaming)}${cacheStreamings.map((s) => opt(s.id, s.nome, f.streaming)).join('')}</select>
        </div>
        <span class="meta" id="total"></span>
        <select class="chip" data-f="ordem" aria-label="Ordenar">
          ${opt('popularity.desc', 'Mais populares', f.ordem)}${opt('vote_average.desc', 'Mais bem avaliados', f.ordem)}${opt('primary_release_date.desc', 'Mais recentes', f.ordem)}
        </select>
      </div>
      <div id="grade">${carregando()}</div>`;

    // Muda filtro -> muda a URL -> roteador desenha de novo
    const aplicar = (novo) => {
      const tudo = { ...f, ...novo };
      Object.keys(tudo).forEach((k) => !tudo[k] && delete tudo[k]);
      location.hash = '#/filmes?' + new URLSearchParams(tudo);
    };
    $$('[data-f]', raiz).forEach((s) => (s.onchange = () => aplicar({ [s.dataset.f]: s.value, pagina: 1 })));
    $('[data-limpar]', raiz).onclick = () => (location.hash = tipo === 'todos' ? '#/filmes' : `#/filmes?tipo=${tipo}`);
    // Trocar Filmes <-> Séries mantém só a busca (gênero muda de lista)
    $$('[data-tipo]', raiz).forEach((b) => (b.onclick = () => {
      const q = { ...(f.q ? { q: f.q } : {}), ...(b.dataset.tipo !== 'todos' ? { tipo: b.dataset.tipo } : {}) };
      location.hash = '#/filmes' + (Object.keys(q).length ? '?' + new URLSearchParams(q) : '');
    }));

    try {
      const pedido = { ...f, tipo: tipo === 'filme' ? '' : tipo };
      if (misto) { delete pedido.genero; delete pedido.classificacao; }
      Object.keys(pedido).forEach((k) => pedido[k] === '' && delete pedido[k]);
      const d = await API.catalogo(pedido);
      $('#total').textContent = `${d.total.toLocaleString('pt-BR')} ${{ serie: 'séries', filme: 'filmes', doc: 'documentários', todos: 'títulos' }[tipo]}`;
      const pessoa = d.pessoa ? `<div class="pessoa-busca">${d.pessoa.foto ? `<img src="${esc(d.pessoa.foto)}" alt="">` : ''}<div><small>${esc(d.pessoa.funcao)}</small><b>Filmes de ${esc(d.pessoa.nome)}</b></div></div>` : '';
      $('#grade').innerHTML = pessoa + (d.filmes.length
        ? `<div class="grade grade-catalogo">${d.filmes.map(filmeCard).join('')}</div>${paginacao(d.pagina, d.total_paginas)}`
        : vazio({ serie: 'Nenhuma série encontrada com esses filtros.', filme: 'Nenhum filme encontrado com esses filtros.', doc: 'Nenhum documentário encontrado.', todos: 'Nada encontrado com esses filtros.' }[tipo]));
      $$('[data-pag]', raiz).forEach((b) => (b.onclick = () => aplicar({ pagina: b.dataset.pag })));
      ligarColunasCheias($('.grade-catalogo', raiz));
    } catch (e) { $('#grade').innerHTML = erroBox(e); }
  },
};

function paginacao(atual, total) {
  if (total <= 1) return '';
  const nums = new Set([1, atual - 1, atual, atual + 1, total].filter((n) => n >= 1 && n <= total));
  let html = `<button data-pag="${atual - 1}" ${atual === 1 ? 'disabled' : ''} aria-label="Anterior">‹</button>`;
  let ant = 0;
  [...nums].sort((a, b) => a - b).forEach((n) => {
    if (n - ant > 1) html += '<button disabled>…</button>';
    html += `<button data-pag="${n}" class="${n === atual ? 'ativo' : ''}">${n}</button>`;
    ant = n;
  });
  html += `<button data-pag="${atual + 1}" ${atual === total ? 'disabled' : ''} aria-label="Próxima">›</button>`;
  return `<nav class="paginacao" aria-label="Páginas">${html}</nav>`;
}

// A página vem com 40 filmes. Escolhe um número de colunas que divide 40 (2, 4, 5, 8 ou 10)
// para a grade nunca terminar com uma linha pela metade.
function ligarColunasCheias(grade) {
  if (!grade) return;
  const ajustar = () => {
    const largura = grade.clientWidth;
    if (!largura) return;
    const minimo = innerWidth <= 900 ? 110 : 125;   // largura mínima de cada pôster
    const cabem = Math.floor((largura + 16) / (minimo + 16));
    const colunas = [10, 8, 5, 4, 2].find((n) => n <= cabem) || 2;
    grade.style.gridTemplateColumns = `repeat(${colunas}, minmax(0, 1fr))`;
  };
  ajustar();
  if (window.ResizeObserver) new ResizeObserver(ajustar).observe(grade);
}
