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

import java.util

import scala.collection.immutable

trait Line {
  def lineNumber: Int
  def startColumn: Int
  def endColumn: Int
}

trait Type {
  type T <: Line
  def typeName: String
  def lines: List[T]
  def properties: Set[Property]
}

abstract class TypeReference {
  type T <: Type
  def repoId: Int
  def file: String
  def types: Set[T]
  def score: Int
}


case class InternalTypeReference(repoId: Int, file: String,
                                 types: Set[InternalType],
                                 score: Int) extends TypeReference {
  type T = InternalType
}

case class ExternalTypeReference(repoId: Int, file: String,
                                 types: Set[ExternalType],
                                 score: Int) extends TypeReference {
  type T = ExternalType
}

case class InternalType(typeName: String, lines: List[InternalLine],
                        properties: Set[Property]) extends Type {
  type T = InternalLine
}

case class ExternalType(typeName: String, lines: List[ExternalLine],
                        properties: Set[Property]) extends Type {
  type T = ExternalLine
}

case class InternalLine(line: String, lineNumber: Int,
                        startColumn: Int, endColumn: Int) extends Line

case class ExternalLine(lineNumber: Int, startColumn: Int, endColumn: Int) extends Line

case class Property(propertyName: String, lines: List[Line])

case class Token(importName: String, importExactName: String,
                 lineNumbers: immutable.Set[ExternalLine])


case class SourceFile(repoId: Int, fileName: String, fileContent: String)

case class RepoFileNameInfo(login: String, id: Int, name: String, fork: Boolean, language: String,
                            defaultBranch: String, stargazersCount: Int)

case class Repository(login: String, id: Int, name: String, fork: Boolean, language: String,
                      defaultBranch: String, stargazersCount: Int, sloc: Int, fileCount: Int,
                      size: Long)

case class Statistics(sloc: Int, fileCount: Int, size: Long)

/** For testing */
object Repository {
  def invalid: Repository =
    Repository("n-a", -1, "n-a", fork = false, "Java", "n-a", 0, -1, -1, -1)
}

/* File Metadata related entities */
case class RepoSource(repoId: Long, fileName: String, fileContent: String)

case class TypeDeclaration(fileType: String, loc: String)

case class ExternalRef(id: Int, fqt: String)

case class VarTypeLocation(loc: String, id: Int)

case class MethodTypeLocation(loc: String, id: Int, method: String, argTypes: List[String])

case class MethodDefinition(loc: String, method: String, argTypes: List[String])

case class InternalRef(childLine: String, parentLine: String)

case class SuperTypes(superClass: String, interfaces: List[String])

case class FileMetaData(repoId: Long, fileName: String, superTypes: SuperTypes,
                        fileTypes: util.List[TypeDeclaration],
                        externalRefList: List[ExternalRef],
                        typeLocationList: List[VarTypeLocation],
                        methodTypeLocation: List[MethodTypeLocation],
                        methodDefinitionList: List[MethodDefinition],
                        internalRefList: List[InternalRef])





