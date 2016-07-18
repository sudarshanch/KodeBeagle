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

package com.kodebeagle.indexer

import com.kodebeagle.parser.{ScalaParser, TypeInFunction}
import org.scalastyle.{Checker, Lines}

import scala.collection.mutable.ListBuffer
import scala.util.Try

class ScalaExternalTypeRefIndexer extends ScalaTypeRefIndexer {

  type ExtTypeRef = ExternalTypeReference

  def generateTypeRefs(repoId: Long, score: Long,
                             filePath: String, fileContent: String,
                             packages: Set[String]): Set[ExternalTypeReference] = {

    val indexEntries = ListBuffer[ExternalTypeReference]()
    log.info(s"File analyzed is >>> $filePath")
    val imports = extractImports(fileContent, packages)
    val mayBeLines = Try(Checker.parseLines(fileContent))
    if (mayBeLines.isSuccess) {
      implicit val lines = mayBeLines.get
      val listOfListOfType = toListOfListOfType(ScalaParser.parse(fileContent, imports))
      indexEntries ++ listOfListOfType.map(listOfType =>
        ExternalTypeReference(repoId, filePath,
          listOfType.asInstanceOf[List[ExternalType]].toSet,
          score))
    }
    indexEntries.filter(_.types.nonEmpty).toSet
  }

  def generateTypeReferences(file: (String, String),
                             packages: List[String],
                             repo: Option[Repository]): Set[ExternalTypeReference] = {
    val repository = repo.getOrElse(Repository.invalid)
    val repoId = repository.id
    val score = repository.stargazersCount
    val (fileName, fileContent) = file
    val filePath = fileNameToURL(repository, fileName)
    generateTypeRefs(repoId, score, filePath, fileContent, packages.toSet)
  }

  override protected def handleInternalImports(arrOfPackageClass: Array[(String, String)],
                                               packages: Set[String]): Set[(String, String)] = {
    arrOfPackageClass.filterNot { case (left, right) => packages.contains(left) }.toSet
  }

  override protected def toType(typeInFunction: TypeInFunction)
                               (implicit pLines: Lines): ExternalType = {
    val typeName = typeInFunction.typeName
    val lines = typeInFunction.ranges.flatMap(toLine(_))
    val properties = typeInFunction.props.map(toProperty)
    ExternalType(typeName, lines.asInstanceOf[List[ExternalLine]], properties)
  }
}
