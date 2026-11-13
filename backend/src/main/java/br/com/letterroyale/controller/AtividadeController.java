package br.com.letterroyale.controller;

import br.com.letterroyale.erro.ApiException;
import br.com.letterroyale.seguranca.Auth;
import br.com.letterroyale.tmdb.TmdbService;
import br.com.letterroyale.util.Db;
import br.com.letterroyale.util.Json;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * STATUS DO FILME (igual ao app de jogos: Desejados / Jogando / Zerados):
 *   quero_assistir | assistindo | assistido | abandonado
 * + Favoritar + "Continue assistindo" + "Amigos assistiram"
 * Tudo fica na tabela assistidos (coluna status). concluido = (status == 'assistido').
 */
@RestController
@RequestMapping("/api/atividade")
public class AtividadeController {
    public static final Set<String> STATUS = Set.of("quero_assistir", "assistindo", "assistido", "abandonado");

    private final Db db;
    private final TmdbService tmdb;

    public AtividadeController(Db db, TmdbService tmdb) {
        this.db = db;
        this.tmdb = tmdb;
    }

    /** Grava o status (reaproveitado por outras telas). status null = tira o filme das prateleiras. */
    private void gravarStatus(long uid, long tmdbId, String status, String data) {
        if (status == null) {
            db.executar("DELETE FROM assistidos WHERE usuario_id = ? AND tmdb_id = ?", uid, tmdbId);
            return;
        }
        // "Assistindo" e "Assistido" só pra filme que já estreou ("Quero assistir" pode)
        if (!"quero_assistir".equals(status)) tmdb.exigirLancado(tmdbId);
        tmdb.garantirNoCache(tmdbId);
        boolean assistido = "assistido".equals(status);
        db.executar("""
                INSERT INTO assistidos (usuario_id, tmdb_id, status, concluido, assistido_em) VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE status = VALUES(status), concluido = VALUES(concluido),
                  assistido_em = COALESCE(VALUES(assistido_em), assistido_em)
                """, uid, tmdbId, status, assistido, assistido ? (data != null ? data : LocalDate.now().toString()) : null);
    }

    /** PUT /api/atividade/status/{tmdbId}  { status: quero_assistir|assistindo|assistido|abandonado|null } */
    @PutMapping("/status/{tmdbId}")
    public Map<String, Object> status(@PathVariable long tmdbId, @RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        String status = Json.texto(corpo, "status", null);
        if (status != null && status.isBlank()) status = null;
        if (status != null && !STATUS.contains(status)) throw new ApiException(400, "Status inválido.");
        gravarStatus(uid, tmdbId, status, Json.texto(corpo, "data", null));
        return Json.obj("status", status);
    }

    /** POST /api/atividade/assistido/{tmdbId} -> atalho liga/desliga "Assistido" */
    @PostMapping("/assistido/{tmdbId}")
    public Map<String, Object> assistido(@PathVariable long tmdbId,
                                         @RequestBody(required = false) Map<String, Object> corpo,
                                         HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        boolean ja = db.existe("SELECT 1 FROM assistidos WHERE usuario_id = ? AND tmdb_id = ? AND status = 'assistido'", uid, tmdbId);
        gravarStatus(uid, tmdbId, ja ? null : "assistido", Json.texto(corpo, "data", null));
        return Json.obj("assistido", !ja, "status", ja ? null : "assistido");
    }

    /** PUT /api/atividade/progresso/{tmdbId}  { minutos } -> "Continue assistindo" */
    @PutMapping("/progresso/{tmdbId}")
    public Map<String, Object> progresso(@PathVariable long tmdbId, @RequestBody Map<String, Object> corpo,
                                         HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        tmdb.garantirNoCache(tmdbId);
        Long minutos = Json.numero(corpo, "minutos");
        db.executar("""
                INSERT INTO assistidos (usuario_id, tmdb_id, progresso_min, status, concluido) VALUES (?, ?, ?, 'assistindo', FALSE)
                ON DUPLICATE KEY UPDATE progresso_min = VALUES(progresso_min), status = 'assistindo', concluido = FALSE
                """, uid, tmdbId, minutos == null ? 0 : minutos);
        return Json.obj("ok", true);
    }

    /** GET /api/atividade/continuar -> filmes com status "assistindo" */
    /** GET /api/atividade/minhas-notas -> { "155": 5.0, "27205": 4.5 } (pra mostrar "5.0 ★" em qualquer pôster) */
    @GetMapping("/minhas-notas")
    public Map<String, Object> minhasNotas(HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        db.lista("SELECT tmdb_id, nota FROM reviews WHERE usuario_id = ?", uid)
                .forEach(l -> m.put(String.valueOf(l.get("tmdb_id")), l.get("nota")));
        return m;
    }

    @GetMapping("/continuar")
    public List<Map<String, Object>> continuar(HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        return db.lista("""
                SELECT a.progresso_min, c.* FROM assistidos a
                  JOIN filmes_cache c ON c.tmdb_id = a.tmdb_id
                 WHERE a.usuario_id = ? AND a.status = 'assistindo'
                 ORDER BY a.atualizado_em DESC LIMIT 5
                """, uid).stream().map(l -> {
            Map<String, Object> f = TmdbService.doCache(l);
            f.put("progresso_min", l.get("progresso_min"));
            f.put("duracao_min", l.get("duracao_min"));
            return f;
        }).toList();
    }

    /** POST /api/atividade/favorito/{tmdbId} -> liga/desliga o coração */
    @PostMapping("/favorito/{tmdbId}")
    public Map<String, Object> favorito(@PathVariable long tmdbId, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        if (db.executar("DELETE FROM favoritos WHERE usuario_id = ? AND tmdb_id = ?", uid, tmdbId) > 0) {
            return Json.obj("favorito", false);
        }
        tmdb.garantirNoCache(tmdbId);
        db.executar("INSERT INTO favoritos (usuario_id, tmdb_id) VALUES (?, ?)", uid, tmdbId);
        return Json.obj("favorito", true);
    }

    /** GET /api/atividade/amigos -> "Amigos assistiram" (quem eu sigo, últimos assistidos) */
    @GetMapping("/amigos")
    public List<Map<String, Object>> amigos(HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        return db.lista("""
                SELECT u.nome, u.username, c.titulo, c.tmdb_id, a.atualizado_em
                  FROM seguidores s
                  JOIN usuarios u     ON u.id = s.usuario_id      -- usuario_id = quem eu sigo
                  JOIN assistidos a   ON a.usuario_id = u.id AND a.status = 'assistido'
                  JOIN filmes_cache c ON c.tmdb_id = a.tmdb_id
                 WHERE s.seguidor_id = ?
                 ORDER BY a.atualizado_em DESC LIMIT 6
                """, uid);
    }
}
