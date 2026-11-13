package br.com.letterroyale.casa;

import br.com.letterroyale.tmdb.TmdbService;
import br.com.letterroyale.util.Db;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * =====================================================================
 *  Tira as CORES PRINCIPAIS do pôster de um filme, pra lâmpada usar.
 *
 *  1. baixa o pôster pequeno (185px) do TMDB
 *  2. olha os pixels e agrupa por "matiz" (hue = a cor no círculo cromático)
 *  3. joga fora preto/escuro (lâmpada "preta" = apagada) e cinza/branco (vira luz branca)
 *  4. pega as cores que mais aparecem, bem diferentes entre si (máx. 5)
 *  5. deixa elas mais vivas (lâmpada mostra melhor cor saturada)
 *  6. salva em filmes_cache.paleta pra não calcular de novo
 * =====================================================================
 */
@Service
public class PaletaService {
    private static final Logger log = LoggerFactory.getLogger(PaletaService.class);

    /** Pôster preto e branco (ex.: Psicose) -> luz quente e suave */
    static final List<String> PALETA_PB = List.of("#ffb36b", "#ffd9a8");

    private static final int MAX_CORES = 5;
    private static final int FATIAS_MATIZ = 24;          // círculo de 360° dividido em fatias de 15°
    private static final float DISTANCIA_MINIMA = 30f;   // 2 cores escolhidas têm que estar a 30°+ de distância

    private final Db db;
    private final TmdbService tmdb;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public PaletaService(Db db, TmdbService tmdb) {
        this.db = db;
        this.tmdb = tmdb;
    }

    /** Paleta do filme (do cache do banco, ou calcula agora). */
    public List<String> paleta(long tmdbId) {
        tmdb.garantirNoCache(tmdbId);
        Map<String, Object> f = db.um("SELECT paleta, poster_path FROM filmes_cache WHERE tmdb_id = ?", tmdbId).orElse(Map.of());
        String salva = (String) f.get("paleta");
        if (salva != null && !salva.isBlank()) {
            try {
                return Arrays.asList(json.readValue(salva, String[].class));
            } catch (Exception ignorado) { /* recalcula */ }
        }

        List<String> cores = PALETA_PB;
        String url = TmdbService.img((String) f.get("poster_path"), "w185");
        if (url != null) {
            try {
                HttpResponse<byte[]> resp = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(resp.body()));
                if (img != null) cores = extrair(img);
            } catch (Exception e) {
                log.warn("Não consegui baixar o pôster do filme {}: {}", tmdbId, e.getMessage());
            }
        }
        try {
            db.executar("UPDATE filmes_cache SET paleta = ? WHERE tmdb_id = ?", json.writeValueAsString(cores), tmdbId);
        } catch (Exception ignorado) { /* sem cache, tudo bem */ }
        return cores;
    }

    /** O algoritmo em si (separado pra dar pra testar com qualquer imagem). */
    static List<String> extrair(BufferedImage img) {
        int[] contagem = new int[FATIAS_MATIZ];
        float[][] soma = new float[FATIAS_MATIZ][3];   // soma de H (em x/y pra média circular), S, B
        float[] somaY = new float[FATIAS_MATIZ];

        int passo = Math.max(1, Math.min(img.getWidth(), img.getHeight()) / 80);   // não precisa ler todo pixel
        float[] hsb = new float[3];
        for (int y = 0; y < img.getHeight(); y += passo) {
            for (int x = 0; x < img.getWidth(); x += passo) {
                int rgb = img.getRGB(x, y);
                Color.RGBtoHSB((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff, hsb);
                if (hsb[2] < 0.20f) continue;   // escuro demais
                if (hsb[1] < 0.25f) continue;   // cinza/branco
                int fatia = Math.min(FATIAS_MATIZ - 1, (int) (hsb[0] * FATIAS_MATIZ));
                double ang = hsb[0] * 2 * Math.PI;
                contagem[fatia]++;
                soma[fatia][0] += (float) Math.cos(ang);
                somaY[fatia] += (float) Math.sin(ang);
                soma[fatia][1] += hsb[1];
                soma[fatia][2] += hsb[2];
            }
        }

        // Fatias da mais comum pra menos comum
        Integer[] ordem = new Integer[FATIAS_MATIZ];
        for (int i = 0; i < FATIAS_MATIZ; i++) ordem[i] = i;
        Arrays.sort(ordem, (a, b) -> contagem[b] - contagem[a]);

        int total = Arrays.stream(contagem).sum();
        List<float[]> escolhidas = new ArrayList<>();
        for (int fatia : ordem) {
            int n = contagem[fatia];
            if (n == 0 || n < total * 0.02) break;   // ignora cor que aparece em menos de 2% do pôster
            double ang = Math.atan2(somaY[fatia] / n, soma[fatia][0] / n);
            float h = (float) ((ang < 0 ? ang + 2 * Math.PI : ang) / (2 * Math.PI));
            float s = soma[fatia][1] / n;
            float b = soma[fatia][2] / n;
            boolean longe = escolhidas.stream().allMatch(c -> distanciaMatiz(c[0], h) >= DISTANCIA_MINIMA);
            if (longe) escolhidas.add(new float[]{h, s, b});
            if (escolhidas.size() == MAX_CORES) break;
        }

        if (escolhidas.isEmpty()) return PALETA_PB;

        // Uma cor só? Cria uma 2ª variação (mais clara) pra lâmpada ter o que trocar
        if (escolhidas.size() == 1) {
            float[] c = escolhidas.get(0);
            escolhidas.add(new float[]{c[0], c[1] * 0.55f, 1f});
        }

        List<String> hex = new ArrayList<>();
        for (float[] c : escolhidas) {
            float s = Math.max(0.55f, Math.min(1f, c[1] * 1.25f));   // mais viva
            float b = Math.max(0.80f, c[2]);                          // clara (o brilho real vem do slider)
            hex.add(String.format("#%06x", Color.HSBtoRGB(c[0], s, b) & 0xffffff));
        }
        return hex;
    }

    /** Distância entre 2 matizes em graus (0 a 180), lembrando que o círculo dá a volta */
    static float distanciaMatiz(float h1, float h2) {
        float d = Math.abs(h1 - h2) * 360f;
        return d > 180 ? 360 - d : d;
    }
}
