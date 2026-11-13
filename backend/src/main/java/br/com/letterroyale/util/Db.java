package br.com.letterroyale.util;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Atalhos em cima do JdbcTemplate (a ferramenta do Spring pra rodar SQL).
 * SEMPRE use "?" nos valores -> evita SQL Injection.
 *
 *   List<Map<String,Object>> linhas = db.lista("SELECT * FROM listas WHERE usuario_id = ?", uid);
 *   Optional<Map<String,Object>> u  = db.um("SELECT * FROM usuarios WHERE email = ?", email);
 *   int alteradas                   = db.executar("DELETE FROM favoritos WHERE ...", a, b);
 *   long novoId                     = db.inserir("INSERT INTO listas (...) VALUES (?, ?)", a, b);
 *
 * Cada linha vem como Map: nome da coluna -> valor. O Spring transforma Map em JSON sozinho.
 */
@Component
public class Db {
    private final JdbcTemplate jdbc;

    public Db(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> lista(String sql, Object... params) {
        return jdbc.queryForList(sql, params);
    }

    public Optional<Map<String, Object>> um(String sql, Object... params) {
        List<Map<String, Object>> l = jdbc.queryForList(sql, params);
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    public boolean existe(String sql, Object... params) {
        return !jdbc.queryForList(sql, params).isEmpty();
    }

    public long numero(String sql, Object... params) {
        Number n = jdbc.queryForObject(sql, Number.class, params);
        return n == null ? 0 : n.longValue();
    }

    public int executar(String sql, Object... params) {
        return jdbc.update(sql, params);
    }

    /** INSERT que devolve o id gerado (AUTO_INCREMENT) */
    public long inserir(String sql, Object... params) {
        KeyHolder chave = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            return ps;
        }, chave);
        Number id = chave.getKey();
        return id == null ? 0 : id.longValue();
    }
}
