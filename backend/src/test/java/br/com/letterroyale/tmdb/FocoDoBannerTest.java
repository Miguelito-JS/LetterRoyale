package br.com.letterroyale.tmdb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class FocoDoBannerTest {

    static final Color PELE = new Color(224, 172, 140);
    static final Color PELE_ESCURA = new Color(120, 78, 58);
    static final Color FOGO = new Color(250, 140, 40);

    /** Imagem 16:9 (320x180) escura. */
    private static BufferedImage fundo() {
        BufferedImage img = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(20, 24, 60));
        g.fillRect(0, 0, 320, 180);
        g.dispose();
        return img;
    }

    private static void desenhar(BufferedImage img, Color cor, int x, int y, int w, int h) {
        Graphics2D g = img.createGraphics();
        g.setColor(cor);
        g.fillOval(x, y, w, h);
        g.dispose();
    }

    /** Em que altura (0 a 1 da imagem) começa a parte que aparece no banner. */
    private static double inicioVisivel(int posicao, double fracao) {
        return posicao / 100.0 * (1 - fracao);
    }

    @Test
    @DisplayName("Cabeça no alto: o banner começa acima dela (não corta a cabeça)")
    void cabecaEmCima() {
        BufferedImage img = fundo();
        desenhar(img, PELE, 200, 20, 36, 40);   // rosto entre 11% e 33% da altura
        int pos = FocoDoBanner.melhorPosicao(FocoDoBanner.pele(img, 40), 0.5);
        assertTrue(inicioVisivel(pos, 0.5) <= 0.11, "começa em " + inicioVisivel(pos, 0.5));
    }

    @Test
    @DisplayName("Rosto + corpo grande embaixo (decote, braços): mira na cabeça, não no peito")
    void rostoECorpo() {
        BufferedImage img = fundo();
        desenhar(img, PELE, 200, 30, 30, 34);      // rosto: 17% a 36%
        desenhar(img, PELE, 170, 80, 100, 100);    // corpo bem maior: 44% a 100%
        int pos = FocoDoBanner.melhorPosicao(FocoDoBanner.pele(img, 40), 0.5);
        assertTrue(inicioVisivel(pos, 0.5) <= 0.17, "começa em " + inicioVisivel(pos, 0.5));
    }

    @Test
    @DisplayName("Pessoa embaixo: o banner desce até ela")
    void pessoaEmbaixo() {
        BufferedImage img = fundo();
        desenhar(img, PELE_ESCURA, 220, 110, 36, 40);   // 61% a 83%
        int pos = FocoDoBanner.melhorPosicao(FocoDoBanner.pele(img, 40), 0.5);
        double inicio = inicioVisivel(pos, 0.5);
        assertTrue(inicio <= 0.61 && inicio + 0.5 >= 0.83, "mostra de " + inicio + " a " + (inicio + 0.5));
    }

    @Test
    @DisplayName("Sem gente (só fogo/explosão): parte de cima, posição padrão")
    void semGente() {
        BufferedImage img = fundo();
        desenhar(img, FOGO, 180, 60, 120, 110);
        assertEquals(FocoDoBanner.POSICAO_SEM_GENTE, FocoDoBanner.melhorPosicao(FocoDoBanner.pele(img, 40), 0.5));
    }

    @Test
    @DisplayName("Rosto no lado esquerdo (embaixo do título) quase não conta")
    void ladoEsquerdo() {
        BufferedImage img = fundo();
        desenhar(img, PELE, 20, 120, 30, 30);    // à esquerda, embaixo
        desenhar(img, PELE, 230, 30, 30, 34);    // à direita, em cima
        int pos = FocoDoBanner.melhorPosicao(FocoDoBanner.pele(img, 40), 0.5);
        assertTrue(inicioVisivel(pos, 0.5) <= 0.17, "começa em " + inicioVisivel(pos, 0.5));
    }

    @Test
    @DisplayName("Banner que mostra a imagem inteira -> 50%")
    void semCorte() {
        assertEquals(50, FocoDoBanner.melhorPosicao(new double[40], 1.0));
        assertEquals(50, FocoDoBanner.melhorPosicao(new double[0], 0.5));
    }

    @Test
    @DisplayName("Pele clara e escura contam; fogo, céu e preto não")
    void pele() {
        assertTrue(FocoDoBanner.ehPele(PELE.getRGB()));
        assertTrue(FocoDoBanner.ehPele(PELE_ESCURA.getRGB()));
        assertFalse(FocoDoBanner.ehPele(FOGO.getRGB()));
        assertFalse(FocoDoBanner.ehPele(new Color(20, 24, 60).getRGB()));
        assertFalse(FocoDoBanner.ehPele(new Color(90, 140, 220).getRGB()));
        assertFalse(FocoDoBanner.ehPele(Color.BLACK.getRGB()));
    }
}
