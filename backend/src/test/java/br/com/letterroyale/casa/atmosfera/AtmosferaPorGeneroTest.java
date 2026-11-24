package br.com.letterroyale.casa.atmosfera;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Gêneros do TMDB -> atmosfera da sala. Ids: https://developer.themoviedb.org/reference/genre-movie-list */
class AtmosferaPorGeneroTest {

    private static String chave(Integer... generos) {
        return AtmosferaPorGenero.escolher(List.of(generos)).chave();
    }

    @Test
    @DisplayName("Terror ganha de qualquer outro gênero (ex.: Invocação do Mal = Terror + Suspense)")
    void terrorTemPrioridade() {
        assertEquals("terror", chave(27));
        assertEquals("terror", chave(53, 27));
        assertEquals("terror", chave(35, 27));   // comédia de terror continua terror
    }

    @Test
    @DisplayName("Filmes conhecidos caem na atmosfera certa")
    void filmesConhecidos() {
        assertEquals("acao", chave(28, 878));              // Matrix: Ação + Ficção
        assertEquals("ficcao", chave(12, 18, 878));        // Interestelar: Aventura + Drama + Ficção
        assertEquals("animacao", chave(16, 35, 10751));    // Shrek: Animação + Comédia + Família
        assertEquals("suspense", chave(80, 18));           // Coringa: Crime + Drama
        assertEquals("romance", chave(10749, 18));         // Romance + Drama
        assertEquals("drama", chave(18));
        assertEquals("documentario", chave(99));
    }

    @Test
    @DisplayName("Séries usam ids próprios do TMDB e também funcionam")
    void generosDeSerie() {
        assertEquals("acao", chave(10759));     // Action & Adventure (série)
        assertEquals("ficcao", chave(10765));   // Sci-Fi & Fantasy (série)
        assertEquals("familia", chave(10762));  // Kids (série)
    }

    @Test
    @DisplayName("Sem gênero ou gênero desconhecido = Cinema clássico (nunca quebra)")
    void semGenero() {
        assertSame(AtmosferaPorGenero.PADRAO, AtmosferaPorGenero.escolher(null));
        assertSame(AtmosferaPorGenero.PADRAO, AtmosferaPorGenero.escolher(List.of()));
        assertSame(AtmosferaPorGenero.PADRAO, AtmosferaPorGenero.escolher(List.of(123456)));
        assertSame(AtmosferaPorGenero.PADRAO, AtmosferaPorGenero.escolher(java.util.Arrays.asList((Integer) null)));
    }

    @Test
    @DisplayName("Terror: luz bem baixa, pulsando, cortinas fechadas")
    void terrorComportamento() {
        Atmosfera t = AtmosferaPorGenero.porChave("terror").orElseThrow();
        assertTrue(t.brilho() <= 20, "terror tem que ser escuro");
        assertEquals(EfeitoLuz.Efeito.PULSAR, t.efeito());
        assertEquals("fechadas", t.cortinas());
    }

    @Test
    @DisplayName("Todas as atmosferas têm valores que os aparelhos aceitam")
    void todasValidas() {
        Set<String> ritmos = Set.of("calmo", "normal", "festa");
        Set<String> cortinas = Set.of("abertas", "meio", "fechadas");
        Set<String> chaves = new HashSet<>();
        for (Atmosfera a : AtmosferaPorGenero.todas()) {
            assertTrue(chaves.add(a.chave()), "chave repetida: " + a.chave());
            assertTrue(ritmos.contains(a.ritmo()), a.chave() + ": ritmo inválido");
            assertTrue(cortinas.contains(a.cortinas()), a.chave() + ": cortinas inválidas");
            assertTrue(a.brilho() >= 5 && a.brilho() <= 100, a.chave() + ": brilho");
            assertTrue(a.volume() >= 0 && a.volume() <= 100, a.chave() + ": volume");
            assertTrue(a.temperaturaAr() >= 16 && a.temperaturaAr() <= 30, a.chave() + ": ar");
            assertFalse(a.nome().isBlank());
            assertFalse(a.descricao().isBlank());
        }
        assertEquals(12, chaves.size());
    }

    @Test
    @DisplayName("porChave acha as atmosferas e não quebra com chave errada")
    void porChave() {
        assertTrue(AtmosferaPorGenero.porChave("acao").isPresent());
        assertTrue(AtmosferaPorGenero.porChave("classico").isPresent());
        assertTrue(AtmosferaPorGenero.porChave("nao-existe").isEmpty());
        assertTrue(AtmosferaPorGenero.porChave(null).isEmpty());
    }

    @Test
    @DisplayName("Atmosfera rejeita valores que a lâmpada/ar não aceitam")
    void validacao() {
        assertThrows(IllegalArgumentException.class, () ->
                new Atmosfera("x", "X", "x", 2, "normal", EfeitoLuz.Efeito.CONSTANTE, "meio", 22, 50));
        assertThrows(IllegalArgumentException.class, () ->
                new Atmosfera("x", "X", "x", 50, "normal", EfeitoLuz.Efeito.CONSTANTE, "meio", 40, 50));
        assertThrows(IllegalArgumentException.class, () ->
                new Atmosfera("", "X", "x", 50, "normal", EfeitoLuz.Efeito.CONSTANTE, "meio", 22, 50));
    }

    @Test
    @DisplayName("paraMapa tem tudo que a tela precisa")
    void paraMapa() {
        var m = AtmosferaPorGenero.PADRAO.paraMapa();
        assertEquals(Set.of("chave", "nome", "descricao", "brilho", "ritmo", "efeito", "cortinas", "temperatura_ar", "volume"), m.keySet());
        assertEquals("constante", m.get("efeito"));
    }
}
