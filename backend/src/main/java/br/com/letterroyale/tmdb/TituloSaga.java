package br.com.letterroyale.tmdb;

import java.text.Normalizer;
import java.util.Locale;

/**
 * =====================================================================
 *  Descobre o nome da SAGA pelo título do filme, pra achar a coleção
 *  quando o TMDB ainda não ligou um filme novo à coleção dele.
 *
 *    "Jogos Vorazes: Amanhecer na Colheita"  -> "Jogos Vorazes"
 *    "The Hunger Games: Sunrise on the Reaping" -> "The Hunger Games"
 *    "Duna: Parte Dois"                       -> "Duna"
 *    "Matrix"  (sem dois-pontos)              -> null (não é saga pelo nome)
 *
 *  E confere se uma coleção encontrada é mesmo da saga:
 *    "Jogos Vorazes" + "Jogos Vorazes - Coleção" -> sim
 *  Classe sem Spring: testada com JUnit puro.
 * =====================================================================
 */
public final class TituloSaga {

    private TituloSaga() {
    }

    /** Parte do título antes de ":" ou " - " (null se não tiver ou for curta demais). */
    public static String prefixo(String titulo) {
        if (titulo == null) return null;
        int corte = -1;
        for (String sep : new String[]{":", " - ", " – "}) {
            int i = titulo.indexOf(sep);
            if (i > 0 && (corte < 0 || i < corte)) corte = i;
        }
        if (corte < 0) return null;
        String p = titulo.substring(0, corte).trim();
        return p.length() >= 3 ? p : null;
    }

    /** A coleção encontrada começa com o nome da saga? (sem acento, maiúscula ou "The"/"O"/"A") */
    public static boolean mesmaSaga(String saga, String nomeColecao) {
        String a = normalizar(saga), b = normalizar(nomeColecao);
        return !a.isEmpty() && b.startsWith(a);
    }

    static String normalizar(String s) {
        if (s == null) return "";
        String t = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        t = t.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        return t.replaceFirst("^(the|o|a|os|as) ", "");
    }
}
