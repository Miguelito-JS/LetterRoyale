package br.com.letterroyale.controller;

import br.com.letterroyale.erro.ApiException;
import br.com.letterroyale.notificacao.NotificacaoService;
import br.com.letterroyale.seguranca.Auth;
import br.com.letterroyale.tmdb.TmdbService;
import br.com.letterroyale.util.Db;
import br.com.letterroyale.util.Json;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Telas: Minhas listas, Minha lista — X, botão "Adicionar à lista" */
@RestController
@RequestMapping("/api/listas")
public class ListasController {
    private final Db db;
    private final TmdbService tmdb;
    private final NotificacaoService notificacoes;

    public ListasController(Db db, TmdbService tmdb, NotificacaoService notificacoes) {
        this.db = db;
        this.tmdb = tmdb;
        this.notificacoes = notificacoes;
    }

    /** Confere se a lista é do usuário logado (senão 404) */
    private Map<String, Object> minhaLista(long listaId, long uid) {
        return db.um("SELECT * FROM listas WHERE id = ? AND usuario_id = ?", listaId, uid)
                .orElseThrow(() -> new ApiException(404, "Lista não encontrada."));
    }

    private static boolean eDoUsuario(Map<String, Object> lista, long uid) {
        return ((Number) lista.get("usuario_id")).longValue() == uid;
    }

    /** GET /api/listas?aba=todas|minhas|salvas -> cards com até 4 pôsteres */
    @GetMapping
    public List<Map<String, Object>> listar(@RequestParam(defaultValue = "todas") String aba, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        String where;
        Object[] params;
        switch (aba) {
            case "minhas" -> { where = "l.usuario_id = ?"; params = new Object[]{uid}; }
            case "salvas" -> { where = "l.id IN (SELECT lista_id FROM listas_salvas WHERE usuario_id = ?)"; params = new Object[]{uid}; }
            default -> {
                where = "(l.usuario_id = ? OR l.id IN (SELECT lista_id FROM listas_salvas WHERE usuario_id = ?))";
                params = new Object[]{uid, uid};
            }
        }
        List<Map<String, Object>> listas = db.lista("""
                SELECT l.*, u.username AS dono,
                       (SELECT COUNT(*) FROM lista_filmes lf WHERE lf.lista_id = l.id) AS total
                  FROM listas l JOIN usuarios u ON u.id = l.usuario_id
                """ + " WHERE " + where + " ORDER BY l.criado_em", params);

        for (Map<String, Object> l : listas) {
            // Capa: 4 primeiros pôsteres de cada lista
            List<Object> capas = db.lista("""
                    SELECT c.* FROM lista_filmes lf JOIN filmes_cache c ON c.tmdb_id = lf.tmdb_id
                     WHERE lf.lista_id = ? ORDER BY lf.adicionado_em DESC LIMIT 4
                    """, l.get("id")).stream().map(c -> TmdbService.doCache(c).get("poster")).filter(Objects::nonNull).toList();
            l.put("capas", capas);
            l.put("minha", eDoUsuario(l, uid));
        }
        return listas;
    }

    /** POST /api/listas { nome, descricao, icone, publica } -> "+ Nova lista" */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> criar(@RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        String nome = Json.texto(corpo, "nome", "").trim();
        if (nome.isEmpty()) throw new ApiException(400, "Dê um nome para a lista.");
        if (nome.length() > 60) nome = nome.substring(0, 60);
        Boolean publica = Json.bool(corpo, "publica");
        long id = db.inserir("INSERT INTO listas (usuario_id, nome, descricao, icone, publica) VALUES (?, ?, ?, ?, ?)",
                uid, nome, Json.texto(corpo, "descricao", null), Json.texto(corpo, "icone", "lista"), publica == null || publica);
        return Json.obj("id", id);
    }

    /** GET /api/listas/{id} -> filmes da lista */
    @GetMapping("/{id}")
    public Map<String, Object> ver(@PathVariable long id, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        Map<String, Object> l = db.um("""
                SELECT l.*, u.username AS dono FROM listas l JOIN usuarios u ON u.id = l.usuario_id
                 WHERE l.id = ? AND (l.publica = TRUE OR l.usuario_id = ?)
                """, id, uid).orElseThrow(() -> new ApiException(404, "Lista não encontrada."));
        List<Map<String, Object>> filmes = db.lista("""
                SELECT c.* FROM lista_filmes lf JOIN filmes_cache c ON c.tmdb_id = lf.tmdb_id
                 WHERE lf.lista_id = ? ORDER BY lf.adicionado_em DESC
                """, id).stream().map(TmdbService::doCache).toList();
        l.put("minha", eDoUsuario(l, uid));
        l.put("filmes", filmes);
        return l;
    }

    /** PUT /api/listas/{id} { nome, descricao, icone, publica } */
    @PutMapping("/{id}")
    public Map<String, Object> editar(@PathVariable long id, @RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        Map<String, Object> l = minhaLista(id, Auth.exigir(req).id());
        Boolean publica = Json.bool(corpo, "publica");
        db.executar("UPDATE listas SET nome = ?, descricao = ?, icone = ?, publica = ? WHERE id = ?",
                Json.texto(corpo, "nome", (String) l.get("nome")),
                Json.texto(corpo, "descricao", (String) l.get("descricao")),
                Json.texto(corpo, "icone", (String) l.get("icone")),
                publica != null ? publica : Json.verdade(l.get("publica")), id);
        return Json.obj("ok", true);
    }

    /** DELETE /api/listas/{id} */
    @DeleteMapping("/{id}")
    public Map<String, Object> apagar(@PathVariable long id, HttpServletRequest req) {
        if (db.executar("DELETE FROM listas WHERE id = ? AND usuario_id = ?", id, Auth.exigir(req).id()) == 0)
            throw new ApiException(404, "Lista não encontrada.");
        return Json.obj("ok", true);
    }

    /** POST /api/listas/{id}/filmes { tmdb_id } -> "Adicionar à lista" */
    @PostMapping("/{id}/filmes")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> adicionarFilme(@PathVariable long id, @RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        minhaLista(id, Auth.exigir(req).id());
        Long tmdbId = Json.numero(corpo, "tmdb_id");
        if (tmdbId == null) throw new ApiException(400, "Filme inválido.");
        tmdb.garantirNoCache(tmdbId);
        db.executar("INSERT IGNORE INTO lista_filmes (lista_id, tmdb_id) VALUES (?, ?)", id, tmdbId);
        return Json.obj("ok", true);
    }

    /** DELETE /api/listas/{id}/filmes/{tmdbId} */
    @DeleteMapping("/{id}/filmes/{tmdbId}")
    public Map<String, Object> tirarFilme(@PathVariable long id, @PathVariable long tmdbId, HttpServletRequest req) {
        minhaLista(id, Auth.exigir(req).id());
        db.executar("DELETE FROM lista_filmes WHERE lista_id = ? AND tmdb_id = ?", id, tmdbId);
        return Json.obj("ok", true);
    }

    /** POST /api/listas/{id}/salvar -> salvar/tirar lista de outra pessoa (aba "Salvas") */
    @PostMapping("/{id}/salvar")
    public Map<String, Object> salvar(@PathVariable long id, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        boolean tirou = db.executar("DELETE FROM listas_salvas WHERE usuario_id = ? AND lista_id = ?", uid, id) > 0;
        if (!tirou) {
            db.executar("INSERT INTO listas_salvas (usuario_id, lista_id) VALUES (?, ?)", uid, id);
            notificacoes.listaSalva(uid, id);
        }
        return Json.obj("salva", !tirou);
    }
}
