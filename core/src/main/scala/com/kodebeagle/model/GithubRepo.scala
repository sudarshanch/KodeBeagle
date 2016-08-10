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
import java.nio.file.{Path, Paths}

import com.kodebeagle.logging.Logger
import com.kodebeagle.model.GithubRepo.GithubRepoInfo
import org.apache.hadoop.conf.Configuration
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk.{RevTree, RevWalk}
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.io.Source
import scala.util.{Failure, Success, Try}

/**
  * This is an abstraction over a github repo that is to be analyzed.
  *
  * By default it will read the repo from the hdfs cluster and will throw
  * RepoNotFoundException if it is not found.
  *
  * However, if update is set to `true` then it will first clone the repo
  * from Github, replace the existing repo on Hdfs and read from that repo.
  *
  */
class GithubRepo protected()
  extends Repo with Logger with LazyLoadSupport {

  // TODO: I don't see much use now for this given how we are using it. Clean.
  private var _files: Option[List[GithubFileInfo]] = None
  private var _stats: Option[RepoStatistics] = None
  private var _languages: Option[Set[String]] = None
  private var _repository: Option[Repository] = None
  protected var _repoGitFile: Option[String] = None
  protected var _gitLogAggregation: Option[GitLogAggregation] = None
  var repoInfo: Option[GithubRepoInfo] = None


  // init()

  private def init(configuration: Configuration, githubRepoInfo: GithubRepoInfo): GithubRepo = {
    val repoUpdateHelper = new GithubRepoUpdateHelper(configuration, githubRepoInfo.fullName)
    if (repoUpdateHelper.shouldUpdate()) {
      repoUpdateHelper.update()
    }
    repoInfo = Option(githubRepoInfo)
    _repoGitFile = repoUpdateHelper.downloadLocalFromDfs()
    _repoGitFile match {
      case Some(git) => this
      case None => throw new IllegalStateException("Repo could not be downloaded.")
    }
  }

  override def files: List[GithubFileInfo] = {
    getOrCompute(_files, () => {
      readProject()
      _files.get
    })
  }

  override def statistics: RepoStatistics = {
    getOrCompute(_stats, () => {
      _stats = Option(calculateStats(files))
      _stats.get
    })
  }

  override def languages: Set[String] = {
    getOrCompute(_languages, () => {
      _languages = Option(extractLanguages(files))
      _languages.get
    })
  }

  def repository: Repository = {
    getOrCompute(_repository, () => {
      val repoPath: String = _repoGitFile.get
      val builder: FileRepositoryBuilder = new FileRepositoryBuilder
      _repository = Option(builder.setGitDir(new File(s"$repoPath/.git"))
        .readEnvironment.findGitDir.build)
      _repository.get
    })
  }

  def gitLogAggregation: GitLogAggregation = {
    getOrCompute(_gitLogAggregation, () => {
      readProject()
      _gitLogAggregation.get
    })
  }

  def calculateStats(files: List[GithubFileInfo]): RepoStatistics = {
    var slocSum: Int = 0
    var sizeSum: Long = 0

    for (fileInfo <- files) {
      slocSum += fileInfo.sloc
      sizeSum += fileInfo.fileContent.getBytes.length
    }
    val repoStatistics: RepoStatistics = new RepoStatistics {

      override def sloc: Int = slocSum

      override def fileCount: Int = files.size

      override def size: Long = sizeSum
    }
    repoStatistics

  }

  def extractLanguages(files: List[GithubFileInfo]): Set[String] = {
    val fileLanguages: mutable.Set[String] = mutable.Set[String]()
    files.map(gitHubFileInfo => fileLanguages
      .add(gitHubFileInfo.extractLang()))
    fileLanguages.toSet
  }


  def analyzeHistory(): GitLogAggregation = {
    import GithubRepoUpdateHelper._

    import sys.process._

    val gitLogCommand =
      s"""git log --numstat --format=format:
          |'{"authorName": "%an", "authorEmail": "%aE",
          |"time": "%at", "msg":"%aE"}' """.stripMargin.replaceAll("\n", "")

    val histAggregator = new GitLogAggregation()
    val cmdFrmDir = bashCmdsFromDir(_repoGitFile.get, Seq(s"cd ${_repoGitFile.get}", gitLogCommand))
    cmdFrmDir.!(ProcessLogger(line => histAggregator.merge(line)))
    histAggregator
  }

  private def readProject() = {
    _gitLogAggregation = Option(analyzeHistory())
    val git = new Git(repository)

    val gitHubFilesInfo: mutable.ListBuffer[GithubFileInfo] =
    mutable.ListBuffer[GithubFileInfo]()

    val fileListOpt = Try {

      // a RevWalk allows to walk over commits based on some filtering that is
      // defined
      val revWalk: RevWalk = new RevWalk(repository)

      // find the HEAD
      val headCommitId = repository.resolve(Constants.HEAD)
      val headTree: RevTree = revWalk.parseCommit(headCommitId).getTree
      revWalk.markStart(revWalk.lookupCommit(headCommitId))


      // Now use a TreeWalk to iterate over all files in the Tree recursively
      // We can set Filters to narrow down the results if needed
      val treeWalk: TreeWalk = new TreeWalk(repository)
      treeWalk.addTree(headTree)
      treeWalk.setRecursive(true)
      while (treeWalk.next) {
        val githubFileInfo = new GithubFileInfo(treeWalk.getPathString,
          treeWalk.getObjectId(0), repository, repoInfo.get)
        gitHubFilesInfo.append(githubFileInfo)
      }
      gitHubFilesInfo.toList
    }

    fileListOpt match {
      case Success(list: List[GithubFileInfo]) => _files = Option(list)
      case Failure(e: Throwable) => {
        log.warn(
          s"""Unable to read file list from repo ${repoInfo};
              | empty file list will be returned.
              | (This is possibly due to repo clone being empty)""".stripMargin,
          e)
        _files = Option(List.empty)
      }
    }
  }

}

class GitLogAggregation() extends Logger {

  import org.json4s.jackson.Serialization.read

  implicit val formats = org.json4s.DefaultFormats
  // map of each file vs file counts of other files changed in one commit
  val adjacencyMap = mutable.Map.empty[String, mutable.Map[String, Int]]
  // List of all commits
  val allCommits = mutable.ListBuffer.empty[Commit]
  // per file authors change count (commit, added lines, deleted lines)
  val fileAuthors = mutable.Map.empty[String, mutable.Map[String, (Int, Int, Int)]]
  // The current commit context in which to parse the subsequent file names
  var currentCommit: Option[Commit] = None
  var filesInCurrentCommit: mutable.Set[String] = mutable.Set.empty[String]
  val fileCommitCount: mutable.Map[String, mutable.ListBuffer[Commit]] = mutable.Map.empty

  private def handleCommit(commitStr: String) = {
    currentCommit = Option(read[Commit](commitStr))
    allCommits.add(currentCommit.get)
    // start with empty set on encountering a new commit
    filesInCurrentCommit = mutable.Set.empty
  }

  private def handleFile(fileRecord: String) = {
    // TODO: this should come from the repo (or any other way?)
    fileRecord.contains("java") || fileRecord.contains("scala") match {
      case false => // ignore
      case true => {
        val splits = fileRecord.split("\\t")
        val (file, add, del) = (splits(2), splits(0), splits(1))
        // update commit count for this file
        val existingCommits = fileCommitCount.getOrElse(file, mutable.ListBuffer.empty)
        existingCommits.add(currentCommit.get)
        fileCommitCount.update(file, existingCommits)

        // update author counts
        val authorCount = fileAuthors.getOrElseUpdate(file, mutable.Map.empty)
        val existing = authorCount.getOrElse(currentCommit.get.authorEmail, (0, 0, 0))
        authorCount.update(currentCommit.get.authorEmail,
          (existing._1 + 1,
            existing._2 + Try(add.toInt).getOrElse(0), existing._3 + Try(del.toInt).getOrElse(0)))

        // update adjacency map for all files in current commit upon seeing this file
        filesInCurrentCommit.foreach(p => {
          val adjcencyCount = adjacencyMap.getOrElseUpdate(p, mutable.Map.empty)
          adjcencyCount.update(file, adjcencyCount.getOrElse(file, 0) + 1)
        })

        // also update the adjacency map for this file wrt to earlier seen files
        val currentFileAdjcencyCount = adjacencyMap.getOrElseUpdate(file, mutable.Map.empty)
        filesInCurrentCommit.foreach(p => {
          currentFileAdjcencyCount.update(p, currentFileAdjcencyCount.getOrElse(p, 0) + 1)
        })

        filesInCurrentCommit.add(file)
      }
    }

  }

  def merge(line: String): Unit = {
    try {
      line.trim.isEmpty match {
        case true => // ignore
        case false => line.startsWith("{") match {
          case true => handleCommit(line)
          case false => handleFile(line)
        }
      }
    } catch {
      case e: Exception => log.error(s"Unable to read log line: ${line}", e)
    }
  }

  def mostChangedFiles(n: Int = 0): List[(String, mutable.ListBuffer[Commit])] = {
    val sortedFiles = fileCommitCount.toList.sortBy(-_._2.size)
    n > 0 match {
      case true => sortedFiles.take(n)
      case false => sortedFiles
    }
  }

  def coOccuringFiles(fileName: String, n: Int = 0): List[(String, Int)] = {
    adjacencyMap.get(fileName) match {
      case Some(m) => {
        val sorted = m.toList.sortBy(-_._2)
        n > 0 match {
          case true => sorted.take(n)
          case false => sorted
        }
      }
      case None => List.empty
    }
  }

  def topAuthors(fileName: String, n: Int = 0): List[(String, (Int, Int, Int))] = {
    def authorRelevanceScore(commit: Int, addedLines: Int, deletedLines: Int): Double = {
      // downgrade the line changes to log but give twice as much imp to add than dels
      // no high funda, just gut feel
      commit + 2 * Math.log(addedLines) + Math.log(deletedLines)
    }
    fileAuthors.get(fileName) match {
      case Some(authorCount) => {
        val sorted = authorCount.toList
          .sortBy(e => -authorRelevanceScore(e._2._1, e._2._2, e._2._3))
        n > 0 match {
          case true => sorted.take(n)
          case false => sorted
        }
      }
      case None => List.empty
    }
  }

}

case class Commit(time: String, authorName: String, authorEmail: String, msg: String)

object GithubRepo {

  val remote = "http://www.github.com"

  case class GithubRepoInfo(id: Long, login: String, name: String, fullName: String,
                            isPrivate: Boolean, isFork: Boolean, size: Long, watchersCount: Long,
                            language: String, forksCount: Long, subscribersCount: Long,
                            defaultBranch: String, stargazersCount: Long)

  def apply(configuration: Configuration, githubRepoInfo: GithubRepoInfo): Option[GithubRepo] = {
    Try(new GithubRepo().init(configuration, githubRepoInfo)).toOption
  }

}

class GithubFileInfo(filePath: String, objectId: ObjectId, repository: Repository,
                     val githubRepoInfo: GithubRepoInfo) extends BaseFileInfo(filePath) {

  override def extractFileName(): String = {
    val p: Path = Paths.get(filePath)
    p.getFileName.toString
  }

  override def readFileContent(): String = {
    val loader: ObjectLoader = repository.open(objectId)
    new String(loader.getBytes(), "UTF-8")
  }

  override def extractLang(): String = {
    val fileType: Array[String] = filePath.split("\\.")
    if (fileType.length > 1) {
      fileType(1)
    } else {
      UNKNOWN_LANG
    }
  }

  override def readSloc(): Int = {
    Source.fromString(fileContent).getLines().size
  }

  override def repoFileLocation: String = {
    s"${githubRepoInfo.login}/${githubRepoInfo.name}/blob/${githubRepoInfo.defaultBranch}/$filePath"
  }

  override def repoId: Long = {
    githubRepoInfo.id
  }
}

