# Rota Entregas

App Android (Kotlin + Jetpack Compose) para entregadores: fotografa os endereços das
entregas, reconhece automaticamente os dados por OCR, monta a melhor rota a partir da
posição atual e acompanha o faturamento por dia/semana/mês.

## Como baixar o APK (sem precisar instalar Android Studio)

Este repositório não tem o Android SDK disponível no ambiente onde o código foi gerado,
então o APK é compilado automaticamente pelo GitHub Actions a cada push nesta pasta:

1. Vá em **Actions** → workflow **"Gerar APK - Rota Entregas"** neste repositório.
2. Abra a execução mais recente da sua branch.
3. Baixe o artifact **`rota-entregas-debug-apk`** — é um `.zip` com o `app-debug.apk` dentro.
4. Copie o APK para o celular e instale (é preciso permitir "instalar de fontes
   desconhecidas" para APKs de debug, já que ele não está assinado pela Play Store).

Se preferir compilar você mesmo com o Android Studio: abra a pasta `rota-entregas/`
como projeto e rode `Build > Build APK(s)`, ou via terminal: `./gradlew assembleDebug`.

## Funcionalidades

- **Importar fotos** (câmera ou galeria, várias de uma vez, em qualquer orientação) e
  reconhecer automaticamente com OCR 100% on-device (ML Kit, funciona sem internet):
  rua, número, complemento, bairro, cidade/UF, CEP e quantidade de pacotes.
- **Revisão dos endereços**: quando falta um dado essencial (número, CEP) para montar a
  rota, o app pede confirmação — por CEP digitado (preenche o resto via ViaCEP) ou por
  uma nova foto, sem travar a importação dos demais endereços.
- **Roteirização automática** a partir da posição atual: heurística do vizinho mais
  próximo + refinamento 2-opt, usando o **custo real de deslocamento pelas ruas** (via
  OSRM) sempre que possível — isso evita o problema citado no pedido original, de um
  endereço parecer perto em linha reta mas exigir contorno por causa de mão única.
  Sem internet, cai automaticamente para distância em linha reta (fica visível na tela).
- **Mapa** (osmdroid/OpenStreetMap, sem depender de Google Maps): pinos coloridos —
  vermelho para a próxima parada, azul para as demais pendentes, amarelo para entregas
  que falharam (ficam **fora** da sequência da rota até serem reenviadas manualmente),
  verde para entregues.
- **Recalculo dinâmico**: a posição do entregador é monitorada continuamente; quando o
  deslocamento é grande o suficiente, a rota pendente é recalculada automaticamente,
  como em um app de navegação.
- **Confirmação de entrega**: ao tocar no pino, é possível confirmar a entrega (segue
  para a próxima) ou marcar falha (fica amarela e desconectada, com opção de reenviar).
- **Painel de acompanhamento**: totais de entregas e faturamento (quantidade × valor por
  pacote) com visão diária, semanal e mensal.
- **Configurações**: valor por pacote (padrão R$ 2,00, editável) e URL do servidor OSRM.

## Limitações conhecidas / próximos passos

- **Mapa offline**: o osmdroid guarda em cache os tiles já visualizados, então uma área
  visitada uma vez com internet volta a funcionar offline depois. Não há, ainda, uma
  tela para importar um pacote de mapa (`.mbtiles`) de uma região inteira antes de sair
  sem sinal — é a melhoria mais natural a fazer a seguir se isso for necessário.
- **Roteamento 100% offline por ruas**: sem internet, a rota cai para linha reta
  (Haversine), então não considera mão única nem necessidade de contorno nesse modo.
  Uma rota real offline exigiria embarcar um motor de roteamento (ex.: GraphHopper) com
  os dados de mapa (`.osm.pbf`) da região de entrega — viável, mas é bem mais pesado
  para o tamanho do APK, por isso ficou fora do escopo inicial.
- O parser de endereço é baseado em heurísticas/regex sobre o texto do OCR; funciona bem
  para etiquetas com layout razoavelmente padrão, mas texto muito degradado pode exigir
  a confirmação manual (CEP ou nova foto) — que já está implementada para esse caso.
- OSRM está configurado para o servidor público de demonstração
  (`router.project-osrm.org`), que tem limite de uso. Para uso diário intenso, hospede
  sua própria instância OSRM e troque a URL em Configurações.

## Estrutura do projeto

```
rota-entregas/
  app/src/main/java/dev/carlosalberto/rotaentregas/
    data/
      db/            Room (paradas + histórico de entregas)
      parser/        OCR (ML Kit) + extração de endereço por regex
      geocode/       ViaCEP + Nominatim
      route/         Motor de rota (OSRM/Haversine, vizinho mais próximo + 2-opt)
      location/      Rastreamento de posição (recalculo dinâmico)
      repository/    Regras de negócio (paradas, entregas, agregações do painel)
      settings/      Preferências (valor por pacote, servidor OSRM)
    ui/
      importar/      Captura/seleção de fotos + OCR
      revisao/       Confirmação de endereços incompletos
      mapa/          Mapa, rota, confirmação de entrega
      painel/        Dashboard diário/semanal/mensal
      config/        Configurações
```
