package br.com.letterroyale.controller;

import br.com.letterroyale.casa.PaletaService;
import br.com.letterroyale.erro.ApiException;
import br.com.letterroyale.seguranca.Auth;
import br.com.letterroyale.seguranca.UsuarioLogado;
import br.com.letterroyale.tmdb.TmdbService;
import br.com.letterroyale.util.Db;
import br.com.letterroyale.util.Json;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Telas: Início (destaque/recomendados), Catálogo, Detalhes do filme, busca do topo */
@RestController
@RequestMapping("/api/filmes")
public class FilmesController {
    private final TmdbService tmdb;
    private final Db db;
    private final PaletaService paletas;

    public FilmesController(TmdbService tmdb, Db db, PaletaService paletas) {
        this.tmdb = tmdb;
        this.db = db;
        this.paletas = paletas;
    }

    /** GET /api/filmes/{id}/paleta -> cores do pôster (as que a lâmpada usa no Modo Cinema) */
    @GetMapping("/{id}/paleta")
    public List<String> paleta(@PathVariable long id) {
        return paletas.paleta(id);
    }

    private List<Map<String, Object>> resumos(JsonNode lista, int max) {
        List<Map<String, Object>> r = new ArrayList<>();
        if (lista == null) return r;
        for (JsonNode f : lista) {
            if (r.size() >= max) break;
            r.add(tmdb.resumo(f));
        }
        return r;
    }

    private List<Map<String, Object>> resumosSeries(JsonNode lista, int max) {
        List<Map<String, Object>> r = new ArrayList<>();
        if (lista == null) return r;
        for (JsonNode s : lista) {
            if (r.size() >= max) break;
            r.add(tmdb.resumoSerie(s));
        }
        return r;
    }

    private static boolean ehSerie(String tipo) {
        return "serie".equals(tipo);
    }

    /** GET /api/filmes/generos?tipo=serie -> filtro "Gênero" (séries têm gêneros próprios no TMDB) */
    @GetMapping("/generos")
    public JsonNode generos(@RequestParam(required = false) String tipo) {
        return tmdb.tmdb(ehSerie(tipo) ? "/genre/tv/list" : "/genre/movie/list").path("genres");
    }

    /** GET /api/filmes/streamings -> filtro "Streaming" (serviços disponíveis no Brasil) */
    @GetMapping("/streamings")
    public List<Map<String, Object>> streamings() {
        String reg = tmdb.getRegiao();
        List<JsonNode> todos = new ArrayList<>();
        tmdb.tmdb("/watch/providers/movie", "watch_region", reg).path("results").forEach(todos::add);
        todos.sort(Comparator.comparingInt(p -> p.path("display_priorities").path(reg).asInt(99)));
        List<Map<String, Object>> r = new ArrayList<>();
        for (JsonNode p : todos.subList(0, Math.min(25, todos.size()))) {
            r.add(Json.obj("id", p.path("provider_id").asInt(), "nome", p.path("provider_name").asText(),
                    "logo", TmdbService.img(p.path("logo_path").asText(null), "w92")));
        }
        return r;
    }

    /**
     * GET /api/filmes/secao/{chave} -> as fileiras extras do Início (Lançamentos, Terror, Anos 90...).
     * Só chaves conhecidas: assim ninguém usa o endpoint pra mandar qualquer coisa pro TMDB.
     */
    @GetMapping("/secao/{chave}")
    public List<Map<String, Object>> secao(@PathVariable String chave) {
        String regiao = tmdb.getRegiao();
        // Fileiras de SÉRIES
        JsonNode ds = switch (chave) {
            case "series-em-alta" -> tmdb.tmdb("/trending/tv/week");
            case "series-aclamadas" -> tmdb.tmdb("/tv/top_rated");
            case "series-brasileiras" -> tmdb.tmdb("/discover/tv", "with_origin_country", "BR",
                    "sort_by", "popularity.desc", "vote_count.gte", 20);
            case "series-animacao" -> tmdb.tmdb("/discover/tv", "with_genres", 16, "sort_by", "popularity.desc", "vote_count.gte", 100);
            default -> null;
        };
        if (ds != null) return resumosSeries(ds.path("results"), 20);

        JsonNode d = switch (chave) {
            case "lancamentos" -> tmdb.tmdb("/movie/now_playing", "region", regiao);
            case "em-breve" -> tmdb.tmdb("/movie/upcoming", "region", regiao);
            case "aclamados" -> tmdb.tmdb("/movie/top_rated");
            case "brasileiros" -> tmdb.tmdb("/discover/movie", "with_origin_country", "BR",
                    "sort_by", "popularity.desc", "vote_count.gte", 50);
            case "anos-90" -> tmdb.tmdb("/discover/movie", "primary_release_date.gte", "1990-01-01",
                    "primary_release_date.lte", "1999-12-31", "sort_by", "vote_count.desc");
            case "curtinhos" -> tmdb.tmdb("/discover/movie", "with_runtime.lte", 95, "with_runtime.gte", 60,
                    "sort_by", "popularity.desc", "vote_count.gte", 300);
            default -> {
                Integer genero = GENEROS_INICIO.get(chave);
                if (genero == null) throw new ApiException(404, "Seção não existe.");
                yield tmdb.tmdb("/discover/movie", "with_genres", genero, "sort_by", "popularity.desc", "vote_count.gte", 100);
            }
        };
        return resumos(d.path("results"), 20);
    }

    private static final Map<String, Integer> GENEROS_INICIO = Map.of(
            "acao", 28, "terror", 27, "comedia", 35, "animacao", 16,
            "ficcao", 878, "romance", 10749, "suspense", 53, "documentarios", 99);

    /** GET /api/filmes/em-alta -> o 1º vira o banner "EM DESTAQUE" do Início */
    @GetMapping("/em-alta")
    public List<Map<String, Object>> emAlta() {
        return resumos(tmdb.tmdb("/trending/movie/week").path("results"), 20);
    }

    /**
     * GET /api/filmes?q=&genero=&ano=&classificacao=&streaming=&ordem=&pagina=
     * Catálogo. Com "q" vira busca em TODOS os filmes do TMDB.
     */
    @GetMapping
    public Map<String, Object> catalogo(@RequestParam(required = false) String q,
                                        @RequestParam(required = false) String genero,
                                        @RequestParam(required = false) String ano,
                                        @RequestParam(required = false) String classificacao,
                                        @RequestParam(required = false) String streaming,
                                        @RequestParam(defaultValue = "popularity.desc") String ordem,
                                        @RequestParam(defaultValue = "1") int pagina,
                                        @RequestParam(required = false) String tipo) {
        // BUSCA POR TEXTO (qualquer tipo): títulos que têm a palavra vêm primeiro
        if (q != null && !q.isBlank()) {
            int pg = Math.max(1, Math.min(pagina, 250));
            String t = ehSerie(tipo) ? "serie" : "todos".equals(tipo) ? "todos" : "doc".equals(tipo) ? "doc" : "filme";
            if ((t.equals("filme") || t.equals("todos")) && q.trim().length() >= 4) {
                Map<String, Object> pessoa = filmesDaPessoa(q.trim(), pg);
                if (pessoa != null) return pessoa;
            }
            return buscaPorTexto(q.trim(), t, ano, pg);
        }
        if (ehSerie(tipo)) return catalogoSeries(q, genero, ano, streaming, ordem, Math.max(1, Math.min(pagina, 250)));
        if ("todos".equals(tipo)) return catalogoTodos(q, null, ano, streaming, ordem, Math.max(1, Math.min(pagina, 250)));
        // Documentários: filmes E séries do gênero 99
        if ("doc".equals(tipo)) return catalogoTodos(q, "99", ano, streaming, ordem, Math.max(1, Math.min(pagina, 250)));
        // Cada página do site = 2 páginas do TMDB (40 filmes). 40 divide certinho em 2, 4, 5, 8 e 10
        // colunas, então a grade do catálogo sempre fecha as linhas sem buraco no final.
        int pag = Math.max(1, Math.min(pagina, 250));

        // Busca por ATOR ou DIRETOR: "emma stone", "nolan"... -> filmes da pessoa
        if (q != null && q.trim().length() >= 4) {
            Map<String, Object> pessoa = filmesDaPessoa(q.trim(), pag);
            if (pessoa != null) return pessoa;
        }

        JsonNode d1 = buscarCatalogo(q, genero, ano, classificacao, streaming, ordem, pag * 2 - 1);
        int paginasTmdb = Math.min(d1.path("total_pages").asInt(0), 500);   // TMDB limita em 500 páginas
        List<Map<String, Object>> filmes = new ArrayList<>(resumos(d1.path("results"), 20));
        if (pag * 2 <= paginasTmdb) {
            filmes.addAll(resumos(buscarCatalogo(q, genero, ano, classificacao, streaming, ordem, pag * 2).path("results"), 20));
        }
        return Json.obj(
                "pagina", pag,
                "total_paginas", (paginasTmdb + 1) / 2,
                "total", d1.path("total_results").asLong(0),
                "filmes", filmes);
    }

    /**
     * "Todos": filmes E séries juntos.
     *  - com busca: /search/multi do TMDB (já vem misturado por relevância; pessoas ficam de fora)
     *  - sem busca: 20 filmes + 20 séries da mesma página, intercalados
     */
    private Map<String, Object> catalogoTodos(String q, String genero, String ano, String streaming, String ordem, int pag) {
        boolean soDoc = "99".equals(genero);
        if (!soDoc && q != null && q.trim().length() >= 4) {
            Map<String, Object> pessoa = filmesDaPessoa(q.trim(), pag);
            if (pessoa != null) return pessoa;
        }
        List<Map<String, Object>> itens = new ArrayList<>();
        long total;
        int paginas;
        if (q != null && !q.isBlank()) {
            JsonNode d1 = tmdb.tmdb("/search/multi", "query", q, "page", pag * 2 - 1, "include_adult", false);
            paginas = Math.min(d1.path("total_pages").asInt(0), 500);
            List<JsonNode> todos = new ArrayList<>();
            d1.path("results").forEach(todos::add);
            if (pag * 2 <= paginas) tmdb.tmdb("/search/multi", "query", q, "page", pag * 2, "include_adult", false).path("results").forEach(todos::add);
            for (JsonNode r : todos) {
                String mt = r.path("media_type").asText();
                if (soDoc && !TmdbService.ehDocumentario(r)) continue;
                if ("movie".equals(mt)) itens.add(tmdb.resumo(r));
                else if ("tv".equals(mt)) itens.add(tmdb.resumoSerie(r));
            }
            total = d1.path("total_results").asLong(0);
            paginas = (paginas + 1) / 2;
        } else {
            JsonNode filmes = buscarCatalogo(null, genero, ano, null, streaming, ordem, pag);
            JsonNode series = buscarSeries(null, genero, ano, streaming, ordem, pag);
            List<Map<String, Object>> fs = resumos(filmes.path("results"), 20), ss = resumosSeries(series.path("results"), 20);
            for (int i = 0; i < Math.max(fs.size(), ss.size()); i++) {
                if (i < fs.size()) itens.add(fs.get(i));
                if (i < ss.size()) itens.add(ss.get(i));
            }
            total = filmes.path("total_results").asLong(0) + series.path("total_results").asLong(0);
            paginas = Math.min(500, Math.max(filmes.path("total_pages").asInt(0), series.path("total_pages").asInt(0)));
        }
        return Json.obj("pagina", pag, "total_paginas", Math.max(1, Math.min(paginas, 250)), "total", total, "filmes", itens);
    }

    // ------------------------------------------------------------------
    // BUSCA: o TMDB devolve por "relevância" do jeito dele ("friends" trazia Barney e Thomas
    // antes da série Friends). Aqui pegamos os 5 primeiros lotes (até 100 resultados), damos
    // uma nota pra cada título e ordenamos:
    //   0 = título igual ao buscado ("Friends")      1 = começa com a palavra ("Friends: The Reunion")
    //   2 = tem a palavra inteira ("Best Friends")    3 = tem o pedaço ("Friendship")
    //   4 = não tem no título (achado por outro nome) -> some, se já tiver resultado bom suficiente
    // Empate: o mais popular primeiro. Sempre 40 por página (a grade fecha certinho).
    // ------------------------------------------------------------------
    private static final int BUSCA_LOTES = 5;

    private static String normal(String s) {
        return semAcento(s == null ? "" : s).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** Data de lançamento (filme) ou de estreia (série) como texto "2024-03-01"; sem data = "" (vai pro fim). */
    private static String lancamento(JsonNode r) {
        String d = r.path("release_date").asText("");
        return d.isBlank() ? r.path("first_air_date").asText("") : d;
    }

    private static int notaTitulo(JsonNode r, String qn) {
        int melhor = 4;
        for (String campo : new String[]{"title", "original_title", "name", "original_name"}) {
            String t = normal(r.path(campo).asText(""));
            if (t.isEmpty()) continue;
            int n;
            if (t.equals(qn)) n = 0;
            else if (t.startsWith(qn + " ")) n = 1;
            else if ((" " + t + " ").contains(" " + qn + " ")) n = 2;
            else if (t.contains(qn)) n = 3;
            else n = 4;
            melhor = Math.min(melhor, n);
        }
        return melhor;
    }

    private record Achado(JsonNode r, String mt, int nota) { }

    /** Detalhes do TMDB de um resultado (coleção, produtoras, criadores). Erro = vazio. */
    private JsonNode detalhe(Achado a) {
        try {
            return tmdb.tmdb(("tv".equals(a.mt()) ? "/tv/" : "/movie/") + a.r().path("id").asLong());
        } catch (Exception e) {
            return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        }
    }

    private static java.util.Set<Long> ids(JsonNode lista) {
        java.util.Set<Long> s = new java.util.HashSet<>();
        for (JsonNode n : lista) s.add(n.path("id").asLong());
        return s;
    }

    /**
     * O título é mesmo da franquia do principal? Precisa de pelo menos UMA prova:
     *  a) é filme de uma COLEÇÃO do TMDB com o nome buscado ("Coleção Naruto") ou da mesma coleção do principal;
     *  b) tem o mesmo CRIADOR do principal (séries);
     *  c) é da mesma PRODUTORA do principal (ex.: Studio Pierrot no Naruto).
     */
    private static boolean ehDaFranquia(Achado a, JsonNode det, JsonNode principal, String qn) {
        JsonNode col = det.path("belongs_to_collection");
        if (col.isObject()) {
            if (normal(col.path("name").asText("")).contains(qn)) return true;
            JsonNode colP = principal.path("belongs_to_collection");
            if (colP.isObject() && colP.path("id").asLong() == col.path("id").asLong()) return true;
        }
        java.util.Set<Long> criadores = ids(principal.path("created_by"));
        criadores.retainAll(ids(det.path("created_by")));
        if (!criadores.isEmpty()) return true;
        java.util.Set<Long> produtoras = ids(principal.path("production_companies"));
        produtoras.retainAll(ids(det.path("production_companies")));
        return !produtoras.isEmpty();
    }

    private Map<String, Object> buscaPorTexto(String q, String tipo, String ano, int pag) {
        String caminho = switch (tipo) {
            case "serie" -> "/search/tv";
            case "filme" -> "/search/movie";
            default -> "/search/multi";   // todos / doc
        };
        String qn = normal(q);
        List<Achado> achados = new ArrayList<>();
        java.util.Set<String> vistos = new java.util.HashSet<>();
        for (int p = 1; p <= BUSCA_LOTES; p++) {
            JsonNode d = tmdb.tmdb(caminho, "query", q, "page", p, "include_adult", false,
                    "year", "filme".equals(tipo) ? ano : null,
                    "first_air_date_year", "serie".equals(tipo) ? ano : null);
            for (JsonNode r : d.path("results")) {
                String mt = switch (tipo) {
                    case "serie" -> "tv";
                    case "filme" -> "movie";
                    default -> r.path("media_type").asText();
                };
                if (!mt.equals("movie") && !mt.equals("tv")) continue;   // pessoas ficam de fora
                if ("doc".equals(tipo) && !TmdbService.ehDocumentario(r)) continue;
                if (vistos.add(mt + r.path("id").asLong())) achados.add(new Achado(r, mt, notaTitulo(r, qn)));
            }
            if (p >= d.path("total_pages").asInt(0)) break;
        }
        long bons = achados.stream().filter(a -> a.nota() < 4).count();
        if (bons >= 10) achados.removeIf(a -> a.nota() == 4);
        // ORDEM DA BUSCA (3 regras):
        //  1) O PRINCIPAL: o título mais famoso (mais votos no TMDB) com o nome EXATO do que foi buscado.
        //     "friends" -> a série Friends (1994); "naruto shippuden" -> a série de 2007.
        //  2) A FRANQUIA: títulos com o nome buscado (ou começando com ele) que SÃO da mesma marca,
        //     do lançamento mais recente pro mais antigo. Só o nome não basta ("Friends with Benefits"
        //     começa com "friends" e não é do Friends): precisa de uma prova, veja ehDaFranquia().
        //  3) O RESTO: do mais parecido pro menos, e os mais populares primeiro.
        Achado principal = achados.stream().filter(a -> a.nota() == 0)
                .max(Comparator.comparingDouble(a -> a.r().path("vote_count").asDouble())).orElse(null);
        if (principal == null) principal = achados.stream().filter(a -> a.nota() == 1)
                .max(Comparator.comparingDouble(a -> a.r().path("vote_count").asDouble())).orElse(null);
        final Achado p0 = principal;

        java.util.Set<Achado> franquia = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        if (p0 != null) {
            JsonNode detP = detalhe(p0);
            // Só os 20 candidatos mais votados são conferidos (cada um é uma consulta ao TMDB, guardada 5 min)
            achados.stream().filter(a -> a != p0 && a.nota() <= 1)
                    .sorted(Comparator.comparingDouble((Achado a) -> a.r().path("vote_count").asDouble()).reversed())
                    .limit(20)
                    .filter(a -> ehDaFranquia(a, detalhe(a), detP, qn))
                    .forEach(franquia::add);
        }
        achados.sort(Comparator.comparingInt((Achado a) -> a == p0 ? 0 : franquia.contains(a) ? 1 : 2)
                .thenComparing((a, b) -> franquia.contains(a) && franquia.contains(b)
                        ? lancamento(b.r()).compareTo(lancamento(a.r()))
                        : 0)
                .thenComparingInt(Achado::nota)
                .thenComparing((a, b) -> Double.compare(b.r().path("popularity").asDouble(), a.r().path("popularity").asDouble())));

        int ini = Math.min((pag - 1) * 40, achados.size()), fim = Math.min(ini + 40, achados.size());
        List<Map<String, Object>> pagina = new ArrayList<>();
        for (Achado a : achados.subList(ini, fim)) {
            pagina.add("tv".equals(a.mt()) ? tmdb.resumoSerie(a.r()) : tmdb.resumo(a.r()));
        }
        return Json.obj("pagina", pag, "total_paginas", Math.max(1, (achados.size() + 39) / 40),
                "total", achados.size(), "filmes", pagina);
    }

    /** Catálogo de SÉRIES (mesmo formato do de filmes: 40 por página) */
    private Map<String, Object> catalogoSeries(String q, String genero, String ano, String streaming, String ordem, int pag) {
        JsonNode d1 = buscarSeries(q, genero, ano, streaming, ordem, pag * 2 - 1);
        int paginasTmdb = Math.min(d1.path("total_pages").asInt(0), 500);
        List<Map<String, Object>> series = new ArrayList<>(resumosSeries(d1.path("results"), 20));
        if (pag * 2 <= paginasTmdb) {
            series.addAll(resumosSeries(buscarSeries(q, genero, ano, streaming, ordem, pag * 2).path("results"), 20));
        }
        return Json.obj(
                "pagina", pag,
                "total_paginas", (paginasTmdb + 1) / 2,
                "total", d1.path("total_results").asLong(0),
                "filmes", series);
    }

    private JsonNode buscarSeries(String q, String genero, String ano, String streaming, String ordem, int paginaTmdb) {
        if (q != null && !q.isBlank()) {
            return tmdb.tmdb("/search/tv", "query", q, "page", paginaTmdb, "first_air_date_year", ano, "include_adult", false);
        }
        // As ordens do filtro são de filme; troca pras equivalentes de série
        String ordemTv = switch (ordem) {
            case "primary_release_date.desc" -> "first_air_date.desc";
            case "vote_average.desc" -> "vote_average.desc";
            default -> "popularity.desc";
        };
        boolean temStreaming = streaming != null && !streaming.isBlank();
        return tmdb.tmdb("/discover/tv",
                "page", paginaTmdb,
                "sort_by", ordemTv,
                "with_genres", genero,
                "first_air_date_year", ano,
                "with_watch_providers", streaming,
                "watch_region", temStreaming ? tmdb.getRegiao() : null,
                "vote_count.gte", ordemTv.startsWith("vote_average") ? 200 : null,
                "first_air_date.lte", ordemTv.startsWith("first_air_date") ? java.time.LocalDate.now().toString() : null,
                "include_adult", false);
    }

    /** Se o texto for o nome de um ator/diretor conhecido, devolve os filmes dele (40 por página). */
    private Map<String, Object> filmesDaPessoa(String q, int pag) {
        JsonNode pessoas = tmdb.tmdb("/search/person", "query", q, "include_adult", false).path("results");
        if (!pessoas.isArray() || pessoas.isEmpty()) return null;
        JsonNode p = pessoas.get(0);
        String nome = p.path("name").asText("");
        String dep = p.path("known_for_department").asText("");
        // Só considera "achou a pessoa" se o nome bate com o que foi digitado
        if (!semAcento(nome).contains(semAcento(q)) || !(dep.equals("Acting") || dep.equals("Directing"))) return null;
        // "naruto" tem uma pessoa com esse nome, mas o que a pessoa procura é o anime:
        // se a busca geral do TMDB acha primeiro um filme/série, não é busca de pessoa
        JsonNode multi = tmdb.tmdb("/search/multi", "query", q, "include_adult", false).path("results");
        if (multi.isArray() && !multi.isEmpty() && !"person".equals(multi.get(0).path("media_type").asText())) return null;

        JsonNode cred = tmdb.tmdb("/person/" + p.path("id").asLong() + "/movie_credits");
        Map<Long, JsonNode> filmes = new LinkedHashMap<>();
        for (JsonNode f : cred.path("cast")) filmes.putIfAbsent(f.path("id").asLong(), f);
        for (JsonNode f : cred.path("crew")) if ("Director".equals(f.path("job").asText())) filmes.putIfAbsent(f.path("id").asLong(), f);
        List<JsonNode> ordem = new ArrayList<>(filmes.values());
        ordem.removeIf(f -> f.path("poster_path").isNull() || f.path("poster_path").asText("").isBlank());
        ordem.sort((a, b) -> Double.compare(b.path("popularity").asDouble(), a.path("popularity").asDouble()));

        if (ordem.size() < 3) return null;   // pessoa quase sem filmes: melhor mostrar a busca normal
        int ini = Math.min((pag - 1) * 40, ordem.size()), fim = Math.min(ini + 40, ordem.size());
        List<Map<String, Object>> pagina = new ArrayList<>();
        for (JsonNode f : ordem.subList(ini, fim)) pagina.add(tmdb.resumo(f));
        return Json.obj(
                "pagina", pag,
                "total_paginas", Math.max(1, (ordem.size() + 39) / 40),
                "total", ordem.size(),
                "pessoa", Json.obj("nome", nome, "foto", TmdbService.img(p.path("profile_path").asText(null), "w185"),
                        "funcao", dep.equals("Directing") ? "Direção" : "Atuação"),
                "filmes", pagina);
    }

    private static String semAcento(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
    }

    private JsonNode buscarCatalogo(String q, String genero, String ano, String classificacao,
                                    String streaming, String ordem, int paginaTmdb) {
        if (q != null && !q.isBlank()) {
            return tmdb.tmdb("/search/movie", "query", q, "page", paginaTmdb, "year", ano, "include_adult", false);
        }
        boolean temClassif = classificacao != null && !classificacao.isBlank();
        boolean temStreaming = streaming != null && !streaming.isBlank();
        return tmdb.tmdb("/discover/movie",
                "page", paginaTmdb,
                "sort_by", ordem,                       // popularity.desc | vote_average.desc | primary_release_date.desc
                "with_genres", genero,
                "primary_release_year", ano,
                "certification_country", temClassif ? "BR" : null,
                "certification", classificacao,          // L, 10, 12, 14, 16, 18
                "with_watch_providers", streaming,
                "watch_region", temStreaming ? tmdb.getRegiao() : null,
                "vote_count.gte", ordem.startsWith("vote_average") ? 300 : null,
                "include_adult", false);
    }

    /** GET /api/filmes/{id} -> tela Detalhes do filme (tudo de uma vez) */
    @GetMapping("/{id}")
    public Map<String, Object> detalhes(@PathVariable long id, HttpServletRequest req) {
        if (TmdbService.ehSerie(id)) return detalhesSerie(id, req);
        JsonNode f = tmdb.tmdb("/movie/" + id, "append_to_response", "credits,watch/providers,release_dates,similar,videos");
        tmdb.salvarNoCache(f);

        // Classificação indicativa do Brasil (ex.: "14")
        String classificacao = null;
        for (JsonNode pais : f.path("release_dates").path("results")) {
            if (!"BR".equals(pais.path("iso_3166_1").asText())) continue;
            for (JsonNode rd : pais.path("release_dates")) {
                if (!rd.path("certification").asText("").isBlank()) { classificacao = rd.path("certification").asText(); break; }
            }
        }

        // Onde assistir (dados JustWatch via TMDB)
        JsonNode prov = f.path("watch/providers").path("results").path(tmdb.getRegiao());
        List<Map<String, Object>> onde = new ArrayList<>();
        String[][] tipos = {{"flatrate", "assinatura"}, {"rent", "aluguel"}, {"buy", "compra"}};
        for (String[] t : tipos) {
            for (JsonNode p : prov.path(t[0])) {
                onde.add(Json.obj("id", p.path("provider_id").asInt(), "nome", p.path("provider_name").asText(),
                        "logo", TmdbService.img(p.path("logo_path").asText(null), "w92"), "tipo", t[1]));
            }
        }

        List<String> generos = new ArrayList<>();
        f.path("genres").forEach(g -> generos.add(g.path("name").asText()));

        List<String> direcao = new ArrayList<>();
        f.path("credits").path("crew").forEach(c -> {
            if ("Director".equals(c.path("job").asText())) direcao.add(c.path("name").asText());
        });

        List<Map<String, Object>> elenco = new ArrayList<>();
        for (JsonNode c : f.path("credits").path("cast")) {
            if (elenco.size() >= 15) break;
            elenco.add(Json.obj("nome", c.path("name").asText(), "personagem", c.path("character").asText(),
                    "foto", TmdbService.img(c.path("profile_path").asText(null), "w185")));
        }

        String trailer = null;
        for (JsonNode v : f.path("videos").path("results")) {
            if ("YouTube".equals(v.path("site").asText()) && "Trailer".equals(v.path("type").asText())) {
                trailer = v.path("key").asText();
                break;
            }
        }

        // Nota da NOSSA comunidade (tabela reviews)
        Map<String, Object> nossa = db.um("SELECT ROUND(AVG(nota),1) AS media, COUNT(*) AS total FROM reviews WHERE tmdb_id = ?", id).orElseThrow();

        // Estado do usuário logado (botões Assistido / Favoritar / review)
        Map<String, Object> meu = Json.obj("assistido", false, "status", null, "favorito", false, "minha_review", null);
        UsuarioLogado u = Auth.opcional(req);
        if (u != null) {
            String status = db.um("SELECT status FROM assistidos WHERE usuario_id = ? AND tmdb_id = ?", u.id(), id)
                    .map(a -> (String) a.get("status")).orElse(null);
            meu.put("status", status);   // quero_assistir | assistindo | assistido | abandonado | null
            meu.put("assistido", "assistido".equals(status));
            meu.put("favorito", db.existe("SELECT 1 FROM favoritos WHERE usuario_id = ? AND tmdb_id = ?", u.id(), id));
            meu.put("minha_review", db.um("SELECT id, nota, texto FROM reviews WHERE usuario_id = ? AND tmdb_id = ?", u.id(), id).orElse(null));
        }

        Map<String, Object> r = tmdb.resumo(f);
        r.put("duracao_min", f.path("runtime").isNumber() ? f.path("runtime").asInt() : null);
        r.put("generos", generos);
        r.put("lancamento", f.path("release_date").asText(null));
        r.put("idioma", f.path("spoken_languages").path(0).path("name").asText(f.path("original_language").asText(null)));
        r.put("classificacao", classificacao);
        r.put("votos_tmdb", f.path("vote_count").asLong(0));
        r.put("direcao", direcao);
        r.put("elenco", elenco);
        r.put("onde_assistir", onde);
        r.put("semelhantes", resumos(f.path("similar").path("results"), 12));
        r.put("trailer_youtube", trailer);
        r.put("comunidade", Json.obj("media", nossa.get("media"), "total", nossa.get("total")));
        r.put("meu", meu);
        return r;
    }

    /** Botões do usuário logado + nota da nossa comunidade (filme ou série) */
    private Map<String, Object> estadoDoUsuario(long id, HttpServletRequest req) {
        Map<String, Object> meu = Json.obj("assistido", false, "status", null, "favorito", false, "minha_review", null);
        UsuarioLogado u = Auth.opcional(req);
        if (u != null) {
            String status = db.um("SELECT status FROM assistidos WHERE usuario_id = ? AND tmdb_id = ?", u.id(), id)
                    .map(a -> (String) a.get("status")).orElse(null);
            meu.put("status", status);
            meu.put("assistido", "assistido".equals(status));
            meu.put("favorito", db.existe("SELECT 1 FROM favoritos WHERE usuario_id = ? AND tmdb_id = ?", u.id(), id));
            meu.put("minha_review", db.um("SELECT id, nota, texto FROM reviews WHERE usuario_id = ? AND tmdb_id = ?", u.id(), id).orElse(null));
        }
        return meu;
    }

    /** Detalhes de uma SÉRIE (id negativo). Mesmas chaves do filme + temporadas. */
    private Map<String, Object> detalhesSerie(long id, HttpServletRequest req) {
        long tvId = -id;
        JsonNode s = tmdb.tmdb("/tv/" + tvId, "append_to_response", "aggregate_credits,watch/providers,content_ratings,similar,videos");
        tmdb.salvarSerieNoCache(s);

        String classificacao = null;
        for (JsonNode c : s.path("content_ratings").path("results")) {
            if ("BR".equals(c.path("iso_3166_1").asText()) && !c.path("rating").asText("").isBlank()) { classificacao = c.path("rating").asText(); break; }
        }

        JsonNode prov = s.path("watch/providers").path("results").path(tmdb.getRegiao());
        List<Map<String, Object>> onde = new ArrayList<>();
        String[][] tipos = {{"flatrate", "assinatura"}, {"rent", "aluguel"}, {"buy", "compra"}};
        for (String[] t : tipos) {
            for (JsonNode p : prov.path(t[0])) {
                onde.add(Json.obj("id", p.path("provider_id").asInt(), "nome", p.path("provider_name").asText(),
                        "logo", TmdbService.img(p.path("logo_path").asText(null), "w92"), "tipo", t[1]));
            }
        }

        List<String> generos = new ArrayList<>();
        s.path("genres").forEach(g -> generos.add(g.path("name").asText()));
        List<String> criacao = new ArrayList<>();
        s.path("created_by").forEach(c -> criacao.add(c.path("name").asText()));

        List<Map<String, Object>> elenco = new ArrayList<>();
        for (JsonNode c : s.path("aggregate_credits").path("cast")) {
            if (elenco.size() >= 15) break;
            elenco.add(Json.obj("nome", c.path("name").asText(),
                    "personagem", c.path("roles").path(0).path("character").asText(""),
                    "foto", TmdbService.img(c.path("profile_path").asText(null), "w185")));
        }

        String trailer = null;
        for (JsonNode v : s.path("videos").path("results")) {
            if ("YouTube".equals(v.path("site").asText()) && ("Trailer".equals(v.path("type").asText()) || "Teaser".equals(v.path("type").asText()))) {
                trailer = v.path("key").asText();
                break;
            }
        }

        // Temporadas (a "temporada 0" do TMDB são os especiais: vão por último)
        List<Map<String, Object>> temporadas = new ArrayList<>();
        Map<String, Object> especiais = null;
        for (JsonNode t : s.path("seasons")) {
            if (t.path("episode_count").asInt(0) == 0) continue;
            Map<String, Object> m = Json.obj("numero", t.path("season_number").asInt(),
                    "nome", t.path("name").asText(), "episodios", t.path("episode_count").asInt(),
                    "ano", t.path("air_date").asText("").length() >= 4 ? Integer.valueOf(t.path("air_date").asText().substring(0, 4)) : null,
                    "poster", TmdbService.img(t.path("poster_path").asText(null), "w342"));
            if (t.path("season_number").asInt() == 0) especiais = m; else temporadas.add(m);
        }
        if (especiais != null) temporadas.add(especiais);

        String situacao = switch (s.path("status").asText("")) {
            case "Returning Series" -> "Em andamento";
            case "Ended" -> "Finalizada";
            case "Canceled" -> "Cancelada";
            case "In Production" -> "Em produção";
            case "Planned" -> "Planejada";
            default -> null;
        };

        Map<String, Object> nossa = db.um("SELECT ROUND(AVG(nota),1) AS media, COUNT(*) AS total FROM reviews WHERE tmdb_id = ?", id).orElseThrow();

        Map<String, Object> r = tmdb.resumoSerie(s);
        int ep = TmdbService.duracaoEpisodio(s);
        r.put("duracao_min", ep == 0 ? null : ep);   // duração de 1 episódio
        r.put("generos", generos);
        r.put("idioma", s.path("spoken_languages").path(0).path("name").asText(s.path("original_language").asText(null)));
        r.put("classificacao", classificacao);
        r.put("votos_tmdb", s.path("vote_count").asLong(0));
        r.put("direcao", criacao);   // na série é "Criação"
        r.put("elenco", elenco);
        r.put("onde_assistir", onde);
        r.put("semelhantes", resumosSeries(s.path("similar").path("results"), 12));
        r.put("trailer_youtube", trailer);
        r.put("comunidade", Json.obj("media", nossa.get("media"), "total", nossa.get("total")));
        r.put("meu", estadoDoUsuario(id, req));
        r.put("temporadas", temporadas);
        r.put("total_temporadas", s.path("number_of_seasons").asInt(0));
        r.put("total_episodios", s.path("number_of_episodes").asInt(0));
        r.put("situacao", situacao);
        return r;
    }

    /** GET /api/filmes/{id}/temporada/{n} -> episódios de uma temporada (só série, id negativo) */
    @GetMapping("/{id}/temporada/{n}")
    public Map<String, Object> temporada(@PathVariable long id, @PathVariable int n) {
        if (!TmdbService.ehSerie(id)) throw new ApiException(400, "Isso não é uma série.");
        JsonNode t = tmdb.tmdb("/tv/" + (-id) + "/season/" + n);
        List<Map<String, Object>> eps = new ArrayList<>();
        for (JsonNode e : t.path("episodes")) {
            double nota = e.path("vote_average").asDouble(0);
            eps.add(Json.obj("numero", e.path("episode_number").asInt(),
                    "nome", e.path("name").asText(),
                    "data", e.path("air_date").asText(null),
                    "duracao_min", e.path("runtime").isNumber() ? e.path("runtime").asInt() : null,
                    "nota", nota == 0 ? null : Math.round(nota * 10) / 10.0,
                    "sinopse", e.path("overview").asText(""),
                    "imagem", TmdbService.img(e.path("still_path").asText(null), "w300")));
        }
        return Json.obj("numero", n, "nome", t.path("name").asText(), "episodios", eps);
    }

    /** GET /api/filmes/{id}/recomendados -> "Recomendados para você" */
    @GetMapping("/{id}/recomendados")
    public List<Map<String, Object>> recomendados(@PathVariable long id) {
        if (TmdbService.ehSerie(id)) return resumosSeries(tmdb.tmdb("/tv/" + (-id) + "/recommendations").path("results"), 20);
        return resumos(tmdb.tmdb("/movie/" + id + "/recommendations").path("results"), 20);
    }
}
