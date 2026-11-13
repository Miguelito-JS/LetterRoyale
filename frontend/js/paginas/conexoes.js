// Tela Conexões (#/conexoes) — Netflix, Prime Video, HBO Max... (layout novo do Figma)
window.Paginas = window.Paginas || {};

// Logo da plataforma: usa frontend/img/plataformas/<slug>.png se existir;
// se a imagem não existir, mostra o nome em cima da cor da marca.
function logoPlataforma(p, cls = '') {
  const claro = ['#FFF200', '#FFE600', '#FFC400'].includes(String(p.cor).toUpperCase());
  return `<span class="plataforma ${cls}" style="background:${esc(p.cor)};color:${claro ? '#111' : '#fff'}">
      <b>${esc(p.nome)}</b>
      <img src="/img/plataformas/${esc(p.slug)}.png" alt="" loading="lazy" onerror="this.remove()">
    </span>`;
}

Paginas.conexoes = {
  titulo: 'Conexões',
  async render(raiz) {
    const [plataformas, conexoes] = await Promise.all([API.plataformas(), API.conexoes()]);
    const nome = Sessao.usuario?.nome || 'você';
    const livres = plataformas.filter((p) => !p.conectada);

    raiz.innerHTML = `
      <p class="sinopse" style="margin-top:-12px;margin-bottom:20px">Conecte seus serviços de streaming para ver onde assistir cada filme e mostrar no seu perfil o que você está assistindo.</p>
      <div class="colunas">
        <div>
          <section>
            <div class="secao-topo"><h2>Minhas conexões</h2><span class="meta">${conexoes.length} ${conexoes.length === 1 ? 'conexão' : 'conexões'}</span></div>
            ${conexoes.map((c) => `
              <article class="conexao">
                ${logoPlataforma({ nome: c.plataforma, cor: c.cor, slug: c.slug })}
                <div><b style="font-size:18px">${esc(c.plataforma)}</b><div class="meta">Conectado como ${esc(c.nome_conta || nome)}</div></div>
                <button class="link-perigo" data-remover="${c.id}">Desconectar</button>
                <div class="opcoes">
                  <label>Exibir no perfil
                    <span class="switch"><input type="checkbox" data-toggle="exibir_no_perfil" data-id="${c.id}" ${c.exibir_no_perfil ? 'checked' : ''}><span></span></span></label>
                  <label>Mostrar o que estou assistindo como status
                    <span class="switch"><input type="checkbox" data-toggle="exibir_status" data-id="${c.id}" ${c.exibir_status ? 'checked' : ''}><span></span></span></label>
                </div>
              </article>`).join('') || vazio('Nenhuma conta conectada ainda. Escolha um serviço abaixo.')}
          </section>

          <section class="secao">
            <h2 style="font-size:22px">Adicionar nova conexão</h2>
            <p class="meta" style="margin:6px 0 16px">Escolha um serviço para conectar à sua conta.</p>
            <div class="plataformas">
              ${livres.map((p) => `<button data-plat="${p.id}" aria-label="Conectar ${esc(p.nome)}" title="${esc(p.nome)}">${logoPlataforma(p)}</button>`).join('')}
            </div>
          </section>
        </div>

        <aside class="lateral">
          <section class="card">
            <h2 style="font-size:22px;margin-bottom:16px">Por que conectar?</h2>
            ${[['filmes', 'Onde assistir', 'Veja onde cada filme está.'],
               ['perfil', 'Status no perfil', 'Mostre o que está assistindo.'],
               ['escudo', 'Você no controle', 'Desconecte quando quiser.']]
              .map(([ico, t, d]) => `<div class="motivo"><span class="ico">${icone(ico)}</span><div><b>${t}</b><p class="meta">${d}</p></div></div>`).join('')}
          </section>
        </aside>
      </div>`;

    const recarregar = () => Paginas.conexoes.render(raiz);

    $$('[data-plat]', raiz).forEach((b) => (b.onclick = async () => {
      const p = plataformas.find((x) => String(x.id) === b.dataset.plat);
      const conta = await perguntar(`Conectar ${p.nome}`, 'Nome da sua conta / perfil');
      if (!conta) return;
      try { await API.conectar(p.id, conta); toast(`${p.nome} conectado!`); recarregar(); }
      catch (err) { toast(err.message); }
    }));
    $$('[data-toggle]', raiz).forEach((i) => (i.onchange = async () => {
      try { await API.atualizarConexao(i.dataset.id, { [i.dataset.toggle]: i.checked }); }
      catch (err) { toast(err.message); }
    }));
    $$('[data-remover]', raiz).forEach((b) => (b.onclick = async () => {
      const nome = b.closest('.conexao')?.querySelector('h3, b, strong')?.textContent?.trim();
      if (!(await confirmar({ titulo: `Desconectar${nome ? ' ' + nome : ''}?`, texto: 'Ela sai do seu perfil. Você pode conectar de novo quando quiser.', botao: 'Desconectar' }))) return;
      try { await API.desconectar(b.dataset.remover); recarregar(); }
      catch (err) { toast(err.message); }
    }));
  },
};
