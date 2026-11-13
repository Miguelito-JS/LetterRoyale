// Telas públicas: Boas-vindas, Login, Criar conta, Recuperação de senha
window.Paginas = window.Paginas || {};

// Posters de fundo vêm do TMDB (em alta). Se o back cair, fica só o gradiente.
async function postersFundo(qtd) {
  try { return (await API.emAlta()).filter((f) => f.poster).slice(0, qtd); } catch { return []; }
}

Paginas['boas-vindas'] = {
  publica: true,
  titulo: 'Bem-vindo',
  async render(raiz) {
    const logado = !!Sessao.token;
    raiz.innerHTML = `
      <section class="boas-vindas">
        <div class="faixa cima" id="f1"></div>
        <div class="centro">
          <h1 class="marca"><img src="/img/logo.svg" alt="">LetterRoyale</h1>
          <p>Filmes, pessoas e histórias.<br>Tudo no mesmo lugar.</p>
          <div class="acoes">
            ${logado
              ? '<a class="btn btn-primario" href="#/inicio">Continuar</a>'
              : '<a class="btn btn-primario" href="#/login">Entrar</a><a class="btn" href="#/cadastro">Criar conta</a>'}
          </div>
        </div>
        <p class="meta" style="justify-content:center;text-align:center;font-size:11px;margin:-10px 0 10px;padding:0 20px">Este produto usa a API do TMDB, mas não é endossado nem certificado pelo TMDB.</p>
        <div class="faixa baixo" id="f2"></div>
      </section>`;
    const ps = await postersFundo(20);
    // Duas faixas que deslizam sem parar (uma pra cada lado)
    $('#f1').innerHTML = faixaPosters(ps.slice(0, 10));
    $('#f2').innerHTML = faixaPosters(ps.slice(10).length ? ps.slice(10) : ps);
  },
};

// Moldura compartilhada das telas de formulário
function telaEntrada(raiz, formHtml) {
  raiz.innerHTML = `
    <section class="entrada">
      <div class="arte">
        <a class="logo" href="#/"><img src="/img/logo.svg" alt=""><span>LetterRoyale</span></a>
        <div class="posters" id="arte-posters"></div>
        <h2>Cada filme<br>conta uma história.</h2>
        <p>Registre o que você assistiu, monte listas, escreva reviews e transforme sua sala em um cinema.</p>
      </div>
      <div class="form-lado">
        ${DECO_FUNDO.replace('fundo-app', 'fundo-app fundo-entrada')}
        <a class="logo logo-celular" href="#/" aria-label="Voltar para a tela inicial"><img src="/img/logo.svg" alt=""><span>LetterRoyale</span></a>
        <div class="faixa cima faixa-celular" id="fc1" aria-hidden="true"></div>
        ${formHtml}
        <div class="faixa baixo faixa-celular" id="fc2" aria-hidden="true"></div>
      </div>
    </section>`;
  postersFundo(20).then((ps) => {
    const box = $('#arte-posters');
    if (box) box.innerHTML = ps.slice(0, 4).map((f) => `<img src="${esc(f.poster)}" alt="">`).join('');
    // No celular a arte da esquerda some: no lugar dela, as faixas passando em cima e embaixo
    const c1 = $('#fc1'), c2 = $('#fc2');
    if (c1) c1.innerHTML = faixaPosters(ps.slice(0, 10));
    if (c2) c2.innerHTML = faixaPosters(ps.slice(10).length ? ps.slice(10) : ps);
  });
}

// ------- Entrar com Google (botão oficial do Google; o servidor confere o login) -------
function carregarScript(src) {
  return new Promise((ok, erro) => {
    if (document.querySelector(`script[src="${src}"]`) && window.google?.accounts) return ok();
    const sc = document.createElement('script');
    sc.src = src; sc.async = true; sc.onload = ok; sc.onerror = erro;
    document.head.appendChild(sc);
  });
}
async function ligarGoogle() {
  const btn = $('#google');
  if (!btn) return;
  btn.onclick = () => toast('Login com Google ainda não foi configurado no servidor.');
  let cfg;
  try { cfg = await API.googleConfig(); } catch { return; }
  if (!cfg.ativo) return;
  try { await carregarScript('https://accounts.google.com/gsi/client'); } catch { btn.onclick = () => toast('Não consegui carregar o Google. Confira a internet.'); return; }
  if (!$('#google')) return;   // a pessoa já saiu da tela
  google.accounts.id.initialize({
    client_id: cfg.client_id,
    ux_mode: 'popup',
    callback: async (r) => {
      try {
        const d = await API.loginGoogle(r.credential);
        Sessao.salvar(d.token, d.usuario);
        toast(`Bem-vindo, ${d.usuario.nome.split(' ')[0]}!`);
        location.hash = '#/inicio';
      } catch (err) { const e = $('#erro'); if (e) e.textContent = err.message; else toast(err.message); }
    },
  });
  const box = document.createElement('div');
  box.className = 'google-box';
  // Mesma largura dos campos e do botão Entrar (mede o nosso botão antes de esconder)
  const largura = Math.round(btn.getBoundingClientRect().width) || 360;
  btn.before(box);
  btn.hidden = true;   // o botão do site só some quando o do Google aparecer de verdade
  google.accounts.id.renderButton(box, {
    theme: 'filled_black', size: 'large', shape: 'rectangular', text: 'continue_with', locale: 'pt-BR',
    width: Math.max(200, Math.min(400, largura)),
  });
  // Se o Google recusar este endereço (ex.: falta http://localhost:8080 em "Origens JavaScript
  // autorizadas"), o botão dele fica com tamanho zero. Aí volta o nosso, explicando o problema.
  setTimeout(() => {
    const fr = box.querySelector('iframe');
    if (fr && fr.offsetHeight > 0) { btn.remove(); return; }
    box.remove();
    btn.hidden = false;
    btn.onclick = () => toast(`O Google recusou este endereço (${location.origin}). Confira "Origens JavaScript autorizadas" no Google Cloud.`);
  }, 3500);
}

const campo = (id, tipo, ico, ph, extra = '') =>
  `<label class="input-ico"><span class="sr-only">${ph}</span>${icone(ico)}<input class="campo" id="${id}" type="${tipo}" placeholder="${ph}" required ${extra}></label>`;

Paginas.login = {
  publica: true,
  titulo: 'Entrar',
  render(raiz) {
    telaEntrada(raiz, `
      <form class="form" id="f">
        <h1>Entrar na sua conta</h1>
        <p class="sub">Bem-vindo de volta! Que filme vamos ver hoje?</p>
        ${campo('email', 'email', 'email', 'E-mail', 'autocomplete="email"')}
        <div style="position:relative">
          ${campo('senha', 'password', 'cadeado', 'Senha', 'autocomplete="current-password"')}
          <button type="button" id="ver" aria-label="Mostrar senha" style="position:absolute;right:12px;top:14px;width:20px;height:20px;color:var(--text-muted)">${icone('olho')}</button>
        </div>
        <div class="linha">
          <label><input type="checkbox" checked> Lembrar de mim</label>
          <a class="link" href="#/recuperar">Esqueceu a senha?</a>
        </div>
        <p class="erro" id="erro"></p>
        <button class="btn btn-primario btn-bloco">Entrar</button>
        <div class="ou">ou</div>
        <button type="button" class="btn btn-bloco" id="google">Entrar com Google</button>
        <p style="text-align:center;color:var(--brand-lavender);font-weight:600">Não tem uma conta? <a class="link" href="#/cadastro">Criar conta</a></p>
      </form>`);
    try { const e = sessionStorage.getItem('lr_email_login'); if (e) { $('#email').value = e; sessionStorage.removeItem('lr_email_login'); } } catch {}
    $('#ver').onclick = () => { const s = $('#senha'); s.type = s.type === 'password' ? 'text' : 'password'; };
    ligarGoogle();
    $('#f').onsubmit = async (e) => {
      e.preventDefault();
      $('#erro').textContent = '';
      try {
        const r = await API.login($('#email').value, $('#senha').value);
        Sessao.salvar(r.token, r.usuario);
        location.hash = '#/inicio';
      } catch (err) { $('#erro').textContent = err.message; }
    };
  },
};

Paginas.cadastro = {
  publica: true,
  titulo: 'Criar conta',
  render(raiz) {
    telaEntrada(raiz, `
      <form class="form" id="f">
        <h1>Crie a sua conta</h1>
        <p class="sub">Quase lá! Crie sua conta para liberar o acesso aos seus filmes favoritos</p>
        ${campo('nome', 'text', 'perfil', 'Seu nome', 'autocomplete="name" required maxlength="80"')}
        ${campo('username', 'text', 'usuarioat', 'Nome de usuário (ex: ana_filmes)', 'autocomplete="username" required minlength="3" maxlength="30" pattern="@?[A-Za-z0-9_.]{3,30}" title="3 a 30 letras, números, _ ou ponto" autocapitalize="none" spellcheck="false"')}
        ${campo('email', 'email', 'email', 'E-mail', 'autocomplete="email"')}
        ${campo('senha', 'password', 'cadeado', 'Senha (mín. 6)', 'autocomplete="new-password" minlength="6"')}
        ${campo('senha2', 'password', 'cadeado', 'Confirmar a senha', 'autocomplete="new-password" minlength="6"')}
        <p class="erro" id="erro"></p>
        <button class="btn btn-primario btn-bloco">Criar</button>
        <div class="ou">ou</div>
        <button type="button" class="btn btn-bloco" id="google">Entrar com Google</button>
        <p style="text-align:center;color:var(--brand-lavender);font-weight:600">Já tem conta? <a class="link" href="#/login">Entrar</a></p>
      </form>`);
    ligarGoogle();
    // Avisa na hora se o @ ou o e-mail já existem
    const usuarioOk = verificarDisponivel($('#username'), 'username');
    const emailOk = verificarDisponivel($('#email'), 'email');
    // Sugere o @usuário a partir do nome, até a pessoa mexer no campo de usuário
    let mexeuNoUsuario = false;
    $('#username').oninput = () => { mexeuNoUsuario = true; };
    $('#nome').oninput = () => {
      if (mexeuNoUsuario) return;
      $('#username').value = $('#nome').value.normalize('NFD').replace(/[\u0300-\u036f]/g, '')
        .toLowerCase().trim().replace(/\s+/g, '_').replace(/[^a-z0-9_.]/g, '').slice(0, 30);
      $('#username').dispatchEvent(new Event('input'));   // confere o @ sugerido também
      mexeuNoUsuario = false;
    };
    $('#f').onsubmit = async (e) => {
      e.preventDefault();
      $('#erro').textContent = '';
      if ($('#senha').value !== $('#senha2').value) { $('#erro').textContent = 'As senhas não são iguais.'; return; }
      if (!usuarioOk()) { $('#erro').textContent = 'Escolha outro nome de usuário.'; $('#username').focus(); return; }
      if (!emailOk()) { $('#erro').textContent = 'Esse e-mail já tem conta.'; $('#email').focus(); return; }
      try {
        const r = await API.cadastro({
          nome: $('#nome').value.trim(),
          username: $('#username').value.trim().replace(/^@/, '').toLowerCase(),
          email: $('#email').value,
          senha: $('#senha').value,
        });
        Sessao.salvar(r.token, r.usuario);
        toast(`Bem-vindo, @${r.usuario.username}!`);
        location.hash = '#/inicio';
      } catch (err) { $('#erro').textContent = err.message; }
    };
  },
};

// ---------------------------------------------------------------------
// RECUPERAÇÃO DE SENHA — 4 telas: e-mail -> código -> nova senha -> pronto
// O e-mail e o código ficam guardados só nesta aba (sessionStorage)
// ---------------------------------------------------------------------
const Recuperacao = {
  get() { try { return JSON.parse(sessionStorage.getItem('lr_rec')) || {}; } catch { return {}; } },
  set(d) { try { sessionStorage.setItem('lr_rec', JSON.stringify({ ...Recuperacao.get(), ...d })); } catch {} },
  limpar() { try { sessionStorage.removeItem('lr_rec'); } catch {} },
};

function passos(atual) {
  return `<div class="passos" aria-label="Etapa ${atual} de 3">${[1, 2, 3].map((n) => `<i class="${n <= atual ? 'on' : ''}"></i>`).join('')}</div>`;
}

Paginas.recuperar = {
  publica: true,
  titulo: 'Recuperação de senha',
  render(raiz, rota) {
    const etapa = rota.partes[0] || 'email';
    const rec = Recuperacao.get();
    if (etapa !== 'email' && etapa !== 'pronto' && !rec.email) { location.hash = '#/recuperar'; return; }
    (TELAS_REC[etapa] || TELAS_REC.email)(raiz, rec);
  },
};

const TELAS_REC = {
  // 1) E-mail + confirmar e-mail (igual ao Figma)
  email(raiz, rec) {
    telaEntrada(raiz, `
      <form class="form" id="f">
        ${passos(1)}
        <h1>Recuperação de senha</h1>
        <p class="sub">Quase lá! Digite seu e-mail para voltar aos seus filmes favoritos</p>
        ${campo('email', 'email', 'email', 'E-mail', `autocomplete="email" value="${esc(rec.email || '')}"`)}
        ${campo('email2', 'email', 'email', 'Confirmar o e-mail', 'autocomplete="email"')}
        <p class="erro" id="erro"></p>
        <button class="btn btn-primario btn-bloco">Enviar código</button>
        <a class="link" href="#/login" style="text-align:center">Voltar para o login</a>
      </form>`);
    $('#f').onsubmit = async (e) => {
      e.preventDefault();
      const email = $('#email').value.trim().toLowerCase();
      if (email !== $('#email2').value.trim().toLowerCase()) { $('#erro').textContent = 'Os e-mails não são iguais.'; return; }
      try {
        const r = await API.esqueciSenha(email);
        Recuperacao.set({ email, codigo_dev: r.codigo_dev || null });
        location.hash = '#/recuperar/codigo';
      } catch (err) { $('#erro').textContent = err.message; }
    };
  },

  // 2) Código de 6 dígitos (6 caixinhas, aceita colar)
  codigo(raiz, rec) {
    telaEntrada(raiz, `
      <form class="form" id="f">
        ${passos(2)}
        <h1>Digite o código</h1>
        <p class="sub">Enviamos um código de 6 dígitos para <b style="color:var(--text-primary)">${esc(rec.email)}</b>. Ele vale por 15 minutos.</p>
        <div class="codigo" id="codigo">
          ${[0, 1, 2, 3, 4, 5].map((i) => `<input inputmode="numeric" maxlength="1" aria-label="Dígito ${i + 1}" autocomplete="${i === 0 ? 'one-time-code' : 'off'}">`).join('')}
        </div>
        ${rec.codigo_dev ? `<p class="meta" style="justify-content:center">Modo desenvolvimento: o código é <b>&nbsp;${esc(rec.codigo_dev)}</b></p>` : ''}
        <p class="erro" id="erro"></p>
        <button class="btn btn-primario btn-bloco">Continuar</button>
        <button type="button" class="link" id="reenviar" style="text-align:center">Não recebeu? Reenviar código</button>
      </form>`);
    const caixas = $$('#codigo input');
    caixas[0].focus();
    caixas.forEach((c, i) => {
      c.oninput = () => { c.value = c.value.replace(/\D/g, '').slice(-1); if (c.value && caixas[i + 1]) caixas[i + 1].focus(); };
      c.onkeydown = (e) => { if (e.key === 'Backspace' && !c.value && caixas[i - 1]) caixas[i - 1].focus(); };
      c.onpaste = (e) => {
        const dig = (e.clipboardData.getData('text') || '').replace(/\D/g, '').slice(0, 6);
        if (!dig) return;
        e.preventDefault();
        dig.split('').forEach((d, j) => { if (caixas[j]) caixas[j].value = d; });
        (caixas[dig.length] || caixas[5]).focus();
      };
    });
    $('#reenviar').onclick = async () => {
      try { const r = await API.esqueciSenha(rec.email); Recuperacao.set({ codigo_dev: r.codigo_dev || null }); toast('Código reenviado!'); Paginas.recuperar.render(raiz, { partes: ['codigo'] }); }
      catch (err) { toast(err.message); }
    };
    $('#f').onsubmit = async (e) => {
      e.preventDefault();
      const codigo = caixas.map((c) => c.value).join('');
      if (codigo.length !== 6) { $('#erro').textContent = 'Digite os 6 números.'; return; }
      try {
        await API.verificarCodigo(rec.email, codigo);
        Recuperacao.set({ codigo });
        location.hash = '#/recuperar/nova';
      } catch (err) { $('#erro').textContent = err.message; }
    };
  },

  // 3) Nova senha + confirmar
  nova(raiz, rec) {
    if (!rec.codigo) { location.hash = '#/recuperar/codigo'; return; }
    telaEntrada(raiz, `
      <form class="form" id="f">
        ${passos(3)}
        <h1>Crie uma nova senha</h1>
        <p class="sub">Use pelo menos 6 caracteres. Dica: misture letras e números.</p>
        ${campo('nova', 'password', 'cadeado', 'Nova senha', 'minlength="6" autocomplete="new-password"')}
        ${campo('nova2', 'password', 'cadeado', 'Confirmar a nova senha', 'minlength="6" autocomplete="new-password"')}
        <p class="erro" id="erro"></p>
        <button class="btn btn-primario btn-bloco">Salvar nova senha</button>
      </form>`);
    $('#f').onsubmit = async (e) => {
      e.preventDefault();
      if ($('#nova').value !== $('#nova2').value) { $('#erro').textContent = 'As senhas não são iguais.'; return; }
      try {
        await API.redefinirSenha({ email: rec.email, codigo: rec.codigo, nova_senha: $('#nova').value });
        location.hash = '#/recuperar/pronto';
      } catch (err) { $('#erro').textContent = err.message; }
    };
  },

  // 4) Pronto!
  pronto(raiz) {
    const email = Recuperacao.get().email || '';
    Recuperacao.limpar();
    telaEntrada(raiz, `
      <div class="form" style="text-align:center;align-items:center">
        <div class="sucesso-ico">${icone('check')}</div>
        <h1>Senha alterada!</h1>
        <p class="sub">Agora é só entrar com a sua nova senha.</p>
        <a class="btn btn-primario btn-bloco" href="#/login" id="ir">Entrar</a>
      </div>`);
    try { if (email) sessionStorage.setItem('lr_email_login', email); } catch {}
  },
};
