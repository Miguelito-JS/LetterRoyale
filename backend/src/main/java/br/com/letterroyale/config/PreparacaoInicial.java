package br.com.letterroyale.config;

import br.com.letterroyale.tmdb.TmdbService;
import br.com.letterroyale.util.Db;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Roda 1 vez sempre que o servidor liga. Prepara os dados do seed.sql:
 *
 *  1. SENHAS: o seed guarda 'SEED:123456' (texto). Aqui vira hash BCrypt de verdade,
 *     senão ninguém conseguiria entrar com os usuários de teste.
 *  2. FILMES: o seed só tem título/pôster. Aqui busca no TMDB o resto
 *     (ano, imagem de fundo, nota, duração) pra listas e perfil ficarem completos.
 *
 * Se algo falhar (ex.: sem internet / sem TMDB_TOKEN), só avisa no log e o site sobe normal.
 */
@Component
public class PreparacaoInicial implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PreparacaoInicial.class);
    private final Db db;
    private final TmdbService tmdb;

    public PreparacaoInicial(Db db, TmdbService tmdb) {
        this.db = db;
        this.tmdb = tmdb;
    }

    /** Cria a coluna se ela ainda não existir (atualiza bancos criados com versões antigas do schema). */
    private void garantirColuna(String tabela, String coluna, String definicao, String depois) {
        boolean existe = db.existe(
                "SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                tabela, coluna);
        if (existe) return;
        db.executar("ALTER TABLE " + tabela + " ADD COLUMN " + coluna + " " + definicao);
        if (depois != null) db.executar(depois);
        log.info("🛠️ Banco atualizado: coluna {}.{} criada", tabela, coluna);
    }

    @Override
    public void run(ApplicationArguments args) {
        // 0. ATUALIZA O BANCO sozinho (ninguém precisa rodar script de migração)
        try {
            garantirColuna("assistidos", "status",
                    "ENUM('quero_assistir','assistindo','assistido','abandonado') NOT NULL DEFAULT 'assistido'",
                    "UPDATE assistidos SET status = 'assistindo' WHERE concluido = FALSE");
            garantirColuna("usuarios", "telefone", "VARCHAR(20)", null);
            garantirColuna("usuarios", "avatar_cor", "CHAR(7)", null);   // cor de fundo da foto (#rrggbb)
            // Fila do Modo Cinema: filmes pra assistir depois do que está passando
            db.executar("""
                    CREATE TABLE IF NOT EXISTS fila_cinema (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        usuario_id INT NOT NULL,
                        tmdb_id INT NOT NULL,
                        temporada SMALLINT NOT NULL DEFAULT 0,
                        episodio SMALLINT NOT NULL DEFAULT 0,
                        titulo_ep VARCHAR(255),
                        duracao_ep SMALLINT,
                        posicao INT NOT NULL DEFAULT 0,
                        criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uq_fila_item (usuario_id, tmdb_id, temporada, episodio),
                        FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
                        FOREIGN KEY (tmdb_id) REFERENCES filmes_cache(tmdb_id) ON DELETE CASCADE
                    )
                    """);
            // Séries no Modo Cinema e na fila: guarda QUAL episódio (filme fica 0 / 0)
            garantirColuna("fila_cinema", "temporada", "SMALLINT NOT NULL DEFAULT 0", null);
            garantirColuna("fila_cinema", "episodio", "SMALLINT NOT NULL DEFAULT 0", null);
            garantirColuna("fila_cinema", "titulo_ep", "VARCHAR(255)", null);
            garantirColuna("fila_cinema", "duracao_ep", "SMALLINT", null);
            garantirColuna("cinema_ativo", "temporada", "SMALLINT", null);
            garantirColuna("cinema_ativo", "episodio", "SMALLINT", null);
            garantirColuna("cinema_ativo", "titulo_ep", "VARCHAR(255)", null);
            // A fila antiga não deixava 2 episódios da mesma série: troca a regra de "repetido"
            boolean indiceVelho = db.existe("""
                    SELECT 1 FROM information_schema.STATISTICS
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fila_cinema' AND INDEX_NAME = 'uq_fila'
                    """);
            if (indiceVelho) {
                db.executar("ALTER TABLE fila_cinema ADD UNIQUE KEY uq_fila_item (usuario_id, tmdb_id, temporada, episodio), DROP INDEX uq_fila");
                log.info("🛠️ Banco atualizado: fila aceita episódios de série");
            }
            // Etiqueta "Documentário" nos pôsteres (a lista de filmes recalcula sozinha quando abre o filme)
            garantirColuna("filmes_cache", "documentario", "BOOLEAN NOT NULL DEFAULT FALSE", null);
            // Foto do perfil enviada pelo site (fica no banco, até 600 KB)
            db.executar("""
                    CREATE TABLE IF NOT EXISTS fotos_perfil (
                        usuario_id INT PRIMARY KEY,
                        tipo VARCHAR(20) NOT NULL,
                        dados MEDIUMBLOB NOT NULL,
                        atualizado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
                    )
                    """);
            // Notificações (o sininho do topo)
            db.executar("""
                    CREATE TABLE IF NOT EXISTS notificacoes (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        usuario_id INT NOT NULL,
                        tipo VARCHAR(20) NOT NULL,
                        texto VARCHAR(255) NOT NULL,
                        link VARCHAR(120),
                        ator_id INT,
                        tmdb_id INT,
                        chave VARCHAR(80),
                        lida BOOLEAN NOT NULL DEFAULT FALSE,
                        criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uq_notif_chave (usuario_id, chave),
                        INDEX idx_notif_usuario (usuario_id, lida, criado_em),
                        FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
                        FOREIGN KEY (ator_id) REFERENCES usuarios(id) ON DELETE CASCADE
                    )
                    """);
            db.executar("UPDATE plataformas SET nome = 'Discovery+', slug = 'discovery', cor = '#1D4ED8', tmdb_provider_id = 510 WHERE slug = 'mercadoplay'");
        } catch (Exception e) {
            log.warn("Não consegui atualizar as colunas do banco: {}", e.getMessage());
        }

        try {
            BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
            List<Map<String, Object>> seeds = db.lista("SELECT id, senha_hash FROM usuarios WHERE senha_hash LIKE 'SEED:%'");
            for (Map<String, Object> u : seeds) {
                String senha = ((String) u.get("senha_hash")).substring("SEED:".length());
                db.executar("UPDATE usuarios SET senha_hash = ? WHERE id = ?", bcrypt.encode(senha), u.get("id"));
            }
            if (!seeds.isEmpty()) log.info("🔑 {} senha(s) de teste do seed convertidas para BCrypt", seeds.size());
        } catch (Exception e) {
            log.warn("Não consegui preparar as senhas do seed: {}", e.getMessage());
        }

        List<Map<String, Object>> incompletos;
        try {
            incompletos = db.lista("SELECT tmdb_id FROM filmes_cache WHERE duracao_min IS NULL LIMIT 100");
        } catch (Exception e) {
            log.warn("Não consegui ler filmes_cache (o schema.sql foi rodado?): {}", e.getMessage());
            return;
        }
        int ok = 0;
        for (Map<String, Object> f : incompletos) {
            try {
                tmdb.garantirNoCache(((Number) f.get("tmdb_id")).longValue());
                ok++;
            } catch (Exception e) {
                log.warn("TMDB não completou o filme {}: {}", f.get("tmdb_id"), e.getMessage());
                break;   
            }
        }
        if (ok > 0) log.info("🎬 {} filme(s) do cache completados com dados do TMDB", ok);
    }
}
