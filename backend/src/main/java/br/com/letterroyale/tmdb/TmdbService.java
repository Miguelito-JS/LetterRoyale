package br.com.letterroyale.tmdb;

import br.com.letterroyale.erro.ApiException;
import br.com.letterroyale.util.Db;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fala com a API do TMDB (The Movie Database) — é daqui que vêm TODOS os filmes.
 * O front NUNCA chama o TMDB direto: chama o nosso back, que chama o TMDB.
 * Assim o token fica escondido no servidor.
 */
@Service
public class TmdbService {
    private static final String BASE = "https://api.themoviedb.org/3";
    private static final String IMG = "https://image.tmdb.org/t/p/";
    private static final long TEMPO_CACHE_MS = 5 * 60 * 1000;   // 5 minutos

    private final RestClient http;
    private final Db db;
    private final String idioma;
    private final String regiao;

    // Cache em memória pra não estourar o limite de chamadas da API
    private final Map<String, Guardado> memoria = new ConcurrentHashMap<>();
    private record Guardado(long em, JsonNode dados) { }

    public TmdbService(Db db,
                       @Value("${app.tmdb.token}") String token,
                       @Value("${app.tmdb.idioma}") String idioma,
                       @Value("${app.tmdb.regiao}") String regiao) {
        this.db = db;
        this.idioma = idioma;
        this.regiao = regiao;
        this.http = RestClient.builder()
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("accept", "application/json")
                .build();
    }

    // -----------------------------------------------------------------
    // PARALELO: várias chamadas ao TMDB ao mesmo tempo (virtual threads do Java 21+).
    // Antes a busca fazia até 26 chamadas uma atrás da outra (~4 s); juntas, levam o tempo da mais lenta.
    // -----------------------------------------------------------------
    private static final java.util.concurrent.ExecutorService PARALELO =
            java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

    /** Começa uma tarefa em paralelo. Pegue o resultado com resultado(...). */
    public <T> java.util.concurrent.CompletableFuture<T> emParalelo(java.util.function.Supplier<T> tarefa) {
        return java.util.concurrent.CompletableFuture.supplyAsync(tarefa, PARALELO);
    }

    /** Espera a tarefa. Se deu erro (TMDB fora, 404...), devolve um JSON vazio em vez de derrubar a página. */
    public static JsonNode resultado(java.util.concurrent.CompletableFuture<JsonNode> f) {
        if (f == null) return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        try {
            return f.join();
        } catch (Exception e) {
            return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        }
    }

    public String getRegiao() {
        return regiao;
    }

    /**
     * Chama o TMDB. Parâmetros em pares: tmdb("/search/movie", "query", "duna", "page", 1)
     * Parâmetro com valor null ou vazio é ignorado.
     */
    public JsonNode tmdb(String caminho, Object... params) {
        UriComponentsBuilder url = UriComponentsBuilder.fromHttpUrl(BASE + caminho)
                .queryParam("language", idioma);
        for (int i = 0; i + 1 < params.length; i += 2) {
            Object v = params[i + 1];
            if (v != null && !String.valueOf(v).isBlank()) url.queryParam(String.valueOf(params[i]), v);
        }
        String chave = url.encode().toUriString();

        Guardado g = memoria.get(chave);
        if (g != null && System.currentTimeMillis() - g.em() < TEMPO_CACHE_MS) return g.dados();

        try {
            JsonNode dados = http.get().uri(java.net.URI.create(chave)).retrieve().body(JsonNode.class);
            memoria.put(chave, new Guardado(System.currentTimeMillis(), dados));
            return dados;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ApiException(404, "Filme não encontrado.");
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new ApiException(502, "Token do TMDB inválido. Confira a variável TMDB_TOKEN.");
        } catch (Exception e) {
            throw new ApiException(502, "Não consegui falar com o TMDB agora.");
        }
    }

    public static String img(String path, String tamanho) {
        return (path == null || path.isBlank() || "null".equals(path)) ? null : IMG + tamanho + path;
    }

    private static String texto(JsonNode n, String campo) {
        JsonNode v = n.get(campo);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    private static Integer ano(String data) {
        return (data == null || data.length() < 4) ? null : Integer.valueOf(data.substring(0, 4));
    }

    private static Double nota(JsonNode f) {
        double v = f.path("vote_average").asDouble(0);
        return v == 0 ? null : BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Bloqueia filme que ainda não estreou (não dá pra "assistir", marcar como assistido nem avaliar).
     * Usa a data de lançamento do TMDB.
     */
    public void exigirLancado(long tmdbId) {
        boolean serie = ehSerie(tmdbId);
        String data = serie ? texto(tmdb("/tv/" + (-tmdbId)), "first_air_date") : texto(tmdb("/movie/" + tmdbId), "release_date");
        if (data == null || data.isBlank()) return;
        LocalDate estreia = LocalDate.parse(data);
        if (estreia.isAfter(LocalDate.now())) {
            throw new ApiException(400, (serie ? "Essa série ainda não estreou. Estreia em " : "Esse filme ainda não estreou. Estreia em ")
                    + estreia.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".");
        }
    }

    /** Formato "enxuto" de filme que o front usa nos cards */
    public Map<String, Object> resumo(JsonNode f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.path("id").asLong());
        m.put("tipo", "filme");
        m.put("documentario", ehDocumentario(f));
        m.put("titulo", texto(f, "title"));
        m.put("ano", ano(texto(f, "release_date")));
        m.put("lancamento", texto(f, "release_date"));   // "2026-12-17" (pra saber se já estreou)
        m.put("nota", nota(f));
        m.put("poster", img(texto(f, "poster_path"), "w500"));
        m.put("backdrop", img(texto(f, "backdrop_path"), "w1280"));
        m.put("sinopse", texto(f, "overview"));
        return m;
    }

    // -----------------------------------------------------------------
    // SÉRIES: usam as MESMAS tabelas dos filmes (status, favoritos, reviews, listas).
    // Pra um id de série nunca bater com o id de um filme, a série é guardada com o
    // id NEGATIVO: a série 1399 do TMDB vira -1399 aqui no site e no banco.
    // -----------------------------------------------------------------
    public static boolean ehSerie(long id) {
        return id < 0;
    }

    /** Documentário = gênero 99 do TMDB (vale pra filme e série). Nas listas vem "genre_ids", nos detalhes "genres". */
    public static boolean ehDocumentario(JsonNode f) {
        for (JsonNode g : f.path("genre_ids")) if (g.asInt() == 99) return true;
        for (JsonNode g : f.path("genres")) if (g.path("id").asInt() == 99) return true;
        return false;
    }

    /** Um episódio (serieId é o id NEGATIVO do site). Bloqueia episódio que ainda não saiu. */
    public JsonNode episodioLancado(long serieId, int temporada, int episodio) {
        JsonNode e = tmdb("/tv/" + (-serieId) + "/season/" + temporada + "/episode/" + episodio);
        String data = texto(e, "air_date");
        if (data != null && !data.isBlank() && LocalDate.parse(data).isAfter(LocalDate.now())) {
            throw new ApiException(400, "Esse episódio ainda não saiu. Sai em "
                    + LocalDate.parse(data).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".");
        }
        return e;
    }

    /** Formato "enxuto" de série (mesmas chaves do filme, pra usar os mesmos cards) */
    public Map<String, Object> resumoSerie(JsonNode s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", -s.path("id").asLong());
        m.put("tipo", "serie");
        m.put("documentario", ehDocumentario(s));
        m.put("titulo", texto(s, "name"));
        m.put("ano", ano(texto(s, "first_air_date")));
        m.put("lancamento", texto(s, "first_air_date"));
        m.put("nota", nota(s));
        m.put("poster", img(texto(s, "poster_path"), "w500"));
        m.put("backdrop", img(texto(s, "backdrop_path"), "w1280"));
        m.put("sinopse", texto(s, "overview"));
        return m;
    }

    /** Duração de 1 episódio (o TMDB às vezes não manda; aí usa 0) */
    public static int duracaoEpisodio(JsonNode s) {
        JsonNode r = s.path("episode_run_time");
        if (r.isArray() && !r.isEmpty()) return r.get(0).asInt(0);
        return s.path("last_episode_to_air").path("runtime").asInt(0);
    }

    public void salvarSerieNoCache(JsonNode s) {
        if (s == null || !s.has("id")) return;
        db.executar("""
                INSERT INTO filmes_cache (tmdb_id, titulo, ano, poster_path, backdrop_path, nota_tmdb, duracao_min, documentario)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE titulo = VALUES(titulo), poster_path = VALUES(poster_path),
                  backdrop_path = VALUES(backdrop_path), nota_tmdb = VALUES(nota_tmdb), duracao_min = VALUES(duracao_min),
                  documentario = VALUES(documentario)
                """,
                -s.path("id").asLong(), texto(s, "name"), ano(texto(s, "first_air_date")),
                texto(s, "poster_path"), texto(s, "backdrop_path"), nota(s), duracaoEpisodio(s), ehDocumentario(s));
    }

    /** Guarda título/pôster no MySQL (tabela filmes_cache) -> usado em listas, perfil, reviews */
    public void salvarNoCache(JsonNode f) {
        if (f == null || !f.has("id")) return;
        db.executar("""
                INSERT INTO filmes_cache (tmdb_id, titulo, ano, poster_path, backdrop_path, nota_tmdb, duracao_min, documentario)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE titulo = VALUES(titulo), poster_path = VALUES(poster_path),
                  backdrop_path = VALUES(backdrop_path), nota_tmdb = VALUES(nota_tmdb),
                  duracao_min = COALESCE(VALUES(duracao_min), duracao_min), documentario = VALUES(documentario)
                """,
                f.path("id").asLong(), texto(f, "title"), ano(texto(f, "release_date")),
                texto(f, "poster_path"), texto(f, "backdrop_path"), nota(f),
                f.path("runtime").isNumber() ? f.path("runtime").asInt() : null, ehDocumentario(f));
    }

    /** Se o filme ainda não está no cache, busca no TMDB e salva */
    public void garantirNoCache(long tmdbId) {
        // duracao_min só vem nos detalhes do filme -> se faltar, busca de novo
        if (db.existe("SELECT tmdb_id FROM filmes_cache WHERE tmdb_id = ? AND duracao_min IS NOT NULL", tmdbId)) return;
        if (ehSerie(tmdbId)) salvarSerieNoCache(tmdb("/tv/" + (-tmdbId)));
        else salvarNoCache(tmdb("/movie/" + tmdbId));
    }

    /** Linha da tabela filmes_cache -> mesmo formato do resumo() */
    public static Map<String, Object> doCache(Map<String, Object> l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.get("tmdb_id"));
        m.put("tipo", l.get("tmdb_id") instanceof Number n && n.longValue() < 0 ? "serie" : "filme");
        Object doc = l.get("documentario");
        m.put("documentario", doc instanceof Boolean b ? b : doc instanceof Number n2 && n2.intValue() == 1);
        m.put("titulo", l.get("titulo"));
        m.put("ano", l.get("ano"));
        m.put("nota", l.get("nota_tmdb"));
        m.put("poster", img((String) l.get("poster_path"), "w500"));
        m.put("backdrop", img((String) l.get("backdrop_path"), "w1280"));
        return m;
    }
}
