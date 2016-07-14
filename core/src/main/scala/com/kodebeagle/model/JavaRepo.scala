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
import com.kodebeagle.javaparser.JavaASTParser.ParseType
import com.kodebeagle.javaparser.{JavaASTParser, SingleClassBindingResolver}
import com.kodebeagle.logging.Logger
import org.eclipse.jdt.core.dom.CompilationUnit

class JavaRepo(baseRepo: GithubRepo) extends Repo with Logger
  with LazyLoadSupport {

  // TODO: This constants needs to go somewhere else
  private val JAVA_LANGUAGE = "java"

  override def files: List[JavaFileInfo] = {
    if (languages.contains(JAVA_LANGUAGE)) {
      baseRepo.files
        .filter(_.fileName.endsWith(".java"))
        .map(f => new JavaFileInfo(f))
    } else {
      Nil
    }
  }

  override def statistics: JavaRepoStatistics = new JavaRepoStatistics(baseRepo.statistics)

  override def languages: Set[String] = baseRepo.languages
}

class JavaFileInfo(baseFile: FileInfo) extends FileInfo with LazyLoadSupport {

  assert(baseFile.fileName.endsWith(".java"),
    s"A java file is expected. Actual file: ${baseFile.fileName}")

  private var _searchableRefs: Option[Set[ExternalTypeReference]] = None

  private var _fileMetaData: Option[FileMetaData] = None

  private var _imports: Option[Set[String]] = None

  def searchableRefs: Set[ExternalTypeReference] = {
    getOrCompute(_searchableRefs, () => {
      parse()
      _searchableRefs.get
    })
  }

  def fileMetaData: FileMetaData = {
    getOrCompute(_fileMetaData, () => {
      parse()
      _fileMetaData.get
    })
  }

  def imports: Set[String] = {
    getOrCompute(_imports, () => {
      parse()
      _imports.get
    })
  }

  override def fileName: String = baseFile.fileName

  override def sloc: Int = baseFile.sloc

  override def fileContent: String = baseFile.fileContent

  override def language: String = baseFile.language

  override def repoId: Int = baseFile.repoId

  override def repoFileLocation: String = baseFile.repoFileLocation

  def isTestFile(): Boolean = imports.exists(_.contains("org.junit"))

  /**
    * This method parses the java file and updates all that needs to be exposed by this class.
    *
    * The method is called lazily on when the data depending on parse is first requested, and
    * then all such data is computed and stored in the fields of the class.
    *
    * @return
    */
  private def parse() = {
    import scala.collection.JavaConversions._

    val parser: JavaASTParser = new JavaASTParser(true)
    val cu: CompilationUnit = parser.getAST(fileContent, ParseType.COMPILATION_UNIT).asInstanceOf
    val scbr: SingleClassBindingResolver = new SingleClassBindingResolver(cu)
    scbr.resolve()

    val nodeVsType = scbr.getTypesAtPosition
    // TODO: Get this properly
    val score = 0
    val externalTypeRefs = extractExtTypeRefs(scbr, cu, score)
    val fileMetaData = FileMetaDataIndexer.generateMetaData(scbr, cu, repoId, fileName)

    _imports = Option(scbr.getImports.toSet)
    _searchableRefs = Option(externalTypeRefs)
    _fileMetaData = Option(fileMetaData)
  }

  private def extractExtTypeRefs(scbr: SingleClassBindingResolver,
                                 cu: CompilationUnit, score: Int): Set[ExternalTypeReference] = {
    import scala.collection.JavaConversions._
    val externalTypeRefs = scbr.getMethodInvoks.values()
      // for every method in the class
      .map(invokList => {
      // group the method call by the type of object
      invokList.groupBy(_.getTargetType)
        // then map the grp to:
        .map { case (typ, grp) =>
        // 1. get list of locations where this type was invoked
        val lineList = grp.map(t => {
          val line = cu.getLineNumber(t.getLocation)
          val col = cu.getColumnNumber(t.getLocation)
          new ExternalLine(line, col, col + t.getLength)
        }).toList

        // 2. method names invoked of this type along with a list of their respective locations
        val propSet = grp.groupBy(_.getMethodName).map { case (prop, pgrp) =>
          new Property(prop, pgrp.map(t => {
            val line = cu.getLineNumber(t.getLocation)
            val col = cu.getColumnNumber(t.getLocation)
            // TODO: This seems redundant (??)
            new ExternalLine(line, col, col + t.getLength)
          }).toList)
        }.toSet

        new ExternalType(typ, lineList, propSet)

      }
    }.toSet)
      // Convert it back to a set of top level ExternalTypeReference object
      .map(extSet => new ExternalTypeReference(repoId, fileName, extSet, score))
      .toSet
    externalTypeRefs
  }

}

class JavaRepoStatistics(repoStatistics: RepoStatistics) extends RepoStatistics {

  override def sloc: Int = repoStatistics.sloc

  override def fileCount: Int = repoStatistics.fileCount

  override def size: Long = repoStatistics.size
}
