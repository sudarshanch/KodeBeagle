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
  private var _repository: Option[Repository] = None
  protected var _repoGitFile: Option[String] = None
  var repoInfo: Option[GithubRepoInfo] = None
  var _gitLogAggregation: Option[GitLogAggregation] = None


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

  override def files: Iterator[GithubFileInfo] = new FileIterator(repository, repoInfo.get)

  def repository: Repository = {
    getOrCompute(_repository, () => {
      val repoPath: String = _repoGitFile.get
      val builder: FileRepositoryBuilder = new FileRepositoryBuilder
      _repository = Option(builder.setGitDir(new File(s"$repoPath/.git"))
        .readEnvironment.findGitDir.build)
      _repository.get
    })
  }

  def gitLogAggregation: GitLogAggregation = getOrCompute(_gitLogAggregation, () => {
    _gitLogAggregation = Option(analyzeHistory())
    _gitLogAggregation.get
  })

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
          |"time": "%at", "msg":"%f"}' """.stripMargin.replaceAll("\n", "")

    val histAggregator = new GitLogAggregation(repoInfo.get)
    val cmdFrmDir = bashCmdsFromDir(_repoGitFile.get, Seq(s"cd ${_repoGitFile.get}", gitLogCommand))
    cmdFrmDir.!(ProcessLogger(line => histAggregator.merge(line)))
    histAggregator
  }

}

class FileIterator(repository: Repository, repoInfo: GithubRepoInfo)
  extends Iterator[GithubFileInfo] with Logger {

  val treeWalkMaybe = Try {
    val git = new Git(repository)
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
    treeWalk
  }

  val treeWalkOpt = treeWalkMaybe match {
    case Success(treeWalk) => Option(treeWalk)
    case Failure(e: Throwable) => {
      log.warn(
        s"""Unable to read file list from repo ${repoInfo};
            | empty file list will be returned.
            | (This is possibly due to repo clone being empty)""".stripMargin,
        e)
      None
    }
  }

  // This call advances the iterator to next element on every invocation
  override def hasNext: Boolean = {
    treeWalkOpt match {
      case None => false
      case Some(treeWalk) => treeWalk.next
    }

  }

  override def next(): GithubFileInfo = {
    treeWalkOpt match {
      case None => throw new IllegalStateException("next() called on non available iterator.")
      case Some(treeWalk) => new GithubFileInfo(treeWalk.getPathString,
        treeWalk.getObjectId(0), repository, repoInfo)
    }

  }
}

class GitLogAggregation(repoInfo: GithubRepoInfo) extends Logger {

  import org.json4s.jackson.Serialization.read

  implicit val formats = org.json4s.DefaultFormats
  // List of all commits
  val allCommits = mutable.ListBuffer.empty[CommitWithFiles]
  // The current commit context in which to parse the subsequent file names
  private var currentCommit: Option[Commit] = None
  private var filesInCurrentCommit: mutable.Set[(String, Int, Int)] =
    mutable.Set.empty[(String, Int, Int)]

  private def handleCommit(commitStr: String) = {
    currentCommit match {
      case Some(c) => allCommits.add(CommitWithFiles(c, filesInCurrentCommit.toSet))
      case None => // ignore (on first commit)
    }
    currentCommit = Option(read[Commit](commitStr.replace("\\", "\\\\")))
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
        filesInCurrentCommit.add((file, Try(add.toInt).getOrElse(0), Try(del.toInt).getOrElse(0)))
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
      case e: Exception => log.error(s"Unable to read log line: ${line} in ${repoInfo.fullName}"
        , e)
    }
  }

  def mostChangedFiles(n: Int = 0): List[(String, mutable.ListBuffer[Commit])] = {
    val countAgg = allCommits.flatMap(cwf => cwf.files.map(f => (f, cwf.commit)))
      .aggregate(mutable.Map.empty[String, mutable.ListBuffer[Commit]])(
        (agg, file) => {
          val existingSet = agg.getOrElseUpdate(file._1._1, mutable.ListBuffer.empty[Commit])
          existingSet.add(file._2)
          agg
        },
        (agg1, agg2) => {
          agg2.foreach(file => {
            val existingSet = agg1.getOrElseUpdate(file._1, mutable.ListBuffer.empty[Commit])
            existingSet ++= file._2
          })
          agg1
        })
    val sortedFiles = countAgg.toList.sortBy(-_._2.size)
    n > 0 match {
      case true => sortedFiles.take(n)
      case false => sortedFiles
    }
  }

  def coOccuringFiles(fileName: String, n: Int = 0): List[(String, Int)] = {
    val cooccurMap = allCommits.filter(_.files.map(_._1).contains(fileName)).flatMap(_.files)
      .filter(!_._1.equals(fileName)).foldLeft(mutable.Map.empty[String, Int])((agg, file) => {
      agg.update(file._1, agg.getOrElse(file._1, 0) + 1)
      agg
    })

    val sorted = cooccurMap.toList.sortBy(-_._2)
    n > 0 match {
      case true => sorted.take(n)
      case false => sorted
    }
  }

  def topAuthors(fileName: String, n: Int = 0): List[(String, (Int, Int, Int))] = {
    def authorRelevanceScore(commit: Int, addedLines: Int, deletedLines: Int): Double = {
      // downgrade the line changes to log but give twice as much imp to add than dels
      // no high funda, just gut feel
      commit + 2 * Math.log(addedLines) + Math.log(deletedLines)
    }
    val authCount = allCommits.filter(_.files.map(_._1).contains(fileName))
      .flatMap(cwf => cwf.files.map((_, cwf.commit.authorEmail))).filter(_._1._1.equals(fileName))
      .foldLeft(mutable.Map.empty[String, (Int, Int, Int)])((agg, fileInfo) => {
        val existing = agg.getOrElse(fileInfo._2, (0, 0, 0))
        agg.update(fileInfo._2,
          // update existing value for author with new count, added lines, deleted lines
          (existing._1 + 1, existing._2 + fileInfo._1._2, existing._3 + fileInfo._1._3))
        agg
      })

    val sorted = authCount.toList.sortBy(e => -authorRelevanceScore(e._2._1, e._2._2, e._2._3))
    n > 0 match {
      case true => sorted.take(n)
      case false => sorted
    }
  }

  def fileCommmitCount(fileName: String): List[Commit] = {
    allCommits.filter(_.files.map(_._1).contains(fileName)).map(_.commit).toList
  }

}

case class Commit(time: String, authorName: String, authorEmail: String, msg: String)

case class CommitWithFiles(commit: Commit, files: Set[(String, Int, Int)] = Set.empty)

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
      fileType(fileType.length - 1)
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

