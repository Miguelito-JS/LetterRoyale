// Telas: Perfil (#/perfil ou #/perfil/username?aba=filmes|listas|avaliacoes|estatisticas),
//        Mostrar tudo (#/meus-filmes/assistido?u=username) e Editar perfil (#/editar-perfil)
window.Paginas = window.Paginas || {};

const ABAS_PERFIL = { filmes: 'Filmes', series: 'Séries', listas: 'Listas', avaliacoes: 'Avaliações', estatisticas: 'Estatísticas' };

Paginas.perfil = {
  titulo: 'Perfil',
  async render(raiz, rota) {
    const username = rota.partes[0] || 'me';
    const aba = ABAS_PERFIL[rota.query.aba] ? rota.query.aba : 'filmes';
    const p = await API.perfil(username);
    const s = p.stats;
    const base = `#/perfil${rota.partes[0] ? '/' + encodeURIComponent(rota.partes[0]) : ''}`;

    raiz.innerHTML = `
      <section class="perfil-topo">
        ${avatar(p, 'lg')}
        <div class="perfil-info">
          <h2>${esc(p.nome)}</h2>
          <div class="meta">@${esc(p.username)} • ${p.seguidores} seguidores • ${p.seguindo} seguindo</div>
          ${p.bio ? `<p class="bio">${esc(p.bio)}</p>` : ''}
          ${p.conexoes.length ? `<div class="badges">${p.conexoes.map((c) => `<span class="badge"><i style="background:${esc(c.cor)}"></i>${esc(c.nome)}${c.exibir_status ? ' • status' : ''}</span>`).join('')}</div>` : ''}
        </div>
        <div class="stats">
          <div><b>${s.assistidos}</b><small>Filmes</small></div>
          <div><b>${s.series ?? 0}</b><small>Séries</small></div>
          <div><b>${s.reviews}</b><small>Reviews</small></div>
          <div><b>${s.listas}</b><small>Listas</small></div>
          <div><b>${s.nota_media || '-'}</b><small>Nota média</small></div>
        </div>
        ${p.eu ? '<a class="btn" href="#/editar-perfil">Editar perfil</a>'
               : `<button class="btn ${p.sigo ? '' : 'btn-primario'}" id="seguir">${p.sigo ? 'Seguindo' : 'Seguir'}</button>`}
      </section>



      <div class="chips abas-perfil" role="tablist">
        ${Object.entries(ABAS_PERFIL).map(([k, v]) => `<a class="chip ${k === aba ? 'ativo' : ''}" role="tab" aria-selected="${k === aba}" href="${base}?aba=${k}">${v}</a>`).join('')}
      </div>
      <div id="aba-perfil">${carregando()}</div>`;

    const sg = $('#seguir');
    if (sg) sg.onclick = async () => {
      try { await api(`/usuarios/${encodeURIComponent(p.username)}/seguir`, { metodo: 'POST' }); Paginas.perfil.render(raiz, rota); }
      catch (err) { toast(err.message); }
    };
    const sair = $('#sair-perfil');
    if (sair) sair.onclick = () => sairDaConta();

    const alvo = $('#aba-perfil');
    try { alvo.innerHTML = await ABAS_RENDER[aba](p); }
    catch (err) { alvo.innerHTML = erroBox(err); }
    ligarCarrosseis(alvo);
    ligarReviews(alvo);
  },
};

const ABAS_RENDER = {
  // Favoritos + prateleiras: Quero assistir / Assistindo / Assistidos / Abandonados
  async series(p) {
    return ABAS_RENDER.filmes(p, true);
  },

  async filmes(p, serie = false) {
    const u = encodeURIComponent(p.username);
    const nomeItem = serie ? 'uma série' : 'um filme';
    const prats = (serie ? p.prateleiras_series : p.prateleiras) || {};
    const favs = (serie ? p.favoritos_series : p.favoritos) || [];
    const prateleira = (status) => {
      const pr = prats[status] || { total: 0, filmes: [] };
      const s = STATUS_FILME[status];
      if (status === 'abandonado' && !pr.total) return '';
      return `
        <section class="secao">
          <div class="secao-topo">
            <h2>${s.nome} <span class="contador">${icone(s.ico, 'width="18"')} ${pr.total}</span></h2>
            ${pr.total ? `<a class="link" href="#/meus-filmes/${status}?u=${u}${serie ? '&tipo=serie' : ''}">Mostrar tudo</a>` : ''}
          </div>
          ${carrosselHtml(pr.filmes.map(filmeCardComNota).join(''),
            p.eu ? `Nada aqui ainda. Abra ${nomeItem} e toque em "Adicionar à" → ${s.nome}.` : 'Nada aqui ainda.')}
        </section>`;
    };
    return `
      <section class="secao">
        <div class="secao-topo"><h2>Favoritos</h2></div>
        ${carrosselHtml(favs.map(filmeCard).join(''), `Toque em ❤ Favoritar em ${nomeItem} para ${serie ? 'ela' : 'ele'} aparecer aqui.`)}
      </section>
      ${['quero_assistir', 'assistindo', 'assistido', 'abandonado'].map(prateleira).join('')}`;
  },

  async listas(p) {
    if (!p.listas.length) return vazio('Nenhuma lista ainda.');
    return `<div class="grade-listas">${p.listas.map((l) => `
      <a class="lista-mini" href="#/lista/${l.id}">
        <span class="ico">${icone(l.icone)}</span>
        <span style="flex:1;min-width:0"><b>${esc(l.nome)}</b><div class="meta">${l.total} filmes</div></span>
        ${icone('seta', 'width="18"')}
      </a>`).join('')}</div>`;
  },

  async avaliacoes(p) {
    const lista = await API.reviewsDoUsuario(p.username);
    return lista.length ? `<div class="secao">${lista.map((r) => reviewCard(r)).join('')}</div>` : vazio('Nenhuma review ainda.');
  },

  async estatisticas(p) {
    const e = await API.estatisticas(p.username);
    // Últimos 12 meses (preenche os meses sem filme com 0)
    const meses = [];
    const hoje = new Date();
    for (let i = 11; i >= 0; i--) {
      const d = new Date(hoje.getFullYear(), hoje.getMonth() - i, 1);
      const chave = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
      const achado = e.por_mes.find((m) => m.mes === chave);
      meses.push({ rotulo: d.toLocaleDateString('pt-BR', { month: 'short' }).replace('.', ''), total: achado ? Number(achado.total) : 0 });
    }
    const max = Math.max(1, ...meses.map((m) => m.total));
    const notas = [0.5, 1, 1.5, 2, 2.5, 3, 3.5, 4, 4.5, 5].map((n) => ({ n, total: Number((e.notas.find((x) => Number(x.nota) === n) || {}).total || 0) }));
    const maxN = Math.max(1, ...notas.map((x) => x.total));
    return `
      <div class="cards-stats secao">
        <div class="card"><b>${e.filmes}</b><small>filmes assistidos</small></div>
        <div class="card"><b>${e.horas}h</b><small>de filme</small></div>
        <div class="card"><b>${e.este_ano}</b><small>em ${hoje.getFullYear()}</small></div>
        <div class="card"><b>${e.nota_media || '-'}</b><small>nota média</small></div>
      </div>
      <section class="card secao">
        <h2 style="font-size:18px;margin-bottom:16px">Filmes por mês</h2>
        <div class="barras" role="img" aria-label="Filmes assistidos por mês nos últimos 12 meses">
          ${meses.map((m) => `<div class="barra" title="${m.total} filme(s)"><span style="height:${(m.total / max) * 100}%"></span><small>${m.rotulo}</small><em>${m.total || ''}</em></div>`).join('')}
        </div>
      </section>
      <section class="card secao">
        <h2 style="font-size:18px;margin-bottom:16px">Suas notas</h2>
        <div class="barras notas" role="img" aria-label="Quantidade de filmes por nota">
          ${notas.map((x) => `<div class="barra" title="${x.total} filme(s) com nota ${x.n}"><span style="height:${(x.total / maxN) * 100}%"></span><small>${x.n % 1 ? '½' : x.n + '★'}</small><em>${x.total || ''}</em></div>`).join('')}
        </div>
      </section>`;
  },
};

// "Mostrar tudo" de uma prateleira (igual a tela "Zerados" do app de jogos)
Paginas['meus-filmes'] = {
  titulo: (rota) => (STATUS_FILME[rota.partes[0]] || STATUS_FILME.assistido).nome,
  async render(raiz, rota) {
    const status = STATUS_FILME[rota.partes[0]] ? rota.partes[0] : 'assistido';
    const u = rota.query.u || 'me';
    const serie = rota.query.tipo === 'serie';
    const filmes = await API.filmesDoUsuario(u, status, serie ? 'serie' : '');
    const extra = serie ? '&tipo=serie' : '';
    raiz.innerHTML = `
      <div style="display:flex;align-items:center;gap:10px;margin-bottom:18px">
        <a class="icon-btn" href="#/perfil${u === 'me' ? '' : '/' + encodeURIComponent(u)}${serie ? '?aba=series' : ''}" aria-label="Voltar ao perfil">${icone('voltar')}</a>
        <div class="chips" style="flex:1;min-width:0">
          ${Object.entries(STATUS_FILME).map(([k, s]) => `<a class="chip ${k === status ? 'ativo dourado' : ''}" href="#/meus-filmes/${k}?u=${encodeURIComponent(u)}${extra}">${s.nome}</a>`).join('')}
        </div>
      </div>
      <p class="meta" style="margin-bottom:14px">${filmes.length} ${serie ? 'série(s)' : 'filme(s)'}</p>
      ${filmes.length ? `<div class="grade grade-prateleira">${filmes.map(filmeCardComNota).join('')}</div>` : vazio(serie ? 'Nenhuma série aqui ainda.' : 'Nenhum filme aqui ainda.')}`;
  },
};

// Editar perfil: nome, @usuário, telefone, bio, foto + trocar senha
Paginas['editar-perfil'] = {
  titulo: 'Editar perfil',
  async render(raiz) {
    const u = await api('/auth/me');
    raiz.innerHTML = `
      <div class="colunas-form">
        <form class="card form-perfil" id="f-dados">
          <h2>Seus dados</h2>
          <div class="avatar-edit">${avatar(u, 'lg')}
            <div>
              <div class="meta" style="margin-bottom:10px">Cor de fundo da foto</div>
              <div class="cores-avatar" role="radiogroup" aria-label="Cor de fundo da foto">
                ${CORES_AVATAR.map((c) => `<button type="button" role="radio" class="cor-bolinha ${(u.avatar_cor || '').toLowerCase() === c ? 'sel' : ''}" data-cor="${c}" style="background:${c}" aria-label="Cor ${c}" aria-checked="${(u.avatar_cor || '').toLowerCase() === c}"></button>`).join('')}
                <button type="button" class="cor-bolinha outra" id="cor-outra" title="Outra cor" aria-label="Escolher outra cor" aria-haspopup="dialog">+</button>
                <button type="button" class="link" id="cor-auto" style="margin-left:4px">Automática</button>
              </div>
            </div>
          </div>
          <label>Nome<input class="campo" id="nome" maxlength="100" required value="${esc(u.nome)}"></label>
          <label>Nome de usuário
            <span class="input-prefixo"><span>@</span><input class="campo" id="username" maxlength="30" required pattern="[A-Za-z0-9_.]{3,30}" value="${esc(u.username)}"></span>
            <small class="meta">3 a 30 letras, números, _ ou ponto.</small>
          </label>
          <label>E-mail<input class="campo" id="email" type="email" maxlength="100" required autocomplete="email" value="${esc(u.email || '')}"></label>
          <label id="caixa-senha-email" hidden>Senha atual (para trocar o e-mail)<input class="campo" id="senha-email" type="password" autocomplete="current-password"></label>
          <label>Telefone<input class="campo" id="telefone" type="tel" inputmode="tel" placeholder="(11) 91234-5678" value="${esc(u.telefone || '')}"></label>
          <label>Bio<textarea class="campo" id="bio" maxlength="200" style="min-height:90px">${esc(u.bio || '')}</textarea></label>
          <div class="foto-campo">
            <span class="rotulo-campo">Foto do perfil</span>
            <div class="foto-botoes">
              <label class="btn btn-roxo" for="foto-arquivo">${icone('mais')} Enviar foto</label>
              <input type="file" id="foto-arquivo" accept="image/png,image/jpeg,image/webp" class="sr-only">
              <button type="button" class="btn" id="foto-remover" ${u.avatar_url ? '' : 'hidden'}>${icone('x')} Remover foto</button>
            </div>
            <small class="meta">JPG, PNG ou WEBP. A foto é cortada em quadrado.</small>
            <details class="foto-link" ${u.avatar_url && !u.avatar_url.startsWith('/api/') ? 'open' : ''}>
              <summary>Prefiro colar um link</summary>
              <input class="campo" id="avatar_url" type="url" placeholder="https://..." value="${esc(u.avatar_url || '')}" aria-label="Link da foto (URL)">
            </details>
          </div>
          <p class="erro" id="erro1"></p>
          <div class="acoes"><a class="btn" href="#/perfil">Cancelar</a><button class="btn btn-primario">Salvar</button></div>
        </form>

        <form class="card form-perfil" id="f-senha">
          <h2>Trocar senha</h2>
          <label>Senha atual<input class="campo" id="atual" type="password" autocomplete="current-password" required></label>
          <label>Nova senha<input class="campo" id="nova" type="password" autocomplete="new-password" minlength="6" required></label>
          <label>Confirmar a nova senha<input class="campo" id="nova2" type="password" autocomplete="new-password" minlength="6" required></label>
          <p class="erro" id="erro2"></p>
          <div class="acoes"><button class="btn btn-roxo">Trocar senha</button></div>
          <p class="meta linha-unica" style="margin-top:10px">Esqueceu a senha atual? <a class="link" href="#/recuperar" id="esqueci">Recuperar por e-mail</a></p>
        </form>
      </div>`;

    // Cor de fundo da foto: mostra na hora no avatar e só grava quando clicar em Salvar
    let corEscolhida = u.avatar_cor || null;
    const pintar = (cor) => {
      corEscolhida = cor;
      $('.avatar-edit .avatar').style.background = cor || corAvatar(u.nome);
      $$('.cor-bolinha[data-cor]').forEach((b) => { const on = b.dataset.cor === cor; b.classList.toggle('sel', on); b.setAttribute('aria-checked', on); });
    };
    $$('.cor-bolinha[data-cor]').forEach((b) => (b.onclick = () => pintar(b.dataset.cor)));
    $('#cor-outra').onclick = () => abrirSeletorCor($('#cor-outra'), corEscolhida || corAvatar(u.nome), { aoMudar: pintar, aoEscolher: pintar });
    $('#cor-auto').onclick = () => pintar(null);
    // A foto nova aparece no círculo assim que colar o link
    const mostrarFoto = (url) => {
      $('.avatar-edit .avatar').innerHTML = url ? fotoAvatar(url, $('#nome').value) : inicial($('#nome').value);
      $('#foto-remover').hidden = !url;
    };
    $('#avatar_url').onchange = () => mostrarFoto($('#avatar_url').value.trim());

    // Enviar foto: corta em quadrado, diminui pra 320x320 e manda pro servidor
    $('#foto-arquivo').onchange = async (e) => {
      const arq = e.target.files[0];
      e.target.value = '';
      if (!arq) return;
      if (!/^image\/(png|jpe?g|webp)$/.test(arq.type)) { toast('Escolha uma imagem JPG, PNG ou WEBP.'); return; }
      if (arq.size > 15 * 1024 * 1024) { toast('Imagem muito grande (máx. 15 MB).'); return; }
      try {
        const dataUrl = await reduzirFoto(arq, 320);
        mostrarFoto(dataUrl);
        const r = await API.enviarFoto(dataUrl);
        $('#avatar_url').value = r.avatar_url;
        Sessao.salvar(Sessao.token, { ...Sessao.usuario, avatar_url: r.avatar_url });
        const fotoTopo = $('#menu-foto .avatar');
        if (fotoTopo) fotoTopo.innerHTML = fotoAvatar(r.avatar_url, u.nome);
        toast('Foto atualizada!');
      } catch (err) { toast(err.message || 'Não consegui enviar a foto.'); mostrarFoto($('#avatar_url').value.trim()); }
    };
    $('#foto-remover').onclick = async () => {
      try {
        if (($('#avatar_url').value || '').startsWith('/api/')) await API.removerFoto();
        $('#avatar_url').value = '';
        mostrarFoto('');
        Sessao.salvar(Sessao.token, { ...Sessao.usuario, avatar_url: null });
        toast('Foto removida. Clique em Salvar se quiser manter assim.');
      } catch (err) { toast(err.message); }
    };

    // Máscara simples de telefone: (11) 91234-5678
    $('#telefone').oninput = (e) => {
      const d = e.target.value.replace(/\D/g, '').slice(0, 11);
      e.target.value = d.length > 6 ? `(${d.slice(0, 2)}) ${d.slice(2, d.length - 4)}-${d.slice(-4)}` : d.length > 2 ? `(${d.slice(0, 2)}) ${d.slice(2)}` : d;
    };
    $('#esqueci').onclick = () => { Sessao.sair(); };

    const usuarioOk = verificarDisponivel($('#username'), 'username');
    const emailOk = verificarDisponivel($('#email'), 'email');
    // Mudou o e-mail -> aparece o campo de senha atual (e ele vira obrigatório)
    const emailOriginal = (u.email || '').toLowerCase();
    $('#email').addEventListener('input', () => {
      const mudou = $('#email').value.trim().toLowerCase() !== emailOriginal;
      $('#caixa-senha-email').hidden = !mudou;
      $('#senha-email').required = mudou;
      if (!mudou) $('#senha-email').value = '';
    });
    $('#f-dados').onsubmit = async (e) => {
      e.preventDefault();
      $('#erro1').textContent = '';
      if (!usuarioOk()) { $('#erro1').textContent = 'Esse @ já é de outra pessoa.'; $('#username').focus(); return; }
      if (!emailOk()) { $('#erro1').textContent = 'Esse e-mail já é de outra conta.'; $('#email').focus(); return; }
      try {
        const r = await API.editarPerfil({
          nome: $('#nome').value, username: $('#username').value, email: $('#email').value.trim(), senha_atual: $('#senha-email').value || undefined, telefone: $('#telefone').value,
          bio: $('#bio').value, avatar_url: $('#avatar_url').value.trim() || null, avatar_cor: corEscolhida,
        });
        Sessao.salvar(r.token, r.usuario);   // token novo (o @usuário pode ter mudado)
        toast('Perfil atualizado!');
        location.hash = '#/perfil';
      } catch (err) { $('#erro1').textContent = err.message; }
    };
    $('#f-senha').onsubmit = async (e) => {
      e.preventDefault();
      $('#erro2').textContent = '';
      if ($('#nova').value !== $('#nova2').value) { $('#erro2').textContent = 'As senhas novas não são iguais.'; return; }
      try {
        await API.trocarSenha($('#atual').value, $('#nova').value);
        toast('Senha alterada!');
        e.target.reset();
      } catch (err) { $('#erro2').textContent = err.message; }
    };
  },
};

// Lê a imagem, corta o meio em quadrado e devolve um JPG pequeno (data:image/jpeg;base64,...)
function reduzirFoto(arquivo, lado) {
  return new Promise((ok, erro) => {
    const img = new Image();
    const url = URL.createObjectURL(arquivo);
    img.onload = () => {
      const menor = Math.min(img.naturalWidth, img.naturalHeight);
      const c = document.createElement('canvas');
      c.width = c.height = lado;
      c.getContext('2d').drawImage(img, (img.naturalWidth - menor) / 2, (img.naturalHeight - menor) / 2, menor, menor, 0, 0, lado, lado);
      URL.revokeObjectURL(url);
      ok(c.toDataURL('image/jpeg', 0.86));
    };
    img.onerror = () => { URL.revokeObjectURL(url); erro(new Error('Não consegui abrir essa imagem.')); };
    img.src = url;
  });
}

// Cores prontas pro fundo da foto (paleta do site + umas extras)
const CORES_AVATAR = ['#7478f2', '#e6b82e', '#f27474', '#2ecc71', '#4fb3e8', '#b06ff2', '#ff8a3d', '#f2f2f5', '#151a63', '#111111'];
