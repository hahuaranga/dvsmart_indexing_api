# dvsmart_indexing_api

## 📋 Tabla de Contenidos

- [Descripción General](#-descripción-general)
- [Stack Tecnológico](#-stack-tecnológico)
- [Arquitectura del Sistema](#-arquitectura-del-sistema)
- [Requisitos Previos](#-requisitos-previos)
- [Instalación y Setup](#-instalación-y-setup)
- [Guía Completa de Configuración](#-guía-completa-de-configuración)
- [Configuración de Alto Rendimiento](#-configuración-de-alto-rendimiento)
- [Uso y API](#-uso-y-api)
- [Arquitectura del Batch Job](#-arquitectura-del-batch-job)
- [Monitorización y Observabilidad](#-monitorización-y-observabilidad)
- [Troubleshooting](#-troubleshooting)
- [Mantenimiento y Testing](#-mantenimiento-y-testing)
- [Consideraciones de Seguridad](#-consideraciones-de-seguridad)

---

## 🎯 Descripción General

**dvsmart_indexing_api** es un microservicio empresarial de alto rendimiento diseñado para indexar masivamente archivos almacenados en servidores SFTP. El sistema procesa millones de archivos de forma distribuida, extrayendo metadata y persistiéndola en MongoDB para su posterior consulta y organización.

### Casos de Uso Principales

- **Indexación masiva**: Procesamiento de 11M+ archivos en ~30-60 minutos
- **Descubrimiento de estructura**: Mapeo recursivo de jerarquías de directorios complejas
- **Coordinación distribuida**: Pool de conexiones SFTP con gestión automática de recursos
- **Alta disponibilidad**: Health checks y auto-recuperación de conexiones

### Características Clave

✅ **Pool de Conexiones SFTP Lazy**: Conexiones creadas bajo demanda, liberadas automáticamente  
✅ **Procesamiento Asíncrono**: Pipeline de 3 etapas (Reader → Processor → Writer) con paralelismo configurable  
✅ **Bulk Upserts a MongoDB**: 3000-5000 documentos/segundo vs 100-200 con operaciones individuales  
✅ **Persistencia Dual**: PostgreSQL para metadatos de Spring Batch, MongoDB para archivos indexados  
✅ **Monitorización Integrada**: Actuator + endpoints custom para observabilidad del pool SFTP  
✅ **Resiliente**: Validación de conexiones, eviction de idle sessions, retry logic  

---

## 🛠 Stack Tecnológico

| Tecnología | Versión | Propósito |
|------------|---------|-----------|
| **Java** | 21 | Lenguaje base con soporte LTS |
| **Spring Boot** | 4.0.1 | Framework de aplicación |
| **Spring Batch** | 6.0.0 | Procesamiento por lotes de alto rendimiento |
| **Spring Integration** | 7.0.0 | Integración con sistemas externos (SFTP) |
| **MongoDB** | 5.0+ | Persistencia de metadata de archivos |
| **PostgreSQL** | - | Metadata de Spring Batch (job repository) |
| **Apache Commons Pool2** | - | Gestión del pool de conexiones SFTP |
| **SSHJ** | 0.38.0 | Cliente SFTP nativo |
| **Lombok** | 1.18.30 | Reducción de boilerplate |
| **Maven** | 3.8+ | Gestión de dependencias y build |

---

## 🏗 Arquitectura del Sistema

### Diagrama de Componentes

```mermaid
graph TB
    subgraph "Cliente"
        API[REST Controller]
    end

    subgraph "Capa de Aplicación"
        UC[StartIndexFullUseCase]
        SVC[StartIndexFullService]
    end

    subgraph "Spring Batch Job"
        JOB[BatchIndexFullJob]
        STEP[IndexingStep]
        
        subgraph "Pipeline Asíncrono"
            READER[DirectoryQueueItemReader]
            PROCESSOR[MetadataExtractorProcessor]
            WRITER[BulkUpsertMongoItemWriter]
        end
    end

    subgraph "Infraestructura"
        POOL[CustomLazySftpSessionFactory]
        MONITOR[SftpPoolMonitor]
    end

    subgraph "Sistemas Externos"
        SFTP[Servidor SFTP Origen]
        MONGO[(MongoDB)]
        POSTGRES[(PostgreSQL)]
    end

    API -->|POST /api/batch/index/full| UC
    UC --> SVC
    SVC -->|Lanza job| JOB
    JOB --> STEP
    STEP --> READER
    READER -->|Lee archivos| PROCESSOR
    PROCESSOR -->|Extrae metadata| WRITER
    WRITER -->|Bulk upsert| MONGO

    READER -.->|Adquiere sesión| POOL
    POOL -.->|Conexión SSH| SFTP
    MONITOR -.->|Monitorea| POOL
    JOB -.->|Persiste estado| POSTGRES
```

### Flujo de Procesamiento

```mermaid
sequenceDiagram
    participant Client
    participant Controller
    participant BatchJob
    participant Reader
    participant SftpPool
    participant Processor
    participant Writer
    participant MongoDB
    participant PostgreSQL

    Client->>Controller: POST /api/batch/index/full
    Controller->>BatchJob: Lanzar job
    BatchJob->>PostgreSQL: Registrar ejecución
    
    Note over BatchJob: Phase 1: Discovery
    BatchJob->>Reader: Descubrir directorios
    Reader->>SftpPool: Obtener sesión (1 vez)
    SftpPool-->>Reader: Queue de directorios
    
    Note over BatchJob: Phase 2: Indexing
    loop Por cada chunk de 100 archivos
        Reader->>SftpPool: Obtener sesión
        SftpPool-->>Reader: Archivos del directorio
        
        par Procesamiento paralelo (20 threads)
            Reader->>Processor: SftpFileEntry[]
            Processor->>Processor: Extraer metadata
        end
        
        par Escritura paralela
            Processor->>Writer: ArchivoMetadata[]
            Writer->>MongoDB: Bulk upsert (100 docs)
        end
        
        Writer->>PostgreSQL: Commit chunk
    end
    
    BatchJob->>PostgreSQL: Actualizar estado COMPLETED
    BatchJob-->>Controller: JobExecutionId
    Controller-->>Client: 202 Accepted
```

### Arquitectura Hexagonal

```mermaid
graph LR
    subgraph "Dominio"
        M[ArchivoMetadata]
        S[FileMetadataService]
        D[DirectoryDiscoveryService]
    end

    subgraph "Puertos Entrada"
        PI[StartIndexFullUseCase]
    end

    subgraph "Adaptadores Entrada"
        AI1[BatchIndexingController]
        AI2[MonitoringController]
    end

    subgraph "Adaptadores Salida"
        AO1[DirectoryQueueItemReader]
        AO2[MetadataExtractorProcessor]
        AO3[BulkUpsertMongoItemWriter]
        AO4[CustomLazySftpSessionFactory]
    end

    AI1 --> PI
    AI2 --> PI
    PI --> S
    S --> M
    AO1 --> D
    AO1 --> AO4
    AO2 --> S
    AO3 --> MongoDB[(MongoDB)]
    AO4 --> SFTP[SFTP Server]
```

### Técnicas de Procesamiento

#### 1. **Lazy Directory Discovery**
```mermaid
flowchart TD
    A[Primera llamada a read] --> B{Discovery completado?}
    B -->|No| C[Ejecutar BFS en SFTP]
    C --> D[Crear queue de directorios]
    D --> E[Marcar discovery completado]
    E --> F[Retornar primer archivo]
    B -->|Sí| G[Retornar archivo de queue actual]
    G --> H{Queue actual vacía?}
    H -->|Sí| I[Cargar siguiente directorio]
    I --> G
    H -->|No| F
```

**Ventajas:**
- Discovery se ejecuta solo cuando el job arranca (no al iniciar la app)
- Memoria eficiente: O(D) donde D = archivos en directorio actual
- Una sola sesión SFTP para todo el escaneo

#### 2. **Async Processing Pipeline**
```mermaid
flowchart LR
    R[Reader<br/>1 thread] --> Q1[Queue]
    Q1 --> P1[Processor<br/>Thread 1]
    Q1 --> P2[Processor<br/>Thread 2]
    Q1 --> P3[Processor<br/>...]
    Q1 --> PN[Processor<br/>Thread N]
    
    P1 --> Q2[Queue]
    P2 --> Q2
    P3 --> Q2
    PN --> Q2
    
    Q2 --> W[Writer<br/>Bulk Operations]
```

**Ventajas:**
- Paralelismo configurable (20 threads por defecto)
- No bloqueante: Reader continúa mientras se procesa
- Processor sin I/O: Solo transformación en memoria

#### 3. **Bulk Upsert Strategy**
```mermaid
sequenceDiagram
    participant Writer
    participant BulkOps
    participant MongoDB
    
    Writer->>BulkOps: Crear operación UNORDERED
    
    loop Por cada ArchivoMetadata
        Writer->>BulkOps: upsert(query, update)
    end
    
    Writer->>BulkOps: execute()
    BulkOps->>MongoDB: Bulk write (1 round-trip)
    MongoDB-->>BulkOps: BulkWriteResult
    BulkOps-->>Writer: inserted + modified counts
```

**Performance:**
- Sin bulk: 100-200 docs/s (1 round-trip por doc)
- Con bulk: 3000-5000 docs/s (1 round-trip por chunk)
- Para 11M archivos: ~30-60 minutos vs ~15-30 horas

#### 4. **Connection Pool Lifecycle**
```mermaid
stateDiagram-v2
    [*] --> Idle: Pool inicializado (lazy)
    
    Idle --> Creating: getSession() llamado
    Creating --> Active: Sesión creada
    Active --> Validating: testOnBorrow=true
    Validating --> InUse: Validación OK
    Validating --> Destroyed: Validación falla
    
    InUse --> Returning: close() llamado
    Returning --> Idle: Devuelta al pool
    
    Idle --> Evicting: Eviction timer
    Evicting --> Destroyed: Idle > minEvictableIdleTime
    
    Destroyed --> [*]
```

**Ventajas:**
- Lazy init: No conexiones al inicio
- testOnBorrow: Detecta conexiones zombie
- Eviction: Libera recursos automáticamente

---

## 📦 Requisitos Previos

### Software Requerido

- **JDK 21** (OpenJDK o Oracle)
- **Maven 3.8+**
- **MongoDB 5.0+**
- **PostgreSQL 12+**
- **Servidor SFTP** con acceso configurado

### Recursos Mínimos

**Desarrollo:**
- RAM: 4 GB
- CPU: 2 cores
- Disco: 2 GB

**Producción (11M archivos):**
- RAM: 8-16 GB (según `batch.thread-pool-size`)
- CPU: 4-8 cores
- Disco: 10 GB (logs + metadata temporal de Batch)

---

## 🚀 Instalación y Setup

### Compilación del Proyecto

```bash
# Clonar repositorio
git clone <repository-url>
cd dvsmart_indexing_api

# Verificar Maven
mvn -version

# Limpiar y compilar (skip tests)
mvn clean package -DskipTests

# Compilar y ejecutar tests
mvn clean install
```

**Artefacto generado:** `target/dvsmart_indexing_api.jar`

### Aplicar Licencias (CopyRight Headers)

```bash
# Aplicar headers a todos los archivos .java
mvn license:format

# Verificar headers
mvn license:check
```

### Ejecución Local

```bash
# Modo development (perfil dev por defecto)
mvn spring-boot:run

# O usando el JAR compilado
java -jar target/dvsmart_indexing_api.jar

# Con perfil prod
java -jar target/dvsmart_indexing_api.jar --spring.profiles.active=prod

# Sobreescribir propiedades
java -jar target/dvsmart_indexing_api.jar \
  --server.port=9090 \
  --spring.mongodb.uri=mongodb://localhost:27017/test
```

La aplicación estará disponible en:
```
http://localhost:8080/dvsmart_indexing_api
```

### Configuración de MongoDB

**Crear usuario y base de datos:**

```javascript
// Conectar a MongoDB como admin
mongo

// Crear usuario
use dvsmart-ms
db.createUser({
  user: "dvsmart_user",
  pwd: "eoQQqfTyMd",
  roles: [
    { role: "readWrite", db: "dvsmart-ms" }
  ]
})

// Verificar conexión
db.auth("dvsmart_user", "eoQQqfTyMd")
```

### Configuración de PostgreSQL

**Crear usuario y base de datos:**

```sql
-- Conectar como superusuario
psql -U postgres

-- Crear base de datos
CREATE DATABASE dvsmart;

-- Crear usuario
CREATE USER dvsmart_ms WITH PASSWORD 'OgxjdNEeQl';

-- Otorgar permisos
GRANT ALL PRIVILEGES ON DATABASE dvsmart TO dvsmart_ms;

-- Conectar a la base de datos
\c dvsmart

-- Otorgar permisos en el schema
GRANT ALL ON SCHEMA public TO dvsmart_ms;
```

### Configuración del Servidor SFTP

**Verificar conectividad:**

```bash
# Conexión manual
sftp -P 22 sftpsourceuser@sftp-host

# Test de latencia
ping sftp-host

# Test de puerto
telnet sftp-host 22
```

---

## ⚙️ Guía Completa de Configuración

### Archivo: `application.properties`

#### Configuración Base

```properties
# Aplicación
spring.application.name=dvsmart-indexing-api
server.servlet.context-path=/dvsmart_indexing_api
server.port=8080
server.shutdown=graceful
```

| Propiedad | Valor | Descripción |
|-----------|-------|-------------|
| `spring.application.name` | `dvsmart-indexing-api` | Nombre de la aplicación |
| `server.servlet.context-path` | `/dvsmart_indexing_api` | Context path base |
| `server.port` | `8080` | Puerto HTTP |
| `server.shutdown` | `graceful` | Shutdown suave (espera jobs activos) |

#### MongoDB

```properties
spring.mongodb.uri=mongodb://dvsmart_user:eoQQqfTyMd@localhost:30000/dvsmart-ms?authSource=dvsmart-ms
```

**Componentes de la URI:**
- **Usuario:** `dvsmart_user`
- **Password:** `eoQQqfTyMd` ⚠️ **Cambiar en producción**
- **Host:** `localhost:30000` (ejemplo NodePort Kubernetes)
- **Base de datos:** `dvsmart-ms`
- **Auth source:** `dvsmart-ms`

**Colecciones utilizadas:**

| Colección | Propósito | Índices |
|-----------|-----------|---------|
| `files_index` | Metadata de archivos | `idUnico` (unique) |

#### PostgreSQL (Spring Batch Repository)

```properties
spring.datasource.url=jdbc:postgresql://localhost:30005/dvsmart
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.username=dvsmart_ms
spring.datasource.password=OgxjdNEeQl
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5
```

| Propiedad | Valor | Descripción |
|-----------|-------|-------------|
| `spring.datasource.url` | `jdbc:postgresql://localhost:30005/dvsmart` | URL de conexión |
| `spring.datasource.username` | `dvsmart_ms` | Usuario PostgreSQL |
| `spring.datasource.password` | `OgxjdNEeQl` | Password ⚠️ **Usar secrets** |
| `hikari.maximum-pool-size` | `10` | Pool máximo de conexiones |
| `hikari.minimum-idle` | `5` | Conexiones idle mínimas |

**Tablas utilizadas por Spring Batch:**

| Tabla | Propósito |
|-------|-----------|
| `BATCH_JOB_INSTANCE` | Instancias de jobs |
| `BATCH_JOB_EXECUTION` | Ejecuciones de jobs |
| `BATCH_STEP_EXECUTION` | Ejecuciones de steps |
| `BATCH_JOB_EXECUTION_PARAMS` | Parámetros de ejecución |

#### Spring Batch

```properties
spring.batch.job.enabled=false
spring.batch.jdbc.initialize-schema=always
```

| Propiedad | Valor | Descripción |
|-----------|-------|-------------|
| `spring.batch.job.enabled` | `false` | ⚠️ **CRÍTICO:** Desactiva inicio automático |
| `spring.batch.jdbc.initialize-schema` | `always` | Crea tablas al inicio |

⚠️ **IMPORTANTE:** `job.enabled=false` evita que los jobs se ejecuten automáticamente al arrancar. Los jobs se lanzan manualmente vía API REST.

#### Configuración del Batch (Prefijo: `batch.*`)

```properties
batch.chunk-size=100
batch.thread-pool-size=20
batch.queue-capacity=1000
batch.skip-limit=5
batch.retry-limit=3
```

| Propiedad | Valor | Rango | Descripción |
|-----------|-------|-------|-------------|
| `batch.chunk-size` | `100` | `50-1000` | Archivos procesados por chunk antes de commit |
| `batch.thread-pool-size` | `20` | `10-50` | Threads del `AsyncItemProcessor/Writer` |
| `batch.queue-capacity` | `1000` | `500-5000` | Capacidad de cola de tareas pendientes |
| `batch.skip-limit` | `5` | `0-100` | Máximo de errores saltables por chunk |
| `batch.retry-limit` | `3` | `0-10` | Reintentos antes de fallar |

**Configuraciones por entorno:**

```properties
# 🔹 DESARROLLO
batch.chunk-size=50
batch.thread-pool-size=5
batch.queue-capacity=500

# 🔹 PRODUCCIÓN ESTÁNDAR
batch.chunk-size=100
batch.thread-pool-size=20
batch.queue-capacity=1000

# 🔹 ALTO RENDIMIENTO (11M archivos)
batch.chunk-size=500
batch.thread-pool-size=50
batch.queue-capacity=5000
```

#### Servidor SFTP Origen (Prefijo: `sftp.origin.*`)

```properties
sftp.origin.host=localhost
sftp.origin.port=30002
sftp.origin.user=sftpsourceuser
sftp.origin.password=securepass
sftp.origin.base-dir=/disorganized_data
sftp.origin.timeout=30000
```

| Propiedad | Valor | Descripción |
|-----------|-------|-------------|
| `sftp.origin.host` | `localhost` | Hostname o IP del servidor |
| `sftp.origin.port` | `30002` | Puerto SSH (NodePort ejemplo) |
| `sftp.origin.user` | `sftpsourceuser` | Usuario SFTP |
| `sftp.origin.password` | `securepass` | Password ⚠️ **Usar Secrets en prod** |
| `sftp.origin.base-dir` | `/disorganized_data` | Directorio raíz a indexar |
| `sftp.origin.timeout` | `30000` | Timeout conexión (ms) |

#### Pool de Conexiones SFTP (Prefijo: `sftp.origin.pool.*`)

```properties
# Pool lazy
sftp.origin.pool.lazy-init=true
sftp.origin.pool.initial-size=0
sftp.origin.pool.max-size=10
sftp.origin.pool.size=10

# Timeouts y validación
sftp.origin.pool.max-wait-millis=30000
sftp.origin.pool.test-on-borrow=true
sftp.origin.pool.test-while-idle=true

# Eviction (limpieza de idle)
sftp.origin.pool.time-between-eviction-runs-millis=60000
sftp.origin.pool.min-evictable-idle-time-millis=300000
```

| Propiedad | Valor | Descripción |
|-----------|-------|-------------|
| `lazy-init` | `true` | No crear conexiones al inicio |
| `initial-size` | `0` | Pool completamente lazy (0 = bajo demanda) |
| `max-size` | `10` | Tamaño máximo del pool |
| `size` | `10` | Alias de max-size |
| `max-wait-millis` | `30000` | Tiempo máximo espera por conexión |
| `test-on-borrow` | `true` | **CRÍTICO:** Validar antes de usar |
| `test-while-idle` | `true` | Validar conexiones idle |
| `time-between-eviction-runs-millis` | `60000` | Frecuencia de limpieza (60s) |
| `min-evictable-idle-time-millis` | `300000` | Tiempo idle antes de cerrar (5 min) |

⚠️ **IMPORTANTE:** `min-evictable-idle-time` debe ser **menor** que el timeout del servidor SFTP para evitar conexiones "zombie".

**Escenarios de configuración:**

```properties
# 🔹 SERVIDOR SFTP CON POCOS RECURSOS
sftp.origin.pool.max-size=3
sftp.origin.pool.min-evictable-idle-time-millis=120000  # 2 min

# 🔹 SERVIDOR SFTP DEDICADO
sftp.origin.pool.max-size=10
sftp.origin.pool.min-evictable-idle-time-millis=300000  # 5 min

# 🔹 ALTO TRÁFICO (múltiples APIs)
sftp.origin.pool.max-size=5
sftp.origin.pool.max-wait-millis=60000
sftp.origin.pool.min-evictable-idle-time-millis=180000  # 3 min
```

#### Logging

```properties
# Niveles generales
logging.level.root=INFO
logging.level.com.indra.minsait.dvsmart.indexing=DEBUG

# Componentes específicos
logging.level.org.springframework.batch=INFO
logging.level.org.springframework.integration.sftp=DEBUG
logging.level.org.springframework.data.mongodb=INFO

# Patrón
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} - %logger{36} - %msg%n
```

**Por entorno:**

```properties
# 🔹 DESARROLLO
logging.level.com.indra.minsait.dvsmart.indexing=TRACE
logging.level.org.springframework.batch=DEBUG

# 🔹 PRODUCCIÓN
logging.level.com.indra.minsait.dvsmart.indexing=INFO
logging.level.org.springframework.batch=WARN
```

#### Actuator (Monitorización)

```properties
management.endpoints.web.exposure.include=health,info,metrics,batch
management.endpoint.health.show-details=always

# Métricas
management.metrics.enable.jvm=true
management.metrics.enable.process=true
management.metrics.enable.system=true
```

---

## 🔥 Configuración de Alto Rendimiento

### Tuning de la JVM

```bash
# Variables de entorno
export JAVA_OPTS="-Xms4g -Xmx8g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:ParallelGCThreads=4"

java $JAVA_OPTS -jar target/dvsmart_indexing_api.jar
```

| Flag | Valor | Propósito |
|------|-------|-----------|
| `-Xms4g` | Heap inicial 4GB | Evita resizing |
| `-Xmx8g` | Heap máximo 8GB | Suficiente para 11M archivos |
| `-XX:+UseG1GC` | G1 GC | Baja latencia |
| `-XX:MaxGCPauseMillis=200` | Pausas < 200ms | Reduce impacto del GC |
| `-XX:ParallelGCThreads=4` | 4 threads GC | Ajustar según CPU |

### Tuning de Spring Batch (11M archivos)

```properties
# Chunks grandes = menos commits
batch.chunk-size=500

# Máximo paralelismo
batch.thread-pool-size=50
batch.queue-capacity=5000

# Pool SFTP suficiente
sftp.origin.pool.max-size=10
```

**Cálculo de throughput:**

```
Archivos: 11,000,000
Chunk size: 500
Threads: 50
Tiempo por chunk: 2s

Chunks totales: 11,000,000 / 500 = 22,000
Tiempo: (22,000 / 50) * 2s = 880s ≈ 15 minutos
```

### Tuning de MongoDB

**Índices óptimos:**

```javascript
// Índice único (crítico para upserts)
db.getCollection("files_index").createIndex(
  { "idUnico": 1 },
  { unique: true, background: false }
)

// Índice para consultas por ruta
db.getCollection("files_index").createIndex(
  { "sourcePath": 1, "indexing_indexedAt": -1 },
  { background: true }
)

// Índice para búsquedas por extensión
db.getCollection("files_index").createIndex(
  { "extension": 1, "fileSize": -1 },
  { background: true }
)

// Índice para estado de indexación
db.getCollection("files_index").createIndex(
  { "indexing_status": 1 },
  { background: true }
)
```

**Write concern para alto throughput:**

```properties
# Agregar a URI
spring.mongodb.uri=mongodb://user:pass@host:27017/db?w=1&journal=false&maxPoolSize=100
```

- `w=1`: Esperar confirmación del primario
- `journal=false`: No esperar flush a disco (⚠️ riesgo de pérdida en crash)
- `maxPoolSize=100`: Pool grande para escrituras masivas

### Tuning de PostgreSQL

**Configuración para Spring Batch:**

```sql
-- Aumentar shared_buffers para cache
ALTER SYSTEM SET shared_buffers = '2GB';

-- Aumentar work_mem para ordenamientos
ALTER SYSTEM SET work_mem = '64MB';

-- Checkpoint menos frecuentes
ALTER SYSTEM SET checkpoint_timeout = '30min';
ALTER SYSTEM SET checkpoint_completion_target = 0.9;

-- Aplicar cambios
SELECT pg_reload_conf();
```

**Índices en tablas de Batch:**

```sql
-- Índice para búsquedas por nombre de job
CREATE INDEX idx_job_inst_job_name ON BATCH_JOB_INSTANCE(JOB_NAME);

-- Índice para búsquedas por estado
CREATE INDEX idx_job_exec_status ON BATCH_JOB_EXECUTION(STATUS);

-- Índice para job execution params
CREATE INDEX idx_job_exec_params_job_exec_id ON BATCH_JOB_EXECUTION_PARAMS(JOB_EXECUTION_ID);
```

---

## 📡 Uso y API

### Endpoints de Indexación

#### 🔵 Iniciar Indexación Completa

```http
POST /dvsmart_indexing_api/api/batch/index/full
Content-Type: application/json

{
  "jobName": "BATCH-INDEX-FULL",
  "parameters": {}
}
```

**Request Body:**

```json
{
  "jobName": "BATCH-INDEX-FULL",
  "parameters": {
    "customParam": "value"
  }
}
```

**Response (202 Accepted):**

```json
{
  "message": "Batch job started successfully",
  "jobExecutionId": 12345,
  "status": "ACCEPTED"
}
```

**Ejemplos:**

```bash
# Local
curl -X POST http://localhost:8080/dvsmart_indexing_api/api/batch/index/full \
  -H "Content-Type: application/json" \
  -d '{"jobName":"BATCH-INDEX-FULL","parameters":{}}'

# Remoto
curl -X POST https://api.example.com/dvsmart_indexing_api/api/batch/index/full \
  -H "Content-Type: application/json" \
  -d '{"jobName":"BATCH-INDEX-FULL","parameters":{}}'
```

**Códigos de estado:**

| Código | Significado | Acción |
|--------|-------------|--------|
| `202` | Job lanzado | Monitorear logs |
| `400` | Request inválido | Verificar JSON |
| `500` | Error interno | Revisar logs |

### Endpoints de Monitorización

#### 🟢 Estadísticas Básicas del Pool SFTP

```http
GET /dvsmart_indexing_api/api/monitoring/sftp-pool
```

**Response:**

```json
{
  "active": 2,
  "idle": 3,
  "maxTotal": 10,
  "totalCreated": 5,
  "totalDestroyed": 0,
  "utilizationPercent": 20.0,
  "availableSlots": 8
}
```

#### 🟢 Estadísticas Extendidas

```http
GET /dvsmart_indexing_api/api/monitoring/sftp-pool/extended
```

**Response:**

```json
{
  "active": 2,
  "idle": 3,
  "maxTotal": 10,
  "totalCreated": 5,
  "totalDestroyed": 0,
  "totalBorrows": 12543,
  "totalReturns": 12541,
  "totalFailures": 2,
  "utilizationPercent": 20.0,
  "lastLogTime": "2025-12-24T10:30:00Z"
}
```

#### 🟢 Health Check del Pool

```http
GET /dvsmart_indexing_api/api/monitoring/sftp-pool/health
```

**Response:**

```json
{
  "status": "HEALTHY",
  "healthy": true,
  "details": {
    "active": 2,
    "idle": 3,
    "utilizationPercent": 20.0,
    "totalFailures": 0,
    "totalBorrows": 12543
  }
}
```

**Estados:**

| Status | Condición | Acción |
|--------|-----------|--------|
| `HEALTHY` | utilization < 80% && failures < 10% | Normal |
| `WARNING` | utilization 80-95% | Aumentar pool |
| `DEGRADED` | failures > 10% | Revisar SFTP |
| `CRITICAL` | utilization > 95% | Urgente |

#### 🟡 Operaciones de Mantenimiento

```bash
# Forzar eviction
POST /api/monitoring/sftp-pool/evict

# Reset contadores
POST /api/monitoring/sftp-pool/reset

# Log manual
POST /api/monitoring/sftp-pool/log
```

### Endpoints de Batch Jobs

#### 🔍 Listar Todos los Jobs

```http
GET /dvsmart_indexing_api/api/monitoring/jobs
```

**Response:**

```json
{
  "jobNames": ["BATCH-INDEX-FULL"],
  "totalJobs": 1
}
```

#### 🔍 Jobs en Ejecución

```http
GET /dvsmart_indexing_api/api/monitoring/jobs/running
```

**Response:**

```json
{
  "runningJobs": [
    {
      "executionId": 12345,
      "jobName": "BATCH-INDEX-FULL",
      "status": "STARTED",
      "startTime": "2025-12-24T10:00:00",
      "duration": "15m 30s"
    }
  ],
  "count": 1
}
```

#### 🔍 Últimas Ejecuciones

```http
GET /dvsmart_indexing_api/api/monitoring/jobs/latest
```

#### 🔍 Historial de un Job

```http
GET /dvsmart_indexing_api/api/monitoring/jobs/{jobName}?page=0&size=10
```

#### 🔍 Detalle de una Ejecución

```http
GET /dvsmart_indexing_api/api/monitoring/jobs/execution/{executionId}
```

**Response:**

```json
{
  "executionId": 12345,
  "jobName": "BATCH-INDEX-FULL",
  "status": "COMPLETED",
  "createTime": "2025-12-24T10:00:00",
  "startTime": "2025-12-24T10:00:05",
  "endTime": "2025-12-24T10:30:00",
  "duration": "29m 55s",
  "exitCode": "COMPLETED",
  "exitDescription": null,
  "parameters": {
    "timestamp": "2025-12-24T10:00:00"
  },
  "steps": [
    {
      "stepName": "indexingStep",
      "status": "COMPLETED",
      "readCount": 11000000,
      "writeCount": 11000000,
      "commitCount": 110000,
      "rollbackCount": 0,
      "readSkipCount": 0,
      "processSkipCount": 0,
      "writeSkipCount": 0,
      "filterCount": 50000,
      "startTime": "2025-12-24T10:00:05",
      "endTime": "2025-12-24T10:30:00",
      "duration": "29m 55s",
      "exitCode": "COMPLETED"
    }
  ]
}
```

#### 🔍 Estadísticas Globales

```http
GET /dvsmart_indexing_api/api/monitoring/jobs/stats
```

#### 🔍 Health Check Global

```http
GET /dvsmart_indexing_api/api/monitoring/health
```

**Response:**

```json
{
  "status": "UP",
  "components": {
    "sftp": {
      "status": "HEALTHY",
      "active": 2,
      "available": 8
    },
    "batch": {
      "status": "RUNNING",
      "runningJobs": 1
    }
  }
}
```

---

## ⚙️ Arquitectura del Batch Job

### Fases del Procesamiento

```mermaid
stateDiagram-v2
    [*] --> Discovery: POST /api/batch/index/full
    Discovery --> Indexing: Queue lista
    Indexing --> Completed: Archivos procesados
    Completed --> [*]
    
    Discovery --> Failed: Error
    Indexing --> Failed: Error
    Failed --> [*]
```

### Componentes Principales

#### 1. DirectoryQueueItemReader

**Responsabilidad:** Lectura secuencial de archivos directorio por directorio.

**Características:**
- **Lazy Discovery:** Escaneo solo en primera llamada a `read()`
- **Memoria eficiente:** Solo archivos del directorio actual en memoria
- **Pool Lazy:** Usa 1-2 conexiones SFTP típicamente
- **ItemStream:** Control de ciclo de vida (`open`, `update`, `close`)

**Flujo:**

```java
public SftpFileEntry read() {
    // Primera llamada: discovery completo
    if (!discoveryCompleted) {
        executeDirectoryDiscovery();  // BFS recursivo
        discoveryCompleted = true;
    }
    
    // Retornar archivos del directorio actual
    if (!currentDirectoryFiles.isEmpty()) {
        return currentDirectoryFiles.poll();
    }
    
    // Si no hay más directorios, terminar
    if (directoryQueue.isEmpty()) {
        return null;
    }
    
    // Cargar siguiente directorio
    loadDirectoryFiles(directoryQueue.poll());
    return read();
}
```

**Estadísticas:**
- Log cada 100 directorios procesados
- Total de archivos y directorios al finalizar

#### 2. MetadataExtractorProcessor

**Responsabilidad:** Transformar `SftpFileEntry` → `ArchivoMetadata`.

**Características:**
- **Sin I/O:** Procesamiento en memoria
- **Paralelo:** 20 threads (configurable)
- **Filtros:** Directorios, ocultos, temporales
- **Error handling:** Crea metadata con estado FAILED en caso de error

```java
public ArchivoMetadata process(SftpFileEntry entry) {
    // Filtros
    if (entry.isDirectory() || entry.getFilename().startsWith(".")) {
        return null;  // Skip
    }
    
    try {
        return metadataService.toMetadata(entry);
    } catch (Exception e) {
        return createFailedMetadata(entry, e);
    }
}
```

**Archivos filtrados:**
- Directorios
- Archivos ocultos (`.file`)
- Temporales (`.tmp`, `.temp`, `.bak`, `~`, `~$`)
- Fuera de rango de tamaño (configurable)

#### 3. BulkUpsertMongoItemWriter

**Responsabilidad:** Persistencia masiva en MongoDB.

**Performance:**
- Sin bulk: 100-200 docs/s
- Con bulk: 3000-5000 docs/s

```java
public void write(Chunk<? extends ArchivoMetadata> chunk) {
    BulkOperations bulkOps = mongoTemplate.bulkOps(
        BulkOperations.BulkMode.UNORDERED,
        DisorganizedFilesIndexDocument.class
    );
    
    for (ArchivoMetadata metadata : chunk) {
        Query query = new Query(Criteria.where("idUnico").is(metadata.getIdUnico()));
        Update update = /* ... */;
        bulkOps.upsert(query, update);
    }
    
    BulkWriteResult result = bulkOps.execute();
}
```

**Métricas:**
- Conteo de inserciones/actualizaciones
- Conteo de éxitos/fallos
- Alerta si tasa de error > 5%

#### 4. CustomLazySftpSessionFactory

**Responsabilidad:** Gestión del pool de conexiones SFTP.

**Características:**
- Pool lazy con Apache Commons Pool2
- Validación pre-uso (`testOnBorrow`)
- Eviction automática de idle sessions
- Wrapper para devolución automática al pool

**Configuración:**

```java
GenericObjectPoolConfig<Session<SftpClient.DirEntry>> config = new GenericObjectPoolConfig<>();
config.setMaxTotal(maxPoolSize);
config.setMaxIdle(maxPoolSize);
config.setMinIdle(0);  // No mantener mínimas
config.setLifo(true);  // LIFO para reusar recientes
config.setTestOnBorrow(testOnBorrow);
config.setTestWhileIdle(true);
```

---

## 📊 Monitorización y Observabilidad

### Health Checks

```bash
# Health general
curl http://localhost:8080/dvsmart_indexing_api/actuator/health

# Detalle (incluye MongoDB y PostgreSQL)
curl http://localhost:8080/dvsmart_indexing_api/actuator/health | jq
```

**Response:**

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "mongo": {
      "status": "UP",
      "details": {
        "version": "5.0.0"
      }
    }
  }
}
```

### Métricas del Pool SFTP

| Métrica | Tipo | Descripción |
|---------|------|-------------|
| `active` | Gauge | Conexiones en uso |
| `idle` | Gauge | Conexiones disponibles |
| `utilizationPercent` | Gauge | % utilización |
| `totalBorrows` | Counter | Total adquisiciones |
| `totalFailures` | Counter | Total fallos |
| `totalCreated` | Counter | Total creadas |
| `totalDestroyed` | Counter | Total destruidas |

### Logs Estructurados

**Ejemplo de log completo:**

```
2025-12-24 10:00:00 - Starting FULL INDEXING JOB
2025-12-24 10:00:01 - ========================================
2025-12-24 10:00:01 - 🔄 OPEN: Initializing DirectoryQueueItemReader
2025-12-24 10:00:01 - Base directory: /disorganized_data
2025-12-24 10:00:01 - ========================================
2025-12-24 10:00:02 - ========================================
2025-12-24 10:00:02 - PHASE 1: DIRECTORY DISCOVERY
2025-12-24 10:00:02 - Base directory: /disorganized_data
2025-12-24 10:00:02 - ========================================
2025-12-24 10:00:25 - ========================================
2025-12-24 10:00:25 - ✅ Discovery completed in 23000 ms (23 seconds)
2025-12-24 10:00:25 - Total directories to process: 8543
2025-12-24 10:00:25 - ========================================
2025-12-24 10:00:25 - PHASE 2: FILE INDEXING
2025-12-24 10:00:25 - ========================================
2025-12-24 10:00:32 - Bulk write completed: 100 inserted, 0 updated | Success: 100, Failed: 0
2025-12-24 10:05:00 - 📊 Progress: 100 directories processed, 12500 files indexed
2025-12-24 10:29:30 - ========================================
2025-12-24 10:29:30 - ✅ INDEXING COMPLETED
2025-12-24 10:29:30 - Total files indexed: 11000000
2025-12-24 10:29:30 - Total directories processed: 8543
2025-12-24 10:29:30 - ========================================
```

### Logs Periódicos del Pool

```
2025-12-24 10:15:00 - ╔════════════════════════════════════════════════════╗
2025-12-24 10:15:00 - ║         SFTP POOL STATISTICS                       ║
2025-12-24 10:15:00 - ╠════════════════════════════════════════════════════╣
2025-12-24 10:15:00 - ║ Active Connections:         2                      ║
2025-12-24 10:15:00 - ║ Idle Connections:           3                      ║
2025-12-24 10:15:00 - ║ Max Pool Size:             10                      ║
2025-12-24 10:15:00 - ║ Total Created:              5                      ║
2025-12-24 10:15:00 - ║ Total Destroyed:            0                      ║
2025-12-24 10:15:00 - ║ Utilization:              20.0%                    ║
2025-12-24 10:15:00 - ║ Available Slots:            8                      ║
2025-12-24 10:15:00 - ╚════════════════════════════════════════════════════╝
```

---

## 🔧 Troubleshooting

### Errores Comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `Could not obtain SFTP session` | Pool saturado | Aumentar `pool.max-size` o `max-wait-millis` |
| `Connection reset by peer` | Servidor cerró idle | Reducir `min-evictable-idle-time-millis` |
| `Auth fail` | Credenciales incorrectas | Verificar `user` y `password` |
| `OutOfMemoryError` | Heap insuficiente | Aumentar `-Xmx` o reducir `thread-pool-size` |
| `E11000 duplicate key` | Índice violado | Verificar duplicados o `db.collection.reIndex()` |
| `Connection to MongoDB refused` | MongoDB caído | Verificar servicio y URI |
| `Connection to PostgreSQL refused` | PostgreSQL caído | Verificar servicio y URL |
| `Job already running` | Ejecución concurrente | Esperar o revisar estado en PostgreSQL |

### Comandos de Diagnóstico

```bash
# Verificar conectividad SFTP
telnet sftp-host 22

# Test de autenticación SFTP
sftp sftpsourceuser@sftp-host

# Verificar MongoDB
mongo --host localhost --port 30000 -u dvsmart_user -p eoQQqfTyMd --authenticationDatabase dvsmart-ms

# Verificar PostgreSQL
psql -h localhost -p 30005 -U dvsmart_ms -d dvsmart

# Verificar logs de la aplicación
tail -f logs/application.log

# Verificar jobs en ejecución
curl http://localhost:8080/dvsmart_indexing_api/api/monitoring/jobs/running | jq

# Verificar pool SFTP
curl http://localhost:8080/dvsmart_indexing_api/api/monitoring/sftp-pool/health | jq
```

### Diagnóstico de Pool SFTP

```bash
# Estado del pool
curl http://localhost:8080/dvsmart_indexing_api/api/monitoring/sftp-pool

# Si está saturado
curl -X POST http://localhost:8080/dvsmart_indexing_api/api/monitoring/sftp-pool/evict

# Logs detallados
curl -X POST http://localhost:8080/dvsmart_indexing_api/api/monitoring/sftp-pool/log
```

### Diagnóstico de Jobs

```bash
# Ver ejecución específica
curl http://localhost:8080/dvsmart_indexing_api/api/monitoring/jobs/execution/{id} | jq

# Ver historial
curl http://localhost:8080/dvsmart_indexing_api/api/monitoring/jobs/BATCH-INDEX-FULL | jq

# Estadísticas globales
curl http://localhost:8080/dvsmart_indexing_api/api/monitoring/jobs/stats | jq
```

### Limpieza de Jobs Huérfanos

```sql
-- Conectar a PostgreSQL
psql -h localhost -p 30005 -U dvsmart_ms -d dvsmart

-- Ver jobs STARTED antiguos (posibles huérfanos)
SELECT job_execution_id, job_instance_id, start_time, status
FROM BATCH_JOB_EXECUTION
WHERE status = 'STARTED'
  AND start_time < NOW() - INTERVAL '1 hour';

-- Marcar como FAILED (si es necesario)
UPDATE BATCH_JOB_EXECUTION
SET status = 'FAILED',
    end_time = NOW(),
    exit_code = 'FAILED',
    exit_message = 'Marcado como FAILED manualmente'
WHERE job_execution_id = {id};
```

---

## 🧪 Mantenimiento y Testing

### Tests Unitarios

```bash
# Ejecutar todos los tests
mvn test

# Ejecutar un test específico
mvn test -Dtest=FileMetadataServiceTest

# Tests con cobertura
mvn clean test jacoco:report
```

### Tests de Integración

```bash
# Tests de integración (requiere MongoDB y PostgreSQL corriendo)
mvn verify

# Skip tests de integración
mvn verify -DskipITs
```

### Limpieza de Datos

**MongoDB:**

```javascript
// Conectar a MongoDB
mongo --host localhost --port 30000 -u dvsmart_user -p eoQQqfTyMd --authenticationDatabase dvsmart-ms

// Eliminar todos los documentos
use dvsmart-ms
db.files_index.deleteMany({})

// Eliminar documentos con errores
db.files_index.deleteMany({ "indexing_status": "FAILED" })

// Contar documentos
db.files_index.countDocuments()

// Verificar índices
db.files_index.getIndexes()
```

**PostgreSQL:**

```sql
-- Conectar a PostgreSQL
psql -h localhost -p 30005 -U dvsmart_ms -d dvsmart

-- Limpiar jobs antiguos (más de 30 días)
DELETE FROM BATCH_STEP_EXECUTION
WHERE job_execution_id IN (
  SELECT job_execution_id
  FROM BATCH_JOB_EXECUTION
  WHERE create_time < NOW() - INTERVAL '30 days'
);

DELETE FROM BATCH_JOB_EXECUTION_PARAMS
WHERE job_execution_id IN (
  SELECT job_execution_id
  FROM BATCH_JOB_EXECUTION
  WHERE create_time < NOW() - INTERVAL '30 days'
);

DELETE FROM BATCH_JOB_EXECUTION
WHERE create_time < NOW() - INTERVAL '30 days';

-- Vacuum para recuperar espacio
VACUUM FULL;
```

### Reinicialización Completa

```bash
# 1. Detener la aplicación
kill {pid}

# 2. Limpiar MongoDB
mongo --host localhost --port 30000 -u dvsmart_user -p eoQQqfTyMd --authenticationDatabase dvsmart-ms <<EOF
use dvsmart-ms
db.files_index.deleteMany({})
EOF

# 3. Limpiar PostgreSQL
psql -h localhost -p 30005 -U dvsmart_ms -d dvsmart <<EOF
TRUNCATE TABLE BATCH_STEP_EXECUTION CASCADE;
TRUNCATE TABLE BATCH_JOB_EXECUTION_PARAMS CASCADE;
TRUNCATE TABLE BATCH_JOB_EXECUTION CASCADE;
TRUNCATE TABLE BATCH_JOB_INSTANCE CASCADE;
EOF

# 4. Reiniciar
java -jar target/dvsmart_indexing_api.jar
```

---

## 🔒 Consideraciones de Seguridad

### Credenciales

⚠️ **NUNCA** commitear credenciales en el repositorio.

**Usar variables de entorno:**

```bash
# .env (NO commitear)
MONGODB_URI=mongodb://user:pass@host:27017/db
POSTGRES_URL=jdbc:postgresql://host:5432/db
POSTGRES_USER=user
POSTGRES_PASS=pass
SFTP_HOST=host
SFTP_USER=user
SFTP_PASS=pass

# Cargar en aplicación
export $(cat .env | xargs)
java -jar target/dvsmart_indexing_api.jar
```

**Usar Kubernetes Secrets:**

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: dvsmart-secrets
type: Opaque
stringData:
  mongodb-uri: mongodb://user:pass@host:27017/db
  postgres-url: jdbc:postgresql://host:5432/db
  postgres-user: user
  postgres-pass: pass
  sftp-user: user
  sftp-pass: pass
```

```yaml
# Deployment
env:
  - name: SPRING_MONGODB_URI
    valueFrom:
      secretKeyRef:
        name: dvsmart-secrets
        key: mongodb-uri
  - name: SPRING_DATASOURCE_URL
    valueFrom:
      secretKeyRef:
        name: dvsmart-secrets
        key: postgres-url
```

### Red

**Firewall:**
- Permitir solo tráfico desde IPs conocidas
- SFTP: Puerto 22 (SSH)
- MongoDB: Puerto 27017 (interno)
- PostgreSQL: Puerto 5432 (interno)
- API: Puerto 8080 (exponer vía Ingress/LoadBalancer)

**SSL/TLS:**

```properties
# SFTP con certificado
sftp.origin.private-key=/path/to/private-key.pem
sftp.origin.passphrase=keypass

# MongoDB con TLS
spring.mongodb.uri=mongodb://user:pass@host:27017/db?ssl=true&sslInvalidHostNameAllowed=false

# PostgreSQL con SSL
spring.datasource.url=jdbc:postgresql://host:5432/db?ssl=true&sslmode=require
```

### Roles y Permisos

**MongoDB:**

```javascript
// Crear usuario con permisos mínimos
use dvsmart-ms
db.createUser({
  user: "dvsmart_user",
  pwd: "securepass",
  roles: [
    { role: "readWrite", db: "dvsmart-ms" }
  ]
})
```

**PostgreSQL:**

```sql
-- Crear usuario con permisos mínimos
CREATE USER dvsmart_ms WITH PASSWORD 'securepass';
GRANT CONNECT ON DATABASE dvsmart TO dvsmart_ms;
GRANT USAGE ON SCHEMA public TO dvsmart_ms;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO dvsmart_ms;
```

### Auditoría

**Logging de accesos:**

```properties
# Habilitar logs de seguridad
logging.level.org.springframework.security=DEBUG
logging.level.org.springframework.data.mongodb=DEBUG
```

**Registro de ejecuciones:**
- Spring Batch registra automáticamente todas las ejecuciones en PostgreSQL
- Incluye parámetros, tiempos, estados, errores

---

## 📚 Referencias

- [Spring Batch Documentation](https://docs.spring.io/spring-batch/docs/current/reference/html/)
- [Spring Integration SFTP](https://docs.spring.io/spring-integration/docs/current/reference/html/sftp.html)
- [Apache Commons Pool2](https://commons.apache.org/proper/commons-pool/)
- [MongoDB Java Driver](https://www.mongodb.com/docs/drivers/java/sync/current/)
- [PostgreSQL JDBC](https://jdbc.postgresql.org/documentation/)

---

## 📝 Licencia

Copyright (c) 2025 Indra Sistemas, S.A. All Rights Reserved.

The contents of this file are owned by Indra Sistemas, S.A. copyright holder.
This file can only be copied, distributed and used all or in part with the
written permission of Indra Sistemas, S.A, or in accordance with the terms and
conditions laid down in the agreement / contract under which supplied.

## 🤝 Soporte y Contacto

**Equipo de Mantenimiento**: DvSmart Reorganization Team  
**Contacto**: hahuaranga@indracompany.com  
**Repositorio**: [Enlace interno al repositorio]  
**Documentación Técnica**: [Enlace a documentación detallada]
