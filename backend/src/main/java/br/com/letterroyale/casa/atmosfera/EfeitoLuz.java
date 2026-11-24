package br.com.letterroyale.casa.atmosfera;

/**
 * =====================================================================
 *  EFEITOS DE BRILHO — a cada troca de cor, quanto de brilho a lâmpada recebe.
 *
 *  "passo" = quantas trocas de cor já aconteceram (0, 1, 2, 3...).
 *  O brilho base vem do slider de Iluminação (ou da atmosfera no começo).
 *
 *    CONSTANTE  -> sempre o brilho base
 *    RESPIRAR   -> sobe e desce devagar (100% → 85% → 70% → 85% → ...)
 *    PULSAR     -> forte, fraco, forte, fraco (coração acelerado no terror)
 *    RELAMPAGO  -> brilho base e UM clarão de 100% logo no começo (uma piscada só)
 *
 *  Classe sem Spring e sem banco: dá pra testar com JUnit puro.
 * =====================================================================
 */
public final class EfeitoLuz {

    public enum Efeito { CONSTANTE, RESPIRAR, PULSAR, RELAMPAGO }

    /** A lâmpada "apaga" abaixo disso, então nunca mandamos menos. */
    public static final int BRILHO_MINIMO = 5;
    public static final int CLARAO = 100;
    /** Em qual troca de cor acontece o único clarão (logo depois de a atmosfera entrar). */
    public static final int PASSO_DO_CLARAO = 1;

    private static final double[] RESPIRAR = {1.0, 0.85, 0.7, 0.85};
    private static final double[] PULSAR = {1.0, 0.4};

    private EfeitoLuz() {
    }

    /** Brilho (5 a 100) que a lâmpada deve receber neste passo. */
    public static int brilho(Efeito efeito, int brilhoBase, int passo) {
        int base = limitar(brilhoBase);
        int p = Math.max(0, passo);
        int resultado = switch (efeito == null ? Efeito.CONSTANTE : efeito) {
            case CONSTANTE -> base;
            case RESPIRAR -> (int) Math.round(base * RESPIRAR[p % RESPIRAR.length]);
            case PULSAR -> (int) Math.round(base * PULSAR[p % PULSAR.length]);
            case RELAMPAGO -> p == PASSO_DO_CLARAO ? CLARAO : base;   // uma piscada só, não fica piscando o filme todo
        };
        return limitar(resultado);
    }

    /** Aceita "pulsar", "PULSAR", null... (null ou desconhecido = CONSTANTE). */
    public static Efeito deTexto(String texto) {
        if (texto == null) return Efeito.CONSTANTE;
        try {
            return Efeito.valueOf(texto.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Efeito.CONSTANTE;
        }
    }

    static int limitar(int brilho) {
        return Math.max(BRILHO_MINIMO, Math.min(100, brilho));
    }
}
