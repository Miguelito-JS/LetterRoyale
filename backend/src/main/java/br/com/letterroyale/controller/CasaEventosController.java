package br.com.letterroyale.controller;

import br.com.letterroyale.casa.atmosfera.Atmosfera;
import br.com.letterroyale.casa.atmosfera.AtmosferaPorGenero;
import br.com.letterroyale.casa.eventos.DiarioDaCasa;
import br.com.letterroyale.seguranca.Auth;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Sala ao vivo (gêmeo digital):
 *   GET /api/casa/eventos?limite=15   -> linha do tempo da casa (mais novo primeiro)
 *   GET /api/casa/atmosferas          -> todas as atmosferas (terror, ação...)
 */
@RestController
@RequestMapping("/api/casa")
public class CasaEventosController {
    private final DiarioDaCasa diario;

    public CasaEventosController(DiarioDaCasa diario) {
        this.diario = diario;
    }

    @GetMapping("/eventos")
    public List<Map<String, Object>> eventos(@RequestParam(defaultValue = "15") int limite, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        return diario.ultimos(uid, Math.max(1, Math.min(limite, 30)));
    }

    @GetMapping("/atmosferas")
    public List<Map<String, Object>> atmosferas(HttpServletRequest req) {
        Auth.exigir(req);
        return AtmosferaPorGenero.todas().stream().map(Atmosfera::paraMapa).toList();
    }
}
