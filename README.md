# LetterRoyale 🎬

Site de filmes (estilo Letterboxd) com reviews, listas, Smart Home e conexões com streamings.
Front em HTML/CSS/JS (responsivo, funciona em WebView) + back em **Java 25 / Spring Boot 3.5** + MySQL + API do TMDB.

**Arquitetura, atmosferas, eventos, testes e Roadmap 2050: [ARQUITETURA.md](ARQUITETURA.md)**

**Leia o [FLUXO.md](FLUXO.md)** e, para a lâmpada inteligente, o **[LAMPADA.md](LAMPADA.md)** — lá está o diagrama, o banco, qual tela usa qual rota e a divisão de tarefas.

## Rodar rápido
1. `mysql -u root -p < database/schema.sql` e depois `database/seed.sql` (login de teste: ana@email.com / 123456)
2. Abrir a pasta `backend/` no IntelliJ (Maven)
3. Configure `DB_PASSWORD`, `TMDB_TOKEN` e `JWT_SECRET` como variáveis de ambiente ou crie `segredos/segredos.properties` usando `segredos/segredos.properties.example` como modelo. O arquivo real é ignorado pelo Git.
4. **Dois cliques no `rodar.cmd`** (compila o código atual com o JDK 27 da pasta `oracleJdk-27` e sobe o servidor) → abrir http://localhost:8080. Não rode o `.jar` antigo da pasta `target`: ele não tem as mudanças novas.
5. Testes automatizados: dois cliques no `testar.cmd`

Este produto usa a API do TMDB, mas não é endossado nem certificado pelo TMDB.
