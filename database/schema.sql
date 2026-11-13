-- ============================================================
-- LetterRoyale - Schema Principal (schema.sql)
-- Base: schema da equipe + colunas/tabelas que o back-end Java usa
-- (marcadas com  -- [+] ). Rodar: schema.sql e depois seed.sql
--
-- Já tinha criado o banco antes? Como é projeto de faculdade, o
-- mais fácil é recriar do zero (apaga tudo!):
--   DROP DATABASE IF EXISTS letterroyale;
-- ============================================================

CREATE DATABASE IF NOT EXISTS letterroyale
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;   -- [+] acentos e emojis
USE letterroyale;

-- Tabela de Usuários
CREATE TABLE IF NOT EXISTS usuarios (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    bio VARCHAR(200),                          -- [+] Perfil: "Fã de ficção científica..."
    avatar_url VARCHAR(255),                   -- [+] foto do perfil
    google_id VARCHAR(64) UNIQUE,              -- [+] futuro "Entrar com Google"
    telefone VARCHAR(20),                      -- [+] Editar perfil
    avatar_cor CHAR(7),                        -- [+] cor de fundo da foto (#rrggbb)
    criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- [+] Foto do perfil enviada pelo site (Editar perfil -> Enviar foto)
CREATE TABLE IF NOT EXISTS fotos_perfil (
    usuario_id INT PRIMARY KEY,
    tipo VARCHAR(20) NOT NULL,                 -- image/jpeg | image/png | image/webp
    dados MEDIUMBLOB NOT NULL,
    atualizado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

-- [+] Tela "Recuperação de senha": código de 6 dígitos
CREATE TABLE IF NOT EXISTS recuperacao_senha (
    id INT AUTO_INCREMENT PRIMARY KEY,
    usuario_id INT NOT NULL,
    codigo CHAR(6) NOT NULL,
    expira_em DATETIME NOT NULL,
    usado BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

-- Tabela de Cache de Filmes do TMDB
CREATE TABLE IF NOT EXISTS filmes_cache (
    tmdb_id INT PRIMARY KEY,
    titulo VARCHAR(255) NOT NULL,
    poster_path VARCHAR(255),
    ano SMALLINT,                              -- [+] cards: "2024 ★ 8.5"
    backdrop_path VARCHAR(255),                -- [+] imagem de fundo (banner)
    nota_tmdb DECIMAL(3,1),                    -- [+] nota do TMDB
    duracao_min SMALLINT,                      -- [+] Modo Cinema sabe quando o filme acaba
    paleta VARCHAR(120),                       -- [+] cores do pôster p/ a lâmpada
    documentario BOOLEAN NOT NULL DEFAULT FALSE, -- [+] etiqueta "Documentário" (série = tmdb_id negativo)
    atualizado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Tabela de Seguidores
-- usuario_id = quem É SEGUIDO | seguidor_id = quem SEGUE
CREATE TABLE IF NOT EXISTS seguidores (
    usuario_id INT NOT NULL,
    seguidor_id INT NOT NULL,
    criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,   -- [+]
    PRIMARY KEY (usuario_id, seguidor_id),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    FOREIGN KEY (seguidor_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

-- Tabela de Listas de Filmes
CREATE TABLE IF NOT EXISTS listas (
    id INT AUTO_INCREMENT PRIMARY KEY,
    usuario_id INT NOT NULL,
    nome VARCHAR(100) NOT NULL,
    icone VARCHAR(50),
    descricao VARCHAR(200),                    -- [+]
    publica BOOLEAN NOT NULL DEFAULT TRUE,     -- [+] outras pessoas podem ver/salvar
    criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,   -- [+]
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

-- Tabela de Associação Lista-Filmes
CREATE TABLE IF NOT EXISTS lista_filmes (
    lista_id INT NOT NULL,
    tmdb_id INT NOT NULL,
    adicionado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,   -- [+] ordem dos filmes na lista
    PRIMARY KEY (lista_id, tmdb_id),
    FOREIGN KEY (lista_id) REFERENCES listas(id) ON DELETE CASCADE,
    FOREIGN KEY (tmdb_id) REFERENCES filmes_cache(tmdb_id) ON DELETE CASCADE
);

-- [+] Aba "Salvas" de Minhas listas (listas de outras pessoas)
CREATE TABLE IF NOT EXISTS listas_salvas (
    usuario_id INT NOT NULL,
    lista_id INT NOT NULL,
    PRIMARY KEY (usuario_id, lista_id),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    FOREIGN KEY (lista_id) REFERENCES listas(id) ON DELETE CASCADE
);

-- Tabela de Filmes Assistidos (+ "Continue assistindo")
CREATE TABLE IF NOT EXISTS assistidos (
    usuario_id INT NOT NULL,
    tmdb_id INT NOT NULL,
    assistido_em DATE,                         -- [+] "Assisti em 5 nov 2026"
    progresso_min INT NOT NULL DEFAULT 0,      -- [+] Continue assistindo
    concluido BOOLEAN NOT NULL DEFAULT TRUE,   -- [+] TRUE quando status = 'assistido'
    status ENUM('quero_assistir','assistindo','assistido','abandonado') NOT NULL DEFAULT 'assistido',  -- [+] botão de status do filme
    atualizado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,   -- [+]
    PRIMARY KEY (usuario_id, tmdb_id),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    FOREIGN KEY (tmdb_id) REFERENCES filmes_cache(tmdb_id) ON DELETE CASCADE
);

-- Tabela de Filmes Favoritos
CREATE TABLE IF NOT EXISTS favoritos (
    usuario_id INT NOT NULL,
    tmdb_id INT NOT NULL,
    criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,   -- [+] ordem no Perfil
    PRIMARY KEY (usuario_id, tmdb_id),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    FOREIGN KEY (tmdb_id) REFERENCES filmes_cache(tmdb_id) ON DELETE CASCADE
);

-- Tabela de Reviews / Avaliações
CREATE TABLE IF NOT EXISTS reviews (
    id INT AUTO_INCREMENT PRIMARY KEY,
    usuario_id INT NOT NULL,
    tmdb_id INT NOT NULL,
    nota DECIMAL(3,1),
    texto TEXT,
    tem_spoiler BOOLEAN DEFAULT FALSE,
    assistido_em DATE,                         -- [+]
    criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_reviews_filme (tmdb_id),         -- [+] deixa "Avaliações do filme" rápido
    UNIQUE KEY uk_usuario_filme (usuario_id, tmdb_id),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    FOREIGN KEY (tmdb_id) REFERENCES filmes_cache(tmdb_id) ON DELETE CASCADE
);

-- Tabela de Curtidas em Reviews
CREATE TABLE IF NOT EXISTS review_curtidas (
    review_id INT NOT NULL,
    usuario_id INT NOT NULL,
    PRIMARY KEY (review_id, usuario_id),
    FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

-- Tabela de Plataformas de Streaming
CREATE TABLE IF NOT EXISTS plataformas (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(50) NOT NULL,
    slug VARCHAR(40) NOT NULL UNIQUE,          -- [+]
    cor CHAR(7) NOT NULL DEFAULT '#444444',    -- [+] cor do quadradinho na tela Conexões
    tmdb_provider_id INT
);

-- Inserção de plataformas base (ids 1-4 da equipe mantidos; 5+ = tela Conexões do Figma)
-- obs: o HBO Max hoje se chama "Max" no TMDB (provider 1899); o 384 é o id antigo
INSERT INTO plataformas (id, nome, slug, cor, tmdb_provider_id) VALUES
(1, 'Netflix', 'netflix', '#E50914', 8),
(2, 'Amazon Prime Video', 'prime', '#1A98FF', 119),
(3, 'Disney+', 'disney', '#0E2A8C', 337),
(4, 'HBO Max', 'max', '#002BE7', 1899),
(5, 'Paramount+', 'paramount', '#0064FF', 531),
(6, 'Apple TV+', 'appletv', '#000000', 350),
(7, 'Crunchyroll', 'crunchyroll', '#F47521', 283),
(8, 'Looke', 'looke', '#0A6CFF', 47),
(9, 'Globoplay', 'globoplay', '#FF2D2D', 307),
(10, 'Telecine', 'telecine', '#D7182A', 227),
(11, 'Pluto TV', 'plutotv', '#FFF200', 300),
(12, 'Sky+', 'sky', '#E4002B', NULL),
(13, 'Runtime', 'runtime', '#FFC400', NULL),
(14, 'Discovery+', 'discovery', '#1D4ED8', 510),
(15, 'MUBI', 'mubi', '#001489', 11),
(16, 'DGO', 'dgo', '#111111', NULL);

-- Tabela de Conexões de Streaming do Utilizador
CREATE TABLE IF NOT EXISTS conexoes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    usuario_id INT NOT NULL,
    plataforma_id INT NOT NULL,
    nome_conta VARCHAR(100),
    exibir_no_perfil BOOLEAN DEFAULT TRUE,
    exibir_status BOOLEAN DEFAULT FALSE,
    criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,   -- [+]
    UNIQUE KEY uk_usuario_plataforma (usuario_id, plataforma_id),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    FOREIGN KEY (plataforma_id) REFERENCES plataformas(id) ON DELETE CASCADE
);

-- ============================================================
-- [+] SMART HOME (lâmpada de verdade + simulados) — ver LAMPADA.md
-- ============================================================
CREATE TABLE IF NOT EXISTS dispositivos (
    id INT AUTO_INCREMENT PRIMARY KEY,
    usuario_id INT NOT NULL,
    tipo ENUM('iluminacao','cortinas','temperatura','tv_som') NOT NULL,
    nome VARCHAR(40) NOT NULL,
    ligado BOOLEAN NOT NULL DEFAULT TRUE,
    valor INT NOT NULL DEFAULT 50,              -- % luz, °C, volume...
    estado VARCHAR(20),                         -- cortinas: abertas | meio | fechadas
    tuya_device_id VARCHAR(64),                 -- lâmpada real (NULL = usa TUYA_DEVICE_ID)
    UNIQUE KEY uk_usuario_tipo (usuario_id, tipo),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS cenas (
    id INT AUTO_INCREMENT PRIMARY KEY,
    usuario_id INT NOT NULL,
    nome VARCHAR(40) NOT NULL,                  -- Modo Cinema, Maratona, Pausa, Créditos
    descricao VARCHAR(80),
    luz INT NOT NULL,
    cortinas VARCHAR(20) NOT NULL,
    temperatura INT NOT NULL,
    volume INT NOT NULL,
    luz_temperatura INT NOT NULL DEFAULT 20,    -- 0 = amarelada (quente) ... 100 = branca (fria)
    luz_cor CHAR(7),                            -- ex: '#ff3300' = cena colorida
    luz_gradual BOOLEAN NOT NULL DEFAULT FALSE, -- TRUE = brilho sobe aos poucos (Créditos)
    ativa BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

-- "Próxima sessão" (Modo Cinema automático no horário)
-- [+] Fila do Modo Cinema: "colocar na fila" enquanto assiste outro filme
CREATE TABLE IF NOT EXISTS fila_cinema (
    id INT AUTO_INCREMENT PRIMARY KEY,
    usuario_id INT NOT NULL,
    tmdb_id INT NOT NULL,                      -- série = id negativo
    temporada SMALLINT NOT NULL DEFAULT 0,     -- série: qual episódio (filme = 0)
    episodio SMALLINT NOT NULL DEFAULT 0,
    titulo_ep VARCHAR(255),
    duracao_ep SMALLINT,
    posicao INT NOT NULL DEFAULT 0,
    criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_fila_item (usuario_id, tmdb_id, temporada, episodio),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    FOREIGN KEY (tmdb_id) REFERENCES filmes_cache(tmdb_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS sessoes_agendadas (
    id INT AUTO_INCREMENT PRIMARY KEY,
    usuario_id INT NOT NULL,
    tmdb_id INT NOT NULL,
    data_hora DATETIME NOT NULL,
    cena_id INT,
    executada BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    FOREIGN KEY (tmdb_id) REFERENCES filmes_cache(tmdb_id) ON DELETE CASCADE,
    FOREIGN KEY (cena_id) REFERENCES cenas(id) ON DELETE SET NULL
);

-- Modo Cinema: a lâmpada troca entre as cores do pôster
--   'navegando'  = acompanha o filme aberto no site
--   'assistindo' = travado num filme até acabar -> Créditos + "O que achou?"
CREATE TABLE IF NOT EXISTS cinema_ativo (
    usuario_id INT PRIMARY KEY,
    tmdb_id INT,
    modo ENUM('navegando','assistindo') NOT NULL DEFAULT 'navegando',
    ritmo_seg TINYINT NOT NULL DEFAULT 4,       -- 8 calmo | 4 normal | 2 festa
    indice_cor INT NOT NULL DEFAULT 0,
    iniciado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    termina_em DATETIME,
    terminou BOOLEAN NOT NULL DEFAULT FALSE,
    temporada SMALLINT,                         -- série: episódio que está passando
    episodio SMALLINT,
    titulo_ep VARCHAR(255),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

-- [+] Notificações (o sininho do topo). "chave" evita repetir a mesma (ex.: estreou:155)
CREATE TABLE IF NOT EXISTS notificacoes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    usuario_id INT NOT NULL,                   -- quem recebe
    tipo VARCHAR(20) NOT NULL,                 -- seguiu | review | curtida | estreou | episodio | cinema | lista
    texto VARCHAR(255) NOT NULL,
    link VARCHAR(120),                         -- pra onde o clique leva (#/filme/155...)
    ator_id INT,                               -- quem fez a ação (foto na notificação)
    tmdb_id INT,                               -- título (pôster na notificação)
    chave VARCHAR(80),
    lida BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_notif_chave (usuario_id, chave),
    INDEX idx_notif_usuario (usuario_id, lida, criado_em),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    FOREIGN KEY (ator_id) REFERENCES usuarios(id) ON DELETE CASCADE
);
