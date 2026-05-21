package com.atendenteVirtual.atendente;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GoogleCalendarService {

    private final Calendar calendar;
    private final String calendarId;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String ZONE = "America/Sao_Paulo";

    public GoogleCalendarService(@Value("${GOOGLE_CALENDAR_ID}") String calendarId)
            throws Exception {

        this.calendarId = calendarId;
        System.out.println(">>> CALENDAR ID: " + calendarId);

        InputStream credentialsStream = getClass()
                .getResourceAsStream("/google-credentials.json");
        System.out.println(">>> CREDENTIALS STREAM: " + credentialsStream);

        GoogleCredentials credentials = GoogleCredentials
                .fromStream(credentialsStream)
                .createScoped(Collections.singletonList(
                        "https://www.googleapis.com/auth/calendar"
                ));

        this.calendar = new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials)
        ).setApplicationName("Clinica Vitale").build();

        System.out.println(">>> GOOGLE CALENDAR CONECTADO COM SUCESSO!");
    }

    @Tool(description = """
            SEMPRE use esta ferramenta quando o paciente perguntar sobre horários
            ou disponibilidade. NUNCA invente horários.
            Parâmetro: filtro — nome do médico ou especialidade.
            Exemplos: "Rafael", "Cardiologia", "" para todos.
            """)
    public String listarHorariosDisponiveis(String filtro) {
        try {
            if (filtro != null) {
                String f = filtro.toLowerCase().trim();
                if (f.contains("cardio")) {
                    filtro = "Cardiologia";
                } else if (f.contains("pediatr")) {
                    filtro = "Pediatria";
                } else if (f.contains("ortop")) {
                    filtro = "Ortopedia";
                } else if (f.contains("dermato")) {
                    filtro = "Dermatologia";
                } else if (f.contains("clinico") || f.contains("clínico") || f.contains("geral")) {
                    filtro = "Clinico Geral";
                } else if (f.contains("epato") || f.contains("hepato")) {
                    filtro = "Epatologia";
                }
            }

            System.out.println(">>> BUSCANDO HORÁRIOS — filtro normalizado: " + filtro);

            DateTime agora = new DateTime(System.currentTimeMillis());
            DateTime em30dias = new DateTime(
                    System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
            );

            String query = "DISPONIVEL";
            if (filtro != null && !filtro.isBlank()) {
                query += " " + filtro;
            }

            System.out.println(">>> QUERY: " + query);

            Events events = calendar.events().list(calendarId)
                    .setTimeMin(agora)
                    .setTimeMax(em30dias)
                    .setQ(query)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute();

            List<Event> items = events.getItems();
            System.out.println(">>> EVENTOS ENCONTRADOS: " + items.size());

            if (items.isEmpty()) {
                return "Não há horários disponíveis para '" + filtro +
                        "' nos próximos 30 dias. Posso verificar outra opção?";
            }

            return "Horários disponíveis:\n" +
                    items.stream()
                            .map(e -> {
                                LocalDateTime dt = Instant
                                        .ofEpochMilli(e.getStart().getDateTime().getValue())
                                        .atZone(ZoneId.of(ZONE))
                                        .toLocalDateTime();
                                String[] partes = e.getSummary().split(" - ");
                                String medico = partes.length > 1 ? partes[1] : "Médico";
                                String esp = partes.length > 2 ? partes[2] : "";
                                return "• " + dt.format(FMT) + " | " + medico +
                                        " | " + esp + " (ID: " + e.getId() + ")";
                            })
                            .collect(Collectors.joining("\n"));

        } catch (Exception ex) {
            System.out.println(">>> ERRO AO LISTAR: " + ex.getMessage());
            ex.printStackTrace();
            return "Não consegui acessar a agenda. Tente novamente.";
        }
    }

    @Tool(description = """
            Lista todas as especialidades médicas disponíveis no calendário agora.
            Use quando o paciente não informar especialidade ou quando não houver
            horários para a especialidade pedida.
            Não precisa de parâmetros — passa string vazia.
            """)
    public String listarEspecialidadesDisponiveis(String ignored) {
        try {
            System.out.println(">>> LISTANDO ESPECIALIDADES DISPONÍVEIS");

            DateTime agora = new DateTime(System.currentTimeMillis());
            DateTime em30dias = new DateTime(
                    System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
            );

            Events events = calendar.events().list(calendarId)
                    .setTimeMin(agora)
                    .setTimeMax(em30dias)
                    .setQ("DISPONIVEL")
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute();

            List<Event> items = events.getItems();

            if (items.isEmpty()) {
                return "Não há horários disponíveis no momento. "
                        + "Entre em contato pelo (21) 3000-0000.";
            }

            String especialidades = items.stream()
                    .map(e -> {
                        String[] partes = e.getSummary().split(" - ");
                        return partes.length > 2 ? partes[2].trim() : "";
                    })
                    .filter(e -> !e.isBlank())
                    .distinct()
                    .sorted()
                    .map(e -> "• " + e)
                    .collect(Collectors.joining("\n"));

            return "Especialidades disponíveis no momento:\n" + especialidades;

        } catch (Exception ex) {
            System.out.println(">>> ERRO AO LISTAR ESPECIALIDADES: " + ex.getMessage());
            return "Não consegui verificar as especialidades agora. "
                    + "Ligue para (21) 3000-0000.";
        }
    }

    @Tool(description = """
            Agenda uma consulta atualizando o evento no Google Calendar.
            Aceita APENAS dois parâmetros:
            - nomePaciente: nome completo do paciente
            - eventId: ID do evento escolhido que veio da listagem de horários
            NUNCA passe telefone, convênio, especialidade, médico, data ou hora.
            """)
    public String agendarConsulta(String nomePaciente, String eventId) {
        try {
            System.out.println(">>> AGENDANDO — paciente: " + nomePaciente
                    + " | eventId: " + eventId);

            Event evento = calendar.events().get(calendarId, eventId).execute();

            if (!evento.getSummary().startsWith("DISPONIVEL")) {
                return "Este horário já foi reservado. Vou buscar outro para você!";
            }

            String[] partes = evento.getSummary().split(" - ");
            String medico = partes.length > 1 ? partes[1] : "Médico";
            String especialidade = partes.length > 2 ? partes[2] : "";

            LocalDateTime dt = Instant
                    .ofEpochMilli(evento.getStart().getDateTime().getValue())
                    .atZone(ZoneId.of(ZONE))
                    .toLocalDateTime();

            evento.setSummary("AGENDADO - " + nomePaciente + " - " + medico
                    + " - " + especialidade);
            evento.setDescription("Paciente: " + nomePaciente
                    + "\nMédico: " + medico
                    + "\nAgendado via Sofia - Clínica Vitale");

            calendar.events().update(calendarId, eventId, evento).execute();

            System.out.println(">>> CONSULTA AGENDADA COM SUCESSO!");

            return "Consulta agendada! Médico: " + medico
                    + " | Especialidade: " + especialidade
                    + " | Data: " + dt.format(FMT);

        } catch (Exception ex) {
            System.out.println(">>> ERRO AO AGENDAR: " + ex.getMessage());
            ex.printStackTrace();
            return "Não consegui finalizar o agendamento. "
                    + "Ligue para (21) 3000-0000.";
        }
    }

    @Tool(description = """
            Cancela uma consulta já agendada no Google Calendar.
            Use quando o paciente quiser cancelar um agendamento existente.
            Parâmetros:
            - nomePaciente: nome do paciente
            - eventId: ID do evento a cancelar
            """)
    public String cancelarConsulta(String nomePaciente, String eventId) {
        try {
            System.out.println(">>> CANCELANDO — paciente: " + nomePaciente
                    + " | eventId: " + eventId);

            Event evento = calendar.events().get(calendarId, eventId).execute();

            String[] partes = evento.getSummary().split(" - ");
            String medico = partes.length > 2 ? partes[2] : "Médico";
            String especialidade = partes.length > 3 ? partes[3] : "";

            evento.setSummary("DISPONIVEL - " + medico + " - " + especialidade);
            evento.setDescription("Horário liberado por cancelamento.");

            calendar.events().update(calendarId, eventId, evento).execute();

            System.out.println(">>> CONSULTA CANCELADA, HORÁRIO LIBERADO!");

            return "Consulta cancelada com sucesso. O horário foi liberado. "
                    + "Quando quiser reagendar é só chamar!";

        } catch (Exception ex) {
            System.out.println(">>> ERRO AO CANCELAR: " + ex.getMessage());
            ex.printStackTrace();
            return "Não consegui cancelar agora. "
                    + "Ligue para (21) 3000-0000 para cancelar manualmente.";
        }
    }
}