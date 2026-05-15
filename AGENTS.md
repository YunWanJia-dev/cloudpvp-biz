# AGENTS.md

## Project Overview

`cloudpvp` is a Gradle multi-module project. It currently uses Spring Boot / Spring Cloud as the main runtime framework. The codebase should keep a clear distinction between framework-independent business/shared code and framework-specific adaptation code.

Root structure:

```text
cloudpvp
├── build.gradle                  # Root build script for plugins, repositories, Java/Kotlin versions, and output layout
├── settings.gradle               # Gradle multi-module declarations
├── buildSrc/
│   └── shared.gradle             # Shared Gradle configuration for business services
├── gradle/
│   └── libs.versions.toml        # Dependency and plugin version catalog
├── common-config.properties      # Shared configuration file
├── cloudpvp-core/                # Framework-independent shared core module, remember `never` add any framework into this module.
├── cloudpvp-beans/               # Spring-related shared configuration, beans, and components
├── cloudpvp-gateway/             # Gateway service
├── cloudpvp-auth/                # Authentication service
├── cloudpvp-lobby/               # Lobby service
├── cloudpvp-play/                # Game/play configuration service
├── cloudpvp-state/               # Player state service
└── cloudpvp-user-summary/        # User profile summary service
```

## Module Responsibilities

| Module | Responsibility |
| --- | --- |
| `cloudpvp-core` | Framework-independent shared core module. It contains business-shared entities, value types, constants, exceptions, protocol models, and pure utilities. It must not introduce Spring, Redis, Servlet, WebSocket, Gateway, or other framework-specific types. |
| `cloudpvp-beans` | Current Spring support module. It contains reusable Spring beans, configuration properties, auto-configuration, exception handling, and other framework-specific shared code. It is not a framework-independent module. It may be renamed to `cloudpvp-spring-support` later. |
| `cloudpvp-gateway` | Spring Cloud Gateway service. It owns entry routing, CORS, gateway filters, and other gateway-side behavior. |
| `cloudpvp-auth` | Authentication service. It currently owns Steam OpenID login, login callbacks, token issuing, and login page templates. |
| `cloudpvp-lobby` | Lobby service. It owns lobby creation, joining, leaving, host switching, WebSocket message delivery, and Redis-backed temporary state. |
| `cloudpvp-play` | Game/play configuration service. It owns queries for games, types, modes, and other play metadata. |
| `cloudpvp-state` | Player state service. It owns player online state, current state storage, and state WebSocket connections. |
| `cloudpvp-user-summary` | User profile summary service. It owns player profile queries, profile summary aggregation, and future profile refresh entry points. |

## Responsibility Boundaries

### `cloudpvp-core`

- Contains only framework-independent code.
- May contain shared domain entities, value types, enums, constants, DTOs, base exceptions, and pure-function utilities.
- Must not depend on Spring, Redis, Servlet, WebSocket, Spring Cloud Gateway, Spring Data, or similar framework types.
- Must not contain concrete infrastructure implementations such as HTTP client details, Redis serializers, or framework bean wiring.
- If a capability must be shared across frameworks, prefer a plain interface or configuration model and implement it in the concrete adapter layer.

### `cloudpvp-beans`

- This is the Spring adaptation/support layer, not a shared business layer.
- It may depend on Spring Boot, Spring Web, Spring ConfigurationProperties, Spring Advice, and related framework APIs.
- It owns reusable Spring bean wiring, configuration property binding, global exception handling, Jackson configuration, and similar support code.
- It must not contain concrete business rules. Business rules belong to the corresponding business service module.

### Business Service Modules

- `controller` / `websocket` own protocol entry points: HTTP parameters, WebSocket sessions, and request/response conversion.
- `service` owns application flows and business use-case orchestration.
- `repository` owns data access interfaces or Spring Data repositories.
- `configurations` owns framework configuration.
- `entity` should primarily express business state. Avoid adding WebSocket sessions, Redis listeners, serializers, or other infrastructure behavior to entities.
- `constant`, `model`, and `exceptions` should hold service-local content. Move content to `cloudpvp-core` only when it is truly shared across services.

## Directory Naming Conventions

- `controller`: HTTP API entry points.
- `websocket`: WebSocket handlers.
- `service`: Application services and business flows.
- `repository`: Data access or state storage abstractions.
- `entity`: Business entities or state objects.
- `model`: API models, message models, or third-party response models.
- `constant`: Constants and enums.
- `configurations`: Framework configuration.
- `property`: Configuration property binding.
- `component`: Framework-managed reusable components.
- `interceptor`: Request or WebSocket handshake interceptors.
- `exceptions`: Business exceptions or service-local exceptions.

## Comment Guidelines

### New Classes

- Add a class-level Javadoc/KDoc comment for every new public or framework-managed class.
- The first line should state the class name or main responsibility in concise terms.
- Use Chinese for class description lines, method summaries, `@param`, `@return`, and `@throws` descriptions.
- Add one short Chinese description line when the class responsibility is not obvious from its name.
- Add `@author` for new classes. Do not invent the author from memory; get it from Git before writing the comment:

```powershell
git config user.name
```

- Add `@since` for new classes. Do not invent the timestamp from memory; get it from the command line before writing the comment:

```powershell
Get-Date -Format "yyyy/M/d HH:mm"
```

Example:

```java
/**
 * SteamApiConfiguration
 * Steam API 配置模型。
 *
 * @author sheip9
 * @since 2026/5/15 15:31
 */
```

### New Methods

- Add method-level Javadoc/KDoc for new public methods and for package/private methods whose behavior is not immediately obvious.
- The summary should describe what the method does in Chinese, not repeat the method name mechanically.
- Document each non-obvious parameter with `@param`.
- Document the return value with `@return` when the return type is not self-explanatory.
- Document checked exceptions and expected business exceptions with `@throws`.
- Keep implementation details out of method comments unless callers need to know them.

Example:

```java
/**
 * 从已校验的令牌中获取当前玩家 ID。
 *
 * @param token 请求头中的授权令牌
 * @return 当前玩家的 Steam ID64
 * @throws UserIdInvalidException 当令牌无效或不包含玩家 ID 时抛出
 */
```
