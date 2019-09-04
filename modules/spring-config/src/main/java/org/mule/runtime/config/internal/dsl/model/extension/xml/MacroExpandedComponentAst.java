/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal.dsl.model.extension.xml;

import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.TypedComponentIdentifier.ComponentType;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.ast.api.ComponentAst;
import org.mule.runtime.ast.api.ComponentMetadataAst;

import java.util.Optional;
import java.util.Spliterator;
import java.util.stream.Stream;

public class MacroExpandedComponentAst implements ComponentAst {

  private final ComponentAst unexpanded;
  private final MacroExpansionModulesModel macroExpansionModel;

  public MacroExpandedComponentAst(ComponentAst unexpanded, MacroExpansionModulesModel macroExpansionModel) {
    this.unexpanded = unexpanded;
    this.macroExpansionModel = macroExpansionModel;
  }

  private Optional<ExtensionModel> macroExpandable1() {
    return macroExpansionModel.getSortedExtensions().stream()
        .filter(extModel -> unexpanded.getIdentifier().getNamespace().equals(extModel.getXmlDslModel().getPrefix()))
        .findFirst();
  }

  private boolean macroExpandable(ComponentAst comp) {
    return macroExpansionModel.getSortedExtensions().stream()
        .anyMatch(extModel -> comp.getIdentifier().getNamespace().equals(extModel.getXmlDslModel().getPrefix()));
  }

  @Override
  public Stream<ComponentAst> recursiveStream() {
    return unexpanded.recursiveStream()
        .flatMap(comp -> macroExpandable(comp)
            ? new MacroExpandedComponentAst(comp, macroExpansionModel).recursiveStream()
            : comp.recursiveStream());
  }

  @Override
  public Stream<ComponentAst> directChildrenStream() {
    return unexpanded.directChildrenStream()
        .map(comp -> macroExpandable(comp)
            ? new MacroExpandedComponentAst(comp, macroExpansionModel)
            : comp);
  }

  @Override
  public Spliterator<ComponentAst> recursiveSpliterator() {
    return recursiveStream().spliterator();
  }

  @Override
  public Spliterator<ComponentAst> directChildrenSpliterator() {
    return directChildrenStream().spliterator();
  }

  @Override
  public ComponentIdentifier getIdentifier() {
    return unexpanded.getIdentifier();
  }

  @Override
  public ComponentType getComponentType() {
    return unexpanded.getComponentType();
  }

  @Override
  public ComponentLocation getLocation() {
    return unexpanded.getLocation();
  }

  @Override
  public ComponentMetadataAst getMetadata() {
    return unexpanded.getMetadata();
  }

  @Override
  public Optional<String> getName() {
    return unexpanded.getName();
  }

  @Override
  public <M> Optional<M> getModel(Class<M> modelClass) {
    return unexpanded.getModel(modelClass);
  }

  @Override
  public Optional<String> getRawParameterValue(String paramName) {
    return unexpanded.getRawParameterValue(paramName);
  }

}
