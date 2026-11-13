-- ============================================================
-- LetterRoyale - Dados de Teste (seed.sql)
-- Arquivo para popular o banco de dados inicial para testes
--
-- LOGIN DOS USUÁRIOS DE TESTE:  senha 123456
--   ana@email.com  |  carlos@email.com  |  mari@email.com
-- (a senha fica como 'SEED:123456' aqui; quando o back-end liga,
--  ele troca sozinho pelo hash BCrypt de verdade — ver SeedSenhas.java)
--
-- Os filmes só têm título/pôster aqui. O back-end completa sozinho
-- (ano, fundo, nota, duração) buscando no TMDB quando liga.
-- ============================================================

USE letterroyale;

-- 1. Inserção de Usuários de Teste
INSERT INTO usuarios (id, nome, username, email, senha_hash) VALUES
(1, 'Ana Souza', 'anasouza', 'ana@email.com', 'SEED:123456'),
(2, 'Carlos Lima', 'carloslima', 'carlos@email.com', 'SEED:123456'),
(3, 'Mariana Dias', 'marianadias', 'mari@email.com', 'SEED:123456');

-- 2. Inserção de Relacionamentos de Seguidores
INSERT INTO seguidores (usuario_id, seguidor_id) VALUES
(1, 2),
(1, 3),
(2, 1),
(3, 2);

-- 3. Inserção de Cache de Filmes (filmes_cache)
INSERT INTO filmes_cache (tmdb_id, titulo, poster_path) VALUES
(693134, 'Duna: Parte 2', NULL),   -- pôster vem do TMDB ao ligar o back-end
(157336, 'Interstellar', '/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg'),
(27205, 'Inception', '/ljs26YviZBdNIATwhKGwo2v8Mvr.jpg'),
(550, 'Clube da Luta', '/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg');

-- 4. Inserção de Listas Padrão e Personalizadas
INSERT INTO listas (id, usuario_id, nome, icone) VALUES
(1, 1, 'Favoritos', 'heart'),
(2, 1, 'Quero Assistir', 'bookmark'),
(3, 1, 'Melhores de Sci-Fi', 'star'),
(4, 2, 'Favoritos', 'heart'),
(5, 3, 'Favoritos', 'heart');

-- 5. Inserção de Filmes nas Listas
INSERT INTO lista_filmes (lista_id, tmdb_id) VALUES
(1, 693134),
(1, 157336),
(3, 27205),
(4, 550);

-- 6. Inserção de Filmes Assistidos e Favoritos
INSERT INTO assistidos (usuario_id, tmdb_id) VALUES
(1, 693134),
(1, 157336),
(2, 550),
(3, 27205);

INSERT INTO favoritos (usuario_id, tmdb_id) VALUES
(1, 693134),
(2, 550);

-- 7. Inserção de Reviews (Avaliações)
INSERT INTO reviews (id, usuario_id, tmdb_id, nota, texto, tem_spoiler) VALUES
(1, 1, 693134, 5.0, 'Uma experiência visual e sonora épica! A direção de Denis Villeneuve é impecável.', 0),
(2, 2, 693134, 4.5, 'Excelente sequência, supera o primeiro em todos os aspectos visuais.', 0),
(3, 3, 157336, 5.0, 'Trilha sonora inesquecível de Hans Zimmer. Um clássico moderno do cinema de ficção.', 0),
(4, 1, 550, 4.8, 'Um clássico atemporal com plot twist espetacular.', 1);

-- 8. Curtidas em Reviews
INSERT INTO review_curtidas (review_id, usuario_id) VALUES
(1, 2),
(1, 3),
(3, 1);

-- 9. Conexões de Usuários com Plataformas de Streaming
INSERT INTO conexoes (usuario_id, plataforma_id, nome_conta, exibir_no_perfil, exibir_status) VALUES
(1, 1, 'AnaSouzaNetflix', 1, 1),
(2, 2, 'CarlosPrime', 1, 1);

-- 10. [+] Smart Home de cada usuário de teste (dispositivos + cenas)
INSERT INTO dispositivos (usuario_id, tipo, nome, valor, estado)
SELECT u.id, d.tipo, d.nome, d.valor, d.estado
  FROM usuarios u
  CROSS JOIN (
    SELECT 'iluminacao' AS tipo, 'Iluminação' AS nome, 70 AS valor, NULL AS estado UNION ALL
    SELECT 'cortinas', 'Cortinas', 0, 'fechadas' UNION ALL
    SELECT 'temperatura', 'Temperatura', 22, NULL UNION ALL
    SELECT 'tv_som', 'TV e Som', 60, NULL
  ) d
 WHERE u.id IN (1, 2, 3);

INSERT INTO cenas (usuario_id, nome, descricao, luz, cortinas, temperatura, volume, luz_temperatura, luz_cor, luz_gradual)
SELECT u.id, c.nome, c.descricao, c.luz, c.cortinas, c.temperatura, c.volume, c.luz_temperatura, NULL, c.luz_gradual
  FROM usuarios u
  CROSS JOIN (
    SELECT 'Modo Cinema' AS nome, 'Luz 10%, cortinas fechadas' AS descricao, 10 AS luz, 'fechadas' AS cortinas, 22 AS temperatura, 60 AS volume, 0 AS luz_temperatura, FALSE AS luz_gradual UNION ALL
    SELECT 'Maratona', 'Luz 20%, ar em 21°C', 20, 'fechadas', 21, 50, 10, FALSE UNION ALL
    SELECT 'Pausa', 'Luz 60%, som baixo', 60, 'meio', 22, 15, 50, FALSE UNION ALL
    SELECT 'Créditos', 'Luz sobe aos poucos', 80, 'abertas', 22, 30, 30, TRUE
  ) c
 WHERE u.id IN (1, 2, 3);

-- 11. [+] Bio da Ana (aparece no Perfil)
UPDATE usuarios SET bio = 'Fã de ficção científica e trilhas sonoras. Sempre com pipoca.' WHERE id = 1;
