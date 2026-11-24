package br.com.letterroyale.casa.aparelhos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ComandosTuyaTest {

    private static Map<String, Object> cmd(String code, Object value) {
        return Map.of("code", code, "value", value);
    }

    @Test
    @DisplayName("Sem ID cadastrado = aparelho simulado")
    void semId() {
        assertNull(ComandosTuya.destino(null));
        assertNull(ComandosTuya.destino("   "));
    }

    @Test
    @DisplayName("ID comum = aparelho Wi-Fi; ir:hub:controle = ar pelo infravermelho")
    void destinos() {
        ComandosTuya.Destino wifi = ComandosTuya.destino(" eb12ab34cd56ef ");
        assertFalse(wifi.infravermelho());
        assertEquals("eb12ab34cd56ef", wifi.deviceId());

        ComandosTuya.Destino ir = ComandosTuya.destino("ir:hub123:ar456");
        assertTrue(ir.infravermelho());
        assertEquals("hub123", ir.hubId());
        assertEquals("ar456", ir.controleId());
    }

    @Test
    @DisplayName("ID com formato errado é recusado com mensagem clara")
    void idsErrados() {
        assertThrows(IllegalArgumentException.class, () -> ComandosTuya.destino("ir:so-um-id"));
        assertThrows(IllegalArgumentException.class, () -> ComandosTuya.destino("tem espaço aqui"));
        assertThrows(IllegalArgumentException.class, () -> ComandosTuya.destino("abc"));
    }

    @Test
    @DisplayName("Cortina: abrir, fechar e meio (com ou sem porcentagem)")
    void cortina() {
        assertEquals(List.of(cmd("control", "open")), ComandosTuya.cortina("abertas", Set.of()));
        assertEquals(List.of(cmd("control", "close")), ComandosTuya.cortina("fechadas", Set.of()));
        assertEquals(List.of(cmd("control", "close")), ComandosTuya.cortina(null, null));
        assertEquals(List.of(cmd("percent_control", 50)), ComandosTuya.cortina("meio", Set.of("control", "percent_control")));
        assertEquals(List.of(cmd("control", "stop")), ComandosTuya.cortina("meio", Set.of("control")));
    }

    @Test
    @DisplayName("Ar Wi-Fi: liga + temperatura (respeitando a escala do aparelho)")
    void arWifi() {
        assertEquals(List.of(cmd("switch", true), cmd("temp_set", 21)), ComandosTuya.ar(true, 21, Set.of("switch", "temp_set"), 0));
        assertEquals(List.of(cmd("switch", true), cmd("temp_set", 210)), ComandosTuya.ar(true, 21, Set.of("switch", "temp_set"), 1));
        assertEquals(List.of(cmd("switch", false)), ComandosTuya.ar(false, 21, Set.of(), 0));
        assertEquals(List.of(cmd("power", true), cmd("temp_set", 22)), ComandosTuya.ar(true, 22, Set.of("power", "temp_set"), 0));
    }

    @Test
    @DisplayName("Temperatura fica entre 16 e 30 °C (o que os ares aceitam)")
    void limitesTemperatura() {
        assertEquals(List.of(cmd("switch", true), cmd("temp_set", 16)), ComandosTuya.ar(true, 5, Set.of(), 0));
        assertEquals(30, ComandosTuya.arInfravermelho(true, 45).get("temp"));
    }

    @Test
    @DisplayName("Ar comum pelo hub infravermelho: power, gelar, temperatura, vento automático")
    void arInfravermelho() {
        Map<String, Object> m = ComandosTuya.arInfravermelho(true, 20);
        assertEquals(1, m.get("power"));
        assertEquals(0, m.get("mode"));
        assertEquals(20, m.get("temp"));
        assertEquals(0, m.get("wind"));
        assertEquals(0, ComandosTuya.arInfravermelho(false, 20).get("power"));
    }

    @Test
    @DisplayName("Lê a escala do temp_set que a Tuya manda")
    void escala() {
        assertEquals(1, ComandosTuya.escala("{\"unit\":\"℃\",\"min\":160,\"max\":300,\"scale\":1,\"step\":5}"));
        assertEquals(0, ComandosTuya.escala("{\"min\":16,\"max\":30,\"scale\":0}"));
        assertEquals(0, ComandosTuya.escala(null));
        assertEquals(0, ComandosTuya.escala("{}"));
    }
}
