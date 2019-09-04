/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal.dsl.model.extension.xml;

import org.mule.runtime.ast.api.ArtifactAst;
import org.mule.runtime.ast.api.ComponentAst;

import java.util.Spliterator;
import java.util.stream.Stream;

public class MacroExpandedArtifactAst implements ArtifactAst {

  private final ArtifactAst unexpanded;
  private final MacroExpansionModulesModel macroExpansionModel;

  public MacroExpandedArtifactAst(ArtifactAst unexpanded, MacroExpansionModulesModel macroExpansionModel) {
    this.unexpanded = unexpanded;
    this.macroExpansionModel = macroExpansionModel;
  }

  @Override
  public Stream<ComponentAst> recursiveStream() {
    return unexpanded.recursiveStream()
        .flatMap(comp -> macroExpandable(comp)
            ? new MacroExpandedComponentAst(comp, macroExpansionModel).recursiveStream()
            : comp.recursiveStream());
  }

  @Override
  public Stream<ComponentAst> topLevelComponentsStream() {
    return unexpanded.topLevelComponentsStream()
        .map(comp -> macroExpandable(comp)
            ? new MacroExpandedComponentAst(comp, macroExpansionModel)
            : comp);
  }

  private boolean macroExpandable(ComponentAst comp) {
    return macroExpansionModel.getSortedExtensions().stream()
        .anyMatch(extModel -> comp.getIdentifier().getNamespace().equals(extModel.getXmlDslModel().getPrefix()));
  }

  @Override
  public Spliterator<ComponentAst> recursiveSpliterator() {
    return recursiveStream().spliterator();
  }

  @Override
  public Spliterator<ComponentAst> topLevelComponentsSpliterator() {
    return topLevelComponentsStream().spliterator();
  }

}
