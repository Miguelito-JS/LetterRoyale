package br.com.letterroyale.casa.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VotacaoTest {

    private static final Votacao.Opcao DUNA = new Votacao.Opcao(1, "Duna", "d.jpg");
    private static final Votacao.Opcao MATRIX = new Votacao.Opcao(2, "Matrix", "m.jpg");
    private static final Votacao.Opcao SHREK = new Votacao.Opcao(3, "Shrek", "s.jpg");

    private static Votacao nova() {
        return new Votacao("ABC23", 7, "ana", List.of(DUNA, MATRIX, SHREK), 0);
    }

    @Test
    @DisplayName("Ganha o filme com mais votos")
    void maisVotado() {
        Votacao v = nova();
        v.votar("cel-1", 2);
        v.votar("cel-2", 2);
        v.votar("cel-3", 1);
        assertEquals(MATRIX, v.vencedor());
        assertEquals(2, v.contagem().get(2L));
    }

    @Test
    @DisplayName("Cada celular vota 1 vez: votar de novo TROCA o voto")
    void umVotoPorCelular() {
        Votacao v = nova();
        v.votar("cel-1", 1);
        v.votar("cel-1", 3);
        assertEquals(0, v.contagem().get(1L));
        assertEquals(1, v.contagem().get(3L));
        assertEquals(1, v.paraMapa(null).get("total_votos"));
        assertEquals(3L, v.paraMapa("cel-1").get("seu_voto"));
    }

    @Test
    @DisplayName("Empate: ganha o filme que o anfitrião colocou primeiro")
    void empate() {
        Votacao v = nova();
        v.votar("cel-1", 3);
        v.votar("cel-2", 1);
        assertEquals(DUNA, v.vencedor());
        assertEquals(DUNA, nova().vencedor());   // ninguém votou
    }

    @Test
    @DisplayName("Depois de encerrar, ninguém vota mais e o vencedor aparece")
    void encerrada() {
        Votacao v = nova();
        v.votar("cel-1", 2);
        assertEquals(MATRIX, v.encerrar());
        assertThrows(IllegalStateException.class, () -> v.votar("cel-2", 1));
        assertEquals(false, v.paraMapa(null).get("aberta"));
        assertEquals(2L, v.paraMapa(null).get("vencedor"));
    }

    @Test
    @DisplayName("Recusa voto em filme de fora e celular sem identificação")
    void votosInvalidos() {
        Votacao v = nova();
        assertThrows(IllegalArgumentException.class, () -> v.votar("cel-1", 99));
        assertThrows(IllegalArgumentException.class, () -> v.votar("", 1));
        assertThrows(IllegalArgumentException.class, () -> v.votar(null, 1));
    }

    @Test
    @DisplayName("Precisa de pelo menos 2 filmes, sem repetir, e aceita mais de 6")
    void regrasDeCriacao() {
        assertThrows(IllegalArgumentException.class, () -> new Votacao("X", 1, "a", List.of(DUNA), 0));
        assertThrows(IllegalArgumentException.class, () -> new Votacao("X", 1, "a", List.of(DUNA, DUNA), 0));
        List<Votacao.Opcao> sete = List.of(DUNA, MATRIX, SHREK, new Votacao.Opcao(4, "a", null), new Votacao.Opcao(5, "b", null),
                new Votacao.Opcao(6, "c", null), new Votacao.Opcao(8, "d", null));
        assertEquals(7, new Votacao("X", 1, "a", sete, 0).paraMapa(null).get("opcoes") instanceof List<?> l ? l.size() : -1);
        List<Votacao.Opcao> demais = new java.util.ArrayList<>();
        for (int i = 1; i <= Votacao.MAXIMO + 1; i++) demais.add(new Votacao.Opcao(1000 + i, "f" + i, null));
        assertThrows(IllegalArgumentException.class, () -> new Votacao("X", 1, "a", demais, 0));
    }
}
