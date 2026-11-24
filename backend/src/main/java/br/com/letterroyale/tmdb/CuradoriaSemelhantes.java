package br.com.letterroyale.tmdb;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * =====================================================================
 *  CURADORIA DOS "SEMELHANTES": só entra o que é RELEVANTE.
 *
 *  Buscar a saga pelo nome ("Homem-Aranha") também traz coisa esquecida
 *  (filmes de 1977 que quase ninguém viu). Regra:
 *    - filme que ainda vai estrear: entra (é a continuação anunciada)
 *    - filme que já saiu: só entra se tiver votos de verdade no TMDB,
 *      pelo menos 5% dos votos do filme mais famoso da saga (e no mínimo 200)
 *  Depois, em ordem de lançamento (do primeiro ao último da sequência).
 *
 *  Classe sem Spring e sem internet: testada com JUnit.
 * =====================================================================
 */
public final class CuradoriaSemelhantes {

    /** Um filme da saga com o que precisamos pra decidir. data = "2002-05-01" (ou vazio). */
    public record Item(long id, int votos, String data) {
    }

    static final int MINIMO_DE_VOTOS = 200;
    static final double PARTE_DO_MAIS_FAMOSO = 0.05;

    private CuradoriaSemelhantes() {
    }

    /** Filmes relevantes da saga, em ordem de lançamento. */
    public static List<Item> relevantesDaSaga(List<Item> partes, LocalDate hoje) {
        int maisVotos = 0;
        for (Item i : partes) maisVotos = Math.max(maisVotos, i.votos());
        int corte = Math.max(MINIMO_DE_VOTOS, (int) Math.round(maisVotos * PARTE_DO_MAIS_FAMOSO));
        List<Item> r = new ArrayList<>();
        for (Item i : partes) {
            boolean vaiEstrear = i.data() != null && !i.data().isBlank() && i.data().compareTo(hoje.toString()) > 0;
            if (vaiEstrear || i.votos() >= corte) r.add(i);
        }
        r.sort(Comparator.comparing((Item i) -> i.data() == null || i.data().isBlank() ? "9999" : i.data()));
        return r;
    }

    /** Filmes do ator principal que valem mostrar: conhecidos (votos) e os mais populares primeiro. */
    public static List<Item> doAtor(List<Item> filmes, int max) {
        return filmes.stream().filter(i -> i.votos() >= 500)
                .sorted(Comparator.comparingInt((Item i) -> -i.votos())).limit(max).toList();
    }
}
