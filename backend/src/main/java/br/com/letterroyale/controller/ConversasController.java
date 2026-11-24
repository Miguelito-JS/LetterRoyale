package br.com.letterroyale.controller;

import br.com.letterroyale.erro.ApiException;
import br.com.letterroyale.notificacao.NotificacaoService;
import br.com.letterroyale.seguranca.Auth;
import br.com.letterroyale.seguranca.UsuarioLogado;
import br.com.letterroyale.tmdb.TmdbService;
import br.com.letterroyale.util.Db;
import br.com.letterroyale.util.Json;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * =====================================================================
 *  CONVERSAS: posts curtos estilo Twitter na tela Reviews.
 *  Ex.: "Me indiquem um filme de terror pra hoje!" e a galera responde,
 *  podendo marcar um filme na resposta (vira um cartãozinho clicável).
 *
 *   GET    /api/conversas?filtro=todas|seguindo|minhas
 *   POST   /api/conversas                {texto, tmdb_id?}
 *   GET    /api/conversas/{id}/respostas
 *   POST   /api/conversas/{id}/respostas {texto, tmdb_id?}   -> avisa o dono do post
 *   POST   /api/conversas/{id}/curtir    (liga/desliga)
 *   DELETE /api/conversas/{id}           (só o dono)
 * =====================================================================
 */
@RestController
@RequestMapping("/api/conversas")
public class ConversasController {
    static final int MAX_TEXTO = 500;

    private final Db db;
    private final TmdbService tmdb;
    private final NotificacaoService notificacoes;

    public ConversasController(Db db, TmdbService tmdb, NotificacaoService notificacoes) {
        this.db = db;
        this.tmdb = tmdb;
        this.notificacoes = notificacoes;
    }

    /** As tabelas se criam sozinhas (quem já tinha o banco não precisa rodar nada). Mesmas do database/schema.sql. */
    @PostConstruct
    void criarTabelas() {
        db.executar("""
                CREATE TABLE IF NOT EXISTS conversas (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    usuario_id INT NOT NULL,
                    texto VARCHAR(500) NOT NULL,
                    tmdb_id INT NULL,
                    criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_conversas_data (criado_em),
                    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
                )""");
        db.executar("""
                CREATE TABLE IF NOT EXISTS conversa_respostas (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    conversa_id INT NOT NULL,
                    usuario_id INT NOT NULL,
                    texto VARCHAR(500) NOT NULL,
                    tmdb_id INT NULL,
                    criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_respostas_conversa (conversa_id),
                    FOREIGN KEY (conversa_id) REFERENCES conversas(id) ON DELETE CASCADE,
                    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
                )""");
        db.executar("""
                CREATE TABLE IF NOT EXISTS conversa_curtidas (
                    conversa_id INT NOT NULL,
                    usuario_id INT NOT NULL,
                    PRIMARY KEY (conversa_id, usuario_id),
                    FOREIGN KEY (conversa_id) REFERENCES conversas(id) ON DELETE CASCADE,
                    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
                )""");
    }

    // O 1º "?" é quem está vendo (pra saber se curtiu)
    private static final String SELECT_CONVERSA = """
            SELECT p.id, p.texto, p.tmdb_id, p.criado_em,
                   u.id AS autor_id, u.nome AS autor_nome, u.username AS autor_username, u.avatar_url, u.avatar_cor,
                   c.titulo, c.ano, c.poster_path, c.backdrop_path, c.nota_tmdb,
                   (SELECT COUNT(*) FROM conversa_respostas r WHERE r.conversa_id = p.id) AS respostas,
                   (SELECT COUNT(*) FROM conversa_curtidas k WHERE k.conversa_id = p.id) AS curtidas,
                   (SELECT COUNT(*) FROM conversa_curtidas k WHERE k.conversa_id = p.id AND k.usuario_id = ?) AS curti
              FROM conversas p
              JOIN usuarios u ON u.id = p.usuario_id
              LEFT JOIN filmes_cache c ON c.tmdb_id = p.tmdb_id
            """;

    private static Map<String, Object> autor(Map<String, Object> l) {
        return Json.obj("id", l.get("autor_id"), "nome", l.get("autor_nome"), "username", l.get("autor_username"),
                "avatar_url", l.get("avatar_url"), "avatar_cor", l.get("avatar_cor"));
    }

    private static Map<String, Object> conversa(Map<String, Object> l, long eu) {
        return Json.obj("id", l.get("id"), "tipo", "conversa", "texto", l.get("texto"), "criado_em", l.get("criado_em"),
                "respostas", l.get("respostas"), "curtidas", l.get("curtidas"), "curti", Json.verdade(l.get("curti")),
                "minha", ((Number) l.get("autor_id")).longValue() == eu,
                "autor", autor(l), "filme", l.get("tmdb_id") == null ? null : TmdbService.doCache(l));
    }

    @GetMapping
    public List<Map<String, Object>> lista(@RequestParam(defaultValue = "todas") String filtro, HttpServletRequest req) {
        UsuarioLogado u = Auth.opcional(req);
        long eu = u == null ? 0 : u.id();
        String onde = switch (filtro) {
            case "seguindo" -> " WHERE p.usuario_id IN (SELECT usuario_id FROM seguidores WHERE seguidor_id = ?) OR p.usuario_id = ?";
            case "minhas" -> " WHERE p.usuario_id = ?";
            default -> "";
        };
        String sql = SELECT_CONVERSA + onde + " ORDER BY p.criado_em DESC LIMIT 30";
        List<Map<String, Object>> linhas = switch (filtro) {
            case "seguindo" -> db.lista(sql, eu, eu, eu);
            case "minhas" -> db.lista(sql, eu, eu);
            default -> db.lista(sql, eu);
        };
        return linhas.stream().map(l -> conversa(l, eu)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> postar(@RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        long eu = Auth.exigir(req).id();
        String texto = textoValido(corpo);
        Long tmdbId = filmeValido(corpo);
        long id = db.inserir("INSERT INTO conversas (usuario_id, texto, tmdb_id) VALUES (?, ?, ?)", eu, texto, tmdbId);
        return db.um(SELECT_CONVERSA + " WHERE p.id = ?", eu, id).map(l -> conversa(l, eu)).orElseThrow();
    }

    @GetMapping("/{id}/respostas")
    public List<Map<String, Object>> respostas(@PathVariable long id) {
        return db.lista("""
                SELECT r.id, r.texto, r.tmdb_id, r.criado_em,
                       u.id AS autor_id, u.nome AS autor_nome, u.username AS autor_username, u.avatar_url, u.avatar_cor,
                       c.titulo, c.ano, c.poster_path, c.backdrop_path, c.nota_tmdb
                  FROM conversa_respostas r
                  JOIN usuarios u ON u.id = r.usuario_id
                  LEFT JOIN filmes_cache c ON c.tmdb_id = r.tmdb_id
                 WHERE r.conversa_id = ? ORDER BY r.criado_em ASC LIMIT 100
                """, id).stream().map(l -> Json.obj("id", l.get("id"), "texto", l.get("texto"), "criado_em", l.get("criado_em"),
                "autor", autor(l), "filme", l.get("tmdb_id") == null ? null : TmdbService.doCache(l))).toList();
    }

    @PostMapping("/{id}/respostas")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> responder(@PathVariable long id, @RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        UsuarioLogado u = Auth.exigir(req);
        Map<String, Object> p = db.um("SELECT usuario_id, texto FROM conversas WHERE id = ?", id)
                .orElseThrow(() -> new ApiException(404, "Essa conversa foi apagada."));
        String texto = textoValido(corpo);
        Long tmdbId = filmeValido(corpo);
        db.inserir("INSERT INTO conversa_respostas (conversa_id, usuario_id, texto, tmdb_id) VALUES (?, ?, ?, ?)", id, u.id(), texto, tmdbId);
        long dono = ((Number) p.get("usuario_id")).longValue();
        notificacoes.criar(dono, "conversa", "@" + u.username() + " respondeu sua conversa: " + texto,
                "#/reviews?filtro=conversas&c=" + id, u.id(), tmdbId, null);
        return Json.obj("ok", true, "respostas", db.numero("SELECT COUNT(*) FROM conversa_respostas WHERE conversa_id = ?", id));
    }

    @PostMapping("/{id}/curtir")
    public Map<String, Object> curtir(@PathVariable long id, HttpServletRequest req) {
        long eu = Auth.exigir(req).id();
        if (!db.existe("SELECT id FROM conversas WHERE id = ?", id)) throw new ApiException(404, "Essa conversa foi apagada.");
        boolean curti = db.existe("SELECT 1 FROM conversa_curtidas WHERE conversa_id = ? AND usuario_id = ?", id, eu);
        if (curti) db.executar("DELETE FROM conversa_curtidas WHERE conversa_id = ? AND usuario_id = ?", id, eu);
        else {
            db.executar("INSERT INTO conversa_curtidas (conversa_id, usuario_id) VALUES (?, ?)", id, eu);
            notificacoes.curtidaConversa(eu, id);
        }
        return Json.obj("curti", !curti, "curtidas", db.numero("SELECT COUNT(*) FROM conversa_curtidas WHERE conversa_id = ?", id));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> apagar(@PathVariable long id, HttpServletRequest req) {
        long eu = Auth.exigir(req).id();
        if (db.executar("DELETE FROM conversas WHERE id = ? AND usuario_id = ?", id, eu) == 0)
            throw new ApiException(404, "Conversa não encontrada.");
        return Json.obj("ok", true);
    }

    private static String textoValido(Map<String, Object> corpo) {
        String t = Json.texto(corpo, "texto", "").trim();
        if (t.isEmpty()) throw new ApiException(400, "Escreva alguma coisa.");
        if (t.length() > MAX_TEXTO) throw new ApiException(400, "No máximo " + MAX_TEXTO + " caracteres.");
        return t;
    }

    private Long filmeValido(Map<String, Object> corpo) {
        Long id = Json.numero(corpo, "tmdb_id");
        if (id == null || id == 0) return null;
        tmdb.garantirNoCache(id);   // o cartãozinho do filme precisa de título e pôster
        return id;
    }
}
