# Meus Gastos: versão 3

## Novidades da versão 3
- **💰 Entradas:** registre salário e o que recebe. Pix recebidos, depósitos e salário avisados pelo banco entram sozinhos (pode desligar em Ajustes).
  O app mostra quanto **entrou, saiu e sobra**, a previsão de sobra no fim do mês e **quanto você pode gastar por dia**.
- **🎯 Limites por categoria:** defina um limite mensal por categoria. O app avisa ao chegar em 50/60/70/80/90% (você escolhe) e de novo ao passar de 100%.
- **💳 Compras parceladas:**
  - Pelo banco: "R$ 300,00 em 3x" vira 3 parcelas de R$ 100,00, uma em cada mês. "10x de R$ 50,00" vira 10 parcelas de R$ 50,00. "Parcela 3/10" lança só essa parcela.
  - À mão: em Adicionar, ligue **Compra parcelada?**, informe o valor (da parcela ou o total), em quantas vezes e **qual parcela você pagou por último**.
    O app lança essa parcela e as que faltam nos próximos meses. Se quiser, lança também as anteriores.
  - A faixa de meses agora mostra os **próximos meses** (⏳) com as parcelas que vão cair.
  - Ao apagar uma parcela, você escolhe: só ela ou a compra inteira.
- **📤 Exportar / Backup** agora tem tela própria no menu, fora de Ajustes. O CSV inclui parcelas e entradas.
- **🏷️ Categorias personalizadas:** crie, renomeie, troque ícone e cor (Menu → Categorias).
- **🎨 Cores:** Verde Dinheiro, Azul Banco, Ouro, Petróleo, Grafite e Bordô (Ajustes → Cores do app).
- **Valor sem vírgula:** digite só números. 3 → R$ 0,03 · 333 → R$ 3,33 · 2590 → R$ 25,90.
- **Relatórios:** "Entenda seus gastos" agora fica recolhido; toque para abrir a análise e a previsão. Nova aba **💰 Sobra** (entradas × gastos).
- **📲 Indicar o app:** Ajustes → Mais opções → Indicar o app.

## Como atualizar para a versão 3
1. No GitHub: **Add file → Upload files**, arraste a pasta **`app`** e o **`LEIA-ME.md`** → **Commit changes**.
2. Espere a aba **Actions** ficar ✅.
3. Abra o app no celular: aparece **"Nova versão disponível" → Atualizar agora**. Seus gastos continuam salvos.
   (Se você ainda não instalou a versão 2, siga primeiro a configuração única abaixo.)

---


## Configuração única (só desta vez)

### 1. Guardar a chave do app no GitHub
A chave garante que cada versão nova instale **por cima** da anterior, mantendo seus gastos.

1. No repositório `meus-gastos`, vá em **Settings** (aba de cima).
2. No menu da esquerda: **Secrets and variables → Actions**.
3. Clique em **New repository secret**.
   - **Name:** `KEYSTORE_BASE64`
   - **Secret:** abra o arquivo `KEYSTORE_BASE64.txt` (enviado junto com este guia), copie **todo** o texto e cole aqui.
4. Clique em **Add secret**.

> 🔒 Guarde o `KEYSTORE_BASE64.txt` num lugar seguro e **não** o envie para o repositório.
> Se perder esse arquivo, as próximas versões não vão instalar por cima.

### 2. Deixar o repositório público
É o que permite ao app consultar se há versão nova. Só o **código** fica visível; seus gastos nunca saem do celular.

1. **Settings → General**, role até o fim (**Danger Zone**).
2. **Change repository visibility → Change to public** e confirme.

### 3. Subir os arquivos novos
1. Descompacte o `meus-gastos.zip`.
2. No repositório: **Add file → Upload files**.
3. Arraste a pasta **`app`** inteira e os arquivos **`LEIA-ME.md`**, **`build.gradle.kts`**, **`settings.gradle.kts`**, **`gradle.properties`** e **`.gitignore`**. Os que já existem são substituídos.
4. Clique em **Commit changes**.
5. Atualize o arquivo do GitHub Actions:
   - Clique na pasta **`.github/workflows`** e depois em **`build.yml`**.
   - Clique no ✏️ (lápis, canto direito), apague todo o conteúdo e cole o do novo `build.yml` (está dentro do zip, em `.github/workflows/build.yml`).
   - Clique em **Commit changes**.

### 4. Baixar a nova versão
1. Aba **Actions**: espere a execução mais recente ficar ✅ verde (5 a 10 minutos).
2. Na página inicial do repositório, à direita, em **Releases**, clique na versão mais nova
   e baixe o **`meus-gastos.apk`**.

### 5. Instalar no celular (desta vez é diferente)
Como a chave mudou, o Android **não** deixa instalar por cima da versão 1:

1. ⚠️ **Anote os gastos que você já lançou**: a versão 1 não tem exportação e eles serão perdidos.
2. Desinstale o Meus Gastos atual.
3. Desligue o Play Protect (Play Store → foto → Play Protect → ⚙️ → desligar "Verificar apps").
4. Instale o `meus-gastos.apk` novo.
5. Religue o Play Protect.
6. Abra o app → ⚙️ Ajustes → ative de novo a leitura das notificações.

---

## Daqui para frente: atualizar é automático

1. Quando houver melhorias, suba os arquivos alterados no GitHub (passo 3).
2. O GitHub gera e publica a versão sozinho.
3. Ao abrir o app, aparece **"🎉 Nova versão disponível"** → **Atualizar agora**.
   - Na primeira vez, o Android pede para **permitir instalar apps do Meus Gastos**. Ligue e volte.
   - O Android sempre pede uma confirmação final ("Atualizar"). É uma regra do sistema.
4. Seus gastos continuam lá.

> Se o Play Protect bloquear a atualização, faça como na instalação: desligue, atualize, religue.

Você também pode procurar manualmente em **⚙️ Ajustes → Atualização**.

## Backup recomendado
De vez em quando: **⚙️ Ajustes → Exportar gastos → Todos os gastos** e salve no Drive.
Se trocar de celular, use **Restaurar backup (CSV)**.

## Se um gasto do banco não for reconhecido
**⚙️ Ajustes → Textos que o app recebeu → Copiar tudo**. Apague nomes e números e envie para ajustar o leitor.
