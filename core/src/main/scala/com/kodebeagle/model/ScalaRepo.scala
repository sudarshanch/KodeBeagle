/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kodebeagle.model

import com.kodebeagle.indexer._
import com.kodebeagle.javaparser.{SingleClassBindingResolver, JavaASTParser}
import com.kodebeagle.javaparser.JavaASTParser.ParseType
import com.kodebeagle.logging.Logger
import org.eclipse.jdt.core.dom.CompilationUnit

class ScalaRepo(baseRepo: GithubRepo) extends Repo with Logger
  with LazyLoadSupport {

  // TODO: This constants needs to go somewhere else
  private val SCALA_LANGUAGE = "java"

  override def files: Iterator[ScalaFileInfo] = {
    baseRepo.files.filter(_.extractLang().equalsIgnoreCase("java"))
      .map(new ScalaFileInfo(_))
  }

}

class ScalaFileInfo(baseFile: GithubFileInfo) extends FileInfo with LazyLoadSupport {

  assert(baseFile.fileName.endsWith(".scala"),
    s"A scala file is expected. Actual file: ${baseFile.fileName}")

  private var _searchableRefs: Option[Set[ExternalTypeReference]] = None

  def searchableRefs: Set[ExternalTypeReference] = {
    getOrCompute(_searchableRefs, () => {
      parse()
      _searchableRefs.get
    })
  }

  override def fileName: String = baseFile.fileName

  override def sloc: Int = baseFile.sloc

  override def fileContent: String = baseFile.fileContent

  override def language: String = baseFile.language

  override def repoId: Long = baseFile.repoId

  override def repoFileLocation: String = baseFile.repoFileLocation

  private def parse() = {
    // TODO: Make this as object
    val scalaExternalIndexer = new ScalaExternalTypeRefIndexer
    val score = baseFile.githubRepoInfo.stargazersCount
    val externalTypeRefs = scalaExternalIndexer.generateTypeRefs(repoId, score,
      repoFileLocation, fileContent, Set.empty)
    _searchableRefs = Option(externalTypeRefs)

  }

}
