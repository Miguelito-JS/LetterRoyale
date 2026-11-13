# LetterRoyale — Funções Java
Use essa função Ctrl+Shift+V
Cada função (método) do back-end em Java, classe por classe: o que recebe, o que faz e o que devolve.

> Cópia em arquivo do documento "LetterRoyale — Funções Java" (9/11/2026). Mudou o código? Atualize aqui também.

## Como ler este documento

O back-end tem 27 classes em `backend/src/main/java/br/com/letterroyale/`, separadas por pasta; cada seção abaixo é uma pasta ou um controller, com uma linha por função.

Um pedido do site sempre passa por estas funções, nesta ordem:

1. `AuthInterceptor.preHandle` lê o token do cabeçalho e guarda quem está logado.
2. A função do controller com a rota certa (por exemplo `ReviewsController.publicar` para `POST /api/reviews`) roda.
3. Ela chama `Auth.exigir(req)` se a rota precisa de login (sem login = erro 401).
4. Ela usa serviços (`TmdbService`, `CinemaService`...) e o banco pelo `Db`.
5. Ela devolve um `Map`, que o Spring transforma em JSON.
6. Se algo der errado ela lança `ApiException`, e `TratadorErros` devolve `{"erro": "..."}`.

Palavras que aparecem muito:

- **`@RestController` / `@GetMapping("/x")`**: diz ao Spring que a função responde quando o site chama `GET /x`.
- **`@PathVariable`**: pedaço do endereço, como o `123` em `/api/filmes/123`.
- **`@RequestParam`**: valor depois do `?`, como `q=duna`.
- **`@RequestBody Map corpo`**: o JSON que o site mandou.
- **`uid`**: o id do usuário logado.
- **`tmdbId`**: o id do filme no TMDB; **negativo = série**.

## LetterRoyaleApplication e config/

Essas classes ligam o servidor e o deixam pronto antes do primeiro pedido.

| Classe | Função | O que faz |
| --- | --- | --- |
| LetterRoyaleApplication | `main(String[] args)` | Ponto de partida: liga o Spring Boot. É esta que você roda. Depois, o site abre em http://localhost:8080. |
| PreparacaoInicial | `PreparacaoInicial(Db, TmdbService)` | Construtor: recebe o acesso ao banco e ao TMDB. |
| PreparacaoInicial | `garantirColuna(tabela, coluna, definicao, depois)` | Olha no `information_schema` se a coluna existe; se não, faz `ALTER TABLE ... ADD COLUMN` e roda o SQL `depois` (se houver). |
| PreparacaoInicial | `run(ApplicationArguments)` | Roda 1 vez quando o servidor liga: (1) atualiza o banco (colunas novas, tabelas `fila_cinema`, `fotos_perfil` e `notificacoes`, Mercado Play → Discovery+, fila aceitando episódios); (2) troca as senhas `SEED:123456` do seed por hash BCrypt; (3) completa até 100 filmes do cache com dados do TMDB. Se algo falhar, só avisa no log. |
| AgendadorConfig | (nenhuma) | Só tem `@EnableScheduling`: liga as funções com `@Scheduled`, como o `tick()` do Modo Cinema. |
| WebConfig | `WebConfig(AuthInterceptor)` | Construtor: recebe o leitor de token. |
| WebConfig | `addInterceptors(registry)` | Faz o `AuthInterceptor` rodar antes de toda rota `/api/**`. |
| WebConfig | `addCorsMappings(registry)` | Libera o app (WebView) e outros endereços a chamar a API. |
| WebConfig | `addViewControllers(registry)` | Faz http://localhost:8080 abrir o `index.html` do site. |

A pasta do site é servida pela linha `spring.web.resources.static-locations` do `application.properties`, sem código Java.

## util/ e erro/

São atalhos que todas as outras classes usam: `Db` para o banco, `Json` para ler o que o site mandou e `ApiException` para devolver erros.

| Classe | Função | O que faz |
| --- | --- | --- |
| Db | `lista(sql, params...)` | Roda um SELECT e devolve todas as linhas (lista de mapas coluna → valor). |
| Db | `um(sql, params...)` | Roda um SELECT e devolve só a primeira linha, ou vazio (`Optional`). |
| Db | `existe(sql, params...)` | `true` se o SELECT achou pelo menos 1 linha. |
| Db | `numero(sql, params...)` | Devolve um número (ex.: `SELECT COUNT(*)`) como `long`; nada = 0. |
| Db | `executar(sql, params...)` | Roda INSERT, UPDATE ou DELETE e devolve quantas linhas mudaram. |
| Db | `inserir(sql, params...)` | Roda um INSERT e devolve o id novo (AUTO_INCREMENT). |
| Json | `texto(m, chave, padrao)` | Lê um texto do corpo; se não veio, devolve o padrão. |
| Json | `numero(m, chave)` | Lê um número inteiro (`Long`), ou `null`. |
| Json | `decimal(m, chave)` | Lê um número com vírgula (`Double`), ou `null`. |
| Json | `bool(m, chave)` | Lê verdadeiro/falso, ou `null`. |
| Json | `obj(chave, valor, ...)` | Monta um mapa rápido para responder: `Json.obj("ok", true, "id", 5)`. |
| Json | `verdade(v)` | Converte o 0/1 do MySQL em `true`/`false`. |
| ApiException | `ApiException(status, mensagem)` | Erro esperado, ex.: `throw new ApiException(404, "Lista não encontrada.")`. |
| ApiException | `getStatus()` | Devolve o código HTTP do erro (400, 401, 404...). |
| TratadorErros | `api(ApiException)` | Transforma o erro em `{"erro": "mensagem"}` com o código certo. |
| TratadorErros | `duplicado(DuplicateKeyException)` | Registro repetido no banco → 409 "Esse registro já existe." |
| TratadorErros | `geral(Exception)` | Qualquer outro erro → escreve no log e devolve 500 "Erro no servidor." |

Todas as funções do `Db` usam `?` no lugar dos valores; é isso que impede SQL injection.

## seguranca/

O login funciona com um token JWT, um "crachá" assinado que vale 7 dias (`app.jwt.validade-dias=7`) e vai em todo pedido do site.

| Classe | Função | O que faz |
| --- | --- | --- |
| JwtService | `JwtService(segredo, dias)` | Construtor: pega o `JWT_SECRET` e a validade em dias. |
| JwtService | `gerar(usuarioId, username)` | Cria o token com o id e o @ da pessoa, assinado com o segredo. |
| JwtService | `validar(token)` | Confere a assinatura e a data; devolve um `UsuarioLogado` ou `null` se for falso ou vencido. |
| AuthInterceptor | `AuthInterceptor(JwtService)` | Construtor. |
| AuthInterceptor | `preHandle(req, res, handler)` | Roda antes de toda rota `/api`: se o cabeçalho tiver `Authorization: Bearer <token>` válido, guarda o usuário no pedido. Nunca bloqueia: quem decide é o controller. |
| Auth | `exigir(req)` | Devolve quem está logado; se ninguém, lança erro 401. Usado nas rotas que precisam de login. |
| Auth | `opcional(req)` | Devolve quem está logado ou `null`. Usado nas rotas abertas que mudam um pouco se você estiver logado. |
| UsuarioLogado | `record UsuarioLogado(id, username)` | Só guarda o id e o @ de quem está logado; `u.id()` e `u.username()` leem os valores. |

## tmdb/TmdbService

Toda conversa com o TMDB passa por esta classe; o site nunca chama o TMDB direto, então o token fica escondido no servidor. As respostas ficam 5 minutos guardadas na memória para não gastar o limite de chamadas.

| Função | O que faz |
| --- | --- |
| `TmdbService(db, token, idioma, regiao)` | Construtor: monta o cliente HTTP com o token e guarda idioma (pt-BR) e região (BR). |
| `getRegiao()` | Devolve a região (BR), usada em "onde assistir" e lançamentos. |
| `tmdb(caminho, params...)` | Chama o TMDB, ex.: `tmdb("/search/movie", "query", "duna", "page", 1)`. Ignora parâmetros vazios, usa a memória de 5 min e traduz erros (404 não encontrado, 502 token inválido ou TMDB fora). |
| `img(path, tamanho)` | Monta o link completo de uma imagem, ex.: `https://image.tmdb.org/t/p/w500/abc.jpg`. |
| `texto(n, campo)` | Ajudante interno: lê um texto do JSON ou `null`. |
| `ano(data)` | Ajudante interno: "2024-03-01" → 2024. |
| `nota(f)` | Ajudante interno: nota do TMDB com 1 casa decimal (0 vira `null`). |
| `exigirLancado(tmdbId)` | Se o filme (ou a série, id negativo) ainda não estreou, lança erro 400 "Ainda não estreou. Estreia em dd/MM/aaaa". |
| `resumo(f)` | Transforma um filme do TMDB no formato dos cards: id, tipo, documentário, título, ano, lançamento, nota, pôster, fundo, sinopse. |
| `ehSerie(id)` | `true` se o id é negativo (as séries usam id negativo no site). |
| `ehDocumentario(f)` | `true` se o título tem o gênero 99 (Documentário). |
| `episodioLancado(serieId, temporada, episodio)` | Busca um episódio; se ainda não saiu, lança erro 400. |
| `resumoSerie(s)` | Igual ao `resumo`, mas para série (usa `name` e `first_air_date`, id negativo). |
| `duracaoEpisodio(s)` | Minutos de um episódio (0 se o TMDB não informar). |
| `salvarSerieNoCache(s)` | Grava ou atualiza a série em `filmes_cache` com id negativo. |
| `salvarNoCache(f)` | Grava ou atualiza o filme em `filmes_cache` (título, pôster, nota, duração, documentário). |
| `garantirNoCache(tmdbId)` | Se o título ainda não está completo no cache, busca no TMDB e salva. Chamada antes de gravar status, review, lista ou fila. |
| `doCache(linha)` | Transforma uma linha de `filmes_cache` no mesmo formato do `resumo`. |

## AuthController (`/api/auth`)

Cuida das telas de entrada: criar conta, entrar com senha ou Google e recuperar a senha.

| Função | Rota | O que faz |
| --- | --- | --- |
| `AuthController(db, jwt, modoDev, googleClientId)` | — | Construtor: recebe banco, gerador de token, se está em modo dev e o Client ID do Google. |
| `publico(u)` | — | Filtra o usuário para só mandar ao site o que pode (nunca a senha). |
| `respostaLogin(u)` | — | Monta `{token, usuario}` que o site guarda ao entrar. |
| `criarDadosPadrao(uid)` | — | Na conta nova: cria 4 listas (Favoritos, Clássicos, Terror, Sci-Fi), 4 dispositivos da casa e 4 cenas (Modo Cinema, Maratona, Pausa, Créditos). |
| `usernameLivre(email)` | — | Cria um @ livre a partir do e-mail: ana.souza@x.com → ana.souza (ou ana.souza2...). |
| `cadastro(corpo)` | `POST /cadastro` | Confere e-mail, senha (6+), @ e se já existem; grava com senha em BCrypt; cria os dados padrão; já devolve o login. |
| `login(corpo)` | `POST /login` | Confere e-mail e senha; erro 401 "E-mail ou senha incorretos" ou devolve o token. |
| `me(req)` | `GET /me` | Devolve os dados de quem está logado (usado no Editar perfil). |
| `disponivel(username, email, req)` | `GET /disponivel` | Enquanto a pessoa digita, diz se o @ e o e-mail estão livres; a própria conta não conta como ocupada. |
| `esqueciSenha(corpo)` | `POST /esqueci-senha` | Gera um código de 6 dígitos que vale 15 min. Responde igual mesmo se o e-mail não existir (não entrega quem tem conta). Em modo dev, devolve o código na tela, porque o envio por e-mail ainda não existe. |
| `verificarCodigo(corpo)` | `POST /verificar-codigo` | Confere se o código é daquele e-mail, não foi usado e não venceu. |
| `redefinirSenha(corpo)` | `POST /redefinir-senha` | Confere o código de novo, grava a senha nova em BCrypt e marca o código como usado. |
| `googleConfig()` | `GET /google/config` | Diz ao site se o Google está configurado e qual é o Client ID. |
| `google(corpo)` | `POST /google` | Pergunta ao Google (`tokeninfo`) se a credencial é verdadeira, do nosso Client ID, não vencida e com e-mail verificado. Acha a conta pelo id do Google ou pelo e-mail; se não existe, cria com nome e foto do Google e senha aleatória. Devolve o token. |

## FilmesController (`/api/filmes`)

Entrega tudo que vem do TMDB: Início, Catálogo, busca e a página do filme ou da série. É a maior classe do projeto.

| Função | Rota | O que faz |
| --- | --- | --- |
| `FilmesController(tmdb, db, paletas)` | — | Construtor. |
| `paleta(id)` | `GET /{id}/paleta` | Devolve as cores do pôster (as mesmas da lâmpada). |
| `resumos(lista, max)` | — | Converte até `max` filmes do TMDB para o formato dos cards. |
| `resumosSeries(lista, max)` | — | O mesmo, para séries. |
| `ehSerie(tipo)` | — | `true` quando o filtro pedido é `tipo=serie`. |
| `generos(tipo)` | `GET /generos` | Lista de gêneros de filme ou de série (o TMDB tem listas diferentes). |
| `streamings()` | `GET /streamings` | Os 25 serviços de streaming mais comuns no Brasil, com logo. |
| `secao(chave)` | `GET /secao/{chave}` | As fileiras do Início: lançamentos, em breve, aclamados, brasileiros, anos 90, curtinhos, gêneros e as de séries. Só aceita chaves conhecidas. |
| `emAlta()` | `GET /em-alta` | Os 20 filmes em alta da semana; o 1º vira o destaque do Início. |
| `catalogo(q, genero, ano, classificacao, streaming, ordem, pagina, tipo)` | `GET /` | Porta de entrada do Catálogo: com texto → busca de pessoa ou `buscaPorTexto`; sem texto → filmes, séries, todos ou documentários, 40 por página. |
| `catalogoTodos(q, genero, ano, streaming, ordem, pag)` | — | "Todos" e "Documentários": 20 filmes + 20 séries intercalados (documentários = gênero 99). |
| `normal(s)` | — | Deixa o texto sem acento, minúsculo e só com letras e números, para comparar títulos. |
| `lancamento(r)` | — | Data de lançamento (filme) ou de estreia (série); sem data vai para o fim. |
| `notaTitulo(r, qn)` | — | Nota de 0 a 4 de quanto o título bate com a busca: 0 igual, 1 começa com, 2 tem a palavra, 3 tem o pedaço, 4 não tem. Olha o título em português e o original. |
| `detalhe(a)` | — | Pega os detalhes de um resultado no TMDB (coleção, produtoras, criadores). |
| `ids(lista)` | — | Junta os ids de uma lista do TMDB. |
| `ehDaFranquia(a, det, principal, qn)` | — | Só aceita na franquia quem tem uma prova além do nome: ser filme de uma coleção do TMDB com o nome buscado ou da mesma coleção do principal, ter o mesmo criador ou ser da mesma produtora. |
| `buscaPorTexto(q, tipo, ano, pag)` | — | Busca em até 5 páginas do TMDB (100 resultados) e tira pessoas e repetidos. Ordem: (1) o principal, o título mais famoso (mais votos) com o nome exato buscado, como a série Friends de 1994; (2) a franquia, do lançamento mais recente ao mais antigo (só os 20 candidatos mais votados são conferidos no `ehDaFranquia`); (3) os outros, pela `notaTitulo` e pela popularidade. Corta em páginas de 40. |
| `catalogoSeries(...)` | — | Catálogo só de séries, 40 por página (2 páginas do TMDB). |
| `buscarSeries(...)` | — | Chama `/search/tv` ou `/discover/tv` com os filtros, trocando a ordem de filme pela equivalente de série. |
| `filmesDaPessoa(q, pag)` | — | Se o texto é o nome de um ator ou diretor (e a busca geral do TMDB acha uma pessoa primeiro), devolve os filmes dela; menos de 3 filmes = ignora. |
| `semAcento(s)` | — | Tira acentos e deixa minúsculo. |
| `buscarCatalogo(...)` | — | Chama `/search/movie` ou `/discover/movie` com gênero, ano, classificação, streaming e ordem. |
| `detalhes(id, req)` | `GET /{id}` | Página do filme: dados, classificação BR, onde assistir, gêneros, direção, elenco, trailer, semelhantes, nota da comunidade e seus botões. Id negativo → `detalhesSerie`. |
| `estadoDoUsuario(id, req)` | — | Seu status, favorito e sua review daquele título (vazio se não estiver logado). |
| `detalhesSerie(id, req)` | — | Mesmo formato da página do filme + temporadas, total de episódios, situação (em andamento, finalizada...) e criação no lugar de direção. |
| `temporada(id, n)` | `GET /{id}/temporada/{n}` | Episódios de uma temporada: número, nome, data, duração, nota, resumo e imagem. |
| `recomendados(id)` | `GET /{id}/recomendados` | 20 recomendados do TMDB para aquele filme ou série. |

## AtividadeController (`/api/atividade`) e ReviewsController (`/api/reviews`)

A Atividade guarda o que você fez com cada título (status, favorito, progresso); o Reviews cuida das notas e textos.

**AtividadeController**

| Função | Rota | O que faz |
| --- | --- | --- |
| `AtividadeController(db, tmdb)` | — | Construtor. |
| `gravarStatus(uid, tmdbId, status, data)` | — | Grava o status na tabela `assistidos`; `null` tira o título das prateleiras. Assistindo, Assistido e Abandonado exigem que já tenha estreado; Assistido guarda a data. |
| `status(tmdbId, corpo, req)` | `PUT /status/{id}` | Botão de status: quero_assistir, assistindo, assistido, abandonado ou `null`. |
| `assistido(tmdbId, corpo, req)` | `POST /assistido/{id}` | Atalho que liga e desliga "Assistido". |
| `progresso(tmdbId, corpo, req)` | `PUT /progresso/{id}` | Guarda em que minuto parou e deixa como "Assistindo" (Continue assistindo). |
| `minhasNotas(req)` | `GET /minhas-notas` | Todas as suas notas, ex.: `{"155": 5.0}`; o site usa para mostrar "5.0 ★" em qualquer pôster. |
| `continuar(req)` | `GET /continuar` | Até 5 títulos que você está assistindo, com o progresso. |
| `favorito(tmdbId, req)` | `POST /favorito/{id}` | Liga e desliga o coração. |
| `amigos(req)` | `GET /amigos` | Os 6 últimos títulos que as pessoas que você segue marcaram como assistidos. |

**ReviewsController**

| Função | Rota | O que faz |
| --- | --- | --- |
| `ReviewsController(db, tmdb, notificacoes)` | — | Construtor. |
| `formatar(linha)` | — | Monta a review no formato do site: nota, texto, spoiler, curtidas, se você curtiu, autor e título. |
| `idOuZero(u)` | — | Id de quem está logado ou 0 (para saber se você curtiu). |
| `feed(filtro, pagina, req)` | `GET /?filtro=` | Feed de 20 em 20: todas (mais curtidas), seguindo (só amigos), recentes, nota_alta, nota_baixa. A ordem vem de uma lista fixa, nunca do texto do site. |
| `doFilme(tmdbId, req)` | `GET /filme/{id}` | Todas as reviews de um título. |
| `maisComentados()` | `GET /mais-comentados` | Os 20 títulos com mais reviews. |
| `meuResumo(req)` | `GET /minhas/resumo` | Seu total de reviews, nota média e curtidas recebidas. |
| `publicar(corpo, req)` | `POST /` | Nota de 0,5 a 5 e até 1000 caracteres; exige título lançado. Se já existe review sua, atualiza (1 por título). Também marca como Assistido. Review nova avisa os amigos (sininho). |
| `apagar(id, req)` | `DELETE /{id}` | Apaga a review, só se for sua. |
| `curtir(id, req)` | `POST /{id}/curtir` | Liga e desliga a curtida e devolve o total novo. Curtida nova avisa o dono da review (sininho). |

## ListasController, UsuariosController e ConexoesController

Listas, perfil (o seu e o dos outros) e plataformas de streaming ligadas ao perfil.

**ListasController (`/api/listas`)**

| Função | Rota | O que faz |
| --- | --- | --- |
| `ListasController(db, tmdb, notificacoes)` | — | Construtor. |
| `minhaLista(listaId, uid)` | — | Busca a lista e confere se é sua; se não, erro 404. |
| `eDoUsuario(lista, uid)` | — | `true` se a lista é daquela pessoa. |
| `listar(aba, req)` | `GET /?aba=` | Cards das listas (todas, minhas ou salvas) com até 4 pôsteres. |
| `criar(corpo, req)` | `POST /` | Cria lista com nome, descrição, ícone e se é pública. |
| `ver(id, req)` | `GET /{id}` | Abre a lista com os títulos (lista privada só o dono vê). |
| `editar(id, corpo, req)` | `PUT /{id}` | Muda nome, descrição, ícone ou pública; só o dono. |
| `apagar(id, req)` | `DELETE /{id}` | Apaga a lista; só o dono. |
| `adicionarFilme(id, corpo, req)` | `POST /{id}/filmes` | "Adicionar à lista": garante o título no cache e coloca na lista. |
| `tirarFilme(id, tmdbId, req)` | `DELETE /{id}/filmes/{tmdbId}` | Tira um título da lista. |
| `salvar(id, req)` | `POST /{id}/salvar` | Salva ou tira a lista de outra pessoa da sua aba Salvas. Salvar avisa o dono (sininho). |

**UsuariosController (`/api/usuarios`)**

| Função | Rota | O que faz |
| --- | --- | --- |
| `UsuariosController(db, jwt, notificacoes)` | — | Construtor. |
| `buscarUsuario(username, eu)` | — | Acha a pessoa pelo @ (`me` = você); não achou = 404. |
| `selectPrateleira(serie)` | — | Monta o SQL de uma prateleira só de filmes (id > 0) ou só de séries (id < 0), com a sua nota. |
| `filmeDaPrateleira(linha)` | — | Card da prateleira: título + sua nota + status. |
| `perfil(username, req)` | `GET /{username}` | Perfil inteiro: números (filmes, séries, reviews, listas, nota média), favoritos e prateleiras de filmes e de séries separados, listas, conexões, seguidores e se você segue. |
| `filmes(username, status, tipo, req)` | `GET /{username}/filmes` | "Mostrar tudo" de uma prateleira (até 500), de filmes ou séries. |
| `reviews(username, req)` | `GET /{username}/reviews` | As últimas 100 reviews da pessoa. |
| `estatisticas(username, req)` | `GET /{username}/estatisticas` | Filmes assistidos, horas, este ano, nota média, filmes por mês (12 meses) e quantas vezes deu cada nota. Séries ficam de fora das horas. |
| `editar(corpo, req)` | `PUT /me` | Editar perfil: confere nome, @, e-mail (válido e livre; trocar o e-mail pede a senha atual), telefone, bio (200), cor (#rrggbb). Devolve um token novo, porque o @ pode ter mudado. |
| `enviarFoto(corpo, req)` | `POST /me/foto` | Recebe a foto em base64 (JPG, PNG ou WEBP, até 600 KB), guarda em `fotos_perfil` e aponta o `avatar_url` para ela. |
| `removerFoto(req)` | `DELETE /me/foto` | Apaga a foto e volta para a letra inicial. |
| `foto(id)` | `GET /{id}/foto` | Entrega a imagem da foto (aberta, porque a tag `<img>` não manda login), com cache de 7 dias. |
| `trocarSenha(corpo, req)` | `PUT /me/senha` | Confere a senha atual e grava a nova em BCrypt. |
| `seguir(username, req)` | `POST /{username}/seguir` | Segue ou deixa de seguir; não dá para seguir a si mesmo. Seguir avisa a pessoa (sininho). |

**ConexoesController (`/api/conexoes`)**

Netflix, Prime e as outras não têm login público; a "conexão" é a pessoa dizer qual assinatura tem.

| Função | Rota | O que faz |
| --- | --- | --- |
| `ConexoesController(db)` | — | Construtor. |
| `plataformas(req)` | `GET /plataformas` | Todas as plataformas e se você já conectou cada uma. |
| `minhas(req)` | `GET /` | Suas conexões. |
| `conectar(corpo, req)` | `POST /` | Liga uma plataforma (com o nome da conta, opcional). |
| `atualizar(id, corpo, req)` | `PATCH /{id}` | Os 2 botões: mostrar no perfil e mostrar status. |
| `remover(id, req)` | `DELETE /{id}` | Desconecta. |

## CasaController (`/api/casa`)

A tela Smart Home e o card "Minha casa" do Início. A iluminação pode ser real (lâmpada Tuya); cortinas, temperatura e TV são simulados, só no banco. O controller só recebe o pedido e passa para `CasaService` e `CinemaService`.

| Função | Rota | O que faz |
| --- | --- | --- |
| `CasaController(db, tmdb, casa, tuya, cinema)` | — | Construtor. |
| `ok(aviso)` | — | Resposta padrão `{ok: true}`, com um aviso se a lâmpada não respondeu. |
| `casa(req)` | `GET /` | Tudo da tela: dispositivos, cenas, próxima sessão, fila, modo da lâmpada e estado do Modo Cinema. |
| `dispositivo(id, corpo, req)` | `PATCH /dispositivos/{id}` | Sliders e botões: liga/desliga, valor, estado da cortina. Apagar a luz desliga o Modo Cinema. |
| `ativarCena(id, req)` | `POST /cenas/{id}/ativar` | Ativa uma cena; o Modo Cinema liga no modo "navegando". |
| `desativar(req)` | `POST /cenas/desativar` | Botão Desativar: desliga a cena e o Modo Cinema. |
| `cinemaEstado(req)` | `GET /cinema` | Está ligado? Qual título e episódio? Quais cores? Acabou? Qual é o próximo da fila? |
| `cinemaIniciar(corpo, req)` | `POST /cinema` | "Assistir agora": liga com título, modo, ritmo e, se for série, temporada e episódio. |
| `cinemaFoco(corpo, req)` | `POST /cinema/foco` | O site avisa que você abriu a página de um título (a luz muda no modo navegando). |
| `cinemaRitmo(corpo, req)` | `PATCH /cinema` | Calmo (8 s), Normal (4 s) ou Festa (2 s) entre as cores. |
| `cinemaTerminar(req)` | `POST /cinema/terminar` | "Encerrar filme agora", para a apresentação. |
| `cinemaParar(req)` | `DELETE /cinema` | Desliga o Modo Cinema e limpa o aviso de fim. |
| `fila(req)` | `GET /fila` | A fila em ordem. |
| `entrarNaFila(corpo, req)` | `POST /fila` | Coloca um filme, ou um episódio (temporada + episódio), no fim da fila. |
| `sairDaFila(id, req)` | `DELETE /fila/{id}` | Tira da fila. |
| `subirNaFila(id, req)` | `POST /fila/{id}/subir` | Sobe uma posição. |
| `assistirProximo(req)` | `POST /fila/proximo` | Começa o 1º da fila. |
| `agendar(corpo, req)` | `POST /sessoes` | Agenda uma "Próxima sessão" com título, data e hora e cena. |
| `lampada(req)` | `GET /lampada` | Modo (real ou simulação), qual lâmpada e o estado dela. |
| `dispositivosTuya(req)` | `GET /lampada/dispositivos` | Lista os aparelhos da conta Smart Life (para achar o id da lâmpada). |
| `escolherLampada(corpo, req)` | `PUT /lampada` | Liga uma lâmpada específica a você. |
| `testar(req)` | `POST /lampada/testar` | Pisca: acende forte e volta para 30%. |
| `cor(corpo, req)` | `POST /lampada/cor` | Deixa a luz numa cor (`#ff8800`) com um brilho. |

## casa/ (Smart Home e Modo Cinema)

Aqui ficam as regras da casa. Duas funções rodam sozinhas, sem ninguém clicar: `CinemaService.tick()` a cada 1 segundo e `CasaService.rodarSessoesAgendadas()` a cada 1 minuto.

**CasaService**

| Função | O que faz |
| --- | --- |
| `CasaService(db, tuya)` | Construtor. |
| `lampadaDo(uid)` | Qual lâmpada é daquela pessoa: a salva no banco ou a padrão do `TUYA_DEVICE_ID`. |
| `aplicarDispositivo(uid, d)` | Alguém mexeu num slider ou liga/desliga: grava e, se for a luz, manda para a lâmpada. Devolve um aviso se a lâmpada não respondeu. |
| `aplicarCena(uid, cenaId)` | Ativa uma cena: grava luz, cortinas, temperatura e volume e manda a luz (branca, colorida ou subindo aos poucos). |
| `subirAosPoucos(lampada, brilhoFinal, temp)` | Sobe o brilho em 5 passos em cerca de 10 segundos (cena Créditos). |
| `rodarSessoesAgendadas()` | A cada 1 minuto: vê se chegou a hora de alguma "Próxima sessão" e ativa a cena escolhida. |

**CinemaService**

| Função | O que faz |
| --- | --- |
| `CinemaService(db, tmdb, paletas, tuya, casa, notificacoes)` | Construtor. |
| `ritmoEmSegundos(ritmo)` | calmo = 8, normal = 4, festa = 2 segundos entre cores. |
| `ativo(uid)` | `true` se a pessoa está com o Modo Cinema ligado. |
| `iniciar(uid, tmdbId, modo, ritmo)` | Atalho para a versão abaixo, sem episódio. |
| `iniciar(uid, tmdbId, modo, ritmo, temporada, episodio)` | Liga o Modo Cinema. "Assistindo" exige título lançado (na série, o episódio); calcula as cores do pôster, marca quando termina (duração do filme ou do episódio), tira da fila, ativa a cena e põe a luz em 30%. |
| `foco(uid, tmdbId)` | No modo navegando, troca as cores para o título que você abriu. |
| `mudarRitmo(uid, ritmo)` | Muda a velocidade das cores. |
| `reaplicarSeAtivo(uid)` | Mexeu no brilho com o Modo Cinema ligado: reenvia a mesma cor com o brilho novo. |
| `parar(uid)` | Desliga o Modo Cinema. |
| `terminarFilme(uid)` | Fim: filme vira "Assistido"; série vira "Assistindo" (só acabou um episódio); manda "Que tal avaliar?" no sininho; ativa a cena Créditos. |
| `estado(uid)` | O que a tela mostra: ligado, título e episódio, cores, cor atual, quando termina, próximo da fila. |
| `comEpisodio(f, linha)` | Junta temporada, episódio e nome do episódio no item, quando é série. |
| `fila(uid)` | A fila em ordem, com episódios e durações. |
| `proximoDaFila(uid)` | O 1º da fila, ou `null`. |
| `entrarNaFila(uid, tmdbId, temporada, episodio)` | Põe no fim da fila sem repetir (episódio precisa já ter saído). |
| `sairDaFila(uid, filaId)` | Tira da fila. |
| `subirNaFila(uid, filaId)` | Troca de lugar com o de cima. |
| `assistirProximo(uid)` | Tira o 1º da fila e começa, mantendo o ritmo. |
| `limparTerminado(uid)` | Apaga o aviso de "acabou" depois que o site perguntou a opinião. |
| `tick()` | A cada 1 segundo: acabou o tempo? → `terminarFilme`. Chegou a hora de trocar? → próxima cor na lâmpada. Se o banco cair, avisa no máximo 1 vez por minuto. |
| `buscarAtivos()` / `executarTick(ativos)` | Partes do `tick`: buscar quem está com o modo ligado e fazer a troca de cada um. |

**PaletaService**

| Função | O que faz |
| --- | --- |
| `PaletaService(db, tmdb)` | Construtor. |
| `paleta(tmdbId)` | Devolve as cores do pôster; se ainda não calculou, baixa o pôster pequeno (185 px), calcula e salva em `filmes_cache.paleta`. Pôster preto e branco → luz quente e suave. |
| `extrair(img)` | O cálculo: agrupa os pixels por cor, joga fora escuro, cinza e branco, ignora cores com menos de 2% do pôster, pega até 5 bem diferentes e deixa mais vivas. |
| `distanciaMatiz(h1, h2)` | Distância entre duas cores no círculo de cores (0 a 180 graus). |

**TuyaService** (a lâmpada; sem chaves, tudo vira simulação)

| Função | O que faz |
| --- | --- |
| `TuyaService(clientId, segredo, ...)` | Construtor: lê as chaves `TUYA_*`. |
| `configurado()` | `true` se tem chaves (manda de verdade); `false` = simulação. |
| `deviceId(doBanco)` | Qual lâmpada usar: a do banco ou a padrão. |
| `luz(deviceId, ligada, brilhoPct)` | Liga ou desliga e ajusta o brilho (0 a 100%) em luz branca. |
| `luzBranca(deviceId, brilhoPct, temperaturaPct)` | Luz branca com brilho e temperatura (0 = amarelada, 100 = branca). |
| `cor(deviceId, hex, brilhoPct)` | Luz colorida, ex.: `#ff8800`. |
| `status(deviceId)` | Lê o estado da lâmpada (`null` na simulação). |
| `dispositivosDaConta()` | Lista os aparelhos da conta Smart Life. |
| `cmd(code, value)` | Monta um comando no formato da Tuya. |
| `enviar(deviceId, comandos)` | Manda os comandos para a lâmpada. |
| `funcoes(deviceId)` | Pergunta à Tuya quais comandos aquela lâmpada aceita e os limites. |
| `token()` | Pega ou renova o token de acesso da Tuya. |
| `chamar(...)` / `requisicao(...)` | Fazem o pedido HTTP com a assinatura que a Tuya exige. |
| `sha256(texto)` / `assinar(texto, chave)` | Calculam a assinatura (SHA-256 e HMAC). |
| `hexParaHsv(hex)` | Converte `#ff8800` em matiz, saturação e brilho, que é o que a lâmpada entende. |
| `LampadaException(msg)` | Erro da lâmpada: o site continua funcionando e só mostra um aviso. |
| `Funcoes.de(lista, json)` | Guarda quais códigos a lâmpada usa e os limites de cada um. |

## notificacao/ e NotificacoesController (o sininho)

O `NotificacaoService` cria as notificações; as outras classes só chamam o gatilho certo. Uma notificação nunca derruba a ação principal: se der erro, só avisa no log.

**NotificacaoService**

| Função | Quem chama | O que faz |
| --- | --- | --- |
| `NotificacaoService(db, tmdb)` | — | Construtor. |
| `criar(uid, tipo, texto, link, atorId, tmdbId, chave)` | os gatilhos abaixo | Grava a notificação. Ninguém recebe aviso de algo que ele mesmo fez; com `chave`, a mesma notificação não se repete. |
| `arroba(uid)` / `titulo(tmdbId)` | — | Ajudantes: o @ de uma pessoa e o título de um filme, para montar o texto. |
| `seguiu(seguidor, alvo)` | `UsuariosController.seguir` | "@ana começou a seguir você." |
| `reviewDeAmigo(autor, tmdbId, reviewId)` | `ReviewsController.publicar` (só review nova) | Avisa quem segue o autor e tem o título no Favoritos ou no Quero assistir. |
| `curtida(quemCurtiu, reviewId)` | `ReviewsController.curtir` | "@ana e mais 3 pessoas curtiram sua review de X." Enquanto não for lida, atualiza a mesma notificação em vez de criar outra. |
| `listaSalva(quemSalvou, listaId)` | `ListasController.salvar` | "@ana salvou sua lista Clássicos." |
| `fimDoCinema(uid, tmdbId, temporada, episodio)` | `CinemaService.terminarFilme` | "Você terminou X (ou T1E3 de X). Que tal avaliar?" |
| `verificarLancamentos()` | roda sozinho a cada 6 horas | "Estreou!" para quem tem o título no Quero assistir e "Episódio novo" para quem está assistindo a série; só lançamentos dos últimos 7 dias, uma vez cada. |

**NotificacoesController (`/api/notificacoes`)**

| Função | Rota | O que faz |
| --- | --- | --- |
| `NotificacoesController(db)` | — | Construtor. |
| `listar(req)` | `GET /` | As 30 últimas, com foto de quem fez a ação ou pôster do título, e quantas não lidas. |
| `contar(uid)` | — | Quantas não lidas. |
| `contagem(req)` | `GET /contagem` | Só o número da bolinha (o site pergunta a cada 1 minuto). |
| `lida(id, req)` | `POST /{id}/lida` | Marca uma como lida (ao clicar nela). |
| `todasLidas(req)` | `POST /lidas` | "Marcar todas como lidas". |
