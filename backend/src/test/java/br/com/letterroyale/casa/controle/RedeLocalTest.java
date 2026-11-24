package br.com.letterroyale.casa.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RedeLocalTest {

    @Test
    @DisplayName("Prefere o IP do Wi-Fi de casa (192.168...) e ignora VPN e IP público")
    void ordem() {
        List<String> r = RedeLocal.ordenar(List.of("26.10.0.5", "10.0.0.8", "172.20.1.1", "192.168.0.15", "8.8.8.8"));
        assertEquals(List.of("192.168.0.15", "10.0.0.8", "172.20.1.1"), r);
    }

    @Test
    @DisplayName("172.x só é rede local entre 172.16 e 172.31")
    void faixa172() {
        assertTrue(RedeLocal.ehRedeLocal("172.16.0.1"));
        assertTrue(RedeLocal.ehRedeLocal("172.31.255.1"));
        assertFalse(RedeLocal.ehRedeLocal("172.32.0.1"));
        assertFalse(RedeLocal.ehRedeLocal(null));
    }

    @Test
    @DisplayName("Não repete IP e não quebra sem rede")
    void semRepetir() {
        assertEquals(List.of("192.168.1.2"), RedeLocal.ordenar(List.of("192.168.1.2", "192.168.1.2")));
        assertTrue(RedeLocal.ordenar(List.of()).isEmpty());
        assertNotNull(RedeLocal.ips());   // na máquina de teste pode vir vazio, mas nunca null
    }
}
