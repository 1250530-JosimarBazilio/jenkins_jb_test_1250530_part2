# Test Fixes Report

**Date:** January 4, 2026  
**Project:** LMS-Books Microservice  
**Author:** GitHub Copilot

---

## Executive Summary

During the execution of unit and integration tests, **85 errors** were identified that prevented the correct execution of the test suite. After detailed analysis, **4 distinct root causes** were identified and fixed, resulting in **208 tests passing successfully**.

| Metric | Before | After |
|--------|--------|-------|
| Tests executed | 208 | 208 |
| Errors | 85 | 0 |
| Failures | 0 | 0 |
| Skipped | 0 | 0 |
| Result | ❌ BUILD FAILURE | ✅ BUILD SUCCESS |

---

## Identified Errors and Solutions

### 1. Invalid `spring.profiles.active` Property in Profile-Specific File

#### Error Description
```
InvalidConfigDataPropertyException: Property 'spring.profiles.active' imported from 
location 'class path resource [application-test.properties]' is invalid in a profile 
specific resource
```

#### Root Cause
The `application-test.properties` file contained the property `spring.profiles.active=test` on line 4. In Spring Boot, it is not allowed to define `spring.profiles.active` inside a profile-specific file (such as `application-{profile}.properties`), as this creates a circular dependency cycle.

#### Affected File
- `src/test/resources/application-test.properties`

#### Implemented Solution
Removal of the `spring.profiles.active=test` line from the test configuration file.

**Before:**
```properties
# Test Profile Configuration
# Used for unit and integration tests

spring.profiles.active=test

# H2 In-Memory Database for tests
```

**After:**
```properties
# Test Profile Configuration
# Used for unit and integration tests

# H2 In-Memory Database for tests
```

#### Impact
This fix resolved the initial ApplicationContext loading problem, reducing errors from 85 to 76.

---

### 2. `BookEventsPublisher` Bean Not Available in Test Environment

#### Error Description
```
NoSuchBeanDefinitionException: No qualifying bean of type 
'pt.psoft.g1.psoftg1.bookmanagement.publishers.BookEventsPublisher' available: 
expected at least 1 bean which qualifies as autowire candidate.
```

#### Root Cause
The `BookController` depends on `BookEventsPublisher` to publish events. The only existing implementation (`BookEventsRabbitmqPublisher`) is marked with `@Profile("!test")`, which means it is not created during tests. Since there was no alternative implementation for the test profile, Spring could not satisfy the dependency.

#### Affected Files
- `src/main/java/pt/psoft/g1/psoftg1/bookmanagement/api/BookController.java` (dependency)
- `src/main/java/pt/psoft/g1/psoftg1/bookmanagement/publishers/BookEventsRabbitmqPublisher.java` (existing implementation)

#### Implemented Solution
Creation of a No-Op (No Operation) implementation of `BookEventsPublisher` for the test profile.

**New file created:** `src/main/java/pt/psoft/g1/psoftg1/bookmanagement/publishers/BookEventsNoOpPublisher.java`

```java
package pt.psoft.g1.psoftg1.bookmanagement.publishers;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pt.psoft.g1.psoftg1.bookmanagement.api.BookViewAMQP;

/**
 * No-operation implementation of BookEventsPublisher.
 * Used in test profile when RabbitMQ is not available.
 */
@Profile("test")
@Component
public class BookEventsNoOpPublisher implements BookEventsPublisher {

    @Override
    public void sendBookCreated(BookViewAMQP bookView) {
        // No-op: Do nothing in test environment
    }

    @Override
    public void sendBookUpdated(BookViewAMQP bookView) {
        // No-op: Do nothing in test environment
    }

    @Override
    public void sendBookDeleted(BookViewAMQP bookView) {
        // No-op: Do nothing in test environment
    }
}
```

#### Impact
This implementation allows the `BookController` to be instantiated during tests without requiring an actual RabbitMQ connection.

---

### 3. Inner Class `ReceiverConfig` Did Not Inherit Profile Annotation

#### Error Description
```
UnsatisfiedDependencyException: Error creating bean with name 'bindingBookCreatedFanout' 
defined in class path resource [pt/psoft/g1/psoftg1/configuration/RabbitmqConfig$ReceiverConfig.class]: 
Unsatisfied dependency expressed through method 'bindingBookCreatedFanout' parameter 0: 
No qualifying bean of type 'org.springframework.amqp.core.FanoutExchange' available
```

#### Root Cause
The `RabbitmqConfig` class was correctly marked with `@Profile("!test")`, but the static inner class `ReceiverConfig` did not inherit this annotation. In Spring, static inner classes do not automatically inherit annotations from the outer class, so the beans defined in `ReceiverConfig` were being created even during tests.

#### Affected File
- `src/main/java/pt/psoft/g1/psoftg1/configuration/RabbitmqConfig.java`

#### Implemented Solution
Addition of the `@Profile("!test")` annotation to the inner `ReceiverConfig` class.

**Before:**
```java
@Configuration
static class ReceiverConfig {
```

**After:**
```java
@Profile("!test")
@Configuration
static class ReceiverConfig {
```

#### Impact
This fix prevents RabbitMQ configuration beans (queues, bindings) from being created during tests, eliminating FanoutExchange dependencies.

---

### 4. Integration Test with Incorrect Profile for `BookEventsListener`

#### Error Description
```
NoSuchBeanDefinitionException: No qualifying bean of type 
'pt.psoft.g1.psoftg1.bookmanagement.listeners.BookEventsListener' available: 
expected at least 1 bean which qualifies as autowire candidate.
```

#### Root Cause
The `BookEventsListener` is marked with `@Profile("database-per-instance")` because it is only needed when each instance has its own database. However, the integration test `BookEventsListenerIntegrationTest` only used `@ActiveProfiles("test")`, which did not activate the listener.

#### Affected File
- `src/test/java/pt/psoft/g1/psoftg1/bookmanagement/listeners/BookEventsListenerIntegrationTest.java`

#### Implemented Solution
Changed the test to activate both required profiles: `test` and `database-per-instance`.

**Before:**
```java
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("BookEventsListener Integration Tests")
class BookEventsListenerIntegrationTest {
```

**After:**
```java
@SpringBootTest
@ActiveProfiles({"test", "database-per-instance"})
@DisplayName("BookEventsListener Integration Tests")
class BookEventsListenerIntegrationTest {
```

#### Impact
The test can now correctly inject the `BookEventsListener`, allowing testing of the synchronization functionality between instances.

---

## Modified/Created Files

| File | Action | Description |
|------|--------|-------------|
| `src/test/resources/application-test.properties` | Modified | Removed invalid property |
| `src/main/java/.../publishers/BookEventsNoOpPublisher.java` | Created | New No-Op implementation for tests |
| `src/main/java/.../configuration/RabbitmqConfig.java` | Modified | Added @Profile to ReceiverConfig |
| `src/test/java/.../listeners/BookEventsListenerIntegrationTest.java` | Modified | Fixed active profiles |

---

## Lessons Learned

1. **Profile Properties:** Never define `spring.profiles.active` inside profile-specific configuration files.

2. **Inner Classes in Spring:** Static inner classes with `@Configuration` do not inherit annotations from the outer class - each one needs its own `@Profile` annotations.

3. **Fallback Implementations:** Always create No-Op or Mock implementations for interfaces that depend on external infrastructure (RabbitMQ, Kafka, etc.) when those services are not available in the test environment.

4. **Test Profiles:** When a component uses a specific profile different from "test", the integration test must activate multiple profiles using `@ActiveProfiles({"test", "other-profile"})`.

---

## Useful Commands

```bash
# Run all tests
./mvnw test

# Run tests with detailed output
./mvnw test -X

# View test reports
cat target/surefire-reports/*.txt

# Run specific tests
./mvnw test -Dtest=BookControllerIntegrationTest
```

---

## Conclusion

All 4 root causes were successfully identified and fixed. The test suite is now fully functional with **208 tests passing** and **0 errors**. The implemented fixes follow Spring Boot best practices and do not affect the application's behavior in production.
