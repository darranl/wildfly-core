# Elytron Subsystem Version History

## Model Versions

| Model Version | WildFly Version | Notes |
|---------------|-----------------|-------|
| 20.0.0        | 42.0+           | Pure version bump for WildFly 42 features |
| 19.0.0        | 32.0+           | Dynamic SSL context support (WFCORE-6756) |
| 18.0.0        | 29.0+           | Added audit log encoding (WFCORE-6312) |
| 17.0.0        | 28.0+           | (WFCORE-6192) |
| ...           | ...             | See git history for earlier versions |

## Schema Versions

Schema versions are maintained independently from model versions.
See ElytronSubsystemSchema.java for current schema versions.

## Version Bump Guidelines

- Model versions track management API changes (attributes, operations, capabilities)
- Schema versions track XML configuration format changes
- Both can be bumped independently or together
- Model version bumps require adding a `fromX()` transformer method
