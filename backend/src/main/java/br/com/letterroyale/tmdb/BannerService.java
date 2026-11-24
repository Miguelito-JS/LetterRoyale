package br.com.letterroyale.tmdb;

import br.com.letterroyale.erro.ApiException;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Baixa a imagem do banner (versão pequena, 300px) e calcula quanto tem de pele em cada faixa
 * com o FocoDoBanner. Guarda em memória: cada imagem é analisada uma vez só.
 * Só aceita imagens do TMDB (ninguém usa o servidor pra baixar qualquer coisa).
 */
@Service
public class BannerService {
    private static final String TMDB_IMG = "https://image.tmdb.org/t/p/";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private final Map<String, Map<String, Object>> cache = new ConcurrentHashMap<>();

    public Map<String, Object> foco(String url) {
        if (url == null || !url.startsWith(TMDB_IMG) || url.contains("..")) throw new ApiException(400, "Só imagens do TMDB.");
        String pequena = url.replaceFirst("/t/p/[^/]+/", "/t/p/w300/");
        return cache.computeIfAbsent(pequena, this::analisar);
    }

    private Map<String, Object> analisar(String url) {
        try {
            HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(r.body()));
            if (img == null) throw new ApiException(502, "Imagem inválida.");
            return Map.of("proporcao", (double) img.getWidth() / img.getHeight(),
                    "pele", FocoDoBanner.pele(img, FocoDoBanner.FAIXAS));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(502, "Não consegui analisar a imagem do banner.");
        }
    }
}
