// Tela Reviews: feed com filtros + Mais comentados + Suas reviews
window.Paginas = window.Paginas || {};

Paginas.reviews = {
  titulo: 'Reviews',
  async render(raiz, rota) {
    const filtro = rota.query.filtro || 'todas';
    const [lista, top, meu] = await Promise.all([
      API.reviews(filtro),
      API.maisComentados().then((l) => l.slice(0, 4)).catch(() => []),
      API.meuResumo().catch(() => ({ total: 0, media: 0, curtidas: 0 })),
    ]);
    const filtros = { todas: 'Todas', seguindo: 'Amigos', recentes: 'Recentes', nota_alta: 'Nota alta', nota_baixa: 'Nota baixa' };
    const u = Sessao.usuario || {};

    raiz.innerHTML = `
      <div class="colunas">
        <div>
          <div class="chips" style="margin-bottom:16px">
            ${Object.entries(filtros).map(([k, v]) => `<a class="chip roxo ${k === filtro ? 'ativo' : ''}" style="display:inline-flex;align-items:center" href="#/reviews?filtro=${k}">${v}</a>`).join('')}
          </div>
          <form class="escrever" id="rapida">
            ${avatar(u, 'sm')}
            <input id="busca-rev" placeholder="Escreva sua review..." aria-label="Escolher filme para avaliar">
            <button class="icon-btn" style="background:var(--action-primary)" aria-label="Buscar filme">${icone('enviar')}</button>
          </form>
          <div id="feed">${lista.map((r) => reviewCard(r)).join('') || vazio('Nenhuma review ainda. Que tal escrever a primeira?')}</div>
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
    // "Escreva sua review": busca o filme e leva pro primeiro resultado
    $('#rapida').onsubmit = async (e) => {
      e.preventDefault();
      const q = $('#busca-rev').value.trim();
      if (!q) return toast('Digite o nome do filme.');
      try {
        const d = await API.catalogo({ q });
        if (!d.filmes.length) return toast('Filme não encontrado.');
        location.hash = `#/filme/${d.filmes[0].id}/review`;
      } catch (err) { toast(err.message); }
    };
  },
};
