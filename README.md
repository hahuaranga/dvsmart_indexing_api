# dvsmart_indexing_api

## 📋 Descripción

**dvsmart_indexing_api** es una aplicación Spring Boot diseñada para indexar masivamente archivos desorganizados desde servidores SFTP hacia MongoDB. Utiliza Spring Batch para procesamiento paralelo y asíncrono, optimizado para manejar millones de archivos de manera eficiente.

### Características principales
- **Indexación completa**: Escaneo recursivo de directorios SFTP
- **Procesamiento masivo**: Optimizado para 11M+ archivos (~30-60 minutos con bulk operations)
- **Arquitectura hexagonal**: Separación clara de responsabilidades
- **Monitoreo**: Integración con Spring Boot Actuator
- **Configuración externa**: Propiedades configurables por entorno
- **Licencia automática**: Plugin Maven para headers de copyright

## 🏗️ Arquitectura

```
┌─────────────────────────────────────────────────────────────┐
│                     BatchIndexingController                  │
│                     (REST API /api/batch/index/full)        │
└───────────────────────┬─────────────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────────────┐
│                 StartIndexFullService                        │
│                 (Application Service)                        │
└───────────────────────┬─────────────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────────────┐
│                 BatchIndexFullConfig                         │
│                 (Spring Batch Job Configuration)             │
└──────────────┬──────────────┬──────────────┬────────────────┘
               │              │              │
    ┌──────────▼─────┐  ┌────▼─────────┐  ┌─▼────────────────┐
    │DirectoryQueue  │  │MetadataExtr. │  │BulkUpsertMongo  │
    │ItemReader      │  │Processor     │  │ItemWriter       │
    └────────────────┘  └──────────────┘  └─────────────────┘
               │              │              │
    ┌──────────▼─────┐  ┌────▼─────────┐  ┌─▼────────────────┐
    │SftpFileEntry   │  │ArchivoMetadata│ │MongoDB Document  │
    │(Model)         │  │(Domain Model) │ │(Entity)          │
    └────────────────┘  └──────────────┘  └─────────────────┘
```

## 📁 Estructura del Proyecto

```
dvsmart_indexing_api/
├── src/main/java/com/indra/minsait/dvsmart/indexing/
│   ├── adapter/
│   │   ├── in/web/                          # Controladores REST
│   │   │   └── BatchIndexingController.java
│   │   ├── out/batch/                       # Adaptadores de Spring Batch
│   │   │   ├── config/BatchIndexFullConfig.java
│   │   │   ├── processor/MetadataExtractorProcessor.java
│   │   │   ├── reader/DirectoryQueueItemReader.java
│   │   │   └── writer/BulkUpsertMongoItemWriter.java
│   │   └── out/persistence/mongodb/         # Persistencia MongoDB
│   │       └── entity/DisorganizedFilesIndexDocument.java
│   ├── application/                         # Capa de aplicación
│   │   ├── port/in/StartIndexFullUseCase.java
│   │   └── service/StartIndexFullService.java
│   ├── domain/                             # Dominio y lógica de negocio
│   │   ├── model/
│   │   │   ├── ArchivoMetadata.java
│   │   │   └── SftpFileEntry.java
│   │   └── service/
│   │       ├── DirectoryDiscoveryService.java
│   │       └── FileMetadataService.java
│   └── infrastructure/                      # Configuración e infraestructura
│       ├── config/
│       │   ├── BatchConfigProperties.java
│       │   └── SftpConfigProperties.java
│       └── ServiceApplication.java          # Punto de entrada
├── src/main/resources/
│   ├── application.properties               # Configuración principal
│   └── license-header.txt                   # Header para plugin de licencia
└── pom.xml                                  # Configuración Maven
```

## ⚙️ Configuración

### Requisitos previos
- **Java 21** o superior
- **Maven 3.6+**
- **MongoDB 4.4+** (local o remoto)
- **Servidor SFTP** accesible

### Propiedades de configuración (`application.properties`)

```properties
# ============================================================================
# CONFIGURACIÓN DE LA APLICACIÓN
# ============================================================================
spring.application.name=dvsmart-reorganization-api
server.port=8080

# ============================================================================
# CONFIGURACIÓN MONGODB
# ============================================================================
spring.mongodb.uri=mongodb://localhost:27017/dvsmart_reorganization

# ============================================================================
# CONFIGURACIÓN SPRING BATCH
# ============================================================================
spring.batch.job.enabled=false                     # Deshabilitar auto-inicio

# Propiedades personalizadas del batch
batch.chunk-size=100                              # Tamaño del chunk
batch.thread-pool-size=20                         # Threads para procesamiento paralelo
batch.queue-capacity=1000                         # Capacidad de la cola

# ============================================================================
# CONFIGURACIÓN SFTP ORIGEN
# ============================================================================
sftp.origin.host=sftp-origin.example.com
sftp.origin.port=22
sftp.origin.user=origin_user
sftp.origin.password=origin_password
sftp.origin.base-dir=/data/legacy/files          # Directorio raíz a indexar
sftp.origin.timeout=30000                         # Timeout en milisegundos
sftp.origin.pool.size=10                          # Pool de conexiones SFTP

# ============================================================================
# CONFIGURACIÓN DE LOGS
# ============================================================================
logging.level.com.indra.minsait.dvsmart.indexing=DEBUG
logging.file.name=logs/reorganization.log
```

### Perfiles Maven
- **dev** (activo por defecto): `mvn spring-boot:run -Pdev`
- **prod**: `mvn spring-boot:run -Pprod`

## 🚀 Compilación y Ejecución

### 1. Compilar el proyecto
```bash
mvn clean package
```

### 2. Ejecutar la aplicación
```bash
# Modo desarrollo (perfil dev por defecto)
mvn spring-boot:run

# Modo producción
mvn spring-boot:run -Pprod

# Ejecutar el JAR generado
java -jar target/dvsmart_indexing_api.jar --spring.profiles.active=prod
```

### 3. Verificar que la aplicación está corriendo
```bash
curl http://localhost:8080/actuator/health
```
Respuesta esperada:
```json
{
  "status": "UP"
}
```

## 📊 Endpoints de la API

### Iniciar indexación completa
```http
POST /api/batch/index/full
Content-Type: application/json

Respuesta exitosa (202 Accepted):
{
  "message": "Batch job started successfully",
  "jobExecutionId": 1,
  "status": "ACCEPTED"
}
```

### Monitoreo con Actuator
- **Health check**: `GET /actuator/health`
- **Información**: `GET /actuator/info`
- **Métricas**: `GET /actuator/metrics`
- **Jobs de Batch**: `GET /actuator/batch`

## 🔧 Mantenimiento

### Estructura de la base de datos MongoDB
**Colección**: `disorganized-files-index`

```json
{
  "_id": "ObjectId",
  "idUnico": "sha256_hash_del_path",
  "rutaOrigen": "/data/legacy/files/subdir/document.pdf",
  "nombre": "document.pdf",
  "mtime": "2023-12-16T10:30:00Z",
  "tamanio": 2048576,
  "extension": "pdf",
  "indexadoEn": "2023-12-17T14:25:30Z"
}
```

**Índices creados automáticamente**:
- `idUnico` (único): Para upserts eficientes

### Logs y Monitoreo
- **Archivo de log**: `logs/reorganization.log` (rotación automática)
- **Niveles de log configurables**: DEBUG, INFO, WARN, ERROR
- **Métricas de Spring Batch**: Disponibles en `/actuator/metrics`

### Optimización del rendimiento

| Parámetro | Valor recomendado | Explicación |
|-----------|-------------------|-------------|
| `batch.thread-pool-size` | 20-50 | Depende de los cores del servidor |
| `batch.chunk-size` | 100-500 | Balance entre memoria y rendimiento |
| `sftp.origin.pool.size` | 10-20 | Conexiones SFTP simultáneas |
| `batch.queue-capacity` | 1000-5000 | Buffer para picos de procesamiento |

### Troubleshooting

#### Problema: Conexión SFTP falla
**Síntomas**: 
- `Connection refused` o `Timeout`
- Errores en `DirectoryDiscoveryService`

**Solución**:
1. Verificar credenciales en `application.properties`
2. Confirmar que el servidor SFTP está accesible
3. Aumentar `sftp.origin.timeout` si es necesario
4. Verificar reglas de firewall

#### Problema: Rendimiento lento
**Síntomas**:
- Procesamiento < 1000 archivos/segundo
- Alta CPU o memoria

**Solución**:
1. Aumentar `batch.thread-pool-size`
2. Verificar conexión a MongoDB (latencia)
3. Monitorear logs para cuellos de botella
4. Considerar particionar el trabajo si hay > 20M archivos

#### Problema: MongoDB sobrecargado
**Síntomas**:
- Timeouts en operaciones bulk
- Alta carga en cluster MongoDB

**Solución**:
1. Reducir `batch.chunk-size`
2. Implementar rate limiting en el writer
3. Considerar sharding en MongoDB para colecciones grandes

## 🧪 Pruebas

### Pruebas unitarias
```bash
# Ejecutar todas las pruebas
mvn test

# Ejecutar pruebas con cobertura (requiere plugin JaCoCo)
mvn test jacoco:report
```

### Pruebas de integración
1. **Configurar entorno de prueba**:
   - MongoDB local en puerto 27017
   - Servidor SFTP de prueba (puede usar `testcontainers`)

2. **Ejecutar indexación de prueba**:
```bash
# Usar un subconjunto de datos para pruebas
# Modificar sftp.origin.base-dir a un directorio de prueba pequeño
```

### Pruebas de carga
Para simular indexación masiva:
1. Crear estructura de prueba con scripts
2. Monitorizar métricas durante la ejecución
3. Validar que todos los archivos se indexan correctamente

## 🔄 Despliegue

### Entorno de desarrollo
```bash
# Usar H2 en memoria para pruebas rápidas
# Configurar SFTP local (vsftpd o similar)
```

### Entorno de producción
**Requisitos mínimos**:
- 4+ cores CPU
- 8GB+ RAM
- 50GB+ disco (dependiendo del tamaño de los archivos)
- Conexión estable a MongoDB cluster
- Acceso al servidor SFTP origen

**Pasos de despliegue**:
1. Configurar variables de entorno o `application-prod.properties`
2. Asegurar permisos de escritura en `logs/`
3. Configurar monitoreo (Prometheus, Grafana)
4. Establecer políticas de retención de logs
5. Configurar backup de MongoDB

## 📝 Licencia y Copyright

El proyecto incluye automáticamente headers de copyright usando el plugin `license-maven-plugin`. Todos los archivos `.java` tendrán el header especificado en `src/main/resources/license-header.txt`.

Para actualizar los headers:
```bash
mvn license:format
```

## 🐛 Reporte de Issues

Cuando encuentre un problema:
1. Revisar logs en `logs/reorganization.log`
2. Verificar configuración de SFTP y MongoDB
3. Proporcionar:
   - Versión de la aplicación
   - Entorno (dev/prod)
   - Stack trace completo
   - Pasos para reproducir

## 🔮 Roadmap y Mejoras Futuras

1. **Indexación incremental**: Solo archivos modificados desde última ejecución
2. **Dashboard web**: Para monitoreo en tiempo real
3. **Configuración multi-SFTP**: Múltiples orígenes simultáneos
4. **Exportación a otros formatos**: CSV, JSON, Elasticsearch
5. **Validación de integridad**: Checksum de archivos indexados
6. **Métricas avanzadas**: Tiempo estimado de finalización, progreso por directorio

## 📚 Recursos Adicionales

- [Documentación Spring Batch](https://docs.spring.io/spring-batch/reference/)
- [Spring Integration SFTP](https://docs.spring.io/spring-integration/reference/sftp.html)
- [MongoDB Spring Data](https://docs.spring.io/spring-data/mongodb/docs/current/reference/html/)
- [SSHJ Library](https://github.com/hierynomus/sshj)

## 🤝 Contribución

1. Fork el repositorio
2. Crear rama para la feature (`git checkout -b feature/AmazingFeature`)
3. Commit cambios (`git commit -m 'Add some AmazingFeature'`)
4. Push a la rama (`git push origin feature/AmazingFeature`)
5. Abrir Pull Request

---

**Mantenido por**: Equipo de Desarrollo DvSmart - Indra Sistemas  
**Contacto**: hahuaranga@indracompany.com  
**Última actualización**: Diciembre 2025
