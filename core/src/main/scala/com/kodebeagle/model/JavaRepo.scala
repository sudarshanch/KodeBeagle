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

import java.util

import com.kodebeagle.indexer._
import com.kodebeagle.javaparser.JavaASTParser.ParseType
import com.kodebeagle.javaparser.{JavaASTParser, SingleClassBindingResolver}
import com.kodebeagle.logging.Logger
import org.eclipse.jdt.core.dom.CompilationUnit

import scala.util.Try

class JavaRepo(val baseRepo: GithubRepo) extends Repo with Logger
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

class JavaFileInfo(baseFile: GithubFileInfo) extends FileInfo with LazyLoadSupport with Logger {

  assert(baseFile.fileName.endsWith(".java"),
    s"A java file is expected. Actual file: ${baseFile.fileName}")

  private var _searchableRefs: Option[Set[ExternalTypeReference]] = None

  private var _fileMetaData: Option[FileMetaData] = None

  private var _imports: Option[Set[String]] = None

  private var _repoPath: Option[String] = None

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

  def repoPath: String = {
    getOrCompute(_repoPath, () => {
      parse()
      _repoPath.get
    })
  }

  override def fileName: String = baseFile.fileName

  override def sloc: Int = baseFile.sloc

  override def fileContent: String = baseFile.fileContent

  override def language: String = baseFile.language

  override def repoId: Long = baseFile.repoId

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
    _repoPath= Option(s"${baseFile.githubRepoInfo.login}/${baseFile.githubRepoInfo.name}")

    // The file may not even be well formed, so the parser may throw an
    // IllegalArgumentException. Need to handle such a case.
    val cu: Option[CompilationUnit] = Try {
      parser.getAST(fileContent, ParseType.COMPILATION_UNIT)
        .asInstanceOf[CompilationUnit]
    }.toOption

    if (!cu.isDefined) {
      import collection.JavaConverters._
      log.error(s"Compilation unit is null for ${fileName} .")
      _imports = Option(Set.empty)
      _searchableRefs = Option(Set.empty)
      _fileMetaData = Option(new FileMetaData(repoId, fileName,
        new SuperTypes(Map("java.lang.Object"->"java.lang.Object"),
          Map("java.lang.Object"->List.empty.asJava)),
        List.empty[TypeDeclaration], List.empty, List.empty, List.empty,
        List.empty, List.empty))
    } else {
      val scbr: SingleClassBindingResolver = new SingleClassBindingResolver(cu.get)
      scbr.resolve()

      val nodeVsType = scbr.getTypesAtPosition
      val score = baseFile.githubRepoInfo.stargazersCount
      val externalTypeRefs = extractExtTypeRefs(scbr, cu.get, score)
      val fileMetaData = FileMetaDataIndexer.generateMetaData(scbr, cu.get, repoId, fileName)

      _imports = Option(scbr.getImports.toSet)
      _searchableRefs = Option(externalTypeRefs)
      _fileMetaData = Option(fileMetaData)
    }

  }

  private def extractExtTypeRefs(scbr: SingleClassBindingResolver,
                                 cu: CompilationUnit, score: Long): Set[ExternalTypeReference] = {
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
