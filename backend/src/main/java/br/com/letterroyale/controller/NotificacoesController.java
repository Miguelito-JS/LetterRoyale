package br.com.letterroyale.controller;

import br.com.letterroyale.seguranca.Auth;
import br.com.letterroyale.tmdb.TmdbService;
import br.com.letterroyale.util.Db;
import br.com.letterroyale.util.Json;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** O sininho do topo: lista, quantas não lidas, marcar como lida. */
@RestController
@RequestMapping("/api/notificacoes")
public class NotificacoesController {
    private final Db db;

    public NotificacoesController(Db db) {
        this.db = db;
    }

    /** GET /api/notificacoes -> as 30 últimas + quantas não lidas */
    @GetMapping
    public Map<String, Object> listar(HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        List<Map<String, Object>> itens = db.lista("""
                SELECT n.id, n.tipo, n.texto, n.link, n.lida, n.criado_em, n.tmdb_id,
                       u.nome AS ator_nome, u.username AS ator_username, u.avatar_url AS ator_avatar_url, u.avatar_cor AS ator_avatar_cor,
                       c.poster_path
                  FROM notificacoes n
                  LEFT JOIN usuarios u     ON u.id = n.ator_id
                  LEFT JOIN filmes_cache c ON c.tmdb_id = n.tmdb_id
                 WHERE n.usuario_id = ?
                 ORDER BY n.criado_em DESC, n.id DESC LIMIT 30
                """, uid).stream().map(l -> Json.obj(
                "id", l.get("id"), "tipo", l.get("tipo"), "texto", l.get("texto"), "link", l.get("link"),
                "lida", Json.verdade(l.get("lida")), "criado_em", l.get("criado_em"),
                "ator", l.get("ator_username") == null ? null : Json.obj("nome", l.get("ator_nome"), "username", l.get("ator_username"),
                        "avatar_url", l.get("ator_avatar_url"), "avatar_cor", l.get("ator_avatar_cor")),
                "poster", TmdbService.img((String) l.get("poster_path"), "w185"))).toList();
        return Json.obj("nao_lidas", contar(uid), "itens", itens);
    }

    private long contar(long uid) {
        return db.numero("SELECT COUNT(*) FROM notificacoes WHERE usuario_id = ? AND lida = FALSE", uid);
    }

    /** GET /api/notificacoes/contagem -> só o número da bolinha (o site pergunta a cada minuto) */
    @GetMapping("/contagem")
    public Map<String, Object> contagem(HttpServletRequest req) {
        return Json.obj("nao_lidas", contar(Auth.exigir(req).id()));
    }

    /** POST /api/notificacoes/{id}/lida -> clicou numa notificação */
    @PostMapping("/{id}/lida")
    public Map<String, Object> lida(@PathVariable long id, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        db.executar("UPDATE notificacoes SET lida = TRUE WHERE id = ? AND usuario_id = ?", id, uid);
        return Json.obj("nao_lidas", contar(uid));
    }

    /** POST /api/notificacoes/lidas -> "Marcar todas como lidas" */
    @PostMapping("/lidas")
    public Map<String, Object> todasLidas(HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        db.executar("UPDATE notificacoes SET lida = TRUE WHERE usuario_id = ? AND lida = FALSE", uid);
        return Json.obj("nao_lidas", 0);
    }
}
