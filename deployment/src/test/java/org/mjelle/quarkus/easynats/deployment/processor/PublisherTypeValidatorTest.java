package org.mjelle.quarkus.easynats.deployment.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for PublisherTypeValidator build-time type validation logic. */
@DisplayName("PublisherTypeValidator Unit Tests")
class PublisherTypeValidatorTest {

  private IndexView index;
  private PublisherTypeValidator validator;

  @BeforeEach
  void setUp() {
    index = mock(IndexView.class);
    validator = new PublisherTypeValidator(index);
  }

  @Test
  @DisplayName("validate rejects null type")
  void testValidateNullType() {
    PublisherTypeValidator.ValidationResult result = validator.validate(null);

    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorMessage()).contains("Type cannot be null");
  }

  @Test
  @DisplayName("validate rejects primitive types")
  void testValidatePrimitiveType() {
    Type primitiveType = mock(Type.class);
    when(primitiveType.kind()).thenReturn(Type.Kind.PRIMITIVE);
    when(primitiveType.name()).thenReturn(DotName.createSimple("int"));

    PublisherTypeValidator.ValidationResult result = validator.validate(primitiveType);

    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorMessage()).contains("Primitive type 'int' is not supported");
    assertThat(result.getErrorMessage()).contains("IntValue");
  }

  @Test
  @DisplayName("validate rejects array types")
  void testValidateArrayType() {
    Type arrayType = mock(Type.class);
    Type.ArrayType arrayTypeImpl = mock(Type.ArrayType.class);
    Type componentType = mock(Type.class);

    when(arrayType.kind()).thenReturn(Type.Kind.ARRAY);
    when(arrayType.asArrayType()).thenReturn(arrayTypeImpl);
    when(arrayTypeImpl.constituent()).thenReturn(componentType);
    when(componentType.name()).thenReturn(DotName.createSimple("java.lang.String"));

    PublisherTypeValidator.ValidationResult result = validator.validate(arrayType);

    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorMessage()).contains("Array type");
    assertThat(result.getErrorMessage()).contains("not supported");
  }

  @Test
  @DisplayName("validate accepts class with no-arg constructor")
  void testValidateClassWithNoArgConstructor() {
    Type classType = mock(Type.class);
    DotName className = DotName.createSimple("com.example.MyClass");
    ClassInfo classInfo = mock(ClassInfo.class);
    MethodInfo constructor = mock(MethodInfo.class);

    when(classType.kind()).thenReturn(Type.Kind.CLASS);
    when(classType.name()).thenReturn(className);
    when(index.getClassByName(className)).thenReturn(classInfo);
    when(classInfo.superName()).thenReturn(DotName.createSimple("java.lang.Object"));
    when(classInfo.methods()).thenReturn(List.of(constructor));
    when(constructor.name()).thenReturn("<init>");
    when(constructor.parametersCount()).thenReturn(0);

    PublisherTypeValidator.ValidationResult result = validator.validate(classType);

    assertThat(result.isValid()).isTrue();
  }

  @Test
  @DisplayName("validate rejects class without no-arg constructor")
  void testValidateClassWithoutNoArgConstructor() {
    Type classType = mock(Type.class);
    DotName className = DotName.createSimple("com.example.MyClass");
    ClassInfo classInfo = mock(ClassInfo.class);
    MethodInfo constructor = mock(MethodInfo.class);

    when(classType.kind()).thenReturn(Type.Kind.CLASS);
    when(classType.name()).thenReturn(className);
    when(index.getClassByName(className)).thenReturn(classInfo);
    when(classInfo.superName()).thenReturn(DotName.createSimple("java.lang.Object"));
    when(classInfo.methods()).thenReturn(List.of(constructor));
    when(constructor.name()).thenReturn("<init>");
    when(constructor.parametersCount()).thenReturn(1); // Has parameter

    PublisherTypeValidator.ValidationResult result = validator.validate(classType);

    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorMessage()).contains("no-arg constructor");
  }

  @Test
  @DisplayName("validate accepts record types without no-arg constructor")
  void testValidateRecordType() {
    Type recordType = mock(Type.class);
    DotName recordName = DotName.createSimple("com.example.MyRecord");
    ClassInfo classInfo = mock(ClassInfo.class);

    when(recordType.kind()).thenReturn(Type.Kind.CLASS);
    when(recordType.name()).thenReturn(recordName);
    when(index.getClassByName(recordName)).thenReturn(classInfo);
    when(classInfo.superName()).thenReturn(DotName.createSimple("java.lang.Record"));
    when(classInfo.methods()).thenReturn(Collections.emptyList());

    PublisherTypeValidator.ValidationResult result = validator.validate(recordType);

    assertThat(result.isValid()).isTrue();
  }

  @Test
  @DisplayName("validate accepts class not in index (external dependency)")
  void testValidateExternalClass() {
    Type classType = mock(Type.class);
    DotName className = DotName.createSimple("com.external.Library");

    when(classType.kind()).thenReturn(Type.Kind.CLASS);
    when(classType.name()).thenReturn(className);
    when(index.getClassByName(className)).thenReturn(null); // Not in index

    PublisherTypeValidator.ValidationResult result = validator.validate(classType);

    assertThat(result.isValid()).isTrue();
  }

  @Test
  @DisplayName("validate recursively validates parameterized type arguments")
  void testValidateParameterizedTypeWithInvalidArgument() {
    Type parameterizedType = mock(Type.class);
    ParameterizedType paramType = mock(ParameterizedType.class);
    Type arrayArgument = mock(Type.class);
    Type.ArrayType arrayTypeImpl = mock(Type.ArrayType.class);
    Type componentType = mock(Type.class);

    when(parameterizedType.kind()).thenReturn(Type.Kind.PARAMETERIZED_TYPE);
    when(parameterizedType.asParameterizedType()).thenReturn(paramType);
    when(paramType.name()).thenReturn(DotName.createSimple("java.util.List"));
    when(paramType.arguments()).thenReturn(List.of(arrayArgument));

    // The argument is an array type (invalid)
    when(arrayArgument.kind()).thenReturn(Type.Kind.ARRAY);
    when(arrayArgument.asArrayType()).thenReturn(arrayTypeImpl);
    when(arrayTypeImpl.constituent()).thenReturn(componentType);
    when(componentType.name()).thenReturn(DotName.createSimple("java.lang.String"));

    PublisherTypeValidator.ValidationResult result = validator.validate(parameterizedType);

    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorMessage()).contains("Array type");
  }

  @Test
  @DisplayName("validate accepts standard collection with valid type argument")
  void testValidateStandardCollectionWithValidArgument() {
    Type parameterizedType = mock(Type.class);
    ParameterizedType paramType = mock(ParameterizedType.class);
    Type stringType = mock(Type.class);
    DotName stringName = DotName.createSimple("java.lang.String");

    when(parameterizedType.kind()).thenReturn(Type.Kind.PARAMETERIZED_TYPE);
    when(parameterizedType.asParameterizedType()).thenReturn(paramType);
    when(paramType.name()).thenReturn(DotName.createSimple("java.util.List"));
    when(paramType.arguments()).thenReturn(List.of(stringType));

    // String type argument (valid - external class)
    when(stringType.kind()).thenReturn(Type.Kind.CLASS);
    when(stringType.name()).thenReturn(stringName);
    when(index.getClassByName(stringName)).thenReturn(null); // External

    PublisherTypeValidator.ValidationResult result = validator.validate(parameterizedType);

    assertThat(result.isValid()).isTrue();
  }

  @Test
  @DisplayName("validate rejects type variables")
  void testValidateTypeVariable() {
    Type typeVariable = mock(Type.class);

    when(typeVariable.kind()).thenReturn(Type.Kind.TYPE_VARIABLE);
    when(typeVariable.toString()).thenReturn("T");

    PublisherTypeValidator.ValidationResult result = validator.validate(typeVariable);

    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorMessage()).contains("Unresolvable generic type");
    assertThat(result.getErrorMessage()).contains("concrete type");
  }

  @Test
  @DisplayName("validate rejects wildcard types")
  void testValidateWildcardType() {
    Type wildcardType = mock(Type.class);

    when(wildcardType.kind()).thenReturn(Type.Kind.WILDCARD_TYPE);
    when(wildcardType.toString()).thenReturn("?");

    PublisherTypeValidator.ValidationResult result = validator.validate(wildcardType);

    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorMessage()).contains("Unresolvable generic type");
  }
}
