package br.com.letterroyale.controller;

import br.com.letterroyale.casa.controle.CertificadoLocal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * GET /certificado/letterroyale-ca.crt -> o certificado PÚBLICO da CA deste PC,
 * pro celular instalar uma vez (ver CertificadoLocal). Não precisa de login:
 * é só a parte pública; a chave fica em segredos/https e nunca sai do PC.
 */
@RestController
public class CertificadoController {

    /** O arquivo é PEM (texto). O Android instala melhor o formato DER (binário): tira o cabeçalho e decodifica. */
    static byte[] der(String pem) {
        String base64 = pem.replace("-----BEGIN CERTIFICATE-----", "").replace("-----END CERTIFICATE-----", "").replaceAll("\\s+", "");
        return java.util.Base64.getDecoder().decode(base64);
    }

    @GetMapping("/certificado/" + CertificadoLocal.ARQUIVO_CA)
    public ResponseEntity<byte[]> baixar() throws Exception {
        Path crt = CertificadoLocal.pasta().resolve(CertificadoLocal.ARQUIVO_CA);
        if (!Files.exists(crt)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-x509-ca-cert"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + CertificadoLocal.ARQUIVO_CA + "\"")
                .body(der(Files.readString(crt)));
    }
}
