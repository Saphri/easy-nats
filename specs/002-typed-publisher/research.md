# Research: Typed NatsPublisher with CloudEvents Support

**Feature**: MVP 002 Typed NatsPublisher
**Date**: 2025-10-26
**Branch**: 002-typed-publisher

## Research Findings

### 1. Jackson Configuration in Quarkus for Native Image

**Decision**: Use `quarkus-jackson` dependency (BOM-managed version) for runtime module. Complex types require `@RegisterForReflection` annotation for GraalVM native image compilation.

**Rationale**:
- Quarkus Jackson extension provides native image support out-of-the-box
- `@RegisterForReflection` from `io.quarkus.runtime.annotations` enables reflection metadata generation
- No custom reflection configuration needed if developers annotate their domain classes
- Jackson ObjectMapper available via dependency injection in Quarkus

**Alternatives Considered**:
- Custom serialization (Gson, Kryo): Too much boilerplate; Jackson is standard in Quarkus
- Relying on Jackson auto-discovery: Works in JVM mode but breaks in native image without annotations
- Custom reflection config files: More complex than @RegisterForReflection annotations

**Implementation Notes**:
- Use `ObjectMapper` injected from Quarkus context (provided by quarkus-jackson)
- Catch `JsonProcessingException` and wrap in custom `SerializationException`
- Document clearly in quickstart.md that complex types MUST have `@RegisterForReflection`

---

### 2. CloudEvents Spec 1.0 Header Naming & Implementation

**Decision**: Use `ce-` prefix for all CloudEvents headers (NATS message headers). Binary content mode: payload is raw event data, attributes in `ce-` headers.

**Rationale**:
- CloudEvents spec 1.0 recommends `ce-` prefix for protocol binding with HTTP headers (NATS headers map to HTTP headers)
- Binary content mode keeps NATS message body clean for event payload (not wrapped in CloudEvents JSON envelope)
- `ce-specversion: 1.0` required header (always set to 1.0)
- `ce-id`, `ce-source`, `ce-type` are required in CloudEvents spec; we auto-generate ce-id and ce-source if missing

**Alternatives Considered**:
- Structured content mode (entire event as JSON): Bloats message size; violates clean separation of headers and payload
- No prefix (bare header names): Risk of collision with user headers
- Custom prefix: Breaks CloudEvents interoperability

**Implementation Notes**:
- NATS headers API: `message.getHeaders()` returns `Headers` object with `add(name, value)` method
- Header names: Case-insensitive in HTTP; NATS preserves case (use lowercase for consistency)
- Auto-generate `ce-type` as fully-qualified class name (e.g., `org.example.UserCreatedEvent`)
- Auto-generate `ce-source` as hostname or application identifier (read from config or JVM properties)
- Auto-generate `ce-id` as UUID v4 (using `java.util.UUID.randomUUID()`)
- Auto-generate `ce-time` as ISO 8601 UTC (using `java.time.Instant.now().toString()`)

---

### 3. GraalVM Reflection Requirements for Type Erasure

**Decision**: Type erasure at compile-time requires explicit reflection hints. Use `@RegisterForReflection` on domain classes. Provide clear documentation and examples in quickstart.md.

**Rationale**:
- Java generics are erased at runtime; `NatsPublisher<T>` cannot introspect `T` class at runtime without help
- GraalVM native image requires pre-computed reflection metadata (not available at runtime discovery)
- `@RegisterForReflection` from Quarkus annotation processor generates the necessary metadata
- Jackson integration: ObjectMapper reflection-based deserialization also needs metadata for complex types

**Alternatives Considered**:
- Reflection config JSON: More verbose; less discoverable than annotations
- Code generation at compile-time: Overkill for MVP 002
- Runtime parameter class passing: Requires developers to pass Class<?> to publisher (bad UX)

**Implementation Notes**:
- `@RegisterForReflection(targets={MyClass.class})` or `@RegisterForReflection` on class definition
- Quarkus scans classpath automatically during build; no manual configuration needed
- Document in quickstart that for complex types, use: `@RegisterForReflection public class MyEvent { ... }`
- Example: `@RegisterForReflection public class UserCreatedEvent { String userId; String email; }`

---

### 4. Encoder/Decoder Resolution Order Strategy

**Decision**: Priority-based encoder/decoder selection with text-safe encoding:
1. Java primitives + String (UTF-8)
2. Byte types (base64-encoded, NEVER raw binary)
3. Primitive/String arrays (UTF-8 space/comma-separated)
4. Jackson for complex types (JSON)

**Rationale**:
- Primitives (int, long, short, double, float, boolean, char) are most common in NATS publishing
- Direct string/byte encoding for primitives avoids Jackson overhead
- **Byte types MUST be base64-encoded**: NATS message payloads must remain text-safe (UTF-8 compatible); raw binary breaks compatibility with NATS headers and CloudEvents spec binary content mode
- Arrays of primitives/String also common; native support reduces GraalVM reflection burden
- Complex types (POJOs, records) fall back to Jackson efficiently
- Clear resolution order prevents ambiguity and makes debugging easier
- **Critical constraint**: All message payloads are text-based (UTF-8 or base64); no raw binary data

**Alternatives Considered**:
- Always use Jackson: Simpler code but adds overhead for primitive types; requires Jackson even for simple int publishing
- Raw binary for bytes: BREAKS CloudEvents header compatibility; non-compliant with NATS spec
- Custom encoder registration: More flexible but complex; overkill for MVP 002
- Type annotation hints: Requires developer annotation (bad UX vs. automatic detection)

**Implementation Notes**:
- Create `TypedPayloadEncoder` utility class with static methods:
  - `canEncodeNatively(Class<?> type)` - checks if primitive, byte type, or array of primitives/String
  - `encodeNatively(Object value)` - direct string conversion with base64 for bytes
  - `encodeWithJackson(Object value, ObjectMapper mapper)` - fallback
- Usage in `NatsPublisher<T>.publish(T object)`:
  1. Null check → throw IllegalArgumentException
  2. Check if T is primitive → encode natively (UTF-8)
  3. Check if T is byte type → encode natively (base64)
  4. Check if T is array → encode natively (space/comma-separated)
  5. Otherwise → use Jackson ObjectMapper (JSON)
- Byte encoding: `java.util.Base64.getEncoder().encodeToString(bytes)` then UTF-8 encode
- Exception handling: Wrap JsonProcessingException in SerializationException with user-friendly message

---

## Design Decisions Summary

| Area | Decision | Rationale |
|------|----------|-----------|
| **Jackson Integration** | Use `quarkus-jackson` BOM; @RegisterForReflection for native image | Standard Quarkus approach; annotation-based is discoverable |
| **CloudEvents Headers** | Binary mode with `ce-` prefix; auto-generate ce-id, ce-source, ce-type | Spec 1.0 compliance; clean payload separation; developer UX (minimal boilerplate) |
| **Reflection Metadata** | @RegisterForReflection on domain classes; document in quickstart | GraalVM requirement; clear; scalable for future MVPs |
| **Encoder Selection** | Primitives → Bytes (base64) → Arrays → Jackson fallback | Performance for common case; text-safe encoding |
| **Byte Encoding** | ALWAYS base64; NEVER raw binary | NATS compatibility; CloudEvents spec compliance; text-safe payloads |

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| GraalVM image size bloat from Jackson reflection | Use @RegisterForReflection selectively; document guidance in quickstart |
| Type erasure breaking encoder selection | Use TypedPayloadEncoder utility with class reflection; test with both JVM and native modes |
| CloudEvents header collisions | Enforce `ce-` prefix; document reserved header names |
| Raw binary payloads breaking NATS compatibility | ENFORCE: Byte types ALWAYS base64-encoded; document this requirement; reject raw binary PRs |
| Byte encoding performance overhead | Base64 expansion ~33%; acceptable for binary data sizes < 10KB; document in quickstart |
| Primitive encoder performance | Benchmark; use StringBuilder for array encoding if needed |

---

## Next Steps (Phase 1)

- Create `TypedPayloadEncoder` class with encoder/decoder logic
- Create `CloudEventsHeaders` factory for header generation
- Extend `NatsPublisher<T>` with generic type parameter and typed publish method
- Create `TypedPublisherResource` REST endpoint for testing
- Document in `quickstart.md` with examples of @RegisterForReflection usage
