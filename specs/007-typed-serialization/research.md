# Research: Typed Serialization with Jackson Integration

**Feature**: 007-typed-serialization
**Date**: 2025-10-27
**Status**: ✅ COMPLETE (No unknowns to resolve)

## Summary

All technical decisions are explicitly defined in the specification and architecture. No NEEDS CLARIFICATION markers remain. This document records key research findings and decisions made.

---

## Key Decisions

### 1. Jackson-Only Deserialization

**Decision**: Remove all native type handling (primitives, wrappers, arrays, byte[], String[]) from `MessageDeserializer`. Support only Jackson-compatible types (POJOs, records, generics).

**Rationale**:
- Simplifies codebase: ~250 lines of native type parsing removed
- Eliminates "magic": Single, explicit rule—if Jackson can handle it, we support it
- Aligns with "less magic for users" feature goal
- Users can wrap unsupported types in POJOs if needed

**Alternatives Considered**:
1. **Keep dual support (native + Jackson)**:
   - Pro: Backward compatible with existing code
   - Con: Adds complexity, contradicts "less magic" goal, hard to test all combinations
   - Rejected: Feature explicitly states "only typed objects will be supported"

2. **Custom encoder strategies**:
   - Pro: Flexibility for users
   - Con: More configuration, more edge cases, violates "less magic"
   - Rejected: Default CDI ObjectMapper is simpler

**Validation**: Existing code in `006-typed-subscriber` feature branch already uses Jackson for complex types. This simplifies to Jackson-only model.

---

### 2. Type Validation via Introspection (No Instantiation)

**Decision**: Validate types at subscriber/publisher registration using Jackson's `ObjectMapper.getTypeFactory().constructType()` without instantiating user classes.

**Rationale**:
- Cannot instantiate user classes (e.g., no no-arg constructor for validation)
- Jackson's type introspection catches structural issues (unresolvable generics, etc.)
- Clear errors at runtime when serialization/deserialization fails (malformed JSON, missing no-arg constructor)
- Two-phase validation: structural (registration) + behavioral (first use)

**Alternatives Considered**:
1. **Build-time annotation processing to validate all types**:
   - Pro: Catch issues at compile-time
   - Con: Requires user to explicitly list types, breaks modularity
   - Rejected: Type introspection at registration is sufficient and simpler

2. **Runtime-only validation**:
   - Pro: Simplest implementation
   - Con: No early feedback when type unsupported
   - Rejected: Introspection at registration provides better UX

**Validation**: Existing `DefaultMessageHandler` already uses `objectMapper.getTypeFactory().constructType()` (line 60 of DefaultMessageHandler.java). Pattern proven in production code.

---

### 3. Default CDI-Injected ObjectMapper Only

**Decision**: Library uses only the default ObjectMapper provided by Quarkus CDI. No custom mapper configuration or injection of user-provided ObjectMapper instances.

**Rationale**:
- Simplifies API surface: Users declare type, library handles serialization with standard ObjectMapper
- Aligns with Quarkus conventions: Single, shared ObjectMapper for entire application
- Reduces configuration burden: One less thing for users to configure
- Avoids custom serialization logic: Rely on Jackson's well-tested implementation

**Alternatives Considered**:
1. **Allow custom ObjectMapper injection**:
   - Pro: Flexibility for users with custom Jackson modules
   - Con: Adds complexity, multiple mappers, unclear which is used where
   - Rejected: Users can configure their mapper in `application.properties` (Quarkus handles this)

2. **Allow per-type custom serializers**:
   - Pro: Ultimate flexibility
   - Con: Violates "less magic", user becomes responsible for serialization logic
   - Rejected: Jackson annotations (@JsonDeserialize, etc.) provide sufficient customization

**Validation**: Quarkus provides a single default ObjectMapper via CDI. This is the standard pattern in Quarkus applications.

---

### 4. CloudEvents Binary-Mode (Internal Implementation Detail)

**Decision**: CloudEvents binary-mode wrapping remains internal implementation detail. Users never see or interact with CloudEvents headers/format.

**Rationale**:
- Simplifies user mental model: Publish/subscribe to typed objects, not events
- Internal consistency: CloudEvents already used internally for compliance (Principle V)
- Transparent to users: Serialization/deserialization is automatic
- Future extensibility: Can enhance CloudEvent support without breaking API

**Alternatives Considered**:
1. **Expose CloudEvent wrapping to users**:
   - Pro: Users can access CloudEvent attributes (source, type, etc.)
   - Con: Adds API surface, complexity, violates "less magic"
   - Rejected: Out of scope for this feature; future enhancement if needed

2. **Support structured content mode (CloudEvent in JSON body)**:
   - Pro: Alternative format option
   - Con: Adds complexity, testing burden
   - Rejected: Binary-mode is simpler and already implemented

**Validation**: Existing `006-typed-subscriber` feature already uses CloudEvents binary-mode. This feature continues the pattern without exposing it to users.

---

### 5. Clear Error Guidance: Wrapper Pattern

**Decision**: Error messages guide users to wrap unsupported types in Jackson-compatible POJOs.

**Example Error Messages**:
- **Primitive type**: "Type `int` is not supported. Wrap it in a POJO: `class IntValue { int value; }`"
- **Missing no-arg constructor**: "Type `OrderData` requires a no-arg constructor for Jackson deserialization. Add a no-arg constructor or use `@JsonDeserialize`"
- **Array**: "Type `String[]` is not supported. Wrap it in a POJO: `class StringList { String[] items; }`"

**Rationale**:
- Eliminates ambiguity: Users understand exactly why their type failed
- Provides solution: Wrapper pattern is simple and well-understood
- Educates users: Teaches Jackson compatibility rules
- Reduces support burden: Users self-service with documentation

**Alternatives Considered**:
1. **Cryptic error messages**:
   - Rejected: Frustrating for users, increases support burden

2. **Support for more complex types (lists, maps)**:
   - Pro: Users don't need wrappers
   - Con: Adds validation complexity, unclear when types work
   - Rejected: Jackson's built-in support for generics is sufficient; users wrap if needed

**Validation**: Documentation will provide comprehensive examples of wrapper patterns and Jackson-compatible types.

---

## Technical Context Validated

| Context | Decision | Confidence |
|---------|----------|-----------|
| Language/Version | Java 21 (enforced per Constitution) | ✅ High |
| Primary Dependency | Jackson Databind (already in use) | ✅ High |
| Storage | N/A (messaging) | ✅ N/A |
| Testing Framework | JUnit 5 + AssertJ (existing) | ✅ High |
| Target Platform | Quarkus 3.27.0, native image | ✅ High |
| Project Type | Multi-module extension (existing) | ✅ High |
| Performance Goals | Standard Jackson serialization latency | ✅ High |
| Constraints | Runtime JAR < 500 KB (no new deps) | ✅ High |

---

## Implementation Readiness

✅ **All technical decisions validated and documented**
✅ **No blocking unknowns remain**
✅ **Patterns proven in existing code (006-typed-subscriber)**
✅ **Constitution check passed (all 7 principles)**
✅ **Ready to proceed to Phase 1 (Design & Contracts)**

---

## Next Steps

1. **Phase 1**: Generate design artifacts (data-model.md, contracts/, quickstart.md)
2. **Phase 1**: Update agent context with Jackson patterns
3. **Phase 2**: Generate implementation tasks (via `/speckit.tasks`)
4. **Implementation**: Code changes per task breakdown
5. **Testing**: JVM + native image validation
6. **Documentation**: Jackson guide, wrapper patterns, error troubleshooting
