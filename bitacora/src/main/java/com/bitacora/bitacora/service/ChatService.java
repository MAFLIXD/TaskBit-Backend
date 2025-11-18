package com.bitacora.bitacora.service;
import com.bitacora.bitacora.model.Proyecto;
import com.bitacora.bitacora.model.Tarea;
import com.bitacora.bitacora.repository.ProyectoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ChatService {

    @Value("${openai.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ProyectoRepository proyectoRepository;
    private final TareaService tareaService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public ChatService(ProyectoRepository proyectoRepository, TareaService tareaService) {
        this.proyectoRepository = proyectoRepository;
        this.tareaService = tareaService;
    }

    public String procesarMensaje(String mensajeUsuario) {
        String fechaActual = LocalDateTime.now().withNano(0).toString();

        String promptBase = """
Eres un asistente inteligente que ayuda a gestionar proyectos y tareas de una bitácora.
Tu trabajo es interpretar instrucciones en lenguaje natural y devolver un objeto JSON estructurado.

### Acciones posibles:
- "crear": crear un proyecto o tarea.
- "actualizar": actualizar un proyecto o tarea existente.
- "eliminar": eliminar un proyecto o tarea existente.

### JSON esperado:
{
  "accion": "crear|actualizar|eliminar",
  "tipo": "proyecto|tarea",
  "nombre": string (para identificar proyectos y tareas por nombre/título),
  "proyecto": { ... },
  "tarea": { ... }
}

### Estructura para crear o actualizar un proyecto:
{
  "nombre": string,
  "descripcion": string o null,
  "fechaInicio": fecha actual en formato "YYYY-MM-DDTHH:mm:ss",
  "fechaFin": fecha estimada si se menciona, o null,
  "duracionHoras": número total de horas estimadas del proyecto (suma de las tareas),
  "fechaCreacion": fecha actual en formato "YYYY-MM-DDTHH:mm:ss",
  "tareas": [] lista de tareas asociadas si el usuario las menciona
}

### Estructura para crear o actualizar una tarea:
{
    "titulo": string,
    "descripcion": string o null,
    "estado": "pendiente" | "En progreso" | "Completada",
    "fechaInicio": fecha actual en formato "YYYY-MM-DDTHH:mm:ss",
    "fechaFin": fecha estimada si se menciona, o null,
    "duracionHoras": número (horas estimadas),
    "observaciones": texto o null si no se especifica,
    "fechaCreacion": fecha actual en formato "YYYY-MM-DDTHH:mm:ss"
}

### Reglas:
- Devuelve solo el JSON, sin explicaciones ni texto adicional.
- Usa comillas dobles para todas las claves y valores de texto.
- Usa siempre el formato "YYYY-MM-DDTHH:mm:ss" para las fechas.
- Si no se menciona una fecha, usa la fecha actual: %s
- No inventes años anteriores al actual.
- Para tareas, usa "nombre" para identificar la tarea por su título
- Las tareas pueden existir sin proyecto asociado (usa "proyecto": null)
""".formatted(fechaActual);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "gpt-4o-mini");
        body.put("temperature", 0.0);
        body.put("max_tokens", 1000);

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
        
        String contenido;
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.openai.com/v1/chat/completions",
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            Map<String, Object> choices = (Map<String, Object>) ((List<?>) response.getBody().get("choices")).get(0);
            Map<String, Object> message = (Map<String, Object>) choices.get("message");
            contenido = ((String) message.get("content")).trim();

        } catch (HttpClientErrorException.TooManyRequests e) {
            return "⚠️ Límite de uso excedido en OpenAI. Por favor espera unas horas o agrega un método de pago a tu cuenta.";
        } catch (HttpClientErrorException e) {
            return "⚠️ Error en la API de OpenAI: " + e.getStatusCode() + " - " + e.getStatusText();
        } catch (Exception e) {
            return "⚠️ Error de conexión con OpenAI: " + e.getMessage();
        }

        if (contenido.startsWith("```")) {
            contenido = contenido.replaceAll("```json|```", "").trim();
        }

        contenido = normalizarFechas(contenido);

        try {
            Map<String, Object> jsonMap = objectMapper.readValue(contenido, Map.class);
            String accion = (String) jsonMap.get("accion");
            String tipo = (String) jsonMap.get("tipo");
            String nombre = (String) jsonMap.get("nombre");
            String titulo = (String) jsonMap.get("titulo");

            //CREAR
            if ("crear".equalsIgnoreCase(accion)) {
                if ("proyecto".equalsIgnoreCase(tipo)) {
                    Proyecto proyecto = objectMapper.convertValue(jsonMap.get("proyecto"), Proyecto.class);
                    
                    //VALIDAR NOMBRE DUPLICADO EN CREACIÓN DE PROYECTO
                    if (proyecto.getNombre() == null || proyecto.getNombre().trim().isEmpty()) {
                        return "⚠️ El nombre del proyecto es requerido";
                    }
                    
                    boolean proyectoExiste = proyectoRepository.findAll().stream()
                            .anyMatch(p -> p.getNombre() != null && 
                                    p.getNombre().equalsIgnoreCase(proyecto.getNombre()));
                    if (proyectoExiste) {
                        return "⚠️ Ya existe un proyecto con el nombre: " + proyecto.getNombre();
                    }
                    
                    if (proyecto.getTareas() != null) proyecto.getTareas().forEach(t -> t.setProyecto(proyecto));
                    proyectoRepository.save(proyecto);
                    return "✅ Proyecto creado: " + proyecto.getNombre();
                    
                } else if ("tarea".equalsIgnoreCase(tipo)) {
                    Tarea tarea = objectMapper.convertValue(jsonMap.get("tarea"), Tarea.class);
                    
                    //VALIDAR TÍTULO DUPLICADO EN CREACIÓN DE TAREA
                    if (tarea.getTitulo() == null || tarea.getTitulo().trim().isEmpty()) {
                        return "⚠️ El título de la tarea es requerido";
                    }
                    
                    //Verificar si ya existe una tarea con el mismo título
                    boolean tareaExiste = tareaService.obtenerTodas().stream()
                            .anyMatch(t -> t.getTitulo() != null && 
                                    t.getTitulo().equalsIgnoreCase(tarea.getTitulo()) &&
                                    //Si ambas tareas tienen proyecto, verificar que sean diferentes
                                    (t.getProyecto() == null ? tarea.getProyecto() == null : 
                                     tarea.getProyecto() != null && 
                                     t.getProyecto().getId().equals(tarea.getProyecto().getId())));
                    
                    if (tareaExiste) {
                        String mensajeError = "⚠️ Ya existe una tarea con el título: " + tarea.getTitulo();
                        if (tarea.getProyecto() != null) {
                            mensajeError += " en el proyecto: " + tarea.getProyecto().getNombre();
                        }
                        return mensajeError;
                    }
                    
                    tareaService.guardar(tarea);
                    return "✅ Tarea creada: " + tarea.getTitulo() + 
                           (tarea.getProyecto() != null ? " en proyecto: " + tarea.getProyecto().getNombre() : " (sin proyecto)");
                }
            }

            //ACTUALIZAR 
            if ("actualizar".equalsIgnoreCase(accion)) {
                if ("proyecto".equalsIgnoreCase(tipo)) {
                    if (nombre == null || nombre.isEmpty()) return "⚠️ Nombre del proyecto es requerido para actualizar";
                    Optional<Proyecto> optProyecto = proyectoRepository.findAll().stream()
                            .filter(p -> p.getNombre() != null)
                            .filter(p -> p.getNombre().equalsIgnoreCase(nombre))
                            .findFirst();
                    if (optProyecto.isEmpty()) return "⚠️ Proyecto no encontrado: " + nombre;

                    Proyecto p = optProyecto.get();
                    Proyecto datos = objectMapper.convertValue(jsonMap.get("proyecto"), Proyecto.class);
                    
                    //VALIDAR NOMBRE DUPLICADO EN ACTUALIZACIÓN DE PROYECTO
                    if (datos.getNombre() != null && !datos.getNombre().equalsIgnoreCase(p.getNombre())) {
                        boolean nombreExiste = proyectoRepository.findAll().stream()
                                .anyMatch(proj -> proj.getNombre() != null && 
                                        proj.getNombre().equalsIgnoreCase(datos.getNombre()) &&
                                        !proj.getId().equals(p.getId()));
                        if (nombreExiste) {
                            return "⚠️ Ya existe otro proyecto con el nombre: " + datos.getNombre();
                        }
                        p.setNombre(datos.getNombre());
                    }
                    
                    if (datos.getDescripcion() != null) p.setDescripcion(datos.getDescripcion());
                    if (datos.getFechaInicio() != null) p.setFechaInicio(datos.getFechaInicio());
                    if (datos.getFechaFin() != null) p.setFechaFin(datos.getFechaFin());
                    proyectoRepository.save(p);
                    return "✅ Proyecto actualizado: " + p.getNombre();
                    
                } else if ("tarea".equalsIgnoreCase(tipo)) {
                    //USAR "nombre" EN LUGAR DE "titulo" PARA CONSISTENCIA
                    String tituloTarea = (titulo != null && !titulo.isEmpty()) ? titulo : nombre;
                    
                    if (tituloTarea == null || tituloTarea.isEmpty()) 
                        return "⚠️ Nombre/título de tarea es requerido para actualizar";
                    
                    Optional<Tarea> optTarea = tareaService.obtenerTodas().stream()
                            .filter(t -> t.getTitulo() != null)
                            .filter(t -> t.getTitulo().equalsIgnoreCase(tituloTarea))
                            .findFirst();
                    if (optTarea.isEmpty()) return "⚠️ Tarea no encontrada: " + tituloTarea;

                    Tarea t = optTarea.get();
                    Tarea datos = objectMapper.convertValue(jsonMap.get("tarea"), Tarea.class);
                    
                    //VALIDAR TÍTULO DUPLICADO EN ACTUALIZACIÓN DE TAREA
                    if (datos.getTitulo() != null && !datos.getTitulo().equalsIgnoreCase(t.getTitulo())) {
                        boolean tituloExiste = tareaService.obtenerTodas().stream()
                                .anyMatch(tarea -> tarea.getTitulo() != null && 
                                        tarea.getTitulo().equalsIgnoreCase(datos.getTitulo()) &&
                                        !tarea.getId().equals(t.getId()) &&
                                        // Si ambas tareas tienen proyecto, verificar que sean el mismo
                                        (tarea.getProyecto() == null ? t.getProyecto() == null : 
                                         t.getProyecto() != null && 
                                         tarea.getProyecto().getId().equals(t.getProyecto().getId())));
                        if (tituloExiste) {
                            String mensajeError = "⚠️ Ya existe otra tarea con el título: " + datos.getTitulo();
                            if (t.getProyecto() != null) {
                                mensajeError += " en el proyecto: " + t.getProyecto().getNombre();
                            }
                            return mensajeError;
                        }
                        t.setTitulo(datos.getTitulo());
                    }
                    
                    //Permitir actualizar proyecto a null (tarea sin proyecto)
                    if (datos.getProyecto() != null && datos.getProyecto().getNombre() != null) {
                        Optional<Proyecto> pOpt = proyectoRepository.findAll().stream()
                                .filter(pj -> pj.getNombre().equalsIgnoreCase(datos.getProyecto().getNombre()))
                                .findFirst();
                        pOpt.ifPresent(t::setProyecto);
                    } else if (jsonMap.get("tarea") instanceof Map) {
                        // 🔹 Si el JSON explícitamente tiene "proyecto": null, desasociar la tarea
                        Map<?, ?> tareaMap = (Map<?, ?>) jsonMap.get("tarea");
                        if (tareaMap.containsKey("proyecto") && tareaMap.get("proyecto") == null) {
                            t.setProyecto(null);
                        }
                    }
                    
                    if (datos.getDescripcion() != null) t.setDescripcion(datos.getDescripcion());
                    if (datos.getEstado() != null) t.setEstado(datos.getEstado());
                    if (datos.getFechaInicio() != null) t.setFechaInicio(datos.getFechaInicio());
                    if (datos.getFechaFin() != null) t.setFechaFin(datos.getFechaFin());
                    if (datos.getDuracionHoras() != null) t.setDuracionHoras(datos.getDuracionHoras());
                    if (datos.getObservaciones() != null) t.setObservaciones(datos.getObservaciones());
                    
                    tareaService.guardar(t);
                    return "✅ Tarea actualizada: " + t.getTitulo();
                }
            }

            //ELIMINAR
            if ("eliminar".equalsIgnoreCase(accion)) {
                if ("proyecto".equalsIgnoreCase(tipo)) {
                    if (nombre == null || nombre.isEmpty()) return "⚠️ Nombre del proyecto es requerido para eliminar";
                    Optional<Proyecto> optProyecto = proyectoRepository.findAll().stream()
                            .filter(p -> p.getNombre() != null)
                            .filter(p -> p.getNombre().equalsIgnoreCase(nombre))
                            .findFirst();
                    if (optProyecto.isPresent()) {
                        proyectoRepository.delete(optProyecto.get());
                        return "✅ Proyecto eliminado: " + nombre;
                    }
                    return "⚠️ Proyecto no encontrado: " + nombre;
                } else if ("tarea".equalsIgnoreCase(tipo)) {
                    //USAR "nombre" EN LUGAR DE "titulo" PARA CONSISTENCIA
                    String tituloTarea = (titulo != null && !titulo.isEmpty()) ? titulo : nombre;
                    
                    if (tituloTarea == null || tituloTarea.isEmpty()) 
                        return "⚠️ Nombre/título de tarea es requerido para eliminar";
                    
                    Optional<Tarea> optTarea = tareaService.obtenerTodas().stream()
                            .filter(t -> t.getTitulo() != null)
                            .filter(t -> t.getTitulo().equalsIgnoreCase(tituloTarea))
                            .findFirst();
                    if (optTarea.isPresent()) {
                        tareaService.eliminar(optTarea.get().getId());
                        return "✅ Tarea eliminada: " + tituloTarea;
                    }
                    return "⚠️ Tarea no encontrada: " + tituloTarea;
                }
            }

            return "⚠️ Acción o tipo no reconocido.";

        } catch (Exception e) {
            return "⚠️ Error al procesar JSON: " + e.getMessage() + "\nJSON recibido:\n" + contenido;
        }
    }

    private String normalizarFechas(String texto) {
        String ahora = LocalDateTime.now().withNano(0).toString();
        return texto.replaceAll("202[0-3]-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}", ahora);
    }
}