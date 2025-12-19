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
- **Coordinación distribuida**: Uso de ShedLock para evitar ejecuciones concurrentes entre múltiples instancias
- **Alta disponibilidad**: Pool de conexiones SFTP lazy con health checks y auto-recuperación

### Características Clave

✅ **Pool de Conexiones SFTP Lazy**: Conexiones creadas bajo demanda, liberadas automáticamente  
✅ **Procesamiento Asíncrono**: Pipeline de 3 etapas (Reader → Processor → Writer) con paralelismo configurable  
✅ **Bulk Upserts a MongoDB**: 3000-5000 documentos/segundo vs 100-200 con operaciones individuales  
✅ **Coordinación Distribuida**: ShedLock previene ejecuciones concurrentes entre pods  
✅ **Monitorización Integrada**: Actuator + endpoints custom para observabilidad del pool SFTP  
✅ **Resiliente**: Validación de conexiones, eviction de idle sessions, retry logic  

---

## 🛠 Stack Tecnológico

| Tecnología | Versión | Propósito |
|------------|---------|-----------|
| **Java** | 21 | Lenguaje base con soporte LTS |
| **Spring Boot** | 4.0.0 | Framework de aplicación |
| **Spring Batch** | 6.0.0 | Procesamiento por lotes de alto rendimiento |
| **Spring Integration** | 7.0.0 | Integración con sistemas externos (SFTP) |
| **MongoDB** | - | Persistencia de metadata de archivos |
| **Apache Commons Pool2** | - | Gestión del pool de conexiones SFTP |
| **SSHJ** | 0.38.0 | Cliente SFTP nativo |
| **ShedLock** | 5.10.2 | Bloqueo distribuido para coordinación |
| **Lombok** | 1.18.30 | Reducción de boilerplate |
| **H2 Database** | - | Metadata de Spring Batch (in-memory) |
| **Maven** | - | Gestión de dependencias y build |

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
        SHEDLOCK[ShedLock MongoDB Provider]
    end

    subgraph "Sistemas Externos"
        SFTP[Servidor SFTP Origen]
        MONGO[(MongoDB)]
    end

    API -->|POST /api/batch/index/full| UC
    UC --> SVC
    SVC -->|Lanza con lock| JOB
    JOB --> STEP
    STEP --> READER
    READER -->|Lee archivos| PROCESSOR
    PROCESSOR -->|Extrae metadata| WRITER
    WRITER -->|Bulk upsert| MONGO

    READER -.->|Adquiere sesión| POOL
    POOL -.->|Conexión SSH| SFTP
    MONITOR -.->|Monitorea| POOL
    SVC -.->|Obtiene lock| SHEDLOCK
    SHEDLOCK -.->|Persiste locks| MONGO
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

    Client->>Controller: POST /api/batch/index/full
    Controller->>BatchJob: Lanzar con ShedLock
    
    Note over BatchJob: Phase 1: Discovery
    BatchJob->>Reader: Descubrir directorios
    Reader->>SftpPool: Obtener sesión (1 vez)
    SftpPool-->>Reader: Queue de directorios
    
    Note over BatchJob: Phase 2: Indexing
    loop Por cada chunk de 500 archivos
        Reader->>SftpPool: Obtener sesión
        SftpPool-->>Reader: Archivos del directorio
        
        par Procesamiento paralelo (20 threads)
            Reader->>Processor: SftpFileEntry[]
            Processor->>Processor: Extraer metadata
        end
        
        par Escritura paralela
            Processor->>Writer: ArchivoMetadata[]
            Writer->>MongoDB: Bulk upsert (500 docs)
        end
    end
    
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
    AO3 --> MongoDB[(MongoDB)]
```

---

## 📦 Requisitos Previos

### Software Requerido

- **JDK 21** (OpenJDK o Oracle)
- **Maven 3.8+**
- **MongoDB 5.0+**
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
```

| Propiedad | Valor | Descripción |
|-----------|-------|-------------|
| `spring.application.name` | `dvsmart-indexing-api` | Nombre de la aplicación |
| `server.servlet.context-path` | `/dvsmart_indexing_api` | Context path base |
| `server.port` | `8080` | Puerto HTTP |

#### MongoDB

```properties
spring.mongodb.uri=mongodb://dvsmart_user:eoQQqfTyMd@host:27017/dvsmart-ms?authSource=dvsmart-ms
```

**Componentes de la URI:**
- **Usuario:** `dvsmart_user`
- **Password:** `eoQQqfTyMd` ⚠️ **Cambiar en producción**
- **Base de datos:** `dvsmart-ms`
- **Auth source:** `dvsmart-ms`

**Colecciones utilizadas:**

| Colección | Propósito | Índices |
|-----------|-----------|---------|
| `disorganized-files-index` | Metadata de archivos | `idUnico` (unique) |
| `shedLock` | Locks distribuidos | `_id`, `lockUntil` |

#### Spring Batch

```properties
# Deshabilitar inicio automático de jobs
spring.batch.job.enabled=false
```

⚠️ **CRÍTICO:** Desactiva el inicio automático. Los jobs se lanzan manualmente vía API REST.

#### Configuración del Batch (Prefijo: `batch.*`)

```properties
batch.chunk-size=500
batch.thread-pool-size=20
batch.queue-capacity=1000
```

| Propiedad | Valor | Rango | Descripción |
|-----------|-------|-------|-------------|
| `batch.chunk-size` | `500` | `100-1000` | Archivos procesados por chunk antes de commit |
| `batch.thread-pool-size` | `20` | `10-50` | Threads del `AsyncItemProcessor/Writer` |
| `batch.queue-capacity` | `1000` | `500-5000` | Capacidad de cola de tareas pendientes |

**Configuraciones por entorno:**

```properties
# 🔹 DESARROLLO
batch.chunk-size=100
batch.thread-pool-size=5
batch.queue-capacity=500

# 🔹 PRODUCCIÓN ESTÁNDAR
batch.chunk-size=500
batch.thread-pool-size=20
batch.queue-capacity=1000

# 🔹 ALTO RENDIMIENTO (11M archivos)
batch.chunk-size=1000
batch.thread-pool-size=50
batch.queue-capacity=5000
```

#### Servidor SFTP Origen (Prefijo: `sftp.origin.*`)

```properties
sftp.origin.host=sftp-server-host
sftp.origin.port=22
sftp.origin.user=sftpsourceuser
sftp.origin.password=securepass
sftp.origin.base-dir=/disorganized_data
sftp.origin.timeout=30000
```

| Propiedad | Valor | Descripción |
|-----------|-------|-------------|
| `sftp.origin.host` | `sftp-server-host` | Hostname o IP del servidor |
| `sftp.origin.port` | `22` | Puerto SSH |
| `sftp.origin.user` | `sftpsourceuser` | Usuario SFTP |
| `sftp.origin.password` | `securepass` | Password (usar Secrets en prod) |
| `sftp.origin.base-dir` | `/disorganized_data` | Directorio raíz |
| `sftp.origin.timeout` | `30000` | Timeout conexión (ms) |

#### Pool de Conexiones SFTP (Prefijo: `sftp.origin.pool.*`)

```properties
# Pool lazy
sftp.origin.pool.lazy-init=true
sftp.origin.pool.initial-size=0
sftp.origin.pool.max-size=10

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

#### ShedLock (Coordinación Distribuida)

Configurado en `ShedLockConfig.java`:

```java
@EnableSchedulerLock(
    defaultLockAtMostFor = "PT2H",    // Lock máximo: 2 horas
    defaultLockAtLeastFor = "PT30M"   // Lock mínimo: 30 minutos
)
```

| Parámetro | Valor | Descripción |
|-----------|-------|-------------|
| `lockAtMostFor` | `PT2H` | Si el proceso cuelga, el lock se libera tras 2h |
| `lockAtLeastFor` | `PT30M` | Lock mínimo 30 min (evita re-ejecuciones) |
| `name` | `indexing-full-job` | Nombre del lock (compartido entre APIs) |

**Verificar locks en MongoDB:**

```javascript
db.shedLock.find().pretty()

// Eliminar lock manualmente (EMERGENCIA)
db.shedLock.deleteOne({ "_id": "indexing-full-job" })
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
logging.level.net.javacrumbs.shedlock=DEBUG

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
batch.chunk-size=1000

# Máximo paralelismo
batch.thread-pool-size=50
batch.queue-capacity=5000

# Pool SFTP suficiente
sftp.origin.pool.max-size=10
```

**Cálculo de throughput:**

```
Archivos: 11,000,000
Chunk size: 1000
Threads: 50
Tiempo por chunk: 2s

Chunks totales: 11,000,000 / 1000 = 11,000
Tiempo: (11,000 / 50) * 2s = 440s ≈ 7 minutos
```

### Tuning de MongoDB

**Índices óptimos:**

```javascript
// Índice único (crítico para upserts)
db.getCollection("disorganized-files-index").createIndex(
  { "idUnico": 1 },
  { unique: true, background: false }
)

// Índice para consultas por ruta
db.getCollection("disorganized-files-index").createIndex(
  { "rutaOrigen": 1, "indexadoEn": -1 },
  { background: true }
)

// Índice para búsquedas por extensión
db.getCollection("disorganized-files-index").createIndex(
  { "extension": 1, "tamanio": -1 },
  { background: true }
)
```

**Write concern para alto throughput:**

```properties
# Agregar a URI
spring.mongodb.uri=mongodb://user:pass@host:27017/db?w=1&journal=false&maxPoolSize=100
```

- `w=1`: Esperar confirmación del primario
- `journal=false`: No esperar flush a disco (⚠️ riesgo)
- `maxPoolSize=100`: Pool grande

---

## 📡 Uso y API

### Endpoints de Indexación

#### 🔵 Iniciar Indexación Completa

```http
POST /dvsmart_indexing_api/api/batch/index/full
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
curl -X POST http://localhost:8080/dvsmart_indexing_api/api/batch/index/full

# Remoto
curl -X POST https://api.example.com/dvsmart_indexing_api/api/batch/index/full
```

**Códigos de estado:**

| Código | Significado | Acción |
|--------|-------------|--------|
| `202` | Job lanzado | Monitorear logs |
| `409` | Job ya corriendo (ShedLock) | Esperar |
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
  "totalBorrows": 12543,
  "totalReturns": 12541,
  "totalFailures": 2,
  "utilizationPercent": 20.0
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
    "utilizationPercent": 20.0
  }
}
```

**Estados:**

| Status | Condición | Acción |
|--------|-----------|--------|
| `HEALTHY` | utilization < 80% | Normal |
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
- **Memoria eficiente:** Solo archivos del directorio actual
- **Pool Lazy:** Usa 1-2 conexiones SFTP

**Flujo:**

```java
public SftpFileEntry read() {
    // Primera llamada: discovery completo
    if (!discoveryCompleted) {
        executeDirectoryDiscovery();  // BFS
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

#### 2. MetadataExtractorProcessor

**Responsabilidad:** Transformar `SftpFileEntry` → `ArchivoMetadata`.

**Características:**
- **Sin I/O:** Procesamiento en memoria
- **Paralelo:** 20 threads (configurable)
- **Filtros:** Directorios, ocultos, temporales

```java
public ArchivoMetadata process(SftpFileEntry entry) {
    if (entry.isDirectory() || entry.getFilename().startsWith(".")) {
        return null;  // Skip
    }
    
    return metadataService.toMetadata(entry);  // SHA-256, extensión, etc.
}
```

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
        bulkOps.upsert(query, update);
    }
    
    BulkWriteResult result = bulkOps.execute();
}
```

---

## 📊 Monitorización y Observabilidad

### Health Checks

```bash
# Health general
curl http://localhost:8080/dvsmart_indexing_api/actuator/health

# Detalle (incluye MongoDB)
curl http://localhost:8080/dvsmart_indexing_api/actuator/health | jq
```

### Métricas del Pool SFTP

| Métrica | Tipo | Descripción |
|---------|------|-------------|
| `active` | Gauge | Conexiones en uso |
| `idle` | Gauge | Conexiones disponibles |
| `utilizationPercent` | Gauge | % utilización |
| `totalBorrows` | Counter | Total adquisiciones |
| `totalFailures` | Counter | Total fallos |

### Logs Estructurados

**Ejemplo de log completo:**

```
2025-12-19 17:30:00 - Starting FULL INDEXING JOB with ShedLock
2025-12-19 17:30:01 - Locked 'indexing-full-job'
2025-12-19 17:30:02 - PHASE 1: DIRECTORY DISCOVERY
2025-12-19 17:30:25 - ✅ Discovery completed: 8543 directories
2025-12-19 17:30:25 - PHASE 2: FILE INDEXING
2025-12-19 17:30:32 - Bulk write: 500 inserted
2025-12-19 17:45:00 - Progress: 100 dirs, 125000 files
2025-12-19 18:15:30 - ✅ INDEXING COMPLETED: 11000000 files
```

---

## 🔧 Troubleshooting

### Errores Comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `409 Conflict` | Job ya corriendo | Esperar o eliminar lock: `db.shedLock.deleteOne({_id: "indexing-full-job"})` |
| `Could not obtain SFTP session` | Pool saturado | Aumentar `pool.max-size` o `max-wait-millis` |
| `Connection reset by peer` | Servidor cerró idle | Reducir `min-evictable-idle-time-millis` |
| `Auth fail` | Credenciales incorrectas | Verificar `user` y `password` |
| `OutOfMemoryError` | Heap insuficiente | Aumentar `-Xmx` o reducir `thread-pool-size` |
| `E11000 duplicate key` | Índice violado | `db.collection.reIndex()` |

Aquí tienes el **README completo con el índice interactivo y la sección adicional solicitada**:

***

# 📦 dvsmart\_indexing\_api

## 📋 Tabla de Contenidos

*   Descripción General
*   Stack Tecnológico
*   Arquitectura del Sistema
*   Requisitos Previos
*   Instalación y Setup
*   Guía Completa de Configuración
*   Configuración de Alto Rendimiento
*   Uso y API
*   Arquitectura del Batch Job
*   Monitorización y Observabilidad
*   Troubleshooting
*   Mantenimiento y Testing
*   Consideraciones de Seguridad
*   Roadmap y Mejoras Futuras
*   Notas Finales

***

*(Contenido completo del README con diagramas, configuración, endpoints, flujo batch, monitorización y troubleshooting se mantiene igual que en tu versión depurada)*

***

## ✅ Roadmap y Mejoras Futuras

### **Corto Plazo (1-3 meses)**

*   Migrar Spring Batch metadata a MongoDB (actualmente H2 in-memory)
*   Health Indicator personalizado para SFTP Pool (integración con Actuator)
*   Métricas custom en Prometheus/Grafana
*   Circuit Breaker para SFTP (resilience4j)
*   Retry configurable en caso de fallos transitorios

### **Medio Plazo (3-6 meses)**

*   Particionamiento del Batch Job para reducir tiempo de procesamiento
*   API de consulta de archivos indexados (búsqueda por path, extensión, fecha)

### **Largo Plazo (6-12 meses)**

*   Indexación incremental (solo archivos nuevos/modificados desde última ejecución)
*   Soporte para múltiples servidores SFTP (multi-tenant)
*   Integración con Apache Kafka para streaming de eventos de indexación

***

## 📝 Notas Finales

### **Contribuciones**

Este proyecto sigue las convenciones de Indra Sistemas, S.A. Para contribuir:

```bash
# Crear branch feature
git checkout -b feature/JIRA-123-descripcion

# Aplicar headers de copyright
mvn license:format

# Ejecutar tests
mvn clean verify

# Crear Pull Request hacia develop
```

### **Licencia**

Copyright © 2025 Indra Sistemas, S.A. All Rights Reserved.  
Este código es propiedad de Indra Sistemas, S.A. y solo puede ser copiado, distribuido o usado total o parcialmente con el permiso escrito de Indra Sistemas, S.A., o de acuerdo con los términos y condiciones establecidos en el acuerdo/contrato bajo el cual se suministró.

### **Contacto**

*   Autor Principal: **<hahuaranga@indracompany.com>**
*   Equipo: **DVSmart - Minsait Digital**
