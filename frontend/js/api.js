// =====================================================================
// Camada que conversa com o backend. TODA chamada passa por aqui.
// No app (WebView) é só definir window.API_URL = 'https://seu-servidor.com'
// antes deste arquivo carregar.
// =====================================================================
const API_URL = (window.API_URL || '') + '/api';

const Sessao = {
  get token() { try { return localStorage.getItem('lr_token'); } catch { return null; } },
  get usuario() { try { return JSON.parse(localStorage.getItem('lr_usuario')); } catch { return null; } },
  salvar(token, usuario) {
    try { localStorage.setItem('lr_token', token); localStorage.setItem('lr_usuario', JSON.stringify(usuario)); } catch {}
  },
  sair() {
    try { localStorage.removeItem('lr_token'); localStorage.removeItem('lr_usuario'); } catch {}
  },
};

async function api(caminho, { metodo = 'GET', corpo } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (Sessao.token) headers.Authorization = `Bearer ${Sessao.token}`;

  const resp = await fetch(API_URL + caminho, {
    method: metodo,
    headers,
    body: corpo ? JSON.stringify(corpo) : undefined,
  });
  let dados = null;
  try { dados = await resp.json(); } catch {}

  if (resp.status === 401 && Sessao.token) {   // token venceu
    Sessao.sair();
    location.hash = '#/login';
  }
  if (!resp.ok) throw new Error(dados?.erro || 'Algo deu errado. Tente de novo.');
  return dados;
}

// Atalhos usados pelas páginas
const API = {
  // auth
  login: (email, senha) => api('/auth/login', { metodo: 'POST', corpo: { email, senha } }),
  cadastro: (d) => api('/auth/cadastro', { metodo: 'POST', corpo: d }),
  esqueciSenha: (email) => api('/auth/esqueci-senha', { metodo: 'POST', corpo: { email } }),
  redefinirSenha: (d) => api('/auth/redefinir-senha', { metodo: 'POST', corpo: d }),
  // filmes (TMDB via backend)
  emAlta: () => api('/filmes/em-alta'),
  minhasNotas: () => api('/atividade/minhas-notas'),
  secao: (chave) => api(`/filmes/secao/${encodeURIComponent(chave)}`),
  generos: (tipo) => api('/filmes/generos' + (tipo === 'serie' ? '?tipo=serie' : '')),
  temporada: (id, n) => api(`/filmes/${id}/temporada/${n}`),
  streamings: () => api('/filmes/streamings'),
  catalogo: (filtros) => api('/filmes?' + new URLSearchParams(filtros)),
  filme: (id) => api(`/filmes/${id}`),
  recomendados: (id) => api(`/filmes/${id}/recomendados`),
  // atividade
  assistido: (id) => api(`/atividade/assistido/${id}`, { metodo: 'POST', corpo: {} }),
  favorito: (id) => api(`/atividade/favorito/${id}`, { metodo: 'POST' }),
  continuar: () => api('/atividade/continuar'),
  amigos: () => api('/atividade/amigos'),
  // reviews
  reviews: (filtro = 'todas') => api(`/reviews?filtro=${filtro}`),
  reviewsDoFilme: (id) => api(`/reviews/filme/${id}`),
  maisComentados: () => api('/reviews/mais-comentados'),
  meuResumo: () => api('/reviews/minhas/resumo'),
  publicarReview: (d) => api('/reviews', { metodo: 'POST', corpo: d }),
  curtir: (id) => api(`/reviews/${id}/curtir`, { metodo: 'POST' }),
  // listas
  listas: (aba = 'todas') => api(`/listas?aba=${aba}`),
  lista: (id) => api(`/listas/${id}`),
  criarLista: (d) => api('/listas', { metodo: 'POST', corpo: d }),
  apagarLista: (id) => api(`/listas/${id}`, { metodo: 'DELETE' }),
  addNaLista: (listaId, tmdb_id) => api(`/listas/${listaId}/filmes`, { metodo: 'POST', corpo: { tmdb_id } }),
  tirarDaLista: (listaId, tmdbId) => api(`/listas/${listaId}/filmes/${tmdbId}`, { metodo: 'DELETE' }),
  // status do filme: quero_assistir | assistindo | assistido | abandonado | null
  status: (id, status) => api(`/atividade/status/${id}`, { metodo: 'PUT', corpo: { status } }),
  disponivel: (q) => api(`/auth/disponivel?${new URLSearchParams(q)}`),
  // sininho
  notificacoes: () => api('/notificacoes'),
  notificacoesContagem: () => api('/notificacoes/contagem'),
  notificacaoLida: (id) => api(`/notificacoes/${id}/lida`, { metodo: 'POST' }),
  notificacoesTodasLidas: () => api('/notificacoes/lidas', { metodo: 'POST' }),
  googleConfig: () => api('/auth/google/config'),
  loginGoogle: (credencial) => api('/auth/google', { metodo: 'POST', corpo: { credencial } }),
  verificarCodigo: (email, codigo) => api('/auth/verificar-codigo', { metodo: 'POST', corpo: { email, codigo } }),
  // perfil
  perfil: (username = 'me') => api(`/usuarios/${encodeURIComponent(username)}`),
  filmesDoUsuario: (username, status, tipo) => api(`/usuarios/${encodeURIComponent(username)}/filmes?status=${status}${tipo === 'serie' ? '&tipo=serie' : ''}`),
  reviewsDoUsuario: (username) => api(`/usuarios/${encodeURIComponent(username)}/reviews`),
  estatisticas: (username) => api(`/usuarios/${encodeURIComponent(username)}/estatisticas`),
  editarPerfil: (d) => api('/usuarios/me', { metodo: 'PUT', corpo: d }),
  enviarFoto: (imagem) => api('/usuarios/me/foto', { metodo: 'POST', corpo: { imagem } }),
  removerFoto: () => api('/usuarios/me/foto', { metodo: 'DELETE' }),
  trocarSenha: (atual, nova) => api('/usuarios/me/senha', { metodo: 'PUT', corpo: { atual, nova } }),
  // conexões
  plataformas: () => api('/conexoes/plataformas'),
  conexoes: () => api('/conexoes'),
  conectar: (plataforma_id, nome_conta) => api('/conexoes', { metodo: 'POST', corpo: { plataforma_id, nome_conta } }),
  atualizarConexao: (id, d) => api(`/conexoes/${id}`, { metodo: 'PATCH', corpo: d }),
  desconectar: (id) => api(`/conexoes/${id}`, { metodo: 'DELETE' }),
  // smart home
  casa: () => api('/casa'),
  dispositivo: (id, d) => api(`/casa/dispositivos/${id}`, { metodo: 'PATCH', corpo: d }),
  ativarCena: (id) => api(`/casa/cenas/${id}/ativar`, { metodo: 'POST' }),
  desativarCena: () => api('/casa/cenas/desativar', { metodo: 'POST' }),
  // lâmpada inteligente (Tuya / Tramontina)
  lampada: () => api('/casa/lampada'),
  lampadaDispositivos: () => api('/casa/lampada/dispositivos'),
  escolherLampada: (device_id) => api('/casa/lampada', { metodo: 'PUT', corpo: { device_id } }),
  testarLampada: () => api('/casa/lampada/testar', { metodo: 'POST' }),
  // Modo Cinema (cores do pôster)
  paleta: (id) => api(`/filmes/${id}/paleta`),
  cinema: () => api('/casa/cinema'),
  cinemaIniciar: (d) => api('/casa/cinema', { metodo: 'POST', corpo: d }),
  cinemaFoco: (tmdb_id) => api('/casa/cinema/foco', { metodo: 'POST', corpo: { tmdb_id } }),
  cinemaRitmo: (ritmo) => api('/casa/cinema', { metodo: 'PATCH', corpo: { ritmo } }),
  cinemaTerminar: () => api('/casa/cinema/terminar', { metodo: 'POST' }),
  cinemaParar: () => api('/casa/cinema', { metodo: 'DELETE' }),
  fila: () => api('/casa/fila'),
  entrarFila: (tmdb_id, ep) => api('/casa/fila', { metodo: 'POST', corpo: { tmdb_id, ...(ep || {}) } }),
  sairFila: (id) => api(`/casa/fila/${id}`, { metodo: 'DELETE' }),
  subirFila: (id) => api(`/casa/fila/${id}/subir`, { metodo: 'POST' }),
  assistirProximo: () => api('/casa/fila/proximo', { metodo: 'POST' }),
  corLampada: (hex, brilho) => api('/casa/lampada/cor', { metodo: 'POST', corpo: { hex, brilho } }),
  // TV e Som: Bluetooth do computador onde o servidor roda
  bluetooth: () => api('/casa/som/bluetooth'),
  procurarBluetooth: () => api('/casa/som/bluetooth/procurar'),
  parearBluetooth: (id, mac) => api('/casa/som/bluetooth/parear', { metodo: 'POST', corpo: { id, mac } }),
  conectarBluetooth: (mac) => api('/casa/som/bluetooth/conectar', { metodo: 'POST', corpo: { mac } }),
  desconectarBluetooth: (mac) => api('/casa/som/bluetooth/desconectar', { metodo: 'POST', corpo: { mac } }),
  tocarCelular: (id) => api('/casa/som/celular/tocar', { metodo: 'POST', corpo: { id } }),
  pararCelular: (id) => api('/casa/som/celular/parar', { metodo: 'POST', corpo: { id } }),
};
