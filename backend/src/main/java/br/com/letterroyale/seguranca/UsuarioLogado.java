package br.com.letterroyale.seguranca;

/** Quem está logado (vem de dentro do token). "record" = classe só de dados. */
public record UsuarioLogado(long id, String username) {
}
