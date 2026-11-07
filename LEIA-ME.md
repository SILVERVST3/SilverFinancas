# SilverFinanças para Windows (.exe)

Mesmas funções do app do celular: início com saldo e previsão, lançamentos (fixos, parcelados, transferências, cartão),
contas com reajuste de saldo, cartões com faturas e pagamento, categorias/subcategorias, limites de gastos, relatórios,
importação de extrato OFX/CSV com categorização automática, anexos de comprovantes, backup e exportação CSV.

Diferenças no PC:
- Menu lateral (Início, Lançamentos, Relatórios, Contas, Cartões, Limites, Mais). Tecla **Esc** volta.
- Bloqueio por **PIN** (no lugar da digital).
- Alertas de contas a pagar aparecem como notificação do Windows ao abrir o programa.
- Recibos: anexa um arquivo (foto/PDF) do computador; a leitura automática por câmera fica só no celular.
- Dados em `%APPDATA%\SilverFinancas\dados.json` (+ cópia `dados.bak`).
- **Celular ⇄ PC:** o backup é o mesmo. Faça backup no celular, copie o .json para o PC e use
  *Mais › Configurações › Restaurar backup* (ou o contrário).

## Como gerar o .exe no GitHub
1. Crie um repositório novo e envie todos os arquivos desta pasta (inclusive `icon.ico`).
2. **Add file → Create new file** → nome `.github/workflows/build.yml` → cole o conteúdo de `COLAR_NO_GITHUB/build.yml` → Commit.
3. Na aba **Actions**, ao terminar (~8–12 min), baixe:
   - **SilverFinancas-instalador** → instalador `.exe` (cria atalho no menu Iniciar e na área de trabalho);
   - **SilverFinancas-portatil** → zip com a pasta `SilverFinancas`, é só abrir `SilverFinancas.exe` (não precisa instalar).
