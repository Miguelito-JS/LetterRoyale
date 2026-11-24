package br.com.letterroyale.casa.atmosfera;

import br.com.letterroyale.casa.atmosfera.EfeitoLuz.Efeito;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * =====================================================================
 *  Escolhe a ATMOSFERA da sala pelos GÊNEROS do filme (ids do TMDB).
 *
 *  Um filme tem vários gêneros (Matrix = Ação + Ficção científica).
 *  A lista ATMOSFERAS está em ORDEM DE PRIORIDADE: ganha a primeira
 *  que bate com algum gênero do filme. Ex.:
 *    • Invocação do Mal (Terror, Suspense)          -> Terror
 *    • Interestelar (Aventura, Drama, Ficção)       -> Ficção e aventura
 *    • Shrek (Animação, Comédia, Família)           -> Animação
 *    • filme sem gênero conhecido                   -> Cinema clássico
 *
 *  Os ids de SÉRIE no TMDB são outros (10759 = Ação e Aventura de TV...),
 *  por isso aparecem junto. Classe sem Spring: testada com JUnit puro.
 *  Para criar uma atmosfera nova: some uma linha na lista. Nada mais muda.
 * =====================================================================
 */
public final class AtmosferaPorGenero {

    /** Uma atmosfera + os gêneros do TMDB que levam até ela. */
    record Regra(Atmosfera atmosfera, Set<Integer> generos) {
    }

    public static final Atmosfera PADRAO = new Atmosfera("classico", "Cinema clássico",
            "Luz baixa e constante, cortinas fechadas", 30, "normal", Efeito.CONSTANTE, "fechadas", 22, 50);

    static final List<Regra> ATMOSFERAS = List.of(
            new Regra(new Atmosfera("terror", "Terror",
                    "Luz bem baixa pulsando como um coração acelerado", 15, "calmo", Efeito.PULSAR, "fechadas", 20, 55),
                    Set.of(27)),
            new Regra(new Atmosfera("suspense", "Suspense",
                    "Penumbra que respira devagar, ar mais frio", 20, "calmo", Efeito.RESPIRAR, "fechadas", 21, 50),
                    Set.of(53, 9648, 80)),                       // suspense, mistério, crime
            new Regra(new Atmosfera("acao", "Ação",
                    "Cores trocando rápido com um clarão de explosão", 45, "festa", Efeito.RELAMPAGO, "fechadas", 20, 60),
                    Set.of(28, 10752, 37, 10759, 10768)),        // ação, guerra, faroeste (+ séries)
            new Regra(new Atmosfera("ficcao", "Ficção e aventura",
                    "Cores do pôster respirando, clima de outro mundo", 35, "normal", Efeito.RESPIRAR, "fechadas", 21, 55),
                    Set.of(878, 14, 12, 10765)),                 // ficção, fantasia, aventura (+ série)
            new Regra(new Atmosfera("romance", "Romance",
                    "Luz quente e baixa, quase à luz de velas", 25, "calmo", Efeito.RESPIRAR, "fechadas", 23, 40),
                    Set.of(10749)),
            new Regra(new Atmosfera("musical", "Música",
                    "Cores no ritmo de festa", 50, "festa", Efeito.CONSTANTE, "meio", 22, 60),
                    Set.of(10402)),
            new Regra(new Atmosfera("animacao", "Animação",
                    "Cores vivas trocando devagar, sala clara", 60, "normal", Efeito.RESPIRAR, "meio", 23, 50),
                    Set.of(16)),                                 // animação
            new Regra(new Atmosfera("familia", "Família",
                    "Sala clara e aconchegante pra todo mundo", 55, "normal", Efeito.CONSTANTE, "meio", 23, 45),
                    Set.of(10751, 10762)),                       // família, infantil (série)
            new Regra(new Atmosfera("comedia", "Comédia",
                    "Luz alegre e clara, cortinas a meia altura", 60, "normal", Efeito.CONSTANTE, "meio", 23, 45),
                    Set.of(35)),
            new Regra(new Atmosfera("documentario", "Documentário",
                    "Luz neutra pra prestar atenção", 50, "calmo", Efeito.CONSTANTE, "meio", 22, 40),
                    Set.of(99, 10763, 10764)),                   // documentário, notícias, reality
            new Regra(new Atmosfera("drama", "Drama",
                    "Luz baixa e estável, sem distração", 30, "calmo", Efeito.CONSTANTE, "fechadas", 22, 45),
                    Set.of(18, 36, 10766, 10767, 10770))         // drama, história, novela, talk, filme de TV
    );

    private static final Map<String, Atmosfera> POR_CHAVE = mapaPorChave();

    private AtmosferaPorGenero() {
    }

    /** Atmosfera para estes gêneros (ids do TMDB). Lista vazia/null = PADRAO. */
    public static Atmosfera escolher(Collection<Integer> generos) {
        if (generos == null || generos.isEmpty()) return PADRAO;
        for (Regra r : ATMOSFERAS) {
            for (Integer g : generos) {
                if (g != null && r.generos().contains(g)) return r.atmosfera();
            }
        }
        return PADRAO;
    }

    /** Atmosfera pela chave ("terror", "acao"...). */
    public static Optional<Atmosfera> porChave(String chave) {
        return Optional.ofNullable(chave == null ? null : POR_CHAVE.get(chave));
    }

    /** Todas as atmosferas (para a documentação e para a tela). */
    public static List<Atmosfera> todas() {
        return java.util.stream.Stream.concat(ATMOSFERAS.stream().map(Regra::atmosfera), java.util.stream.Stream.of(PADRAO)).toList();
    }

    private static Map<String, Atmosfera> mapaPorChave() {
        Map<String, Atmosfera> m = new java.util.HashMap<>();
        for (Atmosfera a : todas()) {
            if (m.put(a.chave(), a) != null) throw new IllegalStateException("Chave de atmosfera repetida: " + a.chave());
        }
        return Map.copyOf(m);
    }
}
