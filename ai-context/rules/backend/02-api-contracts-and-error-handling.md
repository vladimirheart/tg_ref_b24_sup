# API и обработка ошибок: Правила проекта Iguana

## Правило 1: API контракты с DTO слоем

### Структура папок:

```
src/main/java/com/example/panel/model/
├── request/              # Входящие данные от клиента
│   ├── CreateDialogRequest.java
│   ├── UpdateDialogRequest.java
│   ├── CreateTaskRequest.java
│   └── ...
├── response/             # Исходящие данные клиенту
│   ├── DialogResponse.java
│   ├── DialogListItemResponse.java
│   ├── TaskResponse.java
│   └── ...
└── mapper/               # Конвертеры (Entity <-> DTO)
    ├── DialogMapper.java
    ├── TaskMapper.java
    └── ...
```

### ✅ Правильный пример Request DTO:

```java
package com.example.panel.model.request;

import jakarta.validation.constraints.*;

/**
 * Request для создания диалога
 */
public record CreateDialogRequest(
    @NotBlank(message = "Text is required")
    @Size(min = 1, max = 5000, message = "Text must be between 1 and 5000 characters")
    String text,
    
    @NotBlank(message = "Client ID is required")
    String clientId,
    
    @Email(message = "Invalid email format")
    @NotBlank
    String clientEmail,
    
    @Positive(message = "Priority must be positive")
    @Min(value = 1, message = "Priority minimum is 1")
    @Max(value = 5, message = "Priority maximum is 5")
    Integer priority
) {}
```

### ✅ Правильный пример Response DTO:

```java
package com.example.panel.model.response;

import java.time.LocalDateTime;

public record DialogResponse(
    String id,
    String text,
    String clientId,
    String clientEmail,
    DialogStatus status,
    Integer priority,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public enum DialogStatus {
        OPEN, IN_PROGRESS, CLOSED
    }
}
```

### ✅ Правильный интерфейс Mapper:

```java
package com.example.panel.model.mapper;

import com.example.panel.entity.Dialog;
import com.example.panel.model.request.CreateDialogRequest;
import com.example.panel.model.response.DialogResponse;
import org.springframework.stereotype.Component;

@Component
public class DialogMapper {
    
    /**
     * Конвертировать Entity в Response DTO
     */
    public DialogResponse toResponse(Dialog entity) {
        if (entity == null) return null;
        
        return new DialogResponse(
            entity.getId(),
            entity.getText(),
            entity.getClientId(),
            entity.getClientEmail(),
            DialogResponse.DialogStatus.valueOf(entity.getStatus()),
            entity.getPriority(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
    
    /**
     * Конвертировать Request DTO в Entity
     */
    public Dialog toEntity(CreateDialogRequest request) {
        if (request == null) return null;
        
        Dialog dialog = new Dialog();
        dialog.setText(request.text());
        dialog.setClientId(request.clientId());
        dialog.setClientEmail(request.clientEmail());
        dialog.setPriority(request.priority());
        dialog.setStatus("OPEN");
        return dialog;
    }
}
```

### ✅ Правильный контроллер:

```java
package com.example.panel.controller;

import com.example.panel.model.request.CreateDialogRequest;
import com.example.panel.model.response.DialogResponse;
import com.example.panel.service.IDialogService;
import com.example.panel.model.mapper.DialogMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/dialogs")
@Validated
public class DialogApiController {
    private final IDialogService dialogService;
    private final DialogMapper mapper;
    
    public DialogApiController(IDialogService dialogService, DialogMapper mapper) {
        this.dialogService = dialogService;
        this.mapper = mapper;
    }
    
    @PostMapping
    public ResponseEntity<DialogResponse> create(@Valid @RequestBody CreateDialogRequest request) {
        var dialog = mapper.toEntity(request);
        var created = dialogService.create(dialog);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(mapper.toResponse(created));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<DialogResponse> getById(@PathVariable String id) {
        var dialog = dialogService.findById(id);
        return ResponseEntity.ok(mapper.toResponse(dialog));
    }
}
```

---

## Правило 2: Глобальная обработка ошибок

### Структура исключений:

```
src/main/java/com/example/panel/exception/
├── PanelException.java              # Базовое исключение
├── ResourceNotFoundException.java    # 404
├── BusinessLogicException.java       # 400 (business rule violation)
├── UnauthorizedException.java        # 401
├── ForbiddenException.java           # 403
└── GlobalExceptionHandler.java       # Централизованная обработка
```

### ✅ Базовое исключение:

```java
package com.example.panel.exception;

/**
 * Базовое исключение для приложения
 */
public abstract class PanelException extends RuntimeException {
    private final String code;
    private final int httpStatus;
    
    public PanelException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }
    
    public String getCode() {
        return code;
    }
    
    public int getHttpStatus() {
        return httpStatus;
    }
}
```

### ✅ Специфичные исключения:

```java
// ResourceNotFoundException.java
public class ResourceNotFoundException extends PanelException {
    public ResourceNotFoundException(String resourceName, String id) {
        super(
            "RESOURCE_NOT_FOUND",
            resourceName + " not found: " + id,
            404
        );
    }
}

// BusinessLogicException.java
public class BusinessLogicException extends PanelException {
    public BusinessLogicException(String message) {
        super("BUSINESS_ERROR", message, 400);
    }
}

// UnauthorizedException.java
public class UnauthorizedException extends PanelException {
    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message, 401);
    }
}
```

### ✅ ErrorResponse DTO:

```java
package com.example.panel.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Единый формат ошибки для всех API ответов
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String code,           // e.g., "RESOURCE_NOT_FOUND", "BUSINESS_ERROR"
    String message,        // Описание ошибки
    String timestamp,      // ISO 8601 timestamp
    String path,          // Request path
    String details        // Дополнительные детали (nullable)
) {
    public static ErrorResponse of(String code, String message, String path) {
        return new ErrorResponse(
            code,
            message,
            java.time.OffsetDateTime.now().toString(),
            path,
            null
        );
    }
}
```

### ✅ GlobalExceptionHandler:

```java
package com.example.panel.exception;

import com.example.panel.model.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * Обработка кастомных исключений PanelException
     */
    @ExceptionHandler(PanelException.class)
    public ResponseEntity<ErrorResponse> handlePanelException(
        PanelException ex,
        WebRequest request
    ) {
        log.warn("PanelException: {}: {}", ex.getCode(), ex.getMessage());
        
        ErrorResponse error = ErrorResponse.of(
            ex.getCode(),
            ex.getMessage(),
            request.getDescription(false).replace("uri=", "")
        );
        
        return ResponseEntity
            .status(ex.getHttpStatus())
            .body(error);
    }
    
    /**
     * Обработка ошибок валидации (@Valid)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
        MethodArgumentNotValidException ex,
        WebRequest request
    ) {
        String details = ex.getBindingResult().getFieldErrors()
            .stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
        
        log.warn("Validation error: {}", details);
        
        ErrorResponse error = new ErrorResponse(
            "VALIDATION_ERROR",
            "Validation failed",
            java.time.OffsetDateTime.now().toString(),
            request.getDescription(false).replace("uri=", ""),
            details
        );
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(error);
    }
    
    /**
     * Обработка всех остальных исключений
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
        Exception ex,
        WebRequest request
    ) {
        log.error("Unexpected exception", ex);
        
        ErrorResponse error = ErrorResponse.of(
            "INTERNAL_ERROR",
            "Something went wrong",
            request.getDescription(false).replace("uri=", "")
        );
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(error);
    }
}
```

### ✅ Использование в сервисе:

```java
@Service
public class DialogService implements IDialogService {
    private static final Logger log = LoggerFactory.getLogger(DialogService.class);
    
    private final IDialogRepository repository;
    
    public DialogDetails findById(String id) {
        log.debug("Finding dialog: {}", id);
        
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Dialog", id));
    }
    
    public String create(Dialog dialog) {
        if (dialog.getPriority() < 1 || dialog.getPriority() > 5) {
            throw new BusinessLogicException("Priority must be between 1 and 5");
        }
        
        log.info("Creating dialog for client: {}", dialog.getClientId());
        return repository.save(dialog).getId();
    }
}
```

---

## Правило 3: API Versioning

### Path-based versioning (рекомендуемый подход):

```
/api/v1/dialogs          # Версия 1
/api/v2/dialogs          # Версия 2 (улучшенная версия)
```

### ✅ Пример V1:

```java
@RestController
@RequestMapping("/api/v1/dialogs")
public class DialogApiControllerV1 {
    
    @GetMapping
    public List<DialogResponse> list() {
        return dialogService.findAll()
            .stream()
            .map(mapper::toResponse)
            .collect(Collectors.toList());
    }
    
    @PostMapping
    public ResponseEntity<DialogResponse> create(@Valid @RequestBody CreateDialogRequest req) {
        var dialog = mapper.toEntity(req);
        var created = dialogService.create(dialog);
        return ResponseEntity.status(201).body(mapper.toResponse(created));
    }
}
```

### ✅ Пример V2 (с улучшениями):

```java
@RestController
@RequestMapping("/api/v2/dialogs")
public class DialogApiControllerV2 {
    
    @GetMapping
    public ResponseEntity<Page<DialogResponse>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String sortBy
    ) {
        var pageable = PageRequest.of(page, size, Sort.by(sortBy == null ? "createdAt" : sortBy).descending());
        var result = status == null 
            ? dialogService.findAll(pageable)
            : dialogService.findByStatus(status, pageable);
        
        return ResponseEntity.ok(result.map(mapper::toResponse));
    }
    
    @PostMapping
    public ResponseEntity<DialogResponse> create(@Valid @RequestBody CreateDialogRequest req) {
        var dialog = mapper.toEntity(req);
        var created = dialogService.create(dialog);
        return ResponseEntity
            .status(201)
            .location(URI.create("/api/v2/dialogs/" + created.getId()))
            .body(mapper.toResponse(created));
    }
}
```

### Миграция старых клиентов:

```java
@RestController
@RequestMapping("/api/dialogs")  // Legacy endpoint (без версии)
@Deprecated
public class DialogApiControllerLegacy extends DialogApiControllerV1 {
    // Устаревший контроллер, пересылает на V1
}
```

---

## Правило 4: Документирование API с помощью OpenAPI/Swagger

### ✅ Пример аннотирования:

```java
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
@RequestMapping("/api/v1/dialogs")
@Tag(name = "Dialogs", description = "Dialog management API")
public class DialogApiController {
    
    @PostMapping
    @Operation(summary = "Create a new dialog", description = "Creates a new dialog with the provided information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Dialog created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<DialogResponse> create(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Dialog creation request"
        )
        @Valid @RequestBody CreateDialogRequest request
    ) {
        // ...
    }
}
```

### pom.xml зависимость:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.0.2</version>
</dependency>
```

Доступно на: `http://localhost:8080/swagger-ui.html`

---

## Проверочный лист для review

- [ ] Есть Request DTO для входящих данных?
- [ ] Есть Response DTO для исходящих данных?
- [ ] Request DTO содержит валидацию (@NotBlank, @Email, etc)?
- [ ] Есть Mapper для конвертации (Entity <-> DTO)?
- [ ] Контроллер не оборачивает Entity напрямую?
- [ ] Есть GlobalExceptionHandler?
- [ ] Исключения имеют иерархию (extends PanelException)?
- [ ] API эндпоинты версионированы (/api/v1, /api/v2)?
- [ ] Есть ErrorResponse DTO для единого формата ошибок?
- [ ] Логирование в сервисах через SLF4J?

---

**Статус:** Активный, вступает в силу сразу  
**Автор:** GitHub Copilot  
**Дата создания:** 8 апреля 2026  
