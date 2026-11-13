// Telas: Minhas listas (#/listas) e Minha lista — X (#/lista/ID)
window.Paginas = window.Paginas || {};

Paginas.listas = {
  titulo: 'Minhas listas',
  async render(raiz, rota) {
    const aba = rota.query.aba || 'todas';
    const listas = await API.listas(aba);
    const abas = { todas: 'Todas', minhas: 'Criadas por mim', salvas: 'Salvas' };

    raiz.innerHTML = `
      <div class="barra-ferramentas">
        <div class="chips" style="flex:1;min-width:0">
          ${Object.entries(abas).map(([k, v]) => `<a class="chip roxo ${k === aba ? 'ativo' : ''}" style="display:inline-flex;align-items:center" href="#/listas?aba=${k}">${v}</a>`).join('')}
        </div>
        <button class="btn btn-roxo btn-nova" id="nova" aria-label="Nova lista">${icone('mais')}<span>Nova lista</span></button>
      </div>
      <div class="grade" style="grid-template-columns:repeat(auto-fill,minmax(210px,1fr))">
        ${listas.map((l) => `
          <a class="lista-card" href="#/lista/${l.id}">
            <div class="mosaico">${l.capas.filter(Boolean).map((c) => `<img src="${esc(c)}" alt="" loading="lazy">`).join('') || `<span style="color:var(--text-muted)">${icone('lista', 'width="32"')}</span>`}</div>
            <div class="rodape">
              <div><h3>${esc(l.nome)}</h3><div class="meta">${l.total} filmes${l.minha ? '' : ` • @${esc(l.dono)}`}</div></div>
              ${l.minha ? `<button class="icon-btn" style="width:28px;height:28px;background:none" data-apagar="${l.id}" aria-label="Apagar lista">${icone('pontos')}</button>` : ''}
            </div>
          </a>`).join('') || vazio('Nenhuma lista aqui.')}
      </div>`;

    $('#nova').onclick = async () => {
      const nome = await perguntar('Nova lista', 'Ex: Filmes de domingo');
      if (!nome) return;
      try { const r = await API.criarLista({ nome }); location.hash = `#/lista/${r.id}`; }
      catch (err) { toast(err.message); }
    };
    $$('[data-apagar]', raiz).forEach((b) => (b.onclick = async (e) => {
      e.preventDefault();
      const nome = b.closest('.lista-card, a, article, div')?.querySelector('h3, .titulo, b')?.textContent?.trim();
      if (!(await confirmar({ titulo: 'Apagar lista?', texto: `A lista${nome ? ` "${nome}"` : ''} e os filmes dentro dela serão removidos. Isso não pode ser desfeito.`, botao: 'Apagar' }))) return;
      try { await API.apagarLista(b.dataset.apagar); toast('Lista apagada'); Paginas.listas.render(raiz, rota); }
      catch (err) { toast(err.message); }
    }));
  },
};

Paginas.lista = {
  titulo: 'Minha lista',
  async render(raiz, rota) {
    const l = await API.lista(rota.partes[0]);
    document.title = `${l.nome} · LetterRoyale`;
    $('.topbar h1').textContent = l.nome;

    raiz.innerHTML = `
      <div style="display:flex;align-items:center;gap:12px;margin-bottom:20px;flex-wrap:wrap">
        <a class="btn" href="#/listas">${icone('voltar')} Listas</a>
        <span class="meta">${l.filmes.length} filmes • por @${esc(l.dono)}</span>
        ${l.minha ? `<a class="btn btn-roxo" href="#/filmes" style="margin-left:auto">${icone('mais')} Adicionar filmes</a>` : ''}
      </div>
      ${l.filmes.length ? `<div class="grade">${l.filmes.map((f) => `
        <div style="position:relative">
          ${filmeCard(f)}
          ${l.minha ? `<button class="icon-btn" data-tirar="${f.id}" aria-label="Remover da lista" style="position:absolute;top:8px;right:8px;width:32px;height:32px;background:rgba(7,10,51,.8)">${icone('x')}</button>` : ''}
        </div>`).join('')}</div>`
        : vazio('Lista vazia. Abra um filme e toque em "Adicionar à lista".')}`;

    $$('[data-tirar]', raiz).forEach((b) => (b.onclick = async () => {
      try { await API.tirarDaLista(l.id, b.dataset.tirar); b.parentElement.remove(); toast('Removido da lista'); }
      catch (err) { toast(err.message); }
    }));
  },
};
