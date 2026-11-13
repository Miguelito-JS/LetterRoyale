package br.com.letterroyale.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga os métodos com @Scheduled (ex.: Modo Cinema automático na hora da sessão). */
@Configuration
@EnableScheduling
public class AgendadorConfig {
}
