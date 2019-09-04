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

public class MacroexpandedArtifactAst implements ArtifactAst {

  private final ArtifactAst unexpanded;

  public MacroexpandedArtifactAst(ArtifactAst unexpanded) {
    this.unexpanded = unexpanded;
  }

  @Override
  public Stream<ComponentAst> recursiveStream() {
    return unexpanded.recursiveStream()
        .flatMap(comp -> new MacroexpandedComponentAst(comp).recursiveStream());
  }

  @Override
  public Spliterator<ComponentAst> recursiveSpliterator() {
    return recursiveStream().spliterator();
  }

  @Override
  public Stream<ComponentAst> topLevelComponentsStream() {
    return unexpanded.topLevelComponentsStream()
        .map(comp -> new MacroexpandedComponentAst(comp));
  }

  @Override
  public Spliterator<ComponentAst> topLevelComponentsSpliterator() {
    return topLevelComponentsStream().spliterator();
  }

}
