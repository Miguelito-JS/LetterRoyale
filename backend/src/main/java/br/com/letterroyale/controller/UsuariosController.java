package br.com.letterroyale.controller;

import br.com.letterroyale.erro.ApiException;
import br.com.letterroyale.notificacao.NotificacaoService;
import br.com.letterroyale.seguranca.Auth;
import br.com.letterroyale.seguranca.JwtService;
import br.com.letterroyale.seguranca.UsuarioLogado;
import br.com.letterroyale.tmdb.TmdbService;
import br.com.letterroyale.util.Db;
import br.com.letterroyale.util.Json;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tela Perfil (abas Filmes / Listas / Avaliações / Estatísticas), "Mostrar tudo",
 * Editar perfil, trocar senha e seguir amigos.
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuariosController {
    private final Db db;
    private final JwtService jwt;
    private final NotificacaoService notificacoes;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    public UsuariosController(Db db, JwtService jwt, NotificacaoService notificacoes) {
        this.db = db;
        this.jwt = jwt;
        this.notificacoes = notificacoes;
    }

    private Map<String, Object> buscarUsuario(String username, UsuarioLogado eu) {
        String user = "me".equals(username) ? eu.username() : username;
        return db.um("SELECT id, nome, username, bio, avatar_url, avatar_cor FROM usuarios WHERE username = ?", user)
                .orElseThrow(() -> new ApiException(404, "Usuário não encontrado."));
    }

    /** Filme do cache + a nota que o usuário deu (pra aparecer no cantinho do pôster) */
    /** serie = true -> só séries (id negativo); false -> só filmes */
    private static String selectPrateleira(boolean serie) {
        return """
            SELECT c.*, a.status, a.assistido_em, r.nota AS minha_nota
              FROM assistidos a
              JOIN filmes_cache c ON c.tmdb_id = a.tmdb_id
              LEFT JOIN reviews r ON r.usuario_id = a.usuario_id AND r.tmdb_id = a.tmdb_id
             WHERE a.usuario_id = ? AND a.status = ? AND a.tmdb_id %s 0
             ORDER BY a.atualizado_em DESC
            """.formatted(serie ? "<" : ">");
    }

    private static Map<String, Object> filmeDaPrateleira(Map<String, Object> l) {
        Map<String, Object> f = TmdbService.doCache(l);
        f.put("minha_nota", l.get("minha_nota"));
        f.put("status", l.get("status"));
        f.put("assistido_em", l.get("assistido_em") == null ? null : String.valueOf(l.get("assistido_em")));
        return f;
    }

    /** GET /api/usuarios/{username}   ("me" = eu mesmo) */
    @GetMapping("/{username}")
    public Map<String, Object> perfil(@PathVariable String username, HttpServletRequest req) {
        UsuarioLogado eu = Auth.exigir(req);
        Map<String, Object> u = buscarUsuario(username, eu);
        long id = ((Number) u.get("id")).longValue();

        Map<String, Object> stats = db.um("""
                SELECT
                  (SELECT COUNT(*) FROM assistidos WHERE usuario_id = ? AND status = 'assistido' AND tmdb_id > 0) AS assistidos,
                  (SELECT COUNT(*) FROM assistidos WHERE usuario_id = ? AND status = 'assistido' AND tmdb_id < 0) AS series,
                  (SELECT COUNT(*) FROM reviews    WHERE usuario_id = ?) AS reviews,
                  (SELECT COUNT(*) FROM listas     WHERE usuario_id = ?) AS listas,
                  (SELECT ROUND(AVG(nota),1) FROM reviews WHERE usuario_id = ?) AS nota_media
                """, id, id, id, id, id).orElseThrow();
        if (stats.get("nota_media") == null) stats.put("nota_media", 0);

        // Favoritos e prateleiras: filmes e séries separados (aba Filmes / aba Séries do perfil)
        String favSql = """
                SELECT c.* FROM favoritos f JOIN filmes_cache c ON c.tmdb_id = f.tmdb_id
                 WHERE f.usuario_id = ? AND f.tmdb_id %s 0 ORDER BY f.criado_em DESC LIMIT 20
                """;
        List<Map<String, Object>> favoritos = db.lista(favSql.formatted(">"), id).stream().map(TmdbService::doCache).toList();
        List<Map<String, Object>> favoritosSeries = db.lista(favSql.formatted("<"), id).stream().map(TmdbService::doCache).toList();

        // Prateleiras (igual "Desejados / Jogando / Zerados" do app de jogos)
        Map<String, Object> prateleiras = new LinkedHashMap<>();
        Map<String, Object> prateleirasSeries = new LinkedHashMap<>();
        for (String status : List.of("quero_assistir", "assistindo", "assistido", "abandonado")) {
            for (boolean serie : new boolean[]{false, true}) {
                long total = db.numero("SELECT COUNT(*) FROM assistidos WHERE usuario_id = ? AND status = ? AND tmdb_id " + (serie ? "<" : ">") + " 0", id, status);
                List<Map<String, Object>> itens = db.lista(selectPrateleira(serie) + " LIMIT 12", id, status)
                        .stream().map(UsuariosController::filmeDaPrateleira).toList();
                (serie ? prateleirasSeries : prateleiras).put(status, Json.obj("total", total, "filmes", itens));
            }
        }

        List<Map<String, Object>> listas = db.lista("""
                SELECT l.id, l.nome, l.icone, (SELECT COUNT(*) FROM lista_filmes WHERE lista_id = l.id) AS total
                  FROM listas l WHERE l.usuario_id = ? AND (l.publica = TRUE OR l.usuario_id = ?)
                 ORDER BY l.criado_em
                """, id, eu.id());

        List<Map<String, Object>> conexoes = db.lista("""
                SELECT p.nome, p.slug, p.cor, c.nome_conta, c.exibir_status FROM conexoes c
                  JOIN plataformas p ON p.id = c.plataforma_id
                 WHERE c.usuario_id = ? AND c.exibir_no_perfil = TRUE
                """, id);

        u.put("eu", id == eu.id());
        u.put("sigo", db.existe("SELECT 1 FROM seguidores WHERE seguidor_id = ? AND usuario_id = ?", eu.id(), id));
        u.put("seguidores", db.numero("SELECT COUNT(*) FROM seguidores WHERE usuario_id = ?", id));
        u.put("seguindo", db.numero("SELECT COUNT(*) FROM seguidores WHERE seguidor_id = ?", id));
        u.put("stats", stats);
        u.put("favoritos", favoritos);
        u.put("prateleiras", prateleiras);
        u.put("favoritos_series", favoritosSeries);
        u.put("prateleiras_series", prateleirasSeries);
        u.put("listas", listas);
        u.put("conexoes", conexoes);
        return u;
    }

    /** GET /api/usuarios/{username}/filmes?status=assistido -> página "Mostrar tudo" */
    @GetMapping("/{username}/filmes")
    public List<Map<String, Object>> filmes(@PathVariable String username,
                                            @RequestParam(defaultValue = "assistido") String status,
                                            @RequestParam(required = false) String tipo,
                                            HttpServletRequest req) {
        Map<String, Object> u = buscarUsuario(username, Auth.exigir(req));
        if (!AtividadeController.STATUS.contains(status)) throw new ApiException(400, "Status inválido.");
        return db.lista(selectPrateleira("serie".equals(tipo)) + " LIMIT 500", u.get("id"), status)
                .stream().map(UsuariosController::filmeDaPrateleira).toList();
    }

    /** GET /api/usuarios/{username}/reviews -> aba "Avaliações" do perfil */
    @GetMapping("/{username}/reviews")
    public List<Map<String, Object>> reviews(@PathVariable String username, HttpServletRequest req) {
        UsuarioLogado eu = Auth.exigir(req);
        Map<String, Object> u = buscarUsuario(username, eu);
        return db.lista("""
                SELECT r.id, r.nota, r.texto, r.tem_spoiler, r.criado_em, c.*,
                       (SELECT COUNT(*) FROM review_curtidas rc WHERE rc.review_id = r.id) AS curtidas,
                       (SELECT COUNT(*) FROM review_curtidas rc WHERE rc.review_id = r.id AND rc.usuario_id = ?) AS curti
                  FROM reviews r JOIN filmes_cache c ON c.tmdb_id = r.tmdb_id
                 WHERE r.usuario_id = ? ORDER BY r.criado_em DESC LIMIT 100
                """, eu.id(), u.get("id")).stream().map(l -> Json.obj(
                "id", l.get("id"), "nota", l.get("nota"), "texto", l.get("texto"),
                "tem_spoiler", Json.verdade(l.get("tem_spoiler")), "criado_em", l.get("criado_em"),
                "curtidas", l.get("curtidas"), "curti", Json.verdade(l.get("curti")),
                "autor", Json.obj("id", u.get("id"), "nome", u.get("nome"), "username", u.get("username")),
                "filme", TmdbService.doCache(l))).toList();
    }

    /** GET /api/usuarios/{username}/estatisticas -> aba "Estatísticas" */
    @GetMapping("/{username}/estatisticas")
    public Map<String, Object> estatisticas(@PathVariable String username, HttpServletRequest req) {
        Map<String, Object> u = buscarUsuario(username, Auth.exigir(req));
        Object id = u.get("id");
        Map<String, Object> geral = db.um("""
                SELECT COUNT(*) AS filmes,
                       COALESCE(SUM(c.duracao_min), 0) AS minutos,
                       SUM(YEAR(COALESCE(a.assistido_em, a.atualizado_em)) = YEAR(CURDATE())) AS este_ano
                  FROM assistidos a JOIN filmes_cache c ON c.tmdb_id = a.tmdb_id
                 WHERE a.usuario_id = ? AND a.status = 'assistido' AND a.tmdb_id > 0
                """, id).orElseThrow();
        // Filmes assistidos por mês (últimos 12 meses) -> gráfico de barrinhas
        List<Map<String, Object>> porMes = db.lista("""
                SELECT DATE_FORMAT(COALESCE(assistido_em, atualizado_em), '%Y-%m') AS mes, COUNT(*) AS total
                  FROM assistidos
                 WHERE usuario_id = ? AND status = 'assistido' AND tmdb_id > 0
                   AND COALESCE(assistido_em, atualizado_em) >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
                 GROUP BY mes ORDER BY mes
                """, id);
        // Quantos filmes deu cada nota (0,5 a 5)
        List<Map<String, Object>> notas = db.lista(
                "SELECT nota, COUNT(*) AS total FROM reviews WHERE usuario_id = ? GROUP BY nota ORDER BY nota", id);
        Map<String, Object> mediaAno = db.um("SELECT ROUND(AVG(nota),1) AS media FROM reviews WHERE usuario_id = ?", id).orElseThrow();
        long minutos = ((Number) geral.get("minutos")).longValue();
        return Json.obj(
                "filmes", geral.get("filmes"),
                "horas", Math.round(minutos / 60.0),
                "este_ano", geral.get("este_ano") == null ? 0 : geral.get("este_ano"),
                "nota_media", mediaAno.get("media") == null ? 0 : mediaAno.get("media"),
                "por_mes", porMes,
                "notas", notas);
    }

    /**
     * PUT /api/usuarios/me  { nome, username, email, telefone, bio, avatar_url, avatar_cor } -> "Editar perfil"
     * Se mudar o username, devolve um token novo (o token guarda o username).
     */
    @PutMapping("/me")
    public Map<String, Object> editar(@RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        Map<String, Object> atual = db.um("SELECT * FROM usuarios WHERE id = ?", uid).orElseThrow();

        String nome = Json.texto(corpo, "nome", (String) atual.get("nome")).trim();
        String username = Json.texto(corpo, "username", (String) atual.get("username")).trim().replaceFirst("^@", "").toLowerCase();
        String email = Json.texto(corpo, "email", (String) atual.get("email")).trim().toLowerCase();
        String telefone = Json.texto(corpo, "telefone", (String) atual.get("telefone"));
        String bio = Json.texto(corpo, "bio", (String) atual.get("bio"));
        String avatar = Json.texto(corpo, "avatar_url", (String) atual.get("avatar_url"));
        String cor = corpo.containsKey("avatar_cor") ? (String) corpo.get("avatar_cor") : (String) atual.get("avatar_cor");
        if (cor != null && cor.isBlank()) cor = null;
        if (cor != null && !cor.matches("#[0-9a-fA-F]{6}")) throw new ApiException(400, "Cor inválida.");

        if (nome.isEmpty() || nome.length() > 100) throw new ApiException(400, "Nome inválido.");
        if (!username.matches("[a-z0-9_.]{3,30}")) throw new ApiException(400, "Usuário: 3 a 30 letras, números, _ ou ponto.");
        if (db.existe("SELECT 1 FROM usuarios WHERE username = ? AND id <> ?", username, uid))
            throw new ApiException(409, "Esse nome de usuário já está em uso.");
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") || email.length() > 100) throw new ApiException(400, "E-mail inválido.");
        if (db.existe("SELECT 1 FROM usuarios WHERE email = ? AND id <> ?", email, uid))
            throw new ApiException(409, "Esse e-mail já é de outra conta.");
        // Trocar o e-mail = trocar como se entra e recupera a conta -> pede a senha atual
        if (!email.equalsIgnoreCase((String) atual.get("email"))) {
            String senha = Json.texto(corpo, "senha_atual", "");
            String hash = (String) atual.get("senha_hash");
            if (senha.isEmpty()) throw new ApiException(400, "Digite sua senha atual para trocar o e-mail.");
            if (hash == null || !bcrypt.matches(senha, hash)) throw new ApiException(400, "A senha atual está errada.");
        }
        if (telefone != null) {
            telefone = telefone.replaceAll("[^0-9+]", "");
            if (telefone.isEmpty()) telefone = null;
            else if (telefone.length() < 10 || telefone.length() > 15) throw new ApiException(400, "Telefone inválido (use DDD + número).");
        }
        if (bio != null && bio.length() > 200) throw new ApiException(400, "A bio pode ter no máximo 200 caracteres.");

        db.executar("UPDATE usuarios SET nome = ?, username = ?, email = ?, telefone = ?, bio = ?, avatar_url = ?, avatar_cor = ? WHERE id = ?",
                nome, username, email, telefone, bio, avatar, cor, uid);
        Map<String, Object> u = db.um("SELECT id, nome, username, email, telefone, bio, avatar_url, avatar_cor FROM usuarios WHERE id = ?", uid).orElseThrow();
        return Json.obj("usuario", u, "token", jwt.gerar(uid, username));
    }

    // =================== FOTO DO PERFIL (enviada do computador/celular) ===================
    // A imagem fica no MySQL (tabela fotos_perfil) e o avatar_url aponta pra cá:
    // /api/usuarios/5/foto?v=1731170000000  (o ?v= muda a cada foto nova, pro navegador não mostrar a velha)
    private static final int FOTO_MAX_BYTES = 600 * 1024;

    /** POST /api/usuarios/me/foto { imagem: "data:image/jpeg;base64,..." } */
    @PostMapping("/me/foto")
    public Map<String, Object> enviarFoto(@RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        String img = Json.texto(corpo, "imagem", "");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^data:(image/(?:jpeg|png|webp));base64,(.+)$", java.util.regex.Pattern.DOTALL).matcher(img);
        if (!m.matches()) throw new ApiException(400, "Envie uma imagem JPG, PNG ou WEBP.");
        byte[] dados;
        try {
            dados = java.util.Base64.getDecoder().decode(m.group(2).trim());
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "Imagem inválida.");
        }
        if (dados.length > FOTO_MAX_BYTES) throw new ApiException(400, "Imagem muito grande (máx. 600 KB).");
        db.executar("REPLACE INTO fotos_perfil (usuario_id, tipo, dados) VALUES (?, ?, ?)", uid, m.group(1), dados);
        String url = "/api/usuarios/" + uid + "/foto?v=" + System.currentTimeMillis();
        db.executar("UPDATE usuarios SET avatar_url = ? WHERE id = ?", url, uid);
        return Json.obj("avatar_url", url);
    }

    /** DELETE /api/usuarios/me/foto -> volta pra letra inicial */
    @DeleteMapping("/me/foto")
    public Map<String, Object> removerFoto(HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        db.executar("DELETE FROM fotos_perfil WHERE usuario_id = ?", uid);
        db.executar("UPDATE usuarios SET avatar_url = NULL WHERE id = ?", uid);
        return Json.obj("avatar_url", null);
    }

    /** GET /api/usuarios/{id}/foto -> a imagem em si (aberta: a tag <img> não manda o login) */
    @GetMapping("/{id:\\d+}/foto")
    public org.springframework.http.ResponseEntity<byte[]> foto(@PathVariable long id) {
        Map<String, Object> f = db.um("SELECT tipo, dados FROM fotos_perfil WHERE usuario_id = ?", id)
                .orElseThrow(() -> new ApiException(404, "Sem foto."));
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Type", (String) f.get("tipo"))
                .header("Cache-Control", "public, max-age=604800")
                .body((byte[]) f.get("dados"));
    }

    /** PUT /api/usuarios/me/senha  { atual, nova } -> trocar a senha */
    @PutMapping("/me/senha")
    public Map<String, Object> trocarSenha(@RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        String atual = Json.texto(corpo, "atual", "");
        String nova = Json.texto(corpo, "nova", "");
        String hash = (String) db.um("SELECT senha_hash FROM usuarios WHERE id = ?", uid).orElseThrow().get("senha_hash");
        if (hash == null || !bcrypt.matches(atual, hash)) throw new ApiException(400, "A senha atual está errada.");
        if (nova.length() < 6) throw new ApiException(400, "A nova senha precisa ter pelo menos 6 caracteres.");
        db.executar("UPDATE usuarios SET senha_hash = ? WHERE id = ?", bcrypt.encode(nova), uid);
        return Json.obj("mensagem", "Senha alterada!");
    }

    /** POST /api/usuarios/{username}/seguir -> seguir / deixar de seguir */
    @PostMapping("/{username}/seguir")
    public Map<String, Object> seguir(@PathVariable String username, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        Long alvo = db.um("SELECT id FROM usuarios WHERE username = ?", username)
                .map(m -> ((Number) m.get("id")).longValue()).orElse(null);
        if (alvo == null || alvo == uid) throw new ApiException(400, "Usuário inválido.");
        boolean tirou = db.executar("DELETE FROM seguidores WHERE seguidor_id = ? AND usuario_id = ?", uid, alvo) > 0;
        if (!tirou) {
            db.executar("INSERT INTO seguidores (seguidor_id, usuario_id) VALUES (?, ?)", uid, alvo);
            notificacoes.seguiu(uid, alvo);
        }
        return Json.obj("sigo", !tirou);
    }
}
