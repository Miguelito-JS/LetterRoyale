package br.com.letterroyale.tmdb;

import br.com.letterroyale.tmdb.CuradoriaSemelhantes.Item;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CuradoriaSemelhantesTest {

    static final LocalDate HOJE = LocalDate.of(2026, 11, 24);

    @Test
    @DisplayName("Homem-Aranha: tira os filmes esquecidos de 1977/78 e deixa a trilogia famosa em ordem")
    void tiraEsquecidos() {
        List<Item> partes = List.of(
                new Item(1, 120, "1977-09-14"),     // Homem-Aranha (1977)
                new Item(2, 60, "1978-06-01"),      // Spider-Man Strikes Back
                new Item(4, 19000, "2004-06-25"),   // Homem-Aranha 2
                new Item(3, 20000, "2002-05-01"),   // Homem-Aranha
                new Item(5, 15000, "2007-05-01"));  // Homem-Aranha 3
        List<Long> ids = CuradoriaSemelhantes.relevantesDaSaga(partes, HOJE).stream().map(Item::id).toList();
        assertEquals(List.of(3L, 4L, 5L), ids);
    }

    @Test
    @DisplayName("Continuação anunciada (ainda sem votos) entra no fim")
    void continuacaoAnunciada() {
        List<Item> partes = List.of(new Item(1, 9000, "2019-07-02"), new Item(2, 0, "2027-07-30"), new Item(3, 8000, "2017-07-05"));
        List<Long> ids = CuradoriaSemelhantes.relevantesDaSaga(partes, HOJE).stream().map(Item::id).toList();
        assertEquals(List.of(3L, 1L, 2L), ids);
    }

    @Test
    @DisplayName("Saga pequena: o corte mínimo é 200 votos")
    void sagaPequena() {
        List<Item> partes = List.of(new Item(1, 900, "2010-01-01"), new Item(2, 150, "2012-01-01"), new Item(3, 210, "2014-01-01"));
        assertEquals(List.of(1L, 3L), CuradoriaSemelhantes.relevantesDaSaga(partes, HOJE).stream().map(Item::id).toList());
    }

    @Test
    @DisplayName("Filmes do ator: só os conhecidos, mais votados primeiro")
    void doAtor() {
        List<Item> filmes = List.of(new Item(1, 300, "2001-01-01"), new Item(2, 12000, "2010-01-01"), new Item(3, 5000, "2015-01-01"));
        assertEquals(List.of(2L, 3L), CuradoriaSemelhantes.doAtor(filmes, 6).stream().map(Item::id).toList());
    }
}
