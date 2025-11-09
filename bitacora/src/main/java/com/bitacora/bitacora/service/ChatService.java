package com.bitacora.bitacora.service;

import com.bitacora.bitacora.model.Proyecto;
import com.bitacora.bitacora.repository.ProyectoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ChatService {

    @Value("${openai.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ProyectoRepository proyectoRepository;

    // ✅ ObjectMapper con soporte para Java 8 LocalDateTime
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public ChatService(ProyectoRepository proyectoRepository) {
        this.proyectoRepository = proyectoRepository;
    }

    public String procesarMensaje(String mensajeUsuario) {
        String fechaActual = LocalDateTime.now().withNano(0).toString();

        String promptBase = """
Eres un asistente inteligente que ayuda a gestionar proyectos y tareas de una bitácora.
Tu trabajo es interpretar instrucciones en lenguaje natural y devolver un objeto JSON estructurado.

Usa las siguientes estructuras y reglas exactamente como se indican:

### Estructura para crear un proyecto:
{
  "nombre": string,
  "descripcion": string o null,
  "fechaInicio": fecha actual en formato "YYYY-MM-DDTHH:mm:ss",
  "fechaFin": fecha estimada si se menciona, o null (en formato "YYYY-MM-DDTHH:mm:ss"),
  "duracionHoras": número total de horas estimadas del proyecto (suma de las tareas),
  "fechaCreacion": fecha actual en formato "YYYY-MM-DDTHH:mm:ss",
  "tareas": [] lista de tareas asociadas si el usuario las menciona
}

### Estructura esperada para crear una tarea:
{
    "titulo": string,
    "descripcion": string o null,
    "estado": "pendiente" | "en_proceso" | "completado",
    "fechaInicio": fecha actual en formato "YYYY-MM-DDTHH:mm:ss",
    "fechaFin": fecha estimada si el usuario la menciona, o null (en formato "YYYY-MM-DDTHH:mm:ss"),
    "duracionHoras": número (horas estimadas),
    "observaciones": texto o null si no se especifica,
    "fechaCreacion": fecha actual en formato "YYYY-MM-DDTHH:mm:ss"
}

### Reglas:
- Devuelve **solo el JSON**, sin explicaciones ni texto adicional.
- Usa comillas dobles para todas las claves y valores de texto.
- Usa siempre el formato "YYYY-MM-DDTHH:mm:ss" para las fechas.
- Si no se menciona una fecha, usa la fecha actual: %s
- No inventes años anteriores al actual.
- Si el usuario pide varias tareas dentro de un proyecto, inclúyelas en el mismo bloque del proyecto.
- No devuelvas tareas fuera del formato definido.
""".formatted(fechaActual);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "gpt-4o-mini");
        body.put("temperature", 0.0);
        body.put("max_tokens", 800);

        body.put("messages", List.of(
                Map.of("role", "system", "content",
                        "Usa esta fecha actual para todo: " + fechaActual +
                                ". Responde solo en JSON siguiendo las reglas indicadas."),
                Map.of("role", "user", "content", promptBase + "\n\n" + mensajeUsuario)
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                "https://api.openai.com/v1/chat/completions",
                HttpMethod.POST,
                request,
                Map.class
        );

        Map<String, Object> choices = (Map<String, Object>) ((List<?>) response.getBody().get("choices")).get(0);
        Map<String, Object> message = (Map<String, Object>) choices.get("message");
        String contenido = ((String) message.get("content")).trim();

        if (contenido.startsWith("```")) {
            contenido = contenido.replaceAll("```json|```", "").trim();
        }

        contenido = normalizarFechas(contenido);

        try {
            Proyecto proyecto = objectMapper.readValue(contenido, Proyecto.class);

            if (proyecto.getTareas() != null) {
                proyecto.getTareas().forEach(t -> t.setProyecto(proyecto));
            }

            proyectoRepository.save(proyecto);
            return "✅ Proyecto creado y guardado correctamente: " + proyecto.getNombre();

        } catch (Exception e) {
            // 🚨 Mostrar detalles del error de parseo
            StringBuilder sb = new StringBuilder();
            sb.append("⚠️ Error al intentar guardar el proyecto.\n");
            sb.append("Tipo de error: ").append(e.getClass().getSimpleName()).append("\n");
            sb.append("Mensaje: ").append(e.getMessage()).append("\n\n");
            sb.append("JSON recibido:\n").append(contenido).append("\n");
            return sb.toString();
        }
    }

    private String normalizarFechas(String texto) {
        String ahora = LocalDateTime.now().withNano(0).toString();
        return texto.replaceAll("202[0-3]-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}", ahora);
    }
}
