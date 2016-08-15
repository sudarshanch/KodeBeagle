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
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.{RevTree, RevWalk}
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

  // scalastyle:off
  test("repo git history aggregation") {
    val repository = repo.get.repository
    val git = new Git(repository)
    val revWalk: RevWalk = new RevWalk(repository)

    // find the HEAD
    val headCommitId = repository.resolve(Constants.HEAD)
    val headTree: RevTree = revWalk.parseCommit(headCommitId).getTree
    revWalk.markStart(revWalk.lookupCommit(headCommitId))

    val logAgg = time("gitLogAgg", repo.get.analyzeHistory())
    // print(logAgg.allCommits.size + "\n")

     logAgg.mostChangedFiles(10).foreach({
      case (file, count) => {
        print(s"File: $file, changed: ${count.size} \n")
        print("Top Authors: \n")
        logAgg.topAuthors(file, 3).foreach(e=> print(s"\t $e \n"))
        print("Cochanged files: \n")
        logAgg.coOccuringFiles(file, 5).foreach(e => print(s"\t $e \n"))
      }
    })

    // to reproduce issue #600, replace gitRepoPath in this file to local hyperic/hqapi location
    // new JavaRepo(repo.get).files.foreach(f => print(f.fileDetails))
  }

  def time[T](name: String, block: => T): T = {
    val start = System.currentTimeMillis()
    val result = block
    val end = System.currentTimeMillis()
    print(s"Time taken for: ${name} is ${(end - start)} ms \n")
    result
  }

  test("getting language from repository") {
    val expectedLanguages = Set("md", "java", "xml")
    repo.get.files.foreach(e =>
      assert(expectedLanguages.contains(e.extractLang())
    ))
  }



  test("test GithubFileInfo.extractFileName") {
    val files = repo.get.files
    val githubFileInfo = files.filter(file => file.filePath.contains("CollectLink.java")).next()
    assert(githubFileInfo.extractFileName().equals("CollectLink.java"))
  }

  test("test GithubFileInfo.readFileContent") {
    val files = repo.get.files
    val githubFileInfo = files.filter(file => file.filePath.contains("CollectLink.java")).next()
    assert(githubFileInfo.
      readFileContent.contains("package com.pramati.scraper.google_grp_scraper"))
  }

  test("test GithubFileInfo.extractLang") {
    val files = repo.get.files
    val githubFileInfo = files.filter(file => file.filePath.contains("CollectLink.java")).next()
    assert(githubFileInfo.extractLang.equals("java"))
  }

  // scalastyle:off
  test("test GithubFileInfo.repoFileLocation") {
    val files = repo.get.files
    val githubFileInfo = files.filter(file => file.filePath.contains("CollectLink.java")).next()
    assert(githubFileInfo.repoFileLocation.
      equals("himukr/google-grp-scraper/blob/master/src/main/java/com/pramati/scraper/google_grp_scraper/CollectLink.java"))
  }
  // scalastyle:on
}

trait GitHubRepoMockSupport {
  def mockRepo: Option[GithubRepo] = {
    import sys.process._
    val outputDir = s"""${KodeBeagleConfig.repoCloneDir}/himukr/google-grp-scraper"""

    FileUtils.copyFileToDirectory(
      new File(Thread.currentThread.
        getContextClassLoader.getResource("GitRepoTest-git.tar.gz").getPath),
      new File(s"$outputDir"))


    s"""tar -xvf $outputDir/GitRepoTest-git.tar.gz
        |-C $outputDir""".stripMargin.!!

    var gitRepoPath = s"${KodeBeagleConfig.repoCloneDir}/himukr/google-grp-scraper"
    // gitRepoPath = "/home/sachint/todelete/spark"
    Option(new MockedGithubRepo().init(new Configuration, gitRepoPath))
  }
}
