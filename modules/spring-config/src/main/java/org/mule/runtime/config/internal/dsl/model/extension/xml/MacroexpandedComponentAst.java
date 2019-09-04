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
import org.mule.runtime.ast.api.ComponentAst;
import org.mule.runtime.ast.api.ComponentMetadataAst;

import java.util.Optional;
import java.util.Spliterator;
import java.util.stream.Stream;

public class MacroexpandedComponentAst implements ComponentAst {

  private final ComponentAst unexpanded;

  public MacroexpandedComponentAst(ComponentAst unexpanded) {
    this.unexpanded = unexpanded;
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

  @Override
  public Stream<ComponentAst> recursiveStream() {
    return unexpanded.recursiveStream();
  }

  @Override
  public Spliterator<ComponentAst> recursiveSpliterator() {
    return unexpanded.recursiveSpliterator();
  }

  @Override
  public Stream<ComponentAst> directChildrenStream() {
    return unexpanded.directChildrenStream();
  }

  @Override
  public Spliterator<ComponentAst> directChildrenSpliterator() {
    return unexpanded.directChildrenSpliterator();
  }


}
