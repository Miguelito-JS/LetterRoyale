package br.com.letterroyale.seguranca;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/** Cria e valida o token JWT (o "crachá" que o front manda em toda requisição). */
@Service
public class JwtService {
    private final SecretKey chave;
    private final long validadeMs;

    public JwtService(@Value("${app.jwt.segredo}") String segredo,
                      @Value("${app.jwt.validade-dias}") int dias) {
        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        this.validadeMs = Duration.ofDays(dias).toMillis();
    }

    public String gerar(long usuarioId, String username) {
        return Jwts.builder()
                .subject(String.valueOf(usuarioId))
                .claim("username", username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + validadeMs))
                .signWith(chave)
                .compact();
    }

    /** Devolve os dados do token ou null se for inválido/vencido. */
    public UsuarioLogado validar(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(chave).build().parseSignedClaims(token).getPayload();
            return new UsuarioLogado(Long.parseLong(c.getSubject()), c.get("username", String.class));
        } catch (Exception e) {
            return null;
        }
    }
}
