package br.com.letterroyale.tmdb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TituloSagaTest {

    @Test
    @DisplayName("Pega o nome da saga antes dos dois-pontos")
    void prefixo() {
        assertEquals("Jogos Vorazes", TituloSaga.prefixo("Jogos Vorazes: Amanhecer na Colheita"));
        assertEquals("The Hunger Games", TituloSaga.prefixo("The Hunger Games: Sunrise on the Reaping"));
        assertEquals("Duna", TituloSaga.prefixo("Duna: Parte Dois"));
        assertEquals("Velozes e Furiosos", TituloSaga.prefixo("Velozes e Furiosos - Desafio em Tóquio"));
    }

    @Test
    @DisplayName("Filme sem subtítulo não vira saga pelo nome")
    void semSaga() {
        assertNull(TituloSaga.prefixo("Matrix"));
        assertNull(TituloSaga.prefixo(null));
        assertNull(TituloSaga.prefixo("X: O Filme"));   // curto demais, acharia qualquer coisa
    }

    @Test
    @DisplayName("Confere se a coleção achada é mesmo da saga")
    void mesmaSaga() {
        assertTrue(TituloSaga.mesmaSaga("Jogos Vorazes", "Jogos Vorazes - Coleção"));
        assertTrue(TituloSaga.mesmaSaga("The Hunger Games", "The Hunger Games Collection"));
        assertTrue(TituloSaga.mesmaSaga("Duna", "Duna (Coleção)"));
        assertFalse(TituloSaga.mesmaSaga("Jogos Vorazes", "Jogos Mortais - Coleção"));
        assertFalse(TituloSaga.mesmaSaga("", "Qualquer coisa"));
    }
}
