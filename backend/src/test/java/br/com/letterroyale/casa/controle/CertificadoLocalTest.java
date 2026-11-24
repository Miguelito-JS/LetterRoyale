package br.com.letterroyale.casa.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CertificadoLocalTest {

    @Test
    @DisplayName("Certificado vale pra localhost e pra cada IP da rede, sem repetir")
    void nomes() {
        assertEquals("dns:localhost,ip:127.0.0.1,ip:192.168.0.15,ip:10.0.0.8",
                CertificadoLocal.nomesAlternativos(List.of("192.168.0.15", "10.0.0.8", "192.168.0.15")));
    }

    @Test
    @DisplayName("Ignora o que não é IP (não deixa texto estranho entrar no comando do keytool)")
    void soIp() {
        assertEquals("dns:localhost,ip:127.0.0.1", CertificadoLocal.nomesAlternativos(List.of("abc", "1.2.3", "192.168.0.1,dns:x")));
    }

    @Test
    @DisplayName("Sem rede: ainda vale pra localhost")
    void semRede() {
        assertEquals("dns:localhost,ip:127.0.0.1", CertificadoLocal.nomesAlternativos(List.of()));
    }
}
