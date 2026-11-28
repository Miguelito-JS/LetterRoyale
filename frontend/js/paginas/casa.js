// Tela Smart Home (#/casa)
window.Paginas = window.Paginas || {};

const ICONE_CENA = { 'Modo Cinema': 'filmes', Maratona: 'relogio', Pausa: 'power', 'Créditos': 'lampada' };

Paginas.casa = {
  titulo: 'Smart Home',
  async render(raiz) {
    const c = await API.casa();
    const ativa = c.cenas.find((x) => x.ativa);
    const cin = c.cinema || { ativo: false };
    const RITMOS = { calmo: 'Calmo', normal: 'Normal', festa: 'Festa' };

    // Texto do card quando o Modo Cinema (cores do pôster) está ligado
    const descricaoCinema = () => {
      if (!cin.filme) return 'Abra a página de um filme: a luz pega as cores do pôster.';
      const fim = cin.termina_em ? ` • termina às ${new Date(String(cin.termina_em).replace(' ', 'T')).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}` : '';
      const ep = textoEpisodio(cin.filme);
      return (cin.modo === 'assistindo' ? `Assistindo ${cin.filme.titulo}${ep ? ` (${ep})` : ''}` : `Acompanhando: ${cin.filme.titulo}`) + fim;
    };

    const controle = (d) => {
      if (d.tipo === 'cortinas') {
        return `<div class="segmentado controle" role="group" aria-label="Cortinas">
          ${['abertas', 'meio', 'fechadas'].map((e) => `<button data-estado="${e}" data-id="${d.id}" class="${d.estado === e ? 'ativo' : ''}">${e === 'meio' ? 'Meio' : e[0].toUpperCase() + e.slice(1)}</button>`).join('')}
        </div>`;
      }
      const [min, max] = d.tipo === 'temperatura' ? [16, 30] : [0, 100];
      return `<input class="controle" type="range" min="${min}" max="${max}" value="${d.valor}" data-valor="${d.id}" aria-label="${esc(d.nome)}">`;
    };
    // TV e Som real: mostra de onde vem e pra onde vai o som
    // ex.: "🎧 JBL Tune 510BT · Volume 30%" ou "📱 Galaxy A54 → Realtek(R) Audio · Volume 30%"
    // "Fones de ouvido (JBL Tune)" -> "JBL Tune" (o que está no último parêntese, mesmo com "(R)" dentro)
    const nomeCurto = (n) => n.match(/\(((?:[^()]|\([^()]*\))+)\)$/)?.[1] || n;
    const celularTocando = c.som?.celulares_tocando?.[0];
    const saidaSom = c.som?.modo === 'real' && c.som.nome
      ? `${celularTocando ? `${celularTocando} → ` : ''}${nomeCurto(c.som.nome)} · ` : '';
    const sub = (d) => ({ iluminacao: `${d.valor}%`, cortinas: d.estado, temperatura: `${d.valor}°C`, tv_som: `${saidaSom}Volume ${d.valor}%` }[d.tipo]);

    raiz.innerHTML = `
      <div class="colunas">
        <div>
          <section class="cena-ativa">
            <span class="ico ${cin.ativo && cin.filme?.poster ? 'com-poster' : ''}" id="ico-cena" style="${cin.ativo && cin.cor_atual ? `background:${esc(cin.cor_atual)};color:#fff;box-shadow:0 0 28px ${esc(cin.cor_atual)}` : ''}">${cin.ativo && cin.filme?.poster
              ? `<img src="${esc(cin.filme.poster)}" alt="${esc(cin.filme.titulo)}">`
              : icone(ativa ? ICONE_CENA[ativa.nome] || 'casa' : 'casa')}</span>
            <div style="flex:1;min-width:200px">
              ${ativa ? '<span class="badge" style="color:var(--gold)">ATIVO</span>' : ''}
              <h2 style="font-size:28px;margin:6px 0">${esc(ativa ? ativa.nome : 'Nenhuma cena ativa')}</h2>
              <p class="meta">${esc(cin.ativo ? descricaoCinema() : ativa ? ativa.descricao : 'Escolha uma cena rápida ao lado.')}</p>
              ${cin.ativo ? `
                <div class="cinema-info">
                  <span id="paleta-cinema">${paletaHtml(cin.paleta, cin.cor_atual)}</span>
                  <div class="segmentado" role="group" aria-label="Ritmo das cores">
                    ${Object.entries(RITMOS).map(([k, v]) => `<button data-ritmo="${k}" class="${cin.ritmo === k ? 'ativo' : ''}">${v}</button>`).join('')}
                  </div>
                  ${cin.modo === 'assistindo' ? `<button class="btn ${cin.pausado ? 'btn-primario' : ''}" id="intervalo" title="Pausa de verdade: o filme continua de onde parou">${cin.pausado ? `${icone('play')} Voltar ao filme` : `${icone('pausa')} Intervalo`}</button>` : ''}
                  ${cin.modo === 'assistindo' ? `<button class="btn" id="encerrar" title="Para a apresentação: simula o fim">${cin.filme?.episodio ? 'Encerrar episódio agora' : 'Encerrar filme agora'}</button>` : ''}
                </div>` : ''}
            </div>
            ${ativa ? '<button class="btn" id="desativar">Desativar</button>' : ''}
          </section>

          ${SalaAoVivo.html()}

          <section class="secao">
            <div class="secao-topo">
              <h2>Dispositivos</h2>
              <span style="display:flex;gap:10px;align-items:center">
                <span class="badge" title="Real = lâmpada de verdade. Simulação = só no banco.">${icone('lampada', 'width="14"')} ${c.lampada?.modo === 'real' ? 'Lâmpada real' : 'Simulação'}</span>
                <button class="link" id="gerenciar">Gerenciar</button>
              </span>
            </div>
            ${c.dispositivos.map((d) => `
              <div class="dispositivo ${d.ligado ? '' : 'desligado'}" data-disp="${d.id}">
                <span class="ico" style="width:38px;height:38px;border-radius:8px;background:var(--surface-raised);display:grid;place-items:center">${icone(ICONE_DISP[d.tipo], 'width="20"')}</span>
                <div><b>${esc(d.nome)}</b>${['cortinas', 'temperatura'].includes(d.tipo) ? ` <span class="badge disp-modo" title="${d.tuya_device_id ? 'Aparelho de verdade (Tuya)' : 'Só no sistema: cadastre em Gerenciar'}">${d.tuya_device_id ? 'Conectado' : 'Simulado'}</span>` : ''}<div class="meta" id="sub-${d.id}">${esc(sub(d))}</div></div>
                ${controle(d)}
                <label class="switch"><input type="checkbox" data-ligar="${d.id}" ${d.ligado ? 'checked' : ''} aria-label="Ligar ${esc(d.nome)}"><span></span></label>
              </div>`).join('')}
          </section>
        </div>

        <aside class="lateral">
          <section class="card">
            <h2 style="font-size:22px;margin-bottom:14px">Cenas rápidas</h2>
            ${c.cenas.filter((x) => x.nome !== 'Pausa').map((x) => `
              <button class="item ${x.ativa ? 'ativo' : ''}" data-cena="${x.id}">
                <span class="ico">${icone(ICONE_CENA[x.nome] || 'casa')}</span>
                <span class="txt">${esc(x.nome)}<small>${esc(x.descricao || '')}</small></span>
              </button>`).join('')}
          </section>
          <section class="card">
            <!-- preenchido pela Sala ao vivo (sala.js) -->
            <div id="sala-atmosfera" class="sala-atmosfera sala-atmosfera-lateral" aria-live="polite"></div>
          </section>
          <section class="card">
            <div class="secao-topo" style="margin-bottom:12px"><h2 style="font-size:22px">Fila do cinema</h2><span class="meta">${(c.fila || []).length} na fila</span></div>
            ${(c.fila || []).length ? `
              <ol class="fila-cinema">
                ${c.fila.map((x, i) => `
                  <li>
                    <a href="#/filme/${x.id}" class="fila-poster">${poster(x)}</a>
                    <a href="#/filme/${x.id}" class="fila-txt"><b>${i + 1}. ${esc(x.titulo)}</b><small>${esc(textoEpisodio(x)) || [x.ano, duracao(x.duracao_min)].filter(Boolean).join(' • ')}${x.episodio && x.duracao_min ? ` • ${duracao(x.duracao_min)}` : ''}</small></a>
                    ${i ? `<button class="icon-btn" data-subir="${x.fila_id}" aria-label="Subir ${esc(x.titulo)} na fila" title="Subir">${icone('seta', 'style="transform:rotate(-90deg)"')}</button>` : ''}
                    <button class="icon-btn" data-tirar-fila="${x.fila_id}" aria-label="Tirar ${esc(x.titulo)} da fila" title="Tirar da fila">${icone('x')}</button>
                  </li>`).join('')}
              </ol>
              ${cin.ativo && cin.modo === 'assistindo' ? '' : `<button class="btn btn-primario btn-bloco" id="fila-comecar" style="margin-top:12px">${icone('play')} Começar a fila</button>`}`
              : vazio('Nenhum filme na fila.')}
          </section>
        </aside>
      </div>`;

    const recarregar = () => Paginas.casa.render(raiz);
    // Sala ao vivo (gêmeo digital) + comando de voz: ver sala.js
    const sala = SalaAoVivo.montar(raiz, { dispositivos: c.dispositivos, cinema: cin }, { cenas: c.cenas, dispositivos: c.dispositivos, fila: c.fila || [], recarregar });
    // Se a lâmpada não responder, o back salva mesmo assim e manda um "aviso"
    const avisar = (r) => r && r.aviso && toast(r.aviso);
    const salvar = async (id, dados) => { try { avisar(await API.dispositivo(id, dados)); } catch (e) { toast(e.message); } };

    $$('[data-cena]', raiz).forEach((b) => (b.onclick = async () => {
      try { avisar(await API.ativarCena(b.dataset.cena)); } catch (e) { toast(e.message); }
      recarregar();
    }));
    $('#gerenciar').onclick = () => abrirGerenciar(recarregar);
    ligarSegmentados(raiz);   // a tela também é redesenhada sem passar pelo roteador

    // ---- Fila do cinema
    $$('[data-tirar-fila]', raiz).forEach((b) => (b.onclick = async () => { try { await API.sairFila(b.dataset.tirarFila); recarregar(); } catch (e) { toast(e.message); } }));
    $$('[data-subir]', raiz).forEach((b) => (b.onclick = async () => { try { await API.subirFila(b.dataset.subir); recarregar(); } catch (e) { toast(e.message); } }));
    const comecar = $('#fila-comecar');
    if (comecar) comecar.onclick = async () => { try { await API.assistirProximo(); toast('🎬 Bom filme!'); recarregar(); } catch (e) { toast(e.message); } };

    // ---- Modo Cinema: ritmo, encerrar, e a "lâmpada virtual" (ícone muda de cor junto com a real)
    // Sem redesenhar a tela: assim a pílula amarela desliza suave até a opção
    $$('[data-ritmo]', raiz).forEach((b) => (b.onclick = async () => {
      if (b.classList.contains('ativo')) return;
      const antes = b.parentElement.querySelector('button.ativo');
      escolherSegmento(b);
      try { await API.cinemaRitmo(b.dataset.ritmo); } catch (e) { toast(e.message); if (antes) escolherSegmento(antes); }
    }));
    const intervalo = $('#intervalo');
    if (intervalo) intervalo.onclick = async () => {
      try { await (cin.pausado ? API.cinemaRetomar() : API.cinemaPausar()); toast(cin.pausado ? 'Voltando ao filme' : 'Intervalo: o filme espera você'); } catch (e) { toast(e.message); }
      recarregar();
    };
    const enc = $('#encerrar');
    if (enc) enc.onclick = async () => { try { await API.cinemaTerminar(); verificarFimDeFilme(); recarregar(); } catch (e) { toast(e.message); } };

    if (cin.ativo) {
      const rota = location.hash;
      const timer = setInterval(async () => {
        if (location.hash !== rota || !document.body.contains(raiz)) return clearInterval(timer);
        try {
          const est = await API.cinema();
          if (!est.ativo) { clearInterval(timer); if (est.terminou) verificarFimDeFilme(); return recarregar(); }
          const ico = $('#ico-cena');
          if (ico && est.cor_atual) { ico.style.background = est.cor_atual; ico.style.color = '#fff'; ico.style.boxShadow = `0 0 28px ${est.cor_atual}`; }
          const pal = $('#paleta-cinema');
          if (pal) pal.innerHTML = paletaHtml(est.paleta, est.cor_atual);
          sala.atualizarCinema(est);
        } catch { /* sem conexão: tenta de novo */ }
      }, 1000);
    }
    const des = $('#desativar');
    if (des) des.onclick = async () => { await API.desativarCena(); recarregar(); };
    // Desligado = controles apagados e travados (mostra que não dá pra mexer)
    const travar = (id, ligado) => {
      const linha = $(`[data-disp="${id}"]`, raiz);
      if (!linha) return;
      linha.classList.toggle('desligado', !ligado);
      $$('.controle, .controle button', linha).forEach((el) => { el.disabled = !ligado; });
    };
    c.dispositivos.forEach((d) => travar(d.id, d.ligado));
    $$('[data-ligar]', raiz).forEach((i) => (i.onchange = () => { travar(i.dataset.ligar, i.checked); sala.atualizarDispositivo(i.dataset.ligar, { ligado: i.checked }); salvar(i.dataset.ligar, { ligado: i.checked }); }));
    $$('[data-estado]', raiz).forEach((b) => (b.onclick = async () => {
      if (b.classList.contains('ativo')) return;
      escolherSegmento(b);
      const subt = $(`#sub-${b.dataset.id}`);
      if (subt) subt.textContent = b.dataset.estado;
      sala.atualizarDispositivo(b.dataset.id, { estado: b.dataset.estado });
      await salvar(b.dataset.id, { estado: b.dataset.estado });
    }));
    // Slider: mostra o valor enquanto arrasta e só salva quando solta.
    // TV e Som real: o volume do computador/caixinha já muda enquanto arrasta (no máx. a cada 150 ms).
    $$('[data-valor]', raiz).forEach((r) => {
      const d = c.dispositivos.find((x) => String(x.id) === r.dataset.valor);
      const aoVivo = d.tipo === 'tv_som' && c.som?.modo === 'real';
      let ultimo = 0;
      r.oninput = () => {
        $(`#sub-${d.id}`).textContent = sub({ ...d, valor: r.value });
        if (aoVivo && Date.now() - ultimo > 150) { ultimo = Date.now(); API.dispositivo(d.id, { valor: Number(r.value) }).catch(() => {}); }
      };
      r.onchange = () => { sala.atualizarDispositivo(d.id, { valor: Number(r.value) }); salvar(d.id, { valor: Number(r.value) }); };
    });
  },
};

// "Gerenciar": escolhe o que configurar (lâmpada ou Bluetooth da TV e Som)
function abrirGerenciar(recarregar) {
  const dlg = document.createElement('dialog');
  dlg.innerHTML = `<h3>Gerenciar dispositivos</h3>
    <button class="item" data-painel="lampada"><span class="ico">${icone('lampada')}</span><span class="txt">Lâmpada inteligente<small>Testar, mudar a cor, escolher a lâmpada</small></span></button>
    <button class="item" data-painel="bluetooth"><span class="ico">${icone('tv')}</span><span class="txt">TV e Som · Bluetooth<small>Conectar caixinhas e fones ao computador</small></span></button>
    <button class="item" data-painel="aparelhos"><span class="ico">${icone('conexoes')}</span><span class="txt">Aparelhos conectados<small>O que está ligado à casa (Smart Life / Tuya)</small></span></button>
    <button class="item" data-painel="celular"><span class="ico">${icone('celular')}</span><span class="txt">Celular como controle<small>QR code: o celular controla a sala</small></span></button>
    <button class="item" data-painel="ajuda"><span class="ico">${icone('ajuda')}</span><span class="txt">Como conectar<small>Passo a passo de cada tecnologia</small></span></button>
    <div class="acoes"><button class="btn" data-fechar>Fechar</button></div>`;
  document.body.appendChild(dlg);
  dlg.showModal();
  dlg.addEventListener('close', () => dlg.remove());
  dlg.querySelector('[data-fechar]').onclick = () => dlg.close();
  dlg.querySelectorAll('[data-painel]').forEach((b) => (b.onclick = () => {
    dlg.close();
    if (b.dataset.painel === 'lampada') abrirPainelLampada();
    else if (b.dataset.painel === 'aparelhos') abrirPainelAparelhos(recarregar);
    else if (b.dataset.painel === 'celular') abrirQrControle();
    else if (b.dataset.painel === 'ajuda') abrirComoConectar();
    else abrirPainelBluetooth(recarregar);
  }));
}

// Painel Bluetooth: pareados (conectar/desconectar) + procurar aparelhos novos por perto.
// Quem conecta é o servidor (Windows); o aparelho conectado vira a saída de som e o slider de volume mexe nele.
async function abrirPainelBluetooth(recarregar) {
  const dlg = document.createElement('dialog');
  dlg.className = 'sheet';
  dlg.innerHTML = `<h3>🔊 TV e Som · Bluetooth</h3>
    <div id="bt">${carregando()}</div>
    <div id="bt-novos"></div>
    <div class="acoes"><button class="btn" id="bt-procurar">${icone('busca')} Procurar novos</button><button class="btn btn-primario" data-fechar>Fechar</button></div>`;
  document.body.appendChild(dlg);
  dlg.showModal();
  let mudou = false;   // conectou/desconectou -> ao fechar, a tela redesenha com a saída nova
  dlg.addEventListener('close', () => { dlg.remove(); if (mudou) recarregar(); });
  dlg.querySelector('[data-fechar]').onclick = () => dlg.close();
  const box = dlg.querySelector('#bt');
  const novos = dlg.querySelector('#bt-novos');

  const linha = (a, botao, status) => `
    <div class="item" style="cursor:default">
      <span class="ico">${a.celular ? '📱' : icone('tv')}</span>
      <span class="txt">${esc(a.nome)}<small>${status || (a.conectado ? '🟢 Conectado · tocando aqui' : a.pareado ? 'Pareado · desconectado' : a.celular ? 'Celular por perto · não pareado' : 'Por perto · não pareado')}</small></span>
      ${botao}
    </div>`;

  // Roda uma ação (pode levar uns segundos), mostrando "aguarde" no botão
  const acao = async (b, texto, fn) => {
    const antes = b.innerHTML;
    $$('button', dlg).forEach((x) => (x.disabled = true));
    b.textContent = texto;
    try { await fn(); mudou = true; await carregar(); } catch (e) { toast(e.message); b.innerHTML = antes; }
    $$('button', dlg).forEach((x) => (x.disabled = false));
  };

  async function carregar() {
    let r;
    try { r = await API.bluetooth(); } catch (e) { box.innerHTML = erroBox(e); return; }
    box.innerHTML = `
      <p class="meta" style="margin-bottom:10px">Saída de som agora: <b>${esc(r.saida?.nome || 'desconhecida')}</b></p>
      ${r.aparelhos.length ? r.aparelhos.map((a) => linha(a, a.conectado
        ? `<button class="btn" data-desconectar="${esc(a.mac)}">Desconectar</button>`
        : `<button class="btn btn-primario" data-conectar="${esc(a.mac)}">Conectar</button>`)).join('')
        : vazio('Nenhum aparelho pareado. Clique em "Procurar novos".')}
      <h3 style="margin:18px 0 6px;font-size:16px">📱 Celulares</h3>
      <p class="meta" style="margin-bottom:8px">O computador vira a caixinha do celular: o som dele sai aqui e o slider de volume controla.</p>
      ${r.celulares.length ? r.celulares.map((c) => linha({ ...c, celular: true }, c.tocando
        ? `<button class="btn" data-parar-cel="${esc(c.id)}">Parar</button>`
        : `<button class="btn btn-primario" data-tocar-cel="${esc(c.id)}">Tocar aqui</button>`,
        c.tocando ? '🟢 O som do celular está saindo aqui' : 'Pareado')).join('')
        : vazio('Nenhum celular pareado. Clique em "Procurar novos" ou pareie pelo Bluetooth do celular.')}`;
    $$('[data-tocar-cel]', box).forEach((b) => (b.onclick = () => acao(b, 'Conectando...', async () => {
      await API.tocarCelular(b.dataset.tocarCel);
      toast('📱 Conectado! Dê play no celular: o som sai no computador.');
    })));
    $$('[data-parar-cel]', box).forEach((b) => (b.onclick = () => acao(b, 'Parando...', () => API.pararCelular(b.dataset.pararCel))));
    $$('[data-conectar]', box).forEach((b) => (b.onclick = () => acao(b, 'Conectando...', async () => {
      await API.conectarBluetooth(b.dataset.conectar);
      toast('🔊 Conectado! O som agora sai por ele.');
    })));
    $$('[data-desconectar]', box).forEach((b) => (b.onclick = () => acao(b, 'Desconectando...', () => API.desconectarBluetooth(b.dataset.desconectar))));
  }

  dlg.querySelector('#bt-procurar').onclick = async () => {
    const b = dlg.querySelector('#bt-procurar');
    b.disabled = true;
    novos.innerHTML = `<h3 style="margin:18px 0 6px;font-size:16px">Por perto</h3><p class="meta">Procurando... leva até 30 s. Deixe a caixinha/fone em <b>modo de pareamento</b> (luz piscando). Celular: abra a tela de Bluetooth dele.</p>${carregando()}`;
    try {
      const lista = await API.procurarBluetooth();
      novos.innerHTML = `<h3 style="margin:18px 0 6px;font-size:16px">Por perto</h3>${lista.length
        ? lista.map((a) => linha(a, `<button class="btn btn-primario" data-parear="${esc(a.id)}" data-mac="${esc(a.mac)}">Parear</button>`)).join('')
        : vazio('Nenhum aparelho de som encontrado. Ele está em modo de pareamento?')}`;
      $$('[data-parear]', novos).forEach((p) => (p.onclick = () => {
        const celular = !!p.closest('.item').querySelector('.ico').textContent.includes('📱');
        if (celular) toast('📱 Confirme o "Parear" que vai aparecer na tela do celular.');
        acao(p, 'Pareando...', async () => {
          await API.parearBluetooth(p.dataset.parear, celular ? '' : p.dataset.mac);
          p.closest('.item').remove();
          toast(celular ? '📱 Pareado! Agora clique em "Tocar aqui".' : '🔊 Pareado! O som agora sai por ele.');
        });
      }));
    } catch (e) {
      novos.innerHTML = erroBox(e);
    }
    b.disabled = false;
  };

  carregar();
}

// Painel da lâmpada: testar, mudar a cor e escolher qual lâmpada usar
async function abrirPainelLampada() {
  const dlg = document.createElement('dialog');
  dlg.innerHTML = `<h3>💡 Lâmpada inteligente</h3><div id="lamp">${carregando()}</div>
    <div class="acoes acoes-iguais"><button class="btn" data-fechar>Fechar</button><button class="btn btn-primario" id="salvar-dev" hidden>Salvar</button></div>`;
  document.body.appendChild(dlg);
  dlg.showModal();
  dlg.addEventListener('close', () => dlg.remove());
  dlg.querySelector('[data-fechar]').onclick = () => dlg.close();
  const box = dlg.querySelector('#lamp');

  let info;
  try { info = await API.lampada(); } catch (e) { box.innerHTML = erroBox(e); return; }
  const real = info.modo === 'real';
  const ligada = (info.status || []).find?.((s) => s.code === 'switch_led');

  box.innerHTML = `
    <p class="sinopse" style="margin-bottom:14px">
      ${real
        ? `Conectado à nuvem Tuya. Lâmpada: <b>${esc(info.device_id || 'não escolhida')}</b>${ligada ? ` • agora ${ligada.value ? 'ligada' : 'desligada'}` : ''}.`
        : 'Modo <b>simulação</b>: os comandos aparecem só no console do servidor. Quando a lâmpada chegar, siga o LAMPADA.md.'}
      ${info.erro ? `<br><span style="color:var(--vermelho)">${esc(info.erro)}</span>` : ''}
    </p>
    <div class="acoes" style="justify-content:flex-start;margin:0 0 16px">
      <button class="btn btn-roxo" id="testar">Testar (piscar)</button>
      <button type="button" class="btn" id="cor" aria-haspopup="dialog">Cor <span class="cor-amostra" style="background:#f27474"></span></button>
    </div>
    <label class="meta" for="dev">ID da lâmpada (device_id) — deixe vazio para usar o padrão do servidor</label>
    <input class="campo" id="dev" style="width:100%;margin-top:6px" value="${esc(info.device_id || '')}" placeholder="ex: eb1234abcd5678xyz">
    ${real ? '<button class="link" id="listar" style="margin-top:10px">Ver aparelhos da minha conta Smart Life</button><div id="lista-dev"></div>' : ''}`;

  box.querySelector('#testar').onclick = async () => {
    try { const r = await API.testarLampada(); toast(r.modo === 'real' ? 'Piscou? Então está funcionando! 🎉' : 'Simulação: veja o console do servidor'); }
    catch (e) { toast(e.message); }
  };
  // Cor da lâmpada: seletor do site; manda pra lâmpada quando solta o dedo/mouse
  let corLamp = '#f27474';
  const btnCor = box.querySelector('#cor');
  btnCor.onclick = () => abrirSeletorCor(btnCor, corLamp, {
    aoMudar: (hex) => { $('.cor-amostra', btnCor).style.background = hex; },
    aoEscolher: async (hex) => {
      if (hex === corLamp) return;
      corLamp = hex;
      try { await API.corLampada(hex, 70); toast('Cor enviada'); } catch (err) { toast(err.message); }
    },
  });
  const salvarDev = dlg.querySelector('#salvar-dev');
  salvarDev.hidden = false;
  salvarDev.onclick = async () => {
    try { await API.escolherLampada(box.querySelector('#dev').value.trim()); toast('Lâmpada salva'); } catch (e) { toast(e.message); }
  };
  const listar = box.querySelector('#listar');
  if (listar) listar.onclick = async () => {
    try {
      const r = await API.lampadaDispositivos();
      const lista = r?.devices || r || [];
      box.querySelector('#lista-dev').innerHTML = (Array.isArray(lista) ? lista : []).map((d) =>
        `<button class="item" data-id="${esc(d.id)}"><span class="txt">${esc(d.name)}<small>${esc(d.id)} • ${d.online ? 'online' : 'offline'}</small></span></button>`
      ).join('') || vazio('Nenhum aparelho na conta vinculada.');
      $$('#lista-dev [data-id]', box).forEach((b) => (b.onclick = () => { box.querySelector('#dev').value = b.dataset.id; }));
    } catch (e) { toast(e.message); }
  };
}

// [+] APARELHOS CONECTADOS: mostra o que está ligado à casa (sem configurar nada na mão).
// Cortina e ar da conta Smart Life são reconhecidos pelo TIPO e conectados sozinhos.
const TIPOS_TUYA = {
  dj: 'Lâmpada', dd: 'Fita de LED', fwd: 'Luz ambiente', dc: 'Cordão de luz', cl: 'Cortina', clkg: 'Interruptor de cortina',
  kt: 'Ar-condicionado', ktkzq: 'Controle de ar', infrared_ac: 'Ar-condicionado (infravermelho)', wnykq: 'Controle universal infravermelho',
  qt: 'Hub infravermelho', cz: 'Tomada', pc: 'Régua', kg: 'Interruptor', wk: 'Termostato', fs: 'Ventilador', sp: 'Câmera',
};
const FUNCAO_POR_TIPO = { cl: 'cortinas', clkg: 'cortinas', kt: 'temperatura', ktkzq: 'temperatura', wk: 'temperatura', infrared_ac: 'temperatura' };

async function abrirPainelAparelhos(recarregar) {
  const dlg = document.createElement('dialog');
  dlg.className = 'sheet';
  dlg.innerHTML = `<h3>Aparelhos conectados</h3><div id="ap">${carregando()}</div>
    <div class="acoes"><button class="btn" id="ap-ajuda">${icone('ajuda')} Como conectar</button><button class="btn btn-primario" data-fechar>Fechar</button></div>`;
  document.body.appendChild(dlg);
  dlg.showModal();
  let mudou = false;
  dlg.addEventListener('close', () => { dlg.remove(); if (mudou) recarregar(); });
  dlg.querySelector('[data-fechar]').onclick = () => dlg.close();
  dlg.querySelector('#ap-ajuda').onclick = () => { dlg.close(); abrirComoConectar('aparelhos'); };
  const box = dlg.querySelector('#ap');

  let casa, lampada, conta = [];
  try { [casa, lampada] = await Promise.all([API.casa(), API.lampada()]); } catch (e) { box.innerHTML = erroBox(e); return; }
  const real = lampada.modo === 'real';
  if (real) {
    try { const r = await API.lampadaDispositivos(); conta = Array.isArray(r) ? r : r?.devices || []; } catch { conta = []; }
  }

  // Conecta sozinho: cortina/ar da conta que ainda não estão ligados à casa
  const avisosAuto = [];
  for (const tipo of ['cortinas', 'temperatura']) {
    const d = casa.dispositivos.find((x) => x.tipo === tipo);
    if (!d || d.tuya_device_id) continue;
    const achado = conta.find((x) => FUNCAO_POR_TIPO[x.category] === tipo && (x.category !== 'infrared_ac' || x.gateway_id));
    if (!achado) continue;
    const id = achado.category === 'infrared_ac' ? `ir:${achado.gateway_id}:${achado.id}` : achado.id;
    try { await API.configurarAparelho(d.id, id); d.tuya_device_id = id; mudou = true; avisosAuto.push(`${achado.name} conectado como ${d.nome}`); } catch { /* fica simulado */ }
  }

  const nomeNaConta = (id) => {
    const limpo = String(id || '').startsWith('ir:') ? id.split(':')[2] : id;
    return conta.find((x) => x.id === limpo);
  };
  const usoDe = (id) => {
    if (id === lampada.device_id) return 'Iluminação';
    const d = casa.dispositivos.find((x) => x.tuya_device_id && (x.tuya_device_id === id || x.tuya_device_id.endsWith(':' + id)));
    return d ? d.nome : null;
  };
  const ICONE_FUNCAO = { iluminacao: 'lampada', cortinas: 'cortina', temperatura: 'termometro', tv_som: 'som' };
  const statusDe = (d) => {
    if (d.tipo === 'iluminacao') {
      const a = nomeNaConta(lampada.device_id);
      return real && lampada.device_id ? { ok: true, texto: a ? `${a.name} · ${a.online ? 'online' : 'offline'}` : 'Lâmpada da nuvem Tuya' } : { ok: false, texto: 'Simulada: a Tuya não está configurada no servidor' };
    }
    if (d.tipo === 'tv_som') return casa.som?.modo === 'real' ? { ok: true, texto: `Som do computador · ${casa.som.nome || ''}` } : { ok: false, texto: 'Simulado' };
    if (!d.tuya_device_id) return { ok: false, texto: 'Simulado: nenhum aparelho conectado' };
    const a = nomeNaConta(d.tuya_device_id);
    return { ok: true, texto: a ? `${a.name} · ${a.online ? 'online' : 'offline'}` : 'Aparelho Tuya' };
  };

  box.innerHTML = `
    <p class="texto-suave" style="margin-bottom:12px">${real
      ? 'Conectado à nuvem <b>Tuya</b> (a mesma do app Smart Life). Pareie um aparelho no Smart Life e ele aparece aqui sozinho.'
      : 'A nuvem Tuya ainda <b>não está configurada</b> no servidor: tudo funciona em simulação. Veja "Como conectar".'}</p>
    ${avisosAuto.length ? `<p class="aparelhos-auto">${icone('check')} ${avisosAuto.map(esc).join(' · ')}</p>` : ''}
    <h4 class="aparelhos-sub">Na sua casa</h4>
    <ul class="aparelhos-lista">
      ${casa.dispositivos.map((d) => { const st = statusDe(d); return `
        <li><span class="ico">${icone(ICONE_FUNCAO[d.tipo] || 'casa')}</span>
          <span class="txt"><b>${esc(d.nome)}</b><small>${esc(st.texto)}</small></span>
          <span class="badge ${st.ok ? 'badge-ok' : ''}">${st.ok ? 'Conectado' : 'Simulado'}</span></li>`; }).join('')}
    </ul>
    ${real ? `<h4 class="aparelhos-sub">Na sua conta Smart Life</h4>
    <ul class="aparelhos-lista">
      ${conta.map((x) => `<li><span class="ico">${icone(FUNCAO_POR_TIPO[x.category] === 'cortinas' ? 'cortina' : FUNCAO_POR_TIPO[x.category] === 'temperatura' ? 'termometro' : x.category === 'dj' ? 'lampada' : 'conexoes')}</span>
        <span class="txt"><b>${esc(x.name)}</b><small>${esc(TIPOS_TUYA[x.category] || 'Aparelho')} · ${x.online ? 'online' : 'offline'}</small></span>
        ${usoDe(x.id) ? `<span class="badge badge-ok">Em uso: ${esc(usoDe(x.id))}</span>` : '<span class="badge">Livre</span>'}</li>`).join('') || '<li class="texto-suave">Nenhum aparelho na conta vinculada.</li>'}
    </ul>` : ''}
    <details class="aparelhos-avancado"><summary>Configuração manual (avançado)</summary>
      <p class="texto-suave">Só se o aparelho não for reconhecido sozinho. Ar comum com controle infravermelho: <code>ir:ID_DO_HUB:ID_DO_CONTROLE</code>. Vazio = volta a ser simulado.</p>
      ${casa.dispositivos.filter((d) => ['cortinas', 'temperatura'].includes(d.tipo)).map((d) => `
        <label class="aparelho-cfg"><span>${esc(d.nome)}</span>
          <input class="campo" data-cfg="${d.id}" value="${esc(d.tuya_device_id || '')}" placeholder="ID do aparelho Tuya" aria-label="ID do aparelho ${esc(d.nome)}">
          <button type="button" class="btn" data-salvar="${d.id}">Salvar</button><button type="button" class="btn" data-testar="${d.id}">Testar</button></label>`).join('')}
    </details>`;

  $$('[data-salvar]', box).forEach((b) => (b.onclick = async () => {
    try { await API.configurarAparelho(b.dataset.salvar, $(`[data-cfg="${b.dataset.salvar}"]`, box).value.trim()); mudou = true; toast('Aparelho salvo'); }
    catch (e) { toast(e.message); }
  }));
  $$('[data-testar]', box).forEach((b) => (b.onclick = async () => {
    try { toast((await API.testarAparelho(b.dataset.testar)).mensagem); } catch (e) { toast(e.message); }
  }));
}

// [+] "Como conectar": passo a passo de cada tecnologia da casa
function abrirComoConectar(abrir = '') {
  const dlg = document.createElement('dialog');
  dlg.className = 'sheet ajuda-casa';
  const sec = (id, ico, titulo, corpo) => `<details ${abrir === id ? 'open' : ''}><summary>${icone(ico)} ${titulo}</summary><div class="ajuda-corpo">${corpo}</div></details>`;
  dlg.innerHTML = `<h3>Como conectar a casa</h3>
    <p class="texto-suave" style="margin-bottom:12px">Tudo que é <b>real</b> passa pela nuvem Tuya (a mesma do app Smart Life) ou pelo próprio computador. O que não está conectado fica <b>simulado</b>: aparece na Sala ao vivo, mas não mexe em nada de verdade.</p>
    ${sec('lampada', 'lampada', 'Lâmpada inteligente', `<ol>
      <li>Pareie a lâmpada no app <b>Smart Life</b> (não no app da marca).</li>
      <li>Crie um projeto grátis em <b>platform.tuya.com</b> e vincule a conta do Smart Life.</li>
      <li>Coloque <code>TUYA_CLIENT_ID</code>, <code>TUYA_SECRET</code> e <code>TUYA_DEVICE_ID</code> no <code>segredos.properties</code> e reinicie o servidor pelo <code>rodar.cmd</code>.</li>
      <li>Gerenciar, Lâmpada, <b>Testar (piscar)</b>. Passo a passo completo no <b>LAMPADA.md</b>.</li></ol>`)}
    ${sec('aparelhos', 'cortina', 'Cortina e ar-condicionado', `<ol>
      <li>Compre modelos que digam <b>"compatível com Tuya / Smart Life"</b> (motor de cortina Wi-Fi, ar com Wi-Fi).</li>
      <li>Pareie no app Smart Life, com a mesma conta da lâmpada.</li>
      <li>Abra Gerenciar, <b>Aparelhos conectados</b>: o site reconhece a cortina e o ar pelo tipo e <b>conecta sozinho</b>.</li>
      <li><b>Ar comum, sem Wi-Fi?</b> Use um <b>controle universal infravermelho</b> Tuya: cadastre o ar nele pelo Smart Life. Ele também aparece sozinho.</li>
      <li>Pronto: cenas, atmosferas, voz, gestos e o celular passam a mexer no aparelho de verdade. O selo muda de "Simulado" pra <b>Conectado</b>.</li></ol>`)}
    ${sec('som', 'som', 'TV e Som (Bluetooth)', `<ol>
      <li>O som real é o do <b>computador onde o servidor roda</b> (Windows).</li>
      <li>Gerenciar, <b>TV e Som · Bluetooth</b>, "Procurar novos" com a caixinha/fone em modo de pareamento.</li>
      <li>Celular: em "Celulares", <b>Tocar aqui</b>: o som do celular sai no computador.</li></ol>`)}
    ${sec('celular', 'celular', 'Celular como controle (QR code)', `<ol>
      <li>Celular e computador no <b>mesmo Wi-Fi</b>.</li>
      <li>Sala ao vivo, botão <b>Celular</b>: aponte a câmera do celular pro QR code.</li>
      <li>Não abriu? Na 1ª vez, o Windows pergunta se o Java pode usar a rede: marque <b>Redes privadas</b>. E deixe o Wi-Fi do PC como rede <b>Privada</b>.</li>
      <li>O acesso pelo QR vale <b>12 horas</b>. Depois, é só gerar outro.</li></ol>`)}
    ${sec('votacao', 'urna', 'Votação', `<ol>
      <li>No menu da esquerda, <b>Votação</b>: escolha de 2 a 6 filmes e clique em <b>Abrir votação</b>.</li>
      <li>Os amigos escaneiam o QR e votam pelo celular, <b>sem precisar de conta</b>. 1 voto por celular, dá pra trocar.</li>
      <li><b>Encerrar e assistir o vencedor</b>: o filme começa com a sala no clima dele.</li></ol>`)}
    ${sec('voz', 'microfone', 'Comando de voz', `<p>Botão <b>Voz</b> (Chrome ou Edge, com internet). Clique uma vez e fale vários comandos; clique de novo pra desligar.</p><ul>
      <li>"<b>LetterRoyale, modo terror</b>" · "clima de romance" · "modo ação"</li>
      <li>"<b>intervalo</b>" · "<b>continuar</b>" · "encerrar filme" · "próximo da fila"</li>
      <li>"fechar cortinas" · "luz 40 por cento" · "ar vinte e dois graus" · "volume 30"</li></ul>`)}
    ${sec('gestos', 'mao', 'Gestos pela webcam', `<ol>
      <li>Sala ao vivo, botão <b>Gestos</b>: permita a câmera. Na 1ª vez baixa a IA (~8 MB).</li>
      <li><b>Mão aberta</b>, segurando ~1 s = intervalo. <b>Joinha</b> = volta pro filme.</li>
      <li>A imagem da câmera <b>não sai do computador</b>: a IA (MediaPipe, do Google) roda no navegador.</li></ol>`)}
    <div class="acoes"><button class="btn btn-primario" data-fechar>Fechar</button></div>`;
  document.body.appendChild(dlg);
  dlg.showModal();
  dlg.addEventListener('close', () => dlg.remove());
  dlg.querySelector('[data-fechar]').onclick = () => dlg.close();
}
