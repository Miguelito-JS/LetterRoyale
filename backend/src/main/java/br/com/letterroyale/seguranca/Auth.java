package br.com.letterroyale.seguranca;

import br.com.letterroyale.erro.ApiException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Atalhos usados nos controllers:
 *   long uid = Auth.exigir(req).id();        // rota protegida (401 se não logado)
 *   UsuarioLogado u = Auth.opcional(req);     // rota aberta (u pode ser null)
 */
public final class Auth {
    private Auth() {
    }

    public static UsuarioLogado exigir(HttpServletRequest req) {
        UsuarioLogado u = opcional(req);
        if (u == null) throw new ApiException(401, "Faça login para continuar.");
        return u;
    }

    public static UsuarioLogado opcional(HttpServletRequest req) {
        return (UsuarioLogado) req.getAttribute(AuthInterceptor.ATRIBUTO);
    }
}
