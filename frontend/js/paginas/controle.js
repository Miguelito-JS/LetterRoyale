// =====================================================================
// CELULAR COMO CONTROLE REMOTO + SESSÃO COMPARTILHADA (votação)
//
//  #/controle?t=TOKEN  -> o celular que escaneou o QR vira o controle da sala
//  #/votacao           -> (menu da esquerda) o anfitrião monta a votação
//  #/votar/CODIGO      -> amigos escaneiam e votam no filme da noite (sem conta)
//
//  O QR code é gerado aqui mesmo (js/lib/qrcode.js), sem internet.
//  O endereço usa o IP do computador no Wi-Fi (ex.: http://192.168.0.15:8080),
//  porque "localhost" no celular seria o próprio celular.
// =====================================================================
window.Paginas = window.Paginas || {};

/** Atmosferas que aparecem como botões (chave, ícone de icones.js, nome). */
const ATMOSFERAS_BOTOES = [
  ['terror', 'fantasma', 'Terror'], ['suspense', 'olho', 'Suspense'], ['acao', 'raio', 'Ação'], ['ficcao', 'foguete', 'Ficção'],
  ['romance', 'coracao', 'Romance'], ['comedia', 'sorriso', 'Comédia'], ['familia', 'casa', 'Família'], ['musical', 'musica', 'Música'],
  ['drama', 'reviews', 'Drama'], ['documentario', 'globo', 'Documentário'], ['animacao', 'estrela', 'Animação'], ['classico', 'filmes', 'Clássico'],
];

/** Lê o "username" de dentro do token (só pra mostrar o nome; quem valida é o servidor). */
function dadosDoToken(t) {
  try {
    const p = JSON.parse(atob(t.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return { id: Number(p.sub), username: p.username, nome: p.username };
  } catch { return null; }
}

/** Barrinha de votos (largura proporcional ao mais votado). */
const barraVotos = (votos, max, id = '') => `<span class="votar-barra"><span data-pct="${Math.round(votos / Math.max(1, max) * 100)}" data-barra="${id}" style="width:0"></span></span>`;
/**
 * As barrinhas amarelas CRESCEM até o valor novo (não aparecem do nada).
 * Guarda a largura anterior de cada filme: quando chega voto, ela anda do valor velho pro novo.
 */
const LARGURA_ANTERIOR = new Map();
function animarBarras(onde) {
  $$('[data-pct]', onde).forEach((s) => {
    const chave = s.dataset.barra;
    s.style.width = `${LARGURA_ANTERIOR.get(chave) ?? 0}%`;
    void s.offsetWidth;   // o navegador "vê" a largura velha antes de mudar
    requestAnimationFrame(() => { s.style.width = `${s.dataset.pct}%`; });
    LARGURA_ANTERIOR.set(chave, Number(s.dataset.pct));
  });
}
const textoVotos = (n) => `${n} voto${n === 1 ? '' : 's'}`;

// ---------------------------------------------------------------------
// CONTROLE REMOTO (celular)
// ---------------------------------------------------------------------
Paginas.controle = {
  titulo: 'Controle',
  publica: true,
  livre: true,
  async render(raiz, rota) {
    // Veio do QR code: guarda o token (vale 12 h) e limpa o endereço
    if (rota.query.t) {
      const u = dadosDoToken(rota.query.t);
      if (u) Sessao.salvar(rota.query.t, { ...(Sessao.usuario || {}), ...u });
      history.replaceState(null, '', '#/controle');
    }
    if (!Sessao.token) {
      raiz.innerHTML = `<div class="controle"><h1>Controle</h1>${vazio('Escaneie o QR code da tela Smart Home (botão Celular) pra usar o celular como controle.')}</div>`;
      return;
    }

    raiz.innerHTML = `
      <div class="controle">
        <header class="controle-topo">
          <img src="/img/logo.svg" alt="" width="32" height="32">
          <div><b>Controle da sala</b><small>@${esc(Sessao.usuario?.username || '')}</small></div>
          <button class="btn" id="voz" type="button" aria-pressed="false" aria-label="Comando de voz">${icone('microfone')}</button>
        </header>
        <div class="voz-painel" id="voz-dica" hidden aria-live="polite"></div>
        <section class="controle-agora" id="c-agora">${carregando()}</section>
        <div class="controle-play">
          <button class="btn btn-primario" id="c-pausa" type="button">${icone('pausa')} Intervalo</button>
          <button class="btn" id="c-fim" type="button">${icone('bandeira')} Encerrar filme</button>
        </div>
        <h2 class="controle-sub">Atmosfera</h2>
        <div class="controle-grade controle-atmosferas">${ATMOSFERAS_BOTOES.map(([k, ico, nome]) => `<button class="controle-atm" data-atm="${k}" type="button">${icone(ico)}${nome}</button>`).join('')}</div>
        <h2 class="controle-sub">Aparelhos</h2>
        <div id="c-aparelhos">${carregando()}</div>
        <h2 class="controle-sub">Cenas</h2>
        <div class="controle-grade" id="c-cenas"></div>
      </div>`;

    const vivo = () => location.hash.startsWith('#/controle') && document.body.contains(raiz);
    let casa = null;
    const avisar = (r) => r && r.aviso && toast(r.aviso);
    const agir = async (fn, ok) => { try { avisar(await fn()); if (ok) toast(ok); } catch (e) { toast(e.message); } atualizar(); };

    async function atualizar() {
      let cin;
      try { cin = await API.cinema(); } catch (e) { $('#c-agora', raiz).innerHTML = erroBox(e); return; }
      const f = cin.ativo && cin.filme;
      $('#c-agora', raiz).innerHTML = f
        ? `<div class="controle-filme">${f.poster ? `<img src="${esc(f.poster)}" alt="">` : ''}
             <div><span class="badge" style="color:var(--gold)">${cin.pausado ? 'INTERVALO' : cin.modo === 'assistindo' ? 'PASSANDO' : 'NAVEGANDO'}</span>
             <b>${esc(f.titulo)}</b>
             <small>${cin.atmosfera ? `Atmosfera: ${esc(cin.atmosfera.nome)}` : ''}</small>
             <span>${paletaHtml(cin.paleta, cin.cor_atual)}</span></div></div>`
        : '<div class="controle-filme"><div><b>Nenhum filme passando</b><small>Dê Assistir agora num filme no computador, ou escolha uma atmosfera abaixo.</small></div></div>';
      const pausa = $('#c-pausa', raiz);
      pausa.innerHTML = cin.pausado ? `${icone('play')} Voltar ao filme` : `${icone('pausa')} Intervalo`;
      pausa.disabled = !cin.ativo;
      $('#c-fim', raiz).disabled = !(cin.ativo && cin.modo === 'assistindo');
      $$('[data-atm]', raiz).forEach((b) => b.classList.toggle('ativo', cin.atmosfera?.chave === b.dataset.atm));
      pausa.onclick = () => agir(() => (cin.pausado ? API.cinemaRetomar() : API.cinemaPausar()), cin.pausado ? 'Continuando o filme' : 'Intervalo');
    }

    async function carregarCasa() {
      try { casa = await API.casa(); } catch (e) { $('#c-aparelhos', raiz).innerHTML = erroBox(e); return; }
      const d = (tipo) => casa.dispositivos.find((x) => x.tipo === tipo);
      const luz = d('iluminacao'), cort = d('cortinas'), ar = d('temperatura'), som = d('tv_som');
      // Liga/desliga igual ao da tela do computador
      const chave = (d) => `<label class="switch"><input type="checkbox" data-ligar-c="${d.id}" ${d.ligado ? 'checked' : ''} aria-label="Ligar ${esc(d.nome)}"><span></span></label>`;
      const linha = (d, miolo) => `<div class="controle-linha ${d.ligado ? '' : 'desligado'}" data-linha="${d.id}">${miolo}${chave(d)}</div>`;
      $('#c-aparelhos', raiz).innerHTML = `
        ${luz ? linha(luz, `${icone('lampada')}<span class="controle-nome">Luz</span><input type="range" min="0" max="100" value="${luz.valor}" data-disp="${luz.id}" aria-label="Brilho"><output>${luz.valor}%</output>`) : ''}
        ${som ? linha(som, `${icone('som')}<span class="controle-nome">Volume</span><input type="range" min="0" max="100" value="${som.valor}" data-disp="${som.id}" aria-label="Volume"><output>${som.valor}%</output>`) : ''}
        ${ar ? linha(ar, `${icone('termometro')}<span class="controle-nome">Ar</span><span class="controle-ar"><button class="btn" data-ar="-1" type="button" aria-label="Diminuir">−</button><output id="c-ar">${ar.valor}°C</output><button class="btn" data-ar="1" type="button" aria-label="Aumentar">+</button></span>`) : ''}
        ${cort ? linha(cort, `${icone('cortina')}<span class="controle-nome">Cortinas</span><span class="segmentado controle-cortina">${['abertas', 'meio', 'fechadas'].map((e) => `<button type="button" data-cort="${e}" class="${cort.estado === e ? 'ativo' : ''}">${e === 'meio' ? 'Meio' : e[0].toUpperCase() + e.slice(1)}</button>`).join('')}</span>`) : ''}`;
      $$('[data-ligar-c]', raiz).forEach((i) => (i.onchange = () => {
        const d = casa.dispositivos.find((x) => String(x.id) === i.dataset.ligarC);
        d.ligado = i.checked;
        $(`[data-linha="${d.id}"]`, raiz).classList.toggle('desligado', !i.checked);
        agir(() => API.dispositivo(d.id, { ligado: i.checked }), `${d.nome}: ${generoEstado(d.nome, i.checked)}`);
      }));
      $('#c-cenas', raiz).innerHTML = casa.cenas.filter((c) => c.nome !== 'Pausa').map((c) => `<button class="controle-atm" data-cena="${c.id}" type="button">${icone('casa')}${esc(c.nome)}</button>`).join('');

      $$('input[data-disp]', raiz).forEach((r) => {
        r.oninput = () => { r.nextElementSibling.textContent = r.value + '%'; pintarRange(r); };
        r.onchange = () => agir(() => API.dispositivo(r.dataset.disp, { valor: Number(r.value), ligado: true }));
      });
      $$('[data-ar]', raiz).forEach((b) => (b.onclick = () => {
        ar.valor = Math.max(16, Math.min(30, Number(ar.valor) + Number(b.dataset.ar)));
        $('#c-ar', raiz).textContent = ar.valor + '°C';
        agir(() => API.dispositivo(ar.id, { valor: ar.valor, ligado: true }));
      }));
      $$('[data-cort]', raiz).forEach((b) => (b.onclick = () => {
        $$('[data-cort]', raiz).forEach((x) => x.classList.toggle('ativo', x === b));
        agir(() => API.dispositivo(cort.id, { estado: b.dataset.cort }));
      }));
      $$('[data-cena]', raiz).forEach((b) => (b.onclick = () => agir(() => API.ativarCena(b.dataset.cena), b.textContent.trim())));
    }

    $$('[data-atm]', raiz).forEach((b) => (b.onclick = () => agir(() => API.atmosferaManual(b.dataset.atm), `Atmosfera ${b.textContent.trim()}`)));
    $('#c-fim', raiz).onclick = () => agir(() => API.cinemaTerminar(), 'Créditos!');

    // Voz no celular também (o Chrome do Android entende português)
    if (ComandoVoz.disponivel()) {
      ComandoVoz.ligar(raiz, { get cenas() { return casa?.cenas || []; }, get dispositivos() { return casa?.dispositivos || []; }, recarregar: () => { carregarCasa(); atualizar(); } }, () => carregarCasa());
    } else $('#voz', raiz).hidden = true;

    await Promise.all([atualizar(), carregarCasa()]);
    const timer = setInterval(() => (vivo() ? atualizar() : (clearInterval(timer), ComandoVoz.parar())), 2500);
  },
};

// ---------------------------------------------------------------------
// VOTAÇÃO — página do anfitrião (menu da esquerda)
// ---------------------------------------------------------------------
Paginas.votacao = {
  titulo: 'Votação',
  async render(raiz) {
    const vivo = () => location.hash.startsWith('#/votacao') && document.body.contains(raiz);
    let timer = null;
    const v = await API.minhaVotacao().catch(() => ({ ativa: false }));
    if (v.ativa && v.aberta) return mostrarAberta(v);
    return montar();

    // ---- 1. Escolher os filmes (filmes, séries e documentários, quantos quiser)
    async function montar() {
      clearInterval(timer);
      const [casa, emAlta, populares] = await Promise.all([API.casa().catch(() => ({ fila: [] })), API.emAlta().catch(() => []),
        API.catalogo({ tipo: 'todos' }).then((r) => r.filmes || []).catch(() => [])]);
      const escolhidos = new Map();   // id -> filme
      const comPoster = (l) => (l || []).filter((f) => f && f.id && f.poster);
      // Sugestões: fila + em alta + populares (sobra pôster pra sempre fechar as linhas da grade)
      const inicial = [...new Map([...comPoster(casa.fila), ...comPoster(emAlta), ...comPoster(populares)].map((f) => [String(f.id), f])).values()];
      let naGrade = inicial;   // lista que está na grade agora (sugestões ou resultado da busca)
      raiz.innerHTML = `
        <div class="colunas pagina-votacao-grade">
          <div>
            <section class="cena-ativa votacao-topo">
              <span class="ico">${icone('urna')}</span>
              <div style="flex:1;min-width:220px">
                <h2 style="font-size:26px;margin-bottom:6px">Qual filme vamos ver hoje?</h2>
                <p class="texto-suave">Escolha pelo menos 2 títulos (filmes, séries ou documentários). Os amigos escaneiam um QR code e votam pelo celular, <b>sem precisar de conta</b>. O vencedor começa com a sala no clima dele.</p>
              </div>
            </section>
            <form class="busca votacao-busca" id="vt-form" role="search">
              ${icone('busca')}
              <label class="sr-only" for="vt-q">Buscar filme, série ou documentário</label>
              <input id="vt-q" type="search" placeholder="Buscar filme, série ou documentário..." enterkeyhint="search">
            </form>
            <p class="texto-suave votacao-rotulo" id="vt-rotulo">Sugestões: sua fila do cinema, em alta e populares</p>
            <div class="votacao-escolha" id="vt-grade"></div>
          </div>
          <aside class="lateral">
            <section class="card votacao-lado" id="vt-lado">
              <button type="button" class="votacao-lado-topo" id="vt-abrir-lista" aria-expanded="false" aria-controls="vt-lista">
                <h2 style="font-size:22px">Na votação <span class="texto-suave" id="vt-conta">0</span></h2>
                <span class="votacao-seta" aria-hidden="true">${icone('voltar')}</span>
              </button>
              <p class="texto-suave" id="vt-minimo">Mínimo 2 ou mais títulos.</p>
              <ol id="vt-lista" class="votacao-lista"><li class="texto-suave">Toque nos pôsteres pra adicionar.</li></ol>
              <button class="btn btn-primario btn-bloco" id="vt-criar" disabled>${icone('qrcode')} Abrir votação</button>
            </section>
          </aside>
        </div>`;
      const todos = new Map(inicial.map((f) => [String(f.id), f]));

      // Grade sempre com as linhas COMPLETAS (sem buraco no fim): mostra só múltiplos do nº de colunas
      let colunasAntes = 0;
      function encherGrade(forcar = false) {
        const grade = $('#vt-grade', raiz);
        if (!grade || !naGrade.length) return;
        const colunas = getComputedStyle(grade).gridTemplateColumns.split(' ').filter(Boolean).length || 1;
        if (!forcar && colunas === colunasAntes) return;
        colunasAntes = colunas;
        const qtd = naGrade.length < colunas ? naGrade.length : Math.floor(naGrade.length / colunas) * colunas;
        grade.innerHTML = naGrade.slice(0, qtd).map(cartao).join('');
        ligarCartoes(grade);
        desenharLista();
      }

      function cartao(f) {
        const breve = naoEstreou(f);
        return `<button type="button" class="votacao-item ${breve ? 'em-breve' : ''}" data-escolher="${f.id}" aria-pressed="false">${poster(f, 'votacao-capa')}<span>${esc(f.titulo)}${breve ? `<small>Estreia em ${dataBR(f.lancamento)}</small>` : ''}</span></button>`;
      }
      function ligarCartoes(onde) {
        $$('[data-escolher]', onde).forEach((b) => (b.onclick = () => {
          const id = b.dataset.escolher;
          const f = todos.get(id);
          if (escolhidos.has(id)) escolhidos.delete(id);
          else if (escolhidos.size >= 40) return toast('Já tem 40 títulos na votação. Tire algum pra pôr outro.');
          else {
            escolhidos.set(id, f);
            if (naoEstreou(f)) toast(`Aviso: ${f.titulo} ainda não estreou (estreia em ${dataBR(f.lancamento)}).`);
          }
          desenharLista();
        }));
      }
      function desenharLista() {
        $$('[data-escolher]', raiz).forEach((b) => {
          const sim = escolhidos.has(b.dataset.escolher);
          b.classList.toggle('escolhido', sim);
          b.setAttribute('aria-pressed', String(sim));
        });
        $('#vt-conta', raiz).textContent = String(escolhidos.size);
        $('#vt-lista', raiz).innerHTML = escolhidos.size
          ? [...escolhidos.values()].map((f, i) => `<li><b>${i + 1}.</b> ${esc(f.titulo)} <button class="icon-btn" data-tirar="${f.id}" aria-label="Tirar ${esc(f.titulo)}">${icone('x')}</button></li>`).join('')
          : '<li class="texto-suave">Toque nos pôsteres pra adicionar.</li>';
        $$('[data-tirar]', raiz).forEach((b) => (b.onclick = () => { escolhidos.delete(b.dataset.tirar); desenharLista(); }));
        $('#vt-criar', raiz).disabled = escolhidos.size < 2;
        const falta = 2 - escolhidos.size;
        $('#vt-minimo', raiz).textContent = falta > 0 ? 'Mínimo 2 ou mais títulos.' : 'Pronto! Pode abrir a votação ou pôr mais títulos.';
      }
      encherGrade(true);
      if (window.ResizeObserver) new ResizeObserver(() => { if (document.body.contains(raiz)) encherGrade(); }).observe($('#vt-grade', raiz));
      // Celular: o quadro "Na votação" é um pop-up no canto; toca no título pra ver a lista
      $('#vt-abrir-lista', raiz).onclick = () => {
        const aberto = $('#vt-lado', raiz).classList.toggle('aberta');
        $('#vt-abrir-lista', raiz).setAttribute('aria-expanded', String(aberto));
      };
      $('#vt-form', raiz).onsubmit = async (e) => {
        e.preventDefault();
        const q = $('#vt-q', raiz).value.trim();
        const grade = $('#vt-grade', raiz);
        if (!q) {
          $('#vt-rotulo', raiz).textContent = 'Sugestões: sua fila do cinema, em alta e populares';
          naGrade = inicial;
          return encherGrade(true);
        }
        grade.innerHTML = carregando();
        try {
          const r = await API.catalogo({ q, tipo: 'todos' });
          const achados = comPoster(r.filmes);
          achados.forEach((f) => todos.set(String(f.id), f));
          $('#vt-rotulo', raiz).textContent = `Resultado para "${q}"`;
          if (!achados.length) { grade.innerHTML = vazio('Nada encontrado. Tente outro nome.'); return; }
          naGrade = achados;
          encherGrade(true);
        } catch (err) { grade.innerHTML = erroBox(err); }
      };
      $('#vt-q', raiz).addEventListener('search', () => { if (!$('#vt-q', raiz).value) $('#vt-form', raiz).requestSubmit(); });
      $('#vt-criar', raiz).onclick = async () => {
        try { mostrarAberta(await API.criarVotacao([...escolhidos.keys()].map(Number))); } catch (e) { toast(e.message); }
      };
    }

    // ---- 2. Votação aberta: QR code + votos ao vivo
    function mostrarAberta(v) {
      let assinatura = '';
      const desenhar = (x) => {
        const nova = x.codigo + '|' + x.opcoes.map((o) => `${o.id}:${o.votos}`).join(',');
        if (nova === assinatura) return;   // nada mudou: não redesenha (nada pisca)
        assinatura = nova;
        const max = Math.max(...x.opcoes.map((o) => o.votos));
        const lista = [...x.opcoes].sort((a, b) => b.votos - a.votos);
        raiz.innerHTML = `
          <div class="votacao-aberta">
            <section class="card votacao-qr">
              <div class="qr-caixa">${QRGerador.svg(x.url_votar, 260)}</div>
              <span class="texto-suave">Código</span>
              <b class="votacao-codigo">${esc(x.codigo)}</b>
              <p class="texto-suave">Celulares no <b>mesmo Wi-Fi</b>: aponte a câmera pro QR code.</p>
              <button class="btn btn-bloco" id="vt-sair" type="button">${icone('voltar')} Sair sem escolher</button>
            </section>
            <section class="card votacao-placar">
              <div class="secao-topo"><h2>Votos ao vivo</h2><span class="badge">${textoVotos(x.total_votos)}</span></div>
              ${lista.map((o) => `
                <div class="votacao-linha">
                  ${poster(o, 'votacao-mini')}
                  <div><b>${esc(o.titulo)}</b>${barraVotos(o.votos, max, 'h' + o.id)}<small>${textoVotos(o.votos)}</small></div>
                </div>`).join('')}
              <button class="btn btn-primario btn-bloco" id="vt-fim">${icone('play')} Encerrar e assistir o vencedor</button>
            </section>
          </div>`;
        animarBarras(raiz);
        $('#vt-sair', raiz).onclick = async () => {
          if (!(await confirmar({ titulo: 'Sair da votação?', texto: 'A votação é cancelada e os votos são apagados.', botao: 'Sair e cancelar' }))) return;
          try { await API.cancelarVotacao(); clearInterval(timer); toast('Votação cancelada'); montar(); } catch (e) { toast(e.message); }
        };
        $('#vt-fim', raiz).onclick = async () => {
          if (!x.total_votos) return toast('Ninguém votou ainda. Espere os votos ou use "Sair sem escolher".');
          try {
            const r = await API.encerrarVotacao();
            clearInterval(timer);
            try {
              await API.cinemaIniciar({ tmdb_id: r.vencedor.id, modo: 'assistindo', ritmo: 'normal' });
              toast(`Venceu ${r.vencedor.titulo}! Bom filme.`);
              location.hash = '#/casa';
            } catch (e) {
              toast(`Venceu ${r.vencedor.titulo}! ${e.message}`);   // ex.: ainda não estreou -> só avisa
              montar();
            }
          } catch (e) { toast(e.message); }
        };
      };
      desenhar(v);
      clearInterval(timer);
      timer = setInterval(async () => {
        if (!vivo()) return clearInterval(timer);
        try { const x = await API.minhaVotacao(); if (x.ativa && x.aberta) desenhar(x); } catch { /* tenta de novo */ }
      }, 2500);
    }
  },
};

// ---------------------------------------------------------------------
// VOTAÇÃO — celular dos amigos (sem conta)
// ---------------------------------------------------------------------
Paginas.votar = {
  titulo: 'Momento Cinema',
  publica: true,
  livre: true,
  async render(raiz, rota) {
    const codigo = (rota.partes[0] || '').toUpperCase();
    let votante;
    try {
      votante = localStorage.getItem('lr_votante');
      if (!votante) { votante = (crypto.randomUUID?.() || String(Math.random()).slice(2)); localStorage.setItem('lr_votante', votante); }
    } catch { votante = String(Math.random()).slice(2); }

    raiz.innerHTML = `<div class="controle"><header class="controle-topo"><img src="/img/logo.svg" alt="" width="32" height="32">
      <div><b>Momento Cinema</b><small>Código ${esc(codigo)}</small></div></header><div id="v-corpo">${carregando()}</div></div>`;
    const corpo = $('#v-corpo', raiz);
    const vivo = () => location.hash.startsWith('#/votar') && document.body.contains(raiz);

    // Fluxo do amigo: toca no filme -> confirma -> tela de "Obrigado pelo seu voto".
    // A cada 2,5 s só os NÚMEROS mudam (sem redesenhar tudo: nada pisca).
    let escolhido = null;      // filme tocado, ainda não confirmado
    let trocando = false;      // apertou "Trocar voto"
    let tela = '';             // 'lista' | 'obrigado' | 'fim'
    let ultimo = null;

    function placar(v, prefixo) {
      const max = Math.max(...v.opcoes.map((o) => o.votos));
      return v.opcoes.map((o) => `
        <div class="votar-placar-linha ${v.vencedor === o.id ? 'vencedor' : ''}">
          ${poster(o, 'votacao-mini')}
          <div><b>${v.vencedor === o.id ? 'Vencedor: ' : ''}${esc(o.titulo)}</b>${barraVotos(o.votos, max, prefixo + o.id)}<small data-votos="${o.id}">${textoVotos(o.votos)}${v.seu_voto === o.id ? ' · seu voto' : ''}</small></div>
        </div>`).join('');
    }

    function desenhar(v) {
      ultimo = v;
      const novaTela = !v.aberta ? 'fim' : (v.seu_voto && !trocando ? 'obrigado' : 'lista');
      if (novaTela === tela) return atualizarNumeros(v);   // mesma tela: só os números
      tela = novaTela;
      if (tela === 'fim') {
        corpo.innerHTML = `<div class="votar-obrigado"><h2>Votação encerrada!</h2><p class="texto-suave">${textoVotos(v.total_votos)}</p></div>
          <div class="votar-placar">${placar(v, 'f')}</div>`;
      } else if (tela === 'obrigado') {
        const meu = v.opcoes.find((o) => o.id === v.seu_voto);
        corpo.innerHTML = `
          <div class="votar-obrigado">
            ${meu ? poster(meu, 'votar-obrigado-capa') : ''}
            <h2>Obrigado pelo seu voto!</h2>
            <p class="texto-suave">Você votou em <b>${esc(meu?.titulo || '')}</b>. Quando <b>@${esc(v.anfitriao)}</b> encerrar, o filme começa na sala.</p>
            <button class="btn" id="v-trocar" type="button">Trocar voto</button>
          </div>
          <h3 class="controle-sub">Votos até agora <span class="texto-suave" id="v-total">${textoVotos(v.total_votos)}</span></h3>
          <div class="votar-placar">${placar(v, 'o')}</div>`;
        $('#v-trocar', corpo).onclick = () => { trocando = true; escolhido = null; desenhar(ultimo); };
      } else {
        corpo.innerHTML = `
          <p class="texto-suave" style="margin:6px 0 14px"><b>@${esc(v.anfitriao)}</b> quer saber: o que vamos assistir? Toque no filme e confirme.</p>
          <div class="votar-grade">
            ${v.opcoes.map((o) => `
              <button type="button" class="votar-opcao ${escolhido === o.id ? 'escolhido' : ''}" data-filme="${o.id}" aria-pressed="${escolhido === o.id}">
                ${o.poster ? `<img src="${esc(o.poster)}" alt="">` : `<div class="sem-poster">${esc(o.titulo)}</div>`}
                <b>${esc(o.titulo)}</b>
              </button>`).join('')}
          </div>
          <div class="votar-confirmar" id="v-confirmar" ${escolhido ? '' : 'hidden'}>
            <span id="v-escolha"></span>
            <button class="btn btn-primario" id="v-ok" type="button">Confirmar voto</button>
          </div>`;
        const mostrarEscolha = () => {
          const f = ultimo.opcoes.find((o) => o.id === escolhido);
          $('#v-confirmar', corpo).hidden = !f;
          if (f) $('#v-escolha', corpo).innerHTML = `Seu voto: <b>${esc(f.titulo)}</b>`;
          $$('[data-filme]', corpo).forEach((b) => {
            const sim = Number(b.dataset.filme) === escolhido;
            b.classList.toggle('escolhido', sim);
            b.setAttribute('aria-pressed', String(sim));
          });
        };
        $$('[data-filme]', corpo).forEach((b) => (b.onclick = () => { escolhido = Number(b.dataset.filme); mostrarEscolha(); }));
        $('#v-ok', corpo).onclick = async (e) => {
          e.target.disabled = true;
          try { trocando = false; desenhar(await API.votar(codigo, votante, escolhido)); } catch (err) { toast(err.message); e.target.disabled = false; }
        };
        mostrarEscolha();
      }
      animarBarras(corpo);
    }

    // Só muda as larguras das barras e os textos (a barra anda suave, nada pisca)
    function atualizarNumeros(v) {
      const max = Math.max(...v.opcoes.map((o) => o.votos));
      v.opcoes.forEach((o) => {
        const barra = $(`[data-barra="${(tela === 'fim' ? 'f' : 'o') + o.id}"]`, corpo);
        if (barra) { const pct = Math.round(o.votos / Math.max(1, max) * 100); barra.style.width = `${pct}%`; LARGURA_ANTERIOR.set(barra.dataset.barra, pct); }
        const t = $(`[data-votos="${o.id}"]`, corpo);
        if (t) t.textContent = `${textoVotos(o.votos)}${v.seu_voto === o.id ? ' · seu voto' : ''}`;
      });
      const total = $('#v-total', corpo);
      if (total) total.textContent = textoVotos(v.total_votos);
    }

    // Votação que não existe mais: a turma já escolheu e foi pro sofá (o vento levou)
    function telaAtrasado() {
      return `
        <div class="votar-atrasado">
          <div class="votar-vento" aria-hidden="true">
            <span>${icone('vento')}</span><span>${icone('vento')}</span><span>${icone('vento')}</span>
            <i>${icone('urna')}</i>
          </div>
          <h2>A sessão já começou!</h2>
          <p>Você chegou atrasado: a turma já escolheu o filme e foi pro sofá.</p>
          <p class="texto-suave">Peça pro anfitrião abrir uma votação nova, ou digite outro código.</p>
          <form class="votar-codigo" id="v-outro">
            <label class="sr-only" for="v-codigo">Código da votação</label>
            <span class="votar-codigo-campo">${icone('ingresso')}<input class="campo" id="v-codigo" maxlength="5" placeholder="Código" autocomplete="off" autocapitalize="characters" spellcheck="false" enterkeyhint="go"></span>
            <button class="btn btn-primario" type="submit">Entrar</button>
          </form>
        </div>`;
    }
    function ligarOutroCodigo() {
      const f = $('#v-outro', corpo);
      if (!f) return;
      f.onsubmit = (ev) => {
        ev.preventDefault();
        const c = $('#v-codigo', corpo).value.trim().toUpperCase();
        if (c.length >= 4) location.hash = `#/votar/${c}`;
      };
    }

    async function atualizar() {
      try { const v = await API.votacao(codigo, votante); desenhar(v); return v.aberta; }
      catch (e) {
        tela = '';
        corpo.innerHTML = e.status === 404 ? telaAtrasado() : erroBox(e);
        ligarOutroCodigo();
        return false;
      }
    }
    if (await atualizar()) {
      const timer = setInterval(async () => { if (!vivo() || !(await atualizar())) clearInterval(timer); }, 2500);
    }
  },
};

// ---------------------------------------------------------------------
// QR code do celular-controle (janela)
// ---------------------------------------------------------------------
async function abrirQrControle() {
  const dlg = document.createElement('dialog');
  dlg.className = 'sheet';
  dlg.innerHTML = `<h3>Celular como controle</h3><div id="qr-corpo">${carregando()}</div>
    <div class="acoes"><button class="btn btn-primario" data-fechar>Fechar</button></div>`;
  document.body.appendChild(dlg);
  dlg.showModal();
  dlg.addEventListener('close', () => dlg.remove());
  dlg.querySelector('[data-fechar]').onclick = () => dlg.close();
  const corpo = dlg.querySelector('#qr-corpo');
  try {
    const c = await API.conviteControle();
    corpo.innerHTML = `
      <div class="qr-caixa">${QRGerador.svg(c.url_controle, 240)}</div>
      <ol class="qr-passos">
        <li>Celular no <b>mesmo Wi-Fi</b> deste computador.</li>
        <li>Abra a câmera e aponte pro QR code.</li>
        <li>Pronto: o celular controla luz, cortinas, ar, volume, atmosfera e o intervalo do filme.</li>
      </ol>
      <p class="texto-suave">Endereço: <b>${esc(c.base)}</b> · o acesso pelo QR vale 12 horas.</p>
      ${c.https ? `
      <details class="texto-suave qr-certificado"><summary>1ª vez neste celular? Instale o certificado (pro microfone)</summary>
        <p>O celular abre o controle em <b>https</b> (site seguro), que é o que o Chrome exige pra liberar o microfone. Pra ele confiar neste PC, instale o certificado uma vez:</p>
        <div class="qr-caixa">${QRGerador.svg(c.url_certificado, 180)}</div>
        <ol class="qr-passos">
          <li>Aponte a câmera pro QR acima e baixe o arquivo <b>letterroyale-ca.crt</b>.</li>
          <li>No Android: <b>Configurações</b> &gt; <b>Segurança</b> &gt; <b>Mais configurações de segurança</b> &gt; <b>Criptografia e credenciais</b> &gt; <b>Instalar um certificado</b> &gt; <b>Certificado de CA</b> &gt; escolha o arquivo baixado.</li>
          <li>Pronto pra sempre (até em outra rede). Agora escaneie o QR grande.</li>
        </ol>
        <p>Sem instalar, o Chrome mostra "Sua conexão não é particular": toque em <b>Avançado</b> &gt; <b>Continuar</b> e também funciona.</p>
      </details>` : ''}
      ${c.aviso ? `<p class="texto-suave" style="color:var(--vermelho)">${esc(c.aviso)}</p>` : ''}
      <details class="texto-suave"><summary>Não abriu no celular?</summary>
        O Windows pode estar bloqueando o Java na rede. Na primeira vez que o servidor roda, aparece "Permitir acesso" do Firewall: marque <b>Redes privadas</b>.
        Também confira se o Wi-Fi do PC está como rede <b>Privada</b> (Configurações, Rede e Internet).</details>`;
  } catch (e) { corpo.innerHTML = erroBox(e); }
}
