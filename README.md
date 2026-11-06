# Radar

Aplicativo Android nativo Kotlin/Jetpack Compose, backend Python/SQLite e site estático de distribuição. **Implementação inicial incompleta: não monitora promoções ainda.**

## Desenvolvimento

- Android: JDK 17, Gradle 8.9, Android SDK 35. `gradle -p android assembleDebug`. APK em `android/app/build/outputs/apk/debug/app-debug.apk`.
- Backend: Python 3.12+, sem dependências externas. `python -m backend.server`.
- Testes: `python -m unittest discover -s backend/tests -v`.
- Site: `python -m http.server 8000 --directory site`.

Use o checkout existente; este ambiente já é isolado, não precisa de worktree.

## Arquitetura e execução

Android mantém cadastros em SharedPreferences privadas, com JSON e valores monetários inteiros. O backend possui SQLite, endpoints `/products`, `/sources`, `/shares`, `/offers`, `/export`, `/health`; POST cadastra/edita e DELETE `/tipo/id` exclui. O agendador percorre produtos e fontes ativos, mas todos os conectores estão explicitamente indisponíveis. Não há sincronização automática entre Android e backend nesta versão. O botão de conexão verifica apenas `/health`.

Variáveis: `RADAR_DB` (padrão `radar.db`), `RADAR_HOST` (127.0.0.1), `PORT` (8080), `RADAR_INTERVAL_SECONDS` (900, mínimo 60), `RADAR_TOKEN` (token Bearer opcional para uso local). Para exposição pública, configure token forte via segredo e proxy HTTPS; o app aceita apenas HTTPS. Não exponha o serviço sem autenticação. Dockerfile em backend: volume persistente `/data`; execução contínua exige hospedagem externa e política de reinício. Nenhuma hospedagem foi contratada ou configurada.

## Integrações avaliadas

Em 06/11/2026, consultas reais a KaBuM!, Pichau, Terabyte, Amazon Brasil, API de busca Mercado Livre e documentação Meta retornaram `Tunnel connection failed: 403 Forbidden` no proxy deste ambiente. Isso comprova bloqueio de acesso neste ambiente, não inviabilidade geral dessas APIs. Nenhum conector tem suporte declarado. Não foi possível confirmar o alcance atual da API oficial de grupos WhatsApp. Integração automática não implementada; não há captura de sessões ou acesso a conversas. Compartilhamento manual é complementar e armazena texto não verificado.

## Cobertura e pendências

Testes determinísticos cobrem centavos, igualdade ao limite, percentual, variantes RX 7600 XT/computadores, estoque, parcelas, condições não verificadas, persistência/edição e estado de fonte. Não são testes de lojas reais. Faltam testes instrumentados Android e execução em dispositivo.

Pendências: conectores e busca por nome, cache/backoff, sincronização, ofertas/histórico, cupons, frete, deduplicação, notificações, edição completa de fontes, links específicos e importação do backend. Não há ofertas simuladas nem preços extraídos de números arbitrários.

## Publicação

Workflow `.github/workflows/release.yml` testa e compila em pushes para main. A tag `v0.1.0` publica pré-release com APK **debug**, checksum e Pages. Habilite GitHub Pages com origem GitHub Actions. O site só habilita o download após geração de `release.json` com o APK existente. Versões futuras precisam atualizar versionName, versionCode, workflow e script de metadados juntos.

A chave de debug do runner não é uma identidade de distribuição estável. Assinatura definitiva requer keystore e senhas nos secrets do GitHub, configuração de signingConfig e proteção do workflow. Nenhuma chave é versionada. Sem screenshots fictícios.

## Evidências desta execução

- 10 testes Python passaram, incluindo testes HTTP reais locais.
- `assembleDebug` concluído localmente; assinatura v2 verificada por `apksigner`.
- Sem emulador/dispositivo: interface não validada em execução.
- Push para main confirmado. API GitHub retornou Forbidden ao consultar Actions/Pages e ao criar Release. Publicação externa não confirmada.
