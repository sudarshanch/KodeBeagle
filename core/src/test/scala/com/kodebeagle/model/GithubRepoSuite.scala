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
import org.apache.commons.io.FileUtils
import org.apache.hadoop.conf.Configuration
import org.scalatest.{BeforeAndAfterAll, FunSuite}

class GithubRepoSuite extends FunSuite with BeforeAndAfterAll with GitHubRepoMockSupport {

  var repo: Option[GithubRepo] = None

  override def beforeAll {
    repo = mockRepo
  }

  test("getting files from repository") {
    val files = repo.get.files
    assert(files.size == 7)
  }

  test("getting language from repository") {
    val languages: Set[String] = repo.get.languages
    assert(languages.size == 3)

    val expectedLanguages = Set("md", "java", "xml")

    assert(languages.sameElements(expectedLanguages))
  }

  test("getting statistics from repository") {
    val repoStatistics: RepoStatistics = repo.get.statistics
    assert(repoStatistics.fileCount == 7
      && repoStatistics.sloc == 463 && repoStatistics.size == 13728)
  }

  test("test GithubFileInfo.extractFileName") {
    val files = repo.get.files
    val githubFileInfo = files.filter(file => file.filePath.contains("CollectLink.java"))(0)
    assert(githubFileInfo.extractFileName().equals("CollectLink.java"))
  }

  test("test GithubFileInfo.readFileContent") {
    val files = repo.get.files
    val githubFileInfo = files.filter(file => file.filePath.contains("CollectLink.java"))(0)
    assert(githubFileInfo.
      readFileContent.contains("package com.pramati.scraper.google_grp_scraper"))
  }

  test("test GithubFileInfo.extractLang") {
    val files = repo.get.files
    val githubFileInfo = files.filter(file => file.filePath.contains("CollectLink.java"))(0)
    assert(githubFileInfo.extractLang.equals("java"))
  }

  // scalastyle:off
  test("test GithubFileInfo.repoFileLocation") {
    val files = repo.get.files
    val githubFileInfo = files.filter(file => file.filePath.contains("CollectLink.java"))(0)
    assert(githubFileInfo.repoFileLocation.
      equals("himukr/google-grp-scraper/blob/master/src/main/java/com/pramati/scraper/google_grp_scraper/CollectLink.java"))
  }
  // scalastyle:on
}

trait GitHubRepoMockSupport {
  def mockRepo: Option[GithubRepo] = {
    import sys.process._
    FileUtils.copyFileToDirectory(
      new File(Thread.currentThread.
        getContextClassLoader.getResource("GitRepoTest-git.tar.gz").getPath),
      new File(s"${KodeBeagleConfig.repoCloneDir}/himukr/google-grp-scraper"))

    s"""tar -xvf ${KodeBeagleConfig.repoCloneDir}/himukr/google-grp-scraper/GitRepoTest-git.tar.gz
        |-C ${KodeBeagleConfig.repoCloneDir}/himukr/google-grp-scraper""".stripMargin.!!

    Option(new MockedGithubRepo().init(new Configuration, "himukr/google-grp-scraper"))
  }
}
