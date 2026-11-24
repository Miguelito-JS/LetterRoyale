package br.com.letterroyale.casa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testa a extração das cores do pôster SEM internet: o teste desenha
 * "pôsteres" falsos e confere se as cores certas saem.
 */
class PaletaServiceTest {

    /** Pôster 185x278 (tamanho do w185 do TMDB) pintado em faixas verticais. */
    private static BufferedImage poster(Color... faixas) {
        BufferedImage img = new BufferedImage(185, 278, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        int largura = 185 / faixas.length + 1;
        for (int i = 0; i < faixas.length; i++) {
            g.setColor(faixas[i]);
            g.fillRect(i * largura, 0, largura, 278);
        }
        g.dispose();
        return img;
    }

    private static float matiz(String hex) {
        Color c = Color.decode(hex);
        return Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null)[0] * 360f;
    }

    private static boolean temCorPerto(List<String> paleta, float grausAlvo) {
        return paleta.stream().anyMatch(h -> PaletaService.distanciaMatiz(matiz(h) / 360f, grausAlvo / 360f) < 20);
    }

    @Test
    @DisplayName("Pôster vermelho e azul -> paleta com vermelho e azul")
    void duasCores() {
        List<String> p = PaletaService.extrair(poster(new Color(200, 30, 30), new Color(30, 60, 200)));
        assertTrue(temCorPerto(p, 0), "faltou vermelho: " + p);
        assertTrue(temCorPerto(p, 228), "faltou azul: " + p);
    }

    @Test
    @DisplayName("Pôster preto e branco (ex.: Psicose) -> luz quente padrão")
    void pretoEBranco() {
        List<String> p = PaletaService.extrair(poster(Color.BLACK, Color.WHITE, Color.GRAY));
        assertEquals(PaletaService.PALETA_PB, p);
    }

    @Test
    @DisplayName("Preto é ignorado: lâmpada 'preta' seria lâmpada apagada")
    void ignoraEscuro() {
        List<String> p = PaletaService.extrair(poster(Color.BLACK, Color.BLACK, new Color(20, 160, 40)));
        assertTrue(temCorPerto(p, 128), "faltou o verde: " + p);
        for (String h : p) {
            Color c = Color.decode(h);
            float brilho = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null)[2];
            assertTrue(brilho >= 0.79f, "cor escura demais na paleta: " + h);
        }
    }

    @Test
    @DisplayName("Uma cor só -> cria uma 2ª variação pra lâmpada ter o que trocar")
    void umaCorViraDuas() {
        List<String> p = PaletaService.extrair(poster(new Color(230, 120, 20)));
        assertEquals(2, p.size(), "paleta: " + p);
    }

    @Test
    @DisplayName("No máximo 5 cores, todas em formato #rrggbb")
    void maximoCincoCores() {
        List<String> p = PaletaService.extrair(poster(Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN,
                Color.CYAN, Color.BLUE, Color.MAGENTA, new Color(128, 0, 255)));
        assertTrue(p.size() <= 5, "paleta grande demais: " + p);
        p.forEach(h -> assertTrue(h.matches("#[0-9a-f]{6}"), "formato errado: " + h));
    }

    @Test
    @DisplayName("Distância de matiz dá a volta no círculo (350° e 10° estão a 20°)")
    void distanciaCircular() {
        assertEquals(20f, PaletaService.distanciaMatiz(350f / 360f, 10f / 360f), 0.01f);
        assertEquals(180f, PaletaService.distanciaMatiz(0f, 0.5f), 0.01f);
    }
}
