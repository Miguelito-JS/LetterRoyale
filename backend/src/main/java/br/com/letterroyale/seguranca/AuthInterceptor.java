package br.com.letterroyale.seguranca;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Roda ANTES de todo controller da /api.
 * Se tiver "Authorization: Bearer TOKEN" válido, guarda o usuário na requisição.
 * Quem decide se a rota exige login é o próprio controller (Auth.exigir / Auth.opcional).
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    public static final String ATRIBUTO = "usuarioLogado";
    private final JwtService jwt;

    public AuthInterceptor(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            UsuarioLogado u = jwt.validar(header.substring(7));
            if (u != null) req.setAttribute(ATRIBUTO, u);
        }
        return true;
    }
}
