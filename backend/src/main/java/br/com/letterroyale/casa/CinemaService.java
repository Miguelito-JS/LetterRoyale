package br.com.letterroyale.casa;

import br.com.letterroyale.casa.atmosfera.Atmosfera;
import br.com.letterroyale.casa.atmosfera.AtmosferaPorGenero;
import br.com.letterroyale.casa.atmosfera.EfeitoLuz;
import br.com.letterroyale.casa.eventos.EventoCasa;
import br.com.letterroyale.erro.ApiException;
import br.com.letterroyale.notificacao.NotificacaoService;
import br.com.letterroyale.tmdb.TmdbService;
import br.com.letterroyale.util.Db;
import br.com.letterroyale.util.Json;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * =====================================================================
 *  MODO CINEMA — a lâmpada troca entre as cores do pôster do filme.
 *
 *  2 jeitos de usar:
 *   • NAVEGANDO:  ativou o Modo Cinema e saiu olhando filmes no site.
 *                 Abriu Matrix -> verdes. Abriu Duna -> laranjas.
 *   • ASSISTINDO: apertou "Assistir agora" num filme. Fica travado nele
 *                 até acabar a duração do filme. No fim: cena Créditos
 *                 (luz sobe aos poucos) + o site pergunta "O que achou?".
 *
 *  O BRILHO vem do slider de Iluminação (mexer nele NÃO desliga o modo).
 *  [+] ATMOSFERA: no modo ASSISTINDO, os gêneros do filme decidem como a
 *      sala inteira se comporta (terror = luz baixa pulsando, ação = clarões,
 *      romance = luz quente...). Ver casa/atmosfera/AtmosferaPorGenero.java.
 *  O estado fica na tabela cinema_ativo -> se o servidor reiniciar, continua.
 * =====================================================================
 */
@Service
public class CinemaService {
    private static final Logger log = LoggerFactory.getLogger(CinemaService.class);
    public static final int BRILHO_INICIAL = 30;

    private final Db db;
    private final TmdbService tmdb;
    private final PaletaService paletas;
    private final TuyaService tuya;
    private final CasaService casa;
    private final NotificacaoService notificacoes;

    // Quando cada usuário troca de cor de novo (em memória; não precisa ir pro banco)
    private final Map<Long, Long> proximaTroca = new ConcurrentHashMap<>();
    // Atmosfera do filme que cada usuário está ASSISTINDO (em memória; se o servidor reiniciar, é recalculada)
    private final Map<Long, Atmosfera> atmosferas = new ConcurrentHashMap<>();
    // [+] Intervalo: desde quando o filme está pausado (o fim do filme é empurrado pra frente ao voltar)
    private final Map<Long, Long> pausadoDesde = new ConcurrentHashMap<>();

    public CinemaService(Db db, TmdbService tmdb, PaletaService paletas, TuyaService tuya, CasaService casa,
                         NotificacaoService notificacoes) {
        this.notificacoes = notificacoes;
        this.db = db;
        this.tmdb = tmdb;
        this.paletas = paletas;
        this.tuya = tuya;
        this.casa = casa;
    }

    public static int ritmoEmSegundos(String ritmo) {
        return switch (ritmo == null ? "" : ritmo) {
            case "calmo" -> 8;
            case "festa" -> 2;
            default -> 4;   // normal
        };
    }

    private boolean ativo(long uid) {
        return db.existe("SELECT 1 FROM cinema_ativo WHERE usuario_id = ? AND terminou = FALSE", uid);
    }

    /** Liga o Modo Cinema. tmdbId pode ser null (modo navegando sem filme aberto ainda). */
    public void iniciar(long uid, Long tmdbId, String modo, String ritmo) {
        iniciar(uid, tmdbId, modo, ritmo, null, null);
    }

    /**
     * Série: assiste UM episódio (temporada + episódio). A luz usa as cores do pôster da série
     * e o modo dura o tempo do episódio.
     */
    public void iniciar(long uid, Long tmdbId, String modo, String ritmo, Integer temporada, Integer episodio) {
        boolean assistindo = "assistindo".equals(modo);
        if (assistindo && tmdbId == null) throw new ApiException(400, "Escolha o filme que vai assistir.");
        boolean serie = tmdbId != null && TmdbService.ehSerie(tmdbId);
        if (!assistindo || !serie) { temporada = null; episodio = null; }
        if (assistindo && serie && (temporada == null || episodio == null)) throw new ApiException(400, "Escolha o episódio que vai assistir.");

        Integer duracao = null;
        String tituloEp = null;
        if (assistindo && serie) {
            JsonNode ep = tmdb.episodioLancado(tmdbId, temporada, episodio);   // não dá pra assistir episódio que não saiu
            tituloEp = ep.path("name").asText(null);
            if (ep.path("runtime").isNumber()) duracao = ep.path("runtime").asInt();
            tmdb.garantirNoCache(tmdbId);
        } else if (assistindo) {
            tmdb.exigirLancado(tmdbId);   // não dá pra assistir filme que não estreou
        }
        // Começou a assistir algo que estava na fila -> sai da fila
        if (assistindo) db.executar("DELETE FROM fila_cinema WHERE usuario_id = ? AND tmdb_id = ? AND temporada = ? AND episodio = ?",
                uid, tmdbId, serie ? temporada : 0, serie ? episodio : 0);

        if (tmdbId != null) {
            paletas.paleta(tmdbId);   // já calcula e guarda as cores (na série: do pôster da série)
            if (duracao == null) duracao = db.um("SELECT duracao_min FROM filmes_cache WHERE tmdb_id = ?", tmdbId)
                    .map(f -> f.get("duracao_min") == null ? null : ((Number) f.get("duracao_min")).intValue()).orElse(null);
        }
        int minutos = (duracao == null || duracao <= 0) ? (serie ? 45 : 120) : duracao;

        // [+] ATMOSFERA: assistindo um filme -> os gêneros decidem brilho, ritmo, efeito, cortinas, ar e volume
        Atmosfera atmosfera = (assistindo && tmdbId != null) ? atmosferaDoFilme(tmdbId) : null;
        if (atmosfera != null) ritmo = atmosfera.ritmo();
        int brilhoInicial = atmosfera != null ? atmosfera.brilho() : BRILHO_INICIAL;

        db.executar("""
                REPLACE INTO cinema_ativo (usuario_id, tmdb_id, modo, ritmo_seg, indice_cor, iniciado_em, termina_em, terminou,
                                           temporada, episodio, titulo_ep)
                VALUES (?, ?, ?, ?, 0, NOW(), IF(?, DATE_ADD(NOW(), INTERVAL ? MINUTE), NULL), FALSE, ?, ?, ?)
                """, uid, tmdbId, assistindo ? "assistindo" : "navegando", ritmoEmSegundos(ritmo), assistindo, minutos,
                temporada, episodio, tituloEp);

        // Card "Modo Cinema" aparece como ativo + luz em 30%
        db.executar("UPDATE cenas SET ativa = (nome = 'Modo Cinema') WHERE usuario_id = ?", uid);
        db.executar("UPDATE dispositivos SET ligado = TRUE, valor = ? WHERE usuario_id = ? AND tipo = 'iluminacao'", brilhoInicial, uid);

        String titulo = tmdbId == null ? null : db.um("SELECT titulo FROM filmes_cache WHERE tmdb_id = ?", tmdbId)
                .map(f -> (String) f.get("titulo")).orElse(null);
        if (atmosfera != null) {
            atmosferas.put(uid, atmosfera);
            casa.publicar(uid, EventoCasa.Tipo.SESSAO_INICIADA, "Sessão começou: " + titulo + (tituloEp != null ? " · " + tituloEp : ""));
            try {
                casa.aplicarAmbiente(uid, atmosfera);   // cortinas, ar e volume entram no clima do filme
            } catch (Exception e) {
                log.warn("Atmosfera aplicada só em parte: {}", e.getMessage());
            }
        } else {
            atmosferas.remove(uid);
            casa.publicar(uid, EventoCasa.Tipo.SESSAO_INICIADA, titulo == null
                    ? "Modo Cinema ligado: a luz acompanha o filme que você abrir"
                    : "Modo Cinema acompanhando " + titulo);
        }

        if (tmdbId == null) {
            // Ainda sem filme: luz quente e fraca até a pessoa abrir algum
            try {
                tuya.luzBranca(casa.lampadaDo(uid), BRILHO_INICIAL, 0);
            } catch (TuyaService.LampadaException e) {
                log.warn(e.getMessage());
            }
        }
        proximaTroca.put(uid, 0L);   // troca já no próximo segundo
        log.info("🎬 Modo Cinema {} para usuário {} (filme {})", modo, uid, tmdbId);
    }

    /** O usuário abriu a página de um filme. Só muda a luz no modo NAVEGANDO. */
    public boolean foco(long uid, long tmdbId) {
        Map<String, Object> c = db.um("SELECT * FROM cinema_ativo WHERE usuario_id = ? AND terminou = FALSE", uid).orElse(null);
        if (c == null || !"navegando".equals(c.get("modo"))) return false;
        if (c.get("tmdb_id") != null && ((Number) c.get("tmdb_id")).longValue() == tmdbId) return true;
        paletas.paleta(tmdbId);
        db.executar("UPDATE cinema_ativo SET tmdb_id = ?, indice_cor = 0 WHERE usuario_id = ?", tmdbId, uid);
        proximaTroca.put(uid, 0L);
        return true;
    }

    public void mudarRitmo(long uid, String ritmo) {
        db.executar("UPDATE cinema_ativo SET ritmo_seg = ? WHERE usuario_id = ?", ritmoEmSegundos(ritmo), uid);
    }

    /** Slider mexido com o Modo Cinema ligado: reenvia a cor atual com o brilho novo. */
    public boolean reaplicarSeAtivo(long uid) {
        if (!ativo(uid)) return false;
        proximaTroca.put(uid, 0L);
        db.executar("UPDATE cinema_ativo SET indice_cor = GREATEST(indice_cor - 1, 0) WHERE usuario_id = ?", uid);  // mesma cor
        return true;
    }

    /** Desliga o Modo Cinema (botão Desativar, outra cena, lâmpada desligada...). */
    public void parar(long uid) {
        db.executar("DELETE FROM cinema_ativo WHERE usuario_id = ?", uid);
        db.executar("UPDATE cenas SET ativa = FALSE WHERE usuario_id = ? AND nome = 'Modo Cinema'", uid);
        proximaTroca.remove(uid);
        pausadoDesde.remove(uid);
        if (atmosferas.remove(uid) != null) casa.publicar(uid, EventoCasa.Tipo.CINEMA_DESLIGADO, "Modo Cinema desligado");
    }

    /** Fim do filme (pelo relógio ou pelo botão "Encerrar filme agora" da apresentação). */
    public void terminarFilme(long uid) {
        Map<String, Object> c = db.um("SELECT * FROM cinema_ativo WHERE usuario_id = ? AND terminou = FALSE", uid).orElse(null);
        if (c == null) return;
        db.executar("UPDATE cinema_ativo SET terminou = TRUE WHERE usuario_id = ?", uid);
        proximaTroca.remove(uid);
        atmosferas.remove(uid);
        pausadoDesde.remove(uid);
        casa.publicar(uid, EventoCasa.Tipo.CREDITOS, "Fim do filme: créditos subindo, luz voltando aos poucos");

        boolean serie = c.get("tmdb_id") != null && TmdbService.ehSerie(((Number) c.get("tmdb_id")).longValue());
        if (serie && "assistindo".equals(c.get("modo"))) {
            // Acabou UM episódio: a série fica como "Assistindo" (se já não estava como assistida)
            db.executar("""
                    INSERT INTO assistidos (usuario_id, tmdb_id, assistido_em, concluido, status) VALUES (?, ?, CURDATE(), FALSE, 'assistindo')
                    ON DUPLICATE KEY UPDATE status = IF(status = 'assistido', 'assistido', 'assistindo')
                    """, uid, c.get("tmdb_id"));
        } else if (c.get("tmdb_id") != null && "assistindo".equals(c.get("modo"))) {
            // Marca como assistido (o site depois pergunta a opinião)
            db.executar("""
                    INSERT INTO assistidos (usuario_id, tmdb_id, assistido_em, concluido, status) VALUES (?, ?, CURDATE(), TRUE, 'assistido')
                    ON DUPLICATE KEY UPDATE concluido = TRUE, status = 'assistido', assistido_em = CURDATE()
                    """, uid, c.get("tmdb_id"));
        }
        // Sininho: "Você terminou X. Que tal avaliar?" (vale mesmo se a pessoa fechou o site no meio)
        if (c.get("tmdb_id") != null && "assistindo".equals(c.get("modo"))) {
            notificacoes.fimDoCinema(uid, ((Number) c.get("tmdb_id")).longValue(),
                    c.get("temporada") == null ? null : ((Number) c.get("temporada")).intValue(),
                    c.get("episodio") == null ? null : ((Number) c.get("episodio")).intValue());
        }
        // Cena Créditos: luz sobe aos poucos
        db.um("SELECT id FROM cenas WHERE usuario_id = ? AND nome = 'Créditos'", uid)
                .ifPresent(cena -> casa.aplicarCena(uid, ((Number) cena.get("id")).longValue()));
        log.info("🎬 Filme terminou para usuário {}", uid);
    }

    /** Estado para a tela (Smart Home + aviso "O que achou?"). */
    public Map<String, Object> estado(long uid) {
        Map<String, Object> c = db.um("""
                SELECT ca.*, f.titulo, f.poster_path, f.backdrop_path, f.ano, f.nota_tmdb, f.paleta
                  FROM cinema_ativo ca LEFT JOIN filmes_cache f ON f.tmdb_id = ca.tmdb_id
                 WHERE ca.usuario_id = ?
                """, uid).orElse(null);
        if (c == null) return Json.obj("ativo", false, "proximo_fila", proximoDaFila(uid));

        List<String> paleta = c.get("tmdb_id") == null ? List.of() : paletas.paleta(((Number) c.get("tmdb_id")).longValue());
        int idx = ((Number) c.get("indice_cor")).intValue();
        String corAtual = paleta.isEmpty() ? null : paleta.get(Math.floorMod(idx - 1, paleta.size()));
        int ritmo = ((Number) c.get("ritmo_seg")).intValue();
        return Json.obj(
                "ativo", !Json.verdade(c.get("terminou")),
                "terminou", Json.verdade(c.get("terminou")),
                "modo", c.get("modo"),
                "ritmo", ritmo == 8 ? "calmo" : ritmo == 2 ? "festa" : "normal",
                "ritmo_seg", ritmo,
                "termina_em", c.get("termina_em"),
                "filme", c.get("tmdb_id") == null ? null : comEpisodio(TmdbService.doCache(c), c),
                "paleta", paleta,
                "cor_atual", corAtual,
                "atmosfera", Json.verdade(c.get("terminou")) ? null : paraMapa(atmosferaAtiva(uid, c)),
                "pausado", pausadoDesde.containsKey(uid),
                "proximo_fila", proximoDaFila(uid));
    }

    // ================= INTERVALO (pausa de verdade, sem perder o filme) =================

    /** Pausa: as cores param, a luz sobe pra 60% neutra e o relógio do filme congela. */
    public void pausar(long uid) {
        Map<String, Object> c = db.um("SELECT * FROM cinema_ativo WHERE usuario_id = ? AND terminou = FALSE", uid)
                .orElseThrow(() -> new ApiException(400, "Nenhum filme passando agora."));
        if (pausadoDesde.putIfAbsent(uid, System.currentTimeMillis()) != null) return;   // já estava pausado
        try {
            tuya.luzBranca(casa.lampadaDo(uid), 60, 50);
        } catch (TuyaService.LampadaException e) {
            log.warn(e.getMessage());
        }
        String titulo = c.get("tmdb_id") == null ? "o filme" : db.um("SELECT titulo FROM filmes_cache WHERE tmdb_id = ?", c.get("tmdb_id"))
                .map(f -> (String) f.get("titulo")).orElse("o filme");
        casa.publicar(uid, EventoCasa.Tipo.INTERVALO, "Intervalo: " + titulo + " pausado, luz em 60%");
    }

    /** Volta do intervalo: empurra o fim do filme pelo tempo parado e as cores voltam. */
    public void retomar(long uid) {
        Long desde = pausadoDesde.remove(uid);
        if (desde == null) return;
        long segundos = Math.max(0, (System.currentTimeMillis() - desde) / 1000);
        db.executar("UPDATE cinema_ativo SET termina_em = DATE_ADD(termina_em, INTERVAL ? SECOND) WHERE usuario_id = ? AND termina_em IS NOT NULL",
                segundos, uid);
        proximaTroca.put(uid, 0L);
        casa.publicar(uid, EventoCasa.Tipo.RETOMADA, "Filme continuando (ficou " + (segundos / 60) + " min em pausa)");
    }

    /**
     * Atmosfera escolhida na mão ("LetterRoyale, modo terror"): vale na hora,
     * com ou sem filme passando. Com filme: as cores do pôster seguem, mas no clima novo.
     */
    public String forcarAtmosfera(long uid, String chave) {
        Atmosfera a = AtmosferaPorGenero.porChave(chave)
                .orElseThrow(() -> new ApiException(404, "Atmosfera não encontrada: " + chave));
        atmosferas.put(uid, a);
        boolean temFilme = ativo(uid);
        if (temFilme) {
            pausadoDesde.remove(uid);
            mudarRitmo(uid, a.ritmo());
            proximaTroca.put(uid, 0L);
        }
        String aviso = casa.aplicarAmbiente(uid, a);   // brilho, cortinas, ar, volume + eventos
        if (!temFilme) {
            try {
                tuya.luzBranca(casa.lampadaDo(uid), a.brilho(), 10);
            } catch (TuyaService.LampadaException e) {
                log.warn(e.getMessage());
            }
        }
        return aviso;
    }

    // ================= ATMOSFERA (gêneros do filme -> clima da sala) =================

    /** Lê os gêneros do filme/série no TMDB (resposta já fica em cache) e escolhe a atmosfera. */
    Atmosfera atmosferaDoFilme(long tmdbId) {
        try {
            JsonNode detalhes = tmdb.tmdb(TmdbService.ehSerie(tmdbId) ? "/tv/" + (-tmdbId) : "/movie/" + tmdbId);
            List<Integer> generos = new ArrayList<>();
            detalhes.path("genres").forEach(g -> generos.add(g.path("id").asInt()));
            return AtmosferaPorGenero.escolher(generos);
        } catch (Exception e) {
            log.warn("Sem gêneros do filme {} ({}): usando atmosfera padrão", tmdbId, e.getMessage());
            return AtmosferaPorGenero.PADRAO;
        }
    }

    private static Map<String, Object> paraMapa(Atmosfera a) {
        return a == null ? null : a.paraMapa();
    }

    /** Atmosfera em uso (null no modo navegando). Recalcula se o servidor reiniciou no meio do filme. */
    private Atmosfera atmosferaAtiva(long uid, Map<String, Object> c) {
        Atmosfera escolhida = atmosferas.get(uid);   // escolhida na mão (voz/tela) vale em qualquer modo
        if (escolhida != null) return escolhida;
        if (!"assistindo".equals(c.get("modo")) || c.get("tmdb_id") == null) return null;
        return atmosferas.computeIfAbsent(uid, k -> atmosferaDoFilme(((Number) c.get("tmdb_id")).longValue()));
    }

    /** Junta temporada/episódio no item (série). Filme fica igual. */
    private static Map<String, Object> comEpisodio(Map<String, Object> f, Map<String, Object> l) {
        Object t = l.get("temporada"), e = l.get("episodio");
        if (t instanceof Number tn && e instanceof Number en && tn.intValue() + en.intValue() > 0) {
            f.put("temporada", tn.intValue());
            f.put("episodio", en.intValue());
            f.put("episodio_titulo", l.get("titulo_ep"));
        }
        return f;
    }

    // ================= FILA ("colocar na fila" enquanto assiste) =================

    /** Filmes (e episódios) da fila, na ordem. */
    public List<Map<String, Object>> fila(long uid) {
        return db.lista("""
                SELECT q.id AS fila_id, q.posicao, q.temporada, q.episodio, q.titulo_ep, q.duracao_ep, c.* FROM fila_cinema q
                  JOIN filmes_cache c ON c.tmdb_id = q.tmdb_id
                 WHERE q.usuario_id = ? ORDER BY q.posicao, q.id
                """, uid).stream().map(l -> {
            Map<String, Object> f = comEpisodio(TmdbService.doCache(l), l);
            f.put("fila_id", l.get("fila_id"));
            f.put("duracao_min", l.get("duracao_ep") != null ? l.get("duracao_ep") : l.get("duracao_min"));
            return f;
        }).toList();
    }

    private Map<String, Object> proximoDaFila(long uid) {
        List<Map<String, Object>> f = fila(uid);
        return f.isEmpty() ? null : f.get(0);
    }

    /** Coloca no fim da fila (se já estiver, não duplica). Série: temporada + episódio. */
    public void entrarNaFila(long uid, long tmdbId, int temporada, int episodio) {
        String tituloEp = null;
        Integer duracaoEp = null;
        if (TmdbService.ehSerie(tmdbId)) {
            if (temporada <= 0 && episodio <= 0) throw new ApiException(400, "Escolha o episódio que vai pra fila.");
            JsonNode ep = tmdb.episodioLancado(tmdbId, temporada, episodio);
            tituloEp = ep.path("name").asText(null);
            if (ep.path("runtime").isNumber()) duracaoEp = ep.path("runtime").asInt();
        } else {
            temporada = 0;
            episodio = 0;
            tmdb.exigirLancado(tmdbId);
        }
        tmdb.garantirNoCache(tmdbId);
        int fim = (int) db.numero("SELECT COALESCE(MAX(posicao), 0) + 1 FROM fila_cinema WHERE usuario_id = ?", uid);
        db.executar("""
                INSERT IGNORE INTO fila_cinema (usuario_id, tmdb_id, temporada, episodio, titulo_ep, duracao_ep, posicao)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, uid, tmdbId, temporada, episodio, tituloEp, duracaoEp, fim);
    }

    public void sairDaFila(long uid, long filaId) {
        db.executar("DELETE FROM fila_cinema WHERE id = ? AND usuario_id = ?", filaId, uid);
    }

    /** Sobe um filme uma posição (troca com o de cima). */
    public void subirNaFila(long uid, long filaId) {
        List<Map<String, Object>> f = db.lista("SELECT id, posicao FROM fila_cinema WHERE usuario_id = ? ORDER BY posicao, id", uid);
        for (int i = 1; i < f.size(); i++) {
            if (((Number) f.get(i).get("id")).longValue() == filaId) {
                Collections.swap(f, i, i - 1);
                break;
            }
        }
        for (int i = 0; i < f.size(); i++) db.executar("UPDATE fila_cinema SET posicao = ? WHERE id = ?", i + 1, f.get(i).get("id"));
    }

    /** Começa o 1º da fila no modo "assistindo" (sai da fila). */
    public void assistirProximo(long uid) {
        Map<String, Object> p = proximoDaFila(uid);
        if (p == null) throw new ApiException(404, "A fila está vazia.");
        int ritmo = db.um("SELECT ritmo_seg FROM cinema_ativo WHERE usuario_id = ?", uid)
                .map(c -> ((Number) c.get("ritmo_seg")).intValue()).orElse(4);
        sairDaFila(uid, ((Number) p.get("fila_id")).longValue());
        limparTerminado(uid);
        iniciar(uid, ((Number) p.get("id")).longValue(), "assistindo", ritmo == 8 ? "calmo" : ritmo == 2 ? "festa" : "normal",
                (Integer) p.get("temporada"), (Integer) p.get("episodio"));
    }

    /** Limpa o aviso de "filme terminou" depois que o site perguntou a opinião. */
    public void limparTerminado(long uid) {
        db.executar("DELETE FROM cinema_ativo WHERE usuario_id = ? AND terminou = TRUE", uid);
    }

    /**
     * Coração do Modo Cinema: roda a cada 1 segundo.
     *  - filme acabou?  -> terminarFilme()
     *  - chegou a hora? -> manda a próxima cor da paleta pra lâmpada
     */
    private long ultimoAvisoBanco = 0;

    @Scheduled(fixedDelay = 1000, initialDelay = 5000)
    public void tick() {
        List<Map<String, Object>> ativos;
        try {
            ativos = buscarAtivos();
        } catch (Exception e) {
            // Banco fora do ar: avisa no máximo 1x por minuto (em vez de encher o terminal)
            if (System.currentTimeMillis() - ultimoAvisoBanco > 60_000) {
                log.warn("Modo Cinema pausado: sem conexão com o banco ({})", e.getMessage());
                ultimoAvisoBanco = System.currentTimeMillis();
            }
            return;
        }
        executarTick(ativos);
    }

    private List<Map<String, Object>> buscarAtivos() {
        return db.lista("""
                SELECT ca.*, (ca.termina_em IS NOT NULL AND ca.termina_em <= NOW()) AS acabou,
                       d.valor AS brilho, d.ligado AS luz_ligada
                  FROM cinema_ativo ca
                  LEFT JOIN dispositivos d ON d.usuario_id = ca.usuario_id AND d.tipo = 'iluminacao'
                 WHERE ca.terminou = FALSE
                """);
    }

    private void executarTick(List<Map<String, Object>> ativos) {
        long agora = System.currentTimeMillis();
        for (Map<String, Object> c : ativos) {
            long uid = ((Number) c.get("usuario_id")).longValue();
            try {
                if (pausadoDesde.containsKey(uid)) continue;   // intervalo: nem troca cor nem acaba o filme
                if (Json.verdade(c.get("acabou"))) {
                    terminarFilme(uid);
                    continue;
                }
                if (c.get("tmdb_id") == null || !Json.verdade(c.get("luz_ligada"))) continue;
                if (agora < proximaTroca.getOrDefault(uid, 0L)) continue;

                List<String> paleta = paletas.paleta(((Number) c.get("tmdb_id")).longValue());
                int idx = ((Number) c.get("indice_cor")).intValue();
                String cor = paleta.get(Math.floorMod(idx, paleta.size()));
                int brilho = c.get("brilho") == null ? BRILHO_INICIAL : ((Number) c.get("brilho")).intValue();

                db.executar("UPDATE cinema_ativo SET indice_cor = ? WHERE usuario_id = ?", idx + 1, uid);
                proximaTroca.put(uid, agora + ((Number) c.get("ritmo_seg")).intValue() * 1000L);
                Atmosfera atm = atmosferaAtiva(uid, c);   // terror pulsa, ação dá clarões...
                int brilhoAgora = atm == null ? Math.max(5, brilho) : EfeitoLuz.brilho(atm.efeito(), brilho, idx);
                tuya.cor(casa.lampadaDo(uid), cor, brilhoAgora);
            } catch (TuyaService.LampadaException e) {
                log.warn("Modo Cinema: {}", e.getMessage());
                proximaTroca.put(uid, agora + 10_000);   // lâmpada fora? tenta de novo em 10s
            } catch (Exception e) {
                log.warn("Modo Cinema (usuário {}): {}", uid, e.getMessage());
            }
        }
    }
}
