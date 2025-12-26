# dvsmart_indexing_api

## 📋 Tabla de Contenidos

- [Descripción General](#-descripción-general)
- [Stack Tecnológico](#-stack-tecnológico)
- [Arquitectura del Sistema](#-arquitectura-del-sistema)
  - [Diagrama de Componentes](#diagrama-de-componentes)
  - [Flujo de Procesamiento](#flujo-de-procesamiento)
  - [Arquitectura Hexagonal](#arquitectura-hexagonal)
  - [Técnicas de Procesamiento](#técnicas-de-procesamiento)
  - [Sistema de Auditoría](#sistema-de-auditoría)
- [Requisitos Previos](#-requisitos-previos)
- [Instalación y Setup](#-instalación-y-setup)
- [Guía Completa de Configuración](#-guía-completa-de-configuración)
- [Configuración de Alto Rendimiento](#-configuración-de-alto-rendimiento)
- [Uso y API](#-uso-y-api)
- [Monitorización y Observabilidad](#-monitorización-y-observabilidad)
- [Troubleshooting](#-troubleshooting)
- [Mantenimiento y Testing](#-mantenimiento-y-testing)
- [Referencias](#-referencias)
- [Soporte y Contacto](#-soporte-y-contacto)

---

## 🎯 Descripción General

**dvsmart_indexing_api** es un microservicio empresarial de alto rendimiento diseñado para indexar masivamente archivos almacenados en servidores SFTP. El sistema procesa millones de archivos de forma distribuida, extrayendo metadata y persistiéndola en MongoDB para su posterior consulta y organización.

### Casos de Uso Principales

- **Indexación masiva**: Procesamiento de 11M+ archivos en ~30-60 minutos
- **Descubrimiento de estructura**: Mapeo recursivo de jerarquías de directorios complejas
- **Auditoría completa**: Registro detallado de cada ejecución en MongoDB
- **Alta disponibilidad**: Pool de conexiones SFTP con gestión automática de recursos

### Características Clave

✅ **Pool de Conexiones SFTP Lazy**: Conexiones creadas bajo demanda, liberadas automáticamente  
✅ **Procesamiento Asíncrono**: Pipeline de 3 etapas (Reader → Processor → Writer) con paralelismo configurable  
✅ **Bulk Upserts a MongoDB**: 3000-5000 documentos/segundo vs 100-200 con operaciones individuales  
✅ **Persistencia Dual**: PostgreSQL para metadatos de Spring Batch, MongoDB para archivos indexados y auditoría  
✅ **Sistema de Auditoría**: Registro completo de métricas, throughput y estado de cada ejecución  
✅ **Monitorización Integrada**: Actuator + endpoints custom para observabilidad del pool SFTP y jobs  
✅ **Resiliente**: Validación de conexiones, eviction de idle sessions, retry logic  

---

## 🛠 Stack Tecnológico

| Tecnología | Versión | Propósito |
|------------|---------|-----------|
| **Java** | 21 | Lenguaje base con soporte LTS |
| **Spring Boot** | 4.0.1 | Framework de aplicación |
| **Spring Batch** | 6.0.0 | Procesamiento por lotes de alto rendimiento |
| **Spring Integration** | 7.0.0 | Integración con sistemas externos (SFTP) |
| **MongoDB** | 5.0+ | Persistencia de metadata de archivos y auditoría |
| **PostgreSQL** | 12+ | Metadata de Spring Batch (job repository) |
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
        LISTENER[JobExecutionAuditListener]
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
        AUDIT_SVC[JobAuditService]
    end

    subgraph "Sistemas Externos"
        SFTP[Servidor SFTP Origen]
        MONGO[(MongoDB)]
        POSTGRES[(PostgreSQL)]
    end

    API -->|POST /api/batch/index/full| UC
    UC --> SVC
    SVC -->|Lanza job| JOB
    JOB -->|beforeJob/afterJob| LISTENER
    LISTENER --> AUDIT_SVC
    JOB --> STEP
    STEP --> READER
    READER -->|Lee archivos| PROCESSOR
    PROCESSOR -->|Extrae metadata| WRITER
    WRITER -->|Bulk upsert| MONGO

    READER -.->|Adquiere sesión| POOL
    POOL -.->|Conexión SSH| SFTP
    MONITOR -.->|Monitorea| POOL
    JOB -.->|Persiste estado| POSTGRES
    AUDIT_SVC -.->|Guarda auditoría| MONGO
```

### Flujo de Procesamiento

```mermaid
sequenceDiagram
    participant Client
    participant Controller
    participant AuditListener
    participant BatchJob
    participant Reader
    participant SftpPool
    participant Processor
    participant Writer
    participant MongoDB
    participant PostgreSQL

    Client->>Controller: POST /api/batch/index/full
    Controller->>BatchJob: Lanzar job
    
    Note over BatchJob: JobExecutionListener.beforeJob()
    BatchJob->>AuditListener: beforeJob(jobExecution)
    AuditListener->>MongoDB: INSERT audit record (STARTED)
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
    
    Note over BatchJob: JobExecutionListener.afterJob()
    BatchJob->>AuditListener: afterJob(jobExecution)
    AuditListener->>PostgreSQL: Leer métricas finales
    AuditListener->>MongoDB: UPDATE audit record (COMPLETED)
    
    BatchJob->>PostgreSQL: Actualizar estado COMPLETED
    BatchJob-->>Controller: JobExecutionId
    Controller-->>Client: 202 Accepted
```

### Arquitectura Hexagonal

```mermaid
graph LR
    subgraph "Dominio"
        M1[ArchivoMetadata]
        M2[JobExecutionAudit]
        S1[FileMetadataService]
        S2[DirectoryDiscoveryService]
        S3[JobAuditService]
    end

    subgraph "Puertos Entrada"
        PI[StartIndexFullUseCase]
    end

    subgraph "Adaptadores Entrada"
        AI1[BatchIndexingController]
        AI2[MonitoringController]
        AI3[JobAuditController]
    end

    subgraph "Adaptadores Salida"
        AO1[DirectoryQueueItemReader]
        AO2[MetadataExtractorProcessor]
        AO3[BulkUpsertMongoItemWriter]
        AO4[CustomLazySftpSessionFactory]
        AO5[JobExecutionAuditListener]
    end

    AI1 --> PI
    AI2 --> PI
    AI3 --> S3
    PI --> S1
    PI --> S2
    S1 --> M1
    S3 --> M2
    AO1 --> S2
    AO1 --> AO4
    AO2 --> S1
    AO3 --> MongoDB[(MongoDB)]
    AO5 --> S3
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
- Procesamiento BFS (Breadth-First Search) recursivo

**Implementación:**
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

#### 2. **Async Processing Pipeline**

```mermaid
flowchart LR
    R[Reader<br/>1 thread] --> Q1[Queue<br/>Capacity: 1000]
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
- Queue capacity evita saturación de memoria

**Configuración:**
```java
@Bean
AsyncItemProcessor<SftpFileEntry, ArchivoMetadata> asyncMetadataProcessor() {
    AsyncItemProcessor<SftpFileEntry, ArchivoMetadata> asyncProcessor = 
        new AsyncItemProcessor<>(metadataExtractorProcessor);
    asyncProcessor.setTaskExecutor(indexingTaskExecutor());
    return asyncProcessor;
}

@Bean
TaskExecutor indexingTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(batchProps.getThreadPoolSize());  // 20
    executor.setMaxPoolSize(batchProps.getThreadPoolSize());
    executor.setQueueCapacity(batchProps.getQueueCapacity());  // 1000
    executor.initialize();
    return executor;
}
```

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

**Implementación:**
```java
public void write(Chunk<? extends ArchivoMetadata> chunk) {
    BulkOperations bulkOps = mongoTemplate.bulkOps(
        BulkOperations.BulkMode.UNORDERED,
        DisorganizedFilesIndexDocument.class
    );
    
    for (ArchivoMetadata metadata : chunk) {
        Query query = new Query(Criteria.where("idUnico").is(metadata.getIdUnico()));
        Update update = /* build update */;
        bulkOps.upsert(query, update);
    }
    
    BulkWriteResult result = bulkOps.execute();
}
```

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
- Validación pre-uso con comando SFTP simple

**Configuración:**
```properties
sftp.origin.pool.lazy-init=true
sftp.origin.pool.initial-size=0
sftp.origin.pool.max-size=10
sftp.origin.pool.test-on-borrow=true
sftp.origin.pool.time-between-eviction-runs-millis=60000
sftp.origin.pool.min-evictable-idle-time-millis=300000
```

### Sistema de Auditoría

#### Arquitectura de Auditoría

```mermaid
graph TB
    subgraph "Spring Batch Metadata (PostgreSQL)"
        PG_JOB[BATCH_JOB_EXECUTION<br/>Estado global]
        PG_STEP[BATCH_STEP_EXECUTION<br/>Métricas técnicas]
        PG_JOB --> PG_STEP
    end
    
    subgraph "Auditoría de Negocio (MongoDB)"
        MONGO_AUDIT[job_executions_audit<br/>Métricas + KPIs]
    end
    
    subgraph "JobExecutionListener"
        LISTENER[beforeJob/afterJob]
    end
    
    LISTENER -->|Lee métricas| PG_STEP
    LISTENER -->|Calcula KPIs| MONGO_AUDIT
    
    style PG_JOB fill:#87CEEB
    style PG_STEP fill:#87CEEB
    style MONGO_AUDIT fill:#90EE90
```

#### Datos Capturados en Auditoría

**Información básica:**
- `auditId`: ID único (jobName-jobExecutionId-uuid)
- `jobExecutionId`: Referencia a Spring Batch
- `serviceName`: Nombre del microservicio
- `jobName`: Nombre del job (BATCH-INDEX-FULL)

**Tiempos y duración:**
- `startTime`: Fecha/hora de inicio
- `endTime`: Fecha/hora de fin
- `durationMs`: Duración en milisegundos
- `durationFormatted`: Formato legible (ej: "29m 55s")

**Estado y resultados:**
- `status`: STARTED, COMPLETED, FAILED, STOPPED
- `exitCode`: COMPLETED, FAILED, UNKNOWN
- `exitDescription`: Descripción del resultado

**Métricas de procesamiento:**
- `totalFilesIndexed`: Total de archivos indexados (write_count)
- `totalFilesProcessed`: Total procesados (read_count)
- `totalFilesSkipped`: Total saltados (skips + filters)
- `totalFilesFailed`: Total fallidos (write_skip + rollbacks)
- `totalDirectoriesProcessed`: Directorios escaneados

**Métricas de rendimiento:**
- `readCount`, `writeCount`, `commitCount`, `rollbackCount`
- `filesPerSecond`: Throughput calculado (⭐ único en auditoría)

**Información de errores:**
- `errorDescription`: Mensaje del error principal
- `errorStackTrace`: Stack trace truncado (10 líneas)
- `failureCount`: Número de fallos

**Información del servidor:**
- `hostname`: Host donde se ejecutó
- `instanceId`: Pod de Kubernetes (si aplica)
- `jobParameters`: Parámetros de entrada

#### Comparación: Spring Batch vs Auditoría

| Aspecto | Spring Batch (PostgreSQL) | Auditoría (MongoDB) |
|---------|--------------------------|---------------------|
| **Propósito** | Control técnico del framework | Métricas de negocio y BI |
| **Usuario** | Spring Batch internamente | Dashboards, reportes, analistas |
| **Queries** | JOINs complejos | Queries simples |
| **Performance lectura** | Lento (múltiples tablas) | Rápido (documento único) |
| **Datos únicos** | ExecutionContext, versioning | filesPerSecond, hostname, KPIs |
| **Uso** | Restart jobs, recovery | Reportes, análisis, alertas |

**Ejemplo de redundancia beneficiosa:**

```javascript
// ✅ Query simple en MongoDB (auditoría)
db.job_executions_audit.find({
  jobName: "BATCH-INDEX-FULL",
  status: "COMPLETED",
  filesPerSecond: { $gt: 5000 }
}).sort({ startTime: -1 })

// vs

// ❌ Query compleja en PostgreSQL (Spring Batch)
SELECT 
    je.job_execution_id,
    je.status,
    se.write_count,
    (se.write_count::float / EXTRACT(EPOCH FROM (je.end_time - je.start_time))) as files_per_second
FROM batch_job_execution je
JOIN batch_step_execution se ON je.job_execution_id = se.job_execution_id
JOIN batch_job_instance ji ON je.job_instance_id = ji.job_instance_id
WHERE ji.job_name = 'BATCH-INDEX-FULL'
  AND je.status = 'COMPLETED'
  AND (se.write_count::float / EXTRACT(EPOCH FROM (je.end_time - je.start_time))) > 5000
ORDER BY je.start_time DESC;
```

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

### Inicializar Base de Datos MongoDB

**⚠️ IMPORTANTE:** Ejecutar el script de inicialización ANTES del primer despliegue.

```bash
# Desarrollo (local con NodePort)
mongo localhost:30000/dvsmart-ms \
  -u dvsmart_user \
  -p eoQQqfTyMd \
  --authenticationDatabase dvsmart-ms \
  scripts/mongodb/01_init_collections.js

# Producción (cluster interno)
mongo dvsmart-catalog-mongodb.dvsmart.svc.cluster.local:27017/dvsmart-ms \
  -u dvsmart_user \
  -p eoQQqfTyMd \
  --authenticationDatabase dvsmart-ms \
  scripts/mongodb/01_init_collections.js
```

**El script crea:**
- ✅ Colección `files_index` con validación de esquema y 7 índices
- ✅ Colección `job_executions_audit` con validación de esquema y 9 índices
- ✅ Documentos de ejemplo en ambas colecciones
- ✅ Usuario de aplicación con permisos readWrite

**Verificar que todo está OK:**

```javascript
// Conectar a MongoDB
mongo localhost:30000/dvsmart-ms -u dvsmart_user -p eoQQqfTyMd --authenticationDatabase dvsmart-ms

// Verificar colecciones
show collections
// Debe mostrar: files_index, job_executions_audit

// Verificar índices de files_index
db.files_index.getIndexes().length
// Debe retornar: 8 (incluyendo _id)

// Verificar índices de job_executions_audit
db.job_executions_audit.getIndexes().length
// Debe retornar: 10 (incluyendo _id)

// Verificar documentos de ejemplo
db.files_index.countDocuments()
// Debe retornar: 5

db.job_executions_audit.countDocuments()
// Debe retornar: 3
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

**Verificar tablas de Spring Batch:**

Después del primer arranque, Spring Batch creará automáticamente las siguientes tablas:

```sql
-- Verificar tablas creadas
\dt

-- Debe mostrar:
-- BATCH_JOB_INSTANCE
-- BATCH_JOB_EXECUTION
-- BATCH_JOB_EXECUTION_PARAMS
-- BATCH_STEP_EXECUTION
-- BATCH_JOB_EXECUTION_CONTEXT
-- BATCH_STEP_EXECUTION_CONTEXT
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

### Ejecución Local

```bash
# Modo development
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
| `spring.application.name` | `dvsmart-indexing-api` | Nombre usado en auditoría |
| `server.servlet.context-path` | `/dvsmart_indexing_api` | Context path base |
| `server.port` | `8080` | Puerto HTTP |
| `server.shutdown` | `graceful` | Espera jobs activos antes de cerrar |

#### MongoDB

```properties
spring.mongodb.uri=mongodb://dvsmart_user:eoQQqfTyMd@localhost:30000/dvsmart-ms?authSource=dvsmart-ms

# ✅ CRÍTICO: Desactivar auto-creación de índices
# Los índices se crean mediante script 01_init_collections.js
spring.data.mongodb.auto-index-creation=false
```

**Colecciones utilizadas:**

| Colección | Propósito | Índices | Documentos ejemplo |
|-----------|-----------|---------|-------------------|
| `files_index` | Metadata de archivos indexados | 8 índices (1 único, 7 búsqueda) | 5 |
| `job_executions_audit` | Auditoría de ejecuciones | 10 índices (2 únicos, 8 búsqueda) | 3 |

#### PostgreSQL (Spring Batch Repository)

```properties
spring.datasource.url=jdbc:postgresql://localhost:30005/dvsmart
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.username=dvsmart_ms
spring.datasource.password=OgxjdNEeQl
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5
```

**Tablas de Spring Batch (creadas automáticamente):**

| Tabla | Propósito |
|-------|-----------|
| `BATCH_JOB_INSTANCE` | Instancias de jobs |
| `BATCH_JOB_EXECUTION` | Ejecuciones de jobs |
| `BATCH_STEP_EXECUTION` | Ejecuciones de steps (⭐ métricas detalladas) |
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
| `initial-size` | `0` | Pool completamente lazy |
| `max-size` | `10` | Tamaño máximo del pool |
| `test-on-borrow` | `true` | **CRÍTICO:** Validar antes de usar |
| `min-evictable-idle-time-millis` | `300000` | 5 min idle antes de cerrar |

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

### Tuning de Spring Batch (11M archivos)

```properties
batch.chunk-size=500
batch.thread-pool-size=50
batch.queue-capacity=5000
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

**Índices ya creados por script:**

```javascript
// ✅ Ya están en 01_init_collections.js
// files_index: 8 índices
// job_executions_audit: 10 índices
```

**Write concern para alto throughput:**

```properties
# Agregar a URI
spring.mongodb.uri=mongodb://user:pass@host:27017/db?w=1&maxPoolSize=100
```

### Tuning de PostgreSQL

```sql
-- Aumentar shared_buffers para cache
ALTER SYSTEM SET shared_buffers = '2GB';

-- Aumentar work_mem para ordenamientos
ALTER SYSTEM SET work_mem = '64MB';

-- Checkpoint menos frecuentes
ALTER SYSTEM SET checkpoint_timeout = '30min';

-- Aplicar cambios
SELECT pg_reload_conf();
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
```

### Endpoints de Auditoría

#### 📊 Historial de Auditoría de un Job

```http
GET /dvsmart_indexing_api/api/monitoring/audit/jobs/{jobName}
```

**Ejemplo:**

```bash
curl http://localhost:8080/dvsmart_indexing_api/api/monitoring/audit/jobs/BATCH-INDEX-FULL | jq
```

**Response:**

```json
[
  {
    "id": "676b1234567890abcdef1234",
    "auditId": "BATCH-INDEX-FULL-12345-a1b2c3d4",
    "jobExecutionId": 12345,
    "serviceName": "dvsmart-indexing-api",
    "jobName": "BATCH-INDEX-FULL",
    "startTime": "2025-12-26T10:00:00Z",
    "endTime": "2025-12-26T10:30:00Z",
    "durationMs": 1800000,
    "durationFormatted": "30m 0s",
    "status": "COMPLETED",
    "exitCode": "COMPLETED",
    "totalFilesIndexed": 11000000,
    "totalFilesProcessed": 11050000,
    "totalFilesSkipped": 50000,
    "totalFilesFailed": 0,
    "filesPerSecond": 6111.11,
    "hostname": "indexing-api-pod-abc123",
    "instanceId": "indexing-api-pod-abc123"
  }
]
```

#### 📊 Ejecuciones por Estado

```http
GET /dvsmart_indexing_api/api/monitoring/audit/status/{status}
```

**Ejemplos:**

```bash
# Ejecuciones completadas
curl http://localhost:8080/dvsmart_indexing_api/api/monitoring/audit/status/COMPLETED | jq

# Ejecuciones fallidas
curl http://localhost:8080/dvsmart_indexing_api/api/monitoring/audit/status/FAILED | jq

# Ejecuciones en curso
curl http://localhost:8080/dvsmart_indexing_api/api/monitoring/audit/status/STARTED | jq
```

#### 📊 Detalle de una Ejecución

```http
GET /dvsmart_indexing_api/api/monitoring/audit/execution/{jobExecutionId}
```

**Ejemplo:**

```bash
curl http://localhost:8080/dvsmart_indexing_api/api/monitoring/audit/execution/12345 | jq
```

#### 📊 Estadísticas Globales de Auditoría

```http
GET /dvsmart_indexing_api/api/monitoring/audit/stats
```

**Response:**

```json
{
  "totalExecutions": 150,
  "completedExecutions": 140,
  "failedExecutions": 8,
  "startedExecutions": 2
}
```

#### 📊 Últimas Ejecuciones

```http
GET /dvsmart_indexing_api/api/monitoring/audit/latest
```

#### 📊 Ejecuciones en Rango de Fechas

```http
GET /dvsmart_indexing_api/api/monitoring/audit/range?start={ISO_DATE}&end={ISO_DATE}
```

**Ejemplo:**

```bash
curl "http://localhost:8080/dvsmart_indexing_api/api/monitoring/audit/range?start=2025-12-01T00:00:00Z&end=2025-12-31T23:59:59Z" | jq
```

### Endpoints de Monitorización SFTP Pool

#### 🟢 Estadísticas Básicas del Pool

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

#### 🟢 Health Check del Pool

```http
GET /dvsmart_indexing_api/api/monitoring/sftp-pool/health
```

**Estados:**

| Status | Condición | Acción |
|--------|-----------|--------|
| `HEALTHY` | utilization < 80% && failures < 10% | Normal |
| `WARNING` | utilization 80-95% | Aumentar pool |
| `DEGRADED` | failures > 10% | Revisar SFTP |
| `CRITICAL` | utilization > 95% | Urgente |

### Endpoints de Batch Jobs

#### 🔍 Detalle de una Ejecución (Spring Batch)

```http
GET /dvsmart_indexing_api/api/monitoring/jobs/execution/{executionId}
```

**Response incluye:**
- Estado del job
- Métricas de todos los steps
- Parámetros de entrada
- Tiempos de ejecución

---

## 📊 Monitorización y Observabilidad

### Health Checks

```bash
# Health general
curl http://localhost:8080/dvsmart_indexing_api/actuator/health | jq

# Debe mostrar:
# - MongoDB: UP
# - PostgreSQL: UP
# - diskSpace: UP
```

### Métricas Disponibles

| Fuente | Métricas | Propósito |
|--------|----------|-----------|
| **PostgreSQL** | read_count, write_count, commit_count | Control técnico de Spring Batch |
| **MongoDB (auditoría)** | filesPerSecond, throughput, KPIs | Dashboards y reportes de negocio |
| **Pool SFTP** | active, idle, utilization | Monitoreo de recursos |

### Logs Estructurados

**Ejemplo de log completo:**

```
2025-12-26 10:00:00 - Starting FULL INDEXING JOB
2025-12-26 10:00:00 - ════════════════════════════════════════════════════════
2025-12-26 10:00:00 - 📋 AUDIT: Creating audit record for job execution
2025-12-26 10:00:00 -    Job Name: BATCH-INDEX-FULL
2025-12-26 10:00:00 -    Execution ID: 12345
2025-12-26 10:00:00 - ════════════════════════════════════════════════════════
2025-12-26 10:00:00 - ✅ Audit record created: auditId=BATCH-INDEX-FULL-12345-a1b2c3d4, jobExecutionId=12345
2025-12-26 10:00:01 - ════════════════════════════════════════════════════════
2025-12-26 10:00:01 - 🔄 OPEN: Initializing DirectoryQueueItemReader
2025-12-26 10:00:01 - Base directory: /disorganized_data
2025-12-26 10:00:01 - ════════════════════════════════════════════════════════
2025-12-26 10:00:02 - PHASE 1: DIRECTORY DISCOVERY
2025-12-26 10:00:25 - ✅ Discovery completed in 23000 ms (23 seconds)
2025-12-26 10:00:25 - Total directories to process: 8543
2025-12-26 10:00:25 - PHASE 2: FILE INDEXING
2025-12-26 10:00:32 - Bulk write completed: 100 inserted, 0 updated | Success: 100, Failed: 0
2025-12-26 10:05:00 - 📊 Progress: 100 directories processed, 12500 files indexed
2025-12-26 10:29:30 - ✅ INDEXING COMPLETED
2025-12-26 10:29:30 - Total files indexed: 11000000
2025-12-26 10:29:30 - Total directories processed: 8543
2025-12-26 10:29:30 - ════════════════════════════════════════════════════════
2025-12-26 10:29:30 - 📋 AUDIT: Updating audit record for job execution
2025-12-26 10:29:30 -    Status: COMPLETED
2025-12-26 10:29:30 - ✅ Audit record updated: auditId=BATCH-INDEX-FULL-12345-a1b2c3d4, status=COMPLETED, filesIndexed=11000000, duration=30m 0s
```

### Consultas Útiles en MongoDB

```javascript
// Top 10 ejecuciones más rápidas
db.job_executions_audit.find({
  status: "COMPLETED"
}).sort({ filesPerSecond: -1 }).limit(10)

// Ejecuciones con más de 10M archivos
db.job_executions_audit.find({
  totalFilesIndexed: { $gt: 10000000 }
})

// Promedio de throughput
db.job_executions_audit.aggregate([
  { $match: { status: "COMPLETED" } },
  { $group: {
      _id: null,
      avgThroughput: { $avg: "$filesPerSecond" },
      maxThroughput: { $max: "$filesPerSecond" },
      minThroughput: { $min: "$filesPerSecond" }
  }}
])

// Ejecuciones por día (últimos 30 días)
db.job_executions_audit.aggregate([
  {
    $match: {
      startTime: {
        $gte: new Date(new Date().setDate(new Date().getDate() - 30))
      }
    }
  },
  {
    $group: {
      _id: { $dateToString: { format: "%Y-%m-%d", date: "$startTime" } },
      count: { $sum: 1 },
      avgDuration: { $avg: "$durationMs" },
      totalFiles: { $sum: "$totalFilesIndexed" }
    }
  },
  { $sort: { _id: 1 } }
])
```

---

## 🔧 Troubleshooting

### Errores Comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `Could not obtain SFTP session` | Pool saturado | Aumentar `pool.max-size` |
| `Connection reset by peer` | Servidor cerró idle | Reducir `min-evictable-idle-time-millis` |
| `Auth fail` | Credenciales incorrectas | Verificar `user` y `password` |
| `OutOfMemoryError` | Heap insuficiente | Aumentar `-Xmx` |
| `E11000 duplicate key` | Índice violado | `db.collection.reIndex()` |
| `Audit record not found` | Job no registrado | Verificar listener configurado |

### Comandos de Diagnóstico

```bash
# Verificar conectividad SFTP
telnet sftp-host 22

# Verificar MongoDB
mongo localhost:30000/dvsmart-ms -u dvsmart_user -p eoQQqfTyMd --authenticationDatabase dvsmart-ms

# Verificar PostgreSQL
psql -h localhost -p 30005 -U dvsmart_ms -d dvsmart

# Verificar jobs en ejecución
curl http://localhost:8080/dvsmart_indexing_api/api/monitoring/jobs/running | jq

# Verificar última auditoría
curl http://localhost:8080/dvsmart_indexing_api/api/monitoring/audit/latest | jq
```

### Diagnóstico de Auditoría

```javascript
// Conectar a MongoDB
mongo localhost:30000/dvsmart-ms -u dvsmart_user -p eoQQqfTyMd --authenticationDatabase dvsmart-ms

// Verificar registros de auditoría
db.job_executions_audit.find().sort({ startTime: -1 }).limit(5).pretty()

// Verificar ejecuciones sin finalizar
db.job_executions_audit.find({
  status: "STARTED",
  startTime: { $lt: new Date(Date.now() - 3600000) }  // Más de 1 hora
})

// Verificar ejecuciones fallidas recientes
db.job_executions_audit.find({
  status: "FAILED",
  startTime: { $gte: new Date(Date.now() - 86400000) }  // Últimas 24h
}).sort({ startTime: -1 })
```

---

## 🧪 Mantenimiento y Testing

### Tests Unitarios

```bash
# Ejecutar todos los tests
mvn test

# Ejecutar un test específico
mvn test -Dtest=JobAuditServiceTest

# Tests con cobertura
mvn clean test jacoco:report
```

### Limpieza de Datos

**MongoDB:**

```javascript
// Eliminar auditorías antiguas (más de 90 días)
db.job_executions_audit.deleteMany({
  createdAt: { $lt: new Date(Date.now() - 90*24*60*60*1000) }
})

// Eliminar solo ejecuciones fallidas antiguas
db.job_executions_audit.deleteMany({
  status: "FAILED",
  createdAt: { $lt: new Date(Date.now() - 30*24*60*60*1000) }
})

// Verificar espacio usado
db.job_executions_audit.stats()
```

**PostgreSQL:**

```sql
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

-- Vacuum
VACUUM FULL;
```

### Reinicialización Completa

```bash
# 1. Detener la aplicación
kill {pid}

# 2. Limpiar MongoDB
mongo localhost:30000/dvsmart-ms -u dvsmart_user -p eoQQqfTyMd --authenticationDatabase dvsmart-ms <<EOF
use dvsmart-ms
db.files_index.deleteMany({})
db.job_executions_audit.deleteMany({})
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

## 📚 Referencias

- [Spring Batch Documentation](https://docs.spring.io/spring-batch/docs/current/reference/html/)
- [Spring Integration SFTP](https://docs.spring.io/spring-integration/docs/current/reference/html/sftp.html)
- [Apache Commons Pool2](https://commons.apache.org/proper/commons-pool/)
- [MongoDB Java Driver](https://www.mongodb.com/docs/drivers/java/sync/current/)
- [PostgreSQL JDBC](https://jdbc.postgresql.org/documentation/)

---

## 🤝 Soporte y Contacto

**Equipo de Mantenimiento**: DvSmart Team  
**Contacto**: hahuaranga@indracompany.com  
**Repositorio**: [Enlace interno al repositorio]  
**Documentación Técnica**: [Enlace a documentación detallada]