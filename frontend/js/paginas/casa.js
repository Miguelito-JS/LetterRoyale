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
      ? `${celularTocando ? `📱 ${celularTocando} → ` : c.som.bluetooth ? '🎧 ' : ''}${nomeCurto(c.som.nome)} · ` : '';
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
                  ${cin.modo === 'assistindo' ? `<button class="btn" id="encerrar" title="Para a apresentação: simula o fim">${cin.filme?.episodio ? 'Encerrar episódio agora' : 'Encerrar filme agora'}</button>` : ''}
                </div>` : ''}
            </div>
            ${ativa ? '<button class="btn" id="desativar">Desativar</button>' : ''}
          </section>

          <section class="secao">
            <div class="secao-topo">
              <h2>Dispositivos</h2>
              <span style="display:flex;gap:10px;align-items:center">
                <span class="badge" title="Real = lâmpada de verdade. Simulação = só no banco.">💡 ${c.lampada?.modo === 'real' ? 'Lâmpada real' : 'Simulação'}</span>
                <button class="link" id="gerenciar">Gerenciar</button>
              </span>
            </div>
            ${c.dispositivos.map((d) => `
              <div class="dispositivo ${d.ligado ? '' : 'desligado'}" data-disp="${d.id}">
                <span class="ico" style="width:38px;height:38px;border-radius:8px;background:var(--surface-raised);display:grid;place-items:center">${icone(ICONE_DISP[d.tipo], 'width="20"')}</span>
                <div><b>${esc(d.nome)}</b><div class="meta" id="sub-${d.id}">${esc(sub(d))}</div></div>
                ${controle(d)}
                <label class="switch"><input type="checkbox" data-ligar="${d.id}" ${d.ligado ? 'checked' : ''} aria-label="Ligar ${esc(d.nome)}"><span></span></label>
              </div>`).join('')}
          </section>
        </div>

        <aside class="lateral">
          <section class="card">
            <h2 style="font-size:22px;margin-bottom:14px">Cenas rápidas</h2>
            ${c.cenas.map((x) => `
              <button class="item ${x.ativa ? 'ativo' : ''}" data-cena="${x.id}">
                <span class="ico">${icone(ICONE_CENA[x.nome] || 'casa')}</span>
                <span class="txt">${esc(x.nome)}<small>${esc(x.descricao || '')}</small></span>
              </button>`).join('')}
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
    $$('[data-ligar]', raiz).forEach((i) => (i.onchange = () => { travar(i.dataset.ligar, i.checked); salvar(i.dataset.ligar, { ligado: i.checked }); }));
    $$('[data-estado]', raiz).forEach((b) => (b.onclick = async () => {
      if (b.classList.contains('ativo')) return;
      escolherSegmento(b);
      const subt = $(`#sub-${b.dataset.id}`);
      if (subt) subt.textContent = b.dataset.estado;
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
      r.onchange = () => salvar(d.id, { valor: Number(r.value) });
    });
  },
};

// "Gerenciar": escolhe o que configurar (lâmpada ou Bluetooth da TV e Som)
function abrirGerenciar(recarregar) {
  const dlg = document.createElement('dialog');
  dlg.innerHTML = `<h3>Gerenciar dispositivos</h3>
    <button class="item" data-painel="lampada"><span class="ico">${icone('lampada')}</span><span class="txt">Lâmpada inteligente<small>Testar, mudar a cor, escolher a lâmpada</small></span></button>
    <button class="item" data-painel="bluetooth"><span class="ico">${icone('tv')}</span><span class="txt">TV e Som · Bluetooth<small>Conectar caixinhas e fones ao computador</small></span></button>
    <div class="acoes"><button class="btn" data-fechar>Fechar</button></div>`;
  document.body.appendChild(dlg);
  dlg.showModal();
  dlg.addEventListener('close', () => dlg.remove());
  dlg.querySelector('[data-fechar]').onclick = () => dlg.close();
  dlg.querySelectorAll('[data-painel]').forEach((b) => (b.onclick = () => {
    dlg.close();
    if (b.dataset.painel === 'lampada') abrirPainelLampada();
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
