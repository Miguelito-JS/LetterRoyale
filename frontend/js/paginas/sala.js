// =====================================================================
// SALA AO VIVO — "gêmeo digital" da sala (cópia virtual que reage junto)
//
// Desenha a sala em SVG e mostra o estado de TODOS os aparelhos ao vivo:
//   lâmpada (cor do pôster + brilho) · cortinas · ar-condicionado · TV e Som
// + a ATMOSFERA do filme e a LINHA DO TEMPO de eventos da casa.
// Botões: Celular (QR code), Gestos (webcam) e Voz (microfone).
// Usado pela tela Smart Home (casa.js).
// =====================================================================

/** Ícone de cada tipo de evento da linha do tempo (desenhos de icones.js). */
const ICONE_EVENTO = {
  sessao_iniciada: 'play', atmosfera: 'estrela', creditos: 'filmes', cinema_desligado: 'power', cena: 'casa',
  luz: 'lampada', cortinas: 'cortina', ar: 'termometro', som: 'som', intervalo: 'pausa', retomada: 'play',
  votacao: 'urna', controle: 'celular',
};

const SalaAoVivo = {
  html() {
    return `
      <section class="secao sala-ao-vivo" aria-labelledby="sala-titulo">
        <div class="secao-topo">
          <h2 id="sala-titulo">Sala ao vivo</h2>
          <span class="sala-botoes">
            <button class="btn" id="sala-celular" type="button" title="QR code: o celular vira controle remoto">${icone('celular')} Celular</button>
            <button class="btn" id="sala-gestos" type="button" aria-pressed="false" title="Webcam: mão aberta = intervalo, joinha = voltar">${icone('mao')} <span>Gestos</span></button>
            <button class="btn" id="voz" type="button" aria-pressed="false" title="Clique e fale. Ex.: LetterRoyale, modo terror">${icone('microfone')} <span>Voz</span></button>
          </span>
        </div>
        <div class="sala-grade">
          <div class="sala-palco">
            <span class="sala-pausa" id="sala-pausa" hidden>${icone('pausa')} INTERVALO</span>
            <svg id="sala-svg" viewBox="0 0 640 300" role="img" aria-label="Desenho da sala com os aparelhos">
              <defs>
                <radialGradient id="sala-brilho">
                  <stop id="sala-brilho-cor" offset="0%" stop-color="#ffd9a8" stop-opacity=".95"/>
                  <stop offset="100%" stop-color="#ffd9a8" stop-opacity="0" id="sala-brilho-fim"/>
                </radialGradient>
                <linearGradient id="sala-ceu" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="#1b2a7a"/><stop offset="100%" stop-color="#4a3a8a"/>
                </linearGradient>
                <clipPath id="sala-tela"><rect x="232" y="72" width="216" height="121" rx="4"/></clipPath>
              </defs>
              <rect width="640" height="300" fill="#141a5c"/>
              <rect y="238" width="640" height="62" fill="#0b0f3f"/>
              <!-- janela + cortinas -->
              <rect x="30" y="40" width="140" height="150" rx="6" fill="url(#sala-ceu)" stroke="#2a3192" stroke-width="4"/>
              <circle cx="132" cy="72" r="12" fill="#f2f2f5" opacity=".85"/>
              <rect id="sala-cortina-esq" class="sala-anima" x="30" y="40" width="70" height="150" fill="#8585b8"/>
              <rect id="sala-cortina-dir" class="sala-anima" x="100" y="40" width="70" height="150" fill="#7478f2"/>
              <rect x="22" y="34" width="156" height="8" rx="4" fill="#2a3192"/>
              <!-- ar-condicionado -->
              <rect x="200" y="14" width="120" height="30" rx="8" fill="#e9eaf7"/>
              <text id="sala-ar" x="260" y="34" text-anchor="middle" font-size="14" font-weight="700" fill="#151a63">22°C</text>
              <g id="sala-vento" class="sala-vento" stroke="#bfe6ff" stroke-width="2" fill="none" stroke-linecap="round">
                <path d="M215 52 q8 8 0 16"/><path d="M260 52 q8 8 0 16"/><path d="M305 52 q8 8 0 16"/>
              </g>
              <!-- TV -->
              <rect x="226" y="66" width="228" height="133" rx="8" fill="#05061f" stroke="#2a3192" stroke-width="3"/>
              <g clip-path="url(#sala-tela)">
                <rect x="232" y="72" width="216" height="121" fill="#0b0e4a"/>
                <image id="sala-tv-img" x="232" y="72" width="216" height="121" preserveAspectRatio="xMidYMid slice" opacity="0"/>
                <text id="sala-tv-txt" x="340" y="138" text-anchor="middle" font-size="13" fill="#a6a8d4">TV em espera</text>
              </g>
              <rect x="320" y="199" width="40" height="10" fill="#2a3192"/>
              <rect x="300" y="209" width="80" height="6" rx="3" fill="#2a3192"/>
              <rect x="226" y="222" width="228" height="6" rx="3" fill="#2a3192"/>
              <rect id="sala-vol" class="sala-anima" x="226" y="222" width="114" height="6" rx="3" fill="#e6b82e"/>
              <path d="M228 210h3l4-3v10l-4-3h-3z" fill="#a6a8d4"/>
              <!-- sofá -->
              <path d="M180 286 v-34 q0-12 12-12 h256 q12 0 12 12 v34 z" fill="#2a3192"/>
              <rect x="196" y="258" width="248" height="20" rx="8" fill="#353ca8"/>
              <!-- luz ambiente: escurece a sala conforme o brilho -->
              <rect id="sala-escuro" class="sala-anima-lenta" width="640" height="300" fill="#000" opacity=".4" pointer-events="none"/>
              <!-- lâmpada (fica por cima da sombra: ela é a fonte de luz) -->
              <circle id="sala-halo" class="sala-anima-lenta" cx="560" cy="86" r="150" fill="url(#sala-brilho)" opacity=".8" pointer-events="none"/>
              <line x1="560" y1="0" x2="560" y2="62" stroke="#a6a8d4" stroke-width="2"/>
              <path d="M538 70 h44 l-8 -10 h-28 z" fill="#a6a8d4"/>
              <circle id="sala-lampada" class="sala-anima-lenta" cx="560" cy="80" r="12" fill="#ffd9a8"/>
            </svg>
          </div>
          <div class="sala-lado">
            <div class="sala-linha-topo">
              <h3 class="sala-sub">Linha do tempo da casa</h3>
              <button type="button" class="sala-ver-mais" id="sala-ver-mais" aria-expanded="false" aria-controls="sala-eventos" hidden>Ver tudo</button>
            </div>
            <ol id="sala-eventos" class="sala-eventos" aria-live="polite"><li class="texto-suave">Nada aconteceu ainda. Mexa em algo ou dê play num filme.</li></ol>
          </div>
        </div>
        <div class="voz-painel" id="voz-dica" hidden aria-live="polite"></div>
      </section>`;
  },

  /**
   * Liga a sala na tela. estado = { dispositivos, cinema }.
   * acoes = { cenas, dispositivos, recarregar } para o comando de voz.
   * Devolve { atualizarCinema(est), atualizarDispositivo(tipo ou id, mudancas) }.
   */
  montar(raiz, estado, acoes) {
    const s = { dispositivos: estado.dispositivos.map((d) => ({ ...d })), cinema: estado.cinema || { ativo: false } };
    const el = (id) => raiz.querySelector('#' + id);
    const disp = (tipo) => s.dispositivos.find((d) => d.tipo === tipo);

    function pintar() {
      const luz = disp('iluminacao');
      const cin = s.cinema;
      const ligada = luz && luz.ligado;
      const brilho = ligada ? Number(luz.valor) : 0;
      const cor = ligada ? (cin.ativo && cin.cor_atual) || '#ffd9a8' : '#3b3b52';
      el('sala-lampada').setAttribute('fill', cor);
      el('sala-brilho-cor').setAttribute('stop-color', cor);
      el('sala-brilho-fim').setAttribute('stop-color', cor);
      el('sala-halo').setAttribute('opacity', ligada ? (0.25 + brilho / 100 * 0.75).toFixed(2) : '0');
      el('sala-escuro').setAttribute('opacity', (ligada ? 0.55 - brilho / 100 * 0.5 : 0.72).toFixed(2));

      // Cortinas: cada lado cobre 12 (abertas), 38 (meio) ou 70 (fechadas) de 140 da janela
      const cort = disp('cortinas');
      const lado = { abertas: 12, meio: 38, fechadas: 70 }[cort?.estado] ?? 70;
      el('sala-cortina-esq').setAttribute('width', lado);
      el('sala-cortina-dir').setAttribute('x', 170 - lado);
      el('sala-cortina-dir').setAttribute('width', lado);

      const ar = disp('temperatura');
      el('sala-ar').textContent = ar && ar.ligado ? `${ar.valor}°C` : 'Desligado';
      el('sala-vento').classList.toggle('ligado', !!(ar && ar.ligado));

      const tv = disp('tv_som');
      const vol = tv && tv.ligado ? Number(tv.valor) : 0;
      el('sala-vol').setAttribute('width', Math.round(228 * vol / 100));
      const img = el('sala-tv-img');
      const posterUrl = cin.ativo && (cin.filme?.backdrop || cin.filme?.poster);
      if (posterUrl) {
        if (img.getAttribute('href') !== posterUrl) img.setAttribute('href', posterUrl);
        img.setAttribute('opacity', '1');
        el('sala-tv-txt').textContent = '';
      } else {
        img.setAttribute('opacity', '0');
        el('sala-tv-txt').textContent = tv && tv.ligado ? 'TV ligada · escolha um filme' : 'TV em espera';
      }

      el('sala-pausa').hidden = !(cin.ativo && cin.pausado);
      const atm = cin.ativo && cin.atmosfera;
      el('sala-atmosfera').innerHTML = atm
        ? `<span class="badge" style="color:var(--gold)">ATMOSFERA</span>
           <b>${esc(atm.nome)}</b>
           <span class="texto-suave">${esc(atm.descricao)}</span>
           <span class="sala-chips">
             <span class="chip">${icone('lampada')} ${atm.brilho}%</span><span class="chip">${icone('ondas')} ${esc(EFEITOS[atm.efeito] || atm.efeito)}</span>
             <span class="chip">${icone('cortina')} ${esc(atm.cortinas)}</span><span class="chip">${icone('termometro')} ${atm.temperatura_ar}°C</span><span class="chip">${icone('som')} ${atm.volume}%</span>
           </span>`
        : `<span class="badge">ATMOSFERA</span><span class="texto-suave">Aperte <b>Assistir agora</b> num filme: os gêneros dele decidem o clima da sala (terror pulsa, ação dá um clarão, romance fica quente...). Ou diga "LetterRoyale, modo terror".</span>`;
    }

    // ---- Linha do tempo (eventos que o back publica)
    const rota = location.hash;
    const vivo = () => location.hash === rota && document.body.contains(raiz);
    let ultimaAssinatura = '';
    async function buscarEventos() {
      try {
        const lista = await API.eventosCasa(12);
        const assinatura = lista.map((e) => e.instante + e.texto).join('|');
        if (assinatura === ultimaAssinatura) return;
        ultimaAssinatura = assinatura;
        el('sala-eventos').innerHTML = lista.length
          ? lista.map((e) => `<li><span class="sala-ev-ico">${icone(ICONE_EVENTO[e.tipo] || 'casa')}</span><span>${esc(e.texto)}<small>${quandoFoi(e.instante)}</small></span></li>`).join('')
          : '<li class="texto-suave">Nada aconteceu ainda. Mexa em algo ou dê play num filme.</li>';
        el('sala-ver-mais').hidden = lista.length <= 3;   // janelinha: mostra 3, o resto abre no "Ver tudo"
      } catch { /* sem conexão: tenta de novo */ }
    }
    const verMais = el('sala-ver-mais');
    verMais.onclick = () => {
      const aberto = el('sala-eventos').classList.toggle('aberta');
      verMais.setAttribute('aria-expanded', String(aberto));
      verMais.textContent = aberto ? 'Mostrar menos' : 'Ver tudo';
    };
    buscarEventos();
    const timer = setInterval(() => {
      if (vivo()) return buscarEventos();
      clearInterval(timer);
      GestosCamera.desligar();   // saiu da tela: desliga câmera e microfone
      ComandoVoz.parar();
    }, 2500);

    el('sala-celular').onclick = () => abrirQrControle();
    const botaoGestos = el('sala-gestos');
    if (GestosCamera.ativo) GestosCamera.marcarBotao(botaoGestos, true);
    botaoGestos.onclick = () => GestosCamera.ligar(botaoGestos, () => setTimeout(acoes.recarregar, 300));

    const api = {
      atualizarCinema(est) { s.cinema = est || { ativo: false }; pintar(); },
      atualizarDispositivo(tipo, mudancas) {
        const d = /^\d+$/.test(String(tipo)) ? s.dispositivos.find((x) => String(x.id) === String(tipo)) : disp(tipo);
        if (d) { Object.assign(d, mudancas); sincronizarLinha(raiz, d); }
        pintar();
        setTimeout(buscarEventos, 400);   // o evento novo aparece logo
      },
    };
    ComandoVoz.ligar(raiz, acoes, (tipo, mudancas) => api.atualizarDispositivo(tipo, mudancas));
    pintar();
    return api;
  },
};

/**
 * Comando de voz / celular mudou um aparelho -> a linha dele em "Dispositivos" acompanha
 * (botão de liga/desliga, slider, cortinas e o texto embaixo do nome).
 */
function sincronizarLinha(raiz, d) {
  const linha = $(`.dispositivo[data-disp="${d.id}"]`, raiz);
  if (!linha) return;
  const ligado = d.ligado !== false && d.ligado !== 0;
  const chave = $(`[data-ligar="${d.id}"]`, linha);
  if (chave) chave.checked = ligado;
  linha.classList.toggle('desligado', !ligado);
  $$('.controle, .controle button', linha).forEach((el) => { el.disabled = !ligado; });
  const range = $(`[data-valor="${d.id}"]`, linha);
  if (range && d.valor != null) { range.value = d.valor; pintarRange(range); }
  const botao = d.estado && $(`[data-estado="${d.estado}"][data-id="${d.id}"]`, linha);
  if (botao && !botao.classList.contains('ativo')) escolherSegmento(botao);
  const sub = $(`#sub-${d.id}`, linha);
  if (sub) {
    if (d.tipo === 'iluminacao') sub.textContent = `${d.valor}%`;
    else if (d.tipo === 'temperatura') sub.textContent = `${d.valor}°C`;
    else if (d.tipo === 'cortinas') sub.textContent = d.estado;
    else if (d.tipo === 'tv_som') sub.textContent = sub.textContent.replace(/Volume \d+%/, `Volume ${d.valor}%`);
  }
}

const EFEITOS = { constante: 'luz constante', respirar: 'respirando', pulsar: 'pulsando', relampago: 'um clarão' };

function quandoFoi(ms) {
  const s = Math.max(0, Math.round((Date.now() - ms) / 1000));
  if (s < 10) return 'agora';
  if (s < 60) return `há ${s} s`;
  if (s < 3600) return `há ${Math.floor(s / 60)} min`;
  return new Date(ms).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}

// =====================================================================
// COMANDO DE VOZ — reconhecimento de fala do próprio navegador
// (Web Speech API do Chrome/Edge). Não precisa instalar nada.
//
//  • clique 1x: fica OUVINDO SEM PARAR (vários comandos seguidos); clique de novo pra desligar
//  • "LetterRoyale" na frase é opcional (palavra de chamada; aceita "ler royale", "letter royal"...)
//  • entende número falado por extenso ("vinte e dois graus")
//  interpretar() é separado do microfone pra dar pra testar sem falar nada.
// =====================================================================
const NUMEROS_FALADOS = {
  zero: 0, um: 1, uma: 1, dois: 2, duas: 2, tres: 3, quatro: 4, cinco: 5, seis: 6, sete: 7, oito: 8, nove: 9, dez: 10,
  onze: 11, doze: 12, treze: 13, quatorze: 14, catorze: 14, quinze: 15, dezesseis: 16, dezessete: 17, dezoito: 18, dezenove: 19,
  vinte: 20, trinta: 30, quarenta: 40, cinquenta: 50, sessenta: 60, setenta: 70, oitenta: 80, noventa: 90, cem: 100, cento: 100,
};

const ComandoVoz = {
  /** "vinte e dois" -> 22 · "40" -> 40 · sem número -> null */
  numero(t) {
    const m = t.match(/(\d{1,3})/);
    if (m) return Number(m[1]);
    let total = null;
    for (const p of t.split(/\s+/)) {
      if (p in NUMEROS_FALADOS) total = (total || 0) + NUMEROS_FALADOS[p];
      else if (p !== 'e' && total != null) break;
    }
    return total;
  },

  /** Texto falado -> ação. Devolve null se não entendeu. */
  interpretar(texto) {
    const t = ` ${String(texto || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[.,!?;:-]/g, ' ')
      // palavra de chamada: "LetterRoyale", "ler royale", "letter royal", "leter roial", "royale"...
      .replace(/\b(ok |ei |hey |oi )?((letter|leter|letra|ler|lete|lett?re|liter|lerer)\s?)?(royale?|roial|roiale|royal|royali)\b/g, ' ')
      .replace(/\bletterroyale?\b/g, ' ')
      .replace(/\s+/g, ' ')} `;
    const tem = (...palavras) => palavras.some((p) => t.includes(p));
    const n = () => this.numero(t);

    if (tem(' encerrar', ' terminar', 'acabar o filme', 'fim do filme')) return { acao: 'terminar' };
    if (tem('continuar', 'voltar ao filme', 'voltar pro filme', 'volta o filme', 'volta pro filme', 'retomar', 'despausar', ' play ', 'dar play')) return { acao: 'retomar' };
    if (tem('intervalo', ' pausa', 'pausar', ' pause')) return { acao: 'pausar' };
    if (tem('proximo da fila', 'comecar a fila', 'comecar fila', 'proximo filme')) return { acao: 'fila' };
    if (tem('ritmo')) {
      if (tem('calmo', 'devagar', 'lento')) return { acao: 'ritmo', ritmo: 'calmo' };
      if (tem('festa', 'rapido')) return { acao: 'ritmo', ritmo: 'festa' };
      if (tem('normal')) return { acao: 'ritmo', ritmo: 'normal' };
    }
    if (tem('cortina')) {
      if (tem('abr', 'abra')) return { acao: 'dispositivo', tipo: 'cortinas', estado: 'abertas' };
      if (tem('fech')) return { acao: 'dispositivo', tipo: 'cortinas', estado: 'fechadas' };
      if (tem('meio', 'metade', 'meia')) return { acao: 'dispositivo', tipo: 'cortinas', estado: 'meio' };
    }
    if (tem(' ar ', 'ar-condicionado', 'ar condicionado', 'temperatura', 'graus')) {
      if (tem('deslig')) return { acao: 'dispositivo', tipo: 'temperatura', ligado: false };
      if (n() != null) return { acao: 'dispositivo', tipo: 'temperatura', valor: Math.max(16, Math.min(30, n())), ligado: true };
      if (tem('lig')) return { acao: 'dispositivo', tipo: 'temperatura', ligado: true };
    }
    if (tem('volume', ' som ')) {
      if (tem('mudo', 'deslig')) return { acao: 'dispositivo', tipo: 'tv_som', ligado: false };
      if (n() != null) return { acao: 'dispositivo', tipo: 'tv_som', valor: Math.min(100, n()), ligado: true };
    }
    const atm = this.atmosfera(t);
    if (atm && tem('modo ', 'clima', 'atmosfera', 'ambiente')) return { acao: 'atmosfera', chave: atm };
    if (tem('modo cinema', ' cinema ')) return { acao: 'cena', nome: 'Modo Cinema' };
    if (tem('maratona')) return { acao: 'cena', nome: 'Maratona' };
    if (tem('credito')) return { acao: 'cena', nome: 'Créditos' };
    if (tem(' luz', 'lampada', 'brilho', ' cor ')) {
      const cor = this.cor(t);
      if (cor) return { acao: 'cor', ...cor };
      if (tem('apag', 'deslig')) return { acao: 'dispositivo', tipo: 'iluminacao', ligado: false };
      if (n() != null) return { acao: 'dispositivo', tipo: 'iluminacao', valor: Math.min(100, n()), ligado: true };
      if (tem('acend', 'lig')) return { acao: 'dispositivo', tipo: 'iluminacao', ligado: true };
    }
    if (tem('desativar', 'cancelar cena')) return { acao: 'desativar' };
    if (atm) return { acao: 'atmosfera', chave: atm };   // só "terror", "comédia"... também vale
    return null;
  },

  /** "luz azul", "lâmpada vermelha", "cor verde"... -> { hex, nome } (ou null). */
  CORES: [
    ['azul claro', '#00c8ff', 'azul-claro'], ['verde agua', '#00e0b0', 'verde-água'], ['azul', '#1e3cff', 'azul'],
    ['vermelh', '#ff1a1a', 'vermelha'], ['verde', '#1aff3c', 'verde'], ['amarel', '#ffc400', 'amarela'],
    ['laranja', '#ff6a00', 'laranja'], ['roxa', '#8a2be2', 'roxa'], ['roxo', '#8a2be2', 'roxa'], ['lilas', '#b57edc', 'lilás'],
    ['rosa', '#ff4fa3', 'rosa'], ['pink', '#ff4fa3', 'rosa'], ['ciano', '#00e5ff', 'ciano'], ['dourad', '#ffb300', 'dourada'],
    ['branc', '#ffffff', 'branca'], ['quente', '#ffb46b', 'quente'], ['fria', '#cfe6ff', 'fria'],
  ],
  cor(t) {
    const achada = this.CORES.find(([palavra]) => t.includes(palavra));
    return achada ? { hex: achada[1], nome: achada[2] } : null;
  },

  /** Acha uma atmosfera na frase (ou null). */
  atmosfera(t) {
    const ATM = [['terror', ['terror', ' medo', 'assustador']], ['suspense', ['suspense', 'misterio', 'policial']],
      ['acao', [' acao', 'explosao']], ['ficcao', ['ficcao', 'fantasia', 'aventura', 'espacial']],
      ['romance', ['romance', 'romantico']], ['comedia', ['comedia', 'engracado']], ['animacao', ['animacao', 'desenho']], ['familia', ['familia', 'infantil']],
      ['musical', ['musica', 'musical', ' show']], ['drama', ['drama']], ['documentario', ['documentario']], ['classico', ['classico']]];
    const achada = ATM.find(([, palavras]) => palavras.some((p) => t.includes(p)));
    return achada ? achada[0] : null;
  },

  disponivel() {
    return !!(window.SpeechRecognition || window.webkitSpeechRecognition);
  },

  rec: null,
  ouvindo: false,

  ERROS: {
    'not-allowed': 'O navegador bloqueou o microfone. Clique no cadeado da barra de endereço e permita o microfone.',
    'service-not-allowed': 'O navegador bloqueou o microfone. Clique no cadeado da barra de endereço e permita o microfone.',
    'audio-capture': 'Nenhum microfone encontrado. Confira se ele está conectado e escolhido no Windows.',
    network: 'O reconhecimento de voz do Chrome precisa de internet. Confira a conexão.',
    'language-not-supported': 'Este navegador não entende português. Use o Chrome ou o Edge.',
  },

  parar() {
    this.ouvindo = false;
    clearTimeout(this.semResposta);
    try { this.rec?.stop(); } catch { /* já parou */ }
  },

  ligar(raiz, acoes, aoMudarDispositivo) {
    const botao = raiz.querySelector('#voz');
    const painel = raiz.querySelector('#voz-dica');
    if (!botao) return;
    if (!this.disponivel()) {
      botao.title = 'Comando de voz funciona no Chrome ou Edge';
      botao.onclick = () => toast('O comando de voz funciona no Chrome ou no Edge.');
      return;
    }
    const Reconhecimento = window.SpeechRecognition || window.webkitSpeechRecognition;
    const marcar = (ligado) => {
      botao.classList.toggle('ouvindo', ligado);
      botao.setAttribute('aria-pressed', String(ligado));
      const rotulo = botao.querySelector('span');
      if (rotulo) rotulo.textContent = 'Voz';   // o texto não muda: o botão vermelho já mostra que está ouvindo
      botao.title = ligado ? 'Ouvindo: clique pra desligar' : 'Clique e fale. Ex.: LetterRoyale, modo terror';
    };
    const mostrar = (html) => { painel.hidden = false; painel.innerHTML = html; };
    const EXEMPLOS = '"LetterRoyale, modo terror" · "intervalo" · "continuar" · "fechar cortinas" · "luz 40 por cento" · "ar vinte e dois graus"';
    // Página nova = começa do zero. Antes, o "ouvindo" de uma tela antiga ficava ligado:
    // o botão aparecia vermelho mas ninguém ouvia, e o 1º toque só desligava, sem mensagem nenhuma.
    if (this.ouvindo) this.parar();
    marcar(false);

    botao.onclick = () => {
      if (this.ouvindo) { this.parar(); marcar(false); painel.hidden = true; return; }
      this.ouvindo = true;
      this.comecou = false;
      marcar(true);
      mostrar(`<b>Ouvindo.</b> Fale um comando: depois que eu entender, o microfone desliga sozinho.<br><span class="texto-suave">Ex.: ${EXEMPLOS}</span>`);
      const comecar = () => {
        const rec = new Reconhecimento();
        this.rec = rec;
        rec.lang = 'pt-BR';
        rec.continuous = !/Android/i.test(navigator.userAgent);   // PC: não para depois da 1ª frase. Android: o onend religa (lá o "contínuo" repete frases)
        rec.interimResults = true;    // mostra o que está ouvindo enquanto você fala
        rec.maxAlternatives = 5;      // tenta as 5 interpretações do Chrome antes de desistir
        rec.onresult = async (ev) => {
          for (let i = ev.resultIndex; i < ev.results.length; i++) {
            const r = ev.results[i];
            const alternativas = [...r].map((a) => a.transcript.trim());
            if (!r.isFinal) { mostrar(`<b>Ouvindo:</b> "${esc(alternativas[0])}"`); continue; }
            const comando = alternativas.map((a) => this.interpretar(a)).find(Boolean);
            if (!comando) {
              mostrar(`Não entendi "<b>${esc(alternativas[0])}</b>". Tente: <span class="texto-suave">${EXEMPLOS}</span>`);
              continue;
            }
            mostrar(`Entendi: "<b>${esc(alternativas[0])}</b>"`);
            // Um comando por vez: entendeu, desliga o microfone (toque de novo pra outro comando)
            this.parar();
            marcar(false);
            try { await this.executar(comando, acoes, aoMudarDispositivo); } catch (e) { mostrar(`Entendi, mas deu erro: ${esc(e.message)}`); }
            return;
          }
        };
        rec.onerror = (ev) => {
          if (ev.error === 'no-speech' || ev.error === 'aborted') return;   // silêncio: segue ouvindo
          this.ouvindo = false;
          marcar(false);
          // Celular abrindo pelo IP (http://192.168...): o Chrome só libera microfone em site seguro.
          // Não é o site que bloqueia: precisa liberar este endereço uma vez nas flags do Chrome.
          const inseguro = !window.isSecureContext;
          mostrar(ev.error === 'not-allowed' && inseguro
            ? `O Chrome do celular só libera o microfone em site seguro (https). Pra liberar este endereço uma vez:
               <ol class="voz-passos"><li>Abra uma aba nova e digite <b>chrome://flags</b></li>
               <li>Procure <b>Insecure origins treated as secure</b></li>
               <li>Escreva <b>${esc(location.origin)}</b>, mude pra <b>Enabled</b> e toque em <b>Relaunch</b></li>
               <li>Volte aqui e toque no microfone de novo</li></ol>`
            : esc(this.ERROS[ev.error] || 'Microfone: ' + ev.error));
        };
        // O Chrome para sozinho depois de um tempo em silêncio (no celular, depois de CADA frase):
        // se ainda está ligado, volta a ouvir. Espera um pouquinho antes: no Android, religar na hora
        // dava erro escondido e o microfone ficava "ligado" mas surdo.
        rec.onend = () => {
          if (this.ouvindo && document.body.contains(botao)) setTimeout(() => { if (this.ouvindo) comecar(); }, 250);
          else marcar(false);
        };
        // Se o microfone não começar em 4 s, avisa em vez de ficar parado sem dizer nada
        clearTimeout(this.semResposta);
        this.semResposta = setTimeout(() => {
          if (!this.ouvindo || this.comecou) return;
          this.parar(); marcar(false);
          mostrar(!window.isSecureContext
            ? `O microfone não respondeu. No celular, o Chrome só libera o microfone em site seguro (https). Pra liberar este endereço uma vez:
               <ol class="voz-passos"><li>Abra uma aba nova e digite <b>chrome://flags</b></li>
               <li>Procure <b>Insecure origins treated as secure</b></li>
               <li>Escreva <b>${esc(location.origin)}</b>, mude pra <b>Enabled</b> e toque em <b>Relaunch</b></li>
               <li>Volte aqui e toque no microfone de novo</li></ol>`
            : 'O microfone não respondeu. Confira se o Chrome tem permissão de microfone (cadeado na barra de endereço) e toque de novo.');
        }, 4000);
        rec.onstart = () => { this.comecou = true; clearTimeout(this.semResposta); };
        try {
          rec.start();
          falhas = 0;
        } catch {
          // Ainda não soltou o microfone: tenta de novo algumas vezes antes de avisar
          if (++falhas <= 4) setTimeout(() => { if (this.ouvindo) comecar(); }, 400 * falhas);
          else { this.ouvindo = false; marcar(false); mostrar('O microfone travou. Toque no botão de novo pra voltar a ouvir.'); }
        }
      };
      let falhas = 0;
      comecar();
    };
  },

  async executar(c, { cenas, dispositivos, recarregar }, aoMudarDispositivo) {
    const avisar = (r) => r && r.aviso && toast(r.aviso);
    const NOMES_ATM = { terror: 'Terror', suspense: 'Suspense', acao: 'Ação', ficcao: 'Ficção e aventura', romance: 'Romance', comedia: 'Comédia', animacao: 'Animação', familia: 'Família', musical: 'Música', drama: 'Drama', documentario: 'Documentário', classico: 'Cinema clássico' };
    switch (c.acao) {
      case 'cena': {
        const cena = cenas.find((x) => x.nome === c.nome);
        if (!cena) throw new Error(`Cena ${c.nome} não existe.`);
        avisar(await API.ativarCena(cena.id));
        toast(`${c.nome} ativado`);
        return recarregar();
      }
      case 'desativar': await API.desativarCena(); return recarregar();
      case 'terminar': await API.cinemaTerminar(); verificarFimDeFilme(); return recarregar();
      case 'fila': await API.assistirProximo(); toast('Bom filme!'); return recarregar();
      case 'ritmo': await API.cinemaRitmo(c.ritmo); toast(`Ritmo ${c.ritmo}`); return recarregar();
      case 'atmosfera': avisar(await API.atmosferaManual(c.chave)); toast(`Atmosfera ${NOMES_ATM[c.chave] || c.chave}`); return recarregar();
      case 'retomar': await API.cinemaRetomar(); toast('Voltando ao filme'); return recarregar();
      case 'pausar':
        try { await API.cinemaPausar(); toast('Intervalo'); } catch {
          const pausa = cenas.find((x) => x.nome === 'Pausa');   // sem filme passando: usa a cena Pausa
          if (!pausa) throw new Error('Nenhum filme passando.');
          avisar(await API.ativarCena(pausa.id));
          toast('Cena Pausa');
        }
        return recarregar();
      case 'cor': {
        const luz = dispositivos.find((x) => x.tipo === 'iluminacao');
        const brilho = luz && luz.ligado !== false && Number(luz.valor) > 0 ? Number(luz.valor) : 80;
        avisar(await API.corLampada(c.hex, brilho, c.nome));
        if (luz) { Object.assign(luz, { ligado: true, valor: brilho }); aoMudarDispositivo('iluminacao', { ligado: true, valor: brilho, cor: c.hex }); }
        toast(`Luz ${c.nome}`);
        return undefined;
      }
      case 'dispositivo': {
        const d = dispositivos.find((x) => x.tipo === c.tipo);
        if (!d) throw new Error('Aparelho não encontrado.');
        const dados = {};
        if (c.valor != null) dados.valor = c.valor;
        if (c.estado != null) dados.estado = c.estado;
        if (c.ligado != null) dados.ligado = c.ligado;
        avisar(await API.dispositivo(d.id, dados));
        Object.assign(d, dados);
        aoMudarDispositivo(c.tipo, dados);
        toast(`${d.nome}: ${c.estado || (c.ligado === false ? generoEstado(d.nome, false) : c.valor != null ? c.valor + (c.tipo === 'temperatura' ? '°C' : '%') : generoEstado(d.nome, true))}`);
        return undefined;   // sem redesenhar a tela: o microfone continua ouvindo
      }
      default: return undefined;
    }
  },
};
