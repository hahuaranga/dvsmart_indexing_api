
# DVSmart Indexing API

> Microservicio de **indexación masiva de archivos** desde un **SFTP Origen** hacia un **catálogo MongoDB** (`files_index`), implementado con **Spring Boot / Spring Batch**, **Spring Integration SFTP** con **pool perezoso (lazy)** y auditoría de ejecuciones en Mongo. Incluye endpoints REST para **disparo del job**, **auditoría** y **monitorización del pool SFTP y de jobs**. citeturn6search1

---

## Índice

- [Descripción y Stack tecnológico](#descripción-y-stack-tecnologico)
- [Criterios de diseño del servicio (HLD)](#criterios-de-diseño-del-servicio-hld)
  - [Adapters](#adapters)
  - [Application](#application)
  - [Domain](#domain)
  - [Batch](#batch)
  - [Infra](#infra)
  - [Cross-cutting](#cross-cutting)
- [Arquitectura](#arquitectura)
  - [Diagrama de arquitectura](#diagrama-de-arquitectura)
  - [Diagrama de componentes](#diagrama-de-componentes)
  - [Diagrama de secuencia](#diagrama-de-secuencia)
  - [Diagrama de clases](#diagrama-de-clases)
  - [Técnicas de procesamiento](#técnicas-de-procesamiento)
- [Guía Completa de Configuración (application.properties)](#guía-completa-de-configuración-applicationproperties)
- [Despliegue y Escalabilidad](#despliegue-y-escalabilidad)
- [Uso y API (por controller y ruta)](#uso-y-api-por-controller-y-ruta)
- [Monitorización, Logs y Troubleshooting](#monitorización-logs-y-troubleshooting)
- [Riesgos y mitigaciones](#riesgos-y-mitigaciones)
- [Soporte y contacto](#soporte-y-contacto)
- [Referencias](#referencias)

---

## Descripción y Stack tecnologico

**Propósito**: Indexar masivamente archivos ubicados en un SFTP de origen, extrayendo **metadata de archivos** (ruta, nombre, extensión, tamaño, mtime) y persistiendo en **MongoDB** (`files_index`) con control de **estado de indexación** (`PENDING|COMPLETED|FAILED`) y campos de **reorganización** para otros servicios. El servicio ofrece **auditoría de jobs** en `job_executions_audit` y **monitorización** de pool SFTP y jobs. citeturn6search1

**Tecnologías clave**:
- **Java 21**, **Maven**, **Spring Boot 4.0.1** (*starter parent*). citeturn6search1
- **Spring Web**, **Validation**, **Spring Data MongoDB**, **Actuator**. citeturn6search1
- **Spring Batch JDBC** con persistencia en **PostgreSQL** para metadatos. citeturn6search1
- **Spring Integration SFTP** + **pool lazy** con **Apache Commons Pool2** (factory y monitor). citeturn6search1
- **Colecciones Mongo**: `files_index`, `job_executions_audit`. citeturn6search1

---

## Criterios de diseño del servicio (HLD)

### Adapters
- **BatchIndexingController** (`/api/batch/index/full`): inicia el job **BATCH-INDEX-FULL** y retorna `202 Accepted` con `jobExecutionId`. citeturn6search1
- **JobAuditController** (`/api/monitoring/audit/*`): historial por job, por **status**, detalle por **jobExecutionId**, consultas por **rango**, **stats** globales y **últimas ejecuciones**. citeturn6search1
- **MonitoringController** (`/api/monitoring/*`): estadísticas del **SFTP pool** (básicas y extendidas), **health** del pool, acciones de **evict/reset/log**, y consultas de **jobs** (lista, running, latest, history, execution detail, stats, health). citeturn6search1

### Application
- **StartIndexFullUseCase / StartIndexFullService**: orquestan el lanzamiento del job con **JobOperator**, construcción de `JobParameters` (incluye `timestamp` y parámetros de entrada), y validaciones previas. citeturn6search1

### Domain
- **FileMetadataService**: transforma `SftpFileEntry` a `ArchivoMetadata`, genera `idUnico` **SHA-256** y define estado de indexación (`COMPLETED` o `FAILED`). citeturn6search1
- **DirectoryDiscoveryService**: **descubrimiento recursivo** de directorios (BFS) usando una sola **sesión SFTP** por *scan*, devolviendo una **cola concurrente** de rutas. citeturn6search1
- **JobAuditService**: crea y actualiza auditoría (`job_executions_audit`), calcula duración, throughput, y recoge errores/stacktrace; mapea dominio⇄documento. citeturn6search1

### Batch
- **BatchIndexFullConfig**: define el job `BATCH-INDEX-FULL` con un **step** `indexingStep` (*chunk*=`batch.chunk-size`) y **procesamiento asíncrono** (`AsyncItemProcessor` + `AsyncItemWriter`). Usa `@StepScope` en el **reader** para *fresh discovery* por ejecución y aplica **skip/retry** (`skipLimit`, `retryLimit`). Listener de auditoría `JobExecutionAuditListener`. citeturn6search1
- **Reader**: `DirectoryQueueItemReader` con estrategia **Lazy Discovery + Hybrid Streaming** (carga directorio a directorio). citeturn6search1
- **Processor**: `MetadataExtractorProcessor` (filtros, enriquecimiento, **manejo de errores no disruptivo** creando metadata con `indexing_status=FAILED`). citeturn6search1
- **Writer**: `BulkUpsertMongoItemWriter` (**bulk upsert** en modo `UNORDERED`) sobre `MongoTemplate`, setea `reorg_status=PENDING` o `SKIPPED` si falló indexación. citeturn6search1

### Infra
- **SftpSessionFactoryConfig**: `DefaultSftpSessionFactory` + **CustomLazySftpSessionFactory** con **validación**, **eviction** y **pool stats**; `SftpRemoteFileTemplate` para operaciones SFTP; `SftpPoolMonitor` para métricas y *health*. citeturn6search1
- **Mongo**: `JobExecutionAuditRepository` para consultas; documentos `DisorganizedFilesIndexDocument`, `JobExecutionAuditDocument`. citeturn6search1

### Cross-cutting
- **Config**: `BatchConfigProperties`, `SftpConfigProperties` mapean `application.properties`. citeturn6search1
- **Excepciones**: `GlobalExceptionHandler` (incluye `409` para `JobAlreadyRunningException`). citeturn6search1
- **Actuator**: salud y métricas expuestas. citeturn6search1

---

## Arquitectura

### Diagrama de arquitectura

```mermaid
flowchart LR
  subgraph SFTP_Origin[SFTP Origen]
    Dirs[(árbol de directorios)]
  end

  subgraph IndexingService[DVSmart Indexing API]
    API[REST Controllers]
    JOB[Spring Batch\nBATCH-INDEX-FULL]
    S1[Step indexingStep\nReader + Async Proc + Async Writer]
    AUDL[JobExecutionAuditListener]
  end

  subgraph Mongo[MongoDB]
    FI[(files_index)]
    AUD[(job_executions_audit)]
  end

  API --> JOB
  JOB --> S1
  S1 <-- list/read --> SFTP_Origin
  S1 --> FI
  AUDL --> AUD
```
citeturn6search1

### Diagrama de componentes

```mermaid

flowchart TB
  %% Adapters
  subgraph Adapters
    BIC[BatchIndexingController\nRutas: /api/batch/index/*]
    JAC[JobAuditController\nRutas: /api/monitoring/audit/*]
    MON[MonitoringController\nRutas: /api/monitoring/*]
  end

  %% Application
  subgraph Application
    UC[StartIndexFullUseCase]
    SRV[StartIndexFullService\nJobOperator y JobParameters]
  end

  %% Domain
  subgraph Domain
    FMS[FileMetadataService\nGenera idUnico SHA-256]
    DDS[DirectoryDiscoveryService\nDiscovery BFS]
    JAS[JobAuditService\nCrear y actualizar auditoría]
    MODELS[Modelos de dominio\nArchivoMetadata\nSftpFileEntry\nJobExecutionAudit]
  end

  %% Batch
  subgraph Batch
    CFG[BatchIndexFullConfig\nJob BATCH-INDEX-FULL]
    READER[DirectoryQueueItemReader\nLazy discovery + streaming]
    PROC[MetadataExtractorProcessor\nFiltros y manejo de errores]
    AIP[AsyncItemProcessor]
    AIW[AsyncItemWriter]
    WR[BulkUpsertMongoItemWriter\nMongo bulk upsert]
    LST[JobExecutionAuditListener]
  end

  %% Infra
  subgraph Infra
    SF[SftpSessionFactoryConfig\nDefault + Lazy Pool]
    POOL[CustomLazySftpSessionFactory\nApache Commons Pool2]
    TPL[SftpRemoteFileTemplate]
    MONI[SftpPoolMonitor\nEstadísticas y salud]
    AUDREP[JobExecutionAuditRepository]
    MT[MongoTemplate]
  end

  %% Conexiones principales
  BIC --> UC --> SRV --> CFG
  JAC --> AUDREP
  MON --> MONI

  %% Cadena Batch
  CFG --> READER --> PROC --> AIP --> AIW --> WR
  LST --> JAS

  %% Infraestructura
  READER --> TPL
  TPL --> POOL
  SF --> POOL

  WR --> MT
  JAS --> AUDREP

```
citeturn6search1

### Diagrama de secuencia

```mermaid
sequenceDiagram
  autonumber
  participant C as Cliente
  participant API as BatchIndexingController
  participant APP as StartIndexFullService
  participant JOB as Job BATCH-INDEX-FULL
  participant R as DirectoryQueueItemReader
  participant P as MetadataExtractorProcessor
  participant W as BulkUpsertMongoItemWriter
  participant M as MongoDB (files_index)
  participant AUD as JobExecutionAuditListener
  participant ADB as MongoDB (job_executions_audit)
  participant SFTP as SFTP Origen

  C->>API: POST /api/batch/index/full {jobName, parameters}
  API->>APP: execute(jobName, params)
  APP->>JOB: start
  JOB->>AUD: beforeJob()
  loop Lazy discovery + streaming
    R->>SFTP: list directorio
    R-->>JOB: SftpFileEntry
    JOB->>P: process(entry)
    P-->>JOB: ArchivoMetadata (COMPLETED|FAILED)
    JOB->>W: write(chunk)
    W->>M: bulk upsert
  end
  JOB->>AUD: afterJob() update metrics
  AUD->>ADB: save/update audit
```
citeturn6search1

### Diagrama de clases

```mermaid
classDiagram
    %% ═══════════════════════════════════════════════════════════
    %% CAPA DE PRESENTACIÓN (Adapters de Entrada - Driven)
    %% ═══════════════════════════════════════════════════════════
    
    class JobController {
        -JobLauncher jobLauncher
        -JobOperator jobOperator
        -Map~String, Job~ jobs
        +startJob(StartJobRequest) ResponseEntity~JobStatusResponse~
        +getJobStatus(Long jobExecutionId) ResponseEntity~JobStatusResponse~
        +stopJob(Long jobExecutionId) ResponseEntity~StopJobResponse~
        +restartJob(Long jobExecutionId) ResponseEntity~JobStatusResponse~
        +listJobs() ResponseEntity~List~String~~
        +getJobHistory(String jobName) ResponseEntity~List~JobExecutionAudit~~
    }
    
    class HealthController {
        -SftpSessionFactory sessionFactory
        -MongoTemplate mongoTemplate
        -DataSource dataSource
        +health() ResponseEntity~Map~String,Object~~
        +sftpHealth() ResponseEntity~Map~String,Object~~
        +mongoHealth() ResponseEntity~Map~String,Object~~
        +postgresHealth() ResponseEntity~Map~String,Object~~
        +detailedHealth() ResponseEntity~HealthStatus~
    }
    
    %% ═══════════════════════════════════════════════════════════
    %% CAPA DE APLICACIÓN (Puertos de Entrada)
    %% ═══════════════════════════════════════════════════════════
    
    class JobService {
        <<interface>>
        +startJob(String jobName, Map params) JobExecution
        +getJobStatus(Long jobExecutionId) JobExecution
        +stopJob(Long jobExecutionId) void
        +restartJob(Long jobExecutionId) JobExecution
        +getJobHistory(String jobName) List~JobExecution~
    }
    
    class JobServiceImpl {
        -JobLauncher jobLauncher
        -JobOperator jobOperator
        -JobExplorer jobExplorer
        +startJob(String jobName, Map params) JobExecution
        +getJobStatus(Long jobExecutionId) JobExecution
        +stopJob(Long jobExecutionId) void
        +restartJob(Long jobExecutionId) JobExecution
        +getJobHistory(String jobName) List~JobExecution~
    }
    
    %% ═══════════════════════════════════════════════════════════
    %% CAPA DE DOMINIO - CONFIGURACIÓN BATCH
    %% ═══════════════════════════════════════════════════════════
    
    class SftpBatchConfig {
        -JobRepository jobRepository
        -PlatformTransactionManager transactionManager
        -int chunkSize
        -int skipLimit
        -int retryLimit
        +indexingJob() Job
        +reorganizationWithCleanupJob() Job
        +reorganizationOnlyJob() Job
        +cleanupOnlyJob() Job
        +indexingStep() Step
        +reorganizationStep() Step
        +cleanupOriginStep() Step
        +exponentialBackOffPolicy() ExponentialBackOffPolicy
    }
    
    %% ═══════════════════════════════════════════════════════════
    %% CAPA DE DOMINIO - READERS (ItemReader)
    %% ═══════════════════════════════════════════════════════════
    
    class FileItemReader {
        <<ItemReader>>
        -SftpRemoteFileTemplate sftpTemplate
        -String remoteDirectory
        -Pattern filePattern
        -LocalDateTime startDate
        -LocalDateTime endDate
        -Iterator~LsEntry~ fileIterator
        -boolean initialized
        +read() FileMetadata
        -listFiles() List~LsEntry~
        -filterByDate(FileMetadata) boolean
        -filterByPattern(String) boolean
        -toFileMetadata(LsEntry) FileMetadata
        -generateIdUnico(FileMetadata) String
    }
    
    class MongoFileItemReader {
        <<ItemReader>>
        -MongoTemplate mongoTemplate
        -String reorgStatus
        -Boolean deletedFromSource
        -Iterator~FileMetadata~ fileIterator
        +read() FileMetadata
        -queryFiles() List~FileMetadata~
        -buildQuery() Query
    }
    
    %% ═══════════════════════════════════════════════════════════
    %% CAPA DE DOMINIO - PROCESSORS (ItemProcessor)
    %% ═══════════════════════════════════════════════════════════
    
    class FileReorganizationProcessor {
        <<ItemProcessor>>
        -SftpRemoteFileTemplate sftpTemplate
        -ReorganizationStrategy strategy
        -boolean validateChecksum
        -boolean preserveTimestamps
        +process(FileMetadata) FileMetadata
        -calculateDestination(FileMetadata) String
        -moveFile(String source, String dest) void
        -validateChecksum(String source, String dest) boolean
        -calculateMd5(String path) String
        -fileExistsInDestination(String path) boolean
        -createDestinationDirectories(String path) void
    }
    
    class CleanupOriginProcessor {
        <<ItemProcessor>>
        -SftpRemoteFileTemplate sftpTemplate
        -String requiredStatus
        -int minAgeHours
        -boolean validationEnabled
        +process(FileMetadata) FileMetadata
        -validateForCleanup(FileMetadata) boolean
        -deleteSourceFile(String path) boolean
        -isOldEnough(LocalDateTime date) boolean
        -updateDeletionMetadata(FileMetadata) void
    }
    
    class FileValidationProcessor {
        <<ItemProcessor>>
        -SftpRemoteFileTemplate sftpTemplate
        -long maxFileSize
        -List~String~ allowedExtensions
        +process(FileMetadata) FileMetadata
        -validateFileSize(FileMetadata) boolean
        -validateFileType(FileMetadata) boolean
        -validateFileExists(String path) boolean
    }
    
    %% ═══════════════════════════════════════════════════════════
    %% CAPA DE DOMINIO - WRITERS (ItemWriter)
    %% ═══════════════════════════════════════════════════════════
    
    class FileItemWriter {
        <<ItemWriter>>
        -MongoTemplate mongoTemplate
        -String collectionName
        -int batchSize
        +write(Chunk~FileMetadata~) void
        -upsertBatch(List~FileMetadata~) BulkWriteResult
        -buildQuery(FileMetadata) Query
        -buildUpdate(FileMetadata) Update
        -handleDuplicates(FileMetadata) void
        -logWriteResult(BulkWriteResult) void
    }
    
    %% ═══════════════════════════════════════════════════════════
    %% CAPA DE DOMINIO - LISTENERS
    %% ═══════════════════════════════════════════════════════════
    
    class AuditListener {
        <<JobExecutionListener>>
        -JobExecutionAuditRepository repository
        -String serviceName
        -String hostname
        +beforeJob(JobExecution) void
        +afterJob(JobExecution) void
        -createAuditRecord(JobExecution) JobExecutionAudit
        -calculateMetrics(JobExecution) void
        -calculateDuration(JobExecution) long
        -formatDuration(long millis) String
        -extractStepExecutions(JobExecution) List~StepExecutionSummary~
        -calculateThroughput(JobExecution) double
    }
    
    class MetricsListener {
        <<StepExecutionListener>>
        -MeterRegistry meterRegistry
        +beforeStep(StepExecution) void
        +afterStep(StepExecution) ExitStatus
        -recordStepMetrics(StepExecution) void
        -recordFileProcessed(String type, long size) void
        -recordSftpOperation(String op, long duration) void
    }
    
    %% ═══════════════════════════════════════════════════════════
    %% CAPA DE DOMINIO - ESTRATEGIAS
    %% ═══════════════════════════════════════════════════════════
    
    class ReorganizationStrategy {
        <<interface>>
        +calculateDestination(FileMetadata) String
        +isChecksumValidationEnabled() boolean
        +shouldPreserveTimestamps() boolean
    }
    
    class DateBasedStrategy {
        -String datePattern
        -String baseDestination
        +calculateDestination(FileMetadata) String
        +isChecksumValidationEnabled() boolean
        +shouldPreserveTimestamps() boolean
        -formatDate(LocalDateTime) String
    }
    
    class ClientBasedStrategy {
        -String baseDestination
        -Map~String,String~ clientMappings
        +calculateDestination(FileMetadata) String
        +isChecksumValidationEnabled() boolean
        +shouldPreserveTimestamps() boolean
        -extractClientId(FileMetadata) String
    }
    
    class TypeBasedStrategy {
        -String baseDestination
        -Map~String,String~ typeMappings
        +calculateDestination(FileMetadata) String
        +isChecksumValidationEnabled() boolean
        +shouldPreserveTimestamps() boolean
        -extractFileType(FileMetadata) String
    }
    
    %% ═══════════════════════════════════════════════════════════
    %% CAPA DE DOMINIO - MODELOS
    %% ═══════════════════════════════════════════════════════════
    
    class FileMetadata {
        <<Entity>>
        -String id
        -String idUnico
        -String remotePath
        -String fileName
        -Long fileSize
        -LocalDateTime lastModificationDate
        -String fileType
        -LocalDateTime indexedDate
        -LocalDateTime reorganizedDate
        -String reorgStatus
        -String destinationPath
        -Boolean deletedFromSource
        -LocalDateTime sourceDeletionDate
        -String deletedBy
        -String checksum
        -Map~String,String~ customMetadata
        -LocalDateTime createdAt
        -LocalDateTime updatedAt
        +isPending() boolean
        +isCompleted() boolean
        +isFailed() boolean
        +needsCleanup() boolean
    }
    
    class JobExecutionAudit {
        <<Entity>>
        -String auditId
        -Long jobExecutionId
        -String serviceName
        -String jobName
        -LocalDateTime startTime
        -LocalDateTime endTime
        -Long durationMs
        -String durationFormatted
        -String status
        -String exitCode
        -String exitDescription
        -Long totalFilesIndexed
        -Long totalFilesProcessed
        -Long totalFilesSkipped
        -Long totalFilesFailed
        -Long totalFilesDeleted
        -Long totalFilesDeletionFailed
        -Long readCount
        -Long writeCount
        -Long commitCount
        -Long rollbackCount
        -Double filesPerSecond
        -List~StepExecutionSummary~ stepExecutions
        -String errorDescription
        -String errorStackTrace
        -Integer failureCount
        -Map~String,Object~ jobParameters
        -String hostname
        -String instanceId
        -LocalDateTime createdAt
        -LocalDateTime updatedAt
        +isCompleted() boolean
        +isFailed() boolean
        +getTotalFiles() long
    }
    
    class StepExecutionSummary {
        <<ValueObject>>
        -String stepName
        -String status
        -Long readCount
        -Long writeCount
        -Long skipCount
        -String duration
        +isCompleted() boolean
        +isFailed() boolean
    }
    
    %% ═══════════════════════════════════════════════════════════
    %% CAPA DE DOMINIO - DTOs
    %% ═══════════════════════════════════════════════════════════
    
    class StartJobRequest {
        <<DTO>>
        -String jobName
        -Map~String,Object~ jobParameters
        +validate() boolean
    }
    
    class JobStatusResponse {
        <<DTO>>
        -Long jobExecutionId
        -String jobName
        -String status
        -LocalDateTime startTime
        -LocalDateTime endTime
        -String exitCode
        -List~StepExecutionSummary~ stepExecutions
        -Map~String,Object~ metrics
        +fromJobExecution(JobExecution) JobStatusResponse
    }
    
    class StopJobResponse {
        <<DTO>>
        -String message
        -Long jobExecutionId
        -String status
    }
    
    class HealthStatus {
        <<DTO>>
        -String status
        -Map~String,ComponentHealth~ components
        -LocalDateTime timestamp
    }
    
    %% ═══════════════════════════════════════════════════════════
    %% CAPA DE INFRAESTRUCTURA - CONFIGURACIÓN
    %% ═══════════════════════════════════════════════════════════
    
    class SftpSessionFactoryConfig {
        -String host
        -int port
        -String username
        -String password
        -String privateKeyPath
        -int connectionTimeout
        -GenericObjectPoolConfig poolConfig
        +sftpSessionFactory() DefaultSftpSessionFactory
        +sftpTemplate() SftpRemoteFileTemplate
        +createPoolConfig() GenericObjectPoolConfig
        -configureSession(Session) void
    }
    
    class MongoConfig {
        -String uri
        -String database
        -int maxPoolSize
        -int minPoolSize
        +mongoTemplate() MongoTemplate
        +mongoClient() MongoClient
        -createIndexes() void
    }
    
    class BatchDataSourceConfig {
        -String url
        -String username
        -String password
        +dataSource() DataSource
        +transactionManager() PlatformTransactionManager
        +jobRepository() JobRepository
        +jobExplorer() JobExplorer
    }
    
    %% ═══════════════════════════════════════════════════════════
    %% CAPA DE INFRAESTRUCTURA - REPOSITORIES
    %% ═══════════════════════════════════════════════════════════
    
    class FileIndexRepository {
        <<interface>>
        <<MongoRepository>>
        +findByIdUnico(String idUnico) Optional~FileMetadata~
        +findByReorgStatus(String status) List~FileMetadata~
        +findByReorgStatusAndDeletedFromSource(String status, Boolean deleted) List~FileMetadata~
        +findCleanupCandidates() List~FileMetadata~
        +countByReorgStatus(String status) long
        +findByFileType(String fileType) List~FileMetadata~
        +findByIndexedDateBetween(LocalDateTime start, LocalDateTime end) List~FileMetadata~
    }
    
    class JobExecutionAuditRepository {
        <<interface>>
        <<MongoRepository>>
        +findByJobExecutionId(Long id) Optional~JobExecutionAudit~
        +findByJobName(String jobName) List~JobExecutionAudit~
        +findByServiceName(String serviceName) List~JobExecutionAudit~
        +findByStatus(String status) List~JobExecutionAudit~
        +findByJobNameAndStatus(String jobName, String status) List~JobExecutionAudit~
        +findByStartTimeBetween(LocalDateTime start, LocalDateTime end) List~JobExecutionAudit~
        +findTopByJobNameOrderByStartTimeDesc(String jobName) Optional~JobExecutionAudit~
    }
    
    %% ═══════════════════════════════════════════════════════════
    %% CAPA DE INFRAESTRUCTURA - EXCEPCIONES
    %% ═══════════════════════════════════════════════════════════
    
    class GlobalExceptionHandler {
        <<ControllerAdvice>>
        +handleJobNotFoundException(JobNotFoundException) ResponseEntity
        +handleJobExecutionException(JobExecutionException) ResponseEntity
        +handleValidationException(MethodArgumentNotValidException) ResponseEntity
        +handleSftpException(SftpException) ResponseEntity
        +handleMongoException(MongoException) ResponseEntity
        +handleGenericException(Exception) ResponseEntity
        -buildErrorResponse(Exception) ErrorResponse
    }
    
    class SftpConnectionException {
        <<Exception>>
        -String host
        -int port
        +SftpConnectionException(String message, Throwable cause)
    }
    
    class FileProcessingException {
        <<Exception>>
        -String filePath
        -String operation
        +FileProcessingException(String message, Throwable cause)
    }
    
    class ChecksumMismatchException {
        <<Exception>>
        -String sourcePath
        -String destinationPath
        -String sourceChecksum
        -String destChecksum
        +ChecksumMismatchException(String message)
    }
    
    %% ═══════════════════════════════════════════════════════════
    %% RELACIONES - PRESENTACIÓN → APLICACIÓN
    %% ═══════════════════════════════════════════════════════════
    
    JobController --> JobService : usa
    JobController --> StartJobRequest : recibe
    JobController --> JobStatusResponse : retorna
    JobController --> StopJobResponse : retorna
    
    HealthController --> SftpSessionFactoryConfig : usa
    HealthController --> MongoConfig : usa
    HealthController --> HealthStatus : retorna
    
    JobServiceImpl ..|> JobService : implementa
    
    %% ═══════════════════════════════════════════════════════════
    %% RELACIONES - APLICACIÓN → DOMINIO
    %% ═══════════════════════════════════════════════════════════
    
    JobServiceImpl --> SftpBatchConfig : usa
    
    SftpBatchConfig --> FileItemReader : crea
    SftpBatchConfig --> MongoFileItemReader : crea
    SftpBatchConfig --> FileReorganizationProcessor : crea
    SftpBatchConfig --> CleanupOriginProcessor : crea
    SftpBatchConfig --> FileValidationProcessor : crea
    SftpBatchConfig --> FileItemWriter : crea
    SftpBatchConfig --> AuditListener : registra
    SftpBatchConfig --> MetricsListener : registra
    
    %% ═══════════════════════════════════════════════════════════
    %% RELACIONES - DOMINIO (READERS)
    %% ═══════════════════════════════════════════════════════════
    
    FileItemReader --> FileMetadata : produce
    FileItemReader --> SftpSessionFactoryConfig : usa
    
    MongoFileItemReader --> FileMetadata : produce
    MongoFileItemReader --> FileIndexRepository : usa
    
    %% ═══════════════════════════════════════════════════════════
    %% RELACIONES - DOMINIO (PROCESSORS)
    %% ═══════════════════════════════════════════════════════════
    
    FileReorganizationProcessor --> FileMetadata : transforma
    FileReorganizationProcessor --> ReorganizationStrategy : usa
    FileReorganizationProcessor --> SftpSessionFactoryConfig : usa
    
    CleanupOriginProcessor --> FileMetadata : transforma
    CleanupOriginProcessor --> SftpSessionFactoryConfig : usa
    
    FileValidationProcessor --> FileMetadata : valida
    FileValidationProcessor --> SftpSessionFactoryConfig : usa
    
    %% ═══════════════════════════════════════════════════════════
    %% RELACIONES - DOMINIO (STRATEGIES)
    %% ═══════════════════════════════════════════════════════════
    
    DateBasedStrategy ..|> ReorganizationStrategy : implementa
    ClientBasedStrategy ..|> ReorganizationStrategy : implementa
    TypeBasedStrategy ..|> ReorganizationStrategy : implementa
    
    %% ═══════════════════════════════════════════════════════════
    %% RELACIONES - DOMINIO (WRITERS)
    %% ═══════════════════════════════════════════════════════════
    
    FileItemWriter --> FileMetadata : persiste
    FileItemWriter --> FileIndexRepository : usa
    
    %% ═══════════════════════════════════════════════════════════
    %% RELACIONES - DOMINIO (LISTENERS)
    %% ═══════════════════════════════════════════════════════════
    
    AuditListener --> JobExecutionAudit : crea
    AuditListener --> StepExecutionSummary : crea
    AuditListener --> JobExecutionAuditRepository : usa
    
    MetricsListener --> FileMetadata : observa
    
    %% ═══════════════════════════════════════════════════════════
    %% RELACIONES - DOMINIO (MODELOS)
    %% ═══════════════════════════════════════════════════════════
    
    JobExecutionAudit *-- StepExecutionSummary : contiene
    
    %% ═══════════════════════════════════════════════════════════
    %% RELACIONES - INFRAESTRUCTURA
    %% ═══════════════════════════════════════════════════════════
    
    FileIndexRepository --> FileMetadata : gestiona
    JobExecutionAuditRepository --> JobExecutionAudit : gestiona
    
    GlobalExceptionHandler --> SftpConnectionException : maneja
    GlobalExceptionHandler --> FileProcessingException : maneja
    GlobalExceptionHandler --> ChecksumMismatchException : maneja
    
    %% ═══════════════════════════════════════════════════════════
    %% ESTILOS VISUALES POR CAPA
    %% ═══════════════════════════════════════════════════════════
    
    style JobController fill:#4CAF50,stroke:#2E7D32,color:#fff
    style HealthController fill:#4CAF50,stroke:#2E7D32,color:#fff
    
    style JobService fill:#9C27B0,stroke:#6A1B9A,color:#fff
    style JobServiceImpl fill:#9C27B0,stroke:#6A1B9A,color:#fff
    
    style SftpBatchConfig fill:#2196F3,stroke:#1565C0,color:#fff
    
    style FileItemReader fill:#2196F3,stroke:#1565C0,color:#fff
    style MongoFileItemReader fill:#2196F3,stroke:#1565C0,color:#fff
    style FileReorganizationProcessor fill:#2196F3,stroke:#1565C0,color:#fff
    style CleanupOriginProcessor fill:#2196F3,stroke:#1565C0,color:#fff
    style FileValidationProcessor fill:#2196F3,stroke:#1565C0,color:#fff
    style FileItemWriter fill:#2196F3,stroke:#1565C0,color:#fff
    style AuditListener fill:#2196F3,stroke:#1565C0,color:#fff
    style MetricsListener fill:#2196F3,stroke:#1565C0,color:#fff
    
    style ReorganizationStrategy fill:#00BCD4,stroke:#0097A7,color:#fff
    style DateBasedStrategy fill:#00BCD4,stroke:#0097A7,color:#fff
    style ClientBasedStrategy fill:#00BCD4,stroke:#0097A7,color:#fff
    style TypeBasedStrategy fill:#00BCD4,stroke:#0097A7,color:#fff
    
    style FileMetadata fill:#00BCD4,stroke:#0097A7,color:#fff
    style JobExecutionAudit fill:#00BCD4,stroke:#0097A7,color:#fff
    style StepExecutionSummary fill:#00BCD4,stroke:#0097A7,color:#fff
    
    style StartJobRequest fill:#FFC107,stroke:#F57F17,color:#000
    style JobStatusResponse fill:#FFC107,stroke:#F57F17,color:#000
    style StopJobResponse fill:#FFC107,stroke:#F57F17,color:#000
    style HealthStatus fill:#FFC107,stroke:#F57F17,color:#000
    
    style SftpSessionFactoryConfig fill:#FF9800,stroke:#E65100,color:#fff
    style MongoConfig fill:#4DB33D,stroke:#2E7D32,color:#fff
    style BatchDataSourceConfig fill:#336791,stroke:#1A237E,color:#fff
    
    style FileIndexRepository fill:#4DB33D,stroke:#2E7D32,color:#fff
    style JobExecutionAuditRepository fill:#4DB33D,stroke:#2E7D32,color:#fff
    
    style GlobalExceptionHandler fill:#F44336,stroke:#C62828,color:#fff
    style SftpConnectionException fill:#F44336,stroke:#C62828,color:#fff
    style FileProcessingException fill:#F44336,stroke:#C62828,color:#fff
    style ChecksumMismatchException fill:#F44336,stroke:#C62828,color:#fff
```
citeturn6search1

### Técnicas de procesamiento

- **Lazy Discovery + Hybrid Streaming**: primer `read()` ejecuta un **discovery completo** de directorios con **una sola sesión**; luego se procesan directorios **uno a uno**, manteniendo en memoria solo el lote de entradas del directorio actual. citeturn6search1
- **Procesamiento por chunks + async**: `AsyncItemProcessor` y `AsyncItemWriter` con `ThreadPoolTaskExecutor` configurable (`batch.thread-pool-size`, `batch.queue-capacity`). citeturn6search1
- **Extracción de metadata resiliente**: ante errores, el processor **no interrumpe**; devuelve `ArchivoMetadata` con `indexing_status=FAILED` y `indexing_errorDescription` truncado. citeturn6search1
- **Bulk upsert en Mongo**: `BulkOperations.UNORDERED` sobre `MongoTemplate`, actualizando `indexing_*` y seteando `reorg_status` inicial según resultado de indexación. citeturn6search1
- **Pool SFTP perezoso**: `CustomLazySftpSessionFactory` con **validación pre-uso**, **eviction** y **stats** (health, utilization), expuesto vía `SftpPoolMonitor` y endpoints REST. citeturn6search1
- **Auditoría integral**: listener captura tiempos, contadores (`read/write/commit/rollback`), `filesPerSecond`, errores y parámetros; guarda en `job_executions_audit`. citeturn6search1

---

## Guía Completa de Configuración (application.properties)

```properties
# Core
spring.application.name=dvsmart-indexing-api
server.servlet.context-path=/dvsmart_indexing_api
server.port=8080
server.shutdown=graceful

# MongoDB
aub.spring.mongodb.uri=mongodb://dvsmart_user:***@localhost:30000/dvsmart-ms?authSource=dvsmart-ms

# PostgreSQL (Spring Batch)
spring.datasource.url=jdbc:postgresql://localhost:30005/dvsmart
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.username=dvsmart_ms
spring.datasource.password=***
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5

# Spring Batch
spring.batch.job.enabled=false
spring.batch.jdbc.initialize-schema=always

# Batch (custom)
batch.chunk-size=100
batch.thread-pool-size=20
batch.queue-capacity=1000
batch.skip-limit=5
batch.retry-limit=3

# SFTP Origen
sftp.origin.host=localhost
sftp.origin.port=30002
sftp.origin.user=sftpsourceuser
sftp.origin.password=securepass
sftp.origin.base-dir=/disorganized_data
sftp.origin.timeout=30000

# SFTP Pool (lazy + health)
sftp.origin.pool.lazy-init=true
sftp.origin.pool.initial-size=0
sftp.origin.pool.max-size=10
sftp.origin.pool.max-wait-millis=30000
sftp.origin.pool.test-on-borrow=true
sftp.origin.pool.time-between-eviction-runs-millis=60000
sftp.origin.pool.min-evictable-idle-time-millis=300000

# Logging
logging.level.root=INFO
logging.level.com.indra.minsait.dvsmart.indexing=DEBUG
logging.level.org.springframework.batch=INFO
logging.level.org.springframework.integration.sftp=DEBUG
logging.level.org.springframework.data.mongodb=INFO
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} - %logger{36} - %msg%n

# Actuator
management.endpoints.web.exposure.include=health,info,metrics,batch
management.endpoint.health.show-details=always
management.metrics.enable.jvm=true
management.metrics.enable.process=true
management.metrics.enable.system=true
```
> **Notas**: El contexto es `/dvsmart_indexing_api`. Las credenciales y URIs se muestran en el `application.properties` del proyecto; externalízalas en `Secret/ConfigMap` para producción. citeturn6search1

---

## Despliegue y Escalabilidad

### Producción (contenedor / K8s)
- **Variables y secretos**: externaliza `spring.mongodb.uri`, `spring.datasource.*`, `sftp.*` en `Secret`/`ConfigMap`. citeturn6search1
- **Readiness/Liveness**: usa `/dvsmart_indexing_api/actuator/health` para probes. citeturn6search1
- **Pool SFTP**: dimensiona `max-size` y `max-wait-millis` según latencia y concurrencia esperada. citeturn6search1

### Alto rendimiento
- **Chunking y paralelismo**: ajusta `batch.chunk-size` y `batch.thread-pool-size` tras pruebas de saturación; evita colas excesivas (`batch.queue-capacity`). citeturn6search1
- **MongoDB**: verifica **índices** en `files_index` y `job_executions_audit` para las consultas críticas. citeturn6search1
- **Logs**: en producción reduce a `INFO` para minimizar I/O. citeturn6search1

### Build & Run
```bash
mvn -q -DskipTests package
java -jar target/dvsmart_indexing_api-1.0.0-SNAPSHOT.jar --spring.profiles.active=default
```
citeturn6search1

---

## Uso y API (por controller y ruta)

> **Context path**: `/dvsmart_indexing_api`

### BatchIndexingController

**POST** `/api/batch/index/full`
- **Body**
```json
{
  "jobName": "BATCH-INDEX-FULL",
  "parameters": { "runLabel": "manual-YYYY-MM-DD" }
}
```
- **curl**
```bash
curl -s -X POST \
  http://localhost:8080/dvsmart_indexing_api/api/batch/index/full \
  -H 'Content-Type: application/json' \
  -d '{"jobName":"BATCH-INDEX-FULL","parameters":{"runLabel":"manual-2025-12-29"}}'
```
- **Respuesta (202)**
```json
{ "message": "Batch job started successfully", "jobExecutionId": 12345, "status": "ACCEPTED" }
```
citeturn6search1

### JobAuditController

**GET** `/api/monitoring/audit/jobs/{jobName}`
```bash
curl -s http://localhost:8080/dvsmart_indexing_api/api/monitoring/audit/jobs/BATCH-INDEX-FULL | jq
```
**GET** `/api/monitoring/audit/status/{status}`
```bash
curl -s http://localhost:8080/dvsmart_indexing_api/api/monitoring/audit/status/COMPLETED | jq
```
**GET** `/api/monitoring/audit/execution/{jobExecutionId}`
```bash
curl -s http://localhost:8080/dvsmart_indexing_api/api/monitoring/audit/execution/12345 | jq
```
**GET** `/api/monitoring/audit/range?start=ISO&end=ISO`
```bash
curl -s "http://localhost:8080/dvsmart_indexing_api/api/monitoring/audit/range?start=2025-12-01T00:00:00Z&end=2025-12-31T23:59:59Z" | jq
```
**GET** `/api/monitoring/audit/stats`
```bash
curl -s http://localhost:8080/dvsmart_indexing_api/api/monitoring/audit/stats | jq
```
**GET** `/api/monitoring/audit/latest`
```bash
curl -s http://localhost:8080/dvsmart_indexing_api/api/monitoring/audit/latest | jq
```
citeturn6search1

### MonitoringController – SFTP Pool

**GET** `/api/monitoring/sftp-pool`
```bash
curl -s http://localhost:8080/dvsmart_indexing_api/api/monitoring/sftp-pool | jq
```
**GET** `/api/monitoring/sftp-pool/extended`
```bash
curl -s http://localhost:8080/dvsmart_indexing_api/api/monitoring/sftp-pool/extended | jq
```
**GET** `/api/monitoring/sftp-pool/health`
```bash
curl -s http://localhost:8080/dvsmart_indexing_api/api/monitoring/sftp-pool/health | jq
```
**POST** `/api/monitoring/sftp-pool/evict`
```bash
curl -s -X POST http://localhost:8080/dvsmart_indexing_api/api/monitoring/sftp-pool/evict | jq
```
**POST** `/api/monitoring/sftp-pool/reset`
```bash
curl -s -X POST http://localhost:8080/dvsmart_indexing_api/api/monitoring/sftp-pool/reset | jq
```
**POST** `/api/monitoring/sftp-pool/log`
```bash
curl -s -X POST http://localhost:8080/dvsmart_indexing_api/api/monitoring/sftp-pool/log | jq
```
citeturn6search1

### MonitoringController – Jobs de Spring Batch

**GET** `/api/monitoring/jobs`
```bash
curl -s http://localhost:8080/dvsmart_indexing_api/api/monitoring/jobs | jq
```
**GET** `/api/monitoring/jobs/running`
```bash
curl -s http://localhost:8080/dvsmart_indexing_api/api/monitoring/jobs/running | jq
```
**GET** `/api/monitoring/jobs/latest`
```bash
curl -s http://localhost:8080/dvsmart_indexing_api/api/monitoring/jobs/latest | jq
```
**GET** `/api/monitoring/jobs/{jobName}`
```bash
curl -s 'http://localhost:8080/dvsmart_indexing_api/api/monitoring/jobs/BATCH-INDEX-FULL?page=0&size=10' | jq
```
**GET** `/api/monitoring/jobs/execution/{id}`
```bash
curl -s http://localhost:8080/dvsmart_indexing_api/api/monitoring/jobs/execution/12345 | jq
```
**GET** `/api/monitoring/jobs/stats`
```bash
curl -s http://localhost:8080/dvsmart_indexing_api/api/monitoring/jobs/stats | jq
```
**GET** `/api/monitoring/health`
```bash
curl -s http://localhost:8080/dvsmart_indexing_api/api/monitoring/health | jq
```
citeturn6search1

### Actuator

**GET** `/actuator/health`
```bash
curl -s http://localhost:8080/dvsmart_indexing_api/actuator/health | jq
```
**GET** `/actuator/info`, `/actuator/metrics`, `/actuator/batch`
```bash
curl -s http://localhost:8080/dvsmart_indexing_api/actuator/info | jq
curl -s http://localhost:8080/dvsmart_indexing_api/actuator/metrics | jq
```
citeturn6search1

---

## Monitorización, Logs y Troubleshooting

### Monitorización
- **Salud**: `/actuator/health` con detalles habilitados. citeturn6search1
- **Pool SFTP**: endpoints de `MonitoringController` para **stats**, **health** y **acciones** de mantenimiento. citeturn6search1
- **Auditoría**: `JobAuditController` para seguimiento de ejecuciones y métricas agregadas. citeturn6search1

### Logs
- **Paquetes**: `com.indra.minsait.dvsmart.indexing=DEBUG` en pruebas; `INFO` en prod. citeturn6search1
- **SFTP/Mongo/Batch**: niveles ajustables en `application.properties`. citeturn6search1

### Troubleshooting

| Síntoma | Posible causa | Verificación | Acción |
|---|---|---|---|
| `409 CONFLICT` al iniciar el job | Ya existe una ejecución en curso | Ver `GET /api/monitoring/jobs/running` | Esperar finalización o detener; manejado por `GlobalExceptionHandler` |
| `SocketTimeoutException` o pool saturado | Latencia o `max-size` insuficiente | `GET /api/monitoring/sftp-pool/extended` | Aumentar `max-size`, `max-wait-millis`, revisar red |
| Lectura muy lenta | Directorios enormes sin índices | Logs del reader y del discovery | Aumentar `chunk-size` y `thread-pool-size`; revisar latencia SFTP |
| Altas tasas de `FAILED` en indexación | Archivos corruptos/0 bytes | `GET /api/monitoring/audit/stats` y logs del processor | Afinar filtros en `MetadataExtractorProcessor`; ignora temporales/ocultos |
| Bulk upsert falla | Esquema/índices incompatibles | Logs del writer; revisar validadores | Validar schema y claves; dividir chunk o revisar tamaño batch |
citeturn6search1

---

## Riesgos y mitigaciones

- **Inestabilidad de SFTP**: *Mitigación*: `retry-limit`, `max-wait-millis` mayores, **validación pre-uso** en el pool y **eviction** de conexiones inactivas. citeturn6search1
- **Contención del pool**: *Mitigación*: dimensionar `max-size` y limitar `thread-pool-size`; usar *monitor* y alertas de utilización. citeturn6search1
- **Sobrecarga de Mongo**: *Mitigación*: **bulk unordered**, tamaños de chunk moderados, índices adecuados en `files_index`. citeturn6search1
- **Errores de data sucia**: *Mitigación*: filtros y tolerancia a fallos en processor (retorna `FAILED` sin romper el flujo). citeturn6search1
- **Pérdida de trazabilidad**: *Mitigación*: auditoría por job con métricas, parámetros y errores persistidos. citeturn6search1

---

## Soporte y contacto

- Responsable técnico: **hahuaranga@indracompany.com**. citeturn6search1

---

## Referencias

- Código fuente: controllers, batch config, reader/processor/writer, servicios de dominio, configuración SFTP y repositorios Mongo. citeturn6search1
- `application.properties` y scripts de inicialización de Mongo con **validadores** e **índices** para `files_index` y `job_executions_audit`. citeturn6search1

