package br.com.letterroyale.casa.controle;

import br.com.letterroyale.casa.CasaService;
import br.com.letterroyale.casa.eventos.EventoCasa;
import br.com.letterroyale.erro.ApiException;
import br.com.letterroyale.tmdb.TmdbService;
import br.com.letterroyale.util.Db;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda as votações abertas (em memória: uma noite de filme não precisa ir pro banco).
 * Cada anfitrião tem no máximo 1 votação; ela some sozinha depois de 6 horas.
 */
@Service
public class VotacaoService {
    static final long VALIDADE_MS = 6 * 60 * 60 * 1000L;
    private static final String LETRAS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";   // sem 0/O e 1/I pra não confundir

    private final Db db;
    private final TmdbService tmdb;
    private final CasaService casa;
    private final SecureRandom sorte = new SecureRandom();
    private final Map<String, Votacao> porCodigo = new ConcurrentHashMap<>();

    public VotacaoService(Db db, TmdbService tmdb, CasaService casa) {
        this.db = db;
        this.tmdb = tmdb;
        this.casa = casa;
    }

    public Votacao criar(long uid, String anfitriao, List<Long> filmes) {
        limparVencidas();
        porCodigo.values().removeIf(v -> v.anfitriaoId() == uid);   // 1 votação por anfitrião
        List<Votacao.Opcao> opcoes = new ArrayList<>();
        for (Long id : filmes == null ? List.<Long>of() : filmes) {
            tmdb.garantirNoCache(id);
            Map<String, Object> f = db.um("SELECT * FROM filmes_cache WHERE tmdb_id = ?", id)
                    .map(TmdbService::doCache).orElseThrow(() -> new ApiException(404, "Filme " + id + " não encontrado."));
            opcoes.add(new Votacao.Opcao(id, (String) f.get("titulo"), (String) f.get("poster")));
        }
        Votacao v;
        try {
            v = new Votacao(novoCodigo(), uid, anfitriao, opcoes, System.currentTimeMillis());
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, e.getMessage());
        }
        porCodigo.put(v.codigo(), v);
        casa.publicar(uid, EventoCasa.Tipo.VOTACAO, "Votação aberta: " + opcoes.size() + " filmes, código " + v.codigo());
        return v;
    }

    public Votacao obter(String codigo) {
        limparVencidas();
        Votacao v = codigo == null ? null : porCodigo.get(codigo.trim().toUpperCase());
        if (v == null) throw new ApiException(404, "Votação não encontrada ou já expirou.");
        return v;
    }

    /** Votação do anfitrião (ou null). */
    public Votacao doAnfitriao(long uid) {
        limparVencidas();
        return porCodigo.values().stream().filter(v -> v.anfitriaoId() == uid).findFirst().orElse(null);
    }

    public void votar(String codigo, String votante, long tmdbId) {
        try {
            obter(codigo).votar(votante, tmdbId);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ApiException(400, e.getMessage());
        }
    }

    public Votacao.Opcao encerrar(long uid) {
        Votacao v = doAnfitriao(uid);
        if (v == null) throw new ApiException(404, "Você não tem votação aberta.");
        Votacao.Opcao vencedor = v.encerrar();
        casa.publicar(uid, EventoCasa.Tipo.VOTACAO, "Votação encerrada: venceu " + vencedor.titulo()
                + " (" + v.contagem().get(vencedor.tmdbId()) + " votos)");
        return vencedor;
    }

    /** Botão "Sair sem escolher": apaga a votação do anfitrião sem eleger ninguém. */
    public void cancelar(long uid) {
        Votacao v = doAnfitriao(uid);
        if (v == null) return;
        porCodigo.remove(v.codigo());
        casa.publicar(uid, EventoCasa.Tipo.VOTACAO, "Votação cancelada");
    }

    private String novoCodigo() {
        String c;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5; i++) sb.append(LETRAS.charAt(sorte.nextInt(LETRAS.length())));
            c = sb.toString();
        } while (porCodigo.containsKey(c));
        return c;
    }

    private void limparVencidas() {
        long agora = System.currentTimeMillis();
        porCodigo.values().removeIf(v -> agora - v.criadaEm() > VALIDADE_MS);
    }
}
