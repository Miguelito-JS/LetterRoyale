package br.com.letterroyale.casa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RitmoCinemaTest {

    @Test
    @DisplayName("Ritmo do Modo Cinema em segundos (mínimo 2 s por causa do limite da nuvem Tuya)")
    void ritmos() {
        assertEquals(8, CinemaService.ritmoEmSegundos("calmo"));
        assertEquals(4, CinemaService.ritmoEmSegundos("normal"));
        assertEquals(2, CinemaService.ritmoEmSegundos("festa"));
        assertEquals(4, CinemaService.ritmoEmSegundos(null));
        assertEquals(4, CinemaService.ritmoEmSegundos("turbo"));
    }
}
