# Quarkus EasyNATS Documentation

Welcome to the Quarkus EasyNATS documentation. This extension simplifies NATS JetStream integration with Quarkus, providing type-safe pub/sub messaging with automatic JSON serialization.

## 🚀 Quick Start

New to Quarkus EasyNATS? Start here:

- **[Quick Start Guide](./QUICKSTART.md)** - 5-minute introduction
  - Define message types
  - Publish typed messages
  - Subscribe to typed messages
  - Handle common patterns

## 📚 Documentation

### Type System & Compatibility

Learn what types you can use and how to handle unsupported types:

- **[Jackson Compatibility Guide](./JACKSON_COMPATIBILITY_GUIDE.md)** - Complete type reference
  - Supported types (POJOs, Records, Collections, etc.)
  - Type validation rules
  - Best practices
  - Common error solutions

- **[Wrapper Pattern Guide](./WRAPPER_PATTERN.md)** - Wrapping unsupported types
  - Wrapping primitives (int, long, double, etc.)
  - Wrapping arrays (int[], String[], etc.)
  - Wrapping types without no-arg constructors
  - Complete working examples
  - Best practices for wrappers

### Customization & Advanced Features

Customize serialization and deserialization behavior:

- **[Jackson Annotations Guide](./JACKSON_ANNOTATIONS_GUIDE.md)** - Using Jackson annotations
  - @JsonProperty - Rename fields
  - @JsonIgnore - Exclude fields
  - @JsonDeserialize - Custom deserialization
  - @JsonSerialize - Custom serialization
  - @JsonAlias - Accept multiple field names
  - @JsonFormat - Format dates/times
  - @JsonInclude - Conditional inclusion
  - Complete examples for each annotation

### Troubleshooting

When things don't work as expected:

- **[Error Troubleshooting Guide](./ERROR_TROUBLESHOOTING.md)** - Common errors and solutions
  - Primitive type errors
  - Array type errors
  - Missing constructor errors
  - Unrecognized field errors
  - Type mismatch errors
  - Infinite recursion errors
  - No serializer found errors
  - Generic type errors
  - Quick reference table

## 🏗️ Architecture & Specification

For deeper understanding of the implementation:

- **[Feature Specification](../specs/007-typed-serialization/spec.md)** - Complete requirements and use cases
- **[Implementation Plan](../specs/007-typed-serialization/plan.md)** - Architecture and design decisions
- **[Implementation Tasks](../specs/007-typed-serialization/tasks.md)** - All 64 implementation tasks with completion status

## 📖 Common Scenarios

### I want to send/receive simple objects
→ See [Quick Start Guide](./QUICKSTART.md)

### I need to support primitives or arrays
→ See [Wrapper Pattern Guide](./WRAPPER_PATTERN.md)

### I need to customize field names or serialization
→ See [Jackson Annotations Guide](./JACKSON_ANNOTATIONS_GUIDE.md)

### I'm getting an error
→ See [Error Troubleshooting Guide](./ERROR_TROUBLESHOOTING.md)

### I need to understand what types are supported
→ See [Jackson Compatibility Guide](./JACKSON_COMPATIBILITY_GUIDE.md)

## ✨ Key Features

- **Type-Safe Messaging** - Compile-time and runtime type validation
- **Automatic Serialization** - Jackson handles JSON seamlessly
- **CloudEvents Support** - CloudEvents 1.0 binary-mode wrapping
- **Clear Error Messages** - Detailed guidance when things go wrong
- **Annotation Support** - Full Jackson annotation compatibility
- **Records Support** - Java Record types work out-of-the-box

## 🔗 Navigation

| Section | Purpose | Best For |
|---------|---------|----------|
| QUICKSTART | Learn the basics | First-time users |
| JACKSON_COMPATIBILITY_GUIDE | Type reference | Understanding supported types |
| WRAPPER_PATTERN | Handling limitations | Wrapping primitives/arrays |
| JACKSON_ANNOTATIONS_GUIDE | Advanced customization | Custom serialization |
| ERROR_TROUBLESHOOTING | Problem solving | Debugging issues |

## 💡 Tips

- **Start with Quick Start** if you're new to the library
- **Use Records** for simple message types (Java 14+)
- **Use Jackson annotations** instead of creating wrapper types
- **Check the Troubleshooting guide** when you get errors

## 📝 Document Status

All documentation has been tested with working code examples:
- ✅ QUICKSTART.md - Verified with integration tests
- ✅ JACKSON_COMPATIBILITY_GUIDE.md - Comprehensive reference
- ✅ WRAPPER_PATTERN.md - Complete with examples
- ✅ JACKSON_ANNOTATIONS_GUIDE.md - Deep dive guide
- ✅ ERROR_TROUBLESHOOTING.md - 9+ error scenarios covered

---

**Last Updated**: 2025-10-27
**Feature Version**: 007-typed-serialization
**Status**: ✅ Complete (All 64 tasks done, 81 tests passing)
