package br.com.letterroyale.notificacao;

import br.com.letterroyale.tmdb.TmdbService;
import br.com.letterroyale.util.Db;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * =====================================================================
 *  NOTIFICAÇÕES (o sininho do topo)
 *
 *  Quem cria uma notificação chama criar(...). Tipos:
 *   seguiu      -> alguém começou a seguir você
 *   review      -> alguém que você segue avaliou um título do seu Favoritos / Quero assistir
 *   curtida     -> curtiram sua review ("Ana e mais 3 curtiram...")
 *   estreou     -> um título do seu "Quero assistir" acabou de estrear
 *   episodio    -> saiu episódio novo de uma série que você está assistindo
 *   cinema      -> acabou o filme/episódio do Modo Cinema: que tal avaliar?
 *   lista       -> alguém salvou uma lista sua
 *
 *  "chave" evita repetir a mesma notificação (ex.: "estreou:155" só 1 vez por pessoa).
 * =====================================================================
 */
@Service
public class NotificacaoService {
    private static final Logger log = LoggerFactory.getLogger(NotificacaoService.class);
    private final Db db;
    private final TmdbService tmdb;

    public NotificacaoService(Db db, TmdbService tmdb) {
        this.db = db;
        this.tmdb = tmdb;
    }

    /**
     * Cria uma notificação para "uid". atorId = quem fez a ação (null = o próprio site).
     * Ninguém recebe notificação de algo que ele mesmo fez. Com "chave", não repete.
     */
    public void criar(long uid, String tipo, String texto, String link, Long atorId, Long tmdbId, String chave) {
        if (atorId != null && atorId == uid) return;
        try {
            db.executar("""
                    INSERT IGNORE INTO notificacoes (usuario_id, tipo, texto, link, ator_id, tmdb_id, chave)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, uid, tipo, texto.length() > 255 ? texto.substring(0, 252) + "..." : texto, link, atorId, tmdbId, chave);
        } catch (Exception e) {
            // Notificação nunca pode derrubar a ação principal (seguir, curtir...)
            log.warn("Não consegui criar a notificação {}: {}", tipo, e.getMessage());
        }
    }

    private String arroba(long uid) {
        return db.um("SELECT username FROM usuarios WHERE id = ?", uid).map(u -> "@" + u.get("username")).orElse("Alguém");
    }

    private String titulo(long tmdbId) {
        return db.um("SELECT titulo FROM filmes_cache WHERE tmdb_id = ?", tmdbId).map(f -> (String) f.get("titulo")).orElse("um título");
    }

    // ------------------------------------------------------------ gatilhos

    /** Alguém começou a seguir "alvo". */
    public void seguiu(long seguidor, long alvo) {
        String quem = arroba(seguidor);
        criar(alvo, "seguiu", quem + " começou a seguir você.", "#/perfil/" + quem.substring(1), seguidor, null, null);
    }

    /** Review NOVA de "autor": avisa quem segue o autor e tem o título no Favoritos ou no Quero assistir. */
    public void reviewDeAmigo(long autor, long tmdbId, long reviewId) {
        String quem = arroba(autor), t = titulo(tmdbId);
        List<Map<String, Object>> interessados = db.lista("""
                SELECT DISTINCT s.seguidor_id AS uid,
                       EXISTS(SELECT 1 FROM favoritos f WHERE f.usuario_id = s.seguidor_id AND f.tmdb_id = ?) AS favorito
                  FROM seguidores s
                 WHERE s.usuario_id = ?
                   AND (EXISTS(SELECT 1 FROM favoritos f WHERE f.usuario_id = s.seguidor_id AND f.tmdb_id = ?)
                     OR EXISTS(SELECT 1 FROM assistidos a WHERE a.usuario_id = s.seguidor_id AND a.tmdb_id = ? AND a.status = 'quero_assistir'))
                """, tmdbId, autor, tmdbId, tmdbId);
        for (Map<String, Object> i : interessados) {
            boolean fav = ((Number) i.get("favorito")).intValue() == 1;
            criar(((Number) i.get("uid")).longValue(), "review",
                    quem + " escreveu uma review de " + t + (fav ? ", um dos seus favoritos." : ", que está no seu Quero assistir."),
                    "#/filme/" + tmdbId + "/avaliacoes", autor, tmdbId, "review:" + reviewId);
        }
    }

    /** Alguém curtiu a review "reviewId". Junta tudo numa notificação só enquanto ela não for lida. */
    public void curtida(long quemCurtiu, long reviewId) {
        Map<String, Object> r = db.um("SELECT usuario_id, tmdb_id FROM reviews WHERE id = ?", reviewId).orElse(null);
        if (r == null) return;
        long dono = ((Number) r.get("usuario_id")).longValue();
        if (dono == quemCurtiu) return;
        long tmdbId = ((Number) r.get("tmdb_id")).longValue();
        long outros = db.numero("SELECT COUNT(*) FROM review_curtidas WHERE review_id = ? AND usuario_id <> ?", reviewId, quemCurtiu);
        String texto = arroba(quemCurtiu) + (outros > 0 ? " e mais " + outros + (outros == 1 ? " pessoa curtiram" : " pessoas curtiram") : " curtiu")
                + " sua review de " + titulo(tmdbId) + ".";
        // Ainda tem uma não lida dessa review? Atualiza ela (e sobe pro topo) em vez de criar outra
        int mudou = db.executar("""
                UPDATE notificacoes SET texto = ?, ator_id = ?, criado_em = NOW()
                 WHERE usuario_id = ? AND tipo = 'curtida' AND chave = ? AND lida = FALSE
                """, texto, quemCurtiu, dono, "curtida:" + reviewId);
        if (mudou == 0) {
            // Já lida: libera a chave pra uma nova
            db.executar("UPDATE notificacoes SET chave = NULL WHERE usuario_id = ? AND chave = ?", dono, "curtida:" + reviewId);
            criar(dono, "curtida", texto, "#/filme/" + tmdbId + "/avaliacoes", quemCurtiu, tmdbId, "curtida:" + reviewId);
        }
    }

    /** Alguém curtiu a conversa "conversaId". Junta numa notificação só enquanto não for lida (igual à review). */
    public void curtidaConversa(long quemCurtiu, long conversaId) {
        Map<String, Object> c = db.um("SELECT usuario_id, texto FROM conversas WHERE id = ?", conversaId).orElse(null);
        if (c == null) return;
        long dono = ((Number) c.get("usuario_id")).longValue();
        if (dono == quemCurtiu) return;
        String trecho = (String) c.get("texto");
        if (trecho.length() > 40) trecho = trecho.substring(0, 37) + "...";
        long outros = db.numero("SELECT COUNT(*) FROM conversa_curtidas WHERE conversa_id = ? AND usuario_id <> ?", conversaId, quemCurtiu);
        String texto = arroba(quemCurtiu) + (outros > 0 ? " e mais " + outros + (outros == 1 ? " pessoa curtiram" : " pessoas curtiram") : " curtiu")
                + " sua conversa: \"" + trecho + "\"";
        String chave = "curtida-conversa:" + conversaId;
        int mudou = db.executar("""
                UPDATE notificacoes SET texto = ?, ator_id = ?, criado_em = NOW()
                 WHERE usuario_id = ? AND tipo = 'curtida' AND chave = ? AND lida = FALSE
                """, texto, quemCurtiu, dono, chave);
        if (mudou == 0) {
            db.executar("UPDATE notificacoes SET chave = NULL WHERE usuario_id = ? AND chave = ?", dono, chave);
            criar(dono, "curtida", texto, "#/reviews?filtro=conversas&c=" + conversaId, quemCurtiu, null, chave);
        }
    }

    /** Alguém salvou a lista "listaId". */
    public void listaSalva(long quemSalvou, long listaId) {
        db.um("SELECT usuario_id, nome FROM listas WHERE id = ?", listaId).ifPresent(l ->
                criar(((Number) l.get("usuario_id")).longValue(), "lista",
                        arroba(quemSalvou) + " salvou sua lista \"" + l.get("nome") + "\".",
                        "#/lista/" + listaId, quemSalvou, null, "lista:" + listaId + ":" + quemSalvou));
    }

    /** Acabou o filme ou o episódio no Modo Cinema. */
    public void fimDoCinema(long uid, long tmdbId, Integer temporada, Integer episodio) {
        String t = titulo(tmdbId);
        String oque = temporada != null && episodio != null ? "T" + temporada + "E" + episodio + " de " + t : t;
        criar(uid, "cinema", "Você terminou " + oque + ". Que tal avaliar?", "#/filme/" + tmdbId + "/review", null, tmdbId, null);
    }

    // ------------------------------------------------------------ o que roda sozinho

    /**
     * A cada 6 horas: "Estreou!" (Quero assistir) e "Episódio novo" (séries que você está assistindo).
     * Só avisa estreias/episódios dos últimos 7 dias, e cada um 1 vez só (chave).
     */
    @Scheduled(fixedDelay = 6 * 60 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void verificarLancamentos() {
        LocalDate hoje = LocalDate.now(), semanaPassada = hoje.minusDays(7);
        try {
            // Filmes e séries no "Quero assistir" (no máximo 200 títulos por rodada)
            for (Map<String, Object> f : db.lista("SELECT DISTINCT tmdb_id FROM assistidos WHERE status = 'quero_assistir' LIMIT 200")) {
                long id = ((Number) f.get("tmdb_id")).longValue();
                JsonNode d = TmdbService.ehSerie(id) ? tmdb.tmdb("/tv/" + (-id)) : tmdb.tmdb("/movie/" + id);
                String data = d.path(TmdbService.ehSerie(id) ? "first_air_date" : "release_date").asText("");
                if (data.length() < 10) continue;
                LocalDate estreia = LocalDate.parse(data);
                if (estreia.isAfter(hoje) || estreia.isBefore(semanaPassada)) continue;
                for (Map<String, Object> u : db.lista("SELECT usuario_id FROM assistidos WHERE status = 'quero_assistir' AND tmdb_id = ?", id)) {
                    criar(((Number) u.get("usuario_id")).longValue(), "estreou",
                            "Estreou! " + titulo(id) + ", do seu Quero assistir, já está disponível.",
                            "#/filme/" + id, null, id, "estreou:" + id);
                }
            }
            // Séries que alguém está assistindo: episódio novo?
            for (Map<String, Object> f : db.lista("SELECT DISTINCT tmdb_id FROM assistidos WHERE status = 'assistindo' AND tmdb_id < 0 LIMIT 200")) {
                long id = ((Number) f.get("tmdb_id")).longValue();
                JsonNode ep = tmdb.tmdb("/tv/" + (-id)).path("last_episode_to_air");
                String data = ep.path("air_date").asText("");
                if (data.length() < 10) continue;
                LocalDate saiu = LocalDate.parse(data);
                if (saiu.isAfter(hoje) || saiu.isBefore(semanaPassada)) continue;
                int t = ep.path("season_number").asInt(), e = ep.path("episode_number").asInt();
                for (Map<String, Object> u : db.lista("SELECT usuario_id FROM assistidos WHERE status = 'assistindo' AND tmdb_id = ?", id)) {
                    criar(((Number) u.get("usuario_id")).longValue(), "episodio",
                            "Episódio novo de " + titulo(id) + ": T" + t + "E" + e + " \"" + ep.path("name").asText("") + "\".",
                            "#/filme/" + id, null, id, "ep:" + id + ":" + t + ":" + e);
                }
            }
        } catch (Exception ex) {
            log.warn("Não consegui verificar estreias e episódios novos: {}", ex.getMessage());
        }
    }
}
