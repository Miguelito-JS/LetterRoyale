package br.com.letterroyale.controller;

import br.com.letterroyale.erro.ApiException;
import br.com.letterroyale.notificacao.NotificacaoService;
import br.com.letterroyale.seguranca.Auth;
import br.com.letterroyale.seguranca.UsuarioLogado;
import br.com.letterroyale.tmdb.TmdbService;
import br.com.letterroyale.util.Db;
import br.com.letterroyale.util.Json;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Telas: Reviews, Escrever review, Avaliações (por filme) */
@RestController
@RequestMapping("/api/reviews")
public class ReviewsController {
    private final Db db;
    private final TmdbService tmdb;
    private final NotificacaoService notificacoes;

    public ReviewsController(Db db, TmdbService tmdb, NotificacaoService notificacoes) {
        this.db = db;
        this.tmdb = tmdb;
        this.notificacoes = notificacoes;
    }

    // SELECT base reaproveitado: review + autor + filme + curtidas. O 1º "?" é o id de quem está vendo.
    private static final String SELECT_REVIEW = """
            SELECT r.id, r.tmdb_id, r.nota, r.texto, r.tem_spoiler, r.criado_em,
                   u.id AS autor_id, u.nome AS autor_nome, u.username AS autor_username,
                   c.titulo, c.ano, c.poster_path, c.backdrop_path, c.nota_tmdb,
                   (SELECT COUNT(*) FROM review_curtidas rc WHERE rc.review_id = r.id) AS curtidas,
                   (SELECT COUNT(*) FROM review_curtidas rc WHERE rc.review_id = r.id AND rc.usuario_id = ?) AS curti
              FROM reviews r
              JOIN usuarios u ON u.id = r.usuario_id
              LEFT JOIN filmes_cache c ON c.tmdb_id = r.tmdb_id
            """;

    private Map<String, Object> formatar(Map<String, Object> l) {
        return Json.obj(
                "id", l.get("id"), "nota", l.get("nota"), "texto", l.get("texto"),
                "tem_spoiler", Json.verdade(l.get("tem_spoiler")), "criado_em", l.get("criado_em"),
                "curtidas", l.get("curtidas"), "curti", Json.verdade(l.get("curti")),
                "autor", Json.obj("id", l.get("autor_id"), "nome", l.get("autor_nome"), "username", l.get("autor_username")),
                "filme", TmdbService.doCache(l));
    }

    private static long idOuZero(UsuarioLogado u) {
        return u == null ? 0 : u.id();
    }

    /** GET /api/reviews?filtro=todas|seguindo|recentes|nota_alta|nota_baixa&pagina=1 */
    @GetMapping
    public List<Map<String, Object>> feed(@RequestParam(defaultValue = "todas") String filtro,
                                          @RequestParam(defaultValue = "1") int pagina,
                                          HttpServletRequest req) {
        // ORDER BY não aceita "?", então só deixamos opções fixas (seguro contra SQL Injection)
        String ordem = switch (filtro) {
            case "recentes" -> "r.criado_em DESC";
            case "nota_alta" -> "r.nota DESC, r.criado_em DESC";
            case "nota_baixa" -> "r.nota ASC, r.criado_em DESC";
            default -> "curtidas DESC, r.criado_em DESC";
        };
        int limite = 20;
        int offset = (Math.max(1, pagina) - 1) * limite;
        long eu = idOuZero(Auth.opcional(req));
        if ("seguindo".equals(filtro)) {
            // Só reviews de quem eu sigo (aba "Amigos")
            return db.lista(SELECT_REVIEW + " WHERE r.usuario_id IN (SELECT usuario_id FROM seguidores WHERE seguidor_id = ?)"
                    + " ORDER BY r.criado_em DESC LIMIT " + limite + " OFFSET " + offset, eu, eu)
                    .stream().map(this::formatar).toList();
        }
        return db.lista(SELECT_REVIEW + " ORDER BY " + ordem + " LIMIT " + limite + " OFFSET " + offset,
                eu).stream().map(this::formatar).toList();
    }

    /** GET /api/reviews/filme/{tmdbId} -> "Avaliações" de um filme */
    @GetMapping("/filme/{tmdbId}")
    public List<Map<String, Object>> doFilme(@PathVariable long tmdbId, HttpServletRequest req) {
        return db.lista(SELECT_REVIEW + " WHERE r.tmdb_id = ? ORDER BY curtidas DESC, r.criado_em DESC LIMIT 50",
                idOuZero(Auth.opcional(req)), tmdbId).stream().map(this::formatar).toList();
    }

    /** GET /api/reviews/mais-comentados -> card "Mais comentados" */
    @GetMapping("/mais-comentados")
    public List<Map<String, Object>> maisComentados() {
        return db.lista("""
                SELECT c.*, COUNT(r.id) AS total
                  FROM reviews r JOIN filmes_cache c ON c.tmdb_id = r.tmdb_id
                 GROUP BY c.tmdb_id ORDER BY total DESC LIMIT 20
                """).stream().map(l -> {
            Map<String, Object> f = TmdbService.doCache(l);
            f.put("total_reviews", l.get("total"));
            return f;
        }).toList();
    }

    /** GET /api/reviews/minhas/resumo -> card "Suas reviews" */
    @GetMapping("/minhas/resumo")
    public Map<String, Object> meuResumo(HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        Map<String, Object> r = db.um("""
                SELECT COUNT(*) AS total, ROUND(AVG(nota),1) AS media,
                       (SELECT COUNT(*) FROM review_curtidas rc JOIN reviews r2 ON r2.id = rc.review_id
                         WHERE r2.usuario_id = ?) AS curtidas
                  FROM reviews WHERE usuario_id = ?
                """, uid, uid).orElseThrow();
        return Json.obj("total", r.get("total"), "media", r.get("media") == null ? 0 : r.get("media"), "curtidas", r.get("curtidas"));
    }

    /**
     * POST /api/reviews  { tmdb_id, nota, texto, tem_spoiler, assistido_em }
     * Se o usuário já tem review desse filme -> atualiza (1 review por filme)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> publicar(@RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        Long tmdbId = Json.numero(corpo, "tmdb_id");
        Double nota = Json.decimal(corpo, "nota");
        String texto = Json.texto(corpo, "texto", "");
        Boolean spoiler = Json.bool(corpo, "tem_spoiler");
        String assistidoEm = Json.texto(corpo, "assistido_em", null);

        if (tmdbId == null || nota == null || nota < 0.5 || nota > 5) throw new ApiException(400, "Dê uma nota de 0,5 a 5 estrelas.");
        if (texto.length() > 1000) throw new ApiException(400, "Máximo de 1000 caracteres.");

        tmdb.exigirLancado(tmdbId);   // não dá pra avaliar filme que ainda não estreou
        tmdb.garantirNoCache(tmdbId);
        boolean nova = !db.existe("SELECT 1 FROM reviews WHERE usuario_id = ? AND tmdb_id = ?", uid, tmdbId);
        db.executar("""
                INSERT INTO reviews (usuario_id, tmdb_id, nota, texto, tem_spoiler, assistido_em)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE nota = VALUES(nota), texto = VALUES(texto),
                  tem_spoiler = VALUES(tem_spoiler), assistido_em = VALUES(assistido_em)
                """, uid, tmdbId, nota, texto, spoiler != null && spoiler, assistidoEm);
        // Escrever review também marca como assistido
        db.executar("""
                INSERT INTO assistidos (usuario_id, tmdb_id, assistido_em, concluido, status) VALUES (?, ?, ?, TRUE, 'assistido')
                ON DUPLICATE KEY UPDATE concluido = TRUE, status = 'assistido'
                """, uid, tmdbId, assistidoEm);
        // Review nova: avisa os amigos que têm esse título no Favoritos / Quero assistir
        if (nova) db.um("SELECT id FROM reviews WHERE usuario_id = ? AND tmdb_id = ?", uid, tmdbId)
                .ifPresent(r -> notificacoes.reviewDeAmigo(uid, tmdbId, ((Number) r.get("id")).longValue()));
        return Json.obj("ok", true);
    }

    /** DELETE /api/reviews/{id} (só o dono) */
    @DeleteMapping("/{id}")
    public Map<String, Object> apagar(@PathVariable long id, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        if (db.executar("DELETE FROM reviews WHERE id = ? AND usuario_id = ?", id, uid) == 0)
            throw new ApiException(404, "Review não encontrada.");
        return Json.obj("ok", true);
    }

    /** POST /api/reviews/{id}/curtir -> liga/desliga o 👍 */
    @PostMapping("/{id}/curtir")
    public Map<String, Object> curtir(@PathVariable long id, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        boolean tirou = db.executar("DELETE FROM review_curtidas WHERE review_id = ? AND usuario_id = ?", id, uid) > 0;
        if (!tirou) {
            db.executar("INSERT INTO review_curtidas (review_id, usuario_id) VALUES (?, ?)", id, uid);
            notificacoes.curtida(uid, id);
        }
        long total = db.numero("SELECT COUNT(*) FROM review_curtidas WHERE review_id = ?", id);
        return Json.obj("curti", !tirou, "curtidas", total);
    }
}
