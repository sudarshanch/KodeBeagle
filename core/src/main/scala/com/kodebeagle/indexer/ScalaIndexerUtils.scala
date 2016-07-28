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

import java.util.regex.Pattern

import com.kodebeagle.parser.TypeInFunction
import org.scalastyle.Lines

import scala.util.Try
import scalariform.utils.Range

trait ScalaIndexEntityHelper {
  protected def toLine(range: Range)(implicit lines: Lines): Option[ExternalLine] = {
    val offest = range.offset
    val maybeLineColumn = lines.toLineColumn(offest)
    maybeLineColumn.map { lineColumn =>
      val column: Int = lineColumn.column
      ExternalLine(lineColumn.line, column, column + range.length)
    }
  }

  protected def toProperty(prop: (String, List[Range]))(implicit lines: Lines): Prop = {
    Prop(prop._1, prop._2.flatMap(toLine(_)))
  }

  protected def toType(typeInFunction: TypeInFunction)(implicit lines: Lines): ExternalType

}

trait ScalaImportExtractor {

  protected def handleInternalImports(arrOfPacakgeClass: Array[(String, String)],
                                      packages: Set[String]): Set[(String, String)]

  def extractImports(source: String, packages: Set[String]): Set[(String, String)] = {
    val importPattern: Pattern = Pattern.compile("import (.*)\\.(\\w+)")
    val importLines = source.split("\n").filter(line => line.trim.startsWith("import") &&
      !line.trim.endsWith("._"))
    val groupedImports = importLines.filter(_.contains('{'))
    val absoluteImports = importLines.filterNot(_.contains('{'))
    val allImports = absoluteImports ++ groupedImports.flatMap(toSimpleImports)
    val arrOfPackageAndImport = allImports.map(importPattern.matcher(_)).filter(_.find)
      .flatMap(x => Try((x.group(1).trim, x.group(2).trim)).toOption)
    handleInternalImports(arrOfPackageAndImport, packages)
  }

  private def toSimpleImports(importClause: String) = {
    val indexOfOpenCurly = importClause.indexOf('{')
    val indexOfClosedCurly = importClause.indexOf('}')
    if (indexOfOpenCurly != -1 && indexOfClosedCurly != -1) {
      val importPrefix = importClause.substring(0, indexOfOpenCurly).trim
      val importSuffixes = importClause.substring(indexOfOpenCurly + 1, indexOfClosedCurly)
        .split(',').filterNot(_.contains("=>"))
      importSuffixes.map(importSuffix => importPrefix ++ importSuffix.trim)
    } else {
      Array[String]()
    }
  }
}
