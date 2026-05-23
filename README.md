# Agente de IA para Atendimento de Consultórios

Agente de IA desenvolvido em Java com Spring Boot que realiza atendimento automatizado para consultórios médicos, integrando-se ao Google Calendar em tempo real para verificar disponibilidade e agendar consultas.

## Funcionalidades

- Atendimento via API REST com memória de conversa por sessão
- Verificação de horários disponíveis no Google Calendar em tempo real
- Agendamento e cancelamento de consultas com atualização automática do calendário
- Listagem de especialidades médicas disponíveis
- Coleta de dados do paciente (nome, telefone, convênio)
- Resumo completo ao final do agendamento
- Saudação personalizada por horário do dia
- Normalização de termos (ex: "cardiologista" → busca por "Cardiologia")

## Stack

- Java 21
- Spring Boot 4
- Spring AI
- Groq API (LLaMA 3.3 70B)
- Google Calendar API com Service Account
- Docker
- Railway (deploy)

## Como funciona

O agente utiliza Tool Calling — quando o paciente pergunta sobre horários, a IA chama automaticamente um método Java que consulta o Google Calendar e retorna dados reais. Isso garante que nenhuma informação seja inventada.

```
Paciente → ChatController → Sofia (IA)
                                ↓
                    Chama listarHorariosDisponiveis()
                                ↓
                        Google Calendar API
                                ↓
                    Retorna horários reais
                                ↓
                    Sofia responde com dados reais
```

## Como executar localmente

### Pré-requisitos

- Java 21
- Maven
- Conta no Groq (gratuita): console.groq.com
- Projeto no Google Cloud com Calendar API ativada

### Configuração

1. Clone o repositório:
```bash
git clone
https://github.com/PatrickHm-25/agente-atendimento-ia
cd agente-atendimento-ia
```

2. Crie o arquivo `.env` na raiz com:
```
GROQ_API_KEY=sua_chave_aqui
GOOGLE_CALENDAR_ID=seu_calendar_id@group.calendar.google.com
```

3. Adicione o arquivo `google-credentials.json` em `src/main/resources/`

4. Execute:
```bash
mvn spring-boot:run
```

### Testando

Endpoint disponível em `http://localhost:8080/chat`

```json
POST /chat
{
  "sessionId": "paciente-001",
  "mensagem": "Olá, quero agendar uma consulta"
}
```

## Padrão dos eventos no Google Calendar

```
DISPONIVEL - Dr. Rafael - Cardiologia
DISPONIVEL - Dra. Maria - Pediatria
```

Após o agendamento, o agente atualiza automaticamente para:

```
AGENDADO - Patrick Medeiros - Dr. Rafael - Cardiologia
```

## Deploy

A aplicação está configurada para deploy via Docker no Railway. As variáveis de ambiente necessárias em produção são:

```
GROQ_API_KEY
GOOGLE_CALENDAR_ID
GOOGLE_CREDENTIALS_BASE64
```

## Aprendizados

Este projeto foi desenvolvido do zero como parte do meu aprendizado em Java. Os principais conceitos aplicados foram Tool Calling com Spring AI, autenticação via Service Account do Google, deploy com Docker e depuração de APIs em produção.

## Autor

Patrick Henrique  
[LinkedIn](https://www.linkedin.com/in/patrick-medeiros/) 
