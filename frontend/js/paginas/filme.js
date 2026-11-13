// Telas: Detalhes do filme (#/filme/ID), Escrever review (#/filme/ID/review), Avaliações (#/filme/ID/avaliacoes)
window.Paginas = window.Paginas || {};

const ehSerie = (f) => f.tipo === 'serie' || Number(f.id) < 0;
const temporadasTxt = (n) => (n ? `${n} temporada${n > 1 ? 's' : ''}` : '');

function cabecalhoFilme(f) {
  const serie = ehSerie(f);
  return `
    <section class="banner" style="background-image:url('${esc(f.backdrop || '')}');min-height:280px">
      <div class="detalhe-topo">
        <div class="capa">${poster(f)}</div>
        <div>
          <h2>${esc(f.titulo)}</h2>
          <div class="meta info-filme" style="color:var(--text-primary);font-size:15px;flex-wrap:wrap">
            ${f.documentario || (f.generos || []).includes('Documentário') ? `<span class="selo-tipo doc">${serie ? 'Série documental' : 'Documentário'}</span>` : serie ? '<span class="selo-tipo">Série</span>' : ''}
            ${[f.ano, serie ? temporadasTxt(f.total_temporadas) : duracao(f.duracao_min), f.generos[0], f.classificacao && (f.classificacao === 'L' ? 'Livre' : `${f.classificacao} anos`)].filter(Boolean).map(esc).join(' • ')}
          </div>
          <div class="nota-grande">
            <span class="estrela">★</span> <b>${f.nota ?? '-'}</b> /10
            <span>(${(f.votos_tmdb || 0).toLocaleString('pt-BR')} avaliações)</span>
            ${f.comunidade.total ? `<span>• LetterRoyale: ★ ${f.comunidade.media} (${f.comunidade.total})</span>` : ''}
          </div>
          <div class="acoes">
            <button class="btn ${f.meu.status ? 'btn-primario' : ''}" id="b-status">${botaoStatusHtml(f.meu.status)}</button>
            <button class="btn so-icone-celular ${f.meu.favorito ? 'ativo' : ''}" id="b-fav" aria-label="Favoritar">${icone('coracao')} <span class="rotulo">Favoritar</span></button>
            ${naoEstreou(f) ? '' : `<a class="btn so-icone-celular" href="#/filme/${f.id}/review" aria-label="${f.meu.minha_review ? 'Editar review' : 'Escrever review'}">${icone('lapis')} <span class="rotulo">${f.meu.minha_review ? 'Editar review' : 'Escrever review'}</span></a>`}
            ${serie ? '' : `<button class="btn so-icone-celular" id="b-fila" hidden aria-label="Colocar na fila">${icone('mais')} <span class="rotulo">Colocar na fila</span></button>`}
            ${naoEstreou(f)
              ? `<button class="btn btn-estreia" disabled>${icone('relogio')} Estreia em ${dataBR(f.lancamento)}</button>`
              : serie
                ? `<button class="btn btn-roxo" id="b-temporadas">${icone('listas')} Ver temporadas</button>`
                : `<button class="btn btn-roxo" id="b-assistir">${icone('play')} Assistir agora</button>`}
          </div>
          <div class="meta" id="cores-poster" style="margin-top:12px"></div>
        </div>
      </div>
    </section>`;
}

function ligarBotoesFilme(f) {
  // Fora da tela de detalhes (review, avaliações): "Ver temporadas" leva pros detalhes já na aba Temporadas
  const bTemp = $('#b-temporadas');
  if (bTemp && !bTemp.onclick) bTemp.onclick = () => { location.hash = `#/filme/${f.id}?aba=temporadas`; };
  // Botão de status (Quero assistir / Assistindo / Assistido / Abandonado) — igual ao app de jogos
  $('#b-status').onclick = () => abrirStatus(f, f.meu.status, (novo) => {
    f.meu.status = novo;
    const b = $('#b-status');
    if (b) { b.innerHTML = botaoStatusHtml(novo); b.classList.toggle('btn-primario', !!novo); }
  });
  $('#b-fav').onclick = async (e) => {
    const b = e.currentTarget;
    try { const r = await API.favorito(f.id); b.classList.toggle('ativo', r.favorito); toast(r.favorito ? 'Adicionado aos favoritos ❤' : 'Removido dos favoritos'); }
    catch (err) { toast(err.message); }
  };

  // Série: sem fila nem Modo Cinema (são só pra filme)
  let cine = null;
  const btnFila = $('#b-fila');
  if (!btnFila) return ligarCoresPoster(f);
  // 🎬 Fila: se já está assistindo OUTRO filme, aparece "Colocar na fila"
  const mostrarFila = async () => {
    try { cine = await API.cinema(); } catch { return; }
    const assistindoOutro = cine.ativo && cine.modo === 'assistindo' && cine.filme && cine.filme.id !== f.id;
    if (!assistindoOutro) { btnFila.hidden = true; return; }
    const lista = await API.fila().catch(() => []);
    const naFila = lista.some((x) => x.id === f.id);
    btnFila.hidden = false;
    btnFila.disabled = naFila;
    btnFila.innerHTML = naFila ? `${icone('check')} <span class="rotulo">Na fila (${lista.findIndex((x) => x.id === f.id) + 1}º)</span>` : `${icone('mais')} <span class="rotulo">Colocar na fila</span>`;
  };
  if (!naoEstreou(f)) mostrarFila();
  btnFila.onclick = async () => {
    try {
      const lista = await API.entrarFila(f.id);
      toast(`Na fila! ${f.titulo} começa depois de ${cine.filme.titulo}${lista.length > 1 ? ` (é o ${lista.length}º)` : ''}.`);
      mostrarFila();
    } catch (err) { toast(err.message); }
  };

  // 🎬 Assistir agora: Modo Cinema travado neste filme até acabar
  if ($('#b-assistir')) $('#b-assistir').onclick = async () => {
    if (cine?.ativo && cine.modo === 'assistindo' && cine.filme && cine.filme.id !== f.id) {
      const trocar = await confirmar({ titulo: 'Trocar de filme?', texto: `Você está assistindo ${cine.filme.titulo}. Quer parar e começar ${f.titulo} agora? Se preferir, use "Colocar na fila".`, botao: 'Trocar agora' });
      if (!trocar) return;
    }
    try {
      await API.cinemaIniciar({ tmdb_id: f.id, modo: 'assistindo', ritmo: 'normal' });
      toast(`🎬 Modo Cinema ligado: bom filme! A luz acompanha ${f.titulo} por ${duracao(f.duracao_min) || '2h'}.`);
      mostrarFila();
    } catch (err) { toast(err.message); }
  };

  ligarCoresPoster(f);
}

function ligarCoresPoster(f) {
  // Mostra as cores do pôster (as mesmas que a lâmpada usa)
  API.paleta(f.id).then((cores) => {
    const box = $('#cores-poster');
    if (box && cores.length) box.innerHTML = `Cores do pôster ${paletaHtml(cores)}`;
  }).catch(() => {});

  // Modo Cinema "navegando": ficou 2s na página -> a lâmpada pega as cores deste filme
  const rotaAtual = location.hash;
  setTimeout(() => {
    if (location.hash === rotaAtual) API.cinemaFoco(f.id).catch(() => {});
  }, 2000);
}

function lateralAvaliacoes(f, reviews) {
  return `
    <aside class="lateral" id="lat">
      <section>
        <div class="secao-topo"><h2>Avaliações</h2><a class="link" href="#/filme/${f.id}/avaliacoes">Ver todas</a></div>
        ${reviews.length ? `<div class="card grafico-lateral"><div class="media-lateral"><b>${(reviews.reduce((t, r) => t + r.nota, 0) / reviews.length).toFixed(1)}</b><span class="meta">${reviews.length} review${reviews.length > 1 ? 's' : ''}</span></div>${graficoNotas(reviews)}</div>` : ''}
        ${reviews.slice(0, 3).map((r) => reviewCard(r, { mostrarFilme: false })).join('') || vazio('Ninguém avaliou ainda. Seja o primeiro!')}
      </section>
    </aside>`;
}

// #/serie/1399 -> mesma tela, com o id negativo que o site usa para séries
Paginas.serie = {
  titulo: 'Série',
  render() { location.replace(`#/filme/${-Math.abs(Number(lerRota().partes[0]))}`); },
};

Paginas.filme = {
  titulo: (rota) => ({ review: 'Escrever review', avaliacoes: 'Avaliações' }[rota.partes[1]] || (Number(rota.partes[0]) < 0 ? 'Detalhes da série' : 'Detalhes do filme')),
  async render(raiz, rota) {
    const id = Number(rota.partes[0]);
    const sub = rota.partes[1];
    const [f, reviews] = await Promise.all([API.filme(id), API.reviewsDoFilme(id).catch(() => [])]);
    document.title = `${f.titulo} · LetterRoyale`;

    if (sub === 'review' && naoEstreou(f)) {
      raiz.innerHTML = cabecalhoFilme(f) + vazio(`${ehSerie(f) ? 'Essa série' : 'Esse filme'} ainda não estreou. Dá pra avaliar a partir de ${dataBR(f.lancamento)}.`);
      return ligarBotoesFilme(f);
    }
    if (sub === 'review') return telaEscrever(raiz, f, reviews);
    if (sub === 'avaliacoes') return telaAvaliacoes(raiz, f, reviews);

    raiz.innerHTML = `
      ${cabecalhoFilme(f)}
      <div class="colunas secao">
        <div>
          <div class="abas" role="tablist">
            <button class="ativo" data-aba="info">Informações</button>
            ${ehSerie(f) && f.temporadas?.length ? '<button data-aba="temporadas">Temporadas</button>' : ''}
            <button data-aba="elenco">Elenco</button>
            <button data-aba="onde">Onde assistir</button>
            <button data-aba="semelhantes">Semelhantes</button>
          </div>
          <div id="aba"></div>
        </div>
        ${lateralAvaliacoes(f, reviews)}
      </div>`;

    const abas = {
      info: () => `
        <h3 style="margin-bottom:10px">Sinopse</h3>
        <p class="sinopse">${esc(f.sinopse || 'Sem sinopse em português.')}</p>
        <div class="infos secao" style="margin-top:20px">
          <div><small>${ehSerie(f) ? 'Criação' : 'Direção'}</small>${esc(f.direcao.join(', ') || '-')}</div>
          <div><small>Elenco</small>${esc(f.elenco.slice(0, 2).map((e) => e.nome).join(', ') || '-')}</div>
          <div><small>Gênero</small>${esc(f.generos.slice(0, 2).join(', ') || '-')}</div>
          ${ehSerie(f) ? `
          <div><small>Temporadas</small>${f.total_temporadas || '-'} (${f.total_episodios || 0} episódios)</div>
          <div><small>Cada episódio</small>${duracao(f.duracao_min) || '-'}</div>
          <div><small>Estreia</small>${dataBR(f.lancamento) || '-'}</div>
          <div><small>Situação</small>${esc(f.situacao || '-')}</div>` : `
          <div><small>Duração</small>${duracao(f.duracao_min) || '-'}</div>
          <div><small>Lançamento</small>${dataBR(f.lancamento) || '-'}</div>`}
          <div><small>Idioma</small>${esc(f.idioma || '-')}</div>
        </div>
        ${f.trailer_youtube ? `<div class="secao" style="aspect-ratio:16/9;border-radius:12px;overflow:hidden"><iframe src="https://www.youtube-nocookie.com/embed/${esc(f.trailer_youtube)}" title="Trailer" style="width:100%;height:100%;border:0" allowfullscreen loading="lazy"></iframe></div>` : ''}`,
      elenco: () => `<div class="grade">${f.elenco.map((p) => `
        <div class="filme-card"><div class="poster">${p.foto ? `<img src="${esc(p.foto)}" alt="" loading="lazy">` : `<div class="sem-poster">${esc(p.nome)}</div>`}</div>
        <h3>${esc(p.nome)}</h3><div class="meta">${esc(p.personagem)}</div></div>`).join('')}</div>`,
      onde: () => f.onde_assistir.length
        ? f.onde_assistir.map((p) => `<div class="item"><img src="${esc(p.logo)}" alt="" style="width:40px;height:40px;border-radius:8px"><span class="txt">${esc(p.nome)}<small>${p.tipo}</small></span></div>`).join('')
          + '<p class="meta" style="margin-top:12px">Dados de streaming: JustWatch via TMDB</p>'
        : vazio('Não está em nenhum streaming no Brasil agora.'),
      temporadas: () => `
        <div class="temporadas-escolha">
          <select class="chip" id="sel-temp" aria-label="Temporada">
            ${f.temporadas.map((t) => `<option value="${t.numero}">${esc(t.numero === 0 ? 'Especiais' : `Temporada ${t.numero}`)} · ${t.episodios} ep.</option>`).join('')}
          </select>
        </div>
        <div id="episodios">${carregando()}</div>`,
      semelhantes: () => (f.semelhantes.length ? `<div class="grade">${f.semelhantes.map(filmeCard).join('')}</div>` : vazio('Nada por aqui.')),
    };
    const abrir = (nome) => {
      $$('[data-aba]', raiz).forEach((b) => b.classList.toggle('ativo', b.dataset.aba === nome));
      $('#aba').innerHTML = abas[nome]();
      if (nome === 'temporadas') ligarTemporadas(f);
    };
    $$('[data-aba]', raiz).forEach((b) => (b.onclick = () => abrir(b.dataset.aba)));
    const verTemporadas = () => { abrir('temporadas'); $('.abas', raiz).scrollIntoView({ behavior: 'smooth', block: 'start' }); };
    if ($('#b-temporadas')) $('#b-temporadas').onclick = verTemporadas;
    if (rota.query.aba === 'temporadas' && $('[data-aba="temporadas"]', raiz)) verTemporadas();
    else abrir('info');
    ligarBotoesFilme(f);
    ligarReviews($('#lat'));
  },
};

// Aba "Temporadas" da série: escolhe a temporada e vê os episódios.
// Cada episódio tem "Assistir agora" (Modo Cinema com as cores do PÔSTER da série)
// ou, se já estiver assistindo outra coisa, "Colocar na fila".
function ligarTemporadas(f) {
  const cacheTemp = {};
  let tempAtual = f.temporadas[0].numero;
  const mostrar = async (n) => {
    tempAtual = n;
    const sel = $('#sel-temp');
    if (sel && Number(sel.value) !== n) { sel.value = String(n); sel.dispatchEvent(new Event('change')); return; }
    const box = $('#episodios');
    box.innerHTML = carregando();
    try {
      const [t, cine, fila] = await Promise.all([
        cacheTemp[n] ? Promise.resolve(cacheTemp[n]) : API.temporada(f.id, n),
        API.cinema().catch(() => null),
        API.fila().catch(() => []),
      ]);
      cacheTemp[n] = t;
      const hoje = new Date().toISOString().slice(0, 10);
      const passando = cine?.ativo && cine.modo === 'assistindo' && cine.filme;
      const ehEste = (e) => passando && cine.filme.id === f.id && cine.filme.temporada === n && cine.filme.episodio === e.numero;
      const posFila = (e) => fila.findIndex((x) => x.id === f.id && x.temporada === n && x.episodio === e.numero);
      const botao = (e) => {
        if (e.data && e.data > hoje) return `<span class="ep-aviso">${icone('relogio')} Sai em ${dataBR(e.data)}</span>`;
        if (ehEste(e)) return `<button class="btn btn-roxo" disabled>${icone('play')} Assistindo agora</button>`;
        if (passando) {
          const pos = posFila(e);
          return pos >= 0
            ? `<button class="btn" disabled>${icone('check')} Na fila (${pos + 1}º)</button>`
            : `<button class="btn" data-fila-ep="${e.numero}">${icone('mais')} Colocar na fila</button>`;
        }
        return `<button class="btn btn-roxo" data-assistir-ep="${e.numero}">${icone('play')} Assistir agora</button>`;
      };
      box.innerHTML = t.episodios.length ? `<ol class="episodios">${t.episodios.map((e) => `
        <li class="episodio ${e.data && e.data > hoje ? 'futuro' : ''} ${ehEste(e) ? 'passando' : ''}">
          <div class="ep-img">${e.imagem ? `<img src="${esc(e.imagem)}" alt="" loading="lazy">` : `<span>${e.numero}</span>`}</div>
          <div class="ep-txt">
            <b>${e.numero}. ${esc(e.nome)}</b>
            <div class="meta">${[e.data && (e.data > hoje ? `Estreia em ${dataBR(e.data)}` : dataBR(e.data)), duracao(e.duracao_min), e.nota && `<span class="estrela">★</span> ${e.nota}`].filter(Boolean).join(' • ')}</div>
            ${e.sinopse ? `<p class="sinopse">${esc(e.sinopse)}</p>` : ''}
            <div class="ep-acoes">${botao(e)}</div>
          </div>
        </li>`).join('')}</ol>` : vazio('Sem episódios cadastrados ainda.');

      $$('[data-assistir-ep]', box).forEach((b) => (b.onclick = async () => {
        const ep = Number(b.dataset.assistirEp);
        try {
          await API.cinemaIniciar({ tmdb_id: f.id, modo: 'assistindo', ritmo: 'normal', temporada: n, episodio: ep });
          toast(`🎬 Modo Cinema ligado: T${n}E${ep} de ${f.titulo}. A luz segue as cores do pôster.`);
          mostrar(n);
        } catch (err) { toast(err.message); }
      }));
      $$('[data-fila-ep]', box).forEach((b) => (b.onclick = async () => {
        const ep = Number(b.dataset.filaEp);
        try {
          const lista = await API.entrarFila(f.id, { temporada: n, episodio: ep });
          toast(`Na fila! T${n}E${ep} começa depois de ${cine.filme.titulo}${lista.length > 1 ? ` (é o ${lista.length}º)` : ''}.`);
          mostrar(n);
        } catch (err) { toast(err.message); }
      }));
    } catch (err) { box.innerHTML = erroBox(err); }
  };
  // Lista de temporadas com rolagem (a mesma lista do site dos filtros)
  $('#sel-temp').onchange = () => mostrar(Number($('#sel-temp').value));
  melhorarSelects($('#aba'));
  mostrar(tempAtual);
}

function telaEscrever(raiz, f, reviews) {
  const minha = f.meu.minha_review;
  let nota = minha ? Number(minha.nota) : 0;

  raiz.innerHTML = `
    ${cabecalhoFilme(f)}
    <div class="colunas secao">
      <form id="form-review">
        <h2 style="margin-bottom:12px">Sua nota</h2>
        <div id="estrelas" style="display:flex;align-items:center;gap:14px;flex-wrap:wrap"></div>
        <h2 style="margin:24px 0 12px">Sua review</h2>
        <textarea class="campo" id="texto" maxlength="1000" placeholder="Conte o que achou de ${esc(f.titulo)}: roteiro, atuações, trilha sonora, fotografia...">${esc(minha?.texto || '')}</textarea>
        <div class="meta" style="justify-content:flex-end;margin-top:6px"><span id="cont">0</span> / 1000</div>
        <div class="acoes" style="margin-top:14px;align-items:center">
          <button type="button" class="btn" id="spoiler" aria-pressed="false">Contém spoiler</button>
          <span style="flex:1"></span>
          <a class="btn" href="#/filme/${f.id}">Cancelar</a>
          <button class="btn btn-primario">Publicar review</button>
        </div>
      </form>
      <aside class="lateral" id="lat">
        <section>
          <div class="secao-topo"><h2>Avaliações recentes</h2><a class="link" href="#/filme/${f.id}/avaliacoes">Ver todas</a></div>
          ${reviews.slice(0, 3).map((r) => reviewCard(r, { mostrarFilme: false })).join('') || vazio('Ainda sem avaliações.')}
        </section>
      </aside>
    </div>`;

  barraEstrelas($('#estrelas'), { valor: nota, onChange: (v) => (nota = v) });

  const txt = $('#texto');
  const contar = () => ($('#cont').textContent = txt.value.length);
  txt.oninput = contar; contar();

  let spoiler = false;
  $('#spoiler').onclick = (e) => { spoiler = !spoiler; e.currentTarget.classList.toggle('ativo', spoiler); e.currentTarget.setAttribute('aria-pressed', spoiler); };

  ligarBotoesFilme(f);
  ligarReviews($('#lat'));

  $('#form-review').onsubmit = async (e) => {
    e.preventDefault();
    if (!nota) return toast('Escolha uma nota nas estrelas (pode ser meia estrela).');
    try {
      await API.publicarReview({ tmdb_id: f.id, nota, texto: txt.value.trim(), tem_spoiler: spoiler, assistido_em: new Date().toISOString().slice(0, 10) });
      if (window.MINHAS_NOTAS) window.MINHAS_NOTAS[f.id] = nota;   // já aparece "x.x ★" nos pôsteres
      toast('Review publicada! 🎬');
      location.hash = `#/filme/${f.id}/avaliacoes`;
    } catch (err) { toast(err.message); }
  };
}

function telaAvaliacoes(raiz, f, reviews) {
  const media = reviews.length ? reviews.reduce((s, r) => s + r.nota, 0) / reviews.length : 0;
  raiz.innerHTML = `
    ${cabecalhoFilme(f)}
    <div class="colunas secao">
      <div id="lista-av">
        <div class="secao-topo"><h2>${reviews.length} avaliações</h2><a class="btn btn-primario" href="#/filme/${f.id}/review">${icone('lapis')} Avaliar</a></div>
        ${reviews.map((r) => reviewCard(r, { mostrarFilme: false })).join('') || vazio('Ninguém avaliou ainda.')}
      </div>
      <aside class="lateral">
        <section class="card" style="text-align:center">
          <h2 style="font-size:20px">Nota da comunidade</h2>
          <div style="font-size:48px;font-weight:700;margin:10px 0">${media ? media.toFixed(1) : '-'}</div>
          <div class="meta" style="justify-content:center">de 5 estrelas • ${reviews.length} reviews</div>
          ${reviews.length ? graficoNotas(reviews) : ''}
        </section>
      </aside>
    </div>`;
  ligarBotoesFilme(f);
  ligarReviews($('#lista-av'));
}

// Gráfico das notas (quantas reviews deram 5, 4, 3, 2, 1 estrelas) — embaixo da Nota da comunidade
function graficoNotas(reviews) {
  const faixas = [5, 4, 3, 2, 1].map((n) => ({ n, qtd: reviews.filter((r) => Math.ceil(r.nota) === n).length }));
  const maior = Math.max(...faixas.map((f) => f.qtd), 1);
  return `<div class="grafico-notas" role="img" aria-label="${faixas.map((f) => `${f.qtd} com ${f.n} estrelas`).join(', ')}">
    ${faixas.map((f) => `
      <div class="linha-nota">
        <span class="rot">${f.n}★</span>
        <span class="barra"><i style="width:${(f.qtd / maior) * 100}%"></i></span>
        <span class="qtd">${f.qtd}</span>
      </div>`).join('')}
  </div>`;
}
