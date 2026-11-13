package br.com.letterroyale.controller;

import br.com.letterroyale.erro.ApiException;
import br.com.letterroyale.seguranca.Auth;
import br.com.letterroyale.seguranca.JwtService;
import br.com.letterroyale.util.Db;
import br.com.letterroyale.util.Json;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.Map;

/** Telas: Boas-vindas, Login, Criar conta, Recuperação de senha */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final Db db;
    private final JwtService jwt;
    private final boolean modoDev;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    private final SecureRandom aleatorio = new SecureRandom();

    private final String googleClientId;

    public AuthController(Db db, JwtService jwt, @Value("${app.modo-dev}") boolean modoDev,
                          @Value("${app.google.client-id:${GOOGLE_CLIENT_ID:}}") String googleClientId) {
        this.db = db;
        this.jwt = jwt;
        this.modoDev = modoDev;
        this.googleClientId = googleClientId == null ? "" : googleClientId.trim();
    }

    @GetMapping("/google/config")
    public Map<String, Object> googleConfig() {
        return Json.obj("ativo", !googleClientId.isEmpty(), "client_id", googleClientId.isEmpty() ? null : googleClientId);
    }

    @PostMapping("/google")
    @Transactional
    public Map<String, Object> google(@RequestBody Map<String, Object> corpo) {
        if (googleClientId.isEmpty()) throw new ApiException(503, "Login com Google ainda não foi configurado.");
        String credencial = Json.texto(corpo, "credencial", "");
        if (credencial.isBlank()) throw new ApiException(400, "Credencial do Google faltando.");

        com.fasterxml.jackson.databind.JsonNode info;
        try {
            info = org.springframework.web.client.RestClient.builder().build().get()
                    .uri(java.net.URI.create("https://oauth2.googleapis.com/tokeninfo?id_token="
                            + java.net.URLEncoder.encode(credencial, java.nio.charset.StandardCharsets.UTF_8)))
                    .retrieve().body(com.fasterxml.jackson.databind.JsonNode.class);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            throw new ApiException(401, "O Google não confirmou o login. Tente de novo.");
        } catch (Exception e) {
            throw new ApiException(502, "Não consegui falar com o Google agora.");
        }
        String aud = info.path("aud").asText("");
        String iss = info.path("iss").asText("");
        long exp = info.path("exp").asLong(0);
        boolean emailOk = "true".equals(info.path("email_verified").asText(""));
        if (!googleClientId.equals(aud) || !(iss.equals("accounts.google.com") || iss.equals("https://accounts.google.com"))
                || exp * 1000 < System.currentTimeMillis() || !emailOk) {
            throw new ApiException(401, "Login do Google inválido.");
        }

        String googleId = info.path("sub").asText();
        String email = info.path("email").asText("").toLowerCase();
        Map<String, Object> u = db.um("SELECT * FROM usuarios WHERE google_id = ?", googleId).orElse(null);
        if (u == null) {
            u = db.um("SELECT * FROM usuarios WHERE email = ?", email).orElse(null);
            if (u != null) {
                // Já tinha conta com esse e-mail: liga a conta ao Google
                db.executar("UPDATE usuarios SET google_id = ? WHERE id = ?", googleId, u.get("id"));
            } else {
                // Primeira vez: cria a conta (a senha é aleatória; dá pra criar uma em "Esqueceu a senha?")
                String nome = info.path("name").asText("").trim();
                String username = usernameLivre(email);
                if (nome.isEmpty()) nome = username;
                byte[] b = new byte[24];
                aleatorio.nextBytes(b);
                String foto = info.path("picture").asText(null);
                long id = db.inserir("INSERT INTO usuarios (nome, username, email, senha_hash, google_id, avatar_url) VALUES (?, ?, ?, ?, ?, ?)",
                        nome.length() > 100 ? nome.substring(0, 100) : nome, username, email,
                        bcrypt.encode(java.util.HexFormat.of().formatHex(b)), googleId, foto);
                criarDadosPadrao(id);
            }
            u = db.um("SELECT * FROM usuarios WHERE google_id = ?", googleId).orElseThrow();
        }
        return respostaLogin(u);
    }

    /** Só os campos que podem ir pro front (nunca a senha_hash!) */
    private Map<String, Object> publico(Map<String, Object> u) {
        return Json.obj("id", u.get("id"), "nome", u.get("nome"), "username", u.get("username"),
                "email", u.get("email"), "telefone", u.get("telefone"), "bio", u.get("bio"), "avatar_url", u.get("avatar_url"),
                "avatar_cor", u.get("avatar_cor"));
    }

    private Map<String, Object> respostaLogin(Map<String, Object> u) {
        long id = ((Number) u.get("id")).longValue();
        return Json.obj("token", jwt.gerar(id, (String) u.get("username")), "usuario", publico(u));
    }

    /** Quando cria conta, já cria as listas padrão + Smart Home do usuário */
    private void criarDadosPadrao(long uid) {
        // "Quero assistir" agora é um STATUS do filme (prateleira no Perfil), não uma lista
        String[][] listas = {{"Favoritos", "coracao"}, {"Clássicos", "estrela"}, {"Terror", "fantasma"}, {"Sci-Fi", "foguete"}};
        for (String[] l : listas) {
            db.executar("INSERT INTO listas (usuario_id, nome, icone) VALUES (?, ?, ?)", uid, l[0], l[1]);
        }
        db.executar("""
                INSERT INTO dispositivos (usuario_id, tipo, nome, valor, estado) VALUES
                 (?, 'iluminacao', 'Iluminação', 70, NULL), (?, 'cortinas', 'Cortinas', 0, 'fechadas'),
                 (?, 'temperatura', 'Temperatura', 22, NULL), (?, 'tv_som', 'TV e Som', 60, NULL)
                """, uid, uid, uid, uid);
        // luz %, cortinas, ar °C, volume %, temperatura da luz (0 quente..100 fria), cor, sobe aos poucos?
        db.executar("""
                INSERT INTO cenas (usuario_id, nome, descricao, luz, cortinas, temperatura, volume,
                                   luz_temperatura, luz_cor, luz_gradual) VALUES
                 (?, 'Modo Cinema', 'Luz 10%, cortinas fechadas', 10, 'fechadas', 22, 60,  0, NULL, FALSE),
                 (?, 'Maratona',    'Luz 20%, ar em 21°C',        20, 'fechadas', 21, 50, 10, NULL, FALSE),
                 (?, 'Pausa',       'Luz 60%, som baixo',         60, 'meio',     22, 15, 50, NULL, FALSE),
                 (?, 'Créditos',    'Luz sobe aos poucos',        80, 'abertas',  22, 30, 30, NULL, TRUE)
                """, uid, uid, uid, uid);
    }

    /** Cria um username livre a partir do e-mail: "ana.souza@x.com" -> "ana.souza" (ou "ana.souza2"...) */
    private String usernameLivre(String email) {
        String base = email.split("@")[0].toLowerCase().replaceAll("[^a-z0-9_.]", "");
        if (base.length() < 3) base = (base + "user").substring(0, Math.max(3, base.length()));
        if (base.length() > 26) base = base.substring(0, 26);
        String user = base;
        for (int i = 2; db.existe("SELECT 1 FROM usuarios WHERE username = ?", user); i++) user = base + i;
        return user;
    }

    /**
     * POST /api/auth/cadastro  { email, senha, nome?, username? }
     * Figma novo pede só e-mail + senha. Nome e @usuario saem do e-mail e dá pra mudar em "Editar perfil".
     */
    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional   // ou grava tudo (usuário + listas + casa), ou não grava nada
    public Map<String, Object> cadastro(@RequestBody Map<String, Object> corpo) {
        String email = Json.texto(corpo, "email", "").trim().toLowerCase();
        String senha = Json.texto(corpo, "senha", "");
        if (email.isEmpty() || senha.isEmpty()) throw new ApiException(400, "Preencha e-mail e senha.");
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new ApiException(400, "E-mail inválido.");
        if (senha.length() < 6) throw new ApiException(400, "A senha precisa ter pelo menos 6 caracteres.");
        if (db.existe("SELECT id FROM usuarios WHERE email = ?", email))
            throw new ApiException(409, "Esse e-mail já tem conta. Que tal entrar?");

        String username = Json.texto(corpo, "username", "").trim().replaceFirst("^@", "").toLowerCase();
        if (username.isEmpty()) username = usernameLivre(email);
        else if (!username.matches("[a-z0-9_.]{3,30}")) throw new ApiException(400, "Usuário: 3 a 30 letras, números, _ ou ponto.");
        else if (db.existe("SELECT 1 FROM usuarios WHERE username = ?", username)) throw new ApiException(409, "Esse usuário já existe.");

        String nome = Json.texto(corpo, "nome", "").trim();
        if (nome.isEmpty()) {
            String p = email.split("@")[0].replaceAll("[._\\d]+", " ").trim();
            nome = p.isEmpty() ? username : Character.toUpperCase(p.charAt(0)) + p.substring(1);
        }

        long id = db.inserir("INSERT INTO usuarios (nome, username, email, senha_hash) VALUES (?, ?, ?, ?)",
                nome, username, email, bcrypt.encode(senha));
        criarDadosPadrao(id);
        return respostaLogin(db.um("SELECT * FROM usuarios WHERE id = ?", id).orElseThrow());
    }

    /** POST /api/auth/login  { email, senha } */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> corpo) {
        String email = Json.texto(corpo, "email", "").trim().toLowerCase();
        String senha = Json.texto(corpo, "senha", "");
        Map<String, Object> u = db.um("SELECT * FROM usuarios WHERE email = ?", email).orElse(null);
        String hash = u == null ? null : (String) u.get("senha_hash");
        if (hash == null || !bcrypt.matches(senha, hash)) throw new ApiException(401, "E-mail ou senha incorretos.");
        return respostaLogin(u);
    }

    /** GET /api/auth/me -> quem está logado */
    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        return publico(db.um("SELECT * FROM usuarios WHERE id = ?", uid)
                .orElseThrow(() -> new ApiException(404, "Usuário não encontrado.")));
    }

    /** POST /api/auth/esqueci-senha { email } -> gera código de 6 dígitos (vale 15 min) */
    @PostMapping("/esqueci-senha")
    public Map<String, Object> esqueciSenha(@RequestBody Map<String, Object> corpo) {
        String email = Json.texto(corpo, "email", "").trim().toLowerCase();
        // Resposta igual mesmo se o e-mail não existir (não entrega quem tem conta)
        Map<String, Object> resposta = Json.obj("mensagem", "Se o e-mail existir, enviamos um código.");
        var u = db.um("SELECT id FROM usuarios WHERE email = ?", email);
        if (u.isEmpty()) return resposta;

        String codigo = String.valueOf(100000 + aleatorio.nextInt(900000));
        db.executar("INSERT INTO recuperacao_senha (usuario_id, codigo, expira_em) VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 15 MINUTE))",
                u.get().get("id"), codigo);
        // TODO (equipe): enviar por e-mail com spring-boot-starter-mail. Em modo dev devolvemos o código:
        if (modoDev) resposta.put("codigo_dev", codigo);
        return resposta;
    }

    /** POST /api/auth/verificar-codigo { email, codigo } -> confere o código antes de pedir a senha nova */
    /**
     * GET /api/auth/disponivel?username=ana&email=ana@x.com -> avisa ENQUANTO a pessoa digita
     * se o @ ou o e-mail já são de outra conta. (Logado: a própria conta não conta como "ocupada".)
     */
    @GetMapping("/disponivel")
    public Map<String, Object> disponivel(@RequestParam(required = false) String username,
                                          @RequestParam(required = false) String email,
                                          HttpServletRequest req) {
        var eu = Auth.opcional(req);
        long meuId = eu == null ? -1 : eu.id();
        Map<String, Object> r = new java.util.LinkedHashMap<>();
        if (username != null) {
            String u = username.trim().replaceFirst("^@", "").toLowerCase();
            if (!u.matches("[a-z0-9_.]{3,30}")) {
                r.put("username", Json.obj("livre", false, "motivo", "Use 3 a 30 letras, números, _ ou ponto."));
            } else {
                boolean ocupado = db.existe("SELECT 1 FROM usuarios WHERE username = ? AND id <> ?", u, meuId);
                r.put("username", Json.obj("livre", !ocupado, "motivo", ocupado ? "Esse @ já é de outra pessoa." : null));
            }
        }
        if (email != null) {
            String e = email.trim().toLowerCase();
            if (!e.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
                r.put("email", Json.obj("livre", false, "motivo", "E-mail inválido."));
            } else {
                boolean ocupado = db.existe("SELECT 1 FROM usuarios WHERE LOWER(email) = ? AND id <> ?", e, meuId);
                r.put("email", Json.obj("livre", !ocupado, "motivo", ocupado ? "Esse e-mail já tem conta. Que tal entrar?" : null));
            }
        }
        return r;
    }

    @PostMapping("/verificar-codigo")
    public Map<String, Object> verificarCodigo(@RequestBody Map<String, Object> corpo) {
        boolean ok = db.existe("""
                SELECT 1 FROM recuperacao_senha r JOIN usuarios u ON u.id = r.usuario_id
                 WHERE u.email = ? AND r.codigo = ? AND r.usado = FALSE AND r.expira_em > NOW()
                """, Json.texto(corpo, "email", "").trim().toLowerCase(), Json.texto(corpo, "codigo", "").trim());
        if (!ok) throw new ApiException(400, "Código inválido ou expirado.");
        return Json.obj("valido", true);
    }

    /** POST /api/auth/redefinir-senha { email, codigo, nova_senha } */
    @PostMapping("/redefinir-senha")
    public Map<String, Object> redefinirSenha(@RequestBody Map<String, Object> corpo) {
        String email = Json.texto(corpo, "email", "").trim().toLowerCase();
        String codigo = Json.texto(corpo, "codigo", "");
        String nova = Json.texto(corpo, "nova_senha", "");
        if (nova.length() < 6) throw new ApiException(400, "Senha muito curta.");

        Map<String, Object> r = db.um("""
                SELECT r.id, r.usuario_id FROM recuperacao_senha r
                  JOIN usuarios u ON u.id = r.usuario_id
                 WHERE u.email = ? AND r.codigo = ? AND r.usado = FALSE AND r.expira_em > NOW()
                 ORDER BY r.id DESC LIMIT 1
                """, email, codigo).orElseThrow(() -> new ApiException(400, "Código inválido ou expirado."));

        db.executar("UPDATE usuarios SET senha_hash = ? WHERE id = ?", bcrypt.encode(nova), r.get("usuario_id"));
        db.executar("UPDATE recuperacao_senha SET usado = TRUE WHERE id = ?", r.get("id"));
        return Json.obj("mensagem", "Senha alterada! Já pode entrar.");
    }

    // TODO (equipe): "Entrar com Google" -> Google Identity Services no front +
    // validar o id_token aqui (biblioteca google-api-client) e salvar em usuarios.google_id.
}
