# Meus Gastos (app Android pessoal)

App simples que lê as notificações dos seus bancos, registra cada gasto e separa por categoria.
O que ele não reconhecer vai para **❓ Pendentes**, e você só toca na categoria certa. Ele aprende
e, da próxima vez, classifica sozinho. Seus dados ficam **só no seu celular**.

---

## Passo 1: colocar o projeto no GitHub (uma vez só)

1. Entre em https://github.com e crie uma conta (é grátis).
2. Clique em **New repository** (botão verde "New").
   - Nome: `meus-gastos`
   - Marque **Private** (privado)
   - Clique em **Create repository**.
3. Na página do repositório, clique em **uploading an existing file**.
4. Descompacte o arquivo `meus-gastos.zip` no computador. Abra a pasta `meus-gastos`,
   selecione **tudo o que está dentro dela** e arraste para a página do GitHub.
5. Clique em **Commit changes**.

> ⚠️ A pasta `.github` costuma ficar oculta no computador. Se ela não subir, crie o arquivo
> à mão: **Add file → Create new file**, digite o nome `.github/workflows/build.yml`, cole
> o conteúdo do arquivo `build.yml` e clique em **Commit changes**.

## Passo 2: o GitHub gera o app (automático)

1. Clique na aba **Actions**. Vai aparecer "Gerar APK" rodando (bolinha amarela).
2. Espere uns 5 a 10 minutos até ficar ✅ verde.
3. Clique nessa execução e, embaixo, em **Artifacts**, clique em **meus-gastos-apk**.
4. Vai baixar um `.zip`. Dentro dele está o `app-debug.apk`.

> Se ficar ❌ vermelho: clique na execução, depois em "build", copie as linhas de erro
> e envie para eu corrigir.

## Passo 3: instalar no celular

1. Mande o `app-debug.apk` para o celular (WhatsApp para você mesmo, Google Drive ou cabo USB).
2. Toque no arquivo. Se o Android pedir, permita **"Instalar apps desconhecidos"** para esse app.
3. Toque em **Instalar**. Se aparecer aviso do Play Protect, toque em **Instalar mesmo assim**
   (aparece porque o app não veio da Play Store).

## Passo 4: liberar a leitura das notificações

Abra o **Meus Gastos → ⚙️ Ajustes** e siga os botões da tela:

1. **Liberar configurações restritas.** Abre as informações do app. Toque no menu **⋮** (canto
   de cima) e em **Permitir configurações restritas**. Se não aparecer, pule.
2. **Ativar leitura.** Ligue o **Meus Gastos** na lista e confirme.
3. Confira se os seus bancos estão marcados em **Quais apps de banco ler**.

Para testar sem gastar nada, use os botões **Compra conhecida** e **Compra desconhecida**
em Ajustes.

## Se um gasto não for reconhecido

Em **⚙️ Ajustes → Textos que o app recebeu**, os itens com ✖ não viraram gasto.
Toque em **Copiar tudo**, apague nomes e números e envie o texto para ajustar o leitor.

## Atualizar o app

Quando houver uma nova versão do código, suba os arquivos alterados no GitHub (Passo 1, itens 3 a 5).
O APK novo aparece em **Actions**. Instale por cima: os dados continuam lá.
