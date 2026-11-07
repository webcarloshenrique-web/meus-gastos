# Meus Gastos: versão 2

App Android pessoal que lê as notificações dos bancos, registra cada gasto e separa por categoria.
Seus dados ficam **só no seu celular**.

**Novidades da versão 2**
- Menu lateral (botão ☰ no canto de cima, à esquerda) e Ajustes (⚙️ no canto de cima, à direita)
- Faixa de meses (JUL · AGO · SET · OUT · NOV) com o total de cada mês
- Relatórios: gráfico por mês, gráfico por dia, rosca por categoria e a seção "Entenda seus gastos"
- Calendário colorido: quanto mais escuro o dia, mais você gastou
- Toque em qualquer gasto para corrigir o valor, o nome, a categoria ou para apagar
- Exportação em CSV (Excel/Planilhas) com resumo, que também serve de backup
- "Testar o leitor" agora **não grava nada**, então seus gastos não mudam
- **Atualização automática:** o app avisa quando há versão nova e se atualiza sem perder dados

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
