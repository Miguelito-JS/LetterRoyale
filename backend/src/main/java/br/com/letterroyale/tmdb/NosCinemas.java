package br.com.letterroyale.tmdb;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * =====================================================================
 *  "Onde assistir" quando o filme NÃO está em nenhum streaming:
 *  ele pode estar EM CARTAZ nos cinemas ou ESTREANDO em breve.
 *
 *    estreia no futuro            -> EM_BREVE   ("Estreia nos cinemas em 20/11")
 *    estreou há até 75 dias       -> EM_CARTAZ  ("Em cartaz nos cinemas")
 *    estreou há mais tempo        -> nada (o site mostra "não está em streaming")
 *
 *  A data vem do TMDB (release_dates do Brasil, tipo 2 = limitado, 3 = cinema).
 *  Classe sem Spring: testada com JUnit puro.
 * =====================================================================
 */
public final class NosCinemas {

    public enum Situacao { EM_BREVE, EM_CARTAZ }

    /** Filmes costumam ficar ~2 meses e meio em cartaz no Brasil. */
    public static final int DIAS_EM_CARTAZ = 75;

    private NosCinemas() {
    }

    /** null = nem em cartaz nem estreando (ou sem data). */
    public static Situacao situacao(LocalDate hoje, LocalDate estreia) {
        if (hoje == null || estreia == null) return null;
        if (estreia.isAfter(hoje)) return Situacao.EM_BREVE;
        if (!estreia.isBefore(hoje.minusDays(DIAS_EM_CARTAZ))) return Situacao.EM_CARTAZ;
        return null;
    }

    /** "2026-11-20T00:00:00.000Z" ou "2026-11-20" -> LocalDate (null se vier vazio/estranho). */
    public static LocalDate data(String texto) {
        if (texto == null || texto.length() < 10) return null;
        try {
            return LocalDate.parse(texto.substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Link pra achar sessões e comprar ingresso (busca do Google mostra os horários dos cinemas perto). */
    public static String linkSessoes(String titulo) {
        return "https://www.google.com/search?q=" + URLEncoder.encode("ingressos " + titulo + " cinema sessões", StandardCharsets.UTF_8);
    }

    /** Resposta pronta para a tela (ou null). */
    public static Map<String, Object> info(LocalDate hoje, LocalDate estreia, String titulo) {
        Situacao s = situacao(hoje, estreia);
        if (s == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("situacao", s.name().toLowerCase());
        m.put("estreia", estreia.toString());
        m.put("link_sessoes", linkSessoes(titulo));
        return m;
    }
}
