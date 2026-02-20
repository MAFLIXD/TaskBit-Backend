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
        // Detectar si es una transcripción de reunión (texto largo con características de reunión)
        if (esTranscripcionReunion(mensajeUsuario)) {
            return procesarTranscripcionReunion(mensajeUsuario);
        } else {
            return procesarComandoSimple(mensajeUsuario);
        }
    }

    private boolean esTranscripcionReunion(String texto) {
        // Heurísticas para detectar transcripciones de reuniones
        int longitud = texto.length();
        boolean tieneTimestamps = texto.matches("(?s).*\\d{1,2}:\\d{2}\\s*(?:AM|PM|am|pm)?.*");
        boolean tieneParticipantes = texto.matches("(?s).*(?:\\[.*\\]|\\w+\\s*:).*");
        boolean tieneMultiplesLineas = texto.split("\n").length > 10;
        boolean contienePalabrasReunion = texto.toLowerCase().contains("reunión") || 
                                         texto.toLowerCase().contains("meeting") ||
                                         texto.toLowerCase().contains("participante") ||
                                         texto.toLowerCase().contains("agenda");
        
        // Es una transcripción si es texto largo y tiene características de reunión
        return longitud > 300 && (tieneTimestamps || tieneParticipantes || tieneMultiplesLineas || contienePalabrasReunion);
    }

    private String procesarTranscripcionReunion(String transcripcion) {
        String fechaActual = LocalDateTime.now().withNano(0).toString();
        
        String promptReunion = """
        Eres un asistente especializado en análisis de reuniones de proyectos. Tu tarea es analizar la transcripción de una reunión 
        y extraer todas las acciones, tareas, proyectos, responsables y fechas mencionadas.
        
        ### INSTRUCCIONES ESPECÍFICAS:
        1. Analiza toda la conversación y extrae compromisos y acciones concretas
        2. Identifica quién es responsable de cada tarea mencionada
        3. Extrae fechas límite explícitas o deducibles del contexto
        4. Agrupa tareas relacionadas bajo proyectos comunes
        5. Si no se menciona un proyecto específico, crea uno con nombre general basado en el tema principal
        
        ### FORMATO DE RESPUESTA:
        Devuelve un array de objetos JSON, cada uno representando una acción identificada.
        Cada objeto debe seguir este formato:
        {
          "accion": "crear",
          "tipo": "proyecto|tarea",
          "nombre": "Nombre identificado para el proyecto/tarea",
          "proyecto": { ... }  // Solo si tipo es "proyecto"
          "tarea": { ... }     // Solo si tipo es "tarea"
        }
        
        ### ESTRUCTURAS:
        Para PROYECTO:
        {
          "nombre": "Nombre del proyecto (basado en el tema principal de la reunión)",
          "descripcion": "Resumen de los objetivos discutidos en la reunión",
          "fechaInicio": "%s",
          "fechaFin": "Fecha estimada si se menciona, o null",
          "duracionHoras": dejar siempre en 0,
          "fechaCreacion": "%s",
          "tareas": [] // Array de tareas asociadas
        }
        
        Para TAREA:
        {
          "titulo": "Descripción concisa de la acción",
          "descripcion": "Contexto extraído de la reunión",
          "estado": "pendiente",
          "fechaInicio": "%s",
          "fechaFin": "Fecha límite si se menciona, o null",
          "duracionHoras": número (horas estimadas),
          "observaciones": "Responsable: [nombre] - Extraído de: [contexto de la conversación]",
          "fechaCreacion": "%s",
          "proyecto": { "nombre": "Nombre del proyecto asociado" } // IMPORTANTE: Incluir referencia al proyecto
        }

        ### IMPORTANTE:
        - "duracionHoras" debe ser SIEMPRE un número entero (2, 4, 8, etc.)
        - NO uses decimales (13.98, 181.98, etc.)
        - Para cada tarea identificada, estima una duración REALISTA en horas basada en:
             - Tareas pequeñas: 2-4 horas
             - Tareas medias: 8-16 horas  
             - Tareas grandes: 24-40 horas
             - Usa tu criterio profesional para estimar
             - El proyecto duracionHoras debe ser siempre 0
        
        ### REGLAS CRÍTICAS:
        
        1. SIEMPRE incluye en cada tarea el campo "proyecto" con el nombre del proyecto al que pertenece
        2. Para tareas que pertenecen a un proyecto, crea SOLO el proyecto (con sus tareas dentro del array)
        3. NO crees tareas individuales separadas si ya están dentro del proyecto
        4. Solo crea tareas separadas si son tareas independientes sin proyecto
        5. El proyecto debe contener TODAS sus tareas en el array "tareas"
        6. En el campo "proyecto" de cada tarea dentro del array, puede ser null o solo contener {"nombre": "..."}
        
        TRANSCRIPCIÓN DE LA REUNIÓN:
        %s
        
        Devuelve SOLO el array JSON, sin texto adicional.
        """.formatted(fechaActual, fechaActual, fechaActual, fechaActual, transcripcion);

        try {
            String respuestaIA = llamarOpenAI(promptReunion);
            
            // Limpiar la respuesta si viene con markdown
            if (respuestaIA.startsWith("```")) {
                respuestaIA = respuestaIA.replaceAll("```json|```", "").trim();
            }
            
            // Parsear el array de acciones
            List<Map<String, Object>> acciones = objectMapper.readValue(respuestaIA, List.class);
            
            StringBuilder resultado = new StringBuilder();
            resultado.append("📋 **Análisis de reunión completado:**\n\n");
            
            int proyectosCreados = 0;
            int tareasCreadas = 0;
            Map<String, Proyecto> proyectosGuardados = new HashMap<>();
            
            // Primera pasada: Crear proyectos y sus tareas internas
            for (Map<String, Object> accion : acciones) {
                String accionTipo = (String) accion.get("accion");
                String tipo = (String) accion.get("tipo");
                
                if ("crear".equalsIgnoreCase(accionTipo) && "proyecto".equalsIgnoreCase(tipo)) {
                    Map<String, Object> proyectoMap = (Map<String, Object>) accion.get("proyecto");
                    String nombreProyecto = (String) proyectoMap.get("nombre");
                    
                    if (nombreProyecto == null || nombreProyecto.trim().isEmpty()) {
                        continue;
                    }
                    
                    // Validar nombre duplicado
                    boolean proyectoExiste = proyectoRepository.findAll().stream()
                            .anyMatch(p -> p.getNombre() != null && 
                                    p.getNombre().equalsIgnoreCase(nombreProyecto));
                    
                    if (!proyectoExiste) {
                        Proyecto proyecto = objectMapper.convertValue(proyectoMap, Proyecto.class);
                        
                        // Procesar tareas dentro del proyecto
                        List<Map<String, Object>> tareasMap = (List<Map<String, Object>>) proyectoMap.get("tareas");
                        if (tareasMap != null && !tareasMap.isEmpty()) {
                            List<Tarea> tareas = new ArrayList<>();
                            for (Map<String, Object> tareaMap : tareasMap) {
                                Tarea tarea = objectMapper.convertValue(tareaMap, Tarea.class);
                                tarea.setProyecto(proyecto); // Asociar la tarea al proyecto
                                tareas.add(tarea);
                            }
                            proyecto.setTareas(tareas);
                        }
                        
                        proyectoRepository.save(proyecto);
                        proyectosGuardados.put(nombreProyecto.toLowerCase(), proyecto);
                        proyectosCreados++;
                        resultado.append("✅ **Proyecto creado:** ").append(proyecto.getNombre());
                        if (proyecto.getTareas() != null) {
                            resultado.append(" con ").append(proyecto.getTareas().size()).append(" tareas");
                        }
                        resultado.append("\n");
                        if (proyecto.getDescripcion() != null) {
                            resultado.append("   📝 ").append(proyecto.getDescripcion()).append("\n");
                        }
                    }
                }
            }
            
            // Segunda pasada: Procesar tareas individuales (sin proyecto o con referencia a proyecto existente)
            for (Map<String, Object> accion : acciones) {
                String accionTipo = (String) accion.get("accion");
                String tipo = (String) accion.get("tipo");
                
                if ("crear".equalsIgnoreCase(accionTipo) && "tarea".equalsIgnoreCase(tipo)) {
                    Map<String, Object> tareaMap = (Map<String, Object>) accion.get("tarea");
                    String tituloTarea = (String) tareaMap.get("titulo");
                    
                    if (tituloTarea == null || tituloTarea.trim().isEmpty()) {
                        continue;
                    }
                    
                    Tarea tarea = objectMapper.convertValue(tareaMap, Tarea.class);
                    
                    // Buscar proyecto asociado si existe
                    Map<String, Object> proyectoRefMap = (Map<String, Object>) tareaMap.get("proyecto");
                    if (proyectoRefMap != null) {
                        String nombreProyectoRef = (String) proyectoRefMap.get("nombre");
                        if (nombreProyectoRef != null) {
                            // Buscar en proyectos ya creados
                            Proyecto proyectoAsociado = proyectosGuardados.get(nombreProyectoRef.toLowerCase());
                            if (proyectoAsociado == null) {
                                // Buscar en la base de datos existente
                                Optional<Proyecto> proyectoExistente = proyectoRepository.findAll().stream()
                                        .filter(p -> p.getNombre() != null && 
                                                p.getNombre().equalsIgnoreCase(nombreProyectoRef))
                                        .findFirst();
                                if (proyectoExistente.isPresent()) {
                                    proyectoAsociado = proyectoExistente.get();
                                }
                            }
                            if (proyectoAsociado != null) {
                                tarea.setProyecto(proyectoAsociado);
                            }
                        }
                    }
                    
                    // Verificar si ya existe
                    boolean tareaExiste = tareaService.obtenerTodas().stream()
                            .anyMatch(t -> t.getTitulo() != null && 
                                    t.getTitulo().equalsIgnoreCase(tituloTarea) &&
                                    (t.getProyecto() == null ? tarea.getProyecto() == null : 
                                     tarea.getProyecto() != null && 
                                     t.getProyecto().getId().equals(tarea.getProyecto().getId())));
                    
                    if (!tareaExiste) {
                        tareaService.guardar(tarea);
                        tareasCreadas++;
                        resultado.append("✅ **Tarea identificada:** ").append(tarea.getTitulo());
                        if (tarea.getProyecto() != null) {
                            resultado.append(" (Proyecto: ").append(tarea.getProyecto().getNombre()).append(")");
                        }
                        resultado.append("\n");
                        if (tarea.getObservaciones() != null) {
                            resultado.append("   👤 ").append(tarea.getObservaciones()).append("\n");
                        }
                    }
                }
            }
            
            resultado.append("\n📊 **Resumen:** ").append(proyectosCreados)
                     .append(" proyectos y ").append(tareasCreadas)
                     .append(" tareas procesadas de la reunión.\n\n");
            resultado.append("💡 **Consejo:** Revisa las tareas creadas y ajusta responsables o fechas si es necesario.");
            
            return resultado.toString();
            
        } catch (Exception e) {
            // Si falla el análisis de reunión, intentar procesar como comando simple
            return "⚠️ No pude analizar la reunión. Error: " + e.getMessage() + 
                   "\n\nIntentando procesar como comando simple...\n\n" + 
                   procesarComandoSimple(transcripcion);
        }
    }

    private String procesarComandoSimple(String mensajeUsuario) {
        String fechaActual = LocalDateTime.now().withNano(0).toString();

        String promptBase = """
        Eres un asistente inteligente que ayuda a gestionar proyectos y tareas de una bitácora.
        Tu trabajo es interpretar instrucciones en lenguaje natural y devolver un objeto JSON estructurado.
        
        ### Acciones posibles:
        - "crear": crear un proyecto o tarea.
        - "actualizar": actualizar un proyecto o tarea existente.
        - "eliminar": eliminar un proyecto o tarea existente.

        ### Reglas para actualizar:
        - Solo incluye en el JSON los campos que el usuario quiere cambiar
        -   Si el usuario solo quiere cambiar el estado, solo incluye "estado"
        - Si el usuario solo quiere cambiar la duración, solo incluye "duracionHoras"
        - NO incluyas campos con valores por defecto si el usuario no los menciona
        - Para mantener valores existentes, simplemente no los incluyas en el JSON
        
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
          "duracionHoras": debe ser siempre 0,
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
            "fechaCreacion": fecha actual en formato "YYYY-MM-DDTHH:mm:ss",
            "proyecto": { "nombre": "Nombre del proyecto asociado" } o null
        }
        
        ### Reglas:
        - Devuelve solo el JSON, sin explicaciones ni texto adicional.
        - Usa comillas dobles para todas las claves y valores de texto.
        - Usa siempre el formato "YYYY-MM-DDTHH:mm:ss" para las fechas.
        - Si no se menciona una fecha, usa la fecha actual: %s
        - No inventes años anteriores al actual.
        - Para tareas, usa "nombre" para identificar la tarea por su título
        - Las tareas pueden existir sin proyecto asociado (usa "proyecto": null)
        - En proyecto, "duracionHoras" debe ser siempre 0
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

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.openai.com/v1/chat/completions",
                    HttpMethod.POST,
                    crearRequestHttp(body),
                    Map.class
            );

            Map<String, Object> choices = (Map<String, Object>) ((List<?>) response.getBody().get("choices")).get(0);
            Map<String, Object> message = (Map<String, Object>) choices.get("message");
            String contenido = ((String) message.get("content")).trim();

            if (contenido.startsWith("```")) {
                contenido = contenido.replaceAll("```json|```", "").trim();
            }

            contenido = normalizarFechas(contenido);
            return ejecutarAccion(contenido);

        } catch (HttpClientErrorException.TooManyRequests e) {
            return "⚠️ Límite de uso excedido en OpenAI. Por favor espera unas horas o agrega un método de pago a tu cuenta.";
        } catch (HttpClientErrorException e) {
            return "⚠️ Error en la API de OpenAI: " + e.getStatusCode() + " - " + e.getStatusText();
        } catch (Exception e) {
            return "⚠️ Error de conexión con OpenAI: " + e.getMessage();
        }
    }

    private String llamarOpenAI(String prompt) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", "gpt-4o-mini");
        body.put("temperature", 0.1); // Un poco más alto para análisis de reuniones
        body.put("max_tokens", 2000); // Más tokens para transcripciones largas

        body.put("messages", List.of(
                Map.of("role", "system", "content", "Eres un analista de reuniones experto. Responde solo con JSON válido."),
                Map.of("role", "user", "content", prompt)
        ));

        HttpEntity<Map<String, Object>> request = crearRequestHttp(body);
        
        ResponseEntity<Map> response = restTemplate.exchange(
                "https://api.openai.com/v1/chat/completions",
                HttpMethod.POST,
                request,
                Map.class
        );

        Map<String, Object> choices = (Map<String, Object>) ((List<?>) response.getBody().get("choices")).get(0);
        Map<String, Object> message = (Map<String, Object>) choices.get("message");
        return ((String) message.get("content")).trim();
    }

    private HttpEntity<Map<String, Object>> crearRequestHttp(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return new HttpEntity<>(body, headers);
    }

    private String ejecutarAccion(String contenidoJson) {
    try {
        Map<String, Object> jsonMap = objectMapper.readValue(contenidoJson, Map.class);
        String accion = (String) jsonMap.get("accion");
        String tipo = (String) jsonMap.get("tipo");
        String nombre = (String) jsonMap.get("nombre");
        String titulo = (String) jsonMap.get("titulo");

        // CREAR
        if ("crear".equalsIgnoreCase(accion)) {
            if ("proyecto".equalsIgnoreCase(tipo)) {
                Map<String, Object> proyectoMap = (Map<String, Object>) jsonMap.get("proyecto");
                Proyecto proyecto = objectMapper.convertValue(proyectoMap, Proyecto.class);
                
                if (proyecto.getNombre() == null || proyecto.getNombre().trim().isEmpty()) {
                    return "⚠️ El nombre del proyecto es requerido";
                }
                
                boolean proyectoExiste = proyectoRepository.findAll().stream()
                        .anyMatch(p -> p.getNombre() != null && 
                                p.getNombre().equalsIgnoreCase(proyecto.getNombre()));
                if (proyectoExiste) {
                    return "⚠️ Ya existe un proyecto con el nombre: " + proyecto.getNombre();
                }
                
                if (proyecto.getTareas() != null) {
                    proyecto.getTareas().forEach(t -> t.setProyecto(proyecto));
                }
                
                proyectoRepository.save(proyecto);
                return "✅ Proyecto creado: " + proyecto.getNombre();
                
            } else if ("tarea".equalsIgnoreCase(tipo)) {
                Map<String, Object> tareaMap = (Map<String, Object>) jsonMap.get("tarea");
                Tarea tarea = objectMapper.convertValue(tareaMap, Tarea.class);
                
                if (tarea.getTitulo() == null || tarea.getTitulo().trim().isEmpty()) {
                    return "⚠️ El título de la tarea es requerido";
                }
                
                // Buscar proyecto asociado si existe
                if (tareaMap != null && tareaMap.containsKey("proyecto")) {
                    Object proyectoObj = tareaMap.get("proyecto");
                    if (proyectoObj == null) {
                        tarea.setProyecto(null);
                    } else if (proyectoObj instanceof Map) {
                        Map<String, Object> proyectoMap = (Map<String, Object>) proyectoObj;
                        if (proyectoMap.containsKey("nombre")) {
                            String nombreProyecto = (String) proyectoMap.get("nombre");
                            if (nombreProyecto != null && !nombreProyecto.trim().isEmpty()) {
                                Optional<Proyecto> proyectoOpt = proyectoRepository.findAll().stream()
                                        .filter(p -> p.getNombre() != null && 
                                                p.getNombre().equalsIgnoreCase(nombreProyecto))
                                        .findFirst();
                                proyectoOpt.ifPresent(tarea::setProyecto);
                            }
                        }
                    }
                }
                
                boolean tareaExiste = tareaService.obtenerTodas().stream()
                        .anyMatch(t -> t.getTitulo() != null && 
                                t.getTitulo().equalsIgnoreCase(tarea.getTitulo()) &&
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

        // ACTUALIZAR 
        if ("actualizar".equalsIgnoreCase(accion)) {
            if ("proyecto".equalsIgnoreCase(tipo)) {
                if (nombre == null || nombre.isEmpty()) {
                    return "⚠️ Nombre del proyecto es requerido para actualizar";
                }
                
                Optional<Proyecto> optProyecto = proyectoRepository.findAll().stream()
                        .filter(p -> p.getNombre() != null)
                        .filter(p -> p.getNombre().equalsIgnoreCase(nombre))
                        .findFirst();
                
                if (optProyecto.isEmpty()) {
                    return "⚠️ Proyecto no encontrado: " + nombre;
                }

                Proyecto proyecto = optProyecto.get();
                Map<String, Object> proyectoMap = (Map<String, Object>) jsonMap.get("proyecto");
                
                if (proyectoMap != null) {
                    boolean cambiosRealizados = false;
                    
                    // Actualizar solo los campos que vienen en el JSON
                    if (proyectoMap.containsKey("nombre")) {
                        String nuevoNombre = (String) proyectoMap.get("nombre");
                        if (nuevoNombre != null && !nuevoNombre.trim().isEmpty() && 
                            !nuevoNombre.equalsIgnoreCase(proyecto.getNombre())) {
                            
                            boolean nombreExiste = proyectoRepository.findAll().stream()
                                    .anyMatch(p -> p.getNombre() != null && 
                                            p.getNombre().equalsIgnoreCase(nuevoNombre) &&
                                            !p.getId().equals(proyecto.getId()));
                            
                            if (nombreExiste) {
                                return "⚠️ Ya existe otro proyecto con el nombre: " + nuevoNombre;
                            }
                            
                            proyecto.setNombre(nuevoNombre);
                            cambiosRealizados = true;
                        }
                    }
                    
                    if (proyectoMap.containsKey("descripcion")) {
                        proyecto.setDescripcion((String) proyectoMap.get("descripcion"));
                        cambiosRealizados = true;
                    }
                    
                    if (proyectoMap.containsKey("fechaInicio")) {
                        Object fechaInicioObj = proyectoMap.get("fechaInicio");
                        if (fechaInicioObj instanceof String) {
                            String fechaInicioStr = (String) fechaInicioObj;
                            if (fechaInicioStr != null && !fechaInicioStr.equalsIgnoreCase("null")) {
                                proyecto.setFechaInicio(LocalDateTime.parse(fechaInicioStr));
                                cambiosRealizados = true;
                            }
                        }
                    }
                    
                    if (proyectoMap.containsKey("fechaFin")) {
                        Object fechaFinObj = proyectoMap.get("fechaFin");
                        if (fechaFinObj == null) {
                            proyecto.setFechaFin(null);
                            cambiosRealizados = true;
                        } else if (fechaFinObj instanceof String) {
                            String fechaFinStr = (String) fechaFinObj;
                            if ("null".equalsIgnoreCase(fechaFinStr) || fechaFinStr.isEmpty()) {
                                proyecto.setFechaFin(null);
                            } else {
                                proyecto.setFechaFin(LocalDateTime.parse(fechaFinStr));
                            }
                            cambiosRealizados = true;
                        }
                    }
                    
                    if (cambiosRealizados) {
                        proyectoRepository.save(proyecto);
                        return "✅ Proyecto actualizado: " + proyecto.getNombre();
                    } else {
                        return "ℹ️ No se realizaron cambios en el proyecto: " + proyecto.getNombre();
                    }
                }
                
                return "⚠️ No se proporcionaron datos para actualizar el proyecto";
                
            } else if ("tarea".equalsIgnoreCase(tipo)) {
                String tituloTarea = (titulo != null && !titulo.isEmpty()) ? titulo : nombre;
                
                if (tituloTarea == null || tituloTarea.isEmpty()) {
                    return "⚠️ Nombre/título de tarea es requerido para actualizar";
                }
                
                Optional<Tarea> optTarea = tareaService.obtenerTodas().stream()
                        .filter(t -> t.getTitulo() != null)
                        .filter(t -> t.getTitulo().equalsIgnoreCase(tituloTarea))
                        .findFirst();
                
                if (optTarea.isEmpty()) {
                    return "⚠️ Tarea no encontrada: " + tituloTarea;
                }

                Tarea tarea = optTarea.get();
                Map<String, Object> tareaMap = (Map<String, Object>) jsonMap.get("tarea");
                
                if (tareaMap != null) {
                    boolean cambiosRealizados = false;
                    
                    // Actualizar título si se proporciona
                    if (tareaMap.containsKey("titulo")) {
                        String nuevoTitulo = (String) tareaMap.get("titulo");
                        if (nuevoTitulo != null && !nuevoTitulo.trim().isEmpty() && 
                            !nuevoTitulo.equalsIgnoreCase(tarea.getTitulo())) {
                            
                            boolean tituloExiste = tareaService.obtenerTodas().stream()
                                    .anyMatch(t -> t.getTitulo() != null && 
                                            t.getTitulo().equalsIgnoreCase(nuevoTitulo) &&
                                            !t.getId().equals(tarea.getId()) &&
                                            (t.getProyecto() == null ? tarea.getProyecto() == null : 
                                             tarea.getProyecto() != null && 
                                             t.getProyecto().getId().equals(tarea.getProyecto().getId())));
                            
                            if (tituloExiste) {
                                String mensajeError = "⚠️ Ya existe otra tarea con el título: " + nuevoTitulo;
                                if (tarea.getProyecto() != null) {
                                    mensajeError += " en el proyecto: " + tarea.getProyecto().getNombre();
                                }
                                return mensajeError;
                            }
                            
                            tarea.setTitulo(nuevoTitulo);
                            cambiosRealizados = true;
                        }
                    }
                    
                    // Actualizar proyecto asociado si se especifica
                    if (tareaMap.containsKey("proyecto")) {
                        Object proyectoObj = tareaMap.get("proyecto");
                        if (proyectoObj == null) {
                            tarea.setProyecto(null);
                            cambiosRealizados = true;
                        } else if (proyectoObj instanceof Map) {
                            Map<String, Object> proyectoMap = (Map<String, Object>) proyectoObj;
                            if (proyectoMap.containsKey("nombre")) {
                                String nombreProyecto = (String) proyectoMap.get("nombre");
                                if (nombreProyecto != null && !nombreProyecto.trim().isEmpty()) {
                                    Optional<Proyecto> proyectoOpt = proyectoRepository.findAll().stream()
                                            .filter(p -> p.getNombre() != null && 
                                                    p.getNombre().equalsIgnoreCase(nombreProyecto))
                                            .findFirst();
                                    
                                    if (proyectoOpt.isPresent()) {
                                        Proyecto nuevoProyecto = proyectoOpt.get();
                                        if (tarea.getProyecto() == null || 
                                            !tarea.getProyecto().getId().equals(nuevoProyecto.getId())) {
                                            tarea.setProyecto(nuevoProyecto);
                                            cambiosRealizados = true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Actualizar otros campos solo si están presentes en el JSON
                    if (tareaMap.containsKey("descripcion")) {
                        tarea.setDescripcion((String) tareaMap.get("descripcion"));
                        cambiosRealizados = true;
                    }
                    
                    if (tareaMap.containsKey("estado")) {
                        String nuevoEstado = (String) tareaMap.get("estado");
                        if (nuevoEstado != null && !nuevoEstado.equals(tarea.getEstado())) {
                            tarea.setEstado(nuevoEstado);
                            cambiosRealizados = true;
                        }
                    }
                    
                    if (tareaMap.containsKey("fechaInicio")) {
                        Object fechaInicioObj = tareaMap.get("fechaInicio");
                        if (fechaInicioObj instanceof String) {
                            String fechaInicioStr = (String) fechaInicioObj;
                            if (fechaInicioStr != null && !fechaInicioStr.equalsIgnoreCase("null")) {
                                tarea.setFechaInicio(LocalDateTime.parse(fechaInicioStr));
                                cambiosRealizados = true;
                            }
                        }
                    }
                    
                    if (tareaMap.containsKey("fechaFin")) {
                        Object fechaFinObj = tareaMap.get("fechaFin");
                        if (fechaFinObj == null) {
                            tarea.setFechaFin(null);
                            cambiosRealizados = true;
                        } else if (fechaFinObj instanceof String) {
                            String fechaFinStr = (String) fechaFinObj;
                            if ("null".equalsIgnoreCase(fechaFinStr) || fechaFinStr.isEmpty()) {
                                tarea.setFechaFin(null);
                            } else {
                                tarea.setFechaFin(LocalDateTime.parse(fechaFinStr));
                            }
                            cambiosRealizados = true;
                        }
                    }
                    
                    if (tareaMap.containsKey("duracionHoras")) {
                        Object duracionObj = tareaMap.get("duracionHoras");
                        if (duracionObj != null) {
                            Double nuevaDuracion = 0.0;
                            if (duracionObj instanceof Integer) {
                                nuevaDuracion = ((Integer) duracionObj).doubleValue();
                            } else if (duracionObj instanceof Double) {
                                nuevaDuracion = (Double) duracionObj;
                            } else if (duracionObj instanceof String) {
                                try {
                                    nuevaDuracion = Double.parseDouble((String) duracionObj);
                                } catch (NumberFormatException e) {
                                    // Mantener el valor actual si no es un número válido
                                    nuevaDuracion = tarea.getDuracionHoras() != null ? tarea.getDuracionHoras() : 0.0;
                                }
                            }
                            
                            Double duracionActual = tarea.getDuracionHoras() != null ? tarea.getDuracionHoras() : 0.0;
                            if (!nuevaDuracion.equals(duracionActual)) {
                                tarea.setDuracionHoras(nuevaDuracion);
                                cambiosRealizados = true;
                            }
                        }
                    }
                    
                    if (tareaMap.containsKey("observaciones")) {
                        tarea.setObservaciones((String) tareaMap.get("observaciones"));
                        cambiosRealizados = true;
                    }
                    
                    if (cambiosRealizados) {
                        tareaService.guardar(tarea);
                        return "✅ Tarea actualizada: " + tarea.getTitulo();
                    } else {
                        return "ℹ️ No se realizaron cambios en la tarea: " + tarea.getTitulo();
                    }
                }
                
                return "⚠️ No se proporcionaron datos para actualizar la tarea";
            }
        }

        // ELIMINAR
        if ("eliminar".equalsIgnoreCase(accion)) {
            if ("proyecto".equalsIgnoreCase(tipo)) {
                if (nombre == null || nombre.isEmpty()) {
                    return "⚠️ Nombre del proyecto es requerido para eliminar";
                }
                
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
                String tituloTarea = (titulo != null && !titulo.isEmpty()) ? titulo : nombre;
                
                if (tituloTarea == null || tituloTarea.isEmpty()) {
                    return "⚠️ Nombre/título de tarea es requerido para eliminar";
                }
                
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
        return "⚠️ Error al procesar JSON: " + e.getMessage() + "\nJSON recibido:\n" + contenidoJson;
    }
}

    private String normalizarFechas(String texto) {
        String ahora = LocalDateTime.now().withNano(0).toString();
        return texto.replaceAll("202[0-3]-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}", ahora);
    }
}