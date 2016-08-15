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

import java.io.{File, PrintWriter}

import com.kodebeagle.indexer.{Context, ContextType, TypeAggregator}
import com.kodebeagle.util.SparkIndexJobHelper
import org.scalatest.{BeforeAndAfterAll, FunSuite}

class JavaRepoTestSuite extends FunSuite with BeforeAndAfterAll with GitHubRepoMockSupport {
  import JavaRepoTestSuite._

  var testJavaRepo: Option[JavaRepo] = None

  override def beforeAll {
    testJavaRepo = mockRepo.map(new JavaRepo(_))
  }

  test("Number of java files check") {
    // Java file count
    assert(testJavaRepo.get.files.size == 5)
  }


  test("JavaFileInfo.isTestFile check") {
    val javaFileInfo = testJavaRepo.get.files.filter(
      file => file.fileName.equals("CollectLink.java")).next()
    assert(!javaFileInfo.isTestFile())
  }

  test("JavaFileInfo.imports check") {
    val javaFileInfo = testJavaRepo.get.files.filter(
      file => file.fileName.equals("CollectLink.java")).next()
    assert(javaFileInfo.imports.size == 15)
  }

  // scalastyle:off
  test("JavaFileInfo.fileMetaData check") {
    val file = testJavaRepo.get.files.filter(
      file => file.fileName.equals("CollectLink.java")).next()

    val fileMetaData = file.fileMetaData
    // Intention is to check whether fileMetaData presents.
    // The content that are available in fileMetaData will be validated
    // in the test suite related to FileMetaDataIndexer
    assert(fileMetaData.fileName.contains("CollectLink.java"))
    assert(fileMetaData.fileTypes.size > 0)
    assert(fileMetaData.externalRefList.nonEmpty)
    assert(fileMetaData.internalRefList.nonEmpty)

    val expectedTypes = """java.net.MalformedURLException;
                          |java.net.URL;
                          |java.util.HashSet;
                          |java.util.Set;
                          |java.util.concurrent.ArrayBlockingQueue;
                          |java.util.concurrent.BlockingQueue;
                          |java.util.regex.Matcher;
                          |java.util.regex.Pattern;
                          |org.openqa.selenium.By;
                          |org.openqa.selenium.Keys;
                          |org.openqa.selenium.WebDriver;
                          |org.openqa.selenium.WebElement;
                          |org.openqa.selenium.firefox.FirefoxDriver;
                          |org.openqa.selenium.interactions.Actions;""".stripMargin
      .split(";").map(_.trim)
    expectedTypes.foreach(e => assert(fileMetaData.externalRefList.map(_.fqt).contains(e)))
    /* val metaWriter = new PrintWriter(new File("/tmp/meta.txt"))
    metaWriter.write(SparkIndexJobHelper.toIndexTypeJson("java", "fileMetaData",
      fileMetaData, Option(file.repoFileLocation)) + "\n")
    metaWriter.close() */
    // println(file.fileContent)
  }
  // scalastyle:on

  test("JavaFileInfo.searchableRefs check for same package Refs") {
    val javaFileInfo = testJavaRepo.get.files.filter(
      file => file.fileName.equals("ScraperStartup.java")).next()

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
      file => file.fileName.equals("ScraperStartup.java")).next()
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
      file => file.fileName.equals("ScraperStartup.java")).next()
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

  test("test for type aggregations") {
    import scala.collection.JavaConversions._
    val typeAggs = testJavaRepo.get.files.map(_.typesInFile).flatMap(f => {
      val dTypes = f.declaredTypes.mapValues((Set.empty[String], _, f.repoName, f.fileName)).toSeq
      val uTypes = f.usedTypes.mapValues(v => (v._1, v._2, f.repoName, f.fileName)).toSeq
      dTypes ++ uTypes
    }).aggregate(new scala.collection.mutable.HashMap[String, TypeAggregator]())(
      (aggMap, value) => {
        val aggOpt = aggMap.get(value._1)
        aggOpt match {
          case Some(agg) => agg.merge(value._2._1, value._2._2, value._2._3, value._2._4)
          case None => aggMap.put(value._1,
            new TypeAggregator().merge(value._2._1, value._2._2, value._2._3, value._2._4))
        }
        aggMap
      },
      (aggMap1, aggMap2) => {
        aggMap2.foreach(e => {
          val valOpt = aggMap1.get(e._1)
          valOpt match {
            case Some(agg1) => {
              aggMap1.update(e._1, agg1.merge(e._2))
            }
            case None => {
              aggMap1.update(e._1, e._2)
            }
          }

        })
        aggMap1
      })


    import org.json4s._
    import org.json4s.jackson.Serialization
    implicit val formats = Serialization.formats(NoTypeHints)

    expectedTypes.foreach(e => assert(typeAggs.keys.contains(e)))
    /* print(s"Types used in the project: \n ${typeAggs.keys.mkString("\n")}\n")
    typeAggs.foreach(e => {
      print(s"\n\n For type: ${e._1}, aggregation summary is -- \n")
      print(s"\t ${write(e._2.result(e._1))} \n")
    }) */

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

object JavaRepoTestSuite {
  val expectedTypes = """com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException
                        |com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController
                        |com.gargoylesoftware.htmlunit.WebClient
                        |com.gargoylesoftware.htmlunit.WebRequest
                        |com.gargoylesoftware.htmlunit.html.HtmlDivision
                        |com.gargoylesoftware.htmlunit.html.HtmlPage
                        |com.gargoylesoftware.htmlunit.html.HtmlSpan
                        |com.pramati.scraper.util.FileUtil
                        |com.pramati.scraper.util.RecoveryUtil
                        |org.openqa.selenium.By
                        |org.openqa.selenium.WebDriver
                        |org.openqa.selenium.firefox.FirefoxDriver
                        |org.openqa.selenium.interactions.Actions
                        |java.net.MalformedURLException
                        |java.net.URL
                        |java.io.BufferedWriter
                        |java.io.File
                        |java.io.FileWriter
                        |java.io.IOException
                        |java.util.HashSet
                        |java.util.Scanner
                        |java.util.Set
                        |java.util.concurrent.BlockingQueue
                        |java.util.regex.Matcher
                        |java.util.regex.Pattern
                        |java.util.List
                        |""".stripMargin.split("\\n").toSet
}
