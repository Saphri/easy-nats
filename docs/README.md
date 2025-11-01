# Quarkus EasyNATS Documentation

## Overview

This directory contains comprehensive documentation for the Quarkus EasyNATS extension, a Quarkus extension that simplifies integration with NATS JetStream.

## Documentation Structure

```
docs/
├── INDEX.md                              # Documentation hub and navigation guide
├── README.md                             # This file
├── QUICKSTART.md                         # 5-minute quick start guide
├── DEV_SERVICES_GUIDE.md                 # Automatic NATS provisioning for development
├── CONFIGURATION.md                      # Connection, auth, TLS configuration
├── JACKSON_COMPATIBILITY_GUIDE.md        # Type support reference
├── WRAPPER_PATTERN.md                    # Wrapping unsupported types
├── JACKSON_ANNOTATIONS_GUIDE.md          # Jackson annotation customization
├── EXPLICIT_ACK_NAK_GUIDE.md             # Manual ack/nak control guide
├── DISTRIBUTED_TRACING.md                # OpenTelemetry integration
└── ERROR_TROUBLESHOOTING.md              # Common errors and solutions
```

## Quick Navigation

### 🚀 Getting Started
- **[Start Here: Documentation Index](./INDEX.md)** - Complete navigation hub
- **[Quick Start Guide](./QUICKSTART.md)** - 5-minute introduction
- **[Dev Services Guide](./DEV_SERVICES_GUIDE.md)** - Zero-config development with automatic NATS provisioning

### 📚 Learn the Type System
- **[Jackson Compatibility Guide](./JACKSON_COMPATIBILITY_GUIDE.md)** - What types are supported
  - POJOs, Records, Collections
  - Type validation rules
  - Best practices

- **[Wrapper Pattern Guide](./WRAPPER_PATTERN.md)** - Handling unsupported types
  - Wrapping primitives (int, long, etc.)
  - Wrapping arrays
  - Wrapping classes without no-arg constructors
  - Complete working examples

### 🎨 Customize Serialization
- **[Jackson Annotations Guide](./JACKSON_ANNOTATIONS_GUIDE.md)** - Advanced customization
  - @JsonProperty, @JsonIgnore, @JsonDeserialize, @JsonSerialize
  - @JsonAlias, @JsonFormat, @JsonInclude
  - Real-world examples

### 🔧 Advanced Features
- **[Explicit Ack/Nak Guide](./EXPLICIT_ACK_NAK_GUIDE.md)** - Manual message control for retries and error handling.

### 🆘 Troubleshooting
- **[Error Troubleshooting Guide](./ERROR_TROUBLESHOOTING.md)** - Solutions to common problems
  - 9+ error scenarios with solutions
  - Error reference table
  - Step-by-step fixes

## Documentation Stats

| File | Size | Content |
|------|------|---------|
| INDEX.md | 5 KB | Navigation hub & quick links |
| QUICKSTART.md | 11 KB | Getting started guide |
| DEV_SERVICES_GUIDE.md | 14 KB | Automatic provisioning (6+ sections) |
| CONFIGURATION.md | 18 KB | Connection & security (3+ sections) |
| JACKSON_COMPATIBILITY_GUIDE.md | 13 KB | Type reference (5+ sections) |
| WRAPPER_PATTERN.md | 14 KB | Wrapping guide (3 examples) |
| JACKSON_ANNOTATIONS_GUIDE.md | 12 KB | Annotations reference (7+ annotations) |
| EXPLICIT_ACK_NAK_GUIDE.md | 8 KB | Manual ack/nak guide |
| DISTRIBUTED_TRACING.md | 7 KB | OpenTelemetry integration |
| ERROR_TROUBLESHOOTING.md | 20 KB | Troubleshooting (10+ error types) |
| **Total** | **122 KB** | **Complete documentation** |

## Feature Coverage

✅ **Complete Documentation**
- Dev Services automatic provisioning documented with examples
- Type system fully documented with examples
- All supported types explained
- Unsupported types handling covered
- Connection configuration for production deployments
- Jackson annotations guide included
- Explicit ack/nak control for advanced error handling
- Distributed tracing with OpenTelemetry integration
- Error troubleshooting for 10+ common scenarios
- 100+ code examples across all guides
- Best practices and patterns included

✅ **Verified Examples**
- All code examples tested and working
- Example verification document included in specs/
- Examples cover POJOs, Records, Generics, Annotations
- Integration tests validate all scenarios

✅ **Navigation & Organization**
- Documentation index for easy navigation
- Cross-references between guides
- Quick links section in main README
- Clear hierarchy and organization

## Using This Documentation

### I'm New to Quarkus EasyNATS
1. Start with [Quick Start Guide](./QUICKSTART.md)
2. Read [Documentation Index](./INDEX.md) for overview
3. Explore specific guides as needed

### I Need to Support a Specific Type
1. Check [Jackson Compatibility Guide](./JACKSON_COMPATIBILITY_GUIDE.md)
2. If unsupported, see [Wrapper Pattern Guide](./WRAPPER_PATTERN.md)
3. Reference [Error Troubleshooting](./ERROR_TROUBLESHOOTING.md) if issues

### I Need Custom Serialization
1. Read [Jackson Annotations Guide](./JACKSON_ANNOTATIONS_GUIDE.md)
2. Check relevant sections in [Quick Start](./QUICKSTART.md)
3. Use examples as templates for your code

### I Need to Implement Custom Retries
1. Read the [Explicit Ack/Nak Guide](./EXPLICIT_ACK_NAK_GUIDE.md)
2. Check the examples for transient vs. permanent error handling.

### I'm Debugging an Error
1. Find your error in [Error Troubleshooting](./ERROR_TROUBLESHOOTING.md)
2. Follow the step-by-step solution
3. Check relevant sections in other guides for context

## Key Features Documented

- ✅ Type-safe publishing and subscribing
- ✅ Automatic JSON serialization/deserialization
- ✅ CloudEvents 1.0 binary-mode support
- ✅ Explicit Ack/Nak control for advanced messaging patterns
- ✅ Type validation at build-time and runtime
- ✅ Clear error messages with guidance
- ✅ Jackson annotation support
- ✅ POJO and Record support
- ✅ Generic collection support
- ✅ Wrapper patterns for unsupported types

## Related Resources

- **Main README**: [../README.md](../README.md) - Project overview
- **Feature Specification**: [../specs/009-explicit-ack-nak/spec.md](../specs/009-explicit-ack-nak/spec.md)
- **Implementation Tasks**: [../specs/009-explicit-ack-nak/tasks.md](../specs/009-explicit-ack-nak/tasks.md)
- **Implementation Plan**: [../specs/009-explicit-ack-nak/plan.md](../specs/009-explicit-ack-nak/plan.md)

## Documentation Maintenance

This documentation is:
- **Current**: Updated as of 2025-10-28
- **Tested**: All examples verified to compile and run
- **Complete**: Covers all features and common scenarios
- **Organized**: Structured for easy navigation
- **Actionable**: Each guide includes practical examples

---

**Get Started**: [Go to Documentation Index →](./INDEX.md)

**Questions?** Check the [Error Troubleshooting Guide](./ERROR_TROUBLESHOOTING.md) for common issues.
