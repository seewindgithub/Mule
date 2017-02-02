/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.capability.xml.description;

import static org.mule.runtime.extension.api.util.NameUtils.hyphenize;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.api.meta.model.declaration.fluent.ConfigurationDeclaration;
import org.mule.runtime.api.meta.model.declaration.fluent.ExtensionDeclaration;
import org.mule.runtime.extension.api.annotation.Configuration;
import org.mule.runtime.extension.api.annotation.Extension;

import java.util.List;
import java.util.Optional;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;

/**
 * {@link AbstractDescriptionDocumenter} implementation that picks a {@link ExtensionDeclaration} on which a {@link ExtensionModel}
 * has already been described.
 *
 * @since 4.0
 */
final class ExtensionDescriptionDocumenter extends AbstractDescriptionDocumenter<ExtensionDeclaration> {

  private final RoundEnvironment roundEnv;
  private final ConfigurationDescriptionDocumenter configDeclarer;
  private final OperationDescriptionDocumenter operationDeclarer;
  private final SourcesDescriptionDocumenter sourceDeclarer;

  ExtensionDescriptionDocumenter(ProcessingEnvironment processingEnvironment, RoundEnvironment roundEnvironment) {
    super(processingEnvironment);
    this.roundEnv = roundEnvironment;
    this.operationDeclarer = new OperationDescriptionDocumenter(processingEnv);
    this.sourceDeclarer = new SourcesDescriptionDocumenter(processingEnv);
    this.configDeclarer = new ConfigurationDescriptionDocumenter(processingEnvironment);
  }

  /**
   * Sets the description of the given {@link ExtensionDeclaration} and its inner configs and operations by extracting information
   * of the AST tree represented by {@code extensionElement} and {@code roundEnvironment}
   *
   * @param extensionDeclaration a {@link ExtensionDeclaration} on which configurations and operations have already been declared
   * @param extensionElement     a {@link TypeElement} generated by an annotation {@link Processor}
   */
  void document(ExtensionDeclaration extensionDeclaration, TypeElement extensionElement) {
    Extension annotation = extensionElement.getAnnotation(Extension.class);
    extensionDeclaration.setDescription(annotation.description());
    sourceDeclarer.document(extensionDeclaration, extensionElement);
    operationDeclarer.document(extensionDeclaration, extensionElement);
    documentConfigurations(extensionDeclaration, extensionElement);
  }

  private void documentConfigurations(ExtensionDeclaration declaration, TypeElement extensionElement) {
    List<ConfigurationDeclaration> configurations = declaration.getConfigurations();
    if (configurations.size() > 1) {
      processor.getTypeElementsAnnotatedWith(Configuration.class, roundEnv)
          .forEach(config -> findMatchingConfiguration(declaration, config)
              .ifPresent(configDeclaration -> configDeclarer.document(configDeclaration, config)));
    } else {
      configurations.forEach(config -> configDeclarer.document(config, extensionElement));
    }
  }

  private Optional<ConfigurationDeclaration> findMatchingConfiguration(ExtensionDeclaration declaration, TypeElement element) {
    List<ConfigurationDeclaration> configurations = declaration.getConfigurations();
    if (configurations.size() == 1) {
      return Optional.of(configurations.get(0));
    }
    return configurations.stream()
        .filter(config -> {
          Configuration configurationAnnotation = element.getAnnotation(Configuration.class);
          String name = config.getName();
          String annotationName = configurationAnnotation != null ? configurationAnnotation.name() : "";
          String defaultNaming = hyphenize(element.getSimpleName().toString().replace("Configuration", ""));
          return name.equals(defaultNaming) || name.equals(annotationName);
        })
        .findAny();
  }
}
