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
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

import scala.util.Try
import scalariform.utils.Range

class ScalaInternalTypeRefIndexer extends ScalaTypeRefIndexer {

  type IntTypeRef = InternalTypeReference

  override protected def handleInternalImports(arrPackageImport: Array[(String, String)],
                                               packages: Set[String]): Set[(String, String)] = {
    arrPackageImport.filter { case (left, right) => packages.contains(left) }.toSet
  }

  def generateTypeReferences(file: (String, String),
                             packages: List[String],
                             repo: Option[Repository]): Set[TypeReference] = {
    val indexEntries = ListBuffer[TypeReference]()
    val repository = repo.getOrElse(Repository.invalid)
    val (fileName, fileContent) = file
    log.info(s"FileName>>> $fileName")
    val imports = extractImports(fileContent, packages.toSet)
    val mayBeLines = Try(Checker.parseLines(fileContent))
    if (mayBeLines.isSuccess) {
      val absoluteFileName = JavaFileIndexerHelper.fileNameToURL(repository, fileName)
      implicit val lines = mayBeLines.get
      val listOfListOfType = toListOfListOfType(ScalaParser.parse(fileContent, imports))
      indexEntries ++= listOfListOfType.map(listOfType =>
        InternalTypeReference(repository.id, absoluteFileName,
          listOfType.asInstanceOf[List[InternalType]].toSet,
          repository.stargazersCount))
    }
    indexEntries.filter(_.types.nonEmpty).toSet
  }

  override protected def toLine(range: Range)(implicit pLines: Lines): Option[InternalLine] = {
    val mayBeLine = super.toLine(range)
    mayBeLine.flatMap { line =>
      pLines.findLineAndIndex(range.offset).map(lineIndex =>
        InternalLine(lineIndex._1.text, line.lineNumber, line.startColumn, line.endColumn))
    }
  }

  override protected def toType(typeInFunction: TypeInFunction)
                               (implicit pLines: Lines): InternalType = {
    val typeName = typeInFunction.typeName
    val lines = typeInFunction.ranges.flatMap(toLine(_))
    val properties = typeInFunction.props.map(toProperty)
    InternalType(typeName, lines, properties)
  }
}
