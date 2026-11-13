package br.com.letterroyale.erro;

/**
 * Erro "esperado" (ex.: senha errada, lista não encontrada).
 * Lançar:  throw new ApiException(404, "Lista não encontrada.");
 * O TratadorErros transforma em JSON: { "erro": "Lista não encontrada." }
 */
public class ApiException extends RuntimeException {
    private final int status;

    public ApiException(int status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
