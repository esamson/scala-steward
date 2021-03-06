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

package eu.timepit.scalasteward.update

import cats.Monad
import cats.implicits._
import eu.timepit.scalasteward.dependency.DependencyRepository
import eu.timepit.scalasteward.github.data.Repo
import eu.timepit.scalasteward.util
import eu.timepit.scalasteward.model.Update
import eu.timepit.scalasteward.sbt._
import eu.timepit.scalasteward.util.MonadThrowable
import io.chrisdavenport.log4cats.Logger

class UpdateService[F[_]](
    implicit
    dependencyRepository: DependencyRepository[F],
    logger: Logger[F],
    sbtAlg: SbtAlg[F],
    updateRepository: UpdateRepository[F],
    F: Monad[F]
) {

  // Add configuration "phantom-js-jetty" to Update

  // WIP
  def checkForUpdates(implicit F: MonadThrowable[F]): F[List[Update]] =
    dependencyRepository.getDependencies.flatMap { dependencies =>
      val (libraries, plugins) = dependencies
        .filter(
          d =>
            d.groupId != "org.scala-lang" && d.artifactId != "scala-library"
              && d.groupId != "org.eclipse.jetty" && d.artifactId != "jetty-server" &&
              d.artifactId != "jetty-websocket"
        )
        .partition(_.sbtVersion.isEmpty)
      val libProjects = splitter
        .xxx(libraries)
        .map { libs =>
          ArtificialProject(
            ScalaVersion("2.12.7"),
            SbtVersion("1.2.4"),
            libs.sortBy(_.formatAsModuleId),
            List.empty
          )
        }

      val pluginProjects = plugins
        .groupBy(_.sbtVersion)
        .flatMap {
          case (maybeSbtVersion, plugins) =>
            splitter.xxx(plugins).map { ps =>
              ArtificialProject(
                ScalaVersion("2.12.7"),
                seriesToSpecificVersion(maybeSbtVersion.get),
                List.empty,
                ps.sortBy(_.formatAsModuleId)
              )
            }
        }
        .toList

      /*
      val pluginProjects = splitter
        .xxx(plugins)
        .map { ps =>
          ArtificialProject(
            ScalaVersion("2.12.7"),
            SbtVersion("1.2.4"),
            List.empty,
            ps.sortBy(_.formatAsModuleId)
          )
        }*/
      val x = (libProjects ++ pluginProjects).flatTraverse { prj =>
        sbtAlg.getUpdates(prj).attempt.flatMap {
          case Right(updates) =>
            logger.info(util.logger.showUpdates(updates)) >>
              updates.traverse_(updateRepository.save) >> F.pure(updates)
          case Left(t) =>
            println(t)
            F.pure(List.empty[Update])
        }
      }

      x.flatTap { updates =>
        foo(updates).map(rs => { rs.foreach(println); rs })
      }
    }

  def foo(updates: List[Update]): F[List[Repo]] =
    dependencyRepository.getStore.map { store =>
      store
        .filter(
          _._2.dependencies.exists(
            d =>
              updates.exists(
                u => u.groupId == d.groupId && d.artifactIdCross == u.artifactId && d.version == u.currentVersion
              )
          )
        )
        .keys
        .toList
    }

}
