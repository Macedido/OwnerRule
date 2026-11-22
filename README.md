# 👑 OwnerRules

Launcher Android com **Modo Dono** (reconhecimento de rosto) e **Modo Visitante**.

## Como gerar o APK (sem PC)
1. Crie um repositório no GitHub (ex.: `OwnerRules`).
2. Envie TODOS os arquivos deste zip (incluindo a pasta `.github`).
3. Aba **Actions** → espere o "Gerar APK" ficar verde (~5 min).
4. Aba **Releases** → baixe `OwnerRules.apk` e instale.

## Primeiro uso
1. Abra o app → Regras → ative **Tela inicial padrão** e **Mostrar sobre outros apps**.
2. Cadastre seu rosto e um PIN de reserva.
3. Segure um app na tela inicial para escondê-lo do visitante, pausá-lo ou colocá-lo na Lista Boom.

## Controle total (pausar apps com 1 clique)
Uma vez só, pelo PC com ADB (remova as contas do celular antes, depois coloque de volta):
```
adb shell dpm set-device-owner com.ownerrules/.AdminReceiver
```
Para desfazer: Apps → "Desativar controle total".
