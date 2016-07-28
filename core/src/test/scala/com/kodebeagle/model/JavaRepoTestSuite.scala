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

import com.kodebeagle.indexer.{Context, ContextType}
import org.scalatest.{BeforeAndAfterAll, FunSuite}

class JavaRepoTestSuite extends FunSuite with BeforeAndAfterAll with GitHubRepoMockSupport {

  var testJavaRepo: Option[JavaRepo] = None

  override def beforeAll {
    testJavaRepo = mockRepo.map(new JavaRepo(_))
  }

  test("Number of java files check") {
    // Java file count
    assert(testJavaRepo.get.files.size == 5)
  }
  // Total file count
  // assert(testJavaRepo.get.statistics.fileCount == 7)

  test("Languages check") {
    // Check for number of languages
    assert(testJavaRepo.get.languages.size == 3)
    // Check for Java language
    assert(testJavaRepo.get.languages.contains("java"))
  }

  test("Statistics Check") {
    val repoStatistics: JavaRepoStatistics = testJavaRepo.get.statistics
    assert(repoStatistics.fileCount == 7)
    assert(repoStatistics.size == 13728)
    assert(repoStatistics.sloc == 463)
  }

  test("JavaFileInfo.isTestFile check") {
    val javaFileInfo = testJavaRepo.get.files.filter(
      file => file.fileName.equals("CollectLink.java")).head
    assert(!javaFileInfo.isTestFile())
  }

  test("JavaFileInfo.imports check") {
    val javaFileInfo = testJavaRepo.get.files.filter(
      file => file.fileName.equals("CollectLink.java")).head
    assert(javaFileInfo.imports.size == 15)
  }

  // scalastyle:off
  test("JavaFileInfo.fileMetaData check") {
    val javaFileInfo = testJavaRepo.get.files.filter(
      file => file.fileName.equals("CollectLink.java")).head
    // Intention is to check whether fileMetaData presents.
    // The content that are available in fileMetaData will be validated
    // in the test suite related to FileMetaDataIndexer
    assert(javaFileInfo.fileMetaData.fileName.equals("CollectLink.java"))
    assert(javaFileInfo.fileMetaData.methodTypeLocation.nonEmpty)
    assert(javaFileInfo.fileMetaData.fileTypes.size > 0)
    assert(javaFileInfo.fileMetaData.externalRefList.nonEmpty)
    assert(javaFileInfo.fileMetaData.internalRefList.nonEmpty)
  }
  // scalastyle:on

  test("JavaFileInfo.searchableRefs check for same package Refs") {
    val javaFileInfo = testJavaRepo.get.files.filter(
      file => file.fileName.equals("ScraperStartup.java")).head

    val searchableRefs = javaFileInfo.searchableRefs
    val externalRef = searchableRefs.contexts.head
    // Number of methods
    assert(searchableRefs.contexts.size == 1)
    assert(externalRef.types.size == 3)
    // For same package ExternalType
    val typeInContext = getTypeInContext("com.pramati.scraper.google_grp_scraper.CollectLink",
      externalRef)

    assert(typeInContext.props.size == 3)
    // TODO context does not contains lines assert(externalType.lines.size == 1)
  }

  test("JavaFileInfo.searchableRefs check for java.lang package Refs") {
    val javaFileInfo = testJavaRepo.get.files.filter(
      file => file.fileName.equals("ScraperStartup.java")).head
    val searchableRefs = javaFileInfo.searchableRefs
    val context = searchableRefs.contexts.head;
    // Number of methods
    assert(searchableRefs.contexts.size == 1)
    assert(context.types.size == 3)
    // For java.lang package ExternalType
    val typeInContext = getTypeInContext("java.lang.Integer", context)
    assert(typeInContext.props.size == 1)
    // TODO context does not contains lines assert(externalType.lines.size == 1)
  }

  test("JavaFileInfo.searchableRefs check for external package Refs") {
    val javaFileInfo = testJavaRepo.get.files.filter(
      file => file.fileName.equals("ScraperStartup.java"))(0)
    val searchableRefs = javaFileInfo.searchableRefs
    val context = searchableRefs.contexts.head
    // Number of methods
    assert(searchableRefs.contexts.size == 1)
    assert(context.types.size == 3)
    // For java.net.URL package ExternalType
    val typeInContext = getTypeInContext("java.net.URL", context)
    assert(typeInContext.props.size == 1)
    // TODO context does not contains lines assert(externalType.lines.size == 1)
  }

  private def getTypeInContext(extType: String,
                              context: Context): ContextType = {
    var typeInContext: Option[ContextType] = None
    try {
      typeInContext = Option(context.types.filter(
        t => extType.equals(t.name)).head)
    } catch {
      case e: Exception => {
        fail("Expected type java.lang.Integer is not found in" +
          " ScraperStartup.java")
      }
    }
    typeInContext.get
  }
}
