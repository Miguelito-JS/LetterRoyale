// Tela Início: destaque, recomendados, continue assistindo, minha casa, amigos assistiram
window.Paginas = window.Paginas || {};

const ICONE_DISP = { iluminacao: 'lampada', cortinas: 'cortina', temperatura: 'termometro', tv_som: 'tv' };
function textoDisp(d) {
  if (!d.ligado) return 'Desligado';
  if (d.tipo === 'iluminacao') return `${d.valor}%`;
  if (d.tipo === 'cortinas') return d.estado === 'meio' ? 'Meio abertas' : d.estado[0].toUpperCase() + d.estado.slice(1);
  if (d.tipo === 'temperatura') return `${d.valor}°C`;
  return `Ligado • Volume ${d.valor}%`;
}

Paginas.inicio = {
  titulo: 'Início',
  async render(raiz) {
    // Pede tudo em paralelo. Se uma parte falhar, o resto aparece mesmo assim.
    const [emAlta, continuar, casa, amigos, perfil] = await Promise.all([
      API.emAlta().catch(() => []),
      API.continuar().catch(() => []),
      API.casa().catch(() => null),
      API.amigos().catch(() => []),
      API.perfil().catch(() => null),
    ]);

    // Banner "EM DESTAQUE": os 5 filmes em alta, trocando sozinho (bolinhas igual ao Figma)
    const destaques = emAlta.filter((f) => f.backdrop).slice(0, 5);
    // Recomendados: baseado no último favorito; se não tiver, usa "em alta"
    let recomendados = emAlta.slice(5, 25);
    if (perfil?.favoritos?.length) {
      recomendados = await API.recomendados(perfil.favoritos[0].id).catch(() => recomendados);
    }
    const cenaAtiva = casa?.cenas.find((c) => c.ativa);
    // Últimos assistidos: filmes e séries marcados como "Assistido", do mais recente pro mais antigo
    const ultimos = [...(perfil?.prateleiras?.assistido?.filmes || []), ...(perfil?.prateleiras_series?.assistido?.filmes || [])]
      .sort((a, b) => String(b.assistido_em || '').localeCompare(String(a.assistido_em || '')))
      .slice(0, 16);

    raiz.innerHTML = `
      <div class="colunas">
        <div>
          ${destaques.length ? `
          <section class="banner destaque" id="destaque" aria-roledescription="carrossel">
            <div class="destaque-txt">
              <span class="tag">Em destaque</span>
              <h2 id="d-titulo"></h2>
              <p class="linha" id="d-linha"></p>
              <div class="acoes">
                <a class="btn btn-primario" id="d-ver">Ver detalhes</a>
                <button class="btn" id="add-destaque">${icone('mais')} Minha lista</button>
              </div>
            </div>
            <div class="bolinhas" role="tablist">${destaques.map((_, i) => `<button role="tab" aria-label="Destaque ${i + 1}" data-i="${i}"></button>`).join('')}</div>
          </section>` : ''}

          ${continuar.length ? `
          <section class="secao">
            <div class="secao-topo"><h2>Continue assistindo</h2></div>
            ${continuar.slice(0, 3).map((f, i) => `
              <a class="card" href="#/filme/${f.id}" style="display:flex;gap:18px;align-items:center;padding:12px;margin-bottom:10px">
                <div style="width:110px;aspect-ratio:16/9;border-radius:8px;overflow:hidden;flex-shrink:0;background:var(--surface-raised)">
                  ${f.backdrop ? `<img src="${esc(f.backdrop)}" alt="" style="width:100%;height:100%;object-fit:cover">` : ''}
                </div>
                <div style="flex:1;min-width:0">
                  <span class="tag">${i === 0 ? 'Último assistido' : 'Assistindo'}</span>
                  <b style="display:block">${esc(f.titulo)}</b>
                  <div class="meta">${f.duracao_min ? `Faltam ${Math.max(0, f.duracao_min - f.progresso_min)} min` : `${f.progresso_min} min assistidos`}</div>
                  <div class="progresso"><i style="width:${f.duracao_min ? Math.min(100, (f.progresso_min / f.duracao_min) * 100) : 30}%"></i></div>
                </div>
                <span class="icon-btn" style="background:var(--gold);color:var(--bg-page);border-radius:50%">${icone('play')}</span>
              </a>`).join('')}
          </section>` : ''}

          ${ultimos.length ? `
          <section class="secao">
            <div class="secao-topo"><h2>Últimos assistidos</h2><a class="link" href="#/meus-filmes/assistido">Ver todos</a></div>
            ${carrosselHtml(ultimos.map(filmeCard).join(''), 'Nada assistido ainda.')}
          </section>` : ''}

          <section class="secao">
            <div class="secao-topo"><h2>Recomendados para você</h2><a class="link" href="#/secao/recomendados">Ver todos</a></div>
            ${carrosselHtml(recomendados.map(filmeCard).join(''), 'Sem recomendações por enquanto.')}
          </section>

          <section class="secao">
            <div class="secao-topo"><h2>Em alta esta semana</h2><a class="link" href="#/secao/em-alta">Ver todos</a></div>
            ${carrosselHtml(emAlta.map(filmeCard).join(''), 'Nada em alta agora.')}
          </section>


          ${FILEIRAS_INICIO.map((f) => `
          <section class="secao fileira" data-fileira="${f.chave}">
            <div class="secao-topo"><h2>${esc(f.titulo)}</h2><a class="link" href="${f.link || `#/secao/${f.chave}`}">Ver todos</a></div>
            <div class="carrossel esqueleto">${'<div class="filme-card"><div class="poster"></div></div>'.repeat(8)}</div>
          </section>`).join('')}
        </div>

        <aside class="lateral">
          ${casa ? `
          <section class="card">
            <div class="secao-topo"><h2>Minha casa</h2><a class="link" href="#/casa">Abrir</a></div>
            ${cenaAtiva ? `<a class="item ativo" href="#/casa"><span class="ico">${icone('filmes')}</span><span class="txt">${esc(cenaAtiva.nome)}<small>Ativo</small></span>${icone('seta', 'width="18"')}</a>` : ''}
            ${casa.dispositivos.map((d) => `
              <a class="item" href="#/casa"><span class="ico">${icone(ICONE_DISP[d.tipo])}</span>
              <span class="txt">${esc(d.nome)}<small>${textoDisp(d)}</small></span>${icone('seta', 'width="18"')}</a>`).join('')}
          </section>` : ''}

          <section class="card">
            <div class="secao-topo"><h2>Amigos assistiram</h2></div>
            ${amigos.length ? amigos.map((a) => `
              <div style="display:flex;gap:10px;align-items:center;margin-top:12px">
                <a href="#/perfil/${encodeURIComponent(a.username || '')}" aria-label="Perfil de ${esc(a.nome)}">${avatar(a, 'sm')}</a>
                <div><a href="#/perfil/${encodeURIComponent(a.username || '')}"><b>${esc(a.nome)}</b></a><div class="meta">assistiu <a href="#/filme/${a.tmdb_id}" class="link-perfil">${esc(a.titulo)}</a></div></div>
              </div>`).join('') : vazio('Siga pessoas nas reviews (toque no @) para ver o que elas assistem.')}
          </section>
        </aside>
      </div>
      <div class="inicio-largo"></div>`;

    ligarCarrosseis(raiz);
    ligarLarguraTotal(raiz);
    carregarFileiras(raiz);

    // Carrossel do destaque: troca a cada 7s, ou ao tocar nas bolinhas
    if (destaques.length) {
      let i = 0;
      const mostrar = (n) => {
        i = (n + destaques.length) % destaques.length;
        const f = destaques[i];
        $('#destaque').style.backgroundImage = `url('${f.backdrop}')`;
        ajustarBanner($('#destaque'), f.backdrop);   // verificador: acha onde estão os personagens
        $('#d-titulo').textContent = f.titulo;
        $('#d-linha').innerHTML = `${f.ano || ''} ${f.nota ? `• <span class="estrela">★</span> ${f.nota}` : ''}`;
        $('#d-ver').href = `#/filme/${f.id}`;
        $$('.bolinhas button', raiz).forEach((b, j) => b.classList.toggle('on', j === i));
      };
      $$('.bolinhas button', raiz).forEach((b) => (b.onclick = () => { mostrar(Number(b.dataset.i)); reiniciar(); }));
      $('#add-destaque').onclick = () => abrirAdicionarLista(destaques[i].id);
      let timer;
      const reiniciar = () => { clearInterval(timer); timer = setInterval(() => {
        if (!document.body.contains(raiz) || !$('#destaque')) return clearInterval(timer);
        mostrar(i + 1);
      }, 7000); };
      mostrar(0);
      reiniciar();
    }
  },
};

// Fileiras extras do Início. Cada uma só carrega quando chega perto da tela (não pesa a abertura).
const FILEIRAS_INICIO = [
  { chave: 'comentando', titulo: 'O que a galera está comentando', link: '#/reviews' },
  { chave: 'lancamentos', titulo: 'Lançamentos nos cinemas' },
  { chave: 'em-breve', titulo: 'Em breve' },
  { chave: 'series-em-alta', titulo: 'Séries em alta', link: '#/filmes?tipo=serie' },
  { chave: 'aclamados', titulo: 'Os mais bem avaliados de todos os tempos', link: '#/filmes?tipo=filme&ordem=vote_average.desc' },
  { chave: 'acao', titulo: 'Ação pra prender na cadeira', link: '#/filmes?tipo=filme&genero=28' },
  { chave: 'terror', titulo: 'Terror pra ver de luz apagada', link: '#/filmes?tipo=filme&genero=27' },
  { chave: 'comedia', titulo: 'Comédias pra rir', link: '#/filmes?tipo=filme&genero=35' },
  { chave: 'brasileiros', titulo: 'Cinema brasileiro' },
  { chave: 'series-aclamadas', titulo: 'Séries mais bem avaliadas', link: '#/filmes?tipo=serie&ordem=vote_average.desc' },
  { chave: 'animacao', titulo: 'Animações', link: '#/filmes?tipo=filme&genero=16' },
  { chave: 'ficcao', titulo: 'Ficção científica', link: '#/filmes?tipo=filme&genero=878' },
  { chave: 'curtinhos', titulo: 'Até 1h35: pra uma noite rápida' },
  { chave: 'romance', titulo: 'Romance', link: '#/filmes?tipo=filme&genero=10749' },
  { chave: 'series-brasileiras', titulo: 'Séries brasileiras' },
  { chave: 'anos-90', titulo: 'Clássicos dos anos 90' },
  { chave: 'suspense', titulo: 'Suspense', link: '#/filmes?tipo=filme&genero=53' },
  { chave: 'documentarios', titulo: 'Documentários', link: '#/filmes?tipo=doc' },
];

// As seções que ficam abaixo da coluna da direita (Minha casa / Amigos assistiram) passam a ocupar
// a largura toda da tela, em vez de deixar um vazio à direita.
function ligarLarguraTotal(raiz) {
  const colunas = $('.colunas', raiz);
  const esquerda = colunas?.firstElementChild;
  const lateral = $('.lateral', raiz);
  const largo = $('.inicio-largo', raiz);
  if (!esquerda || !lateral || !largo) return;

  let duasColunas = null;
  const ajustar = () => {
    const agora = getComputedStyle(colunas).gridTemplateColumns.trim().split(/\s+/).length > 1;
    if (agora === duasColunas) return;   // só refaz quando o layout muda (evita resetar a rolagem dos carrosséis)
    duasColunas = agora;
    [...largo.children].forEach((s) => esquerda.appendChild(s));   // volta tudo pra esquerda
    if (!agora) return;                                             // no celular é uma coluna só
    const fimLateral = lateral.getBoundingClientRect().bottom;
    const secoes = [...esquerda.children];
    const i = secoes.findIndex((s) => s.getBoundingClientRect().top >= fimLateral);
    if (i >= 0) secoes.slice(i).forEach((s) => largo.appendChild(s));
  };
  ajustar();

  let t;
  const aoRedimensionar = () => {
    if (!document.body.contains(raiz) || !raiz.contains(colunas)) return window.removeEventListener('resize', aoRedimensionar);
    clearTimeout(t);
    t = setTimeout(ajustar, 150);
  };
  window.addEventListener('resize', aoRedimensionar);
}

function carregarFileiras(raiz) {
  const carregar = async (sec) => {
    const chave = sec.dataset.fileira;
    try {
      const filmes = chave === 'comentando' ? await API.maisComentados() : await API.secao(chave);
      if (!filmes.length) { sec.remove(); return; }
      const card = chave === 'comentando'
        ? (f) => filmeCard({ ...f, ano: `${f.total_reviews} review${f.total_reviews == 1 ? '' : 's'} •` })
        : filmeCard;
      $('.esqueleto', sec).outerHTML = carrosselHtml(filmes.map(card).join(''));
      ligarCarrosseis(sec);
    } catch { sec.remove(); }   // se o TMDB falhar, some só essa fileira
  };
  const secoes = $$('[data-fileira]', raiz);
  if (!window.IntersectionObserver) { secoes.forEach(carregar); return; }
  const obs = new IntersectionObserver((itens) => itens.forEach((i) => {
    if (i.isIntersecting) { obs.unobserve(i.target); carregar(i.target); }
  }), { rootMargin: '400px 0px' });
  secoes.forEach((s) => obs.observe(s));
}

// Página "Ver todos" das fileiras que não têm filtro no Catálogo (#/secao/lancamentos, #/secao/em-breve...)
Paginas.secao = {
  titulo: (rota) => ({ 'em-alta': 'Em alta esta semana', recomendados: 'Recomendados para você' }[rota.partes[0]]
    || (FILEIRAS_INICIO.find((x) => x.chave === rota.partes[0]) || {}).titulo || 'Filmes'),
  async render(raiz, rota) {
    const chave = rota.partes[0];
    const filmes = await carregarSecaoInicio(chave);
    raiz.innerHTML = filmes.length
      ? `<div class="grade grade-catalogo">${filmes.map(filmeCard).join('')}</div>`
      : vazio('Nada por aqui agora.');
    if (typeof ligarColunasCheias === 'function') ligarColunasCheias($('.grade-catalogo', raiz));
  },
};

/** Filmes de uma fileira do Início (o "Ver todos" mostra exatamente aquela categoria). */
async function carregarSecaoInicio(chave) {
  if (chave === 'comentando') return API.maisComentados();
  if (chave === 'em-alta') return API.emAlta();
  if (chave === 'recomendados') {
    const perfil = await API.perfil().catch(() => null);
    const fav = perfil?.favoritos?.[0];
    return fav ? API.recomendados(fav.id).catch(() => API.emAlta()) : (await API.emAlta()).slice(5);
  }
  return API.secao(chave);
}
