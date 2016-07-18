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

import java.io.File

import com.kodebeagle.configuration.KodeBeagleConfig
import com.kodebeagle.indexer.{ExternalType, ExternalTypeReference}
import org.apache.commons.io.FileUtils
import org.apache.hadoop.conf.Configuration
import org.scalatest.{BeforeAndAfterAll, FunSuite}

class JavaRepoTestSuite extends FunSuite with BeforeAndAfterAll {

  var testJavaRepo: Option[JavaRepo] = None

  override def beforeAll {
    import sys.process._
    FileUtils.copyFileToDirectory(
      new File(Thread.currentThread.
        getContextClassLoader.getResource("GitRepoTest-git.tar.gz").getPath),
      new File(s"${KodeBeagleConfig.repoCloneDir}/testlogin/testgitrepo"))

    s"""tar -xvf ${KodeBeagleConfig.repoCloneDir}/testlogin/testgitrepo/GitRepoTest-git.tar.gz
        |-C ${KodeBeagleConfig.repoCloneDir}/testlogin/testgitrepo""".stripMargin.!!

    val githubRepo = new MockedGithubRepo().init(new Configuration, "testlogin/testgitrepo")
    testJavaRepo = Option(new JavaRepo(githubRepo))
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
      file => file.fileName.equals("CollectLink.java"))(0)
    assert(!javaFileInfo.isTestFile())
  }

  test("JavaFileInfo.imports check") {
    val javaFileInfo = testJavaRepo.get.files.filter(
      file => file.fileName.equals("CollectLink.java"))(0)
    assert(javaFileInfo.imports.size == 15)
  }

  test("JavaFileInfo.fileMetaData check") {
    val javaFileInfo = testJavaRepo.get.files.filter(
      file => file.fileName.equals("CollectLink.java"))(0)
    // Intention is to check whether fileMetaData presents.
    // The content that are available in fileMetaData will be validated
    // in the test suite related to FileMetaDataIndexer
    assert(javaFileInfo.fileMetaData.fileName.equals("CollectLink.java"))
    assert(javaFileInfo.fileMetaData.methodTypeLocation.size > 0)
    assert(javaFileInfo.fileMetaData.fileTypes.size > 0)
    assert(javaFileInfo.fileMetaData.externalRefList.size > 0)
    assert(javaFileInfo.fileMetaData.internalRefList.size > 0)
  }

  test("JavaFileInfo.searchableRefs check for same package Refs") {
    val javaFileInfo = testJavaRepo.get.files.filter(
      file => file.fileName.equals("ScraperStartup.java"))(0)

    val searchableRefs = javaFileInfo.searchableRefs
    val externalRef = searchableRefs.head;
    // Number of methods
    assert(searchableRefs.size == 1)
    assert(externalRef.types.size == 3)
    // For same package ExternalType
    val externalType = getExternalType("com.pramati.scraper.google_grp_scraper.CollectLink",
      externalRef)

    assert(externalType.properties.size == 3)
    assert(externalType.lines.size == 3)
  }

  test("JavaFileInfo.searchableRefs check for java.lang package Refs") {
    val javaFileInfo = testJavaRepo.get.files.filter(
      file => file.fileName.equals("ScraperStartup.java"))(0)
    val searchableRefs = javaFileInfo.searchableRefs
    val externalRef = searchableRefs.head;
    // Number of methods
    assert(searchableRefs.size == 1)
    assert(externalRef.types.size == 3)
    // For java.lang package ExternalType
    val externalType = getExternalType("java.lang.Integer", externalRef)
    assert(externalType.properties.size == 1)
    assert(externalType.lines.size == 1)
  }

  test("JavaFileInfo.searchableRefs check for external package Refs") {
    val javaFileInfo = testJavaRepo.get.files.filter(
      file => file.fileName.equals("ScraperStartup.java"))(0)
    val searchableRefs = javaFileInfo.searchableRefs
    val externalRef = searchableRefs.head;
    // Number of methods
    assert(searchableRefs.size == 1)
    assert(externalRef.types.size == 3)
    // For java.net.URL package ExternalType
    val externalType = getExternalType("java.net.URL", externalRef)
    assert(externalType.properties.size == 1)
    assert(externalType.lines.size == 1)
  }

  private def getExternalType(extType: String,
                              externalRef: ExternalTypeReference): ExternalType = {
    var externalType: Option[ExternalType] = None
    try {
      externalType = Option(externalRef.types.filter(
        t => extType.equals(t.typeName)).head)
    } catch {
      case e: Exception => {
        fail("Expected type java.lang.Integer is not found in" +
          " ScraperStartup.java")
      }
    }
    externalType.get
  }
}
