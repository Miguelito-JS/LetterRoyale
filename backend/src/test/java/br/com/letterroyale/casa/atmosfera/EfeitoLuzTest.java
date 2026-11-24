package br.com.letterroyale.casa.atmosfera;

import br.com.letterroyale.casa.atmosfera.EfeitoLuz.Efeito;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EfeitoLuzTest {

    @Test
    @DisplayName("CONSTANTE: sempre o brilho do slider")
    void constante() {
        for (int passo = 0; passo < 10; passo++) assertEquals(40, EfeitoLuz.brilho(Efeito.CONSTANTE, 40, passo));
    }

    @Test
    @DisplayName("PULSAR: forte, fraco, forte, fraco (coração acelerado)")
    void pulsar() {
        assertEquals(50, EfeitoLuz.brilho(Efeito.PULSAR, 50, 0));
        assertEquals(20, EfeitoLuz.brilho(Efeito.PULSAR, 50, 1));
        assertEquals(50, EfeitoLuz.brilho(Efeito.PULSAR, 50, 2));
        assertEquals(20, EfeitoLuz.brilho(Efeito.PULSAR, 50, 3));
    }

    @Test
    @DisplayName("RESPIRAR: 100% → 85% → 70% → 85% e repete")
    void respirar() {
        int[] esperado = {40, 34, 28, 34, 40};
        for (int passo = 0; passo < esperado.length; passo++) {
            assertEquals(esperado[passo], EfeitoLuz.brilho(Efeito.RESPIRAR, 40, passo), "passo " + passo);
        }
    }

    @Test
    @DisplayName("RELAMPAGO: um clarão de 100% só uma vez, depois fica no brilho base")
    void relampago() {
        for (int passo = 0; passo < 15; passo++) {
            int b = EfeitoLuz.brilho(Efeito.RELAMPAGO, 45, passo);
            assertEquals(passo == 1 ? 100 : 45, b, "passo " + passo);
        }
    }

    @Test
    @DisplayName("Nunca manda menos que 5% (a lâmpada apagaria) nem mais que 100%")
    void limites() {
        assertEquals(5, EfeitoLuz.brilho(Efeito.PULSAR, 8, 1));      // 8 * 0.4 = 3 -> sobe pra 5
        assertEquals(5, EfeitoLuz.brilho(Efeito.CONSTANTE, 0, 0));
        assertEquals(100, EfeitoLuz.brilho(Efeito.CONSTANTE, 250, 0));
        assertEquals(5, EfeitoLuz.brilho(Efeito.CONSTANTE, -10, 0));
    }

    @Test
    @DisplayName("Passo negativo e efeito nulo não quebram")
    void entradasEstranhas() {
        assertEquals(30, EfeitoLuz.brilho(null, 30, 3));
        assertEquals(30, EfeitoLuz.brilho(Efeito.PULSAR, 30, -1));
    }

    @Test
    @DisplayName("deTexto entende maiúscula/minúscula e cai em CONSTANTE se não conhecer")
    void deTexto() {
        assertEquals(Efeito.PULSAR, EfeitoLuz.deTexto("pulsar"));
        assertEquals(Efeito.RELAMPAGO, EfeitoLuz.deTexto(" RELAMPAGO "));
        assertEquals(Efeito.CONSTANTE, EfeitoLuz.deTexto("disco"));
        assertEquals(Efeito.CONSTANTE, EfeitoLuz.deTexto(null));
    }
}
