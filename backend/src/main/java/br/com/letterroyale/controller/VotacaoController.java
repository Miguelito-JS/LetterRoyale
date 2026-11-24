package br.com.letterroyale.controller;

import br.com.letterroyale.casa.controle.VotacaoService;
import br.com.letterroyale.util.Json;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Votação pelo celular dos amigos (rota ABERTA: quem escaneou o QR não precisa de conta).
 *   GET  /api/votacao/{codigo}?votante=abc     -> filmes + votos
 *   POST /api/votacao/{codigo}/votar {votante, tmdb_id}
 * "votante" = um id aleatório que o celular guarda (1 voto por celular).
 */
@RestController
@RequestMapping("/api/votacao")
public class VotacaoController {
    private final VotacaoService votacoes;

    public VotacaoController(VotacaoService votacoes) {
        this.votacoes = votacoes;
    }

    @GetMapping("/{codigo}")
    public Map<String, Object> ver(@PathVariable String codigo, @RequestParam(required = false) String votante) {
        return votacoes.obter(codigo).paraMapa(votante);
    }

    @PostMapping("/{codigo}/votar")
    public Map<String, Object> votar(@PathVariable String codigo, @RequestBody Map<String, Object> corpo) {
        Long filme = Json.numero(corpo, "tmdb_id");
        String votante = Json.texto(corpo, "votante", null);
        if (filme == null) throw new br.com.letterroyale.erro.ApiException(400, "Escolha um filme.");
        votacoes.votar(codigo, votante, filme);
        return votacoes.obter(codigo).paraMapa(votante);
    }
}
