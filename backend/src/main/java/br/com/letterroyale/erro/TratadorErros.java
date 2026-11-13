package br.com.letterroyale.erro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Pega qualquer erro dos controllers e devolve sempre no formato { "erro": "..." } */
@RestControllerAdvice
public class TratadorErros {
    private static final Logger log = LoggerFactory.getLogger(TratadorErros.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> api(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Map<String, String>> duplicado(DuplicateKeyException e) {
        return ResponseEntity.status(409).body(Map.of("erro", "Esse registro já existe."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> geral(Exception e) {
        log.error("Erro inesperado", e);
        return ResponseEntity.status(500).body(Map.of("erro", "Erro no servidor."));
    }
}
