package br.com.letterroyale.controller;

import br.com.letterroyale.erro.ApiException;
import br.com.letterroyale.seguranca.Auth;
import br.com.letterroyale.util.Db;
import br.com.letterroyale.util.Json;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Tela Conexões (Netflix, Prime Video, HBO Max...)
 * Obs.: Netflix/Prime etc. NÃO têm API pública de login. Então a "conexão" é o usuário dizer
 * qual conta/assinatura tem. Serve pra: 1) mostrar no perfil 2) usar como status
 * 3) (tarefa) filtrar o Catálogo e destacar no "Onde assistir".
 */
@RestController
@RequestMapping("/api/conexoes")
public class ConexoesController {
    private final Db db;

    public ConexoesController(Db db) {
        this.db = db;
    }

    /** GET /api/conexoes/plataformas -> grade "Adicionar uma nova conexão" */
    @GetMapping("/plataformas")
    public List<Map<String, Object>> plataformas(HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        List<Map<String, Object>> l = db.lista("""
                SELECT p.*, (c.id IS NOT NULL) AS conectada FROM plataformas p
                  LEFT JOIN conexoes c ON c.plataforma_id = p.id AND c.usuario_id = ?
                 ORDER BY p.id
                """, uid);
        l.forEach(p -> p.put("conectada", Json.verdade(p.get("conectada"))));
        return l;
    }

    /** GET /api/conexoes -> lista "N conexões" */
    @GetMapping
    public List<Map<String, Object>> minhas(HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        return db.lista("""
                SELECT c.*, p.nome AS plataforma, p.slug, p.cor, p.tmdb_provider_id
                  FROM conexoes c JOIN plataformas p ON p.id = c.plataforma_id
                 WHERE c.usuario_id = ? ORDER BY c.criado_em
                """, uid);
    }

    /** POST /api/conexoes { plataforma_id, nome_conta } */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> conectar(@RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        Long platId = Json.numero(corpo, "plataforma_id");
        Map<String, Object> p = db.um("SELECT * FROM plataformas WHERE id = ?", platId == null ? 0 : platId)
                .orElseThrow(() -> new ApiException(400, "Plataforma inválida."));
        if (db.existe("SELECT 1 FROM conexoes WHERE usuario_id = ? AND plataforma_id = ?", uid, platId))
            throw new ApiException(409, "Você já conectou " + p.get("nome") + ".");

        String nomeConta = Json.texto(corpo, "nome_conta", p.get("nome") + " user").trim();
        if (nomeConta.length() > 60) nomeConta = nomeConta.substring(0, 60);
        long id = db.inserir("INSERT INTO conexoes (usuario_id, plataforma_id, nome_conta) VALUES (?, ?, ?)", uid, platId, nomeConta);
        return Json.obj("id", id);
    }

    /** PATCH /api/conexoes/{id} { exibir_no_perfil?, exibir_status? } -> os 2 botões liga/desliga */
    @PatchMapping("/{id}")
    public Map<String, Object> atualizar(@PathVariable long id, @RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        Map<String, Object> c = db.um("SELECT * FROM conexoes WHERE id = ? AND usuario_id = ?", id, uid)
                .orElseThrow(() -> new ApiException(404, "Conexão não encontrada."));
        Boolean perfil = Json.bool(corpo, "exibir_no_perfil");
        Boolean status = Json.bool(corpo, "exibir_status");
        boolean novoPerfil = perfil != null ? perfil : Json.verdade(c.get("exibir_no_perfil"));
        boolean novoStatus = status != null ? status : Json.verdade(c.get("exibir_status"));

        // Só 1 plataforma pode ser "status" por vez
        if (novoStatus) db.executar("UPDATE conexoes SET exibir_status = FALSE WHERE usuario_id = ?", uid);
        db.executar("UPDATE conexoes SET exibir_no_perfil = ?, exibir_status = ? WHERE id = ?", novoPerfil, novoStatus, id);
        return Json.obj("ok", true);
    }

    /** DELETE /api/conexoes/{id} -> o "X" */
    @DeleteMapping("/{id}")
    public Map<String, Object> remover(@PathVariable long id, HttpServletRequest req) {
        if (db.executar("DELETE FROM conexoes WHERE id = ? AND usuario_id = ?", id, Auth.exigir(req).id()) == 0)
            throw new ApiException(404, "Conexão não encontrada.");
        return Json.obj("ok", true);
    }
}
