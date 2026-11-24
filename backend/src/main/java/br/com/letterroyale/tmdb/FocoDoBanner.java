package br.com.letterroyale.tmdb;

import java.awt.image.BufferedImage;

/**
 * =====================================================================
 *  VERIFICADOR DE BANNER: o banner largo do site não pode cortar a
 *  cabeça de ninguém.
 *
 *  O banner é bem mais largo que a imagem do TMDB (16:9), então sempre
 *  sobra imagem em cima ou embaixo. A regra é a de um editor de cinema:
 *
 *   1. divide a imagem em FAIXAS horizontais (40) e conta, em cada uma,
 *      quanto tem de PELE (rosto, mãos) - só na parte direita, porque a
 *      esquerda do banner fica coberta pelo título e pelo pôster
 *   2. acha o TOPO das pessoas: a primeira faixa com bastante pele
 *      (a cabeça vem antes do corpo, então mirar no topo nunca corta a cabeça
 *      e não fica "focando no peito")
 *   3. o banner começa um pouco ACIMA desse topo (folga pra testa e cabelo)
 *   4. sem gente na imagem (robô, paisagem, máscara): mostra a parte de cima
 *      (20%), que é onde costumam ficar cabeças e o céu da cena
 *
 *  Classe sem Spring e sem internet: testada com JUnit usando imagens desenhadas.
 * =====================================================================
 */
public final class FocoDoBanner {

    /** Faixas horizontais do perfil (o navegador recebe 40 números). */
    public static final int FAIXAS = 40;
    /** A esquerda do banner (título, pôster e degradê escuro) quase não conta. */
    static final double INICIO_DIREITA = 0.45;
    static final double PESO_ESQUERDA = 0.15;
    /** Uma faixa é "topo das pessoas" quando tem pelo menos isso da faixa com mais pele
     *  (baixo de propósito: um rosto é bem menor que um corpo inteiro)... */
    static final double LIMIAR_TOPO = 0.15;
    /** ...e pelo menos isso da imagem (pontinhos soltos de cor de pele não contam). */
    static final double MINIMO_NA_FAIXA = 0.0006;
    /** Menos pele que isso (fração dos pixels da imagem) = imagem sem gente. */
    static final double MINIMO_DE_PELE = 0.004;
    /** Folga acima da cabeça, em fração da altura que aparece no banner. */
    static final double FOLGA_ACIMA = 0.15;
    /** Sem gente: posição padrão (parte de cima da imagem). */
    public static final int POSICAO_SEM_GENTE = 20;

    private FocoDoBanner() {
    }

    /**
     * Quanto de pele tem em cada faixa (já com o peso de esquerda/direita),
     * dividido pelo total de pixels da imagem. Ex.: 0.01 = 1% da imagem é pele naquela faixa.
     */
    public static double[] pele(BufferedImage img, int faixas) {
        int w = img.getWidth(), h = img.getHeight();
        double[] pele = new double[faixas];
        if (w < 2 || h < 2) return pele;
        double total = (double) w * h;
        for (int y = 0; y < h; y++) {
            int faixa = Math.min(faixas - 1, y * faixas / h);
            for (int x = 0; x < w; x++) {
                if (ehPele(img.getRGB(x, y))) pele[faixa] += (x < w * INICIO_DIREITA ? PESO_ESQUERDA : 1.0) / total;
            }
        }
        for (int i = 0; i < faixas; i++) pele[i] = Math.round(pele[i] * 1_000_000) / 1_000_000.0;
        return pele;
    }

    /**
     * Posição vertical (0 a 100, igual ao background-position-y em %)
     * quando só "fracaoVisivel" da altura da imagem aparece no banner.
     */
    public static int melhorPosicao(double[] pele, double fracaoVisivel) {
        int n = pele.length;
        if (n == 0 || fracaoVisivel >= 0.999) return 50;
        int k = Math.max(1, (int) Math.round(fracaoVisivel * n));
        if (k >= n) return 50;
        double soma = 0, max = 0;
        for (double v : pele) { soma += v; max = Math.max(max, v); }
        if (soma < MINIMO_DE_PELE) return POSICAO_SEM_GENTE;
        int topo = 0;
        while (topo < n && (pele[topo] < max * LIMIAR_TOPO || pele[topo] < MINIMO_NA_FAIXA)) topo++;
        if (topo >= n) return POSICAO_SEM_GENTE;
        int inicio = Math.max(0, Math.min(n - k, topo - (int) Math.round(k * FOLGA_ACIMA)));
        return (int) Math.round(inicio * 100.0 / (n - k));
    }

    /**
     * Tom de pele (claro ou escuro), pela PROPORÇÃO entre as cores, não pelo brilho:
     * vermelho > verde > azul, com o azul ainda presente. Fogo e explosão (laranja forte,
     * quase sem azul) ficam de fora; céu, roupa azul e preto também.
     */
    static boolean ehPele(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        if (r < 60 || r - b < 15 || g < b) return false;
        double gr = (double) g / r, br = (double) b / r;
        return gr >= 0.45 && gr <= 0.90 && br >= 0.28 && br <= 0.80;
    }

}
