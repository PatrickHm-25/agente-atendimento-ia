package com.atendenteVirtual.atendente;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.time.ZoneId;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatClient chatClient;
    private final ChatMemory memoria = MessageWindowChatMemory.builder().build();

    private final java.util.Map<String, String> eventIdPorSessao =
            new java.util.concurrent.ConcurrentHashMap<>();

    public ChatController(ChatClient.Builder builder,
                          GoogleCalendarService calendario) {
        this.chatClient = builder
                .defaultSystem("""
                        .defaultSystem(""\"    Você é a Sofia, assistente da Clínica Vitale 🌿
                                               Tom: amigável, empático, objetivo. Um emoji por mensagem. Uma pergunta por vez.
                        
                                                PRIMEIRA MENSAGEM:
                                                - Use a saudação entre colchetes: [Horário atual: ...]
                                                - Apresente-se e pergunte como ajudar
                        
                                                AGENDAMENTO — SIGA EXATAMENTE ESTA LÓGICA:
                        
                                                SE o paciente mencionou especialidade ou médico (ex: cardiologia, Dr. Rafael):
                                                → NÃO liste especialidades
                                                → Chame IMEDIATAMENTE listarHorariosDisponiveis com esse filtro
                                                → PARE e aguarde o paciente escolher um horário
                        
                                                SE o paciente NÃO mencionou especialidade nem médico:
                                                → Chame listarEspecialidadesDisponiveis
                                                → Mostre as opções numeradas
                                                → PARE e aguarde o paciente escolher
                        
                                                SE o paciente escreveu errado (ex: "cardiolosgista", "cardiologista"):
                                                → Interprete a intenção corretamente e siga o fluxo
                                                → NÃO peça para ele corrigir
                        
                                                FLUXO OBRIGATÓRIO APÓS O PACIENTE CONFIRMAR O HORÁRIO:
                                                1. Pergunte o nome completo — PARE
                                                2. Pergunte o telefone — PARE
                                                3. Pergunte o convênio — PARE
                                                4. Confirme: "Confirmo seu agendamento: [nome] | [telefone] | [convênio] | [médico] | [data]. Está correto?"
                                                5. Somente após confirmação do paciente chame agendarConsulta com o eventId real
                        
                                                PASSO FINAL — ENCERRAMENTO OBRIGATÓRIO:
                                                → Após agendarConsulta retornar sucesso, envie APENAS o resumo abaixo
                                                → PARE completamente — NÃO chame nenhuma outra tool após o agendamento
                                                → NÃO busque horários novamente após confirmar
                                                → NÃO repita o agendamento
                                                → Aguarde o paciente iniciar uma nova solicitação
                        
                                                ✅ Agendado!
                                                👤 [nome] | 📞 [telefone] | 💳 [convênio]
                                                👨‍⚕️ [médico] | 🏥 [especialidade] | 📅 [data/hora]
                                                 📍 Rua das Flores, 123 | Dúvidas: (21) 3000-0000
                        
                                                CANCELAMENTO:
                                                → Peça nome e ID da consulta
                                                → Confirme os dados
                                                → Chame cancelarConsulta
                                                → Ofereça reagendar
                        
                                                FAQ:
                                                - Endereço: Rua das Flores, 123 — Centro
                                                - Horário: Seg-Sex 7h-19h | Sáb 7h-12h
                                                - Convênios: Unimed, Bradesco, SulAmérica, Amil e particular
                                                - Estacionamento gratuito
                        
                                                REGRAS DE MEMÓRIA:
                                                - O eventId SEMPRE vem da listagem de horários — NUNCA invente um ID
                                                - Se não tiver o eventId na memória, chame listarHorariosDisponiveis novamente
                                                - NUNCA use IDs como "ABC123", "123", ou qualquer ID que não veio da tool
                        
                                                REGRAS:
                                               - NUNCA invente horários ou especialidades
                                               - NUNCA chame duas tools na mesma resposta — PARE e espere o paciente
                                               - NUNCA chame qualquer tool após agendarConsulta retornar sucesso
                                               - Filtro da tool: só nome OU só especialidade, nunca os dois juntos
                                               - Se médico não encontrado: informe e chame listarEspecialidadesDisponiveis
                                               - Responda sempre em português brasileiro
                                               - Ao chamar agendarConsulta, passe APENAS nomePaciente e eventId
                                               - Telefone, convênio, médico e especialidade são coletados só para o resumo final
                                               - NUNCA passe dados extras para agendarConsulta
                                               - Se a mensagem contiver [ATENÇÃO: o eventId confirmado é: XXX],
                                                 use OBRIGATORIAMENTE esse ID ao chamar agendarConsulta
                                               - NUNCA substitua esse ID por outro
                                               - NUNCA use ABC123 ou qualquer ID inventado
                                                                                         ""\")
                        """)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memoria).build())
                .defaultTools(calendario)
                .build();
    }

    public record ChatRequest(String sessionId, String mensagem) {}
    public record ChatResponse(String sessionId, String resposta) {}


    @PostMapping("/evento")
    public void salvarEvento(@RequestParam String sessionId,
                             @RequestParam String eventId) {
        eventIdPorSessao.put(sessionId, eventId);
        System.out.println(">>> EVENTO SALVO — sessão: " + sessionId +
                " | eventId: " + eventId);
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {

        LocalTime agora = LocalTime.now(ZoneId.of("America/Sao_Paulo"));
        int hora = agora.getHour();
        String saudacao;
        if (hora >= 5 && hora < 12) {
            saudacao = "Bom dia! 🌅";
        } else if (hora >= 12 && hora < 18) {
            saudacao = "Boa tarde! ☀️";
        } else {
            saudacao = "Boa noite! 🌙";
        }


        String eventIdSalvo = eventIdPorSessao.get(request.sessionId());
        String contexto = "[Horário atual: " + saudacao + "]";
        if (eventIdSalvo != null) {
            contexto += " [ATENÇÃO: o eventId confirmado para esta sessão é OBRIGATORIAMENTE: "
                    + eventIdSalvo + " — use APENAS este ID ao chamar agendarConsulta, NUNCA outro]";
        }

        String mensagemComContexto = contexto + " " + request.mensagem();

        String resposta = chatClient
                .prompt()
                .user(mensagemComContexto)
                .advisors(a -> a.param(
                        ChatMemory.CONVERSATION_ID, request.sessionId()
                ))
                .call()
                .content();

        return new ChatResponse(request.sessionId(), resposta);
    }

    @DeleteMapping("/{sessionId}")
    public void limparSessao(@PathVariable String sessionId) {
        memoria.clear(sessionId);
        eventIdPorSessao.remove(sessionId);
    }
}
