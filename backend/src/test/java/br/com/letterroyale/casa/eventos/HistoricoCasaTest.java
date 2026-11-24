package br.com.letterroyale.casa.eventos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class HistoricoCasaTest {

    private static EventoCasa ev(long uid, String texto) {
        return new EventoCasa(uid, EventoCasa.Tipo.LUZ, texto, System.currentTimeMillis());
    }

    @Test
    @DisplayName("Mostra do mais novo para o mais antigo")
    void ordem() {
        HistoricoCasa h = new HistoricoCasa();
        h.registrar(ev(1, "primeiro"));
        h.registrar(ev(1, "segundo"));
        h.registrar(ev(1, "terceiro"));
        assertEquals(List.of("terceiro", "segundo", "primeiro"), h.ultimos(1, 10).stream().map(EventoCasa::texto).toList());
    }

    @Test
    @DisplayName("Passou do limite: o mais antigo sai (memória não cresce sem parar)")
    void limite() {
        HistoricoCasa h = new HistoricoCasa(3);
        for (int i = 1; i <= 10; i++) h.registrar(ev(1, "e" + i));
        assertEquals(List.of("e10", "e9", "e8"), h.ultimos(1, 99).stream().map(EventoCasa::texto).toList());
    }

    @Test
    @DisplayName("Cada usuário só vê a própria casa")
    void separadoPorUsuario() {
        HistoricoCasa h = new HistoricoCasa();
        h.registrar(ev(1, "casa da Ana"));
        h.registrar(ev(2, "casa do Bruno"));
        assertEquals(1, h.ultimos(1, 10).size());
        assertEquals("casa da Ana", h.ultimos(1, 10).get(0).texto());
        assertTrue(h.ultimos(3, 10).isEmpty());
    }

    @Test
    @DisplayName("Pedir 0 ou menos devolve vazio; limite inválido é recusado")
    void entradasEstranhas() {
        HistoricoCasa h = new HistoricoCasa();
        h.registrar(ev(1, "x"));
        assertTrue(h.ultimos(1, 0).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new HistoricoCasa(0));
    }

    @Test
    @DisplayName("Aguenta vários eventos chegando ao mesmo tempo (agendador + usuário)")
    void concorrencia() throws InterruptedException {
        HistoricoCasa h = new HistoricoCasa(50);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 1000; i++) {
            int n = i;
            pool.submit(() -> h.registrar(ev(1, "e" + n)));
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(50, h.ultimos(1, 100).size());
    }

    @Test
    @DisplayName("Evento vira JSON com ícone pra tela")
    void paraMapa() {
        var m = new EventoCasa(7, EventoCasa.Tipo.SESSAO_INICIADA, "Sessão começou: Duna", 1000).paraMapa();
        assertEquals("sessao_iniciada", m.get("tipo"));
        assertEquals("🎬", m.get("icone"));
        assertEquals("Sessão começou: Duna", m.get("texto"));
        assertEquals(1000L, m.get("instante"));
    }
}
