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

import com.kodebeagle.logging.Logger
import com.kodebeagle.parser.TypeInFunction
import org.scalastyle.Lines

trait ScalaTypeRefIndexer extends ScalaImportExtractor
  with ScalaIndexEntityHelper with Logger with Serializable {

  protected def generateTypeReferences(file: (String, String),
                                       packages: List[String],
                                       repo: Option[Repository]): Set[ExternalTypeReference]

  protected def toListOfListOfType(listOfListOfTypeInFunction: List[List[TypeInFunction]])
                                  (implicit lines: Lines) = {
    listOfListOfTypeInFunction.map(listOfTypeInFunction =>
      listOfTypeInFunction.map(typeInFunction => toType(typeInFunction)).filter(typeRef =>
        typeRef.typeName.nonEmpty && typeRef.lines.nonEmpty && typeRef.properties.nonEmpty))
  }

  protected def fileNameToURL(repo: Repository, f: String): String = {
    val (_, actualFileName) = f.splitAt(f.indexOf('/'))
    s"""${repo.login}/${repo.name}/blob/${repo.defaultBranch}$actualFileName"""
  }
}

