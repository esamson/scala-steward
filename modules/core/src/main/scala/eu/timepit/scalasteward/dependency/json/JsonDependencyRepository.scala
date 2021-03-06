/*
 * Copyright 2018 scala-steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.timepit.scalasteward.dependency.json

import better.files.File
import cats.implicits._
import eu.timepit.scalasteward.dependency.{Dependency, DependencyRepository}
import eu.timepit.scalasteward.git.Sha1
import eu.timepit.scalasteward.github.data.Repo
import eu.timepit.scalasteward.io.{FileAlg, WorkspaceAlg}
import eu.timepit.scalasteward.util.MonadThrowable
import io.circe.parser.decode
import io.circe.syntax._

class JsonDependencyRepository[F[_]](
    implicit
    fileAlg: FileAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: MonadThrowable[F]
) extends DependencyRepository[F] {

  override def findSha1(repo: Repo): F[Option[Sha1]] =
    readJson.map(_.store.get(repo).map(_.sha1))

  override def getDependencies: F[List[Dependency]] =
    readJson.map(_.store.values.flatMap(_.dependencies).toList.distinct)

  override def getStore: F[Map[Repo, RepoData]] =
    readJson.map(_.store)

  override def setDependencies(repo: Repo, sha1: Sha1, dependencies: List[Dependency]): F[Unit] =
    readJson.flatMap { store =>
      val updated = store.store.updated(repo, RepoData(sha1, dependencies))
      writeJson(RepoStore(updated))
    }

  def jsonFile: F[File] =
    workspaceAlg.rootDir.map(_ / "repos_v04.json")

  def readJson: F[RepoStore] =
    jsonFile.flatMap { file =>
      fileAlg.readFile(file).flatMap {
        case Some(content) => F.fromEither(decode[RepoStore](content))
        case None          => F.pure(RepoStore(Map.empty))
      }
    }

  def writeJson(store: RepoStore): F[Unit] =
    jsonFile.flatMap { file =>
      fileAlg.writeFile(file, store.asJson.toString)
    }
}
