# AutoTask Manager – Backend

Backend del proyecto AutoTask Manager, desarrollado con Java + Spring Boot + MySQL.

⚠️ El frontend del proyecto se encuentra en otro repositorio: Tasbik frontend

## 🧠 Descripción del Proyecto

AutoTask Manager es un asistente inteligente que permite crear, actualizar y gestionar tareas y proyectos mediante comandos en lenguaje natural (voz o texto).

El backend:

- Procesa solicitudes enviadas desde el frontend.
- Integra Inteligencia Artificial para interpretar comandos.
- Gestiona la lógica de negocio.
- Almacena información en base de datos MySQL.
- Expone endpoints seguros mediante Spring Boot.

## 🛠️ Tecnologías

☕ Java 17+

🌱 Spring Boot

🗄️ MySQL

🤖 OpenAI API

## 🏗️ Arquitectura
- Frontend (React)    
- Spring Boot REST API  
- Servicio de IA (OpenAI)        ↓  
- Base de Datos MySQL

El flujo funciona así:

1. El frontend envía un comando en texto o voz.
2. El backend envía el texto a OpenAI.
3. La IA interpreta la intención (crear tarea, actualizar estado, etc.).
4. Spring Boot ejecuta la acción correspondiente.
5. Se guarda la información en MySQL.
6. Se retorna respuesta estructurada al frontend.

## ⚙️ Configuración del Proyecto
1️⃣ Clonar el repositorio  
`git clone https://github.com/tu-usuario/tu-repositorio-backend.git`  
`cd tu-repositorio-backend`

2️⃣ Configurar Base de Datos MySQL

Crear una base de datos:
```sql
CREATE DATABASE autotask_manager;
```
Configurar en application.properties:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/autotask_manager
spring.datasource.username=TU_USUARIO
spring.datasource.password=TU_PASSWORD

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect
```

3️⃣ 🔑 Configurar API Key de Inteligencia Artificial (OBLIGATORIO)

Para que el proyecto funcione correctamente, debes agregar tu API Key de OpenAI.

Agregar en application.properties:

```properties
openai.api.key=TU_API_KEY_AQUI
```

Recomendado usar variables de entorno:

```properties
openai.api.key=${OPENAI_API_KEY}
```

Y en tu sistema:
```bash
export OPENAI_API_KEY=tu_api_key
```

## 📌 Endpoints Principales 

- `POST /api/tareas`
- `GET /api/proyectos`
- `PUT /api/tareas/{id}`
- `DELETE /api/proyectos/{id}`

## 🔒 Seguridad

Arquitectura preparada para JWT.

- Validaciones en capa de servicio.
- Separación por capas:
  - Controller
  - Service
  - Repository
  - Entity

## 📂 Estructura del Proyecto
`src/main/java`
│
├── controller
├── service
├── repository
├── model
├── config
└── util

## 📊 Beneficios del Backend

✔ Automatización inteligente de tareas  
✔ Reducción de trabajo manual  
✔ Arquitectura escalable  
✔ Integración sencilla con frontend  
✔ Base sólida para microservicios

## 🔄 Integraciones Futuras

- Integración directa con Azure DevOps
- Mejoras en seguridad (encriptación avanzada)
- Microservicios independientes
- Logs inteligentes con IA

## 📌 Repositorio Frontend  
El frontend se encuentra en:  
👉 Tasbik frontend
