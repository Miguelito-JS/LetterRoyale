package br.com.letterroyale.config;

import br.com.letterroyale.casa.controle.CertificadoLocal;
import br.com.letterroyale.casa.controle.RedeLocal;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * =====================================================================
 *  Segunda porta, com HTTPS, só pro celular: https://IP-do-PC:8443
 *  A porta normal (http://localhost:8080) continua igual pro computador
 *  e pros amigos que votam pelo QR (não precisam instalar nada).
 *  O certificado é feito pelo CertificadoLocal. Se der qualquer erro,
 *  o site sobe só com http, como antes.
 * =====================================================================
 */
@Configuration
public class HttpsLocal {
    private static final Logger log = LoggerFactory.getLogger(HttpsLocal.class);

    /** Ligado depois que a porta HTTPS foi configurada. */
    private static volatile boolean ativo = false;
    private static volatile int porta = 8443;

    public static boolean ativo() {
        return ativo;
    }

    public static int porta() {
        return porta;
    }

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> portaHttps(
            @Value("${app.https.ligado:true}") boolean ligado,
            @Value("${app.https.porta:8443}") int portaHttps) {
        return fabrica -> {
            if (!ligado) return;
            CertificadoLocal.Pronto cert = CertificadoLocal.preparar(RedeLocal.ips());
            if (cert == null) return;
            try {
                Connector c = new Connector("org.apache.coyote.http11.Http11NioProtocol");
                c.setPort(portaHttps);
                c.setScheme("https");
                c.setSecure(true);
                c.setProperty("SSLEnabled", "true");
                SSLHostConfig ssl = new SSLHostConfig();
                SSLHostConfigCertificate certificado = new SSLHostConfigCertificate(ssl, SSLHostConfigCertificate.Type.UNDEFINED);
                certificado.setCertificateKeystoreFile(cert.keystore().toString());
                certificado.setCertificateKeystorePassword(cert.senha());
                certificado.setCertificateKeystoreType("PKCS12");
                certificado.setCertificateKeyAlias(CertificadoLocal.ALIAS);
                ssl.addCertificate(certificado);
                c.addSslHostConfig(ssl);
                fabrica.addAdditionalTomcatConnectors(c);
                porta = portaHttps;
                ativo = true;
                log.info("HTTPS pro celular ligado na porta {}", portaHttps);
            } catch (Exception e) {
                log.warn("Não consegui ligar o HTTPS ({}). Seguindo só com http.", e.getMessage());
            }
        };
    }
}
