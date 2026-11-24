package br.com.letterroyale.tmdb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class NosCinemasTest {
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 23);

    @Test
    @DisplayName("Estreia no futuro = EM BREVE")
    void emBreve() {
        assertEquals(NosCinemas.Situacao.EM_BREVE, NosCinemas.situacao(HOJE, LocalDate.of(2026, 12, 18)));
    }

    @Test
    @DisplayName("Estreou há pouco (até 75 dias, inclusive hoje) = EM CARTAZ")
    void emCartaz() {
        assertEquals(NosCinemas.Situacao.EM_CARTAZ, NosCinemas.situacao(HOJE, HOJE));
        assertEquals(NosCinemas.Situacao.EM_CARTAZ, NosCinemas.situacao(HOJE, LocalDate.of(2026, 11, 20)));
        assertEquals(NosCinemas.Situacao.EM_CARTAZ, NosCinemas.situacao(HOJE, HOJE.minusDays(75)));
    }

    @Test
    @DisplayName("Estreou faz tempo ou sem data = nada")
    void antigoOuSemData() {
        assertNull(NosCinemas.situacao(HOJE, HOJE.minusDays(76)));
        assertNull(NosCinemas.situacao(HOJE, null));
    }

    @Test
    @DisplayName("Lê a data do TMDB nos dois formatos")
    void datas() {
        assertEquals(LocalDate.of(2026, 11, 20), NosCinemas.data("2026-11-20T00:00:00.000Z"));
        assertEquals(LocalDate.of(2026, 11, 20), NosCinemas.data("2026-11-20"));
        assertNull(NosCinemas.data(""));
        assertNull(NosCinemas.data("ontem!!!!!!"));
    }

    @Test
    @DisplayName("Resposta pronta pra tela com link de sessões")
    void info() {
        var m = NosCinemas.info(HOJE, LocalDate.of(2026, 11, 20), "Jogos Vorazes: Amanhecer na Colheita");
        assertEquals("em_cartaz", m.get("situacao"));
        assertEquals("2026-11-20", m.get("estreia"));
        assertTrue(((String) m.get("link_sessoes")).startsWith("https://www.google.com/search?q=ingressos+Jogos+Vorazes"));
        assertNull(NosCinemas.info(HOJE, HOJE.minusYears(3), "Velho"));
    }
}
