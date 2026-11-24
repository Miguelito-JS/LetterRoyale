package br.com.letterroyale.controller;

import br.com.letterroyale.casa.CinemaService;
import br.com.letterroyale.casa.aparelhos.AparelhosService;
import br.com.letterroyale.casa.controle.RedeLocal;
import br.com.letterroyale.config.HttpsLocal;
import br.com.letterroyale.casa.controle.Votacao;
import br.com.letterroyale.casa.controle.VotacaoService;
import br.com.letterroyale.seguranca.Auth;
import br.com.letterroyale.seguranca.JwtService;
import br.com.letterroyale.seguranca.UsuarioLogado;
import br.com.letterroyale.util.Json;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * [+] Casa inteligente de verdade:
 *   PUT  /api/casa/dispositivos/{id}/aparelho  {device_id}  -> cortina/ar REAL (vazio = simulado)
 *   POST /api/casa/dispositivos/{id}/testar                -> reenvia o estado pro aparelho
 *   POST /api/casa/cinema/pausar | /cinema/retomar         -> intervalo sem perder o filme
 *   POST /api/casa/atmosfera  {chave}                      -> "modo terror" na hora
 *   GET  /api/casa/controle/convite                        -> QR code: celular vira controle
 *   POST /api/casa/votacao {filmes:[ids]} | GET | POST /votacao/encerrar | DELETE /votacao -> votação
 */
@RestController
@RequestMapping("/api/casa")
public class CasaInteligenteController {
    private final AparelhosService aparelhos;
    private final CinemaService cinema;
    private final VotacaoService votacoes;
    private final JwtService jwt;
    private final int porta;

    public CasaInteligenteController(AparelhosService aparelhos, CinemaService cinema, VotacaoService votacoes, JwtService jwt,
                                     @Value("${server.port:8080}") int porta) {
        this.aparelhos = aparelhos;
        this.cinema = cinema;
        this.votacoes = votacoes;
        this.jwt = jwt;
        this.porta = porta;
    }

    private static Map<String, Object> ok(String aviso) {
        Map<String, Object> r = Json.obj("ok", true);
        if (aviso != null) r.put("aviso", aviso);
        return r;
    }

    // ---------------- Aparelhos reais (cortina, ar) ----------------

    @PutMapping("/dispositivos/{id}/aparelho")
    public Map<String, Object> configurar(@PathVariable long id, @RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        aparelhos.configurar(Auth.exigir(req).id(), id, Json.texto(corpo, "device_id", null));
        return ok(null);
    }

    @PostMapping("/dispositivos/{id}/testar")
    public Map<String, Object> testar(@PathVariable long id, HttpServletRequest req) {
        return Json.obj("mensagem", aparelhos.testar(Auth.exigir(req).id(), id));
    }

    // ---------------- Intervalo e atmosfera na mão ----------------

    @PostMapping("/cinema/pausar")
    public Map<String, Object> pausar(HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        cinema.pausar(uid);
        return cinema.estado(uid);
    }

    @PostMapping("/cinema/retomar")
    public Map<String, Object> retomar(HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        cinema.retomar(uid);
        return cinema.estado(uid);
    }

    @PostMapping("/atmosfera")
    public Map<String, Object> atmosfera(@RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        return ok(cinema.forcarAtmosfera(Auth.exigir(req).id(), Json.texto(corpo, "chave", "")));
    }

    // ---------------- QR code: celular vira controle remoto ----------------

    /** Endereço do site NA REDE DE CASA + token de 12 h, pra virar QR code. */
    @GetMapping("/controle/convite")
    public Map<String, Object> convite(HttpServletRequest req) {
        UsuarioLogado u = Auth.exigir(req);
        String base = enderecoNaRede(req, true);   // controle = https (microfone do celular)
        String token = jwt.gerar(u.id(), u.username(), Duration.ofHours(12));
        Map<String, Object> r = Json.obj(
                "base", base,
                "url_controle", base + "/#/controle?t=" + token,
                "na_rede", !base.contains("localhost"),
                "https", base.startsWith("https://"),
                // O certificado é baixado pelo http (ainda não confia no https antes de instalar)
                "url_certificado", enderecoNaRede(req, false) + "/certificado/" + br.com.letterroyale.casa.controle.CertificadoLocal.ARQUIVO_CA,
                "ips", RedeLocal.ips());
        if (base.contains("localhost")) r.put("aviso", "Não achei o IP do computador no Wi-Fi. Conecte o PC na mesma rede do celular.");
        return r;
    }

    // ---------------- Sessão compartilhada (votação) ----------------

    @PostMapping("/votacao")
    public Map<String, Object> criarVotacao(@RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        UsuarioLogado u = Auth.exigir(req);
        List<Long> filmes = new ArrayList<>();
        if (corpo.get("filmes") instanceof List<?> lista) {
            for (Object o : lista) if (o instanceof Number n) filmes.add(n.longValue());
        }
        Votacao v = votacoes.criar(u.id(), u.username(), filmes);
        return comLink(v, req);
    }

    @GetMapping("/votacao")
    public Map<String, Object> minhaVotacao(HttpServletRequest req) {
        Votacao v = votacoes.doAnfitriao(Auth.exigir(req).id());
        return v == null ? Json.obj("ativa", false) : comLink(v, req);
    }

    @PostMapping("/votacao/encerrar")
    public Map<String, Object> encerrar(HttpServletRequest req) {
        Votacao.Opcao vencedor = votacoes.encerrar(Auth.exigir(req).id());
        return Json.obj("vencedor", Json.obj("id", vencedor.tmdbId(), "titulo", vencedor.titulo(), "poster", vencedor.poster()));
    }

    @DeleteMapping("/votacao")
    public Map<String, Object> cancelarVotacao(HttpServletRequest req) {
        votacoes.cancelar(Auth.exigir(req).id());
        return Json.obj("ok", true);
    }

    private Map<String, Object> comLink(Votacao v, HttpServletRequest req) {
        Map<String, Object> r = v.paraMapa(null);
        r.put("ativa", true);
        r.put("url_votar", enderecoNaRede(req, false) + "/#/votar/" + v.codigo());   // amigos: http, sem instalar nada
        return r;
    }

    /**
     * Endereço do PC na rede de casa. seguro = true -> https://192.168.x.x:8443 (se o HTTPS subiu);
     * false -> http://192.168.x.x:8080. Se o navegador já está usando um IP, usa o mesmo.
     */
    private String enderecoNaRede(HttpServletRequest req, boolean seguro) {
        String host = req.getServerName();
        if (host == null || host.equals("localhost") || host.startsWith("127.")) {
            List<String> ips = RedeLocal.ips();
            host = ips.isEmpty() ? "localhost" : ips.get(0);
        }
        if (seguro && HttpsLocal.ativo()) return "https://" + host + ":" + HttpsLocal.porta();
        return "http://" + host + ":" + porta;
    }
}
