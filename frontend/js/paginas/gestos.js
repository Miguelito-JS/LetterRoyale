// =====================================================================
// GESTOS PELA WEBCAM (beta) — a mão vira controle remoto
//
//   mão aberta (segurar ~1 s)  -> Intervalo (pausa o filme, luz sobe)
//   joinha     (segurar ~1 s)  -> Volta pro filme
//
// Usa o MediaPipe Gesture Recognizer do Google (IA que roda NO NAVEGADOR:
// a imagem da câmera não sai do computador). Na 1ª vez baixa o modelo
// (~8 MB) da internet. Funciona no Chrome/Edge em http://localhost.
// =====================================================================
const GestosCamera = {
  VERSAO: '0.10.14',
  SEGURAR_MS: 900,       // gesto precisa ficar parado esse tempo (evita disparo sem querer)
  ESPERA_MS: 3000,       // depois de disparar, espera antes de aceitar outro
  CONFIANCA: 0.55,

  ativo: false,
  video: null,
  reconhecedor: null,
  ultimo: { nome: null, desde: 0 },
  disparouEm: -Infinity,

  /** Gesto do MediaPipe -> ação do site. */
  ACOES: {
    Open_Palm: { gesto: 'mão aberta', texto: 'Intervalo', rodar: () => API.cinemaPausar() },
    Thumb_Up: { gesto: 'joinha', texto: 'Voltando ao filme', rodar: () => API.cinemaRetomar() },
  },

  /**
   * Plano B, sem IA: olha só a posição dos pontos da mão (21 pontos do MediaPipe).
   * Não importa se é a mão direita ou a esquerda, nem se a câmera está espelhada:
   * só compara distâncias até o pulso e a altura do polegar.
   * Devolve 'Thumb_Up', 'Open_Palm' ou null.
   */
  pelaGeometria(p) {
    if (!p || p.length < 21) return null;
    const d = (a, b) => Math.hypot(p[a].x - p[b].x, p[a].y - p[b].y);
    const mao = d(0, 9) || 1e-6;                       // tamanho da mão (pulso até a base do dedo do meio)
    const dedos = [[8, 6], [12, 10], [16, 14], [20, 18]]; // [ponta, junta do meio]
    const esticados = dedos.filter(([ponta, junta]) => d(ponta, 0) > d(junta, 0) * 1.12).length;
    const dobrados = dedos.filter(([ponta, junta]) => d(ponta, 0) < d(junta, 0) * 1.02).length;
    const pontaMaisAlta = Math.min(...dedos.map(([ponta]) => p[ponta].y));
    const polegarEmPe = p[4].y < p[3].y && p[3].y < p[2].y && p[4].y < pontaMaisAlta - mao * 0.25 && d(4, 0) > mao * 0.9;
    if (dobrados >= 3 && polegarEmPe) return 'Thumb_Up';
    if (esticados === 4 && d(4, 5) > mao * 0.45) return 'Open_Palm';
    return null;
  },

  /** Junta a IA com o plano B, olhando as duas mãos. */
  gestoDoQuadro(r) {
    const maos = r.landmarks?.length || 0;
    for (let i = 0; i < maos; i++) {
      const g = r.gestures?.[i]?.[0];
      if (g && this.ACOES[g.categoryName] && g.score >= this.CONFIANCA) return g.categoryName;
    }
    for (let i = 0; i < maos; i++) {
      const g = this.pelaGeometria(r.landmarks[i]);
      if (g) return g;
    }
    return null;
  },

  /** Decide se o gesto atual já ficou parado tempo suficiente (separado pra dar pra testar). */
  decidir(nome, agora) {
    if (!nome || !this.ACOES[nome]) { this.ultimo = { nome: null, desde: agora }; return null; }
    if (this.ultimo.nome !== nome) { this.ultimo = { nome, desde: agora }; return null; }
    if (agora - this.ultimo.desde < this.SEGURAR_MS) return null;
    if (agora - this.disparouEm < this.ESPERA_MS) return null;
    this.disparouEm = agora;
    this.ultimo = { nome: null, desde: agora };
    return nome;
  },

  async ligar(botao, aoMudar) {
    if (this.ativo) return this.desligar(botao);
    if (!navigator.mediaDevices?.getUserMedia) { toast('Este navegador não deixa usar a câmera.'); return; }
    botao.disabled = true;
    botao.classList.add('carregando-gestos');   // só muda a cor enquanto a IA carrega (o texto fica igual)
    botao.title = 'Carregando a IA dos gestos...';
    try {
      if (!this.reconhecedor) {
        const base = `https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@${this.VERSAO}`;
        const { GestureRecognizer, FilesetResolver } = await import(`${base}/vision_bundle.mjs`);
        const arquivos = await FilesetResolver.forVisionTasks(`${base}/wasm`);
        this.reconhecedor = await GestureRecognizer.createFromOptions(arquivos, {
          baseOptions: {
            modelAssetPath: 'https://storage.googleapis.com/mediapipe-models/gesture_recognizer/gesture_recognizer/float16/1/gesture_recognizer.task',
            delegate: 'GPU',
          },
          runningMode: 'VIDEO',
          numHands: 2,                 // qualquer uma das duas mãos vale
        });
      }
      const stream = await navigator.mediaDevices.getUserMedia({ video: { width: 320, height: 240 }, audio: false });
      this.video = Object.assign(document.createElement('video'), { autoplay: true, muted: true, playsInline: true, srcObject: stream });
      this.video.className = 'gestos-video';
      this.video.setAttribute('aria-label', 'Câmera dos gestos');
      document.body.appendChild(this.video);
      await this.video.play();
      this.ativo = true;
      this.marcarBotao(botao, true);
      toast('Gestos ligados: mão aberta = intervalo · joinha = voltar ao filme');
      this.loop(aoMudar);
    } catch (e) {
      console.error(e);
      toast(e.name === 'NotAllowedError' ? 'Permita a câmera pra usar os gestos (cadeado na barra de endereço).' : 'Não consegui carregar os gestos (precisa de internet na 1ª vez).');
      this.desligar(botao);
    } finally {
      botao.disabled = false;
    }
  },

  loop(aoMudar) {
    let ultimoFrame = -1;
    const passo = async () => {
      if (!this.ativo || !this.video) return;
      if (this.video.currentTime !== ultimoFrame) {
        ultimoFrame = this.video.currentTime;
        const r = this.reconhecedor.recognizeForVideo(this.video, performance.now());
        const nome = this.gestoDoQuadro(r);
        this.video.classList.toggle('reconhecendo', !!(nome && this.ACOES[nome]));
        const disparar = this.decidir(nome, Date.now());
        if (disparar) {
          const a = this.ACOES[disparar];
          try { await a.rodar(); toast(`${a.texto} (${a.gesto})`); if (aoMudar) aoMudar(); }
          catch (e) { toast(e.message); }
        }
      }
      requestAnimationFrame(passo);
    };
    requestAnimationFrame(passo);
  },

  desligar(botao) {
    this.ativo = false;
    if (this.video) {
      this.video.srcObject?.getTracks().forEach((t) => t.stop());
      this.video.remove();
      this.video = null;
    }
    if (botao) this.marcarBotao(botao, false);
  },

  /** Botão "Gestos": aceso enquanto a câmera está ligada. */
  marcarBotao(botao, ligado) {
    botao.classList.toggle('ouvindo', ligado);
    botao.setAttribute('aria-pressed', String(ligado));
    botao.classList.remove('carregando-gestos');
    this.rotulo(botao, 'Gestos');   // o texto não muda: o botão vermelho já mostra que está ligado
    botao.title = ligado ? 'Gestos ligados: clique pra desligar' : 'Webcam: mão aberta = intervalo, joinha = voltar';
  },

  rotulo(botao, texto) {
    const span = botao.querySelector('span');
    if (span) span.textContent = texto; else botao.textContent = texto;
  },
};
